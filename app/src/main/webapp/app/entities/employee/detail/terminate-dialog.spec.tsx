import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import TerminateDialog from './terminate-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = () => render(<TerminateDialog employeeId={7} isOpen toggle={vi.fn()} onDone={vi.fn()} />);

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('Ngày làm việc cuối'), { target: { value: '31/10/2026' } });
  fireEvent.change(screen.getByLabelText('Hình thức nghỉ việc'), { target: { value: 'TU_NGHI' } });
};

describe('TerminateDialog', () => {
  beforeEach(() => {
    mocked.post.mockReset();
    mocked.get.mockReset();
    mocked.delete.mockReset();
  });

  it('ngày nghỉ việc tự điền = ngày làm việc cuối + 1 và gửi lên server', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDialog();
    fillBasics();
    expect(screen.getByLabelText('Ngày nghỉ việc').value).toBe('01/11/2026');
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith(
        'api/employees/7/terminate',
        expect.objectContaining({ lastWorkingDay: '2026-10-31', terminationDate: '2026-11-01', terminationType: 'TU_NGHI' }),
      ),
    );
  });

  it('ngày nghỉ việc sửa tay được giữ nguyên', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDialog();
    fillBasics();
    fireEvent.change(screen.getByLabelText('Ngày nghỉ việc'), { target: { value: '15/11/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/7/terminate', expect.objectContaining({ terminationDate: '2026-11-15' })),
    );
  });

  it('ngày nghỉ việc trước ngày làm việc cuối thì báo lỗi, không gửi', async () => {
    renderDialog();
    fillBasics();
    fireEvent.change(screen.getByLabelText('Ngày nghỉ việc'), { target: { value: '30/10/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    expect(await screen.findByText('Ngày nghỉ việc không được trước ngày làm việc cuối')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('server báo nghỉ trước giai đoạn đã bắt đầu thì hướng dẫn sửa Lịch sử công tác', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.terminatebeforeassignment' } } });
    renderDialog();
    fillBasics();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    expect(await screen.findByText(/xóa \/ sửa giai đoạn đó trong "Lịch sử công tác"/)).toBeTruthy();
  });

  it('chỉ vướng giai đoạn "quay về" tự tạo: một nút xóa giai đoạn đó rồi cho nghỉ', async () => {
    mocked.post
      .mockRejectedValueOnce({ response: { status: 400, data: { message: 'error.terminatebeforeassignment' } } })
      .mockResolvedValueOnce({ data: {} } as any);
    mocked.get.mockResolvedValue({
      data: [{ id: 55, type: 'VAN_PHONG', unit: null, fromDate: '2027-02-01', toDate: null, started: false, autoReturn: true }],
    } as any);
    mocked.delete.mockResolvedValue({} as any);
    const onDone = vi.fn();
    render(<TerminateDialog employeeId={7} isOpen toggle={vi.fn()} onDone={onDone} />);
    fillBasics();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    const combined = await screen.findByRole('button', { name: 'Xóa giai đoạn quay về và cho nghỉ' });
    expect(mocked.get).toHaveBeenCalledWith('api/employees/7/termination/blockers', { params: { lastWorkingDay: '2026-10-31' } });
    expect(screen.getByText(/01\/02\/2027/)).toBeTruthy();
    fireEvent.click(combined);
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(mocked.delete).toHaveBeenCalledWith('api/employees/7/assignments/55');
    expect(mocked.post).toHaveBeenCalledTimes(2);
    expect(mocked.delete.mock.invocationCallOrder[0]).toBeLessThan(mocked.post.mock.invocationCallOrder[1]);
  });

  it('vướng giai đoạn khác (không tự tạo) thì không có nút xóa nhanh', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.terminatebeforeassignment' } } });
    mocked.get.mockResolvedValue({
      data: [
        { id: 56, type: 'CONG_TAC', unit: 'Đà Nẵng', fromDate: '2026-11-10', toDate: '2026-11-12', started: false, autoReturn: false },
      ],
    } as any);
    renderDialog();
    fillBasics();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nghỉ việc' }));
    expect(await screen.findByText(/xóa \/ sửa giai đoạn đó trong "Lịch sử công tác"/)).toBeTruthy();
    await waitFor(() => expect(mocked.get).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Xóa giai đoạn quay về và cho nghỉ' })).toBeNull();
  });

  it('giải thích ngày tương lai được hẹn lịch', () => {
    renderDialog();
    expect(screen.getByText(/hẹn lịch nghỉ việc/)).toBeTruthy();
  });
});
