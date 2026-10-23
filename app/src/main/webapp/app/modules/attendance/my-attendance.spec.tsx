import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen, within } from '@testing-library/react';

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
  beforeEach(() => mocked.get.mockReset());

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
});
