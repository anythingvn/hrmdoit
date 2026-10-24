import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { downloadFile } from 'app/shared/util/download';

import WorkCalendar from './work-calendar';

vi.mock('axios');
vi.mock('app/shared/util/download', async orig => ({ ...(await orig<any>()), downloadFile: vi.fn(() => Promise.resolve()) }));
const mocked = vi.mocked(axios, true);

describe('WorkCalendar', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/work-calendar')
        return Promise.resolve({ data: { workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] } } as any);
      if (url === 'api/holidays') return Promise.resolve({ data: [{ id: 3, day: '2026-09-02', name: 'Quốc khánh' }] } as any);
      return Promise.resolve({ data: {} } as any);
    });
  });

  it('Quản trị tick thêm thứ Bảy rồi lưu: gửi đủ 6 ngày', async () => {
    mocked.put.mockResolvedValue({ data: { workingDays: [] } } as any);
    render(<WorkCalendar canEditWeek canEditHolidays initialYear={2026} />);
    const sat = (await screen.findByLabelText('Thứ Bảy')) as HTMLInputElement;
    expect(sat.checked).toBe(false);
    fireEvent.click(sat);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu ngày làm việc' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/work-calendar', {
        workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
      }),
    );
  });

  it('HR không sửa được ngày làm việc trong tuần', async () => {
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    expect(((await screen.findByLabelText('Thứ Hai')) as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Lưu ngày làm việc' })).toBeNull();
  });

  it('hiện ngày lễ của năm, thêm ngày lễ gửi ngày ISO', async () => {
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    expect(await screen.findByText('02/09/2026')).toBeTruthy();
    expect(screen.getByText('Quốc khánh')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith('api/holidays', { params: { year: 2026 } });
    fireEvent.change(screen.getByLabelText('Ngày lễ (dd/MM/yyyy)'), { target: { value: '01/01/2026' } });
    fireEvent.change(screen.getByLabelText('Tên ngày lễ'), { target: { value: 'Tết Dương lịch' } });
    fireEvent.click(screen.getByRole('button', { name: 'Thêm ngày lễ' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/holidays', { day: '2026-01-01', name: 'Tết Dương lịch' }));
  });

  it('chép ngày lễ từ năm trước', async () => {
    mocked.post.mockResolvedValue({ data: [] } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Chép từ năm 2025' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/holidays/copy', null, { params: { fromYear: 2025, toYear: 2026 } }));
  });

  it('nút Xuất Excel tải đúng URL và tên file', async () => {
    render(<WorkCalendar canEditWeek={false} canEditHolidays={false} initialYear={2026} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Xuất Excel' }));
    await waitFor(() => expect(downloadFile).toHaveBeenCalledWith('api/holidays/export.xlsx', 'ngay-le-2026.xlsx', { year: 2026 }));
  });

  const preview = (errors: any[]) => ({
    years: [2027],
    added: [{ day: '2027-05-01', name: 'Quốc tế Lao động' }],
    renamed: [{ day: '2027-04-30', oldName: 'Giải phóng', name: 'Ngày Giải phóng miền Nam' }],
    removed: [{ day: '2027-09-02', name: 'Quốc khánh' }],
    unchanged: [{ day: '2027-01-01', name: 'Tết Dương lịch' }],
    errors,
  });
  const pick = async () => {
    const input = (await screen.findByLabelText('Chọn file Excel ngày lễ')) as HTMLInputElement;
    const file = new File(['x'], 'ngay-le.xlsx');
    fireEvent.change(input, { target: { files: [file] } });
  };

  it('chọn file thì hiện bảng xem trước theo nhóm; Áp dụng gửi file', async () => {
    mocked.post.mockResolvedValueOnce({ data: preview([]) } as any).mockResolvedValueOnce({ data: preview([]) } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    await pick();
    expect(await screen.findByText('Thêm mới (1)')).toBeTruthy();
    expect(screen.getByText('Đổi tên (1)')).toBeTruthy();
    expect(screen.getByText('Sẽ xóa (1)')).toBeTruthy();
    expect(screen.getByText('Không đổi (1)')).toBeTruthy();
    expect(screen.getByText('Quốc tế Lao động')).toBeTruthy();
    expect(mocked.post).toHaveBeenCalledWith('api/holidays/import/preview', expect.any(FormData));
    const apply = screen.getByRole('button', { name: 'Áp dụng' }) as HTMLButtonElement;
    expect(apply.disabled).toBe(false);
    fireEvent.click(apply);
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/holidays/import/apply', expect.any(FormData)));
  });

  it('có lỗi thì nút Áp dụng bị tắt', async () => {
    mocked.post.mockResolvedValueOnce({
      data: preview([{ row: 3, key: 'holidaymonthlocked', message: 'Tháng 11/2026 đã chốt công hoặc đã duyệt lương – không sửa ngày lễ' }]),
    } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    await pick();
    expect(await screen.findByText(/Dòng 3: Tháng 11\/2026/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Áp dụng' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('đọc file xong thì xóa giá trị ô chọn file để chọn lại đúng file đó', async () => {
    mocked.post.mockResolvedValue({ data: preview([]) } as any);
    render(<WorkCalendar canEditWeek={false} canEditHolidays initialYear={2026} />);
    const input = (await screen.findByLabelText('Chọn file Excel ngày lễ')) as HTMLInputElement;
    const proto = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!;
    const writes: string[] = [];
    Object.defineProperty(input, 'value', {
      configurable: true,
      get() {
        return proto.get!.call(this);
      },
      set(v: string) {
        writes.push(v);
        proto.set!.call(this, v);
      },
    });
    fireEvent.change(input, { target: { files: [new File(['x'], 'ngay-le.xlsx')] } });
    await waitFor(() => expect(mocked.post).toHaveBeenCalledTimes(1));
    expect(writes).toContain('');
  });
});
