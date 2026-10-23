import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import LeaveSettings from './leave-settings';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const settings = {
  advanceDays: 1,
  directorThresholdDays: 3,
  otMonthCap: 40,
  otYearCap: 200,
  carryExpiryMonth: 3,
  carryExpiryDay: 31,
  pastRequestDays: 30,
  otConfirmDay: 5,
};

describe('LeaveSettings', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: settings } as any);
  });

  it('chỉ xem khi không có quyền sửa', async () => {
    render(<LeaveSettings canEdit={false} />);
    expect(((await screen.findByLabelText(/Ngưỡng chuyển giám đốc duyệt/)) as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu cài đặt' })).toBeNull();
  });

  it('Quản trị sửa và lưu, báo quy định chỉ áp dụng từ nay', async () => {
    mocked.put.mockResolvedValue({ data: { ...settings, directorThresholdDays: 4 } } as any);
    render(<LeaveSettings />);
    const input = (await screen.findByLabelText(/Ngưỡng chuyển giám đốc duyệt/)) as HTMLInputElement;
    expect(input.disabled).toBe(false);
    fireEvent.change(input, { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/leave/settings', { ...settings, directorThresholdDays: 4 }));
    expect(await screen.findByText(/Quy định mới chỉ áp dụng từ nay/)).toBeTruthy();
  });

  it('giá trị ngoài khoảng: báo lỗi, không gọi API', async () => {
    render(<LeaveSettings />);
    fireEvent.change(await screen.findByLabelText('Hạn dùng phép năm trước: tháng'), { target: { value: '13' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt' }));
    expect(await screen.findByText('Hạn dùng phép năm trước: tháng: nhập số nguyên từ 1 đến 12')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });
});
