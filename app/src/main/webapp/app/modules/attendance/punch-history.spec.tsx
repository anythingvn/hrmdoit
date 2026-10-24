import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import PunchHistory from './punch-history';

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
  punches: [],
  pendingCount: 0,
  ...over,
});

const totals = {
  standardDays: 22,
  actualDays: 20.5,
  workDays: 20.5,
  paidLeaveDays: 0,
  unpaidLeaveDays: 0,
  sickDays: 0,
  holidayDays: 0,
  tripDays: 0,
  lateCount: 1,
  lateMinutes: 80,
  earlyCount: 0,
  halfDays: 1,
  absentDays: 0,
  missingDays: 1,
};

const board = (status: 'DANG_MO' | 'DA_CHOT' = 'DANG_MO') => ({
  month: '2026-10',
  status,
  rows: [
    {
      employee: { id: 1, code: 'NV1', name: 'An' },
      self: true,
      changedAfterClose: false,
      pendingDays: 0,
      totals,
      days: [
        day('2026-10-21'),
        day('2026-10-22', {
          in: '09:50',
          credit: 0.5,
          actualCredit: 0.5,
          lateMinutes: 80,
          punches: [
            {
              id: 7,
              kind: 'VAO',
              time: '09:50',
              source: 'APP',
              status: 'CHAP_NHAN',
              method: 'GPS',
              siteName: 'Văn phòng DOIT',
              distanceM: 35,
            },
          ],
        }),
        day('2026-10-23', { out: null, status: 'THIEU_GIO', credit: null, actualCredit: 0 }),
        day('2026-10-24', { autoKind: 'NGHI', status: null, in: null, out: null, credit: null, actualCredit: 0 }),
        day('2026-10-26', { status: 'CHUA_NHAP', in: null, out: null, credit: null, actualCredit: 0 }),
      ],
    },
  ],
});

const routes = (data: unknown) =>
  mocked.get.mockImplementation((url: string) => Promise.resolve({ data: url.includes('explanations') ? [] : data }) as any);

describe('PunchHistory', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 24, 10, 0));
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('mở tháng hiện tại, dòng tiêu đề "Tháng 10/2026", gọi API của Bảng công của tôi', async () => {
    routes(board());
    render(<PunchHistory />);
    expect(await screen.findByText('Tháng 10/2026')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/my', { params: { month: '2026-10' } });
  });

  it('dòng tổng: công thực tế / công chuẩn, đi muộn, thiếu giờ, chờ duyệt', async () => {
    routes(board());
    render(<PunchHistory />);
    const summary = await screen.findByTestId('history-summary');
    expect(summary.textContent).toContain('Công thực tế 20,5 / 22');
    expect(summary.textContent).toContain('Đi muộn 1');
    expect(summary.textContent).toContain('Thiếu giờ 1');
    expect(summary.textContent).toContain('Chờ duyệt 0');
    expect(summary.textContent).not.toContain('giờ công');
  });

  it('mỗi ngày một dòng: giờ vào → ra · công, nhãn muộn, nơi chấm và khoảng cách; không hiện ngày chưa tới, ngày nghỉ trống', async () => {
    routes(board());
    render(<PunchHistory />);
    const row = within(await screen.findByTestId('history-day-2026-10-22'));
    expect(row.getByText('09:50 → 17:00 · 0,5 công')).toBeTruthy();
    expect(row.getByText("Muộn 80'")).toBeTruthy();
    expect(row.getByText(/Văn phòng DOIT · cách 35 m/)).toBeTruthy();
    expect(row.getByText(/Thứ Năm/)).toBeTruthy();
    expect(row.queryByRole('button', { name: /Giải trình/ })).toBeNull();
    expect(screen.queryByTestId('history-day-2026-10-26')).toBeNull();
    expect(screen.queryByTestId('history-day-2026-10-24')).toBeNull();
    expect(within(screen.getByTestId('history-day-2026-10-21')).queryByRole('button', { name: /Giải trình/ })).toBeNull();
  });

  it('ngày thiếu giờ ra có nút Giải trình, mở hộp giải trình của ngày đó', async () => {
    routes(board());
    render(<PunchHistory />);
    const row = within(await screen.findByTestId('history-day-2026-10-23'));
    expect(row.getByText('Thiếu giờ ra')).toBeTruthy();
    fireEvent.click(row.getByRole('button', { name: 'Giải trình ngày 23/10' }));
    expect(await screen.findByText('Giải trình 23/10/2026')).toBeTruthy();
  });

  it('closedMonthHistoryNoExplain: tháng đã chốt không có nút Giải trình', async () => {
    routes(board('DA_CHOT'));
    render(<PunchHistory />);
    await screen.findByTestId('history-day-2026-10-23');
    expect(screen.queryByRole('button', { name: /Giải trình/ })).toBeNull();
  });

  it('chuyển tháng trước gọi lại API với tháng 09', async () => {
    routes(board());
    render(<PunchHistory />);
    await screen.findByText('Tháng 10/2026');
    fireEvent.click(screen.getByRole('button', { name: 'Tháng trước' }));
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/attendance/my', { params: { month: '2026-09' } }));
    expect(screen.getByText('Tháng 09/2026')).toBeTruthy();
  });

  it('ngày có lần chấm chờ duyệt hoặc bị từ chối: nhãn và nút Giải trình', async () => {
    const b = board();
    b.rows[0].pendingDays = 1;
    b.rows[0].days[0] = day('2026-10-21', {
      pendingCount: 1,
      punches: [{ id: 8, kind: 'RA', time: '17:00', source: 'APP', status: 'TU_CHOI', method: 'GPS', siteName: 'KH A', distanceM: 2400 }],
    });
    routes(b);
    render(<PunchHistory />);
    const row = within(await screen.findByTestId('history-day-2026-10-21'));
    expect(row.getByText('Chờ duyệt')).toBeTruthy();
    expect(row.getByText('Bị từ chối')).toBeTruthy();
    expect(row.getByText(/KH A · cách 2,4 km/)).toBeTruthy();
    expect(row.getByRole('button', { name: 'Giải trình ngày 21/10' })).toBeTruthy();
    expect(screen.getByTestId('history-summary').textContent).toContain('Chờ duyệt 1');
  });
});
