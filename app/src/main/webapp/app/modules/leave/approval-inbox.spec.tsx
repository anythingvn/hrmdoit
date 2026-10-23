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

const pendingItem = (type: 'PUNCH' | 'EXPLANATION', id: number, name: string, extra: object = {}) => ({
  type,
  id,
  employeeId: 50 + id,
  employeeCode: `NV${50 + id}`,
  employeeName: name,
  day: '2026-11-19',
  summary: type === 'PUNCH' ? 'Vào 08:20 · GPS ±140 m' : 'Giải trình vào 08:00',
  detail: type === 'PUNCH' ? 'Cách văn phòng 210 m' : 'Kẹt xe',
  ...extra,
});

const routes = (leaves: object[], ots: object[], pending: object[] = []) =>
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/leave/requests/inbox') return Promise.resolve({ data: leaves } as any);
    if (url === 'api/leave/overtime/inbox') return Promise.resolve({ data: ots } as any);
    if (url === 'api/attendance/pending') return Promise.resolve({ data: pending } as any);
    return Promise.resolve({ data: [] } as any);
  });

describe('ApprovalInbox: mỗi phần tải riêng', () => {
  beforeEach(() => {
    mocked.get.mockReset();
  });

  it('chấm công lỗi: đơn nghỉ và làm thêm giờ vẫn hiện, phần chấm công báo lỗi riêng (không dùng câu của đơn nghỉ)', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/leave/requests/inbox') return Promise.resolve({ data: [leave(1)] } as any);
      if (url === 'api/leave/overtime/inbox') return Promise.resolve({ data: [overtime] } as any);
      if (url === 'api/attendance/pending') return Promise.reject(Object.assign(new Error('500'), { response: { status: 500, data: {} } }));
      return Promise.resolve({ data: [] } as any);
    });
    render(<ApprovalInbox />);
    expect(await screen.findByTestId('leave-1')).toBeTruthy();
    expect(screen.getByTestId('ot-31')).toBeTruthy();
    expect(await screen.findByText('Không tải được danh sách chấm công chờ duyệt')).toBeTruthy();
    expect(screen.queryByText('Không tải được đơn chờ duyệt')).toBeNull();
  });

  it('đơn nghỉ lỗi: chấm công vẫn hiện', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/leave/requests/inbox')
        return Promise.reject(Object.assign(new Error('500'), { response: { status: 500, data: {} } }));
      if (url === 'api/attendance/pending') return Promise.resolve({ data: [pendingItem('PUNCH', 7, 'Chị Hoa')] } as any);
      return Promise.resolve({ data: [] } as any);
    });
    render(<ApprovalInbox />);
    expect(await screen.findByTestId('att-PUNCH-7')).toBeTruthy();
    expect(await screen.findByText('Không tải được đơn chờ duyệt')).toBeTruthy();
  });
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

  it('từ chối nhiều đơn nghỉ một lần, bắt buộc lý do, báo đơn lỗi', async () => {
    routes([leave(1), leave(2)], []);
    render(<ApprovalInbox />);
    await screen.findByTestId('leave-2');
    fireEvent.click(screen.getByLabelText('Chọn đơn của Người 1'));
    fireEvent.click(screen.getByLabelText('Chọn đơn của Người 2'));
    fireEvent.click(screen.getByRole('button', { name: 'Từ chối các đơn đã chọn' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Từ chối 2 đơn đã chọn')).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
    expect(await within(dialog).findByText(/Phải ghi lý do/)).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    mocked.post.mockResolvedValue({
      data: [
        { id: 1, ok: true },
        { id: 2, ok: false, errorKey: 'error.leaverequeststatus', message: 'Đơn đã được xử lý' },
      ],
    } as any);
    fireEvent.change(within(dialog).getByLabelText('Lý do từ chối'), { target: { value: 'Dự án gấp' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/leave/requests/bulk-reject', { ids: [1, 2], note: 'Dự án gấp' }));
    expect(await screen.findByText('Đã từ chối 1/2 đơn.')).toBeTruthy();
    expect(screen.getByText(/Đơn đã được xử lý/)).toBeTruthy();
  });

  describe('bấm tên người xin xem nghỉ phép', () => {
    const personLeave = {
      balance: {
        employeeId: 11,
        annual: { previousYear: 0, currentYear: 4, total: 4, requestable: 5 },
        compensatory: { previousYear: 0, currentYear: 0, total: 0 },
      },
      requests: [],
      ledger: [{ id: 1, pool: 'PHEP_NAM', bucket: 'NAM_NAY', year: 2026, amount: 4, effectiveDate: '2026-10-01', reason: 'DAU_KY' }],
    };

    const withPerson = (status: number | null) =>
      mocked.get.mockImplementation((url: string) => {
        if (url === 'api/leave/requests/inbox') return Promise.resolve({ data: [leave(1)] } as any);
        if (url === 'api/leave/overtime/inbox') return Promise.resolve({ data: [overtime] } as any);
        if (url === 'api/leave/employees/11' || url === 'api/leave/employees/40')
          return status === null
            ? Promise.resolve({ data: personLeave } as any)
            : Promise.reject(Object.assign(new Error(String(status)), { response: { status, data: {} } }));
        return Promise.resolve({ data: [] } as any);
      });

    it('trưởng nhóm mở hộp thoại và thấy số dư, sổ phép', async () => {
      withPerson(null);
      render(<ApprovalInbox />);
      fireEvent.click(await screen.findByRole('button', { name: 'Người 1' }));
      const dialog = await screen.findByRole('dialog');
      expect(within(dialog).getByText('Nghỉ phép: Người 1')).toBeTruthy();
      expect(await within(dialog).findByText('Phép năm còn lại')).toBeTruthy();
      expect(within(dialog).getByText('Số dư đầu kỳ')).toBeTruthy();
      expect(mocked.get).toHaveBeenCalledWith('api/leave/employees/11');
    });

    it('server trả 403 thì báo không có quyền (cả đơn làm thêm)', async () => {
      withPerson(403);
      render(<ApprovalInbox />);
      fireEvent.click(await screen.findByRole('button', { name: 'Người làm thêm' }));
      const dialog = await screen.findByRole('dialog');
      expect(await within(dialog).findByText('Bạn không có quyền xem nghỉ phép của người này')).toBeTruthy();
      expect(mocked.get).toHaveBeenCalledWith('api/leave/employees/40');
    });
  });

  describe('Chấm công chờ duyệt', () => {
    const PENDING = [pendingItem('PUNCH', 5, 'Lan'), pendingItem('PUNCH', 6, 'Mai'), pendingItem('EXPLANATION', 9, 'Nam')];

    it('có mục "Chấm công chờ duyệt (3)" với 2 lần chấm và 1 giải trình', async () => {
      routes([], [], PENDING);
      render(<ApprovalInbox />);
      expect(await screen.findByRole('heading', { name: 'Chấm công chờ duyệt (3)' })).toBeTruthy();
      expect(mocked.get).toHaveBeenCalledWith('api/attendance/pending');
      const punch = within(screen.getByTestId('att-PUNCH-5'));
      expect(punch.getByText('Lan')).toBeTruthy();
      expect(punch.getByText(/19\/11\/2026/)).toBeTruthy();
      expect(punch.getByText('Vào 08:20 · GPS ±140 m')).toBeTruthy();
      expect(punch.getByText('Cách văn phòng 210 m')).toBeTruthy();
      expect(punch.getByRole('button', { name: 'Duyệt' })).toBeTruthy();
      const expl = within(screen.getByTestId('att-EXPLANATION-9'));
      expect(expl.getByRole('button', { name: 'Đồng ý' })).toBeTruthy();
      expect(expl.queryByRole('button', { name: 'Duyệt' })).toBeNull();
    });

    it('Duyệt lần chấm và Đồng ý giải trình gọi đúng endpoint', async () => {
      routes([], [], PENDING);
      render(<ApprovalInbox />);
      fireEvent.click(within(await screen.findByTestId('att-PUNCH-5')).getByRole('button', { name: 'Duyệt' }));
      await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches/5/approve'));
      fireEvent.click(within(screen.getByTestId('att-EXPLANATION-9')).getByRole('button', { name: 'Đồng ý' }));
      await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/explanations/9/approve'));
    });

    it('Từ chối bắt buộc lý do; gửi note tới đúng endpoint', async () => {
      routes([], [], PENDING);
      render(<ApprovalInbox />);
      fireEvent.click(within(await screen.findByTestId('att-EXPLANATION-9')).getByRole('button', { name: 'Từ chối' }));
      const dialog = await screen.findByRole('dialog');
      fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
      expect(await within(dialog).findByText(/Phải ghi lý do/)).toBeTruthy();
      expect(mocked.post).not.toHaveBeenCalled();
      fireEvent.change(within(dialog).getByLabelText('Lý do từ chối'), { target: { value: 'Không có bằng chứng' } });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
      await waitFor(() =>
        expect(mocked.post).toHaveBeenCalledWith('api/attendance/explanations/9/reject', { note: 'Không có bằng chứng' }),
      );
    });

    it('lần chấm bị từ chối gọi punches/{id}/reject; lỗi hiện trên thẻ', async () => {
      routes([], [], PENDING);
      mocked.post.mockRejectedValueOnce({ response: { status: 400, data: { message: 'error.attendancepunchnotpending' } } });
      render(<ApprovalInbox />);
      fireEvent.click(within(await screen.findByTestId('att-PUNCH-6')).getByRole('button', { name: 'Duyệt' }));
      expect(await screen.findByText('Lần chấm này không còn chờ duyệt')).toBeTruthy();
      mocked.post.mockResolvedValue({ data: {} } as any);
      fireEvent.click(within(screen.getByTestId('att-PUNCH-6')).getByRole('button', { name: 'Từ chối' }));
      const dialog = await screen.findByRole('dialog');
      fireEvent.change(within(dialog).getByLabelText('Lý do từ chối'), { target: { value: 'Sai nơi' } });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }));
      await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches/6/reject', { note: 'Sai nơi' }));
    });

    it('không có mục nào thì báo trống', async () => {
      routes([], [], []);
      render(<ApprovalInbox />);
      expect(await screen.findByText('Không có lần chấm hay giải trình nào chờ bạn duyệt.')).toBeTruthy();
    });
  });
});
