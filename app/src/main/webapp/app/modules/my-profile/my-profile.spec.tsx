import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import MyProfile from './my-profile';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderPage = () =>
  render(
    <Provider store={makeStore(['ROLE_NHAN_VIEN'])}>
      <MemoryRouter>
        <MyProfile />
      </MemoryRouter>
    </Provider>,
  );

describe('MyProfile (Hồ sơ của tôi)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
  });

  it('tải hồ sơ của mình, chỉ đọc: không có nút sửa, không có tab lịch sử', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/account/employee') {
        return Promise.resolve({
          data: { id: 7, fullName: 'Nguyễn Thị Mai', code: 'NV0007', status: 'DANG_LAM_VIEC', phone: '0901' },
        } as any);
      }
      return Promise.reject(new Error(`unexpected ${url}`));
    });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Nguyễn Thị Mai' })).toBeTruthy();
    expect(screen.getByText('0901')).toBeTruthy();
    expect(screen.queryByText('Sửa hồ sơ')).toBeNull();
    expect(screen.queryByText('Cho nghỉ việc')).toBeNull();
    expect(screen.queryByText('Lịch sử thay đổi')).toBeNull();
    expect(screen.queryByText('Hợp đồng & lương')).toBeNull();
  });

  it('bấm "Hiện" thì xem được dữ liệu nhạy cảm của chính mình', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/account/employee')
        return Promise.resolve({ data: { id: 7, fullName: 'Nguyễn Thị Mai', status: 'DANG_LAM_VIEC' } } as any);
      if (url === 'api/employees/7/sensitive') return Promise.resolve({ data: { idNumber: '079100000001', taxCode: '8001234567' } } as any);
      return Promise.reject(new Error(`unexpected ${url}`));
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Hiện' }));
    expect(await screen.findByText('079100000001')).toBeTruthy();
    expect(screen.getByText('8001234567')).toBeTruthy();
  });

  it('tài khoản chưa gắn hồ sơ thì báo rõ', async () => {
    mocked.get.mockRejectedValue({ response: { status: 404 } });
    renderPage();
    expect(await screen.findByText('Tài khoản của bạn chưa được gắn với hồ sơ nhân viên. Hãy liên hệ phòng Nhân sự.')).toBeTruthy();
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/account/employee'));
  });
});
