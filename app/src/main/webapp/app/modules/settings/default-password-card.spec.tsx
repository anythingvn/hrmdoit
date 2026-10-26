import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import DefaultPasswordCard from './default-password-card';

vi.mock('axios');
const mocked = vi.mocked(axios, true);
const URL = 'api/admin/account-settings/default-password';

describe('DefaultPasswordCard', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({ data: { defaultPassword: 'CongTy@2027', source: 'WEB' } } as any);
  });

  it('hiện giá trị đang dùng và nguồn', async () => {
    render(<DefaultPasswordCard />);
    expect(await screen.findByText('CongTy@2027')).toBeTruthy();
    expect(screen.getByText('Cài trên web')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith(URL, expect.anything());
  });

  it.each([
    ['ENV', 'Từ file .env trên máy chủ'],
    ['DEFAULT', 'Mặc định Doit@2024'],
  ])('nguồn %s hiện "%s"', async (source, text) => {
    mocked.get.mockResolvedValue({ data: { defaultPassword: 'Doit@2024', source } } as any);
    render(<DefaultPasswordCard />);
    expect(await screen.findByText(text)).toBeTruthy();
  });

  it('lưu mật khẩu mới gửi PUT', async () => {
    mocked.put.mockResolvedValue({ data: { defaultPassword: 'MoiHon@2028', source: 'WEB' } } as any);
    render(<DefaultPasswordCard />);
    await screen.findByText('CongTy@2027');
    fireEvent.change(screen.getByLabelText('Mật khẩu mặc định mới'), { target: { value: 'MoiHon@2028' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mặc định' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith(URL, { defaultPassword: 'MoiHon@2028' }));
    expect(await screen.findByText('MoiHon@2028')).toBeTruthy();
  });

  it('dưới 8 ký tự bị chặn ngay trên trang, không gọi máy chủ', async () => {
    render(<DefaultPasswordCard />);
    await screen.findByText('CongTy@2027');
    fireEvent.change(screen.getByLabelText('Mật khẩu mặc định mới'), { target: { value: 'Ab@1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu mật khẩu mặc định' }));
    expect(await screen.findByText('Mật khẩu mặc định phải có từ 8 đến 100 ký tự.')).toBeTruthy();
    expect(mocked.put).not.toHaveBeenCalled();
  });

  it('"Dùng lại mặc định" gửi null', async () => {
    mocked.put.mockResolvedValue({ data: { defaultPassword: 'Doit@2024', source: 'DEFAULT' } } as any);
    render(<DefaultPasswordCard />);
    await screen.findByText('CongTy@2027');
    fireEvent.click(screen.getByRole('button', { name: 'Dùng lại mặc định' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith(URL, { defaultPassword: null }));
    expect(await screen.findByText('Mặc định Doit@2024')).toBeTruthy();
  });
});
