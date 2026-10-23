import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import SelfPunchSettings from './self-punch-settings';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const SETTING = { noticeText: 'Hệ thống lấy vị trí một lần.', noticeVersion: 2, locationRetentionDays: 90 };

describe('SelfPunchSettings', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: SETTING } as any);
  });

  it('Quản trị sửa được: nội dung thông báo và số ngày giữ tọa độ, gửi PUT', async () => {
    mocked.put.mockResolvedValue({ data: { ...SETTING, noticeText: 'Nội dung mới', noticeVersion: 3, locationRetentionDays: 30 } } as any);
    render(<SelfPunchSettings canEdit />);
    const text = (await screen.findByLabelText('Nội dung thông báo')) as HTMLTextAreaElement;
    expect(mocked.get).toHaveBeenCalledWith('api/company-setting/attendance-notice');
    await waitFor(() => expect(text.value).toBe(SETTING.noticeText));
    expect((screen.getByLabelText('Số ngày giữ tọa độ và IP') as HTMLInputElement).value).toBe('90');
    fireEvent.change(text, { target: { value: 'Nội dung mới' } });
    fireEvent.change(screen.getByLabelText('Số ngày giữ tọa độ và IP'), { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt tự chấm công' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/company-setting/attendance-notice', {
        noticeText: 'Nội dung mới',
        locationRetentionDays: 30,
      }),
    );
    expect(await screen.findByText(/Đã lưu/)).toBeTruthy();
  });

  it('HR chỉ xem: ô khóa, không có nút Lưu', async () => {
    render(<SelfPunchSettings canEdit={false} />);
    const text = (await screen.findByLabelText('Nội dung thông báo')) as HTMLTextAreaElement;
    expect(text.readOnly).toBe(true);
    expect((screen.getByLabelText('Số ngày giữ tọa độ và IP') as HTMLInputElement).readOnly).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu cài đặt tự chấm công' })).toBeNull();
    expect(screen.getByText(/Chỉ Quản trị/)).toBeTruthy();
  });

  it('số ngày ngoài 1..3650 bị chặn trước khi gửi', async () => {
    render(<SelfPunchSettings canEdit />);
    await screen.findByLabelText('Nội dung thông báo');
    await waitFor(() => expect((screen.getByLabelText('Số ngày giữ tọa độ và IP') as HTMLInputElement).value).toBe('90'));
    fireEvent.change(screen.getByLabelText('Số ngày giữ tọa độ và IP'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt tự chấm công' }));
    expect(await screen.findByText('Số ngày giữ tọa độ từ 1 đến 3650')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('lỗi server hiện câu tiếng Việt', async () => {
    mocked.put.mockRejectedValue({ response: { status: 400, data: { message: 'error.attendancenoticesettinginvalid' } } });
    render(<SelfPunchSettings canEdit />);
    await waitFor(() => expect((screen.getByLabelText('Số ngày giữ tọa độ và IP') as HTMLInputElement).value).toBe('90'));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cài đặt tự chấm công' }));
    expect(await screen.findByText(/Nội dung thông báo tối đa 10.000 ký tự/)).toBeTruthy();
  });
});
