import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';

import PeriodDetail from './period-detail';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const detail = (status: string) => ({
  period: '2026-06',
  status,
  officialVersion: 1,
  approvedVersion: status === 'DA_DUYET' ? 1 : null,
  versions: [
    {
      version: 1,
      importedBy: 'ketoan1',
      importedAt: '2026-10-21T03:00:00Z',
      fileName: 'bang-luong.xlsx',
      headcount: 14,
      totalNet: 219821925,
      totalCost: 245709000,
      rules: { effectiveFrom: '2026-01', estimated: false, personalDeduction: 15500000 },
      issues: [{ severity: 'WARNING', excelRow: 12, message: 'Có ngày ký HĐLĐ nhưng không đóng BHXH' }],
    },
  ],
  events: [],
});

const lines = [
  {
    excelRow: 10,
    employeeCode: 'DOIT2401',
    employeeName: 'Phạm Văn An',
    fileName: 'Phạm Văn An',
    mappedManually: false,
    amounts: {},
    thucNhan: 16546425,
    tongChiPhi: 18425500,
    thueTncn: 0,
    tongBhNld: 0,
    archived: true,
  },
];

const renderPage = (canApprove: boolean) =>
  render(
    <MemoryRouter initialEntries={['/bang-luong/2026-06']}>
      <Routes>
        <Route path="/bang-luong/:period" element={<PeriodDetail canApprove={canApprove} />} />
      </Routes>
    </MemoryRouter>,
  );

describe('PeriodDetail', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  const routeGets = (status: string) =>
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/payroll/periods/2026-06') return Promise.resolve({ data: detail(status) } as any);
      if (url.endsWith('/lines')) return Promise.resolve({ data: lines } as any);
      if (url === 'api/payroll/fields') return Promise.resolve({ data: [] } as any);
      return Promise.resolve({ data: [] } as any);
    });

  it('hiện tổng, luật áp dụng và cảnh báo từ bản chụp; Kế toán không có nút duyệt', async () => {
    routeGets('NHAP');
    renderPage(false);
    expect(await screen.findByText('219.821.925')).toBeTruthy();
    expect(screen.getByText('245.709.000')).toBeTruthy();
    expect(screen.getByText(/Luật áp dụng: mốc 01\/2026/)).toBeTruthy();
    expect(screen.getByText(/Có ngày ký HĐLĐ nhưng không đóng BHXH/)).toBeTruthy();
    expect(await screen.findByText('16.546.425')).toBeTruthy();
    // Dòng của hồ sơ đã lưu trữ vẫn hiện, kèm nhãn.
    expect(screen.getByTestId('archived-badge').textContent).toBe('Lưu trữ');
    expect(screen.queryByRole('button', { name: 'Duyệt kỳ' })).toBeNull();
  });

  it('khối Phiếu lương chỉ hiện khi kỳ đã duyệt', async () => {
    routeGets('NHAP');
    const { unmount } = renderPage(false);
    expect(await screen.findByText('219.821.925')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Phiếu lương' })).toBeNull();
    unmount();
    routeGets('DA_DUYET');
    renderPage(false);
    expect(await screen.findByRole('heading', { name: 'Phiếu lương' })).toBeTruthy();
  });

  it('Quản trị duyệt kỳ', async () => {
    routeGets('NHAP');
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderPage(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Duyệt kỳ' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/periods/2026-06/approve'));
  });

  it('duyệt khi bảng công chưa chốt: vẫn duyệt, hiện cảnh báo', async () => {
    routeGets('NHAP');
    mocked.post.mockResolvedValue({ data: { period: '2026-06', status: 'DA_DUYET', warnings: ['Bảng công tháng này chưa chốt'] } } as any);
    renderPage(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Duyệt kỳ' }));
    expect(await screen.findByText(/Bảng công tháng này chưa chốt/)).toBeTruthy();
  });

  it('Quản trị mở lại kỳ phải nhập lý do', async () => {
    routeGets('DA_DUYET');
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderPage(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Mở lại' }));
    fireEvent.change(screen.getByLabelText('Lý do mở lại'), { target: { value: 'Sai phụ cấp' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mở lại' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/periods/2026-06/reopen', { reason: 'Sai phụ cấp' }));
  });
});
