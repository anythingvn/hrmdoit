import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import RowMapping from './row-mapping';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const row = {
  excelRow: 18,
  fileCode: 'DOIT2508',
  fileName: 'Dương Tuấn Minh',
  position: 'Lập trình viên',
  employeeId: null,
  mappedManually: false,
};

describe('RowMapping', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        {
          id: 15,
          code: 'DOIT2515',
          fullName: 'Dương Tuấn Minh',
          position: 'Lập trình viên',
          personType: 'NHAN_VIEN',
          status: 'DANG_LAM_VIEC',
          exactName: true,
        },
      ],
    } as any);
  });

  it('mở ô chọn thì gợi ý theo tên, gõ tìm thì gọi lại với q, chọn thì gắn', async () => {
    const onChanged = vi.fn();
    mocked.put.mockResolvedValue({ data: { id: 7 } } as any);
    render(<RowMapping importId={7} row={row} onChanged={onChanged} />);
    fireEvent.click(screen.getByRole('button', { name: 'Chọn hồ sơ cho dòng 18' }));
    await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/payroll/imports/7/rows/18/candidates', { params: { q: undefined } }));
    fireEvent.change(screen.getByLabelText('Tìm hồ sơ (dòng 18)'), { target: { value: 'tuan minh' } });
    await waitFor(() =>
      expect(mocked.get).toHaveBeenLastCalledWith('api/payroll/imports/7/rows/18/candidates', { params: { q: 'tuan minh' } }),
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Chọn DOIT2515 – Dương Tuấn Minh' }));
    await waitFor(() => expect(mocked.put).toHaveBeenCalledWith('api/payroll/imports/7/rows/18/employee', { employeeId: 15 }));
    expect(onChanged).toHaveBeenCalledWith({ id: 7 });
  });

  it('hồ sơ đã gắn dòng khác thì hiện câu báo của máy chủ', async () => {
    mocked.put.mockRejectedValue({
      response: {
        status: 400,
        data: { message: 'error.employeemappedtwice', detail: 'Hồ sơ DOIT2515 – Dương Tuấn Minh đã gắn với dòng 19' },
      },
    });
    render(<RowMapping importId={7} row={row} onChanged={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Chọn hồ sơ cho dòng 18' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Chọn DOIT2515 – Dương Tuấn Minh' }));
    expect(await screen.findByText('Hồ sơ DOIT2515 – Dương Tuấn Minh đã gắn với dòng 19')).toBeTruthy();
  });

  it('tạo hồ sơ rút gọn: hợp đồng dịch vụ thì ẩn ô mã', async () => {
    mocked.post.mockResolvedValue({ data: { id: 7 } } as any);
    render(<RowMapping importId={7} row={{ ...row, excelRow: 23, fileCode: null, fileName: 'Hồ Thị Sen' }} onChanged={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Tạo hồ sơ rút gọn cho dòng 23' }));
    expect((screen.getByLabelText('Họ tên') as HTMLInputElement).value).toBe('Hồ Thị Sen');
    expect(screen.getByLabelText('Mã nhân viên')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Loại'), { target: { value: 'DICH_VU' } });
    expect(screen.queryByLabelText('Mã nhân viên')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Tạo và gắn' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/payroll/imports/7/rows/23/new-employee', {
        fullName: 'Hồ Thị Sen',
        personType: 'DICH_VU',
        code: null,
      }),
    );
  });
});
