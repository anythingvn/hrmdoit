import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router';

import userManagement from './user-management.reducer';
import UserManagementUpdate from './user-management-update';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderNew = () => {
  const store = configureStore({ reducer: { userManagement } as any });
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/cai-dat/tai-khoan/new']}>
        <Routes>
          <Route path="/cai-dat/tai-khoan/new" element={<UserManagementUpdate />} />
          <Route path="/cai-dat/tai-khoan" element={<p>Danh sách tài khoản</p>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
};

describe('UserManagementUpdate (tạo tài khoản)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/authorities')
        return Promise.resolve({
          data: [{ name: 'ROLE_ADMIN' }, { name: 'ROLE_HR' }, { name: 'ROLE_NHAN_VIEN' }, { name: 'ROLE_USER' }],
        } as any);
      if (url === 'api/admin/account-settings/default-password') return Promise.resolve({ data: { defaultPassword: 'Doit@2024' } } as any);
      return Promise.resolve({ data: [], headers: {} } as any);
    });
  });

  it('ô Số CCCD tự điền Tên đăng nhập (khóa, không sửa tay); mật khẩu ban đầu tùy chọn (trống = mặc định, không gửi); email không bắt buộc (gửi null)', async () => {
    mocked.post.mockResolvedValue({ data: { login: '079100000001' } } as any);
    renderNew();
    const login = (await screen.findByLabelText('Tên đăng nhập')) as HTMLInputElement;
    expect(login.readOnly).toBe(true);
    expect((screen.getByLabelText('Mật khẩu ban đầu (tùy chọn)') as HTMLInputElement).placeholder).toBe('Để trống = mật khẩu mặc định');
    fireEvent.change(screen.getByLabelText('Số CCCD'), { target: { value: ' 079100000001 ' } });
    await waitFor(() => expect(login.value).toBe('079100000001'));
    expect(await screen.findByText('Doit@2024')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const [url, body] = mocked.post.mock.calls[0] as [string, any];
    expect(url).toBe('api/admin/users');
    expect(body.login).toBe('079100000001');
    expect(body.idNumber).toBe('079100000001');
    expect(body.password).toBeUndefined();
    expect(body.email).toBeNull();
    expect(body.activated).toBe(true);
  });

  it('mật khẩu ban đầu tùy chọn: nhập thì gửi; ngắn hơn 8 ký tự thì không gửi', async () => {
    mocked.post.mockResolvedValue({ data: { login: '079100000001' } } as any);
    renderNew();
    fireEvent.change(await screen.findByLabelText('Số CCCD'), { target: { value: '079100000001' } });
    const pw = screen.getByLabelText('Mật khẩu ban đầu (tùy chọn)');
    fireEvent.change(pw, { target: { value: '1234567' } });
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 100 ký tự (hoặc để trống)')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.change(pw, { target: { value: 'MatKhau2026' } });
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    expect((mocked.post.mock.calls[0] as [string, any])[1].password).toBe('MatKhau2026');
  });

  it('số CCCD không đúng 12 (hoặc 9) chữ số thì không gửi', async () => {
    renderNew();
    fireEvent.change(await screen.findByLabelText('Số CCCD'), { target: { value: '07910-ABC' } });
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    expect(await screen.findByText('Số CCCD gồm 12 chữ số (hoặc 9 chữ số CMND cũ)')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('vai trò hiện tên tiếng Việt', async () => {
    renderNew();
    expect(await screen.findByRole('option', { name: 'Nhân viên' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Quản trị' })).toBeTruthy();
    // ROLE_USER không được cấp mới.
    expect(screen.queryByRole('option', { name: 'Người dùng' })).toBeNull();
    expect(screen.queryByRole('option', { name: 'ROLE_USER' })).toBeNull();
  });
});
