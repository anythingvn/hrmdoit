import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { isoToVn } from 'app/entities/employee/vn-date';
import ReminderList from './reminder-list';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const items = [
  {
    type: 'PROBATION_RESULT_MISSING',
    typeLabel: 'Chưa có kết quả thử việc',
    employeeId: 7,
    employeeCode: 'DOIT2601',
    employeeName: 'Nguyễn Văn Thử',
    eventDate: '2026-10-19',
    daysLeft: -3,
    urgent: true,
    message: 'Thử việc đã kết thúc từ 19/10/2026 (3 ngày) nhưng chưa ghi nhận kết quả thử việc.',
    contractId: 41,
  },
  {
    type: 'SALARY_REVIEW',
    typeLabel: 'Đến hạn xét tăng lương',
    employeeId: 8,
    employeeCode: 'DOIT2602',
    employeeName: 'Trần Thị Lương',
    eventDate: '2026-11-10',
    daysLeft: 19,
    urgent: false,
    message: 'Đến hạn xét tăng lương ngày 10/11/2026, còn 19 ngày.',
    salaryChangeId: 55,
  },
];

const renderList = (canAct: boolean) =>
  render(
    <MemoryRouter>
      <ReminderList canAct={canAct} />
    </MemoryRouter>,
  );

describe('ReminderList', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.patch.mockReset();
    mocked.get.mockResolvedValue({ data: items } as any);
  });

  it('hiện mục quá hạn và lọc theo loại bằng nút dạng viên thuốc', async () => {
    renderList(true);
    expect(await screen.findByText('Quá hạn 3 ngày')).toBeTruthy();
    expect(screen.getByText('Còn 19 ngày')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/reminders/upcoming', { params: {} });
    expect(screen.getByRole('button', { name: 'Tất cả' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Thử việc' }));
    expect(screen.getByRole('button', { name: 'Thử việc' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByText('Nguyễn Văn Thử')).toBeTruthy();
    expect(screen.queryByText('Trần Thị Lương')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Sinh nhật' }));
    expect(screen.queryByText('Nguyễn Văn Thử')).toBeNull();
    expect(screen.getByText('Không có việc loại này trong 30 ngày tới.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Lương' }));
    expect(screen.getByText('Trần Thị Lương')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Tất cả' }));
    expect(screen.getByText('Nguyễn Văn Thử')).toBeTruthy();
    expect(screen.getByText('Trần Thị Lương')).toBeTruthy();
  });

  it('mỗi dòng: chip loại, tên · mã, ngày dd/MM/yyyy', async () => {
    renderList(false);
    expect(await screen.findByText('· DOIT2601')).toBeTruthy();
    expect(screen.getByText('19/10/2026')).toBeTruthy();
    expect(screen.getByTitle('Chưa có kết quả thử việc').textContent).toBe('Thử việc');
  });

  it('Ghi kết quả thử việc gửi PATCH hợp đồng', async () => {
    mocked.patch.mockResolvedValue({ data: {} } as any);
    renderList(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Ghi kết quả thử việc' }));
    fireEvent.change(screen.getByLabelText('Kết quả thử việc'), { target: { value: 'DAT' } });
    fireEvent.change(screen.getByLabelText('Ngày ghi kết quả'), { target: { value: '22/10/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(mocked.patch).toHaveBeenCalledWith('api/employment-contracts/41', {
        id: 41,
        probationResult: 'DAT',
        probationResultDate: '2026-10-22',
      }),
    );
  });

  it('Hẹn xét lại gửi PATCH điều chỉnh lương', async () => {
    mocked.patch.mockResolvedValue({ data: {} } as any);
    renderList(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Hẹn xét lại' }));
    fireEvent.change(screen.getByLabelText('Hẹn xét lại ngày'), { target: { value: '10/02/2027' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.patch).toHaveBeenCalledWith('api/salary-changes/55', { id: 55, nextReviewOverride: '2027-02-10' }));
    expect(screen.getByRole('link', { name: 'Thêm điều chỉnh lương' }).getAttribute('href')).toBe('/nhan-vien/8?tab=contracts');
  });

  it('Ban giám đốc không thấy nút xử lý', async () => {
    renderList(false);
    expect(await screen.findByText('Nguyễn Văn Thử')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Ghi kết quả thử việc' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hẹn xét lại' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Thêm điều chỉnh lương' })).toBeNull();
  });

  it('ngày ghi kết quả mặc định là hôm nay', async () => {
    renderList(true);
    fireEvent.click(await screen.findByRole('button', { name: 'Ghi kết quả thử việc' }));
    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    expect((screen.getByLabelText('Ngày ghi kết quả') as HTMLInputElement).value).toBe(isoToVn(iso));
  });
});
