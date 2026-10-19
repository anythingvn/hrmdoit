import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from './test-utils';
import EmployeeList from './employee-list';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderList = (authorities: string[]) =>
  render(
    <Provider store={makeStore(authorities)}>
      <MemoryRouter initialEntries={['/nhan-vien']}>
        <EmployeeList />
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeList', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          id: 1,
          code: 'NV0142',
          fullName: 'Trần Minh Quân',
          department: { id: 1, name: 'IT' },
          position: 'Dev',
          status: 'DANG_LAM_VIEC',
        },
      ],
      headers: { 'x-total-count': '1' },
    } as any);
  });

  it('vai trò BGD không có nút "Thêm nhân viên", HR thì có', async () => {
    const { unmount } = renderList(['ROLE_BGD']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.queryByText('Thêm nhân viên')).toBeNull();
    unmount();
    renderList(['ROLE_HR']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.getByText('Thêm nhân viên')).toBeTruthy();
  });

  it('gõ "quan" vào ô tìm thì request có q.contains=quan', async () => {
    renderList(['ROLE_HR']);
    await screen.findByText('Trần Minh Quân');
    fireEvent.change(screen.getByLabelText('Tìm theo tên hoặc mã'), { target: { value: 'quan' } });
    await waitFor(
      () => {
        const urls = mocked.get.mock.calls.map(c => String(c[0]));
        expect(urls.some(u => u.includes('api/employees') && u.includes('q.contains=quan'))).toBe(true);
      },
      { timeout: 2000 },
    );
  });
});
