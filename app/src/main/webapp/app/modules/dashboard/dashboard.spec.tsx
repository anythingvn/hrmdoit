import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import Dashboard, { dateLine, greeting } from './dashboard';
import { summarize } from './today-attendance';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const movement = Array.from({ length: 12 }, (_, i) => {
  const d = new Date(2025, 10 + i, 1);
  return { month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`, hires: i % 3, leavers: i % 2, closing: 10 + i };
});

const leave = {
  id: 31,
  employeeId: 5,
  employeeName: 'Ngô Thảo',
  type: 'PHEP_NAM',
  fromDate: '2026-10-22',
  toDate: '2026-10-23',
  days: 2,
  status: 'CHO_DUYET',
};
const punch = { type: 'PUNCH', id: 9, employeeId: 6, employeeName: 'Bùi Linh', day: '2026-10-19', summary: 'Vào 08:05' };
const ot = {
  id: 4,
  employeeId: 7,
  employeeName: 'Hà Tùng',
  workDate: '2026-10-17',
  startTime: '18:00',
  endTime: '21:00',
  plannedHours: 3,
  rate: 150,
  night: false,
  status: 'CHO_DUYET',
};
const reminder = {
  type: 'CONTRACT_EXPIRY',
  typeLabel: 'Hết hạn hợp đồng',
  employeeId: 8,
  employeeCode: 'NV0142',
  employeeName: 'Trần Minh Quân',
  eventDate: '2026-10-25',
  daysLeft: 5,
  urgent: true,
  message: 'HĐ hết hạn ngày 25/10/2026.',
};

const Where = () => {
  const loc = useLocation();
  return <p data-testid="where">{`${loc.pathname}${loc.search}`}</p>;
};

const renderAs = (authorities: string[], data: Record<string, unknown>, account: Record<string, unknown> = {}) => {
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/dashboard') return Promise.resolve({ data } as any);
    if (url === 'api/reminders/upcoming') return Promise.resolve({ data: [reminder] } as any);
    if (url === 'api/leave/requests/inbox') return Promise.resolve({ data: [leave] } as any);
    if (url === 'api/leave/overtime/inbox') return Promise.resolve({ data: [ot] } as any);
    if (url === 'api/attendance/pending') return Promise.resolve({ data: [punch] } as any);
    return Promise.resolve({ data: [] } as any);
  });
  return render(
    <Provider store={makeStore(authorities, account)}>
      <MemoryRouter initialEntries={['/tong-quan']}>
        <Routes>
          <Route path="/tong-quan" element={<Dashboard />} />
          <Route path="/nhan-vien" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
};

describe('Dashboard', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('Kế toán thấy chi phí lương kỳ gần nhất kèm trạng thái kỳ', async () => {
    renderAs(['ROLE_KE_TOAN'], {
      headcount: 137,
      onProbation: 1,
      contractsExpiring30: 2,
      payrollCost: { period: '2026-06', status: 'NHAP', totalCost: 245709000 },
      movement,
    });
    expect(await screen.findByText('Chi phí lương kỳ 06/2026')).toBeTruthy();
    expect(screen.getByText('245.709.000')).toBeTruthy();
    expect(screen.getByText('Nháp')).toBeTruthy();
    expect(screen.getByText('Tổng nhân sự')).toBeTruthy();
    expect(screen.getByText('137')).toBeTruthy();
    expect(screen.getByRole('img', { name: /Biến động nhân sự 2026/ })).toBeTruthy();
  });

  it('không có payrollCost thì không có ô chi phí', async () => {
    renderAs(['ROLE_HR'], { headcount: 14, onProbation: 1, contractsExpiring30: 2, movement });
    expect(await screen.findByText('Tổng nhân sự')).toBeTruthy();
    expect(screen.queryByText(/Chi phí lương kỳ/)).toBeNull();
  });

  it('biểu đồ biến động chỉ lấy các tháng của năm hiện tại', async () => {
    renderAs(['ROLE_HR'], { headcount: 14, onProbation: 1, contractsExpiring30: 2, movement });
    await screen.findByRole('heading', { name: 'Biến động nhân sự 2026' });
    const rows = screen.getAllByRole('row').map(r => r.textContent ?? '');
    expect(rows.some(r => r.startsWith('10/2026'))).toBe(true);
    expect(rows.some(r => r.startsWith('12/2025'))).toBe(false);
  });

  it('lời chào theo tên gọi; dòng phụ đếm nhắc việc + đơn chờ duyệt của người duyệt', async () => {
    renderAs(
      ['ROLE_HR'],
      { headcount: 14, onProbation: 1, contractsExpiring30: 2, movement },
      { firstName: 'Hoàng', lastName: 'Lan', isApprover: true },
    );
    expect(await screen.findByRole('heading', { level: 1, name: /, Lan$/ })).toBeTruthy();
    // 1 nhắc việc + 3 đơn (nghỉ, làm thêm, chấm công).
    expect(await screen.findByText(/· 4 việc cần bạn xử lý$/)).toBeTruthy();
  });

  it('không phải người duyệt: không có thẻ "Đơn chờ duyệt", chỉ đếm nhắc việc', async () => {
    renderAs(['ROLE_BGD'], { headcount: 14, onProbation: 1, contractsExpiring30: 2, movement });
    expect(await screen.findByText(/· 1 việc cần bạn xử lý$/)).toBeTruthy();
    expect(screen.queryByRole('region', { name: /Đơn chờ duyệt/ })).toBeNull();
    expect(mocked.get).not.toHaveBeenCalledWith('api/leave/requests/inbox');
  });

  it('nút "+ Thêm nhân viên" chỉ cho Quản trị/HR', async () => {
    const { unmount } = renderAs(['ROLE_BGD'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement });
    await screen.findByText('Tổng nhân sự');
    expect(screen.queryByRole('link', { name: '+ Thêm nhân viên' })).toBeNull();
    unmount();
    renderAs(['ROLE_HR'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement });
    await screen.findByText('Tổng nhân sự');
    expect(screen.getByRole('link', { name: '+ Thêm nhân viên' }).getAttribute('href')).toBe('/nhan-vien/moi');
  });

  it('tìm nhân viên dẫn tới danh sách nhân viên đã lọc', async () => {
    renderAs(['ROLE_HR'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement });
    fireEvent.change(screen.getByLabelText('Tìm nhân viên'), { target: { value: 'Trần Quân' } });
    fireEvent.submit(screen.getByRole('search'));
    expect((await screen.findByTestId('where')).textContent).toBe('/nhan-vien?q=Tr%E1%BA%A7n%20Qu%C3%A2n');
  });

  it('Duyệt đơn nghỉ và lần chấm gọi đúng API duyệt; làm thêm giờ dẫn sang "Duyệt đơn"', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderAs(['ROLE_HR'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement }, { isApprover: true });
    const card = await screen.findByRole('region', { name: /Đơn chờ duyệt/ });
    await screen.findByText('Ngô Thảo');
    expect(screen.getByText('Nghỉ phép năm · 22/10/2026 – 23/10/2026 · 2 ngày')).toBeTruthy();
    const approve = () => screen.getAllByRole('button', { name: 'Duyệt' });
    expect(approve()).toHaveLength(2);
    fireEvent.click(approve()[0]);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/31/approve', {}));
    await waitFor(() => expect(approve()[1].hasAttribute('disabled')).toBe(false));
    fireEvent.click(approve()[1]);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches/9/approve'));
    expect(screen.getByRole('link', { name: 'Xử lý' }).getAttribute('href')).toBe('/duyet-don');
    expect(card.querySelector('a[href="/duyet-don"]')).toBeTruthy();
  });

  it('Từ chối bắt buộc ghi lý do rồi gọi API từ chối', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderAs(['ROLE_HR'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement }, { isApprover: true });
    await screen.findByText('Ngô Thảo');
    fireEvent.click(screen.getAllByRole('button', { name: 'Từ chối' })[0]);
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận từ chối' }));
    expect(await screen.findByText('Phải ghi lý do từ chối')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Lý do từ chối'), { target: { value: 'Trùng lịch dự án' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận từ chối' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/31/reject', { note: 'Trùng lịch dự án' }));
  });

  it('người gõ giờ thấy "Chấm công hôm nay" tính từ bảng ngày', async () => {
    const row = (over: Record<string, unknown>) => ({ lateMinutes: 0, ...over });
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/attendance/day')
        return Promise.resolve({
          data: {
            date: '2026-10-26',
            graceMinutes: 5,
            halfDayThresholdMinutes: 240,
            groups: [
              {
                group: 'A',
                label: 'A',
                hours: {},
                rows: [row({ in: '08:00' }), row({ in: '08:30', lateMinutes: 30 }), row({ autoKind: 'PHEP_NAM' })],
              },
            ],
          },
        } as any);
      return Promise.resolve({
        data: url === 'api/dashboard' ? { headcount: 3, onProbation: 0, contractsExpiring30: 0, movement } : [],
      } as any);
    });
    render(
      <Provider store={makeStore(['ROLE_HR'], { entersAttendance: true })}>
        <MemoryRouter>
          <Dashboard />
        </MemoryRouter>
      </Provider>,
    );
    const card = await screen.findByRole('region', { name: 'Chấm công hôm nay' });
    expect(await screen.findByText('Tỷ lệ đúng giờ')).toBeTruthy();
    expect(card.textContent).toContain('Đã check-in2');
    expect(card.textContent).toContain('Đi muộn1');
    expect(card.textContent).toContain('Nghỉ có phép1');
    expect(card.textContent).toContain('50%');
  });

  it('không gõ giờ thì không có "Chấm công hôm nay"', async () => {
    renderAs(['ROLE_KE_TOAN'], { headcount: 1, onProbation: 0, contractsExpiring30: 0, movement });
    await screen.findByText('Tổng nhân sự');
    expect(screen.queryByText('Chấm công hôm nay')).toBeNull();
    expect(mocked.get).not.toHaveBeenCalledWith('api/attendance/day', expect.anything());
  });
});

describe('Dashboard helpers', () => {
  it('lời chào theo giờ', () => {
    expect(greeting(7)).toBe('Chào buổi sáng');
    expect(greeting(10)).toBe('Chào buổi sáng');
    expect(greeting(11)).toBe('Chào buổi chiều');
    expect(greeting(17)).toBe('Chào buổi chiều');
    expect(greeting(18)).toBe('Chào buổi tối');
  });

  it('dòng ngày "Thứ Hai, 19/10/2026"', () => {
    expect(dateLine(new Date(2026, 9, 19))).toBe('Thứ Hai, 19/10/2026');
    expect(dateLine(new Date(2026, 9, 25))).toBe('Chủ nhật, 25/10/2026');
  });

  it('summarize đếm công tác', () => {
    const s = summarize({
      date: '2026-10-26',
      graceMinutes: 0,
      halfDayThresholdMinutes: 0,
      groups: [{ group: 'A', label: 'A', hours: {} as any, rows: [{ autoKind: 'CONG_TAC', lateMinutes: 0 } as any] }],
    });
    expect(s).toEqual({ total: 1, checkedIn: 0, late: 0, onLeave: 0, onTrip: 1 });
  });
});
