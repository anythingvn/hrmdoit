import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import LoginModal from './login-modal';

const renderModal = (loginErrorDetail?: string | null) =>
  render(
    <MemoryRouter>
      <LoginModal showModal loginError loginErrorDetail={loginErrorDetail} handleLogin={() => undefined} handleClose={() => undefined} />
    </MemoryRouter>,
  );

describe('LoginModal', () => {
  it('hiện câu báo của máy chủ khi đăng nhập sai quá nhiều lần', () => {
    renderModal('Bạn đã nhập sai mật khẩu 5 lần trong 15 phút. Vui lòng thử lại sau 15 phút.');
    expect(screen.getByText(/Vui lòng thử lại sau 15 phút/)).toBeTruthy();
  });

  it('không có câu riêng thì hiện câu chung', () => {
    renderModal(null);
    const alert = document.querySelector('[data-cy="loginError"]');
    expect(alert).not.toBeNull();
    expect(alert?.textContent).not.toContain('15 phút');
  });
});
