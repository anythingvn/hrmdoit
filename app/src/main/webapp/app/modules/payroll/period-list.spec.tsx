import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { downloadFile } from 'app/shared/util/download';

import PeriodList from './period-list';

vi.mock('axios');
vi.mock('app/shared/util/download', async orig => ({ ...(await orig<any>()), downloadFile: vi.fn(() => Promise.resolve()) }));
const mocked = vi.mocked(axios, true);
const download = vi.mocked(downloadFile);

const renderPage = () =>
  render(
    <MemoryRouter>
      <PeriodList />
    </MemoryRouter>,
  );

describe('PeriodList – file bảng lương có ngày công', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({ data: [] } as any);
    download.mockReset();
    download.mockResolvedValue(undefined);
  });

  it('tải file của tháng đã chọn', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('Tháng (MM/yyyy)'), { target: { value: '11/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải file bảng lương có ngày công' }));
    await waitFor(() =>
      expect(download).toHaveBeenCalledWith('api/payroll/imports/template.xlsx', 'bang-luong-2026-11.xlsx', { period: '2026-11' }),
    );
  });

  it('tháng chưa chốt công: hiện câu lỗi', async () => {
    download.mockRejectedValue({ response: { status: 400, data: { message: 'error.attendancenotclosed' } } });
    renderPage();
    fireEvent.change(screen.getByLabelText('Tháng (MM/yyyy)'), { target: { value: '11/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải file bảng lương có ngày công' }));
    expect(await screen.findByText('Bảng công tháng này chưa chốt')).toBeTruthy();
  });

  it('tháng sai dạng: không gọi API', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('Tháng (MM/yyyy)'), { target: { value: '13/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải file bảng lương có ngày công' }));
    expect(await screen.findByText(/Nhập tháng dạng MM\/yyyy/)).toBeTruthy();
    expect(download).not.toHaveBeenCalled();
  });
});
