import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { makeStore } from '../test-utils';
import ArchivedList from './archived-list';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const ROWS = [
  {
    id: 4,
    code: 'NV0400',
    fullName: 'Đỗ Đã Nghỉ',
    departmentName: 'Phòng Kỹ thuật',
    lastWorkingDay: '2026-09-30',
    archivedAt: '2026-10-20T03:00:00Z',
    archivedBy: 'hr01',
    archiveNote: 'Nghỉ lâu',
  },
];

const renderList = (login = 'hr01', authorities = ['ROLE_HR']) =>
  render(
    <Provider store={makeStore(authorities, { login })}>
      <MemoryRouter>
        <ArchivedList />
      </MemoryRouter>
    </Provider>,
  );

describe('ArchivedList', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockResolvedValue({ data: ROWS, headers: { 'x-total-count': '1' } } as any);
  });

  it('tải vùng lưu trữ, hiện đủ cột; tìm thì gọi lại với từ khóa', async () => {
    renderList();
    const row = (await screen.findByText('Đỗ Đã Nghỉ')).closest('tr')!;
    expect(mocked.get).toHaveBeenCalledWith('api/employees/archived', { params: { q: '', page: 0, size: 20 } });
    expect(within(row).getByText('NV0400')).toBeTruthy();
    expect(within(row).getByText('Phòng Kỹ thuật')).toBeTruthy();
    expect(within(row).getByText('30/09/2026')).toBeTruthy();
    expect(within(row).getByText('20/10/2026')).toBeTruthy();
    expect(within(row).getByText('hr01')).toBeTruthy();
    expect(within(row).getByText('Nghỉ lâu')).toBeTruthy();
    expect(within(row).getByRole('link', { name: 'Xem hồ sơ Đỗ Đã Nghỉ' }).getAttribute('href')).toBe('/nhan-vien/4');
    expect(within(row).getByRole('button', { name: 'Khôi phục Đỗ Đã Nghỉ' })).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Tìm theo mã, họ tên hoặc CCCD'), { target: { value: 'nv04' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tìm' }));
    await waitFor(() =>
      expect(mocked.get).toHaveBeenLastCalledWith('api/employees/archived', { params: { q: 'nv04', page: 0, size: 20 } }),
    );
  });

  it('nút "Xóa hẳn" chỉ hiện với tài khoản admin', async () => {
    const { unmount } = renderList('hr01', ['ROLE_HR']);
    await screen.findByText('Đỗ Đã Nghỉ');
    expect(screen.queryByRole('button', { name: 'Xóa hẳn Đỗ Đã Nghỉ' })).toBeNull();
    unmount();
    const other = renderList('quantri2', ['ROLE_ADMIN']);
    await screen.findByText('Đỗ Đã Nghỉ');
    expect(screen.queryByRole('button', { name: 'Xóa hẳn Đỗ Đã Nghỉ' })).toBeNull();
    other.unmount();
    renderList('admin', ['ROLE_ADMIN']);
    await screen.findByText('Đỗ Đã Nghỉ');
    expect(screen.getByRole('button', { name: 'Xóa hẳn Đỗ Đã Nghỉ' })).toBeTruthy();
  });

  it('khôi phục có xác nhận, rồi tải lại danh sách', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderList();
    fireEvent.click(await screen.findByRole('button', { name: 'Khôi phục Đỗ Đã Nghỉ' }));
    expect(mocked.post).not.toHaveBeenCalled();
    mocked.get.mockResolvedValue({ data: [], headers: { 'x-total-count': '0' } } as any);
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận khôi phục' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/4/restore'));
    expect(await screen.findByText(/Đã khôi phục hồ sơ NV0400 – Đỗ Đã Nghỉ/)).toBeTruthy();
    expect(await screen.findByText('Không có hồ sơ nào trong vùng lưu trữ')).toBeTruthy();
  });

  it('admin bấm "Xóa hẳn" mở hộp thoại xóa của đúng người', async () => {
    renderList('admin', ['ROLE_ADMIN']);
    fireEvent.click(await screen.findByRole('button', { name: 'Xóa hẳn Đỗ Đã Nghỉ' }));
    expect(await screen.findByRole('heading', { name: 'Xóa hẳn hồ sơ NV0400 – Đỗ Đã Nghỉ' })).toBeTruthy();
  });
});
