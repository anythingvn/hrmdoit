import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { Link, MemoryRouter, Route, Routes } from 'react-router';

import { makeStore } from '../test-utils';
import EmployeeDetail from './employee-detail';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const EMPLOYEES: Record<string, any> = {
  1: { id: 1, fullName: 'Trần Minh Quân', code: 'NV0142', status: 'DANG_LAM_VIEC' },
  2: { id: 2, fullName: 'Lê Thị Hoa', code: 'NV0200', status: 'DANG_LAM_VIEC' },
  3: { id: 3, fullName: 'Phạm Sắp Nghỉ', code: 'NV0300', status: 'DANG_LAM_VIEC', scheduledTermination: '2026-10-31' },
  5: {
    id: 5,
    fullName: 'Ngô Quá Hạn',
    code: 'NV0500',
    status: 'DANG_LAM_VIEC',
    scheduledTermination: '2026-10-31',
    scheduledTerminationOverdue: true,
    scheduledTerminationError: 'Ngày làm việc cuối trước giai đoạn làm việc đã lên kế hoạch từ 03/11/2026',
  },
  4: {
    id: 4,
    fullName: 'Đỗ Đã Nghỉ',
    code: 'NV0400',
    status: 'DA_NGHI',
    lastWorkingDay: '2026-10-26',
    terminationDate: '2026-10-27',
    terminationUndoable: true,
  },
};

const renderDetail = (id: number, authorities = ['ROLE_HR'], query = '') =>
  render(
    <Provider store={makeStore(authorities)}>
      <MemoryRouter initialEntries={[`/nhan-vien/${id}${query}`]}>
        <Routes>
          <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeDetail', () => {
  it('?tab=contracts mở thẳng tab Hợp đồng & lương', async () => {
    renderDetail(1, ['ROLE_HR'], '?tab=contracts');
    const tab = await screen.findByRole('tab', { name: 'Hợp đồng & lương' });
    expect(tab.getAttribute('aria-selected')).toBe('true');
  });

  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockImplementation((url: string) => {
      const sensitive = /api\/employees\/(\d+)\/sensitive$/.exec(url);
      if (sensitive) return Promise.resolve({ data: { idNumber: sensitive[1] === '1' ? '001096012345' : '079000000002' } } as any);
      const one = /api\/employees\/(\d+)$/.exec(url);
      if (one) return Promise.resolve({ data: EMPLOYEES[one[1]] } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('chuyển sang nhân viên khác thì dữ liệu nhạy cảm bị ẩn lại và về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    expect(await screen.findByText('001096012345')).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.queryByText('001096012345')).toBeNull();
    expect(screen.queryByText('079000000002')).toBeNull();
    expect(screen.getByRole('button', { name: 'Hiện' })).toBeTruthy();
  });

  it('đang ở tab khác, chuyển nhân viên thì về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử công tác' }));
    expect(screen.getByRole('tab', { name: 'Lịch sử công tác' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.getByRole('tab', { name: 'Thông tin' }).getAttribute('aria-selected')).toBe('true');
  });

  it('lịch nghỉ đã hẹn: nhãn "Sắp nghỉ" và nút "Hủy lịch nghỉ" có xác nhận', async () => {
    mocked.delete.mockResolvedValue({ data: {} } as any);
    renderDetail(3);
    await screen.findByRole('heading', { name: 'Phạm Sắp Nghỉ' });
    expect(screen.getByText('Sắp nghỉ (31/10/2026)')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Cho nghỉ việc' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Hủy lịch nghỉ' }));
    expect(mocked.delete).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hủy lịch' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/employees/3/termination'));
  });

  it('đã nghỉ và hoàn tác được: nút "Hoàn tác nghỉ việc" có xác nhận', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/4/termination/undo'));
  });

  it('hoàn tác bị từ chối vì dữ liệu đã sửa thì hiện chi tiết từ server', async () => {
    mocked.post.mockRejectedValue({
      response: { status: 400, data: { message: 'error.undoconflict', detail: 'Không hoàn tác được: Hợp đồng HD01 đã sửa' } },
    });
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    expect(await screen.findByText('Không hoàn tác được: Hợp đồng HD01 đã sửa')).toBeTruthy();
  });

  it('KE_TOAN thấy nhãn nhưng không có nút hủy lịch / hoàn tác', async () => {
    const { unmount } = renderDetail(3, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Phạm Sắp Nghỉ' });
    expect(screen.getByText('Sắp nghỉ (31/10/2026)')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Hủy lịch nghỉ' })).toBeNull();
    unmount();
    renderDetail(4, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    expect(screen.queryByRole('button', { name: 'Hoàn tác nghỉ việc' })).toBeNull();
  });

  it('lịch nghỉ quá hạn chưa áp dụng được: nhãn đỏ kèm lý do', async () => {
    renderDetail(5);
    await screen.findByRole('heading', { name: 'Ngô Quá Hạn' });
    expect(
      screen.getByText('Quá hạn nghỉ việc – chưa áp dụng được: Ngày làm việc cuối trước giai đoạn làm việc đã lên kế hoạch từ 03/11/2026'),
    ).toBeTruthy();
    expect(screen.queryByText('Sắp nghỉ (31/10/2026)')).toBeNull();
    expect(screen.getByRole('button', { name: 'Hủy lịch nghỉ' })).toBeTruthy();
  });

  it('hoàn tác nhưng tài khoản đã bỏ gắn: hiện ghi chú của server', async () => {
    mocked.post.mockResolvedValue({ data: { terminationNotice: 'Tài khoản đăng nhập đã bị bỏ gắn — không mở lại tài khoản cũ.' } } as any);
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    expect(await screen.findByText('Tài khoản đăng nhập đã bị bỏ gắn — không mở lại tài khoản cũ.')).toBeTruthy();
  });
  it('nút "Tạo văn bản từ mẫu" mở tab Hợp đồng và hộp văn bản của HĐ mới nhất', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/employees/1') return Promise.resolve({ data: EMPLOYEES[1] } as any);
      if (url === 'api/employees/1/contracts')
        return Promise.resolve({
          data: [
            { id: 70, type: 'HDTV', startDate: '2026-01-01', endDate: '2026-02-28', status: 'DA_KY' },
            { id: 71, type: 'HDLD_XDTH', contractNo: 'DOIT/2026/HĐLĐ/9', startDate: '2026-03-01', status: 'DA_KY' },
          ],
        } as any);
      return Promise.resolve({ data: [] } as any);
    });
    renderDetail(1);
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo văn bản từ mẫu' }));
    expect(await screen.findByRole('heading', { name: /Văn bản — HĐLĐ xác định thời hạn số DOIT\/2026\/HĐLĐ\/9/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Hợp đồng & lương' }).getAttribute('aria-selected')).toBe('true');
  });

  it('Kế toán không thấy nút "Tạo văn bản từ mẫu"', async () => {
    renderDetail(1, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    expect(screen.queryByRole('button', { name: 'Tạo văn bản từ mẫu' })).toBeNull();
  });
});
