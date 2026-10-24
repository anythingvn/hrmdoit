import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import MyLeave from './my-leave';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const balance = {
  employeeId: 7,
  annual: {
    previousYear: 2,
    currentYear: 4.5,
    total: 6.5,
    requestable: 7.5,
    previousYearExpiry: '2027-03-31',
    baseAnnualDays: 12,
    seniorityYears: 5,
    seniorityExtraDays: 1,
    annualDays: 13,
    advanceDays: 2,
  },
  compensatory: { previousYear: 0, currentYear: 12, total: 12 },
  sick: { usedDays: 2, limitDays: 30 },
  overtime: { monthHours: 6, monthCap: 40 },
};

const mine = [
  {
    id: 3,
    employeeId: 7,
    type: 'PHEP_NAM',
    fromDate: '2099-11-02',
    toDate: '2099-11-04',
    firstPart: 'CA_NGAY',
    lastPart: 'CA_NGAY',
    days: 2,
    status: 'CHO_DUYET',
    approverName: 'Trưởng nhóm A',
  },
];

const routes = (balanceResponse: Promise<any>) =>
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/leave/balances/me') return balanceResponse;
    if (url === 'api/leave/requests/mine') return Promise.resolve({ data: mine } as any);
    if (url === 'api/leave/overtime/mine') return Promise.resolve({ data: [] } as any);
    if (url === 'api/leave/ledger/7')
      return Promise.resolve({
        data: [{ id: 1, pool: 'PHEP_NAM', bucket: 'NAM_NAY', year: 2026, amount: 1, effectiveDate: '2026-10-01', reason: 'CONG_THANG' }],
      } as any);
    if (url === 'api/leave/calendar')
      return Promise.resolve({
        data: {
          days: [],
          items: [
            { employeeId: 7, employeeName: 'Tôi', type: 'PHEP_NAM', from: '2026-10-05', to: '2026-10-05', pending: false },
            { employeeId: 8, employeeName: 'Người khác', type: 'OM', from: '2026-10-05', to: '2026-10-05', pending: false },
          ],
        },
      } as any);
    return Promise.resolve({ data: [] } as any);
  });

describe('MyLeave', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('hiện số dư, đơn đã gửi với số ngày của server, sổ phép và chỉ lịch của mình', async () => {
    routes(Promise.resolve({ data: balance }));
    render(<MyLeave />);
    expect(await screen.findByText('Phép năm còn lại')).toBeTruthy();
    expect(screen.getByText(/Trong đó 2 ngày năm trước, dùng đến 31\/03\/2027/)).toBeTruthy();
    expect(screen.getByText('Phép năm: 12 + 1 (thâm niên 5 năm) = 13 ngày')).toBeTruthy();
    expect(screen.getByText(/Được xin tối đa 7,5 ngày \(gồm 2 ngày ứng\)|Được xin tối đa 7.5 ngày \(gồm 2 ngày ứng\)/)).toBeTruthy();
    const row = await screen.findByTestId('mine-3');
    expect(row.textContent).toContain('02/11/2099 – 04/11/2099');
    expect(within(row).getByText('2')).toBeTruthy();
    expect(screen.getByText('Cộng phép tháng')).toBeTruthy();
    expect(screen.queryByText(/Người khác/)).toBeNull();
  });

  it('rút đơn đang chờ', async () => {
    routes(Promise.resolve({ data: balance }));
    mocked.post.mockResolvedValue({ data: {} } as any);
    render(<MyLeave />);
    const row = await screen.findByTestId('mine-3');
    fireEvent.click(within(row).getByRole('button', { name: 'Rút đơn' }));
    // Hỏi lại trước khi rút.
    const dialog = await screen.findByRole('dialog');
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận rút đơn' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/3/withdraw', {}));
  });

  it('xin hủy đơn đã duyệt cũng hỏi lại', async () => {
    mine[0] = { ...mine[0], status: 'DA_DUYET' };
    routes(Promise.resolve({ data: balance }));
    mocked.post.mockResolvedValue({ data: {} } as any);
    render(<MyLeave />);
    const row = await screen.findByTestId('mine-3');
    fireEvent.click(within(row).getByRole('button', { name: 'Xin hủy' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Đóng' }));
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.click(within(row).getByRole('button', { name: 'Xin hủy' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Gửi yêu cầu hủy' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/3/cancel-request', {}));
    mine[0] = { ...mine[0], status: 'CHO_DUYET' };
  });

  it('người không thuộc diện nghỉ phép thấy thông báo', async () => {
    routes(Promise.reject(Object.assign(new Error('400'), { response: { status: 400, data: { message: 'error.leavenotapplicable' } } })));
    render(<MyLeave />);
    expect(await screen.findByText(/không thuộc diện dùng phần nghỉ phép/)).toBeTruthy();
  });
});
