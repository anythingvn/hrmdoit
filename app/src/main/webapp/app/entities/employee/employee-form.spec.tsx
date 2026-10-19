import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { makeStore } from './test-utils';
import EmployeeForm from './employee-form';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

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
});
