import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { downloadFile } from 'app/shared/util/download';
import OwnPayslips from './own-payslips';

vi.mock('axios');
vi.mock('app/shared/util/download', () => ({ downloadFile: vi.fn(() => Promise.resolve()) }));
const mocked = vi.mocked(axios, true);

describe('OwnPayslips', () => {
  beforeEach(() => mocked.get.mockReset());

  it('liệt kê kỳ đã gửi và tải PDF', async () => {
    mocked.get.mockResolvedValue({ data: [{ period: '2026-06', version: 1, sentAt: '2026-10-22T02:15:00Z' }] } as any);
    render(<OwnPayslips />);
    expect(await screen.findByText('06/2026')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/account/employee/payslips');
    fireEvent.click(screen.getByRole('button', { name: 'Tải PDF' }));
    await waitFor(() => expect(downloadFile).toHaveBeenCalledWith('api/account/employee/payslips/2026-06/pdf', 'PhieuLuong_06-2026.pdf'));
  });

  it('chưa có phiếu nào', async () => {
    mocked.get.mockResolvedValue({ data: [] } as any);
    render(<OwnPayslips />);
    expect(await screen.findByText('Chưa có phiếu lương nào được gửi.')).toBeTruthy();
  });
});
