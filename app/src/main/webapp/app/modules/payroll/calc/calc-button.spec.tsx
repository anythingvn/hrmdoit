import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';

import CalcButton from './calc-button';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderIt = () =>
  render(
    <MemoryRouter initialEntries={['/bang-luong/2026-06']}>
      <Routes>
        <Route path="/bang-luong/2026-06" element={<CalcButton period="2026-06" />} />
        <Route path="/bang-luong/tinh-luong/:id" element={<div>Màn bản nháp</div>} />
      </Routes>
    </MemoryRouter>,
  );

describe('CalcButton', () => {
  beforeEach(() => mocked.post.mockReset());

  it('gọi POST tạo bản nháp rồi chuyển tới màn bản nháp', async () => {
    mocked.post.mockResolvedValue({ data: { id: 7 } } as any);
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Tính lương' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/periods/2026-06/calc'));
    expect(await screen.findByText('Màn bản nháp')).toBeTruthy();
  });

  it('chưa chốt bảng công: hiện câu lý do và link tới bảng công', async () => {
    mocked.post.mockRejectedValueOnce({
      response: {
        status: 400,
        data: { message: 'error.payrollattendancenotclosed', detail: 'Hãy chốt bảng công tháng 06/2026 trước khi tính lương.' },
      },
    });
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Tính lương' }));
    expect(await screen.findByText(/Hãy chốt bảng công tháng 06\/2026/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Mở bảng công tháng' }).getAttribute('href')).toBe('/bang-cong');
  });
});
