import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import WorkHours, { scheduleFormError } from './work-hours';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const COMPANY = {
  id: 1,
  effectiveFrom: '2020-01-01',
  startTime: '08:00',
  endTime: '17:00',
  breakStart: '12:00',
  breakEnd: '13:00',
  graceMinutes: 5,
  halfDayThresholdMinutes: 90,
  workSiteId: null,
};

const valid = {
  effectiveFrom: '15/11/2026',
  startTime: '08:30',
  endTime: '17:30',
  breakStart: '12:00',
  breakEnd: '13:00',
  graceMinutes: '5',
  halfDayThresholdMinutes: '90',
};

describe('WorkHours', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockResolvedValue({ data: [COMPANY] } as any);
  });

  it('giờ ra phải sau giờ vào', () => {
    expect(scheduleFormError(valid, true)).toBeNull();
    expect(scheduleFormError({ ...valid, startTime: '17:00', endTime: '08:00' }, true)).toBe('Giờ ra phải sau giờ vào');
    expect(scheduleFormError({ ...valid, startTime: '08:00', endTime: '08:00' }, true)).toBe('Giờ ra phải sau giờ vào');
    expect(scheduleFormError({ ...valid, breakStart: '13:00', breakEnd: '12:00' }, true)).toBe(
      'Giờ kết thúc nghỉ trưa phải sau giờ bắt đầu nghỉ trưa',
    );
    expect(scheduleFormError({ ...valid, startTime: '8:30' }, true)).toBe('Nhập giờ dạng HH:mm (vd 08:00)');
    expect(scheduleFormError({ ...valid, effectiveFrom: '31/02/2026' }, true)).toBe('Nhập ngày hiệu lực dạng dd/MM/yyyy');
    expect(scheduleFormError({ ...valid, halfDayThresholdMinutes: '5' }, true)).toBe('Mốc nửa ngày phải lớn hơn số phút muộn cho phép');
    // Khách hàng không có phút muộn cho phép và mốc nửa ngày.
    expect(scheduleFormError({ ...valid, graceMinutes: '', halfDayThresholdMinutes: '' }, false)).toBeNull();
  });

  it('hiện bộ giờ công ty dạng dd/MM/yyyy và HH:mm', async () => {
    render(<WorkHours canEdit={false} />);
    expect(await screen.findByText('01/01/2020')).toBeTruthy();
    expect(screen.getByText('08:00')).toBeTruthy();
    expect(screen.getByText('5 phút')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/schedules', { params: {} });
    expect(screen.queryByRole('button', { name: 'Lưu bộ giờ' })).toBeNull();
  });

  it('giờ ra trước giờ vào thì báo lỗi, không gửi', async () => {
    render(<WorkHours canEdit />);
    await screen.findByText('01/01/2020');
    fireEvent.change(screen.getByLabelText('Hiệu lực từ (dd/MM/yyyy)'), { target: { value: '15/11/2026' } });
    fireEvent.change(screen.getByLabelText('Giờ vào'), { target: { value: '17:00' } });
    fireEvent.change(screen.getByLabelText('Giờ ra'), { target: { value: '08:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu bộ giờ' }));
    expect(await screen.findByText('Giờ ra phải sau giờ vào')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('lưu bộ giờ công ty gửi ngày ISO và giờ HH:mm', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<WorkHours canEdit />);
    await screen.findByText('01/01/2020');
    fireEvent.change(screen.getByLabelText('Hiệu lực từ (dd/MM/yyyy)'), { target: { value: '15/11/2026' } });
    fireEvent.change(screen.getByLabelText('Giờ vào'), { target: { value: '08:30' } });
    fireEvent.change(screen.getByLabelText('Giờ ra'), { target: { value: '17:30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu bộ giờ' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/attendance/schedules', {
        effectiveFrom: '2026-11-15',
        startTime: '08:30',
        endTime: '17:30',
        breakStart: '12:00',
        breakEnd: '13:00',
        graceMinutes: 5,
        halfDayThresholdMinutes: 90,
        workSiteId: null,
      }),
    );
  });

  it('bộ giờ khách hàng không có phút muộn và mốc nửa ngày', async () => {
    mocked.get.mockResolvedValue({ data: [] } as any);
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<WorkHours siteId={7} canEdit />);
    expect(await screen.findByText('Chưa có bộ giờ riêng, đang dùng giờ của công ty')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/schedules', { params: { siteId: 7 } });
    expect(screen.queryByLabelText('Muộn cho phép (phút)')).toBeNull();
    fireEvent.change(screen.getByLabelText('Hiệu lực từ (dd/MM/yyyy)'), { target: { value: '01/12/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu bộ giờ' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith(
        'api/attendance/schedules',
        expect.objectContaining({ workSiteId: 7, graceMinutes: null, halfDayThresholdMinutes: null }),
      ),
    );
  });
});
