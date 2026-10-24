import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import MyAttendance from './my-attendance';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const day = (d: string, over: Record<string, unknown> = {}) => ({
  employeeId: 1,
  code: 'NV1',
  name: 'An',
  day: d,
  group: 'VP',
  groupLabel: 'Văn phòng',
  autoKind: null,
  credit: 1,
  actualCredit: 1,
  status: 'DI_LAM',
  in: '08:00',
  out: '17:00',
  lateMinutes: 0,
  earlyMinutes: 0,
  remote: false,
  leavePart: null,
  markedAbsent: false,
  ...over,
});

const TOTALS = {
  standardDays: 21,
  actualDays: 20.5,
  workDays: 19.5,
  paidLeaveDays: 1,
  unpaidLeaveDays: 0,
  sickDays: 0,
  holidayDays: 0,
  tripDays: 0,
  lateCount: 1,
  lateMinutes: 110,
  earlyCount: 0,
  halfDays: 1,
  absentDays: 0,
  missingDays: 1,
};

describe('MyAttendance', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('hiện tổng công, từng ngày, ô đỏ; chỉ đọc', async () => {
    mocked.get.mockResolvedValue({
      data: {
        month: '2026-11',
        status: 'DA_CHOT',
        rows: [
          {
            employee: { id: 1, code: 'NV1', name: 'An' },
            days: [
              day('2026-11-01', { autoKind: 'NGHI', status: null, in: null, out: null, credit: null, actualCredit: 0 }),
              day('2026-11-05', { in: '09:50', lateMinutes: 110, credit: 0.5, actualCredit: 0.5 }),
              day('2026-11-06', { autoKind: 'PHEP_NAM', status: null, in: null, out: null, credit: null }),
              day('2026-11-09', { status: 'CHUA_NHAP', in: null, out: null, credit: null, actualCredit: 0 }),
            ],
            totals: TOTALS,
            changedAfterClose: true,
          },
        ],
      },
    } as any);
    render(<MyAttendance />);
    const totals = within(await screen.findByTestId('my-totals'));
    expect(totals.getByText('20,5')).toBeTruthy();
    expect(totals.getByText('21')).toBeTruthy();
    expect(totals.getByText('1 lần, 110 phút')).toBeTruthy();
    expect(screen.getByText('Đã chốt')).toBeTruthy();
    expect(screen.getByText(/đã thay đổi sau khi chốt/)).toBeTruthy();
    const late = within(screen.getByTestId('my-day-2026-11-05'));
    expect(late.getByText('05/11/2026')).toBeTruthy();
    expect(late.getByText('0,5')).toBeTruthy();
    expect(late.getByText("09:50–17:00 · muộn 110'")).toBeTruthy();
    expect(within(screen.getByTestId('my-day-2026-11-06')).getByText('Nghỉ phép năm')).toBeTruthy();
    expect(screen.getByTestId('my-day-2026-11-09').className).toContain('table-danger');
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/my', { params: { month: expect.stringMatching(/^\d{4}-\d{2}$/) } });
  });

  it('không có dòng: báo không có bảng công', async () => {
    mocked.get.mockResolvedValue({ data: { month: '2026-11', status: 'DANG_MO', rows: [] } } as any);
    render(<MyAttendance />);
    expect(await screen.findByText('Bạn không có bảng công tháng 11/2026.')).toBeTruthy();
  });

  const TWO_DAYS = (extra: Record<string, unknown> = {}) => ({
    month: '2026-10',
    status: 'DANG_MO',
    rows: [
      {
        employee: { id: 1, code: 'NV1', name: 'An' },
        days: [
          day('2026-10-13', {
            status: 'THIEU_GIO',
            out: null,
            credit: null,
            actualCredit: 0,
            punches: [
              { id: 1, kind: 'VAO', time: '08:01', source: 'APP', status: 'CHAP_NHAN', method: 'IP', siteName: 'VP', distanceM: null },
            ],
          }),
          day('2026-10-21', { status: 'CHUA_NHAP', in: null, out: null, credit: null, actualCredit: 0 }),
          day('2026-10-23', {
            pendingCount: 1,
            in: null,
            out: null,
            punches: [
              {
                id: 2,
                kind: 'VAO',
                time: '08:41',
                source: 'APP',
                status: 'CHO_DUYET',
                method: 'GPS',
                distanceM: 140,
                reviewReason: 'Sai số 140 m',
              },
              {
                id: 3,
                kind: 'VAO',
                time: '08:39',
                source: 'APP',
                status: 'TU_CHOI',
                method: 'GPS',
                distanceM: 2400,
                reviewReason: 'Bạn đang cách VP 2,4 km',
                decisionNote: 'Không ở văn phòng',
              },
              { id: 4, kind: 'RA', time: '17:00', source: 'GO_TAY', status: null },
            ],
            ...extra,
          }),
        ],
        totals: TOTALS,
        changedAfterClose: false,
      },
    ],
  });

  const EXPLANATIONS = [
    { id: 9, workDay: '2026-10-21', proposedIn: '08:00', proposedOut: null, reason: 'Quên', status: 'DANG_CHO' },
    {
      id: 8,
      workDay: '2026-10-05',
      proposedIn: null,
      proposedOut: '17:10',
      reason: 'Quên ra',
      status: 'TU_CHOI',
      decisionNote: 'Không đủ căn cứ',
    },
  ];

  const route = (board: unknown, list: unknown = []) =>
    mocked.get.mockImplementation((url: string) => Promise.resolve({ data: url.includes('explanations') ? list : board }) as any);

  it('ô vàng khi có lần chờ duyệt hoặc giải trình đang chờ; chi tiết ngày liệt kê lần chấm kèm nguồn và trạng thái', async () => {
    route(TWO_DAYS(), EXPLANATIONS);
    render(<MyAttendance />);
    const pending = await screen.findByTestId('my-day-2026-10-23');
    expect(pending.className).toContain('table-warning');
    expect(await waitFor(() => screen.getByTestId('my-day-2026-10-21').className)).toContain('table-warning');
    expect(screen.getByTestId('my-day-2026-10-13').className).toContain('table-danger');
    fireEvent.click(within(pending).getByRole('button', { name: '23/10/2026' }));
    const detail = within(screen.getByTestId('my-detail-2026-10-23'));
    expect(detail.getByText(/Vào 08:41/).textContent).toContain('Tự chấm');
    expect(detail.getByText('Chờ duyệt')).toBeTruthy();
    expect(detail.getByText('Từ chối')).toBeTruthy();
    expect(detail.getByText(/Ra 17:00/).textContent).toContain('Gõ tay');
    expect(detail.getByText(/Sai số 140 m/)).toBeTruthy();
    // Lần bị lead từ chối: hiện lý do của lead, không hiện lý do tự động.
    expect(detail.getByText('Lý do: Không ở văn phòng')).toBeTruthy();
    expect(detail.queryByText(/cách VP 2,4 km/)).toBeNull();
  });

  it('nút "Gửi giải trình cho ngày dd/MM" ở ngày thiếu giờ, chưa nhập hoặc có lần bị từ chối', async () => {
    route(TWO_DAYS());
    render(<MyAttendance />);
    expect(await screen.findByRole('button', { name: 'Gửi giải trình cho ngày 13/10' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Gửi giải trình cho ngày 21/10' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Gửi giải trình cho ngày 23/10' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi giải trình cho ngày 13/10' }));
    expect(await screen.findByText(/Giải trình 13\/10\/2026/)).toBeTruthy();
  });

  it('ngày đã chốt hoặc đã có giải trình đang chờ: không có nút gửi', async () => {
    route({ ...TWO_DAYS(), status: 'DA_CHOT' });
    const { unmount } = render(<MyAttendance />);
    await screen.findByTestId('my-day-2026-10-13');
    expect(screen.queryByRole('button', { name: /Gửi giải trình cho ngày/ })).toBeNull();
    unmount();
    route(TWO_DAYS(), EXPLANATIONS);
    render(<MyAttendance />);
    await screen.findByText('Giải trình của tôi');
    await screen.findByText('Quên');
    expect(screen.queryByRole('button', { name: 'Gửi giải trình cho ngày 21/10' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Gửi giải trình cho ngày 13/10' })).toBeTruthy();
  });

  it('"Giải trình của tôi": nút Rút gọi withdraw rồi tải lại', async () => {
    route(TWO_DAYS(), EXPLANATIONS);
    mocked.post.mockResolvedValue({ data: { id: 9, status: 'DA_RUT' } } as any);
    render(<MyAttendance />);
    await screen.findByText('Giải trình của tôi');
    expect(await screen.findByText('Không đủ căn cứ')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: /^Rút giải trình ngày/ })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Rút giải trình ngày 21/10/2026' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận rút' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/explanations/9/withdraw'));
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/explanations/mine', {
      params: { month: expect.stringMatching(/^\d{4}-\d{2}$/) },
    });
  });

  describe('theo ngày hôm nay', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 24, 10, 0));
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    it('mở tháng hiện tại', async () => {
      route(TWO_DAYS());
      render(<MyAttendance />);
      expect(await screen.findByText('Tháng 10/2026')).toBeTruthy();
      expect(mocked.get).toHaveBeenCalledWith('api/attendance/my', { params: { month: '2026-10' } });
      expect(mocked.get).not.toHaveBeenCalledWith('api/attendance/my', { params: { month: '2026-09' } });
    });

    it('chuyển tháng thì ẩn thông báo "Đã gửi giải trình."', async () => {
      route(TWO_DAYS());
      mocked.post.mockResolvedValue({ data: { id: 1 } } as any);
      render(<MyAttendance />);
      fireEvent.click(await screen.findByRole('button', { name: 'Gửi giải trình cho ngày 13/10' }));
      fireEvent.change(await screen.findByLabelText(/Giờ ra/), { target: { value: '17:00' } });
      fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: 'Quên chấm ra' } });
      fireEvent.click(screen.getByRole('button', { name: 'Gửi giải trình' }));
      expect(await screen.findByText('Đã gửi giải trình.')).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: /Tháng trước/ }));
      await waitFor(() => expect(screen.queryByText('Đã gửi giải trình.')).toBeNull());
    });

    it('hôm nay đã vào chưa ra: không có nút gửi giải trình', async () => {
      route({
        ...TWO_DAYS(),
        rows: [{ ...TWO_DAYS().rows[0], days: [day('2026-10-24', { out: null, status: 'THIEU_GIO', credit: null, actualCredit: 0 })] }],
      });
      render(<MyAttendance />);
      await screen.findByTestId('my-day-2026-10-24');
      expect(screen.queryByRole('button', { name: /Gửi giải trình cho ngày/ })).toBeNull();
    });
  });
});
