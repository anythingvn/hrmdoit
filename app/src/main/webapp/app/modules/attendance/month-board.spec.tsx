import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Provider } from 'react-redux';

import { makeStore } from 'app/entities/employee/test-utils';
import MonthBoard from './month-board';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const day = (d: string, over: Record<string, unknown> = {}) => ({
  employeeId: 1,
  code: 'NV1',
  name: 'An',
  day: d,
  group: 'VP',
  groupLabel: 'Văn phòng',
  autoKind: null,
  credit: 1,
  actualCredit: 1,
  status: 'DI_LAM',
  in: '08:00',
  out: '17:00',
  lateMinutes: 0,
  earlyMinutes: 0,
  remote: false,
  leavePart: null,
  markedAbsent: false,
  ...over,
});

const totals = (over: Record<string, unknown> = {}) => ({
  standardDays: 21,
  actualDays: 2,
  workDays: 2,
  paidLeaveDays: 0,
  unpaidLeaveDays: 0,
  sickDays: 0,
  holidayDays: 0,
  tripDays: 0,
  lateCount: 0,
  lateMinutes: 0,
  earlyCount: 0,
  halfDays: 0,
  absentDays: 0,
  missingDays: 0,
  ...over,
});

const D1 = '2026-11-02';
const D2 = '2026-11-03';

const board = (status: 'DANG_MO' | 'DA_CHOT') => ({
  month: '2026-11',
  status,
  rows: [
    {
      employee: { id: 1, code: 'NV1', name: 'An', departmentId: 10, departmentName: 'Kỹ thuật', managerId: 5, managerName: 'Lan' },
      days: [day(D1), day(D2, { autoKind: 'PHEP_NAM', status: null, in: null, out: null, credit: null })],
      totals: totals(),
      changedAfterClose: status === 'DA_CHOT',
    },
    {
      employee: { id: 2, code: 'NV2', name: 'Bình', departmentId: 11, departmentName: 'Kế toán', managerId: 6, managerName: 'Hà' },
      days: [
        day(D1, { employeeId: 2, status: 'THIEU_GIO', out: null, credit: null, actualCredit: 0 }),
        day(D2, {
          employeeId: 2,
          status: 'CHUA_NHAP',
          in: null,
          out: null,
          credit: null,
          actualCredit: 0,
          group: 'SITE:7',
          groupLabel: 'Onsite · ACME',
        }),
      ],
      totals: totals({ actualDays: 0, missingDays: 2 }),
      changedAfterClose: false,
    },
  ],
});

const renderAs = (authorities: string[], status: 'DANG_MO' | 'DA_CHOT' = 'DANG_MO') => {
  mocked.get.mockResolvedValue({ data: board(status) } as any);
  return render(
    <Provider store={makeStore(authorities)}>
      <MonthBoard />
    </Provider>,
  );
};

describe('MonthBoard', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.put.mockReset();
  });

  it('mỗi người một dòng, ô giờ, nhãn tự điền, ô đỏ và cột tổng', async () => {
    renderAs(['ROLE_HR']);
    const an = within(await screen.findByTestId('board-row-1'));
    expect(an.getByText('08:00–17:00')).toBeTruthy();
    expect(an.getByText('P')).toBeTruthy();
    expect(screen.getByTestId(`cell-1-${D2}`).getAttribute('title')).toBe('Nghỉ phép năm');
    expect(screen.getByTestId(`cell-2-${D1}`).className).toContain('table-danger');
    expect(screen.getByTestId(`cell-2-${D2}`).className).toContain('table-danger');
    expect(screen.getByTestId(`cell-1-${D1}`).className).not.toContain('table-danger');
    expect(within(screen.getByTestId('board-row-2')).getByText('2')).toBeTruthy(); // cột Thiếu
    expect(screen.getByTestId('month-status').textContent).toBe('Đang mở');
    expect(mocked.get).toHaveBeenCalledWith('api/attendance/month', { params: { month: expect.stringMatching(/^\d{4}-\d{2}$/) } });
  });

  it('lọc theo phòng, nhóm và khách hàng', async () => {
    renderAs(['ROLE_BGD']);
    await screen.findByTestId('board-row-1');
    fireEvent.change(screen.getByLabelText('Lọc theo phòng'), { target: { value: '11' } });
    expect(screen.queryByTestId('board-row-1')).toBeNull();
    expect(screen.getByTestId('board-row-2')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Lọc theo phòng'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Lọc theo nhóm'), { target: { value: '5' } });
    expect(screen.getByTestId('board-row-1')).toBeTruthy();
    expect(screen.queryByTestId('board-row-2')).toBeNull();
    fireEvent.change(screen.getByLabelText('Lọc theo nhóm'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Lọc theo khách hàng'), { target: { value: 'SITE:7' } });
    expect(screen.queryByTestId('board-row-1')).toBeNull();
    expect(screen.getByTestId('board-row-2')).toBeTruthy();
  });

  it('chốt khi còn ô đỏ: hiện danh sách, "Vẫn chốt" gửi force=true', async () => {
    renderAs(['ROLE_HR']);
    await screen.findByTestId('board-row-1');
    mocked.post.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          message: 'error.attendanceunresolved',
          detail: 'còn ô',
          unresolved: [{ employeeId: 2, code: 'NV2', name: 'Bình', dates: [D1, D2] }],
        },
      },
    });
    fireEvent.click(screen.getByText('Chốt bảng công'));
    const list = await screen.findByTestId('unresolved');
    expect(within(list).getByText('NV2 Bình: 02/11, 03/11')).toBeTruthy();
    expect(mocked.post.mock.calls[0][0]).toMatch(/^api\/attendance\/month\/\d{4}-\d{2}\/close$/);
    expect(mocked.post.mock.calls[0][2]).toEqual({ params: { force: false } });

    mocked.post.mockResolvedValueOnce({ data: { month: '2026-11', status: 'DA_CHOT' } } as any);
    mocked.get.mockResolvedValue({ data: board('DA_CHOT') } as any);
    fireEvent.click(within(list).getByText('Vẫn chốt'));
    await waitFor(() => expect(screen.getByTestId('month-status').textContent).toBe('Đã chốt'));
    expect(mocked.post.mock.calls[1][2]).toEqual({ params: { force: true } });
    expect(screen.queryByTestId('unresolved')).toBeNull();
  });

  it('tháng đã chốt: dấu "Đổi sau chốt", nút Mở lại cần lý do, ô không bấm sửa được', async () => {
    renderAs(['ROLE_HR'], 'DA_CHOT');
    const an = within(await screen.findByTestId('board-row-1'));
    expect(an.getByText('Đổi sau chốt')).toBeTruthy();
    expect(within(screen.getByTestId('board-row-2')).queryByText('Đổi sau chốt')).toBeNull();
    expect(screen.queryByText('Chốt bảng công')).toBeNull();
    expect(screen.queryByLabelText(/Sửa giờ An/)).toBeNull();

    fireEvent.click(screen.getByText('Mở lại chốt'));
    const confirm = await screen.findByRole('button', { name: 'Mở lại' });
    expect((confirm as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Lý do mở lại'), { target: { value: 'Sửa giờ ngày 10' } });
    expect((confirm as HTMLButtonElement).disabled).toBe(false);
    mocked.post.mockResolvedValueOnce({ data: { month: '2026-11', status: 'DANG_MO' } } as any);
    fireEvent.click(confirm);
    await waitFor(() => expect(mocked.post).toHaveBeenCalled());
    expect(mocked.post.mock.calls[0][0]).toMatch(/^api\/attendance\/month\/\d{4}-\d{2}\/reopen$/);
    expect(mocked.post.mock.calls[0][1]).toEqual({ reason: 'Sửa giờ ngày 10' });
  });

  it('Kế toán, BGĐ chỉ xem: không có nút Chốt, ô không sửa được', async () => {
    renderAs(['ROLE_BGD']);
    await screen.findByTestId('board-row-1');
    expect(screen.queryByText('Chốt bảng công')).toBeNull();
    expect(screen.queryByLabelText(/Sửa giờ/)).toBeNull();
    expect(screen.getByText('Tải Excel')).toBeTruthy();
  });

  it('HR bấm ô để sửa giờ: gửi PUT /day một dòng rồi tải lại', async () => {
    renderAs(['ROLE_HR']);
    await screen.findByTestId('board-row-2');
    fireEvent.click(screen.getByLabelText('Sửa giờ Bình ngày 02/11/2026'));
    fireEvent.change(await screen.findByLabelText('Ra'), { target: { value: '17:30' } });
    mocked.put.mockResolvedValueOnce({ data: [] } as any);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() =>
      expect(mocked.put).toHaveBeenCalledWith('api/attendance/day', {
        date: D1,
        entries: [{ employeeId: 2, in: '08:00', out: '17:30', absent: false }],
      }),
    );
    await waitFor(() => expect(mocked.get).toHaveBeenCalledTimes(2));
  });

  it('lỗi server khi sửa ô hiện câu tiếng Việt', async () => {
    renderAs(['ROLE_HR']);
    await screen.findByTestId('board-row-2');
    fireEvent.click(screen.getByLabelText('Sửa giờ Bình ngày 02/11/2026'));
    mocked.put.mockRejectedValueOnce({ response: { status: 400, data: { message: 'error.attendancemonthclosed' } } });
    fireEvent.click(await screen.findByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Bảng công tháng này đã chốt, không sửa được giờ')).toBeTruthy();
  });
});
