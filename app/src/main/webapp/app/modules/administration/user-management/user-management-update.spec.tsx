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
      <MemoryRouter initialEntries={['/admin/user-management/new']}>
        <Routes>
          <Route path="/admin/user-management/new" element={<UserManagementUpdate />} />
          <Route path="/admin/user-management" element={<p>Danh sách tài khoản</p>} />
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
        return Promise.resolve({ data: [{ name: 'ROLE_ADMIN' }, { name: 'ROLE_HR' }, { name: 'ROLE_NHAN_VIEN' }] } as any);
      return Promise.resolve({ data: [], headers: {} } as any);
    });
  });

  it('có ô mật khẩu ban đầu; email không bắt buộc (gửi null)', async () => {
    mocked.post.mockResolvedValue({ data: { login: 'ketoan' } } as any);
    renderNew();
    fireEvent.change(await screen.findByLabelText('Tên đăng nhập'), { target: { value: 'ketoan' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'MatKhau2026' } });
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const [url, body] = mocked.post.mock.calls[0] as [string, any];
    expect(url).toBe('api/admin/users');
    expect(body.login).toBe('ketoan');
    expect(body.password).toBe('MatKhau2026');
    expect(body.email).toBeNull();
    expect(body.activated).toBe(true);
  });

  it('mật khẩu ban đầu ngắn hơn 8 ký tự thì không gửi', async () => {
    renderNew();
    fireEvent.change(await screen.findByLabelText('Tên đăng nhập'), { target: { value: 'ketoan' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: '1234567' } });
    fireEvent.click(screen.getByRole('button', { name: /Lưu/ }));
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 100 ký tự')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('vai trò hiện tên tiếng Việt', async () => {
    renderNew();
    expect(await screen.findByRole('option', { name: 'Nhân viên' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Quản trị' })).toBeTruthy();
  });
});
