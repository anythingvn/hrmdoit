import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { resetBrandingForTests } from 'app/shared/branding/branding';

import Sidebar, { initials } from './sidebar';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('Sidebar', () => {
  beforeEach(() => {
    resetBrandingForTests();
    mocked.get.mockReset();
    mocked.create.mockReturnValue(mocked as any);
    mocked.get.mockResolvedValue({ data: { companyName: 'Công ty ABC', shortName: 'ABC HR', tagline: 'Làm thật', logoVersion: 5 } } as any);
  });

  it('hiện tên viết tắt, khẩu hiệu và logo lấy từ branding', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/nhan-vien']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    expect(await screen.findByText('ABC HR')).toBeTruthy();
    expect(screen.getByText('Làm thật')).toBeTruthy();
    expect(screen.queryByText('DOIT HRM')).toBeNull();
    expect(screen.getByRole('link', { name: 'ABC HR Làm thật' }).getAttribute('href')).toBe('/');
    await waitFor(() => expect(container.querySelector('.doit-brand img')?.getAttribute('src')).toBe('api/public/branding/logo?v=5'));
  });

  it('HR, Quản trị có mục Cài đặt', () => {
    render(
      <MemoryRouter initialEntries={['/cai-dat/dia-diem']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    const link = screen.getByRole('link', { name: 'Cài đặt' });
    expect(link.getAttribute('href')).toBe('/cai-dat');
    expect(link.className).toContain('nv-on');
  });

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

  it('trong mục Nhân viên, HR thấy mục con "Nhân viên đã lưu trữ"; ngoài mục thì không', () => {
    const { unmount } = render(
      <MemoryRouter initialEntries={['/nhan-vien/luu-tru']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    const sub = screen.getByRole('link', { name: 'Nhân viên đã lưu trữ' });
    expect(sub.getAttribute('href')).toBe('/nhan-vien/luu-tru');
    expect(sub.className).toContain('nv-sub-on');
    unmount();
    render(
      <MemoryRouter initialEntries={['/tong-quan']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    expect(screen.queryByRole('link', { name: 'Nhân viên đã lưu trữ' })).toBeNull();
  });

  it('nút menu đổi nhãn "Mở menu"/"Đóng menu" theo trạng thái', () => {
    render(
      <MemoryRouter initialEntries={['/nhan-vien']}>
        <Sidebar authorities={['ROLE_HR']} displayName="Hoàng Lan" />
      </MemoryRouter>,
    );
    const toggle = screen.getByRole('button', { name: 'Mở menu' });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(toggle);
    expect(screen.getByRole('button', { name: 'Đóng menu' }).getAttribute('aria-expanded')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Đóng menu' }));
    expect(screen.getByRole('button', { name: 'Mở menu' })).toBeTruthy();
  });

  it('initials lấy chữ đầu họ và tên', () => {
    expect(initials('Hoàng Lan')).toBe('HL');
    expect(initials('')).toBe('?');
  });
});
