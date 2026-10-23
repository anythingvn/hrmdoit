import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { resetBrandingForTests } from 'app/shared/branding/branding';

import LoginModal from './login-modal';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderModal = (loginErrorDetail?: string | null) =>
  render(
    <MemoryRouter>
      <LoginModal showModal loginError loginErrorDetail={loginErrorDetail} handleLogin={() => undefined} handleClose={() => undefined} />
    </MemoryRouter>,
  );

describe('LoginModal', () => {
  beforeEach(() => {
    resetBrandingForTests();
    mocked.get.mockReset();
    mocked.create.mockReturnValue(mocked as any);
    mocked.get.mockResolvedValue({ data: { companyName: 'Công ty ABC', shortName: 'ABC HR', tagline: 'Làm thật', logoVersion: 5 } } as any);
  });

  it('hiện logo, tên viết tắt và khẩu hiệu của công ty', async () => {
    renderModal(null);
    expect(await screen.findByText('ABC HR')).toBeTruthy();
    expect(screen.getByText('Làm thật')).toBeTruthy();
    await waitFor(() =>
      expect(document.querySelector('[data-cy="loginTitle"] img')?.getAttribute('src')).toBe('api/public/branding/logo?v=5'),
    );
  });

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
