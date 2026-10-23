import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { resetBrandingForTests } from 'app/shared/branding/branding';

import CompanyTab from './company-tab';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const setting = {
  companyName: 'Công ty TNHH DO IT',
  shortName: 'DOIT HRM',
  tagline: 'Do it if you can',
  representativeName: 'Nguyễn Văn A',
  representativeTitle: 'Giám đốc',
  logoVersion: 3,
};

const fileOf = (size: number, type: string, name = 'logo.png') => {
  const f = new File(['x'], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('CompanyTab', () => {
  let brandingVersion = 3;

  beforeEach(() => {
    resetBrandingForTests();
    brandingVersion = 3;
    mocked.get.mockReset();
    mocked.create.mockReturnValue(mocked as any);
    mocked.put.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/company-setting') return Promise.resolve({ data: setting } as any);
      if (url === 'api/public/branding')
        return Promise.resolve({
          data: { companyName: setting.companyName, shortName: 'DOIT HRM', tagline: '', logoVersion: brandingVersion },
        } as any);
      return Promise.resolve({ data: {} } as any);
    });
  });

  it('lưu tên: PUT đúng body (đã cắt khoảng trắng)', async () => {
    mocked.put.mockResolvedValue({ data: { ...setting, companyName: 'Công ty CP ABC' } } as any);
    render(<CompanyTab />);
    const name = (await screen.findByLabelText('Tên công ty')) as HTMLInputElement;
    expect(name.value).toBe('Công ty TNHH DO IT');
    fireEvent.change(name, { target: { value: '  Công ty CP ABC ' } });
    fireEvent.change(screen.getByLabelText('Tên viết tắt'), { target: { value: 'ABC HR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thông tin công ty' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/company-setting', {
        companyName: 'Công ty CP ABC',
        shortName: 'ABC HR',
        tagline: 'Do it if you can',
        representativeName: 'Nguyễn Văn A',
        representativeTitle: 'Giám đốc',
      }),
    );
    expect(await screen.findByText('Đã lưu thông tin công ty.')).toBeTruthy();
  });

  it('tên công ty trống: báo lỗi, không gọi API', async () => {
    render(<CompanyTab />);
    fireEvent.change(await screen.findByLabelText('Tên công ty'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thông tin công ty' }));
    expect(await screen.findByText('Cần nhập tên công ty')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('chọn file 2 MB: báo "Logo tối đa 1 MB", không gọi API', async () => {
    render(<CompanyTab />);
    const input = await screen.findByLabelText('Chọn logo');
    fireEvent.change(input, { target: { files: [fileOf(2 * 1024 * 1024, 'image/png')] } });
    expect(await screen.findByText('Logo tối đa 1 MB')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Tải logo lên' }) as HTMLButtonElement).disabled).toBe(true);
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('tải logo thành công: làm mới branding (menu, favicon dùng phiên bản mới)', async () => {
    mocked.post.mockImplementation(() => {
      brandingVersion = 99;
      return Promise.resolve({ data: { ...setting, logoVersion: 99 } } as any);
    });
    render(<CompanyTab />);
    await screen.findByLabelText('Tên công ty');
    await waitFor(() => expect(mocked.get.mock.calls.filter(c => c[0] === 'api/public/branding')).toHaveLength(1));
    fireEvent.change(screen.getByLabelText('Chọn logo'), { target: { files: [fileOf(1000, 'image/png')] } });
    fireEvent.click(screen.getByRole('button', { name: 'Tải logo lên' }));
    expect(await screen.findByText('Đã cập nhật logo.')).toBeTruthy();
    expect(mocked.post.mock.calls[0][0]).toBe('api/company-setting/logo');
    await waitFor(() => expect(mocked.get.mock.calls.filter(c => c[0] === 'api/public/branding')).toHaveLength(2));
    await waitFor(() => expect(screen.getByAltText('Logo hiện tại').getAttribute('src')).toBe('api/public/branding/logo?v=99'));
  });

  it('khôi phục logo mặc định gọi DELETE rồi làm mới branding', async () => {
    mocked.delete.mockResolvedValue({ data: { ...setting, logoVersion: 0 } } as any);
    render(<CompanyTab />);
    await screen.findByLabelText('Tên công ty');
    fireEvent.click(screen.getByRole('button', { name: 'Khôi phục logo mặc định' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/company-setting/logo'));
    expect(await screen.findByText('Đã trở lại logo mặc định.')).toBeTruthy();
  });
});
