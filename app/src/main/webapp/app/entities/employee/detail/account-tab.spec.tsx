import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from '../test-utils';
import AccountTab from './account-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const NONE = { userId: null, login: null, activated: null, authorities: null };
const LINKED = { userId: 5, login: '079100000001', activated: true, authorities: ['ROLE_NHAN_VIEN'] };

const renderTab = (role: string, onChanged = vi.fn(), terminated = false) => {
  render(
    <Provider store={makeStore([role])}>
      <AccountTab employeeId={7} onChanged={onChanged} terminated={terminated} />
    </Provider>,
  );
  return onChanged;
};

const mockGet = (account: any, candidates: any[] = []) =>
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/employees/7/account') return Promise.resolve({ data: account } as any);
    if (url === 'api/employees/account-candidates') return Promise.resolve({ data: candidates } as any);
    if (url === 'api/admin/account-settings/default-password') return Promise.resolve({ data: { defaultPassword: 'Doit@2024' } } as any);
    return Promise.reject(new Error(`unexpected ${url}`));
  });

describe('AccountTab (Tài khoản đăng nhập)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.delete.mockReset();
  });

  it('HR tạo tài khoản: tên đăng nhập là số CCCD, mật khẩu ban đầu tùy chọn (trống = mặc định); mặc định vai trò Nhân viên', async () => {
    mockGet(NONE);
    mocked.post.mockResolvedValue({ data: LINKED } as any);
    const onChanged = renderTab('ROLE_HR');
    expect(await screen.findByText(/Tên đăng nhập là số CCCD trong hồ sơ/)).toBeTruthy();
    expect(await screen.findByText('Doit@2024')).toBeTruthy();
    expect(screen.queryByLabelText('Tên đăng nhập')).toBeNull();
    const pw = screen.getByLabelText('Mật khẩu ban đầu (tùy chọn)') as HTMLInputElement;
    expect(pw.type).toBe('password');
    expect(pw.placeholder).toBe('Để trống = mật khẩu mặc định');
    expect((screen.getByLabelText('Nhân viên (chỉ xem hồ sơ của mình)') as HTMLInputElement).checked).toBe(true);
    expect(screen.queryByLabelText('Quản trị')).toBeNull();
    fireEvent.click(screen.getByLabelText('Kế toán'));
    mockGet(LINKED);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/7/account', {
        authorities: ['ROLE_NHAN_VIEN', 'ROLE_KE_TOAN'],
      }),
    );
    expect(await screen.findByText('079100000001')).toBeTruthy();
    expect(onChanged).toHaveBeenCalled();
  });

  it('mật khẩu ban đầu tùy chọn: nhập thì gửi kèm; ngắn hơn 8 ký tự thì không gọi server', async () => {
    mockGet(NONE);
    mocked.post.mockResolvedValue({ data: LINKED } as any);
    renderTab('ROLE_HR');
    const pw = await screen.findByLabelText('Mật khẩu ban đầu (tùy chọn)');
    fireEvent.change(pw, { target: { value: '1234567' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 100 ký tự (hoặc để trống)')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.change(pw, { target: { value: 'MatKhau2026' } });
    mockGet(LINKED);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/7/account', {
        password: 'MatKhau2026',
        authorities: ['ROLE_NHAN_VIEN'],
      }),
    );
  });

  it('Quản trị thấy ô Quản trị', async () => {
    mockGet(NONE);
    renderTab('ROLE_ADMIN');
    expect(await screen.findByLabelText('Quản trị')).toBeTruthy();
  });

  it('hồ sơ chưa có CCCD: báo lỗi tiếng Việt từ server', async () => {
    mockGet(NONE);
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.idnumberrequired' } } });
    renderTab('ROLE_HR');
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByText('Nhập số CCCD trong hồ sơ trước khi tạo tài khoản')).toBeTruthy();
  });

  it('gắn tài khoản có sẵn: cảnh báo tên đăng nhập sẽ đổi thành số CCCD', async () => {
    mockGet(NONE, [{ id: 9, login: 'co-san', name: 'Có Sẵn', activated: true }]);
    renderTab('ROLE_HR');
    expect(await screen.findByText(/tên đăng nhập của tài khoản được đổi thành số CCCD/)).toBeTruthy();
  });

  it('gắn tài khoản có sẵn; tài khoản đã gắn người khác thì báo lỗi tiếng Việt', async () => {
    mockGet(NONE, [{ id: 9, login: 'co-san', name: 'Có Sẵn', activated: true }]);
    mocked.put.mockRejectedValue({ response: { status: 400, data: { message: 'error.useralreadylinked' } } });
    renderTab('ROLE_HR');
    await screen.findByRole('option', { name: 'co-san (Có Sẵn)' });
    fireEvent.change(screen.getByLabelText('Tài khoản có sẵn'), { target: { value: '9' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gắn tài khoản' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/employees/7/account', { userId: 9 }));
    expect(await screen.findByText('Tài khoản này đã gắn với hồ sơ nhân viên khác')).toBeTruthy();
  });

  it('đã gắn thì hiện tài khoản và bỏ gắn được (sau khi xác nhận)', async () => {
    mockGet(LINKED);
    mocked.delete.mockResolvedValue({ status: 204 } as any);
    const onChanged = renderTab('ROLE_HR');
    expect(await screen.findByText('079100000001')).toBeTruthy();
    expect(screen.getByText('Đang mở')).toBeTruthy();
    expect(screen.getByText('Nhân viên')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tạo tài khoản' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ gắn tài khoản' }));
    expect(mocked.delete).not.toHaveBeenCalled();
    mockGet(NONE);
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận bỏ gắn' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/employees/7/account'));
    expect(await screen.findByRole('button', { name: 'Tạo tài khoản' })).toBeTruthy();
    expect(onChanged).toHaveBeenCalled();
  });
  it('hồ sơ đã nghỉ: không có tạo / gắn tài khoản, báo tài khoản bị khóa', async () => {
    mockGet(NONE, [{ id: 9, login: 'co-san', activated: false }]);
    renderTab('ROLE_HR', vi.fn(), true);
    expect(await screen.findByText('Nhân viên đã nghỉ — tài khoản bị khóa')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tạo tài khoản' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Gắn tài khoản' })).toBeNull();
  });

  it('hồ sơ đã nghỉ có tài khoản: vẫn xem được và báo bị khóa', async () => {
    mockGet({ ...LINKED, activated: false });
    renderTab('ROLE_HR', vi.fn(), true);
    expect(await screen.findByText('079100000001')).toBeTruthy();
    expect(screen.getByText('Nhân viên đã nghỉ — tài khoản bị khóa')).toBeTruthy();
  });
});
