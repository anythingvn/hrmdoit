import { vi } from 'vitest';
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { downloadFile } from 'app/shared/util/download';
import HrReport from './hr-report';

vi.mock('app/shared/util/download', () => ({ downloadFile: vi.fn(() => Promise.resolve()) }));

describe('HrReport', () => {
  it('chọn khoảng tháng rồi tải', async () => {
    render(<HrReport />);
    fireEvent.change(screen.getByLabelText('Từ tháng (MM/yyyy)'), { target: { value: '01/2026' } });
    fireEvent.change(screen.getByLabelText('Đến tháng (MM/yyyy)'), { target: { value: '12/2026' } });
    fireEvent.change(screen.getByLabelText('Gom chi phí theo'), { target: { value: 'POSITION' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải báo cáo' }));
    await waitFor(() =>
      expect(downloadFile).toHaveBeenCalledWith('api/reports/hr.xlsx', expect.stringContaining('.xlsx'), {
        from: '2026-01',
        to: '2026-12',
        groupBy: 'POSITION',
      }),
    );
  });

  it('từ tháng sau đến tháng thì báo lỗi, không tải', () => {
    vi.mocked(downloadFile).mockClear();
    render(<HrReport />);
    fireEvent.change(screen.getByLabelText('Từ tháng (MM/yyyy)'), { target: { value: '12/2026' } });
    fireEvent.change(screen.getByLabelText('Đến tháng (MM/yyyy)'), { target: { value: '01/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải báo cáo' }));
    expect(screen.getByText(/Từ tháng phải trước hoặc bằng Đến tháng/)).toBeTruthy();
    expect(downloadFile).not.toHaveBeenCalled();
  });
});
