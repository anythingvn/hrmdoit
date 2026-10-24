import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router';

import userManagement from './user-management.reducer';
import UserManagement from './user-management';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const USERS = [
  { id: 1, login: 'admin', email: 'admin@doit.vn', activated: true, authorities: ['ROLE_ADMIN'] },
  { id: 2, login: 'quantri2', email: 'qt2@doit.vn', activated: true, authorities: ['ROLE_ADMIN'] },
  { id: 3, login: 'nhanvien1', email: 'nv1@doit.vn', activated: true, authorities: ['ROLE_NHAN_VIEN'] },
];

const renderList = (me: string) => {
  const store = configureStore({
    reducer: {
      userManagement,
      authentication: (state = { account: { login: me, authorities: ['ROLE_ADMIN'] }, isAuthenticated: true }) => state,
    } as any,
  });
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/admin/user-management']}>
        <UserManagement />
      </MemoryRouter>
    </Provider>,
  );
};

const row = (login: string) => within(document.getElementById(login) as HTMLElement);

describe('UserManagement (nút Đặt mật khẩu trên từng dòng)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockImplementation(
      (url: string) =>
        Promise.resolve(
          url.startsWith('api/admin/users') ? { data: USERS, headers: { 'x-total-count': '3' } } : { data: {}, headers: {} },
        ) as any,
    );
  });

  it('dòng nhanvien1 có nút Đặt mật khẩu, bấm mở hộp thoại đặt mật khẩu của người đó', async () => {
    renderList('quantri2');
    await screen.findByText('nhanvien1');
    fireEvent.click(row('nhanvien1').getByRole('button', { name: 'Đặt mật khẩu cho nhanvien1' }));
    expect(await screen.findByText('Đặt mật khẩu: nhanvien1')).toBeTruthy();
  });

  it('dòng admin và dòng của chính mình không có nút', async () => {
    renderList('quantri2');
    await screen.findByText('nhanvien1');
    expect(row('admin').queryByRole('button', { name: /Đặt mật khẩu/ })).toBeNull();
    expect(row('quantri2').queryByRole('button', { name: /Đặt mật khẩu/ })).toBeNull();
    expect(row('nhanvien1').getByRole('button', { name: /Đặt mật khẩu/ })).toBeTruthy();
  });

  it('Quản trị khác (không phải admin, không phải mình) vẫn có nút', async () => {
    renderList('nhanvien1');
    await screen.findByText('quantri2');
    expect(row('quantri2').getByRole('button', { name: 'Đặt mật khẩu cho quantri2' })).toBeTruthy();
    expect(row('nhanvien1').queryByRole('button', { name: /Đặt mật khẩu/ })).toBeNull();
  });
});
