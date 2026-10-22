import { vi } from 'vitest';
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import MonthCalendar from './month-calendar';
import { ICalendarDay, ICalendarItem } from './leave-api';

const days: ICalendarDay[] = Array.from({ length: 31 }, (_, i) => {
  const date = `2026-10-${String(i + 1).padStart(2, '0')}`;
  const w = new Date(`${date}T00:00:00Z`).getUTCDay();
  return { date, weekend: w === 0 || w === 6, holidayName: null };
});
days[19] = { ...days[19], holidayName: 'Ngày Phụ nữ Việt Nam' };

const item = (name: string, extra: Partial<ICalendarItem> = {}): ICalendarItem => ({
  employeeId: name.length,
  employeeName: name,
  type: 'PHEP_NAM',
  from: '2026-10-05',
  to: '2026-10-05',
  pending: false,
  ...extra,
});

describe('MonthCalendar', () => {
  it('5 người cùng ngày: hiện 3 người và "+2 người", bấm vào xem đủ danh sách', () => {
    const onDayClick = vi.fn();
    const items = ['An', 'Bình', 'Chi', 'Dũng', 'Giang'].map(n => item(n));
    render(<MonthCalendar month="2026-10" days={days} items={items} onDayClick={onDayClick} />);
    const cell = screen.getByTestId('day-2026-10-05');
    expect(cell.textContent).toContain('An · Phép');
    expect(cell.textContent).toContain('Chi · Phép');
    expect(cell.textContent).not.toContain('Dũng');
    fireEvent.click(screen.getByRole('button', { name: /\+2 người/ }));
    expect(onDayClick).toHaveBeenCalledWith('2026-10-05', expect.any(Array));
    expect(screen.getByRole('dialog').textContent).toContain('Giang · Phép');
  });

  it('đơn chờ duyệt hiện mờ; màu theo loại', () => {
    render(
      <MonthCalendar
        month="2026-10"
        days={days}
        items={[item('An', { pending: true, type: 'OM' }), item('Bình', { type: 'NGHI_VIEC', employeeId: 99 })]}
      />,
    );
    const pending = screen.getByText('An · Ốm');
    expect(pending.className).toContain('is-pending');
    expect(screen.getByText('Bình · Nghỉ việc').className).not.toContain('is-pending');
  });

  it('ngày lễ hiện tên và nền xám như cuối tuần; đơn nhiều ngày trải trên từng ngày', () => {
    render(<MonthCalendar month="2026-10" days={days} items={[item('An', { from: '2026-10-08', to: '2026-10-09' })]} />);
    const holiday = screen.getByTestId('day-2026-10-20');
    expect(holiday.textContent).toContain('Ngày Phụ nữ Việt Nam');
    expect(holiday.className).toContain('is-off');
    expect(screen.getByTestId('day-2026-10-03').className).toContain('is-off');
    expect(screen.getByTestId('day-2026-10-05').className).not.toContain('is-off');
    expect(screen.getByTestId('day-2026-10-08').textContent).toContain('An · Phép');
    expect(screen.getByTestId('day-2026-10-09').textContent).toContain('An · Phép');
  });

  it('lưới bắt đầu từ Thứ Hai và chuyển tháng', () => {
    const onMonthChange = vi.fn();
    render(<MonthCalendar month="2026-10" days={days} items={[]} onMonthChange={onMonthChange} />);
    expect(screen.getAllByRole('columnheader').map(h => h.textContent)).toEqual(['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']);
    expect(screen.getByText('Tháng 10/2026')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Tháng sau' }));
    expect(onMonthChange).toHaveBeenCalledWith('2026-11');
    fireEvent.click(screen.getByRole('button', { name: 'Tháng trước' }));
    expect(onMonthChange).toHaveBeenCalledWith('2026-09');
  });
});
