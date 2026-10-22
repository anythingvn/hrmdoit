import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import ApprovalInbox from './approval-inbox';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const leave = (id: number, extra: object = {}) => ({
  id,
  employeeId: 10 + id,
  employeeCode: `NV${id}`,
  employeeName: `Người ${id}`,
  type: 'PHEP_NAM',
  fromDate: '2026-11-02',
  toDate: '2026-11-03',
  firstPart: 'CA_NGAY',
  lastPart: 'CA_NGAY',
  days: 2,
  reasonText: 'Việc nhà',
  status: 'CHO_DUYET',
  approverKind: 'TRUONG_NHOM',
  balance: { annualTotal: 6.5, annualRequestable: 7.5, compensatoryHours: 12 },
  ...extra,
});

const overtime = {
  id: 31,
  employeeId: 40,
  employeeCode: 'NV40',
  employeeName: 'Người làm thêm',
  workDate: '2026-10-24',
  startTime: '08:00:00',
  endTime: '12:00:00',
  plannedHours: 4,
  rate: 200,
  night: false,
  content: 'Triển khai hệ thống',
  status: 'CHO_DUYET',
};

const routes = (leaves: object[], ots: object[]) =>
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/leave/requests/inbox') return Promise.resolve({ data: leaves } as any);
    if (url === 'api/leave/overtime/inbox') return Promise.resolve({ data: ots } as any);
    return Promise.resolve({ data: [] } as any);
  });

describe('ApprovalInbox', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.post.mockResolvedValue({ data: {} } as any);
  });

  it('hiện số dư người xin trước khi duyệt', async () => {
    routes([leave(1)], []);
    render(<ApprovalInbox />);
    const row = await screen.findByTestId('leave-1');
    expect(row.textContent).toContain('Người 1');
    expect(row.textContent).toContain('6,5 ngày');
    expect(row.textContent).toContain('12 giờ');
  });

  it('duyệt làm thêm giờ bắt buộc chọn hình thức', async () => {
    routes([], [overtime]);
    render(<ApprovalInbox />);
    const row = await screen.findByTestId('ot-31');
    fireEvent.click(within(row).getByRole('button', { name: 'Duyệt' }));
    expect(await within(row).findByText(/Chọn Trả tiền hoặc Nghỉ bù/)).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.click(within(row).getByLabelText('Nghỉ bù'));
    fireEvent.click(within(row).getByRole('button', { name: 'Duyệt' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/overtime/31/approve', { compensation: 'NGHI_BU' }));
  });

  it('từ chối bắt buộc ghi lý do', async () => {
    routes([leave(1)], []);
    render(<ApprovalInbox />);
    const row = await screen.findByTestId('leave-1');
    fireEvent.click(within(row).getByRole('button', { name: 'Từ chối' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
    expect(await within(dialog).findByText(/Phải ghi lý do/)).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByLabelText('Lý do từ chối'), { target: { value: 'Dự án đang gấp' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/1/reject', { note: 'Dự án đang gấp' }));
  });

  it('duyệt nhiều đơn nghỉ một lần và báo đơn lỗi', async () => {
    routes([leave(1), leave(2)], []);
    mocked.post.mockResolvedValue({
      data: [
        { id: 1, ok: true },
        { id: 2, ok: false, errorKey: 'insufficientbalance', message: 'Không đủ số dư để duyệt' },
      ],
    } as any);
    render(<ApprovalInbox />);
    await screen.findByTestId('leave-2');
    fireEvent.click(screen.getByLabelText('Chọn đơn của Người 1'));
    fireEvent.click(screen.getByLabelText('Chọn đơn của Người 2'));
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt 2 đơn đã chọn' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/bulk-approve', { ids: [1, 2] }));
    expect(await screen.findByText(/Không đủ số dư để duyệt/)).toBeTruthy();
  });
});
