import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import DecisionsSection from './decisions-section';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const decisions = [
  {
    decision: {
      id: 9,
      decisionNo: 'DOIT/2026/QĐCNS/1',
      signDate: '2026-10-21',
      type: 'KHACH_HANG',
      unit: 'LP Bank',
      fromDate: '2026-11-01',
      toDate: '2027-01-31',
    },
    documents: [{ id: 21, kind: 'DOCX', fileName: 'DOIT-2026-QĐCNS-1 Lê Văn Tâm.docx', createdAt: '2026-10-21T03:00:00Z' }],
  },
];

describe('DecisionsSection', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/employees/5/assignment-decisions') return Promise.resolve({ data: decisions } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('liệt kê quyết định; Word chưa có PDF thì có nút Tạo lại PDF', async () => {
    render(<DecisionsSection employeeId={5} canWrite onCreated={vi.fn()} />);
    expect(await screen.findByText('DOIT/2026/QĐCNS/1')).toBeTruthy();
    expect(screen.getByText('LP Bank · 01/11/2026 – 31/01/2027')).toBeTruthy();
    mocked.post.mockResolvedValue({ data: { pdf: { id: 22 }, pdfError: null } } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo lại PDF DOIT/2026/QĐCNS/1' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/assignment-decisions/9/documents/pdf'));
  });

  it('nút cử nhân sự mở hộp thoại', async () => {
    render(<DecisionsSection employeeId={5} canWrite onCreated={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Cử đi công tác / làm tại khách hàng' }));
    expect(await screen.findByRole('heading', { name: 'Cử đi công tác / làm tại khách hàng' })).toBeTruthy();
  });

  it('Kế toán / Ban giám đốc: không hiện mục quyết định (văn bản có CCCD), không gọi API', () => {
    render(<DecisionsSection employeeId={5} canWrite={false} onCreated={vi.fn()} />);
    expect(screen.queryByRole('button', { name: 'Cử đi công tác / làm tại khách hàng' })).toBeNull();
    expect(mocked.get).not.toHaveBeenCalled();
  });

  it('hồ sơ đã lưu trữ (xem được, không sửa): có danh sách và tải file, không có nút cử nhân sự / Tạo lại PDF', async () => {
    render(<DecisionsSection employeeId={5} canWrite={false} canView onCreated={vi.fn()} />);
    expect(await screen.findByText('DOIT/2026/QĐCNS/1')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tải Word DOIT/2026/QĐCNS/1' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Cử đi công tác / làm tại khách hàng' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Tạo lại PDF DOIT/2026/QĐCNS/1' })).toBeNull();
  });
});
