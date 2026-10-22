import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { configureStore } from '@reduxjs/toolkit';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router';

import department from './department.reducer';
import DepartmentUpdate from './department-update';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const store = () =>
  configureStore({
    reducer: {
      department,
      authentication: (state = { account: { authorities: ['ROLE_HR'], login: 'hr' }, isAuthenticated: true }) => state,
    } as any,
  });

describe('DepartmentUpdate', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url.startsWith('api/employees')) {
        return Promise.resolve({
          data: [
            { id: 7, code: 'DOIT2001', fullName: 'Trần Văn Trưởng' },
            { id: 8, code: 'DOIT2002', fullName: 'Lê Thị Phó' },
          ],
          headers: {},
        } as any);
      }
      if (url === 'api/departments/3') {
        return Promise.resolve({ data: { id: 3, name: 'Kinh doanh', active: true, headId: null }, headers: {} } as any);
      }
      return Promise.resolve({ data: [], headers: {} } as any);
    });
  });

  it('chọn trưởng phòng trong số nhân viên đang làm và gửi headId dạng số', async () => {
    mocked.put.mockResolvedValue({ data: { id: 3, name: 'Kinh doanh', active: true, headId: 8 }, headers: {} } as any);
    render(
      <Provider store={store()}>
        <MemoryRouter initialEntries={['/danh-muc/phong-ban/3/edit']}>
          <Routes>
            <Route path="/danh-muc/phong-ban/:id/edit" element={<DepartmentUpdate />} />
            <Route path="/danh-muc/phong-ban" element={<p>Danh sách</p>} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    expect(mocked.get.mock.calls.some(c => String(c[0]).includes('status.equals=DANG_LAM_VIEC'))).toBe(true);
    const select = (await screen.findByLabelText('Trưởng phòng')) as HTMLSelectElement;
    await screen.findByRole('option', { name: 'DOIT2002 Lê Thị Phó' });
    fireEvent.change(select, { target: { value: '8' } });
    fireEvent.click(document.getElementById('save-entity')!);
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    const body = mocked.put.mock.calls[0][1] as any;
    expect(body.headId).toBe(8);
  });
});
