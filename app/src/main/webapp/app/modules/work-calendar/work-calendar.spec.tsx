import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import WorkCalendar from './work-calendar';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('WorkCalendar', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/work-calendar')
        return Promise.resolve({ data: { workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] } } as any);
      if (url === 'api/holidays') return Promise.resolve({ data: [{ id: 3, day: '2026-09-02', name: 'Quốc khánh' }] } as any);
      return Promise.resolve({ data: {} } as any);
    });
  });

  it('Quản trị tick thêm thứ Bảy rồi lưu: gửi đủ 6 ngày', async () => {
    mocked.put.mockResolvedValue({ data: { workingDays: [] } } as any);
    render(<WorkCalendar canEditWeek canEditHolidays initialYear={2026} />);
    const sat = (await screen.findByLabelText('Thứ Bảy')) as HTMLInputElement;
    expect(sat.checked).toBe(false);
    fireEvent.click(sat);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu ngày làm việc' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/work-calendar', {
        workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
      }),
    );
  });

  it('HR không sửa được ngày làm việc trong tuần', async () => {
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    expect(((await screen.findByLabelText('Thứ Hai')) as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu ngày làm việc' })).toBeNull();
  });

  it('hiện ngày lễ của năm, thêm ngày lễ gửi ngày ISO', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    expect(await screen.findByText('02/09/2026')).toBeTruthy();
    expect(screen.getByText('Quốc khánh')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/holidays', { params: { year: 2026 } });
    fireEvent.change(screen.getByLabelText('Ngày lễ (dd/MM/yyyy)'), { target: { value: '01/01/2026' } });
    fireEvent.change(screen.getByLabelText('Tên ngày lễ'), { target: { value: 'Tết Dương lịch' } });
    fireEvent.click(screen.getByRole('button', { name: 'Thêm ngày lễ' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/holidays', { day: '2026-01-01', name: 'Tết Dương lịch' }));
  });

  it('chép ngày lễ từ năm trước', async () => {
    mocked.post.mockResolvedValue({ data: [] } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Chép từ năm 2025' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/holidays/copy', null, { params: { fromYear: 2025, toYear: 2026 } }));
  });
});
