import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { resetNumberingCache } from 'app/modules/settings/use-numbering';

import DocumentNumbers from './document-numbers';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const rows = [
  { type: 'HDLD', code: 'HĐLĐ', year: 2026, nextValue: 5, lastIssued: 4, nextNumber: 'DOIT/2026/HĐLĐ/5' },
  { type: 'HDTV', code: 'HĐTV', year: 2026, nextValue: 1, lastIssued: 0, nextNumber: 'DOIT/2026/HĐTV/1' },
];

describe('DocumentNumbers', () => {
  beforeEach(() => {
    resetNumberingCache();
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: rows } as any);
  });

  it('tải dãy số của năm và hiện số sẽ cấp tiếp', async () => {
    render(<DocumentNumbers canEdit={false} initialYear={2026} />);
    expect(await screen.findByText('DOIT/2026/HĐLĐ/5')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/admin/document-sequences', { params: { year: 2026 } });
    expect(screen.getByText('Hợp đồng lao động')).toBeTruthy();
    // Nhân sự chỉ xem: không có ô sửa số bắt đầu.
    expect(screen.queryByLabelText('Số bắt đầu HĐLĐ')).toBeNull();
  });

  it('câu mô tả dùng tiền tố hiệu lực lấy từ cấu hình', async () => {
    mocked.get.mockImplementation((url: string) =>
      Promise.resolve({ data: url === 'api/company-setting/numbering' ? { documentPrefix: 'ABC' } : rows } as any),
    );
    render(<DocumentNumbers canEdit={false} initialYear={2026} />);
    expect(await screen.findByText('ABC/năm/mã loại/số')).toBeTruthy();
  });

  it('Quản trị đặt số bắt đầu', async () => {
    mocked.put.mockResolvedValue({ data: { ...rows[0], nextValue: 112, nextNumber: 'DOIT/2026/HĐLĐ/112' } } as any);
    render(<DocumentNumbers canEdit initialYear={2026} />);
    const input = await screen.findByLabelText('Số bắt đầu HĐLĐ');
    fireEvent.change(input, { target: { value: '112' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu số HĐLĐ' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/admin/document-sequences/HDLD/2026', { nextValue: 112 }));
    expect(await screen.findByText('DOIT/2026/HĐLĐ/112')).toBeTruthy();
  });

  it('server từ chối số nhỏ hơn số đã cấp thì hiện câu báo', async () => {
    mocked.put.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'error.sequencebelowused', detail: 'Số bắt đầu phải lớn hơn số lớn nhất đã cấp trong năm (4)' },
      },
    });
    render(<DocumentNumbers canEdit initialYear={2026} />);
    const input = await screen.findByLabelText('Số bắt đầu HĐLĐ');
    fireEvent.change(input, { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu số HĐLĐ' }));
    expect(await screen.findByText('Số bắt đầu phải lớn hơn số lớn nhất đã cấp trong năm (4)')).toBeTruthy();
  });

  it('đổi năm thì tải lại', async () => {
    render(<DocumentNumbers canEdit={false} initialYear={2026} />);
    await screen.findByText('DOIT/2026/HĐLĐ/5');
    fireEvent.change(screen.getByLabelText('Năm'), { target: { value: '2027' } });
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/admin/document-sequences', { params: { year: 2027 } }));
  });
});
