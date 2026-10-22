import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import LeaveForm from './leave-form';
import { ILeaveBalance, previewDays } from './leave-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const balance: ILeaveBalance = {
  employeeId: 7,
  annual: { previousYear: 2, currentYear: 4.5, total: 6.5, requestable: 7.5, previousYearExpiry: '2027-03-31' },
  compensatory: { previousYear: 0, currentYear: 12, total: 12 },
  sick: { usedDays: 2, limitDays: 30 },
  overtime: { monthHours: 6, monthCap: 40 },
};

const setup = (onSubmitted = vi.fn()) => {
  render(<LeaveForm balance={balance} onSubmitted={onSubmitted} />);
  return onSubmitted;
};

const pick = (label: RegExp | string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

const optionValues = (label: RegExp | string) =>
  within(screen.getByLabelText(label) as HTMLSelectElement)
    .getAllByRole('option')
    .map(o => (o as HTMLOptionElement).value);

describe('LeaveForm', () => {
  beforeEach(() => {
    mocked.post.mockReset();
  });

  it('việc riêng: hiện chọn lý do; kết hôn tối đa 3 ngày, vượt thì không gửi', async () => {
    setup();
    expect(screen.queryByLabelText('Lý do nghỉ việc riêng')).toBeNull();
    pick('Loại đơn', 'VIEC_RIENG');
    pick('Lý do nghỉ việc riêng', 'KET_HON');
    expect(screen.getByText(/Tối đa 3 ngày/)).toBeTruthy();
    // 02/11/2026 (Thứ Hai) – 06/11/2026 (Thứ Sáu): 5 ngày làm việc
    pick('Từ ngày', '02/11/2026');
    pick('Đến ngày', '06/11/2026');
    expect(await screen.findByText(/vượt quá 3 ngày/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đơn' }));
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('nghỉ ốm: hiện ô đính kèm giấy nghỉ ốm', () => {
    setup();
    expect(screen.queryByLabelText(/Giấy chứng nhận nghỉ ốm/)).toBeNull();
    pick('Loại đơn', 'OM');
    expect(screen.getByLabelText(/Giấy chứng nhận nghỉ ốm/)).toBeTruthy();
  });

  it('đơn nhiều ngày: ngày đầu chỉ chọn được chiều, ngày cuối chỉ chọn được sáng', () => {
    setup();
    pick('Từ ngày', '02/11/2026');
    pick('Đến ngày', '02/11/2026');
    expect(optionValues('Thời lượng')).toEqual(['CA_NGAY', 'SANG', 'CHIEU']);
    pick('Đến ngày', '04/11/2026');
    expect(optionValues('Ngày đầu')).toEqual(['CA_NGAY', 'CHIEU']);
    expect(optionValues('Ngày cuối')).toEqual(['CA_NGAY', 'SANG']);
  });

  it('hiện số ngày ước tính và "Sau khi duyệt còn"; gửi đúng dữ liệu', async () => {
    mocked.post.mockResolvedValue({ data: { id: 5, days: 2, status: 'CHO_DUYET' } } as any);
    const done = setup();
    pick('Từ ngày', '02/11/2026');
    pick('Đến ngày', '03/11/2026');
    expect(screen.getByText(/Sau khi duyệt còn/).textContent).toContain('4,5 ngày');
    fireEvent.change(screen.getByLabelText('Ghi chú'), { target: { value: 'Về quê' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đơn' }));
    await waitFor(() => expect(done).toHaveBeenCalled());
    expect(mocked.post).toHaveBeenCalledWith('api/leave/requests', {
      type: 'PHEP_NAM',
      personalReason: null,
      fromDate: '2026-11-02',
      toDate: '2026-11-03',
      firstPart: 'CA_NGAY',
      lastPart: 'CA_NGAY',
      reasonText: 'Về quê',
    });
  });

  it('server báo không đủ số dư thì hiện số còn lại', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.insufficientbalance', remaining: 1.5 } } });
    setup();
    pick('Từ ngày', '02/11/2026');
    pick('Đến ngày', '03/11/2026');
    fireEvent.click(screen.getByRole('button', { name: 'Gửi đơn' }));
    expect(await screen.findByText(/còn 1,5 ngày/)).toBeTruthy();
  });

  it('số ngày ước tính bỏ cuối tuần và trừ nửa ngày', () => {
    expect(previewDays('2026-10-30', '2026-11-02')).toBe(2);
    expect(previewDays('2026-11-02', '2026-11-04', 'CHIEU', 'SANG')).toBe(2);
    expect(previewDays('2026-11-02', '2026-11-02', 'SANG')).toBe(0.5);
  });
});
