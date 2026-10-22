import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import OvertimeForm, { hoursBetween } from './overtime-form';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('OvertimeForm', () => {
  beforeEach(() => {
    mocked.post.mockReset();
  });

  it('gửi ngày, giờ và nội dung', async () => {
    mocked.post.mockResolvedValue({ data: { id: 1, plannedHours: 3 } } as any);
    const done = vi.fn();
    render(<OvertimeForm onSubmitted={done} />);
    fireEvent.change(screen.getByLabelText('Ngày làm'), { target: { value: '24/10/2026' } });
    fireEvent.change(screen.getByLabelText('Giờ bắt đầu'), { target: { value: '08:00' } });
    fireEvent.change(screen.getByLabelText('Giờ kết thúc'), { target: { value: '11:00' } });
    fireEvent.change(screen.getByLabelText('Nội dung công việc'), { target: { value: 'Triển khai' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đăng ký' }));
    await waitFor(() => expect(done).toHaveBeenCalled());
    expect(mocked.post).toHaveBeenCalledWith('api/leave/overtime', {
      workDate: '2026-10-24',
      startTime: '08:00',
      endTime: '11:00',
      content: 'Triển khai',
    });
  });

  it('server báo vượt giới hạn thì hiện câu của server', async () => {
    mocked.post.mockRejectedValue({
      response: { status: 400, data: { message: 'error.overtimelimit', detail: 'Vượt giới hạn 40 giờ/tháng: đã có 38 giờ' } },
    });
    render(<OvertimeForm onSubmitted={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Ngày làm'), { target: { value: '24/10/2026' } });
    fireEvent.change(screen.getByLabelText('Nội dung công việc'), { target: { value: 'Triển khai' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đăng ký' }));
    expect(await screen.findByText(/đã có 38 giờ/)).toBeTruthy();
  });

  it('tính giờ, kể cả qua nửa đêm', () => {
    expect(hoursBetween('18:00', '20:30')).toBe(2.5);
    expect(hoursBetween('22:00', '02:00')).toBe(4);
  });
});
