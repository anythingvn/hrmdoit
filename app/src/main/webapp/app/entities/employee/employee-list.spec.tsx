import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from './test-utils';
import EmployeeList from './employee-list';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderList = (authorities: string[]) =>
  render(
    <Provider store={makeStore(authorities)}>
      <MemoryRouter initialEntries={['/nhan-vien']}>
        <EmployeeList />
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeList', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          id: 1,
          code: 'NV0142',
          fullName: 'Trần Minh Quân',
          department: { id: 1, name: 'IT' },
          position: 'Dev',
          status: 'DANG_LAM_VIEC',
        },
      ],
      headers: { 'x-total-count': '1' },
    } as any);
  });

  it('vai trò BGD không có nút "Thêm nhân viên", HR thì có', async () => {
    const { unmount } = renderList(['ROLE_BGD']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.queryByText('Thêm nhân viên')).toBeNull();
    unmount();
    renderList(['ROLE_HR']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.getByText('Thêm nhân viên')).toBeTruthy();
  });

  it('liên kết "Nhân viên đã lưu trữ" chỉ cho Quản trị/HR', async () => {
    const { unmount } = renderList(['ROLE_BGD']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.queryByRole('link', { name: 'Nhân viên đã lưu trữ' })).toBeNull();
    unmount();
    renderList(['ROLE_HR']);
    await screen.findByText('Trần Minh Quân');
    expect(screen.getByRole('link', { name: 'Nhân viên đã lưu trữ' }).getAttribute('href')).toBe('/nhan-vien/luu-tru');
  });

  it('gõ "quan" vào ô tìm thì request có q.contains=quan', async () => {
    renderList(['ROLE_HR']);
    await screen.findByText('Trần Minh Quân');
    fireEvent.change(screen.getByLabelText('Tìm theo tên hoặc mã'), { target: { value: 'quan' } });
    await waitFor(
      () => {
        const urls = mocked.get.mock.calls.map(c => String(c[0]));
        expect(urls.some(u => u.includes('api/employees') && u.includes('q.contains=quan'))).toBe(true);
      },
      { timeout: 2000 },
    );
  });

  it('nhân viên có lịch nghỉ hiện nhãn "Sắp nghỉ (dd/MM/yyyy)"', async () => {
    mocked.get.mockResolvedValue({
      data: [{ id: 3, code: 'NV0300', fullName: 'Phạm Sắp Nghỉ', status: 'DANG_LAM_VIEC', scheduledTermination: '2026-10-31' }],
      headers: { 'x-total-count': '1' },
    } as any);
    renderList(['ROLE_BGD']);
    await screen.findByText('Phạm Sắp Nghỉ');
    expect(screen.getByText('Sắp nghỉ (31/10/2026)')).toBeTruthy();
  });

  it('lịch nghỉ quá hạn hiện nhãn "Quá hạn nghỉ việc" kèm lý do', async () => {
    mocked.get.mockResolvedValue({
      data: [
        {
          id: 5,
          code: 'NV0500',
          fullName: 'Ngô Quá Hạn',
          status: 'DANG_LAM_VIEC',
          scheduledTermination: '2026-10-31',
          scheduledTerminationOverdue: true,
          scheduledTerminationError: 'Lý do X',
        },
      ],
      headers: { 'x-total-count': '1' },
    } as any);
    renderList(['ROLE_HR']);
    await screen.findByText('Ngô Quá Hạn');
    expect(screen.getByText('Quá hạn nghỉ việc – chưa áp dụng được: Lý do X')).toBeTruthy();
  });
});
