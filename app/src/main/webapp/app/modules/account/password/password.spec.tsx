import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';
import { configureStore } from '@reduxjs/toolkit';

import authentication from 'app/shared/reducers/authentication';
import locale from 'app/shared/reducers/locale';
import password, { savePassword } from './password.reducer';
import PasswordPage from './password';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderPage = (account: any) => {
  mocked.get.mockResolvedValue({ data: account } as any);
  const store = configureStore({ reducer: { authentication, password, locale } as any });
  render(
    <Provider store={store}>
      <MemoryRouter>
        <PasswordPage />
      </MemoryRouter>
    </Provider>,
  );
};

describe('PasswordPage (đổi mật khẩu)', () => {
  beforeEach(() => mocked.get.mockReset());

  it('đang dùng mật khẩu mặc định: báo phải đổi mật khẩu để tiếp tục', async () => {
    renderPage({ login: '079100000001', authorities: ['ROLE_NHAN_VIEN'], mustChangePassword: true });
    expect(await screen.findByText('Bạn đang dùng mật khẩu mặc định — đổi mật khẩu để tiếp tục')).toBeTruthy();
  });

  it('đã đổi mật khẩu: không có thông báo', async () => {
    renderPage({ login: '079100000001', authorities: ['ROLE_NHAN_VIEN'], mustChangePassword: false });
    await screen.findByRole('button');
    expect(screen.queryByText('Bạn đang dùng mật khẩu mặc định — đổi mật khẩu để tiếp tục')).toBeNull();
  });
});

describe('password reducer: mật khẩu mặc định', () => {
  it('server từ chối mật khẩu mặc định thì báo câu tiếng Việt', () => {
    const state = password(undefined, {
      type: savePassword.rejected.type,
      error: { response: { status: 400, data: { message: 'error.defaultpassword' } } },
    } as any);
    expect(state.errorMessage).toBe('Mật khẩu mới không được trùng mật khẩu mặc định');
  });
});
