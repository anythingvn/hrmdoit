import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router';

import userManagement from './user-management.reducer';
import UserManagementDetail from './user-management-detail';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDetail = () => {
  const store = configureStore({ reducer: { userManagement } as any });
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/admin/user-management/mai.nguyen']}>
        <Routes>
          <Route path="/admin/user-management/:login" element={<UserManagementDetail />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
};

describe('UserManagementDetail (mở khóa đăng nhập)', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockResolvedValue({ data: { login: 'mai.nguyen', activated: true, authorities: [] } } as any);
  });

  it('bấm "Mở khóa đăng nhập" gọi API Quản trị và báo thành công', async () => {
    mocked.delete.mockResolvedValue({ status: 204 } as any);
    renderDetail();
    fireEvent.click(await screen.findByRole('button', { name: 'Mở khóa đăng nhập' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/admin/login-attempts/mai.nguyen'));
    expect((await screen.findByRole('status')).textContent).toContain('Đã mở khóa đăng nhập');
  });

  it('báo lỗi khi mở khóa thất bại', async () => {
    mocked.delete.mockRejectedValue({ response: { status: 500 } });
    renderDetail();
    fireEvent.click(await screen.findByRole('button', { name: 'Mở khóa đăng nhập' }));
    expect((await screen.findByRole('status')).textContent).toContain('Không mở khóa được');
  });
});
