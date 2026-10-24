import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router';

import { makeStore } from './test-utils';
import EmployeeForm from './employee-form';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderForm = (path = '/nhan-vien/moi') =>
  render(
    <Provider store={makeStore(['ROLE_HR'])}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/nhan-vien/moi" element={<EmployeeForm />} />
          <Route path="/nhan-vien/:id/sua" element={<EmployeeForm />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeForm', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({ data: [], headers: {} } as any);
  });

  it('hồ sơ mới: điền sẵn mã gợi ý theo loại và năm vào làm; sửa tay thì không ghi đè', async () => {
    mocked.get.mockImplementation((url: string, config?: any) => {
      if (url === 'api/employees/next-code') {
        const p = config?.params ?? {};
        const year = (p.joinDate ?? '2026').slice(2, 4);
        return Promise.resolve({ data: { code: p.personType === 'THUC_TAP' ? `TT${year}02` : `DOIT${year}17` } } as any);
      }
      return Promise.resolve({ data: [], headers: {} } as any);
    });
    renderForm();
    const code = screen.getByLabelText(/Mã nhân viên/) as HTMLInputElement;
    await waitFor(() => expect(code.value).toBe('DOIT2617'));
    fireEvent.change(screen.getByLabelText('Ngày vào công ty'), { target: { value: '02/01/2027' } });
    await waitFor(() => expect(code.value).toBe('DOIT2717'));
    fireEvent.change(screen.getByLabelText('Loại hồ sơ'), { target: { value: 'THUC_TAP' } });
    await waitFor(() => expect((screen.getByLabelText(/Mã nhân viên/) as HTMLInputElement).value).toBe('TT2702'));
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'TT2799' } });
    fireEvent.change(screen.getByLabelText('Ngày vào công ty'), { target: { value: '02/01/2026' } });
    await new Promise(r => setTimeout(r, 50));
    expect((screen.getByLabelText(/Mã nhân viên/) as HTMLInputElement).value).toBe('TT2799');
  });

  it('đánh dấu không thuộc diện đóng BHXH bắt buộc gửi lý do và ghi chú', async () => {
    mocked.post.mockResolvedValue({ data: { id: 5 }, headers: {} } as any);
    renderForm();
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'DOIT2601' } });
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Nguyễn Hưu Trí' } });
    fireEvent.change(screen.getByLabelText('Bảo hiểm bắt buộc'), { target: { value: 'LUONG_HUU' } });
    fireEvent.change(screen.getByLabelText('Ghi chú về bảo hiểm'), { target: { value: 'Đã nghỉ hưu từ 2024' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const body = mocked.post.mock.calls[0][1] as any;
    expect(body.insuranceExemption).toBe('LUONG_HUU');
    expect(body.insuranceExemptionNote).toBe('Đã nghỉ hưu từ 2024');
  });

  it('gửi mức phép năm và số năm đóng BHXH trước khi vào DO IT', async () => {
    mocked.post.mockResolvedValue({ data: { id: 6 }, headers: {} } as any);
    renderForm();
    expect((screen.getByLabelText('Mức phép năm') as HTMLSelectElement).value).toBe('12');
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'DOIT2602' } });
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Lê Văn Phép' } });
    fireEvent.change(screen.getByLabelText('Mức phép năm'), { target: { value: '14' } });
    fireEvent.change(screen.getByLabelText('Số năm đóng BHXH trước khi vào DO IT'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const body = mocked.post.mock.calls[0][1] as any;
    expect(body.annualLeaveDays).toBe(14);
    expect(body.priorSocialInsuranceYears).toBe(7);
  });

  it('số năm đóng BHXH trước khi vào âm thì báo lỗi và không gửi', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Lê Văn Phép' } });
    fireEvent.change(screen.getByLabelText('Số năm đóng BHXH trước khi vào DO IT'), { target: { value: '-1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Phải là số nguyên không âm')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('chọn "HĐ dịch vụ" thì mất ô Mã nhân viên và hiện dòng tự sinh', async () => {
    renderForm();
    expect(screen.getByLabelText(/Mã nhân viên/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Loại hồ sơ'), { target: { value: 'DICH_VU' } });
    expect(screen.queryByLabelText(/Mã nhân viên/)).toBeNull();
    expect(screen.getByText('Mã HDDV sẽ được tự sinh')).toBeTruthy();
    await waitFor(() => expect(mocked.get).toHaveBeenCalled());
  });

  it('để trống Họ tên rồi Lưu thì báo "Bắt buộc nhập" và không gọi API', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'NV1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    const msg = await screen.findAllByText('Bắt buộc nhập');
    expect(msg.length).toBeGreaterThan(0);
    const name = screen.getByLabelText(/Họ và tên/);
    expect(name.getAttribute('aria-invalid')).toBe('true');
    expect(name.getAttribute('aria-describedby')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('gửi ngày dạng ISO và tiền BHXH dạng số khi Lưu', async () => {
    mocked.post.mockResolvedValue({ data: { id: 5 }, headers: {} } as any);
    renderForm();
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'nv1' } });
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Trần Minh Quân' } });
    fireEvent.change(screen.getByLabelText('Ngày sinh'), { target: { value: '05/03/1990' } });
    fireEvent.change(screen.getByLabelText('Mức lương đóng BHXH'), { target: { value: '15.000.000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    const body = mocked.post.mock.calls[0][1] as any;
    expect(body.birthDate).toBe('1990-03-05');
    expect(body.socialInsuranceSalary).toBe(15000000);
    expect(body.fullName).toBe('Trần Minh Quân');
    expect(body.idNumber).toBeUndefined();
    expect(body.status).toBeUndefined();
  });

  it('gõ chữ vào Mức lương đóng BHXH rồi Lưu thì báo lỗi và không gửi request', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: 'NV1' } });
    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Trần Minh Quân' } });
    fireEvent.change(screen.getByLabelText('Mức lương đóng BHXH'), { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect((await screen.findAllByText('Số tiền không hợp lệ')).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Mức lương đóng BHXH').getAttribute('aria-invalid')).toBe('true');
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('gợi ý mức phép năm theo cài đặt thâm niên', async () => {
    mocked.get.mockImplementation((url: string) =>
      url === 'api/leave/settings'
        ? Promise.resolve({ data: { seniorityCycleYears: 3, seniorityExtraDays: 2 } } as any)
        : Promise.resolve({ data: [], headers: {} } as any),
    );
    renderForm();
    expect(await screen.findByText(/Mỗi đủ 3 năm làm việc tự cộng thêm 2 ngày/)).toBeTruthy();
    expect(screen.queryByText(/Mỗi đủ 5 năm/)).toBeNull();
  });

  it('cài đặt tắt thâm niên thì gợi ý không cộng thêm', async () => {
    mocked.get.mockImplementation((url: string) =>
      url === 'api/leave/settings'
        ? Promise.resolve({ data: { seniorityCycleYears: 5, seniorityExtraDays: 0 } } as any)
        : Promise.resolve({ data: [], headers: {} } as any),
    );
    renderForm();
    expect(await screen.findByText(/Hiện không cộng thêm theo thâm niên/)).toBeTruthy();
  });
});
