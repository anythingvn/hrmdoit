import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';

import ImportWizard from './import-wizard';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const columns = [
  { field: 'LUONG_CO_BAN', label: 'Mức lương / Lương cơ bản', column: 'F', required: false },
  { field: 'THUC_NHAN', label: 'Lương thực nhận', column: 'AJ', required: true },
];

const draft = (over: Record<string, unknown> = {}) => ({
  id: 7,
  period: '2026-06',
  fileName: 'bang-luong.xlsx',
  status: 'DANG_LAM',
  warningsAcknowledged: false,
  columns,
  missingRequired: [],
  rows: [
    {
      excelRow: 10,
      fileCode: 'DOIT2401',
      fileName: 'Phạm Văn An',
      position: 'Lập trình viên',
      employeeId: 1,
      employeeCode: 'DOIT2401',
      employeeName: 'Phạm Văn An',
      mappedManually: false,
    },
    {
      excelRow: 21,
      fileCode: null,
      fileName: 'Mai Văn Phúc',
      position: 'Thực tập',
      employeeId: null,
      employeeCode: null,
      employeeName: null,
      mappedManually: false,
    },
  ],
  issues: [
    {
      severity: 'ERROR',
      excelRow: 21,
      employeeCode: null,
      field: null,
      fieldLabel: null,
      message: 'Thiếu mã nhân viên (Mai Văn Phúc, Thực tập)',
    },
    {
      severity: 'WARNING',
      excelRow: 0,
      employeeCode: null,
      field: null,
      fieldLabel: null,
      message: 'Tiêu đề file ghi kỳ 08/2025 nhưng đang import cho kỳ 06/2026.',
    },
  ],
  errorCount: 1,
  warningCount: 1,
  canConfirm: false,
  ...over,
});

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/bang-luong/import/:id" element={<ImportWizard />} />
        <Route path="/bang-luong/:period" element={<div>Trang kỳ lương</div>} />
      </Routes>
    </MemoryRouter>,
  );

describe('ImportWizard', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
  });

  it('bước 1: kỳ lương điền sẵn tháng trước; thiếu file thì báo riêng', () => {
    renderAt('/bang-luong/import/moi');
    const now = new Date();
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const expected = `${String(prev.getMonth() + 1).padStart(2, '0')}/${prev.getFullYear()}`;
    expect((screen.getByLabelText('Kỳ lương (MM/yyyy)') as HTMLInputElement).value).toBe(expected);
    fireEvent.click(screen.getByRole('button', { name: 'Tải lên và đọc file' }));
    expect(screen.getByText('Chọn file bảng lương (.xlsx)')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Kỳ lương (MM/yyyy)'), { target: { value: '13/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải lên và đọc file' }));
    expect(screen.getByText('Nhập kỳ lương dạng MM/yyyy, ví dụ 06/2026')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('bước 1 gửi file kèm kỳ rồi sang bước nhận diện cột', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: draft() } as any);
    mocked.get.mockResolvedValue({ data: draft() } as any);
    renderAt('/bang-luong/import/moi');
    fireEvent.change(screen.getByLabelText('Kỳ lương (MM/yyyy)'), { target: { value: '06/2026' } });
    fireEvent.change(screen.getByLabelText('File bảng lương (.xlsx)'), {
      target: { files: [new File(['PK'], 'bang-luong.xlsx')] },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Tải lên và đọc file' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/imports', expect.any(FormData)));
    const form = mocked.post.mock.calls[0][1] as FormData;
    expect(form.get('period')).toBe('2026-06');
    expect(await screen.findByRole('heading', { name: 'Bước 2. Nhận diện cột' })).toBeTruthy();
    expect(screen.getByText('Lương thực nhận')).toBeTruthy();
    expect(screen.getByText('AJ')).toBeTruthy();
  });

  it('bước 2 báo cột bắt buộc thiếu và không cho đi tiếp', async () => {
    mocked.get.mockResolvedValue({ data: draft({ missingRequired: ['Lương thực nhận'] }) } as any);
    renderAt('/bang-luong/import/7');
    expect(await screen.findByText(/Thiếu cột bắt buộc: Lương thực nhận/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Tiếp tục' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('bước 3 hiện lỗi theo dòng; chỉ đi tiếp khi canConfirm; xác nhận cảnh báo gọi API', async () => {
    mocked.get.mockResolvedValue({ data: draft() } as any);
    renderAt('/bang-luong/import/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Tiếp tục' }));
    expect(await screen.findByRole('heading', { name: 'Bước 3. Kiểm tra' })).toBeTruthy();
    expect(screen.getByText('Thiếu mã nhân viên (Mai Văn Phúc, Thực tập)')).toBeTruthy();
    expect(screen.getByText('Tiêu đề file ghi kỳ 08/2025 nhưng đang import cho kỳ 06/2026.')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Tiếp tục' }) as HTMLButtonElement).disabled).toBe(true);
    mocked.post.mockResolvedValue({ data: draft({ warningsAcknowledged: true }) } as any);
    fireEvent.click(screen.getByLabelText('Đã xem và xác nhận các cảnh báo'));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/imports/7/acknowledge-warnings'));
  });

  it('bước 4 xác nhận lưu rồi chuyển sang trang kỳ lương', async () => {
    mocked.get.mockResolvedValue({ data: draft({ errorCount: 0, issues: [], warningCount: 0, canConfirm: true }) } as any);
    mocked.post.mockResolvedValue({ status: 201, data: { period: '2026-06', version: 1 } } as any);
    renderAt('/bang-luong/import/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Tiếp tục' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Tiếp tục' }));
    expect(await screen.findByRole('heading', { name: 'Bước 4. Xác nhận' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu bảng lương' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/imports/7/confirm'));
    expect(await screen.findByText('Trang kỳ lương')).toBeTruthy();
  });
});
