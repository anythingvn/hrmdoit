import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';

import CalcDraft from './calc-draft';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const columns = [
  { field: 'NGAY_CONG_THUC_TE', header: 'Ngày công thực tế', input: false },
  { field: 'LUONG_THEO_NGAY_CONG', header: 'Lương theo ngày công', input: false },
  { field: 'THUONG_KPI', header: 'Thưởng KPI', input: true },
  { field: 'THUC_NHAN', header: 'Thực nhận', input: false },
];

const line = (over: Record<string, any> = {}) => ({
  id: 11,
  employeeId: 1,
  code: 'DOIT2401',
  fullName: 'Phạm Văn An',
  position: 'Kỹ sư',
  amounts: { NGAY_CONG_THUC_TE: 21, LUONG_THEO_NGAY_CONG: 10000000, THUONG_KPI: 0, THUC_NHAN: 9000000 },
  manual: {},
  overrides: {},
  errors: [],
  warnings: [],
  ...over,
});

const draft = (over: Record<string, any> = {}) => ({
  id: 5,
  period: '2026-06',
  status: 'DANG_LAM',
  lines: [line()],
  totals: { LUONG_THEO_NGAY_CONG: 10000000, THUONG_KPI: 0, THUC_NHAN: 9000000 },
  canConfirm: true,
  warningsAcknowledged: false,
  columns,
  ...over,
});

const renderIt = () =>
  render(
    <MemoryRouter initialEntries={['/bang-luong/tinh-luong/5']}>
      <Routes>
        <Route path="/bang-luong/tinh-luong/:id" element={<CalcDraft />} />
        <Route path="/bang-luong/:period" element={<div>Chi tiết kỳ</div>} />
      </Routes>
    </MemoryRouter>,
  );

const load = (d: any) => mocked.get.mockResolvedValue({ data: d } as any);

describe('CalcDraft', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.delete.mockReset();
  });

  it('phân lớp ô: tự tính xám, nhập tay input, ô sửa tay tô màu kèm nút ↺; dòng tổng chỉ cột tiền', async () => {
    load(draft({ lines: [line({ overrides: { LUONG_THEO_NGAY_CONG: 9000000 } })] }));
    const { container } = renderIt();
    await screen.findByText('Phạm Văn An');
    expect(container.querySelectorAll('td.calc-cell-auto').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('td.calc-cell-input input').length).toBe(1);
    expect(container.querySelectorAll('td.calc-cell-overridden').length).toBe(1);
    expect(screen.getAllByRole('button', { name: /↺/ }).length).toBe(1);
    const totalRow = screen.getByRole('row', { name: /Tổng/ });
    expect(within(totalRow).getByText('10.000.000')).toBeTruthy();
    // Cột ngày công không có tổng.
    expect(within(totalRow).queryByText('21')).toBeNull();
  });

  it('nhập Thưởng KPI rồi rời ô: PUT manual và dòng cập nhật theo trả về', async () => {
    load(draft());
    mocked.put.mockResolvedValue({
      data: line({ manual: { THUONG_KPI: 500000 }, amounts: { ...line().amounts, THUONG_KPI: 500000, THUC_NHAN: 9400000 } }),
    } as any);
    const { container } = renderIt();
    await screen.findByText('Phạm Văn An');
    const input = container.querySelector('td.calc-cell-input input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '500000' } });
    fireEvent.blur(input);
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/payroll/calc/5/lines/11', { manual: { THUONG_KPI: 500000 } }));
    expect(await screen.findByText('9.400.000')).toBeTruthy();
  });

  it('giá trị không phải số: không gọi PUT, báo lỗi', async () => {
    load(draft());
    const { container } = renderIt();
    await screen.findByText('Phạm Văn An');
    const input = container.querySelector('td.calc-cell-input input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'abc' } });
    fireEvent.blur(input);
    expect(await screen.findByText(/Nhập một số/)).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('↺ gửi overrides null', async () => {
    load(draft({ lines: [line({ overrides: { LUONG_THEO_NGAY_CONG: 9000000 } })] }));
    mocked.put.mockResolvedValue({ data: line() } as any);
    renderIt();
    fireEvent.click(await screen.findByRole('button', { name: /↺/ }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/payroll/calc/5/lines/11', { overrides: { LUONG_THEO_NGAY_CONG: null } }),
    );
  });

  it('sửa ô tự tính (ngày công) gửi overrides', async () => {
    load(draft());
    mocked.put.mockResolvedValue({ data: line({ overrides: { NGAY_CONG_THUC_TE: 20.5 } }) } as any);
    renderIt();
    fireEvent.click(await screen.findByRole('button', { name: 'Sửa Ngày công thực tế của Phạm Văn An' }));
    const input = screen.getByLabelText('Ngày công thực tế của Phạm Văn An');
    fireEvent.change(input, { target: { value: '20,5' } });
    fireEvent.blur(input);
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/payroll/calc/5/lines/11', { overrides: { NGAY_CONG_THUC_TE: 20.5 } }));
  });

  it('hiện lỗi và cảnh báo theo dòng', async () => {
    load(draft({ lines: [line({ errors: ['Thiếu mã số thuế'], warnings: ['Lương thấp hơn tối thiểu vùng'] })], canConfirm: false }));
    renderIt();
    expect(await screen.findByText('Thiếu mã số thuế')).toBeTruthy();
    expect(screen.getByText('Lương thấp hơn tối thiểu vùng')).toBeTruthy();
  });

  it('Xác nhận disabled khi canConfirm=false; có cảnh báo thì phải tích ô xác nhận', async () => {
    load(draft({ lines: [line({ warnings: ['Cảnh báo A'] })], canConfirm: false }));
    mocked.post.mockResolvedValue({
      data: draft({ lines: [line({ warnings: ['Cảnh báo A'] })], canConfirm: true, warningsAcknowledged: true }),
    } as any);
    renderIt();
    const confirm = (await screen.findByRole('button', { name: 'Xác nhận' })) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.click(screen.getByLabelText('Đã xem và xác nhận các cảnh báo'));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/calc/5/acknowledge'));
    await waitFor(() => expect((screen.getByRole('button', { name: 'Xác nhận' }) as HTMLButtonElement).disabled).toBe(false));
  });

  it('xác nhận xong chuyển về chi tiết kỳ', async () => {
    load(draft());
    mocked.post.mockResolvedValue({ data: { period: '2026-06', version: 2 } } as any);
    renderIt();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/calc/5/confirm', { warningsAcknowledged: false }));
    expect(await screen.findByText('Chi tiết kỳ')).toBeTruthy();
  });

  it('"Tính lại từ hồ sơ" gọi recalculate và cập nhật bảng', async () => {
    load(draft());
    mocked.post.mockResolvedValue({ data: draft({ lines: [line({ fullName: 'Phạm Văn Bình' })] }) } as any);
    renderIt();
    fireEvent.click(await screen.findByRole('button', { name: 'Tính lại từ hồ sơ' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/payroll/calc/5/recalculate'));
    expect(await screen.findByText('Phạm Văn Bình')).toBeTruthy();
  });

  it('bản đã xác nhận: không sửa, không xác nhận lại', async () => {
    load(draft({ status: 'DA_XAC_NHAN', canConfirm: false }));
    const { container } = renderIt();
    await screen.findByText('Phạm Văn An');
    expect((container.querySelector('td.calc-cell-input input') as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: 'Xác nhận' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Tính lại từ hồ sơ' })).toBeNull();
  });

  it('bỏ dòng gọi DELETE rồi bỏ dòng khỏi bảng', async () => {
    load(draft());
    mocked.delete.mockResolvedValue({} as any);
    renderIt();
    fireEvent.click(await screen.findByRole('button', { name: 'Bỏ dòng Phạm Văn An' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/payroll/calc/5/lines/11'));
    await waitFor(() => expect(screen.queryByText('Phạm Văn An')).toBeNull());
  });
});
