import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

import PayrollSettingsTab from './payroll-settings-tab';

vi.mock('axios');
vi.mock('app/modules/payroll/rules/payroll-rules', () => ({
  default: (p: any) => <div data-testid="payroll-rules">canEdit:{String(p.canEdit)}</div>,
}));
const mocked = vi.mocked(axios, true);

const setting = (over: Record<string, unknown> = {}) => ({
  id: 1,
  effectiveFrom: '2026-01',
  bhxhEmployee: 0.08,
  bhytEmployee: 0.015,
  bhtnEmployee: 0.01,
  bhxhCompany: 0.175,
  bhytCompany: 0.03,
  bhtnCompany: 0.01,
  unionFeeEnabled: false,
  unionFeeRate: 0.02,
  hoursPerDay: 8,
  overtimeBaseIncludesInsuredAllowance: false,
  overtimeAllNonTaxable: false,
  actualDayKinds: ['WORK', 'PAID_LEAVE', 'HOLIDAY', 'TRIP'],
  allowanceKeywords: {
    PC_CHUYEN_CAN: ['chuyen can'],
    PC_XANG_XE: ['xang'],
    PC_TRACH_NHIEM: ['trach nhiem'],
    PHU_CAP_DONG_BH: ['dong bh', 'dong bao hiem'],
  },
  withholdingThreshold: 2000000,
  withholdingRate: 0.1,
  payUnusedLeaveOnTermination: true,
  insuranceSkipOnLongUnpaid: true,
  locked: false,
  ...over,
});

const load = (list: unknown[]) => mocked.get.mockResolvedValue({ data: list } as any);

describe('PayrollSettingsTab', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.delete.mockReset();
  });

  it('chưa có bộ nào: hiện mặc định của hệ thống', async () => {
    load([]);
    render(<PayrollSettingsTab canEdit={false} />);
    const card = await screen.findByTestId('calc-default');
    expect(within(card).getByText('Mặc định của hệ thống')).toBeTruthy();
    expect(within(card).getByText(/BHXH người lao động 8%/)).toBeTruthy();
    expect(within(card).getByText(/BHXH công ty 17,5%/)).toBeTruthy();
    expect(within(card).getByText(/Kinh phí công đoàn: tắt/)).toBeTruthy();
    expect(within(card).getByText(/Nghỉ không lương từ 14 ngày trong tháng: không đóng BH, KPCĐ/)).toBeTruthy();
    expect(screen.getByTestId('payroll-rules').textContent).toBe('canEdit:false');
  });

  it('HR, Kế toán chỉ xem: không có nút thêm, sửa, xóa', async () => {
    load([setting()]);
    render(<PayrollSettingsTab canEdit={false} />);
    const card = await screen.findByTestId('calc-setting-1');
    expect(within(card).getByText('Hiệu lực từ 01/2026')).toBeTruthy();
    expect(within(card).getByText(/BHXH công ty 17,5%/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Thêm bộ thông số' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Sửa' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Xóa' })).toBeNull();
  });

  it('bộ đã dùng cho kỳ đã duyệt: khóa, không sửa, không xóa', async () => {
    load([setting({ locked: true })]);
    render(<PayrollSettingsTab canEdit />);
    const card = await screen.findByTestId('calc-setting-1');
    expect(within(card).getByText('Đã dùng')).toBeTruthy();
    expect(within(card).queryByRole('button', { name: 'Sửa' })).toBeNull();
    expect(within(card).queryByRole('button', { name: 'Xóa' })).toBeNull();
    expect(screen.getByTestId('payroll-rules').textContent).toBe('canEdit:true');
  });

  it('Quản trị thêm bộ mới: nhập 17,5 gửi 0.175, kèm tháng hiệu lực', async () => {
    load([setting()]);
    mocked.post.mockResolvedValue({ status: 201, data: {} } as any);
    render(<PayrollSettingsTab canEdit />);
    await screen.findByTestId('calc-setting-1');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm bộ thông số' }));
    // Form điền sẵn theo bộ mới nhất.
    expect((screen.getByLabelText('BHXH công ty (%)') as HTMLInputElement).value).toBe('17,5');
    fireEvent.change(screen.getByLabelText('Hiệu lực từ tháng (MM/yyyy)'), { target: { value: '07/2026' } });
    fireEvent.change(screen.getByLabelText('BHXH công ty (%)'), { target: { value: '18,5' } });
    fireEvent.change(screen.getByLabelText('Ngưỡng khấu trừ thuế (đồng/tháng)'), { target: { value: '3.000.000' } });
    fireEvent.click(screen.getByLabelText('Tính kinh phí công đoàn'));
    fireEvent.click(screen.getByLabelText('Ốm'));
    fireEvent.change(screen.getByLabelText('Từ khóa: PC chuyên cần'), { target: { value: 'chuyen can, Chuyên cần 2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const [url, body] = mocked.post.mock.calls[0] as [string, any];
    expect(url).toBe('api/payroll/calc-settings');
    expect(body.effectiveFrom).toBe('2026-07');
    expect(body.bhxhCompany).toBe(0.185);
    expect(body.bhxhEmployee).toBe(0.08);
    expect(body.bhytEmployee).toBe(0.015);
    expect(body.withholdingThreshold).toBe(3000000);
    expect(body.withholdingRate).toBe(0.1);
    expect(body.unionFeeEnabled).toBe(true);
    expect(body.actualDayKinds).toEqual(['WORK', 'PAID_LEAVE', 'HOLIDAY', 'TRIP', 'SICK']);
    expect(body.allowanceKeywords.PC_CHUYEN_CAN).toEqual(['chuyen can', 'Chuyên cần 2']);
    expect(body.allowanceKeywords.PHU_CAP_DONG_BH).toEqual(['dong bh', 'dong bao hiem']);
    expect(body.hoursPerDay).toBe(8);
    expect(body.payUnusedLeaveOnTermination).toBe(true);
    expect(body.insuranceSkipOnLongUnpaid).toBe(true);
  });

  it('tắt quy tắc không đóng BH khi nghỉ không lương từ 14 ngày; bộ cũ thiếu trường hiểu là bật', async () => {
    const old: Record<string, unknown> = setting({ id: 5 });
    delete old.insuranceSkipOnLongUnpaid;
    load([old]);
    mocked.put.mockResolvedValue({ data: {} } as any);
    render(<PayrollSettingsTab canEdit />);
    const card = await screen.findByTestId('calc-setting-5');
    expect(within(card).getByText(/Nghỉ không lương từ 14 ngày trong tháng: không đóng BH, KPCĐ/)).toBeTruthy();
    fireEvent.click(within(card).getByRole('button', { name: 'Sửa' }));
    const box = screen.getByLabelText('Không đóng BH, KPCĐ khi nghỉ không lương từ 14 ngày trong tháng') as HTMLInputElement;
    expect(box.checked).toBe(true);
    fireEvent.click(box);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    expect((mocked.put.mock.calls[0] as [string, any])[1].insuranceSkipOnLongUnpaid).toBe(false);
  });

  it('báo lỗi khi thiếu tháng hiệu lực, không gửi', async () => {
    load([]);
    render(<PayrollSettingsTab canEdit />);
    await screen.findByTestId('calc-default');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm bộ thông số' }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText(/Nhập tháng hiệu lực/)).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('Quản trị sửa bộ chưa dùng: gửi PUT; xóa có xác nhận', async () => {
    load([setting({ id: 4 })]);
    mocked.put.mockResolvedValue({ data: {} } as any);
    mocked.delete.mockResolvedValue({ status: 204 } as any);
    render(<PayrollSettingsTab canEdit />);
    const card = await screen.findByTestId('calc-setting-4');
    fireEvent.click(within(card).getByRole('button', { name: 'Sửa' }));
    fireEvent.change(screen.getByLabelText('Số giờ một ngày công'), { target: { value: '7,5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalled());
    const [url, body] = mocked.put.mock.calls[0] as [string, any];
    expect(url).toBe('api/payroll/calc-settings/4');
    expect(body.hoursPerDay).toBe(7.5);
    expect(body.effectiveFrom).toBe('2026-01');
    fireEvent.click(await within(await screen.findByTestId('calc-setting-4')).findByRole('button', { name: 'Xóa' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa bộ thông số' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/payroll/calc-settings/4'));
  });

  it('lỗi máy chủ hiện câu tiếng Việt', async () => {
    load([setting({ id: 4 })]);
    mocked.put.mockRejectedValue({ response: { data: { message: 'error.calcsettinglocked' } } });
    render(<PayrollSettingsTab canEdit />);
    const card = await screen.findByTestId('calc-setting-4');
    fireEvent.click(within(card).getByRole('button', { name: 'Sửa' }));
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Bộ thông số này đã dùng cho kỳ đã duyệt, hãy thêm bộ mới.')).toBeTruthy();
  });
});
