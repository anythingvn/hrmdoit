import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import LeaveCalendarPage from './leave-calendar-page';
import { currentMonth, shiftMonth } from './leave-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const calendarCalls = () => mocked.get.mock.calls.filter(c => c[0] === 'api/leave/calendar').map(c => (c[1] as any)?.params);

describe('LeaveCalendarPage', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/leave/calendar/departments')
        return Promise.resolve({
          data: [
            { id: 3, name: 'Kinh doanh' },
            { id: 4, name: 'Kỹ thuật' },
          ],
        } as any);
      if (url === 'api/leave/calendar')
        return Promise.resolve({
          data: {
            days: [],
            items: [
              {
                employeeId: 9,
                employeeName: 'Nguyễn An',
                department: 'Kinh doanh',
                type: 'OM',
                from: '2026-10-05',
                to: '2026-10-05',
                pending: true,
              },
            ],
          },
        } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('gọi lịch theo tháng hiện tại, lọc phòng và loại, chuyển tháng', async () => {
    render(<LeaveCalendarPage />);
    const month = currentMonth();
    await waitFor(() => expect(calendarCalls()).toContainEqual({ month }));
    expect(await screen.findByRole('option', { name: 'Kỹ thuật' })).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Phòng ban'), { target: { value: '4' } });
    await waitFor(() => expect(calendarCalls()).toContainEqual({ month, departmentId: 4 }));

    fireEvent.change(screen.getByLabelText('Loại'), { target: { value: 'OM' } });
    await waitFor(() => expect(calendarCalls()).toContainEqual({ month, departmentId: 4, type: 'OM' }));

    fireEvent.click(screen.getByRole('button', { name: 'Tháng sau' }));
    await waitFor(() => expect(calendarCalls()).toContainEqual({ month: shiftMonth(month, 1), departmentId: 4, type: 'OM' }));
  });

  it('lỗi tải lịch thì báo lỗi', async () => {
    mocked.get.mockImplementation((url: string) =>
      url === 'api/leave/calendar'
        ? Promise.reject(Object.assign(new Error('403'), { response: { status: 403, data: {} } }))
        : Promise.resolve({ data: [] } as any),
    );
    render(<LeaveCalendarPage />);
    expect(await screen.findByRole('alert')).toBeTruthy();
  });
});
