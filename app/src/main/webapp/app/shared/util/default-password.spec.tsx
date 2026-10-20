import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { act, render, screen, waitFor } from '@testing-library/react';

import { DEFAULT_PASSWORD_URL, DefaultPasswordText } from './default-password';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('mật khẩu mặc định lấy từ server', () => {
  beforeEach(() => mocked.get.mockReset());

  it('hiện giá trị cấu hình ở server (không có giá trị viết cứng)', async () => {
    mocked.get.mockResolvedValue({ data: { defaultPassword: 'CongTy@2027' } } as any);
    render(<DefaultPasswordText />);
    expect(await screen.findByText('CongTy@2027')).toBeTruthy();
    expect(mocked.get).toHaveBeenCalledWith(DEFAULT_PASSWORD_URL, expect.anything());
    expect(screen.queryByText('Doit@2014')).toBeNull();
  });

  it('server không trả giá trị thì ghi chú xem cấu hình, không hiện mật khẩu nào', async () => {
    mocked.get.mockResolvedValue({ data: {} } as any);
    render(<DefaultPasswordText />);
    await waitFor(() => expect(mocked.get).toHaveBeenCalled());
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText('(xem cấu hình máy chủ)')).toBeTruthy();
  });
});
