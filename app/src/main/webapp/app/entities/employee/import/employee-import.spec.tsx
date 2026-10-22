import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';

import { downloadFile } from 'app/shared/util/download';
import EmployeeImport from './employee-import';
import { IEmployeeImportCheck } from './employee-import.model';

vi.mock('axios');
vi.mock('app/shared/util/download', async orig => ({ ...(await orig<any>()), downloadFile: vi.fn(() => Promise.resolve()) }));
const mocked = vi.mocked(axios, true);

const check = (over: Partial<IEmployeeImportCheck> = {}): IEmployeeImportCheck => ({
  rowCount: 3,
  rows: [
    {
      excelRow: 2,
      fullName: 'Nguyễn Văn An',
      idNumber: '012345678901',
      personType: 'NHAN_VIEN',
      code: 'DOIT2601',
      codeProvisional: true,
      departmentName: 'Kinh doanh',
      managerRef: null,
    },
    {
      excelRow: 3,
      fullName: 'Trần Thị Bình',
      idNumber: '012345678902',
      personType: 'THUC_TAP',
      code: 'TT2601',
      codeProvisional: true,
      departmentName: 'Kinh doanh',
      managerRef: 'DOIT2601',
    },
    {
      excelRow: 4,
      fullName: 'Lê Văn Cường',
      idNumber: '012345678903',
      personType: 'DICH_VU',
      code: null,
      codeProvisional: false,
      departmentName: null,
      managerRef: null,
    },
  ],
  issues: [
    {
      severity: 'WARNING',
      excelRow: 3,
      fullName: 'Trần Thị Bình',
      field: 'startDate',
      fieldLabel: 'Ngày vào làm',
      message: 'Ngày vào làm ở tương lai',
    },
  ],
  newDepartments: [{ name: 'Kinh doanh', excelRows: [2, 3] }],
  errorCount: 0,
  warningCount: 1,
  canSave: true,
  ...over,
});

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/nhan-vien/nhap-excel']}>
      <Routes>
        <Route path="/nhan-vien/nhap-excel" element={<EmployeeImport />} />
        <Route path="/nhan-vien" element={<div>Danh sách</div>} />
      </Routes>
    </MemoryRouter>,
  );

const pickFile = async (data = check()) => {
  mocked.post.mockResolvedValueOnce({ data } as any);
  fireEvent.change(screen.getByLabelText('File danh sách nhân viên (.xlsx)'), {
    target: { files: [new File(['PK'], 'nhan-vien.xlsx')] },
  });
  await screen.findByRole('heading', { name: 'Bước 2. Kiểm tra' });
};

describe('EmployeeImport', () => {
  beforeEach(() => {
    mocked.post.mockReset();
    vi.mocked(downloadFile).mockClear();
  });

  it('downloads template', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Tải file mẫu' }));
    expect(downloadFile).toHaveBeenCalledWith('api/employees/import/template.xlsx', 'mau-nhap-nhan-vien.xlsx');
  });

  it('checks file then shows rows', async () => {
    renderPage();
    await pickFile();
    expect(mocked.post).toHaveBeenCalledWith('api/employees/import/check', expect.any(FormData));
    expect(screen.getByText('3 dòng · 0 lỗi · 1 cảnh báo')).toBeTruthy();
    expect(screen.getByText(/Phòng ban sẽ tạo/).textContent).toContain('Kinh doanh');
    expect(screen.getAllByText('dự kiến')).toHaveLength(2);
    expect(screen.getByText('HDDV (tự sinh)')).toBeTruthy();
    expect(screen.getByText('Dòng 3 – Ngày vào làm: Ngày vào làm ở tương lai')).toBeTruthy();
  });

  it('save disabled with errors; error report downloads via post blob', async () => {
    renderPage();
    await pickFile(check({ errorCount: 2, warningCount: 0, issues: [], canSave: false }));
    expect((screen.getByRole('button', { name: 'Lưu 3 hồ sơ' }) as HTMLButtonElement).disabled).toBe(true);
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    mocked.post.mockResolvedValueOnce({ data: new Blob(['x']) } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Tải báo cáo lỗi' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/import/issues.xlsx', expect.any(FormData), { responseType: 'blob' }),
    );
  });

  it('save needs warning acknowledgement', async () => {
    renderPage();
    await pickFile();
    const btn = screen.getByRole('button', { name: 'Lưu 3 hồ sơ' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    fireEvent.click(screen.getByLabelText('Đã xem cảnh báo'));
    expect(btn.disabled).toBe(false);
    mocked.post.mockResolvedValueOnce({ status: 201, data: { createdCount: 3, created: [], createdDepartments: [] } } as any);
    fireEvent.click(btn);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/import', expect.any(FormData)));
    const body = mocked.post.mock.calls[1][1] as FormData;
    expect(body.get('warningsAcknowledged')).toBe('true');
    expect(body.get('file')).toBeTruthy();
  });

  it('shows result', async () => {
    renderPage();
    await pickFile(check({ warningCount: 0, issues: [] }));
    mocked.post.mockResolvedValueOnce({
      status: 201,
      data: {
        createdCount: 3,
        created: [
          { excelRow: 2, fullName: 'Nguyễn Văn An', code: 'DOIT2601' },
          { excelRow: 3, fullName: 'Trần Thị Bình', code: 'TT2601' },
          { excelRow: 4, fullName: 'Lê Văn Cường', code: 'HDDV2601' },
        ],
        createdDepartments: ['Kinh doanh'],
      },
    } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu 3 hồ sơ' }));
    expect(await screen.findByText('Đã tạo 3 hồ sơ')).toBeTruthy();
    expect(screen.getByText('HDDV2601')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Về danh sách nhân viên' }).getAttribute('href')).toBe('/nhan-vien');
  });

  it('400 with check refreshes step 2', async () => {
    renderPage();
    await pickFile(check({ warningCount: 0, issues: [] }));
    mocked.post.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          message: 'error.importinvalid',
          detail: 'File còn lỗi',
          check: check({ errorCount: 1, warningCount: 0, canSave: false, issues: [] }),
        },
      },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu 3 hồ sơ' }));
    expect(await screen.findByText('3 dòng · 1 lỗi · 0 cảnh báo')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('File còn lỗi');
    expect(screen.getByRole('heading', { name: 'Bước 2. Kiểm tra' })).toBeTruthy();
  });

  it('choose another file', async () => {
    renderPage();
    await pickFile();
    fireEvent.click(screen.getByRole('button', { name: 'Chọn file khác' }));
    expect(screen.getByRole('heading', { name: 'Bước 1. Tải file' })).toBeTruthy();
  });
});
