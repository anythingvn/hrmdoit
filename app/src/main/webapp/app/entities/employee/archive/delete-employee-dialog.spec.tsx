import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import DeleteEmployeeDialog from './delete-employee-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = (onDone = vi.fn(), onClose = vi.fn()) =>
  render(<DeleteEmployeeDialog employee={{ id: 4, code: 'NV0400', fullName: 'Đỗ Đã Nghỉ' }} onClose={onClose} onDone={onDone} />);

describe('DeleteEmployeeDialog', () => {
  beforeEach(() => {
    mocked.delete.mockReset();
  });

  it('liệt kê dữ liệu sẽ bị xóa và dữ liệu được giữ, cảnh báo không hoàn tác được', () => {
    renderDialog();
    expect(screen.getByText(/không hoàn tác được/i)).toBeTruthy();
    expect(screen.getByText('Sẽ bị xóa')).toBeTruthy();
    expect(screen.getByText(/Hồ sơ và mọi dữ liệu cá nhân/)).toBeTruthy();
    expect(screen.getByText('Được giữ')).toBeTruthy();
    expect(screen.getByText(/Dòng lương các kỳ đã duyệt/)).toBeTruthy();
  });

  it('nút Xóa tắt cho đến khi gõ đúng mã và có lý do; gửi DELETE kèm body', async () => {
    const onDone = vi.fn();
    mocked.delete.mockResolvedValue({ data: null } as any);
    renderDialog(onDone);
    const button = screen.getByRole('button', { name: 'Xóa hẳn' }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Gõ lại mã nhân viên (NV0400)'), { target: { value: 'NV040' } });
    fireEvent.change(screen.getByLabelText('Lý do xóa hẳn'), { target: { value: 'Hồ sơ trùng' } });
    expect(button.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Gõ lại mã nhân viên (NV0400)'), { target: { value: 'NV0400' } });
    fireEvent.change(screen.getByLabelText('Lý do xóa hẳn'), { target: { value: '   ' } });
    expect(button.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Lý do xóa hẳn'), { target: { value: 'Hồ sơ trùng' } });
    expect(button.disabled).toBe(false);
    fireEvent.click(button);
    await waitFor(() =>
      expect(mocked.delete).toHaveBeenCalledWith('api/employees/4', { data: { confirmCode: 'NV0400', reason: 'Hồ sơ trùng' } }),
    );
    await waitFor(() => expect(onDone).toHaveBeenCalled());
  });

  it('bị chặn: hiện lý do từ server, không đóng', async () => {
    const onDone = vi.fn();
    mocked.delete.mockRejectedValue({
      response: { status: 400, data: { message: 'error.deleteblocked', detail: 'Chưa xóa hẳn được: kỳ lương 10/2026 chưa duyệt' } },
    });
    renderDialog(onDone);
    fireEvent.change(screen.getByLabelText('Gõ lại mã nhân viên (NV0400)'), { target: { value: 'NV0400' } });
    fireEvent.change(screen.getByLabelText('Lý do xóa hẳn'), { target: { value: 'Hồ sơ trùng' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xóa hẳn' }));
    expect(await screen.findByText('Chưa xóa hẳn được: kỳ lương 10/2026 chưa duyệt')).toBeTruthy();
    expect(onDone).not.toHaveBeenCalled();
  });
});
