import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { HrLeave } from './hr-leave';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const balances = [
  {
    employeeId: 11,
    code: 'NV0011',
    fullName: 'Trần Bình',
    departmentName: 'Kinh doanh',
    annual: { previousYear: 1, currentYear: 4, total: 5, requestable: 6 },
    compensatory: { previousYear: 0, currentYear: 8, total: 8 },
  },
  {
    employeeId: 12,
    code: 'NV0012',
    fullName: 'Lê Chi',
    departmentName: 'Kỹ thuật',
    annual: { previousYear: 0, currentYear: 2, total: 2, requestable: 3 },
    compensatory: { previousYear: 0, currentYear: 0, total: 0 },
  },
];

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

const render = (ui: React.ReactElement) => rtlRender(<MemoryRouter>{ui}</MemoryRouter>);

describe('HrLeave', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/leave/balances') return Promise.resolve({ data: balances } as any);
      if (url === 'api/leave/settings') return Promise.resolve({ data: settings } as any);
      if (url === 'api/departments') return Promise.resolve({ data: [{ id: 3, name: 'Kinh doanh' }] } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('bảng số dư cả công ty, tìm theo tên', async () => {
    render(<HrLeave canEditSettings={false} />);
    expect(await screen.findByText('Trần Bình')).toBeTruthy();
    expect(screen.getByText('Lê Chi')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Tìm nhân viên'), { target: { value: 'chi' } });
    expect(screen.queryByText('Trần Bình')).toBeNull();
    expect(screen.getByText('Lê Chi')).toBeTruthy();
  });

  it('điều chỉnh bắt buộc ghi chú', async () => {
    mocked.post.mockResolvedValue({ data: [] } as any);
    render(<HrLeave canEditSettings={false} />);
    const row = await screen.findByTestId('balance-11');
    fireEvent.click(within(row).getByRole('button', { name: 'Điều chỉnh' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Số điều chỉnh'), { target: { value: '1,5' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Ghi điều chỉnh' }));
    expect(await within(dialog).findByText('Phải ghi lý do điều chỉnh')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText('Lý do'), { target: { value: 'Bù phép tháng 9' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Ghi điều chỉnh' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/leave/adjustments', {
        employeeId: 11,
        pool: 'PHEP_NAM',
        bucket: 'NAM_NAY',
        amount: 1.5,
        note: 'Bù phép tháng 9',
      }),
    );
  });

  it('HR chỉ xem cài đặt; Quản trị sửa được', async () => {
    const { unmount } = render(<HrLeave canEditSettings={false} />);
    fireEvent.click(await screen.findByRole('tab', { name: 'Cài đặt' }));
    expect(((await screen.findByLabelText(/Ngưỡng chuyển giám đốc duyệt/)) as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu cài đặt' })).toBeNull();
    unmount();

    mocked.put.mockResolvedValue({ data: settings } as any);
    render(<HrLeave canEditSettings />);
    fireEvent.click(await screen.findByRole('tab', { name: 'Cài đặt' }));
    const input = (await screen.findByLabelText(/Ngưỡng chuyển giám đốc duyệt/)) as HTMLInputElement;
    expect(input.disabled).toBe(false);
    fireEvent.change(input, { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/leave/settings', { ...settings, directorThresholdDays: 4 }));
  });

  it('nhập số dư đầu kỳ: kiểm tra hiện lỗi từng dòng, không cho lưu khi còn lỗi', async () => {
    mocked.post.mockImplementation((url: string) => {
      if (url === 'api/leave/opening/check')
        return Promise.resolve({
          data: {
            rowCount: 2,
            rows: [{ excelRow: 2, code: 'NV0011', fullName: 'Trần Bình', annualRemaining: 3, annualPreviousYear: 1, compensatoryHours: 4 }],
            issues: [{ excelRow: 3, code: 'NV9999', message: 'Không tìm thấy mã nhân viên' }],
            errorCount: 1,
            canSave: false,
          },
        } as any);
      return Promise.resolve({ data: {} } as any);
    });
    render(<HrLeave canEditSettings={false} />);
    fireEvent.click(await screen.findByRole('tab', { name: 'Số dư đầu kỳ' }));
    const file = new File(['x'], 'so-du.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    fireEvent.change(screen.getByLabelText('File Excel số dư đầu kỳ'), { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra file' }));
    expect(await screen.findByText(/Không tìm thấy mã nhân viên/)).toBeTruthy();
    expect(screen.getByText(/Dòng 3/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Lưu số dư đầu kỳ' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
