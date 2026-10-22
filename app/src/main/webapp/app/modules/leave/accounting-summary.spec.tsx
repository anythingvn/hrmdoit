import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import AccountingSummary from './accounting-summary';
import { currentMonth, shiftMonth } from './leave-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const row = (code: string, extra: Record<string, unknown> = {}) => ({
  employeeId: code.length,
  code,
  fullName: `Người ${code}`,
  departmentName: 'Kinh doanh',
  status: 'Đang làm việc',
  annualDays: 0,
  personalDays: 0,
  sickDays: 0,
  unpaidDays: 0,
  compensatoryDays: 0,
  ot150Hours: 0,
  ot200Hours: 0,
  ot300Hours: 0,
  nightCount: 0,
  settlementDays: null,
  settlementCompensatoryHours: null,
  ...extra,
});

describe('AccountingSummary', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockImplementation((url: string, cfg: any) =>
      Promise.resolve({
        data: {
          month: cfg?.params?.month,
          rows: [
            row('NV01', { annualDays: 2, unpaidDays: 1, ot150Hours: 3, nightCount: 1 }),
            row('NV02', { status: 'Đã nghỉ', settlementDays: 2.5 }),
            row('NV03', { status: 'Đã nghỉ', settlementDays: -1, archived: true }),
            row('NV04', { settlementDays: -2.5, settlementReversal: true }),
          ],
        },
      } as any),
    );
  });

  it('tải theo tháng, hiện số liệu và cách xử lý phép khi nghỉ việc', async () => {
    render(<AccountingSummary />);
    const month = currentMonth();
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/leave/accounting', { params: { month } }));
    const first = await screen.findByTestId('acc-NV01');
    expect(
      within(first)
        .getAllByRole('cell')
        .map(c => c.textContent),
    ).toContain('3');
    expect(within(screen.getByTestId('acc-NV02')).getByText(/Trả tiền phép chưa nghỉ/)).toBeTruthy();
    expect(within(screen.getByTestId('acc-NV03')).getByText(/Trừ lương phép ứng/)).toBeTruthy();
    expect(within(screen.getByTestId('acc-NV04')).getByText(/Hoàn tác nghỉ việc/)).toBeTruthy();
    // Hồ sơ đã lưu trữ vẫn hiện ở tháng cũ, kèm nhãn.
    expect(within(screen.getByTestId('acc-NV03')).getByText('Lưu trữ')).toBeTruthy();
    expect(within(screen.getByTestId('acc-NV02')).queryByText('Lưu trữ')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Tháng trước' }));
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/leave/accounting', { params: { month: shiftMonth(month, -1) } }));
  });
});
