import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import SetPasswordDialog from './set-password-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = (toggle = vi.fn()) => {
  render(<SetPasswordDialog login="nhanvien1" isOpen toggle={toggle} />);
  return toggle;
};

const fill = (password: string, confirm: string) => {
  fireEvent.change(screen.getByLabelText('Mật khẩu mới'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Nhập lại mật khẩu mới'), { target: { value: confirm } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }));
};

describe('SetPasswordDialog', () => {
  beforeEach(() => {
    mocked.put.mockReset();
  });

  it('mật khẩu quá ngắn thì báo lỗi, không gọi server', async () => {
    renderDialog();
    fill('1234567', '1234567');
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 100 ký tự')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('nhập lại không khớp thì báo lỗi, không gọi server', async () => {
    renderDialog();
    fill('MatKhauMoi2026', 'MatKhauMoi2027');
    expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('hợp lệ thì gọi PUT …/password rồi báo đã đặt', async () => {
    mocked.put.mockResolvedValue({ status: 204 } as any);
    renderDialog();
    fill('MatKhauMoi2026', 'MatKhauMoi2026');
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/admin/users/nhanvien1/password', { newPassword: 'MatKhauMoi2026' }));
    expect(await screen.findByText('Đã đặt mật khẩu mới cho tài khoản nhanvien1.')).toBeTruthy();
  });

  it('server từ chối thì hiện lỗi', async () => {
    mocked.put.mockRejectedValue({ response: { status: 403 } });
    renderDialog();
    fill('MatKhauMoi2026', 'MatKhauMoi2026');
    expect(await screen.findByText('Bạn không có quyền thực hiện thao tác này')).toBeTruthy();
  });
});
