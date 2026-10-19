import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import AssignmentsTab from './assignments-tab';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

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
    mocked.get.mockImplementation((url: string) => {
      if (url.includes('work-sites')) return Promise.resolve({ data: [{ id: 3, name: 'Văn phòng Hà Nội', type: 'VAN_PHONG' }] } as any);
      if (url.includes('timeline'))
        return Promise.resolve({
          data: [
            { from: '2023-11-03', to: '2026-05-31', type: 'VAN_PHONG', unit: 'Văn phòng Hà Nội', label: 'Văn phòng Hà Nội' },
            { from: '2026-06-01', to: null, type: 'CONG_TAC', unit: 'Chi nhánh TP.HCM', label: 'Chi nhánh TP.HCM' },
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
});
