import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import DayEntry from './day-entry';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const row = (over: Record<string, unknown>) => ({
  day: '2026-11-19',
  group: 'VP',
  groupLabel: 'Văn phòng',
  autoKind: null,
  credit: null,
  actualCredit: 0,
  status: 'CHUA_NHAP',
  in: null,
  out: null,
  lateMinutes: 0,
  earlyMinutes: 0,
  remote: false,
  leavePart: null,
  ...over,
});

const BOARD = {
  date: '2026-11-19',
  graceMinutes: 5,
  halfDayThresholdMinutes: 90,
  groups: [
    {
      group: 'VP',
      label: 'Văn phòng',
      hours: { start: '08:00', end: '17:00', breakStart: '12:00', breakEnd: '13:00' },
      rows: [
        row({ employeeId: 1, code: 'NV1', name: 'An', in: '08:00', out: '17:00', status: 'DI_LAM', credit: 1, actualCredit: 1 }),
        row({ employeeId: 2, code: 'NV2', name: 'Bình', in: '08:00', status: 'THIEU_GIO' }),
        row({ employeeId: 3, code: 'NV3', name: 'Chi', autoKind: 'PHEP_NAM', status: null, actualCredit: 1 }),
        row({ employeeId: 4, code: 'NV4', name: 'Dũng', remote: true }),
      ],
    },
    {
      group: 'SITE:7',
      label: 'Onsite · ACME',
      hours: { start: '08:30', end: '17:30', breakStart: '12:00', breakEnd: '13:00' },
      rows: [row({ employeeId: 5, code: 'NV5', name: 'Em', group: 'SITE:7', groupLabel: 'Onsite · ACME' })],
    },
  ],
};

describe('DayEntry', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: BOARD } as any);
  });

  it('có khối Văn phòng và khối Onsite kèm giờ, nhãn Từ xa', async () => {
    render(<DayEntry />);
    expect(await screen.findByText(/Văn phòng \(08:00–17:00/)).toBeTruthy();
    expect(screen.getByText(/Onsite · ACME \(08:30–17:30/)).toBeTruthy();
    expect(screen.getByText('Từ xa')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/day', { params: { date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) } });
  });

  it('gõ giờ vào 09:50 thì hiện nửa công ngay', async () => {
    render(<DayEntry />);
    const input = await screen.findByLabelText('Giờ vào An');
    expect(within(screen.getByTestId('row-1')).getByText('Đúng giờ')).toBeTruthy();
    fireEvent.change(input, { target: { value: '09:50' } });
    const r = within(screen.getByTestId('row-1'));
    expect(r.getByText("Muộn 110' → nửa công")).toBeTruthy();
    expect(r.getByText('0,5')).toBeTruthy();
  });

  it('khách hàng dùng giờ của khách: vào 10:00 với giờ 08:30 vẫn 1 công', async () => {
    render(<DayEntry />);
    fireEvent.change(await screen.findByLabelText('Giờ vào Em'), { target: { value: '10:00' } });
    fireEvent.change(screen.getByLabelText('Giờ ra Em'), { target: { value: '17:30' } });
    expect(within(screen.getByTestId('row-5')).getByText("Muộn 90'")).toBeTruthy();
  });

  it('ô thiếu giờ ra được tô đỏ', async () => {
    render(<DayEntry />);
    const out = await screen.findByLabelText('Giờ ra Bình');
    expect(out.className).toContain('is-invalid');
    expect(screen.getByLabelText('Giờ vào Bình').className).not.toContain('is-invalid');
    expect(within(screen.getByTestId('row-2')).getByText('Thiếu giờ')).toBeTruthy();
  });

  it('dòng ô tự điền không có ô nhập', async () => {
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào An');
    const r = within(screen.getByTestId('row-3'));
    expect(r.queryAllByRole('textbox')).toHaveLength(0);
    expect(r.getAllByText('Nghỉ phép năm').length).toBeGreaterThan(0);
    expect(r.queryByRole('button', { name: 'Vắng' })).toBeNull();
  });

  it('Lưu chỉ gửi dòng đã sửa; Vắng xóa giờ', async () => {
    mocked.put.mockResolvedValue({ data: [row({ employeeId: 4, code: 'NV4', name: 'Dũng', status: 'VANG', credit: 0 })] } as any);
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào Dũng');
    fireEvent.click(within(screen.getByTestId('row-4')).getByRole('button', { name: 'Vắng' }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    expect(mocked.put.mock.calls[0][0]).toBe('api/attendance/day');
    expect((mocked.put.mock.calls[0][1] as any).entries).toEqual([{ employeeId: 4, in: null, out: null, absent: true }]);
    expect(await screen.findByText('Đã lưu 1 người')).toBeTruthy();
  });

  it('lỗi server hiện câu tiếng Việt', async () => {
    mocked.put.mockRejectedValue({ response: { status: 400, data: { message: 'error.attendancefuture' } } });
    render(<DayEntry />);
    fireEvent.change(await screen.findByLabelText('Giờ ra Bình'), { target: { value: '17:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Không gõ giờ cho thời điểm chưa tới')).toBeTruthy();
  });
});
