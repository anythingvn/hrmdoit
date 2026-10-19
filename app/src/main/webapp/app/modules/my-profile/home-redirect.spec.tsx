import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import HomeRedirect from './home-redirect';

const renderAt = (authorities: string[]) =>
  render(
    <Provider store={makeStore(authorities)}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route index element={<HomeRedirect />} />
          <Route path="nhan-vien" element={<p>Danh sách nhân viên</p>} />
          <Route path="ho-so-cua-toi" element={<p>Trang hồ sơ của tôi</p>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );

describe('HomeRedirect', () => {
  it('chỉ ROLE_NHAN_VIEN thì về Hồ sơ của tôi', () => {
    renderAt(['ROLE_NHAN_VIEN']);
    expect(screen.getByText('Trang hồ sơ của tôi')).toBeTruthy();
  });

  it('có vai trò nghiệp vụ thì về danh sách nhân viên', () => {
    renderAt(['ROLE_HR', 'ROLE_NHAN_VIEN']);
    expect(screen.getByText('Danh sách nhân viên')).toBeTruthy();
  });

  it('chưa đăng nhập (không có vai trò) thì về danh sách nhân viên như cũ', () => {
    renderAt([]);
    expect(screen.getByText('Danh sách nhân viên')).toBeTruthy();
  });
});
