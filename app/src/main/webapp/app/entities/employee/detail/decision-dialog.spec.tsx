import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import DecisionDialog from './decision-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = (onSaved = vi.fn()) => {
  render(<DecisionDialog employeeId={5} onClose={vi.fn()} onSaved={onSaved} />);
  return onSaved;
};

const fill = () => {
  fireEvent.change(screen.getByLabelText('Hình thức'), { target: { value: 'KHACH_HANG' } });
  fireEvent.change(screen.getByLabelText('Nơi đến'), { target: { value: 'khac' } });
  fireEvent.change(screen.getByLabelText('Đơn vị / khách hàng'), { target: { value: 'LP Bank' } });
  fireEvent.change(screen.getByLabelText('Địa chỉ'), { target: { value: '210 Trần Quang Khải, Hà Nội' } });
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '01/11/2026' } });
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '31/01/2027' } });
  fireEvent.change(screen.getByLabelText('Nội dung công việc'), { target: { value: 'Triển khai core banking' } });
  fireEvent.change(screen.getByLabelText('Chế độ'), { target: { value: 'Hưởng nguyên lương' } });
};

describe('DecisionDialog', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/assignment-decisions/defaults')
        return Promise.resolve({ data: { signerName: 'Trần Ngọc Nga', signerTitle: 'Giám đốc' } } as any);
      if (url === 'api/work-sites')
        return Promise.resolve({ data: [{ id: 3, name: 'VPBank', type: 'KHACH_HANG', address: 'HN', active: true }] } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('điền sẵn người ký theo cấu hình công ty', async () => {
    renderDialog();
    await waitFor(() => expect((screen.getByLabelText('Người ký') as HTMLInputElement).value).toBe('Trần Ngọc Nga'));
    expect((screen.getByLabelText('Chức vụ người ký') as HTMLInputElement).value).toBe('Giám đốc');
  });

  it('gửi quyết định với ngày ISO và báo số quyết định', async () => {
    mocked.post.mockResolvedValue({
      status: 201,
      data: { decision: { id: 9, decisionNo: 'DOIT/2026/QĐCNS/1' }, docx: { id: 1 }, pdf: { id: 2 }, pdfError: null },
    } as any);
    const onSaved = renderDialog();
    await waitFor(() => expect((screen.getByLabelText('Người ký') as HTMLInputElement).value).toBe('Trần Ngọc Nga'));
    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Tạo quyết định' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith(
        'api/employees/5/assignment-decisions',
        expect.objectContaining({
          type: 'KHACH_HANG',
          unit: 'LP Bank',
          address: '210 Trần Quang Khải, Hà Nội',
          fromDate: '2026-11-01',
          toDate: '2027-01-31',
          task: 'Triển khai core banking',
          benefits: 'Hưởng nguyên lương',
          signerName: 'Trần Ngọc Nga',
          signerTitle: 'Giám đốc',
        }),
      ),
    );
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith('Đã tạo quyết định số DOIT/2026/QĐCNS/1.'));
  });

  it('chồng lấn lịch sử vị trí: hiện nguyên câu của máy chủ', async () => {
    mocked.post.mockRejectedValue({
      response: {
        status: 400,
        data: {
          message: 'error.assignmentconflict',
          detail: 'Giai đoạn 01/11/2026 – 31/01/2027 trùng với "LP Bank" (01/11/2026 – 31/01/2027).',
        },
      },
    });
    renderDialog();
    await waitFor(() => expect((screen.getByLabelText('Người ký') as HTMLInputElement).value).toBe('Trần Ngọc Nga'));
    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Tạo quyết định' }));
    expect(await screen.findByText('Giai đoạn 01/11/2026 – 31/01/2027 trùng với "LP Bank" (01/11/2026 – 31/01/2027).')).toBeTruthy();
  });

  it('thiếu trường bắt buộc thì không gửi', async () => {
    renderDialog();
    await waitFor(() => expect((screen.getByLabelText('Người ký') as HTMLInputElement).value).toBe('Trần Ngọc Nga'));
    fireEvent.click(screen.getByRole('button', { name: 'Tạo quyết định' }));
    expect((await screen.findAllByText('Bắt buộc nhập')).length).toBeGreaterThan(0);
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('PDF chưa tạo được: vẫn lưu, báo kèm', async () => {
    mocked.post.mockResolvedValue({
      status: 201,
      data: {
        decision: { id: 9, decisionNo: 'DOIT/2026/QĐCNS/1' },
        docx: { id: 1 },
        pdf: null,
        pdfError: 'Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi',
      },
    } as any);
    const onSaved = renderDialog();
    await waitFor(() => expect((screen.getByLabelText('Người ký') as HTMLInputElement).value).toBe('Trần Ngọc Nga'));
    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Tạo quyết định' }));
    await waitFor(() =>
      expect(onSaved).toHaveBeenCalledWith(
        'Đã tạo quyết định số DOIT/2026/QĐCNS/1. Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi; bấm "Tạo lại PDF" sau.',
      ),
    );
  });
});
