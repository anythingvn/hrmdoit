import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import ReminderStatus from './reminder-status';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('ReminderStatus', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('chưa cấu hình SMTP thì cảnh báo', async () => {
    mocked.get.mockResolvedValue({ data: { mailConfigured: false, lastRun: null, missingRecipients: [] } } as any);
    render(<ReminderStatus />);
    expect(await screen.findByText(/Chưa cấu hình máy chủ gửi email/)).toBeTruthy();
  });

  it('hiện lần gửi gần nhất, người thiếu email và gửi thử', async () => {
    mocked.get.mockResolvedValue({
      data: {
        mailConfigured: true,
        lastRun: { runDate: '2026-10-22', startedAt: '2026-10-22T00:30:00Z', automatic: true, recipients: 4, items: 3, error: null },
        missingRecipients: ['Quản lý Lê Văn A (DOIT2401)'],
      },
    } as any);
    mocked.post.mockResolvedValue({ data: { sentTo: 'qt@doit.vn' } } as any);
    render(<ReminderStatus />);
    expect(await screen.findByText(/22\/10\/2026 07:30/)).toBeTruthy();
    expect(screen.getByText(/4 người nhận, 3 mục/)).toBeTruthy();
    expect(screen.getByText('Quản lý Lê Văn A (DOIT2401)')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi thử ngay' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/admin/reminders/test-send'));
    expect(await screen.findByText(/Đã gửi thử tới qt@doit.vn/)).toBeTruthy();
  });
});
