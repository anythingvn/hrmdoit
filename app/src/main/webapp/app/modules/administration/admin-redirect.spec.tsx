import { vi } from 'vitest';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';

import AdministrationRoutes from './index';

vi.mock('axios');
vi.mock('./health/health', () => ({ default: () => <div data-testid="health" /> }));

const Where = () => <div data-testid="where">{useLocation().pathname + useLocation().search}</div>;

const renderAt = (path: string) =>
  render(
    <Provider store={makeStore(['ROLE_ADMIN'])}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="admin/*" element={<AdministrationRoutes />} />
          <Route path="cai-dat/*" element={<p>Cài đặt</p>} />
        </Routes>
        <Where />
      </MemoryRouter>
    </Provider>,
  );

describe('Chuyển hướng địa chỉ Quản trị cũ sang Cài đặt', () => {
  it.each([
    ['/admin/user-management', '/cai-dat/tai-khoan'],
    ['/admin/user-management/new', '/cai-dat/tai-khoan/new'],
    ['/admin/user-management/abc', '/cai-dat/tai-khoan/abc'],
    ['/admin/user-management/abc/edit', '/cai-dat/tai-khoan/abc/edit'],
    ['/admin/user-management/abc/delete', '/cai-dat/tai-khoan/abc/delete'],
    ['/admin/nhat-ky-xoa-nhan-vien', '/cai-dat/nhat-ky-xoa-nhan-vien'],
  ])('%s -> %s', async (from, to) => {
    renderAt(from);
    expect(await screen.findByText('Cài đặt')).toBeTruthy();
    expect(screen.getByTestId('where').textContent).toBe(to);
  });

  it('giữ route health; không còn thanh tab Quản trị', async () => {
    renderAt('/admin/health');
    expect(await screen.findByTestId('health')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Quản trị' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Tài khoản' })).toBeNull();
  });
});
