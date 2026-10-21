import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { downloadFile } from 'app/shared/util/download';
import PayslipPanel from './payslip-panel';

vi.mock('axios');
vi.mock('app/shared/util/download', () => ({ downloadFile: vi.fn(() => Promise.resolve()) }));
const mocked = vi.mocked(axios, true);

const rows = [
  {
    employeeId: 1,
    employeeCode: 'DOIT2401',
    fullName: 'Phạm Văn An',
    email: 'an@doit.vn',
    status: 'SENT',
    version: 1,
    sentAt: '2026-10-22T02:15:00Z',
    staleVersion: false,
  },
  {
    employeeId: 2,
    employeeCode: 'DOIT2402',
    fullName: 'Lê Văn Bình',
    email: null,
    status: 'FAILED',
    version: 1,
    error: 'Chưa có email trên hồ sơ',
    staleVersion: false,
  },
  { employeeId: 3, employeeCode: 'DOIT2403', fullName: 'Hoàng Văn Cường', email: 'cuong@doit.vn', status: null, staleVersion: false },
  {
    employeeId: 4,
    employeeCode: 'DOIT2505',
    fullName: 'Vũ Đức Dũng',
    email: 'dung@doit.vn',
    status: 'SENT',
    version: 1,
    sentAt: '2026-10-22T02:15:00Z',
    staleVersion: true,
  },
];

describe('PayslipPanel', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: rows } as any);
  });

  it('hiện tình trạng từng người', async () => {
    render(<PayslipPanel period="2026-06" />);
    expect(await screen.findAllByText('Đã gửi lúc 22/10/2026 09:15')).toHaveLength(2);
    expect(screen.getByText('Lỗi: Chưa có email trên hồ sơ')).toBeTruthy();
    expect(screen.getByText('Chưa gửi')).toBeTruthy();
    expect(screen.getByText(/Theo phiên bản cũ/)).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/payroll/periods/2026-06/payslips');
  });

  it('gửi cho người chưa nhận, gửi lại một người, tải phiếu', async () => {
    mocked.post.mockResolvedValue({ status: 202, data: rows } as any);
    render(<PayslipPanel period="2026-06" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi phiếu lương cho người chưa nhận' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/periods/2026-06/payslips/send', { employeeIds: null }));
    fireEvent.click(screen.getAllByRole('button', { name: 'Gửi lại' })[0]);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/periods/2026-06/payslips/send', { employeeIds: [1] }));
    fireEvent.click(screen.getAllByRole('button', { name: 'Tải' })[0]);
    await waitFor(() =>
      expect(downloadFile).toHaveBeenCalledWith('api/payroll/periods/2026-06/payslips/1/pdf', 'PhieuLuong_06-2026_DOIT2401.pdf'),
    );
  });

  it('chưa cấu hình SMTP thì báo lỗi tiếng Việt', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.mailnotconfigured' } } });
    render(<PayslipPanel period="2026-06" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Gửi phiếu lương cho người chưa nhận' }));
    expect(await screen.findByText(/Chưa cấu hình máy chủ gửi email/)).toBeTruthy();
  });
});
