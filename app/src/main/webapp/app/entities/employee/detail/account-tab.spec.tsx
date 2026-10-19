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
const LINKED = { userId: 5, login: 'mai.nguyen', activated: true, authorities: ['ROLE_NHAN_VIEN'] };

const renderTab = (role: string, onChanged = vi.fn()) => {
  render(
    <Provider store={makeStore([role])}>
      <AccountTab employeeId={7} onChanged={onChanged} />
    </Provider>,
  );
  return onChanged;
};

const mockGet = (account: any, candidates: any[] = []) =>
  mocked.get.mockImplementation((url: string) => {
    if (url === 'api/employees/7/account') return Promise.resolve({ data: account } as any);
    if (url === 'api/employees/account-candidates') return Promise.resolve({ data: candidates } as any);
    return Promise.reject(new Error(`unexpected ${url}`));
  });

describe('AccountTab (Tài khoản đăng nhập)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.delete.mockReset();
  });

  it('HR tạo tài khoản kèm mật khẩu ban đầu; mặc định vai trò Nhân viên; không có ô Quản trị', async () => {
    mockGet(NONE);
    mocked.post.mockResolvedValue({ data: LINKED } as any);
    const onChanged = renderTab('ROLE_HR');
    fireEvent.change(await screen.findByLabelText('Tên đăng nhập'), { target: { value: 'mai.nguyen' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'MatKhau2026' } });
    fireEvent.change(screen.getByLabelText('Nhập lại mật khẩu'), { target: { value: 'MatKhau2026' } });
    expect((screen.getByLabelText('Nhân viên (chỉ xem hồ sơ của mình)') as HTMLInputElement).checked).toBe(true);
    expect(screen.queryByLabelText('Quản trị')).toBeNull();
    fireEvent.click(screen.getByLabelText('Kế toán'));
    mockGet(LINKED);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/7/account', {
        login: 'mai.nguyen',
        password: 'MatKhau2026',
        authorities: ['ROLE_NHAN_VIEN', 'ROLE_KE_TOAN'],
      }),
    );
    expect(await screen.findByText('mai.nguyen')).toBeTruthy();
    expect(onChanged).toHaveBeenCalled();
  });

  it('Quản trị thấy ô Quản trị', async () => {
    mockGet(NONE);
    renderTab('ROLE_ADMIN');
    expect(await screen.findByLabelText('Quản trị')).toBeTruthy();
  });

  it('mật khẩu ngắn hoặc nhập lại không khớp thì không gọi server', async () => {
    mockGet(NONE);
    renderTab('ROLE_HR');
    fireEvent.change(await screen.findByLabelText('Tên đăng nhập'), { target: { value: 'mai' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: '1234567' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 100 ký tự')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'MatKhau2026' } });
    fireEvent.change(screen.getByLabelText('Nhập lại mật khẩu'), { target: { value: 'MatKhau2027' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
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
    expect(await screen.findByText('mai.nguyen')).toBeTruthy();
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
});
