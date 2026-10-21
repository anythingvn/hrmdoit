import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import MailSettings from './mail-settings';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const fromEnv = {
  host: 'mail.doit.com.vn',
  port: 587,
  security: 'STARTTLS',
  username: 'admin@doit.com.vn',
  passwordSet: true,
  fromAddress: 'admin@doit.com.vn',
  fromName: null,
  source: 'ENV',
  configured: true,
  keyAvailable: true,
};

describe('MailSettings', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
  });

  it('hiện cấu hình đang dùng; lưu không gửi mật khẩu khi để trống', async () => {
    mocked.get.mockResolvedValue({ data: fromEnv } as any);
    mocked.put.mockResolvedValue({ data: { ...fromEnv, source: 'DATABASE', fromName: 'Nhân sự DOIT' } } as any);
    render(<MailSettings />);
    expect(await screen.findByText(/Đang dùng cấu hình trong .env/)).toBeTruthy();
    expect((screen.getByLabelText('Máy chủ SMTP') as HTMLInputElement).value).toBe('mail.doit.com.vn');
    expect((screen.getByLabelText('Mật khẩu') as HTMLInputElement).placeholder).toMatch(/Đã đặt mật khẩu/);
    fireEvent.change(screen.getByLabelText('Tên hiển thị'), { target: { value: 'Nhân sự DOIT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/admin/mail-settings', {
        host: 'mail.doit.com.vn',
        port: 587,
        security: 'STARTTLS',
        username: 'admin@doit.com.vn',
        password: null,
        clearPassword: false,
        fromAddress: 'admin@doit.com.vn',
        fromName: 'Nhân sự DOIT',
      }),
    );
    expect(await screen.findByText(/Đang dùng cấu hình lưu trên giao diện/)).toBeTruthy();
  });

  it('nhập mật khẩu mới thì gửi; gửi thử và trở lại cấu hình .env', async () => {
    mocked.get.mockResolvedValue({ data: { ...fromEnv, source: 'DATABASE' } } as any);
    mocked.put.mockResolvedValue({ data: { ...fromEnv, source: 'DATABASE' } } as any);
    mocked.post.mockResolvedValue({ data: { sentTo: 'qt@doit.vn' } } as any);
    mocked.delete.mockResolvedValue({ status: 204 } as any);
    render(<MailSettings />);
    fireEvent.change(await screen.findByLabelText('Mật khẩu'), { target: { value: 'MatKhauMoi' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
    await waitFor(() => expect((mocked.put.mock.calls[0][1] as any).password).toBe('MatKhauMoi'));

    fireEvent.change(screen.getByLabelText('Gửi thư thử tới'), { target: { value: 'qt@doit.vn' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi thư thử' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/admin/mail-settings/test', { to: 'qt@doit.vn' }));
    expect(await screen.findByText(/Đã gửi thư thử tới qt@doit.vn/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Dùng lại cấu hình .env' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/admin/mail-settings'));
  });

  it('chưa đặt khóa mã hóa thì cảnh báo và khóa ô mật khẩu', async () => {
    mocked.get.mockResolvedValue({ data: { ...fromEnv, keyAvailable: false } } as any);
    render(<MailSettings />);
    expect(await screen.findByText(/APPLICATION_MAIL_SECRET_KEY/)).toBeTruthy();
    expect((screen.getByLabelText('Mật khẩu') as HTMLInputElement).disabled).toBe(true);
  });

  it('gửi thử lỗi thì hiện lý do của máy chủ', async () => {
    mocked.get.mockResolvedValue({ data: fromEnv } as any);
    mocked.post.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'error.mailtestfailed', detail: 'Máy chủ từ chối đăng nhập: sai tài khoản hoặc mật khẩu email.' },
      },
    });
    render(<MailSettings />);
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi thư thử' }));
    expect(await screen.findByText(/sai tài khoản hoặc mật khẩu email/)).toBeTruthy();
  });
});
