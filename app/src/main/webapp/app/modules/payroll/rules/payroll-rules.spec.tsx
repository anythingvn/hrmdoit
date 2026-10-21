import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import PayrollRules from './payroll-rules';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const data = {
  knownThrough: '2026-12',
  milestones: [
    {
      id: 6,
      effectiveFrom: '2026-07',
      personalDeduction: 15500000,
      dependantDeduction: 6200000,
      socialInsuranceCap: 50600000,
      unemploymentInsuranceCap: 106200000,
      brackets: [
        { upTo: 10000000, rate: 0.05 },
        { upTo: null, rate: 0.35 },
      ],
      legalBasis: 'NĐ 161/2026',
      createdBy: 'system',
    },
  ],
};

describe('PayrollRules', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data } as any);
  });

  it('hiện các mốc với tiền và thuế suất định dạng Việt Nam', async () => {
    render(<PayrollRules canEdit={false} />);
    const card = await screen.findByTestId('milestone-6');
    expect(within(card).getByText('Hiệu lực từ 07/2026')).toBeTruthy();
    expect(within(card).getByText('15.500.000')).toBeTruthy();
    expect(within(card).getByText('Đến 10.000.000')).toBeTruthy();
    expect(within(card).getByText('5%')).toBeTruthy();
    expect(within(card).getByText('Phần còn lại')).toBeTruthy();
    expect(within(card).getByText('35%')).toBeTruthy();
    expect(screen.getByText(/Đã biết chắc luật đến tháng 12\/2026/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Thêm mốc' })).toBeNull();
  });

  it('Quản trị thêm mốc: gửi đúng dữ liệu', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<PayrollRules canEdit />);
    await screen.findByTestId('milestone-6');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm mốc' }));
    fireEvent.change(screen.getByLabelText('Hiệu lực từ tháng'), { target: { value: '2027-01' } });
    fireEvent.change(screen.getByLabelText('Giảm trừ bản thân'), { target: { value: '16.000.000' } });
    fireEvent.change(screen.getByLabelText('Giảm trừ người phụ thuộc'), { target: { value: '6.400.000' } });
    fireEvent.change(screen.getByLabelText('Trần đóng BHXH, BHYT'), { target: { value: '50.600.000' } });
    fireEvent.change(screen.getByLabelText('Trần đóng BHTN'), { target: { value: '106.200.000' } });
    fireEvent.change(screen.getByLabelText('Mức trên bậc 1'), { target: { value: '10.000.000' } });
    fireEvent.change(screen.getByLabelText('Thuế suất bậc 1 (%)'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Thuế suất bậc 2 (%)'), { target: { value: '35' } });
    fireEvent.change(screen.getByLabelText('Căn cứ văn bản'), { target: { value: 'NQ mới' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mốc' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/payroll/rules', {
        effectiveFrom: '2027-01',
        personalDeduction: 16000000,
        dependantDeduction: 6400000,
        socialInsuranceCap: 50600000,
        unemploymentInsuranceCap: 106200000,
        brackets: [
          { upTo: 10000000, rate: 0.05 },
          { upTo: null, rate: 0.35 },
        ],
        legalBasis: 'NQ mới',
      }),
    );
  });

  it('máy chủ từ chối thì hiện câu báo', async () => {
    mocked.post.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'error.rulesbeforeapproved', detail: 'Mốc mới phải có hiệu lực sau kỳ lương đã duyệt 06/2026' },
      },
    });
    render(<PayrollRules canEdit />);
    await screen.findByTestId('milestone-6');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm mốc' }));
    fireEvent.change(screen.getByLabelText('Hiệu lực từ tháng'), { target: { value: '2026-05' } });
    fireEvent.change(screen.getByLabelText('Giảm trừ bản thân'), { target: { value: '16.000.000' } });
    fireEvent.change(screen.getByLabelText('Giảm trừ người phụ thuộc'), { target: { value: '6.400.000' } });
    fireEvent.change(screen.getByLabelText('Trần đóng BHXH, BHYT'), { target: { value: '50.600.000' } });
    fireEvent.change(screen.getByLabelText('Trần đóng BHTN'), { target: { value: '106.200.000' } });
    fireEvent.change(screen.getByLabelText('Mức trên bậc 1'), { target: { value: '10.000.000' } });
    fireEvent.change(screen.getByLabelText('Thuế suất bậc 1 (%)'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Thuế suất bậc 2 (%)'), { target: { value: '35' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mốc' }));
    expect(await screen.findByText('Mốc mới phải có hiệu lực sau kỳ lương đã duyệt 06/2026')).toBeTruthy();
  });

  it('Quản trị đổi tháng đã biết chắc luật', async () => {
    mocked.put.mockResolvedValue({ data: { knownThrough: '2027-12' } } as any);
    render(<PayrollRules canEdit />);
    await screen.findByTestId('milestone-6');
    fireEvent.change(screen.getByLabelText('Đã biết chắc luật đến tháng'), { target: { value: '2027-12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu tháng' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/payroll/rules/known-through', { knownThrough: '2027-12' }));
  });
});
