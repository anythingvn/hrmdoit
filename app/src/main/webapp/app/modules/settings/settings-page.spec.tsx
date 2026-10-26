import { vi } from 'vitest';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import EntitiesRoutes from 'app/entities/routes';
import AdministrationRoutes from 'app/modules/administration';

import SettingsPage from './settings-page';

vi.mock('axios');

vi.mock('app/modules/work-calendar/work-calendar', () => ({
  default: (p: any) => (
    <div data-testid="work-calendar">
      week:{String(p.canEditWeek)} holidays:{String(p.canEditHolidays)} hours:{String(p.canEditHours)}
    </div>
  ),
}));
vi.mock('app/modules/work-calendar/reminder-status', () => ({ default: () => <div data-testid="reminder-status" /> }));
vi.mock('app/modules/administration/mail-settings/mail-settings', () => ({ default: () => <div data-testid="mail-settings" /> }));
vi.mock('app/modules/leave/leave-settings', () => ({
  default: (p: any) => <div data-testid="leave-settings">canEdit:{String(p.canEdit)}</div>,
}));
const UserManagementStub = () => <div data-testid="user-management">{useLocation().pathname}</div>;
vi.mock('app/modules/administration/user-management', () => ({ default: () => <UserManagementStub /> }));
vi.mock('app/modules/administration/employee-deletions/employee-deletions', () => ({
  default: () => <div data-testid="employee-deletions" />,
}));
vi.mock('./company-tab', () => ({ default: () => <div data-testid="company-tab" /> }));
vi.mock('./backup-tab', () => ({ default: () => <div data-testid="backup-tab" /> }));
vi.mock('./locations-tab', () => ({ default: () => <div data-testid="locations-tab" /> }));
vi.mock('./self-punch-settings', () => ({ default: (p: any) => <div data-testid="self-punch-settings">canEdit:{String(p.canEdit)}</div> }));

const Where = () => <div data-testid="where">{useLocation().pathname}</div>;

const renderAt = (path: string, roles: string[], account: Record<string, unknown> = {}) =>
  render(
    <Provider store={makeStore(roles, account)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="cai-dat/*" element={<SettingsPage />} />
          <Route path="danh-muc/*" element={<EntitiesRoutes />} />
          <Route path="admin/*" element={<AdministrationRoutes />} />
        </Routes>
        <Where />
      </MemoryRouter>
    </Provider>,
  );

const TAB_NAMES = ['Công ty', 'Lịch & giờ làm', 'Địa điểm chấm công', 'Email', 'Sao lưu', 'Tài khoản', 'Nghỉ phép'];

describe('SettingsPage', () => {
  it('HR thấy 3 tab (Lịch & giờ làm, Địa điểm chấm công, Nghỉ phép chỉ xem), không thấy Công ty, Email', async () => {
    renderAt('/cai-dat', ['ROLE_HR']);
    const nav = await screen.findByRole('navigation', { name: 'Cài đặt' });
    const names = Array.from(nav.querySelectorAll('a')).map(a => a.textContent);
    expect(names).toEqual(['Lịch & giờ làm', 'Địa điểm chấm công', 'Nghỉ phép']);
    // Vào thẳng trang: chuyển sang tab đầu tiên được thấy.
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/lich-lam-viec');
    expect(screen.getByTestId('work-calendar').textContent).toBe('week:false holidays:true hours:true');
    expect(screen.queryByTestId('reminder-status')).toBeNull();
  });

  it('HR ở tab Nghỉ phép: chỉ xem, không sửa; Quản trị sửa được', async () => {
    const r = renderAt('/cai-dat/nghi-phep', ['ROLE_HR']);
    expect((await screen.findByTestId('leave-settings')).textContent).toBe('canEdit:false');
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/nghi-phep');
    r.unmount();
    renderAt('/cai-dat/nghi-phep', ['ROLE_ADMIN']);
    expect((await screen.findByTestId('leave-settings')).textContent).toBe('canEdit:true');
  });

  it('HR gõ link tab không được thấy thì về tab đầu tiên', async () => {
    renderAt('/cai-dat/cong-ty', ['ROLE_HR']);
    expect(await screen.findByTestId('work-calendar')).toBeTruthy();
    expect(screen.queryByTestId('company-tab')).toBeNull();
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/lich-lam-viec');
  });

  it('ADMIN (không phải tài khoản admin hệ thống) thấy 7 tab; mỗi tab nhúng đúng phần', async () => {
    renderAt('/cai-dat', ['ROLE_ADMIN']);
    const nav = await screen.findByRole('navigation', { name: 'Cài đặt' });
    expect(Array.from(nav.querySelectorAll('a')).map(a => a.textContent)).toEqual(TAB_NAMES);
    expect(Array.from(nav.querySelectorAll('a')).map(a => a.getAttribute('href'))).toEqual([
      '/cai-dat/cong-ty',
      '/cai-dat/lich-lam-viec',
      '/cai-dat/dia-diem',
      '/cai-dat/email',
      '/cai-dat/sao-luu',
      '/cai-dat/tai-khoan',
      '/cai-dat/nghi-phep',
    ]);
    expect(screen.getByTestId('company-tab')).toBeTruthy();
  });

  it('ADMIN ở tab Lịch & giờ làm: có trạng thái nhắc việc, sửa được ngày làm trong tuần', async () => {
    renderAt('/cai-dat/lich-lam-viec', ['ROLE_ADMIN']);
    expect((await screen.findByTestId('work-calendar')).textContent).toBe('week:true holidays:true hours:true');
    expect(screen.getByTestId('reminder-status')).toBeTruthy();
  });

  it('các tab Email, Nghỉ phép, Địa điểm', async () => {
    const { unmount } = renderAt('/cai-dat/email', ['ROLE_ADMIN']);
    expect(await screen.findByTestId('mail-settings')).toBeTruthy();
    unmount();
    const r2 = renderAt('/cai-dat/nghi-phep', ['ROLE_ADMIN']);
    expect(await screen.findByTestId('leave-settings')).toBeTruthy();
    r2.unmount();
    renderAt('/cai-dat/dia-diem', ['ROLE_HR']);
    expect(await screen.findByTestId('locations-tab')).toBeTruthy();
  });

  it('tab Địa điểm có khối Tự chấm công: HR chỉ xem, Quản trị sửa được', async () => {
    const r = renderAt('/cai-dat/dia-diem', ['ROLE_HR']);
    expect((await screen.findByTestId('self-punch-settings')).textContent).toBe('canEdit:false');
    r.unmount();
    renderAt('/cai-dat/dia-diem', ['ROLE_ADMIN']);
    expect((await screen.findByTestId('self-punch-settings')).textContent).toBe('canEdit:true');
  });

  it('/danh-muc/lich-lam-viec chuyển sang /cai-dat/lich-lam-viec; Danh mục không còn mục Lịch làm việc', async () => {
    renderAt('/danh-muc/lich-lam-viec', ['ROLE_HR']);
    expect(await screen.findByTestId('work-calendar')).toBeTruthy();
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/lich-lam-viec');
    expect(screen.queryByRole('link', { name: 'Lịch làm việc' })).toBeNull();
  });

  it('/admin/cau-hinh-email chuyển sang /cai-dat/email', async () => {
    renderAt('/admin/cau-hinh-email', ['ROLE_ADMIN']);
    expect(await screen.findByTestId('mail-settings')).toBeTruthy();
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/email');
    expect(screen.queryByRole('link', { name: 'Cấu hình email' })).toBeNull();
  });

  it('Tài khoản nằm sau Sao lưu; Nhật ký xóa nhân viên chỉ hiện với login admin, ở cuối', async () => {
    renderAt('/cai-dat/tai-khoan', ['ROLE_ADMIN']).unmount();
    renderAt('/cai-dat/tai-khoan', ['ROLE_ADMIN'], { login: 'admin' });
    const nav = await screen.findByRole('navigation', { name: 'Cài đặt' });
    const names = Array.from(nav.querySelectorAll('a')).map(a => a.textContent);
    expect(names.slice(-2)).toEqual(['Nghỉ phép', 'Nhật ký xóa nhân viên']);
    expect(names.indexOf('Tài khoản')).toBe(names.indexOf('Sao lưu') + 1);
  });

  it('tab Nhật ký xóa nhân viên: login admin thấy; tài khoản khác bị đưa về tab đầu', async () => {
    const r = renderAt('/cai-dat/nhat-ky-xoa-nhan-vien', ['ROLE_ADMIN'], { login: 'admin' });
    expect(await screen.findByTestId('employee-deletions')).toBeTruthy();
    r.unmount();
    renderAt('/cai-dat/nhat-ky-xoa-nhan-vien', ['ROLE_ADMIN']);
    expect(await screen.findByTestId('company-tab')).toBeTruthy();
    expect(screen.queryByTestId('employee-deletions')).toBeNull();
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/cong-ty');
  });

  it('tab Tài khoản hiện màn quản lý tài khoản, kể cả đường dẫn con', async () => {
    const r = renderAt('/cai-dat/tai-khoan', ['ROLE_ADMIN']);
    expect((await screen.findByTestId('user-management')).textContent).toBe('/cai-dat/tai-khoan');
    r.unmount();
    renderAt('/cai-dat/tai-khoan/abc/edit', ['ROLE_ADMIN']);
    expect((await screen.findByTestId('user-management')).textContent).toBe('/cai-dat/tai-khoan/abc/edit');
  });

  it('HR không thấy Tài khoản; mở /cai-dat/tai-khoan thì về tab đầu HR được xem', async () => {
    renderAt('/cai-dat/tai-khoan', ['ROLE_HR']);
    expect(await screen.findByTestId('work-calendar')).toBeTruthy();
    expect(screen.queryByTestId('user-management')).toBeNull();
    expect(screen.getByTestId('where').textContent).toBe('/cai-dat/lich-lam-viec');
    expect(screen.queryByRole('link', { name: 'Tài khoản' })).toBeNull();
  });
});
