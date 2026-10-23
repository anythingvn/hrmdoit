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
  markedAbsent: false,
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
        row({
          employeeId: 1,
          code: 'NV1',
          name: 'An',
          in: '08:00',
          out: '17:00',
          manualIn: '08:00',
          manualOut: '17:00',
          status: 'DI_LAM',
          credit: 1,
          actualCredit: 1,
        }),
        row({ employeeId: 2, code: 'NV2', name: 'Bình', in: '08:00', manualIn: '08:00', status: 'THIEU_GIO' }),
        row({ employeeId: 3, code: 'NV3', name: 'Chi', autoKind: 'PHEP_NAM', status: null, actualCredit: 1 }),
        row({ employeeId: 4, code: 'NV4', name: 'Dũng', remote: true }),
        row({ employeeId: 6, code: 'NV6', name: 'Giang', status: 'VANG', credit: 0, markedAbsent: true }),
        row({
          employeeId: 7,
          code: 'NV7',
          name: 'Hà',
          in: '08:03',
          status: 'THIEU_GIO',
          punches: [{ id: 70, kind: 'VAO', time: '08:03', source: 'APP', status: 'CHAP_NHAN', method: 'IP' }],
        }),
        row({
          employeeId: 8,
          code: 'NV8',
          name: 'Ngọc',
          pendingCount: 1,
          punches: [
            { id: 55, kind: 'VAO', time: '08:10', source: 'APP', status: 'CHO_DUYET', method: 'GPS', accuracyM: 140, distanceM: 210 },
            { id: 56, kind: 'VAO', time: '07:00', source: 'APP', status: 'TU_CHOI', method: 'GPS', reviewReason: 'Quá xa' },
            { id: 57, kind: 'RA', time: '17:00', source: 'GIAI_TRINH', status: 'DA_DUYET' },
          ],
        }),
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

  it('dòng đã đánh dấu vắng: nút Vắng bật, ô giờ khóa, công 0', async () => {
    render(<DayEntry />);
    const r = within(await screen.findByTestId('row-6'));
    expect(r.getByRole('button', { name: 'Vắng' }).getAttribute('aria-pressed')).toBe('true');
    expect((r.getByLabelText('Giờ vào Giang') as HTMLInputElement).disabled).toBe(true);
    expect(r.getByText('0')).toBeTruthy();
    expect(r.getByText('Vắng', { selector: 'td' })).toBeTruthy();
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

  it('ô nhập chỉ lấy giờ gõ tay: ngày có lần tự chấm 08:03 thì ô trống, lưu không gửi 08:03', async () => {
    mocked.put.mockResolvedValue({ data: [] } as any);
    render(<DayEntry />);
    const inp = (await screen.findByLabelText('Giờ vào Hà')) as HTMLInputElement;
    expect(inp.value).toBe('');
    fireEvent.change(screen.getByLabelText('Giờ ra Hà'), { target: { value: '17:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    const body = mocked.put.mock.calls[0][1] as any;
    expect(body.entries).toEqual([{ employeeId: 7, in: null, out: '17:00', absent: false }]);
    expect(JSON.stringify(body)).not.toContain('08:03');
  });

  it('dưới ô ghi nguồn các lần chấm; lần bị từ chối hiện mờ', async () => {
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào Hà');
    expect(within(screen.getByTestId('row-7')).getByText('Vào 08:03 · Tự chấm · IP')).toBeTruthy();
    const r = within(screen.getByTestId('row-8'));
    expect(r.getByText('Vào 08:10 · Tự chấm · GPS ±140 m · chờ duyệt')).toBeTruthy();
    expect(r.getByText('Ra 17:00 · Giải trình')).toBeTruthy();
    expect(r.getByTestId('punch-56').style.opacity).toBe('0.5');
    expect(r.getByTestId('punch-55').style.opacity).toBe('');
  });

  it('dòng chờ duyệt tô vàng, có bộ đếm; Duyệt gọi approve rồi cập nhật dòng', async () => {
    mocked.post.mockResolvedValue({
      data: row({
        employeeId: 8,
        code: 'NV8',
        name: 'Ngọc',
        pendingCount: 0,
        punches: [{ id: 55, kind: 'VAO', time: '08:10', source: 'APP', status: 'DA_DUYET', method: 'GPS' }],
      }),
    } as any);
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào Ngọc');
    expect(screen.getByTestId('row-8').className).toContain('table-warning');
    expect(screen.getByTestId('row-7').className).not.toContain('table-warning');
    expect(screen.getByText('1 lần chấm chờ duyệt')).toBeTruthy();
    fireEvent.click(within(screen.getByTestId('punch-55')).getByRole('button', { name: 'Duyệt' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches/55/approve'));
    await waitFor(() => expect(screen.getByTestId('row-8').className).not.toContain('table-warning'));
    expect(screen.queryByText('1 lần chấm chờ duyệt')).toBeNull();
  });

  it('Từ chối bắt buộc lý do: không có lý do thì nút xác nhận tắt; gửi note', async () => {
    mocked.post.mockResolvedValue({ data: row({ employeeId: 8, code: 'NV8', name: 'Ngọc', pendingCount: 0 }) } as any);
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào Ngọc');
    fireEvent.click(within(screen.getByTestId('punch-55')).getByRole('button', { name: 'Từ chối' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: 'Xác nhận từ chối' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(within(dialog).getByLabelText('Lý do từ chối'), { target: { value: 'Không đúng nơi' } });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/attendance/punches/55/reject', { note: 'Không đúng nơi' }));
  });

  it('duyệt lỗi hiện câu tiếng Việt', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.attendancepunchnotpending' } } });
    render(<DayEntry />);
    await screen.findByLabelText('Giờ vào Ngọc');
    fireEvent.click(within(screen.getByTestId('punch-55')).getByRole('button', { name: 'Duyệt' }));
    expect(await screen.findByText('Lần chấm này không còn chờ duyệt')).toBeTruthy();
  });
});
