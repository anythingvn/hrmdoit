import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import TerminateDialog from './terminate-dialog';

jest.mock('axios');
const mocked = axios as jest.Mocked<typeof axios>;

const renderDialog = () => render(<TerminateDialog employeeId={7} isOpen toggle={jest.fn()} onDone={jest.fn()} />);

const fillBasics = () => {
  fireEvent.change(screen.getByLabelText('Ngày làm việc cuối'), { target: { value: '31/10/2026' } });
  fireEvent.change(screen.getByLabelText('Hình thức nghỉ việc'), { target: { value: 'TU_NGHI' } });
};

describe('TerminateDialog', () => {
  beforeEach(() => mocked.post.mockReset());

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
});
