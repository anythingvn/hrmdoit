import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import RehireDialog from './rehire-dialog';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const renderDialog = (onDone = vi.fn()) =>
  render(
    <RehireDialog
      employee={{ id: 4, fullName: 'Đỗ Đã Nghỉ', lastWorkingDay: '2026-09-30', position: 'Kỹ sư', personType: 'NHAN_VIEN' }}
      onClose={vi.fn()}
      onDone={onDone}
    />,
  );

describe('RehireDialog', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.get.mockResolvedValue({
      data: [
        { id: 1, name: 'Phòng Kỹ thuật', active: true },
        { id: 2, name: 'Phòng cũ', active: false },
      ],
    } as any);
  });

  it('ngày vào làm mới bắt buộc và đúng dạng dd/MM/yyyy', async () => {
    renderDialog();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhận làm lại' }));
    expect(await screen.findByText('Bắt buộc nhập')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Ngày vào làm mới'), { target: { value: '2026-11-02' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhận làm lại' }));
    expect(await screen.findByText('Ngày không hợp lệ, nhập dd/MM/yyyy')).toBeTruthy();
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it('gửi ngày ISO; phòng ban, vị trí, loại hồ sơ để trống thì giữ như cũ', async () => {
    const onDone = vi.fn();
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDialog(onDone);
    expect(await screen.findByRole('option', { name: 'Phòng Kỹ thuật' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Phòng cũ' })).toBeNull();
    fireEvent.change(screen.getByLabelText('Ngày vào làm mới'), { target: { value: '02/11/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhận làm lại' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/4/rehire', {
        joinDate: '2026-11-02',
        departmentId: null,
        position: null,
        personType: null,
      }),
    );
    await waitFor(() => expect(onDone).toHaveBeenCalled());
  });

  it('gửi phòng ban, vị trí, loại hồ sơ khi có chọn', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDialog();
    await screen.findByRole('option', { name: 'Phòng Kỹ thuật' });
    fireEvent.change(screen.getByLabelText('Ngày vào làm mới'), { target: { value: '02/11/2026' } });
    fireEvent.change(screen.getByLabelText('Phòng ban'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('Vị trí'), { target: { value: ' Trưởng nhóm ' } });
    fireEvent.change(screen.getByLabelText('Loại hồ sơ'), { target: { value: 'DICH_VU' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhận làm lại' }));
    await waitFor(() =>
      expect(mocked.post).toHaveBeenCalledWith('api/employees/4/rehire', {
        joinDate: '2026-11-02',
        departmentId: 1,
        position: 'Trưởng nhóm',
        personType: 'DICH_VU',
      }),
    );
  });

  it('server trả rehiredatebeforeleave thì hiện câu tiếng Việt', async () => {
    mocked.post.mockRejectedValue({ response: { status: 400, data: { message: 'error.rehiredatebeforeleave' } } });
    renderDialog();
    fireEvent.change(screen.getByLabelText('Ngày vào làm mới'), { target: { value: '15/09/2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhận làm lại' }));
    expect(await screen.findByText('Ngày vào làm mới phải sau ngày làm việc cuối của lần trước')).toBeTruthy();
  });
});
