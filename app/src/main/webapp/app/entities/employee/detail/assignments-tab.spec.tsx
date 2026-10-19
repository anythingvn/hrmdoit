import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import AssignmentsTab from './assignments-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderTab = (role = 'ROLE_HR') =>
  render(
    <Provider store={makeStore([role])}>
      <AssignmentsTab employeeId={1} />
    </Provider>,
  );

describe('AssignmentsTab', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.patch.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url.includes('work-sites')) return Promise.resolve({ data: [{ id: 3, name: 'Văn phòng Hà Nội', type: 'VAN_PHONG' }] } as any);
      if (url.includes('timeline'))
        return Promise.resolve({
          data: [
            { id: 11, from: '2023-11-03', to: '2026-05-31', type: 'VAN_PHONG', unit: 'Văn phòng Hà Nội', label: 'Văn phòng Hà Nội' },
            { id: 12, from: '2026-06-01', to: null, type: 'CONG_TAC', unit: 'Chi nhánh TP.HCM', label: 'Chi nhánh TP.HCM' },
          ],
        } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('hiện dòng thời gian, giai đoạn đang mở là hiện tại', async () => {
    renderTab();
    expect(await screen.findByText('01/06/2026 – nay')).toBeTruthy();
    expect(screen.getByText('Hiện tại')).toBeTruthy();
  });

  it('server báo chồng lấn thì hiện nguyên câu báo trên form', async () => {
    const detail = 'Trùng thời gian với đợt công tác Chi nhánh TP.HCM (01/06/2026 – 31/08/2026)';
    mocked.post.mockRejectedValue({
      response: { status: 400, data: { title: 'Lịch sử vị trí làm việc không hợp lệ', detail, message: 'error.assignmentconflict' } },
    });
    renderTab();
    fireEvent.click(await screen.findByRole('button', { name: '+ Thêm điều động' }));
    await screen.findByRole('option', { name: 'Văn phòng Hà Nội' });
    fireEvent.change(screen.getByLabelText('Nơi làm việc'), { target: { value: '3' } });
    fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '01/07/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText(detail)).toBeTruthy();
    expect(mocked.post).toHaveBeenCalledWith(
      'api/employees/1/assignments',
      expect.objectContaining({ fromDate: '2026-07-01', site: { id: 3 } }),
    );
  });

  it('Kế toán không thấy nút thêm điều động', async () => {
    renderTab('ROLE_KE_TOAN');
    await screen.findByText('01/06/2026 – nay');
    expect(screen.queryByRole('button', { name: '+ Thêm điều động' })).toBeNull();
  });

  it('Xóa giai đoạn: hỏi xác nhận, gọi DELETE rồi tải lại', async () => {
    mocked.delete.mockResolvedValue({ status: 204 } as any);
    const onChanged = vi.fn();
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <AssignmentsTab employeeId={1} onChanged={onChanged} />
      </Provider>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa giai đoạn 03/11/2023 – 31/05/2026' }));
    expect(screen.getByText('Xóa giai đoạn này? Thao tác được ghi vào nhật ký.')).toBeTruthy();
    const timelineCalls = () => mocked.get.mock.calls.filter(c => String(c[0]).includes('timeline')).length;
    const before = timelineCalls();
    fireEvent.click(screen.getByRole('button', { name: 'Xóa' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/employees/1/assignments/11'));
    await waitFor(() => expect(timelineCalls()).toBe(before + 1));
    expect(onChanged).toHaveBeenCalled();
  });

  it('Đổi ngày kết thúc gọi PATCH …/end', async () => {
    mocked.patch.mockResolvedValue({ data: {} } as any);
    renderTab();
    fireEvent.click(await screen.findByRole('button', { name: 'Đổi ngày kết thúc 01/06/2026 – nay' }));
    fireEvent.change(screen.getByLabelText('Ngày kết thúc mới'), { target: { value: '30/11/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu ngày kết thúc' }));
    await waitFor(() => expect(mocked.patch).toHaveBeenCalledWith('api/employees/1/assignments/12/end', { toDate: '2026-11-30' }));
  });

  it('Kế toán không thấy nút xóa / đổi ngày kết thúc', async () => {
    renderTab('ROLE_KE_TOAN');
    await screen.findByText('01/06/2026 – nay');
    expect(screen.queryByRole('button', { name: /^Xóa giai đoạn/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Đổi ngày kết thúc/ })).toBeNull();
  });
});
