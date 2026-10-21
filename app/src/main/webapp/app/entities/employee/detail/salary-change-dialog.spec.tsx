import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import SalaryChangeDialog from './salary-change-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = () => render(<SalaryChangeDialog employeeId={1} dependants={0} isOpen toggle={vi.fn()} onSaved={vi.fn()} />);

describe('SalaryChangeDialog', () => {
  beforeEach(() => {
    mocked.get.mockReset();
  });

  it('kỳ chưa có biểu thuế (gợi ý trả lý do) thì hiện lý do và nhắc nhập Gross thủ công', async () => {
    mocked.get.mockResolvedValue({ data: { gross: null, estimated: false, reason: 'Chưa có biểu thuế cho kỳ này' } } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày hiệu lực'), { target: { value: '01/03/2019' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '20.000.000' } });
    expect(await screen.findByText('Chưa có biểu thuế cho kỳ này — nhập Gross thủ công.')).toBeTruthy();
  });

  it('kỳ tương lai: điền Gross ước tính và ghi rõ theo quy định mới nhất', async () => {
    mocked.get.mockResolvedValue({ data: { gross: 23000000, estimated: true, rulesFrom: '2026-07' } } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày hiệu lực'), { target: { value: '01/03/2027' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '20.000.000' } });
    expect(await screen.findByText('Ước tính theo quy định mới nhất (tháng 07/2026)')).toBeTruthy();
    await waitFor(() => expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('23.000.000'));
  });

  it('có gợi ý thì điền Gross và không hiện lời nhắc', async () => {
    mocked.get.mockResolvedValue({ data: { gross: 23000000, estimated: false } } as any);
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày hiệu lực'), { target: { value: '01/03/2026' } });
    fireEvent.change(screen.getByLabelText('Lương tính theo'), { target: { value: 'NET' } });
    fireEvent.change(screen.getByLabelText('Mức lương'), { target: { value: '20.000.000' } });
    await waitFor(() => expect(screen.getByLabelText('Lương Gross (cho kế toán)').value).toBe('23.000.000'));
    expect(screen.queryByText(/Chưa có biểu thuế/)).toBeNull();
    expect(screen.queryByText(/Ước tính/)).toBeNull();
  });
  it('hẹn xét lại ngày: gửi nextReviewOverride dạng ISO', async () => {
    mocked.get.mockResolvedValue({ data: { gross: 23000000, estimated: false } } as any);
    mocked.put.mockResolvedValue({ data: {} } as any);
    render(
      <SalaryChangeDialog
        employeeId={1}
        dependants={0}
        salaryChange={{ id: 4, effectiveDate: '2025-11-01', amount: 20000000, basis: 'GROSS', reviewCycleMonths: 12 }}
        isOpen
        toggle={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText('Hẹn xét lại ngày'), { target: { value: '01/02/2027' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/salary-changes/4', expect.objectContaining({ nextReviewOverride: '2027-02-01' })),
    );
  });
});
