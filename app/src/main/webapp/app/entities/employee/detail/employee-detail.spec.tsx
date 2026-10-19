import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { Link, MemoryRouter, Route, Routes } from 'react-router';

import { makeStore } from '../test-utils';
import EmployeeDetail from './employee-detail';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const EMPLOYEES: Record<string, any> = {
  1: { id: 1, fullName: 'Trần Minh Quân', code: 'NV0142', status: 'DANG_LAM_VIEC' },
  2: { id: 2, fullName: 'Lê Thị Hoa', code: 'NV0200', status: 'DANG_LAM_VIEC' },
};

describe('EmployeeDetail', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockImplementation((url: string) => {
      const sensitive = /api\/employees\/(\d+)\/sensitive$/.exec(url);
      if (sensitive) return Promise.resolve({ data: { idNumber: sensitive[1] === '1' ? '001096012345' : '079000000002' } } as any);
      const one = /api\/employees\/(\d+)$/.exec(url);
      if (one) return Promise.resolve({ data: EMPLOYEES[one[1]] } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('chuyển sang nhân viên khác thì dữ liệu nhạy cảm bị ẩn lại và về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    expect(await screen.findByText('001096012345')).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.queryByText('001096012345')).toBeNull();
    expect(screen.queryByText('079000000002')).toBeNull();
    expect(screen.getByRole('button', { name: 'Hiện' })).toBeTruthy();
  });

  it('đang ở tab khác, chuyển nhân viên thì về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử công tác' }));
    expect(screen.getByRole('tab', { name: 'Lịch sử công tác' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.getByRole('tab', { name: 'Thông tin' }).getAttribute('aria-selected')).toBe('true');
  });
});
