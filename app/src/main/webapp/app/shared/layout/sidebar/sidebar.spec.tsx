import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import Sidebar, { initials } from './sidebar';

describe('Sidebar', () => {
  it('có nav "Điều hướng chính" với link thật và khối người dùng', () => {
    render(
      <MemoryRouter initialEntries={['/nhan-vien']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    expect(screen.getByRole('navigation', { name: 'Điều hướng chính' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Nhân viên' }).getAttribute('href')).toBe('/nhan-vien');
    expect(screen.getByRole('link', { name: 'Danh mục' }).getAttribute('href')).toBe('/danh-muc/phong-ban');
    expect(screen.queryByRole('link', { name: 'Quản trị' })).toBeNull();
    expect(screen.getByText('Nhân sự')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Đăng xuất' })).toBeTruthy();
  });

  it('initials lấy chữ đầu họ và tên', () => {
    expect(initials('Hoàng Lan')).toBe('HL');
    expect(initials('')).toBe('?');
  });
});
