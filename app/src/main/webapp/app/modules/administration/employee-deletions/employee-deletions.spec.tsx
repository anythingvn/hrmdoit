import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { render, screen, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from 'app/entities/employee/test-utils';
import EmployeeDeletions from './employee-deletions';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderPage = (login: string) =>
  render(
    <Provider store={makeStore(['ROLE_ADMIN'], { login })}>
      <MemoryRouter>
        <EmployeeDeletions />
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeDeletions', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          id: 1,
          deletedAt: '2026-10-21T02:30:00Z',
          deletedBy: 'admin',
          reason: 'Hồ sơ trùng',
          employeeCode: 'NV0400',
          employeeName: 'Đỗ Đã Nghỉ',
        },
      ],
      headers: { 'x-total-count': '1' },
    } as any);
  });

  it('tài khoản admin xem nhật ký xóa: thời điểm, người xóa, mã, họ tên, lý do', async () => {
    renderPage('admin');
    const row = (await screen.findByText('Đỗ Đã Nghỉ')).closest('tr')!;
    expect(mocked.get).toHaveBeenCalledWith('api/admin/employee-deletions', { params: { page: 0, size: 20 } });
    expect(within(row).getByText('21/10/2026 09:30')).toBeTruthy();
    expect(within(row).getByText('admin')).toBeTruthy();
    expect(within(row).getByText('NV0400')).toBeTruthy();
    expect(within(row).getByText('Hồ sơ trùng')).toBeTruthy();
  });

  it('Quản trị khác: không gọi API, báo chỉ tài khoản admin xem được', () => {
    renderPage('quantri2');
    expect(screen.getByText('Chỉ tài khoản admin xem được nhật ký xóa hẳn nhân viên.')).toBeTruthy();
    expect(mocked.get).not.toHaveBeenCalled();
  });
});
