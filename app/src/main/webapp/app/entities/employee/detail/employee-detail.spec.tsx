import { vi } from 'vitest';
import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { Link, MemoryRouter, Route, Routes } from 'react-router';

import { makeStore } from '../test-utils';
import EmployeeDetail from './employee-detail';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

const EMPLOYEES: Record<string, any> = {
  1: { id: 1, fullName: 'Trần Minh Quân', code: 'NV0142', status: 'DANG_LAM_VIEC' },
  2: { id: 2, fullName: 'Lê Thị Hoa', code: 'NV0200', status: 'DANG_LAM_VIEC' },
  3: { id: 3, fullName: 'Phạm Sắp Nghỉ', code: 'NV0300', status: 'DANG_LAM_VIEC', scheduledTermination: '2026-10-31' },
  5: {
    id: 5,
    fullName: 'Ngô Quá Hạn',
    code: 'NV0500',
    status: 'DANG_LAM_VIEC',
    scheduledTermination: '2026-10-31',
    scheduledTerminationOverdue: true,
    scheduledTerminationError: 'Ngày làm việc cuối trước giai đoạn làm việc đã lên kế hoạch từ 03/11/2026',
  },
  4: {
    id: 4,
    fullName: 'Đỗ Đã Nghỉ',
    code: 'NV0400',
    status: 'DA_NGHI',
    lastWorkingDay: '2026-10-26',
    terminationDate: '2026-10-27',
    terminationUndoable: true,
  },
  8: {
    id: 8,
    fullName: 'Hồ Đã Nghỉ',
    code: 'NV0800',
    status: 'DA_NGHI',
    lastWorkingDay: '2026-09-30',
    terminationDate: '2026-10-01',
    archivable: true,
  },
  9: {
    id: 9,
    fullName: 'Mai Lưu Trữ',
    code: 'NV0900',
    status: 'DA_NGHI',
    lastWorkingDay: '2026-09-30',
    terminationDate: '2026-10-01',
    terminationUndoable: true,
    archivedAt: '2026-10-20T03:00:00Z',
    archivedBy: 'hr01',
  },
};

const renderDetail = (id: number, authorities = ['ROLE_HR'], query = '', account: Record<string, unknown> = {}) =>
  render(
    <Provider store={makeStore(authorities, account)}>
      <MemoryRouter initialEntries={[`/nhan-vien/${id}${query}`]}>
        <Routes>
          <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );

describe('EmployeeDetail', () => {
  it('?tab=contracts mở thẳng tab Hợp đồng & lương', async () => {
    renderDetail(1, ['ROLE_HR'], '?tab=contracts');
    const tab = await screen.findByRole('tab', { name: 'Hợp đồng & lương' });
    expect(tab.getAttribute('aria-selected')).toBe('true');
  });

  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
    mocked.get.mockImplementation((url: string) => {
      const sensitive = /api\/employees\/(\d+)\/sensitive$/.exec(url);
      if (sensitive) return Promise.resolve({ data: { idNumber: sensitive[1] === '1' ? '001096012345' : '079000000002' } } as any);
      const one = /api\/employees\/(\d+)$/.exec(url);
      if (one) return Promise.resolve({ data: EMPLOYEES[one[1]] } as any);
      return Promise.resolve({ data: [] } as any);
    });
  });

  it('chuyển sang nhân viên khác thì dữ liệu nhạy cảm bị ẩn lại và về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('button', { name: 'Hiện' }));
    expect(await screen.findByText('001096012345')).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.queryByText('001096012345')).toBeNull();
    expect(screen.queryByText('079000000002')).toBeNull();
    expect(screen.getByRole('button', { name: 'Hiện' })).toBeTruthy();
  });

  it('đang ở tab khác, chuyển nhân viên thì về tab Thông tin', async () => {
    render(
      <Provider store={makeStore(['ROLE_HR'])}>
        <MemoryRouter initialEntries={['/nhan-vien/1']}>
          <Link to="/nhan-vien/2">Sang người khác</Link>
          <Routes>
            <Route path="/nhan-vien/:id" element={<EmployeeDetail />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử công tác' }));
    expect(screen.getByRole('tab', { name: 'Lịch sử công tác' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.click(screen.getByRole('link', { name: 'Sang người khác' }));
    await screen.findByRole('heading', { name: 'Lê Thị Hoa' });
    expect(screen.getByRole('tab', { name: 'Thông tin' }).getAttribute('aria-selected')).toBe('true');
  });

  it('lịch nghỉ đã hẹn: nhãn "Sắp nghỉ" và nút "Hủy lịch nghỉ" có xác nhận', async () => {
    mocked.delete.mockResolvedValue({ data: {} } as any);
    renderDetail(3);
    await screen.findByRole('heading', { name: 'Phạm Sắp Nghỉ' });
    expect(screen.getByText('Sắp nghỉ (31/10/2026)')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Cho nghỉ việc' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Hủy lịch nghỉ' }));
    expect(mocked.delete).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hủy lịch' }));
    await waitFor(() => expect(mocked.delete).toHaveBeenCalledWith('api/employees/3/termination'));
  });

  it('đã nghỉ và hoàn tác được: nút "Hoàn tác nghỉ việc" có xác nhận', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    expect(mocked.post).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/4/termination/undo'));
  });

  it('hoàn tác bị từ chối vì dữ liệu đã sửa thì hiện chi tiết từ server', async () => {
    mocked.post.mockRejectedValue({
      response: { status: 400, data: { message: 'error.undoconflict', detail: 'Không hoàn tác được: Hợp đồng HD01 đã sửa' } },
    });
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    expect(await screen.findByText('Không hoàn tác được: Hợp đồng HD01 đã sửa')).toBeTruthy();
  });

  it('KE_TOAN thấy nhãn nhưng không có nút hủy lịch / hoàn tác', async () => {
    const { unmount } = renderDetail(3, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Phạm Sắp Nghỉ' });
    expect(screen.getByText('Sắp nghỉ (31/10/2026)')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Hủy lịch nghỉ' })).toBeNull();
    unmount();
    renderDetail(4, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    expect(screen.queryByRole('button', { name: 'Hoàn tác nghỉ việc' })).toBeNull();
  });

  it('lịch nghỉ quá hạn chưa áp dụng được: nhãn đỏ kèm lý do', async () => {
    renderDetail(5);
    await screen.findByRole('heading', { name: 'Ngô Quá Hạn' });
    expect(
      screen.getByText('Quá hạn nghỉ việc – chưa áp dụng được: Ngày làm việc cuối trước giai đoạn làm việc đã lên kế hoạch từ 03/11/2026'),
    ).toBeTruthy();
    expect(screen.queryByText('Sắp nghỉ (31/10/2026)')).toBeNull();
    expect(screen.getByRole('button', { name: 'Hủy lịch nghỉ' })).toBeTruthy();
  });

  it('hoàn tác nhưng tài khoản đã bỏ gắn: hiện ghi chú của server', async () => {
    mocked.post.mockResolvedValue({ data: { terminationNotice: 'Tài khoản đăng nhập đã bị bỏ gắn — không mở lại tài khoản cũ.' } } as any);
    renderDetail(4);
    await screen.findByRole('heading', { name: 'Đỗ Đã Nghỉ' });
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tác nghỉ việc' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận hoàn tác' }));
    expect(await screen.findByText('Tài khoản đăng nhập đã bị bỏ gắn — không mở lại tài khoản cũ.')).toBeTruthy();
  });
  it('nút "Tạo văn bản từ mẫu" mở tab Hợp đồng và hộp văn bản của HĐ mới nhất', async () => {
    mocked.get.mockImplementation((url: string) => {
      if (url === 'api/employees/1') return Promise.resolve({ data: EMPLOYEES[1] } as any);
      if (url === 'api/employees/1/contracts')
        return Promise.resolve({
          data: [
            { id: 70, type: 'HDTV', startDate: '2026-01-01', endDate: '2026-02-28', status: 'DA_KY' },
            { id: 71, type: 'HDLD_XDTH', contractNo: 'DOIT/2026/HĐLĐ/9', startDate: '2026-03-01', status: 'DA_KY' },
          ],
        } as any);
      return Promise.resolve({ data: [] } as any);
    });
    renderDetail(1);
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo văn bản từ mẫu' }));
    expect(await screen.findByRole('heading', { name: /Văn bản — HĐLĐ xác định thời hạn số DOIT\/2026\/HĐLĐ\/9/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Hợp đồng & lương' }).getAttribute('aria-selected')).toBe('true');
  });

  describe('tab Thông tin hai cột (canvas)', () => {
    const iso = (days: number) => {
      const d = new Date();
      d.setDate(d.getDate() + days);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    };
    const vn = (isoDate: string) => isoDate.split('-').reverse().join('/');
    const soon = iso(10);

    beforeEach(() => {
      mocked.get.mockImplementation((url: string) => {
        if (url === 'api/employees/1') return Promise.resolve({ data: EMPLOYEES[1] } as any);
        if (url === 'api/employees/1/contracts')
          return Promise.resolve({
            data: [
              { id: 70, type: 'HDTV', contractNo: '77/HĐTV', startDate: '2025-01-01', endDate: '2025-02-28', status: 'DA_KET_THUC' },
              { id: 71, type: 'HDLD_XDTH', contractNo: '112/HĐLĐ', startDate: '2025-03-01', endDate: soon, status: 'DA_KY' },
            ],
          } as any);
        if (url === 'api/employees/1/assignment-decisions')
          return Promise.resolve({
            data: [
              {
                decision: {
                  id: 5,
                  decisionNo: '08/QĐ',
                  signDate: '2025-05-20',
                  type: 'CONG_TAC',
                  unit: 'TP.HCM',
                  fromDate: '2025-06-01',
                  toDate: '2025-08-31',
                },
                documents: [],
              },
            ],
          } as any);
        if (url === 'api/employees/1/assignments/timeline')
          return Promise.resolve({
            data: [
              { id: 1, from: '2025-01-01', to: '2025-05-31', type: 'VAN_PHONG', label: 'Văn phòng Hà Nội' },
              { id: 2, from: '2025-06-01', to: null, type: 'CONG_TAC', unit: 'Chi nhánh TP.HCM' },
            ],
          } as any);
        return Promise.resolve({ data: [] } as any);
      });
    });

    it('thẻ đầu có chip "HĐ hết hạn"; bảng Hợp đồng & quyết định với chip trạng thái; dòng thời gian công tác', async () => {
      renderDetail(1);
      expect(await screen.findByText(`HĐ hết hạn ${vn(soon)}`)).toBeTruthy();
      const table = await screen.findByRole('region', { name: 'Hợp đồng & quyết định' });
      expect(table.textContent).toContain('HĐLĐ xác định thời hạn');
      expect(table.textContent).toContain('Số 112/HĐLĐ');
      expect(table.textContent).toContain('Sắp hết hạn');
      expect(await screen.findByText('QĐ cử đi công tác')).toBeTruthy();
      expect(table.textContent).toContain('Đã kết thúc');
      const timeline = screen.getByRole('region', { name: 'Lịch sử công tác' });
      expect(await screen.findByText('Chi nhánh TP.HCM')).toBeTruthy();
      expect(timeline.textContent).toContain('01/06/2025 – nay');
      expect(screen.getByRole('region', { name: 'Hồ sơ scan (PDF)' })).toBeTruthy();
      expect(screen.getByRole('region', { name: 'Thông tin cá nhân' })).toBeTruthy();
    });

    it('"+ Thêm điều động" mở tab Lịch sử công tác với biểu mẫu; "Xem chi tiết" mở tab Hợp đồng', async () => {
      renderDetail(1);
      fireEvent.click(await screen.findByRole('button', { name: '+ Thêm điều động' }));
      expect(screen.getByRole('tab', { name: 'Lịch sử công tác' }).getAttribute('aria-selected')).toBe('true');
      expect(await screen.findByRole('heading', { name: 'Thêm điều động / vị trí làm việc' })).toBeTruthy();
      fireEvent.click(screen.getByRole('tab', { name: 'Thông tin' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Xem chi tiết' }));
      expect(screen.getByRole('tab', { name: 'Hợp đồng & lương' }).getAttribute('aria-selected')).toBe('true');
    });

    it('Kế toán: không có "+ Thêm điều động", không tải quyết định cử nhân sự', async () => {
      renderDetail(1, ['ROLE_KE_TOAN']);
      await screen.findByRole('region', { name: 'Hợp đồng & quyết định' });
      expect(screen.queryByRole('button', { name: '+ Thêm điều động' })).toBeNull();
      expect(mocked.get).not.toHaveBeenCalledWith('api/employees/1/assignment-decisions');
    });
  });

  it('Kế toán không thấy nút "Tạo văn bản từ mẫu"', async () => {
    renderDetail(1, ['ROLE_KE_TOAN']);
    await screen.findByRole('heading', { name: 'Trần Minh Quân' });
    expect(screen.queryByRole('button', { name: 'Tạo văn bản từ mẫu' })).toBeNull();
  });

  describe('tab Nghỉ phép', () => {
    const leaveData = {
      balance: {
        employeeId: 6,
        annual: {
          previousYear: 0,
          currentYear: 5,
          total: 5,
          requestable: 6,
          baseAnnualDays: 14,
          seniorityYears: 10,
          seniorityExtraDays: 2,
          annualDays: 16,
        },
        compensatory: { previousYear: 0, currentYear: 8, total: 8 },
        sick: { usedDays: 1, limitDays: 30 },
        overtime: { monthHours: 2, monthCap: 40 },
      },
      requests: [{ id: 1, employeeId: 6, type: 'PHEP_NAM', fromDate: '2026-11-02', toDate: '2026-11-03', days: 2, status: 'CHO_DUYET' }],
      ledger: [{ id: 1, pool: 'PHEP_NAM', bucket: 'NAM_NAY', year: 2026, amount: 5, effectiveDate: '2026-10-01', reason: 'DAU_KY' }],
    };

    const withLeave = (status: number | null) => {
      EMPLOYEES[6] = { id: 6, fullName: 'Vũ Nghỉ Phép', code: 'NV0600', status: 'DANG_LAM_VIEC', personType: 'NHAN_VIEN' };
      EMPLOYEES[7] = { id: 7, fullName: 'Thực Tập Sinh', code: 'TT0700', status: 'DANG_LAM_VIEC', personType: 'THUC_TAP' };
      const base = mocked.get.getMockImplementation()!;
      mocked.get.mockImplementation((url: string, cfg?: any) => {
        if (/api\/leave\/employees\/\d+$/.test(url)) {
          return status === null
            ? Promise.resolve({ data: leaveData } as any)
            : Promise.reject(Object.assign(new Error(String(status)), { response: { status, data: {} } }));
        }
        return base(url, cfg);
      });
    };

    it('HR thấy tab Nghỉ phép với số dư, đơn và sổ', async () => {
      withLeave(null);
      renderDetail(6, ['ROLE_HR']);
      const tab = await screen.findByRole('tab', { name: 'Nghỉ phép' });
      fireEvent.click(tab);
      expect(await screen.findByText('Phép năm còn lại')).toBeTruthy();
      expect(screen.getByText('Phép năm: 14 + 2 (thâm niên 10 năm) = 16 ngày')).toBeTruthy();
      expect(screen.getByText('Số dư đầu kỳ')).toBeTruthy();
      expect(screen.getByText('02/11/2026 – 03/11/2026')).toBeTruthy();
    });

    it('ẩn với người không đủ quyền (server 403)', async () => {
      withLeave(403);
      renderDetail(6, ['ROLE_BGD'], '', { isApprover: true });
      await screen.findByRole('heading', { name: 'Vũ Nghỉ Phép' });
      await waitFor(() => expect(mocked.get).toHaveBeenCalledWith('api/leave/employees/6'));
      expect(screen.queryByRole('tab', { name: 'Nghỉ phép' })).toBeNull();
    });

    it('Kế toán không phải trưởng nhóm: không có tab, không gọi API', async () => {
      withLeave(null);
      renderDetail(6, ['ROLE_KE_TOAN']);
      await screen.findByRole('heading', { name: 'Vũ Nghỉ Phép' });
      expect(screen.queryByRole('tab', { name: 'Nghỉ phép' })).toBeNull();
      expect(mocked.get).not.toHaveBeenCalledWith('api/leave/employees/6');
    });

    it('thực tập sinh không có tab Nghỉ phép', async () => {
      withLeave(null);
      renderDetail(7, ['ROLE_HR']);
      await screen.findByRole('heading', { name: 'Thực Tập Sinh' });
      expect(screen.queryByRole('tab', { name: 'Nghỉ phép' })).toBeNull();
      expect(mocked.get).not.toHaveBeenCalledWith('api/leave/employees/7');
    });
  });
  describe('lưu trữ, khôi phục, nhận làm lại', () => {
    it('Đã nghỉ, chưa lưu trữ: có "Đưa vào lưu trữ" và "Nhận làm lại"', async () => {
      renderDetail(8);
      await screen.findByRole('heading', { name: 'Hồ Đã Nghỉ' });
      expect(screen.getByRole('button', { name: 'Đưa vào lưu trữ' })).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Nhận làm lại' })).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: 'Nhận làm lại' }));
      expect(await screen.findByRole('heading', { name: 'Nhận làm lại: Hồ Đã Nghỉ' })).toBeTruthy();
    });

    it('đang làm việc không đủ điều kiện: không có hai nút', async () => {
      renderDetail(1);
      await screen.findByRole('heading', { name: 'Trần Minh Quân' });
      expect(screen.queryByRole('button', { name: 'Đưa vào lưu trữ' })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Nhận làm lại' })).toBeNull();
    });

    it('hồ sơ tạo nhầm (server báo archivable) đang làm việc: có "Đưa vào lưu trữ", không có "Nhận làm lại"', async () => {
      EMPLOYEES[10] = { id: 10, fullName: 'Tạo Nhầm', code: 'NV1000', status: 'DANG_LAM_VIEC', archivable: true };
      renderDetail(10);
      await screen.findByRole('heading', { name: 'Tạo Nhầm' });
      expect(screen.getByRole('button', { name: 'Đưa vào lưu trữ' })).toBeTruthy();
      expect(screen.queryByRole('button', { name: 'Nhận làm lại' })).toBeNull();
    });

    it('Kế toán không thấy nút lưu trữ / nhận làm lại', async () => {
      renderDetail(8, ['ROLE_KE_TOAN']);
      await screen.findByRole('heading', { name: 'Hồ Đã Nghỉ' });
      expect(screen.queryByRole('button', { name: 'Đưa vào lưu trữ' })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Nhận làm lại' })).toBeNull();
    });

    it('lưu trữ có xác nhận và ghi chú; hồ sơ còn người được quản lý thì hiện danh sách cần đổi', async () => {
      mocked.post.mockResolvedValue({
        data: {
          managedEmployees: [{ id: 11, code: 'NV0011', name: 'Lý Cấp Dưới' }],
          headedDepartments: [{ id: 3, code: null, name: 'Phòng Kỹ thuật' }],
        },
      } as any);
      renderDetail(8);
      await screen.findByRole('heading', { name: 'Hồ Đã Nghỉ' });
      fireEvent.click(screen.getByRole('button', { name: 'Đưa vào lưu trữ' }));
      expect(mocked.post).not.toHaveBeenCalled();
      fireEvent.change(await screen.findByLabelText('Ghi chú (không bắt buộc)'), { target: { value: 'Nghỉ đã lâu' } });
      fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu trữ' }));
      await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/8/archive', { note: 'Nghỉ đã lâu' }));
      expect(await screen.findByText('Hãy đổi quản lý/trưởng phòng cho các hồ sơ sau:')).toBeTruthy();
      expect(screen.getByRole('link', { name: 'NV0011 – Lý Cấp Dưới' }).getAttribute('href')).toBe('/nhan-vien/11');
      expect(screen.getByText('Trưởng phòng: Phòng Kỹ thuật')).toBeTruthy();
    });

    it('đã lưu trữ: có băng lưu trữ và nút Khôi phục, không có nút sửa', async () => {
      mocked.get.mockImplementation((url: string) => {
        if (url === 'api/employees/9') return Promise.resolve({ data: EMPLOYEES[9] } as any);
        if (url === 'api/employees/9/contracts')
          return Promise.resolve({ data: [{ id: 70, type: 'HDTV', startDate: '2026-01-01', status: 'DA_KET_THUC' }] } as any);
        return Promise.resolve({ data: [] } as any);
      });
      mocked.post.mockResolvedValue({ data: {} } as any);
      renderDetail(9);
      await screen.findByRole('heading', { name: 'Mai Lưu Trữ' });
      expect(screen.getByText('Hồ sơ đã lưu trữ (20/10/2026, hr01)')).toBeTruthy();
      for (const name of ['Sửa hồ sơ', 'Tạo văn bản từ mẫu', 'Hoàn tác nghỉ việc', 'Đưa vào lưu trữ', 'Nhận làm lại']) {
        expect(screen.queryByRole('button', { name })).toBeNull();
        expect(screen.queryByRole('link', { name })).toBeNull();
      }
      expect(screen.queryByRole('tab', { name: 'Tài khoản đăng nhập' })).toBeNull();
      fireEvent.click(screen.getByRole('tab', { name: 'Hợp đồng & lương' }));
      expect(await screen.findByText('HĐ thử việc')).toBeTruthy();
      expect(screen.queryByRole('button', { name: '+ Thêm hợp đồng' })).toBeNull();
      expect(screen.queryByRole('button', { name: /^Sửa/ })).toBeNull();

      fireEvent.click(screen.getByRole('button', { name: 'Khôi phục' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận khôi phục' }));
      await waitFor(() => expect(mocked.post).toHaveBeenCalledWith('api/employees/9/restore'));
    });

    it('đã lưu trữ: Quản trị/HR vẫn xem được Văn bản của HĐ và danh sách quyết định, không có nút cử nhân sự', async () => {
      mocked.get.mockImplementation((url: string) => {
        if (url === 'api/employees/9') return Promise.resolve({ data: EMPLOYEES[9] } as any);
        if (url === 'api/employees/9/contracts')
          return Promise.resolve({
            data: [{ id: 70, type: 'HDTV', contractNo: 'DOIT/2026/HĐTV/3', startDate: '2026-01-01', status: 'DA_KET_THUC' }],
          } as any);
        if (url === 'api/employees/9/assignment-decisions')
          return Promise.resolve({
            data: [
              {
                decision: { id: 4, decisionNo: 'DOIT/2026/QĐCNS/4', signDate: '2026-03-01', unit: 'LP Bank', fromDate: '2026-03-02' },
                documents: [],
              },
            ],
          } as any);
        return Promise.resolve({ data: [] } as any);
      });
      renderDetail(9);
      await screen.findByRole('heading', { name: 'Mai Lưu Trữ' });
      fireEvent.click(screen.getByRole('tab', { name: 'Hợp đồng & lương' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Văn bản HĐ thử việc DOIT/2026/HĐTV/3' }));
      expect(await screen.findByRole('heading', { name: /Văn bản — HĐ thử việc/ })).toBeTruthy();
      expect(screen.queryByRole('button', { name: 'Tạo văn bản' })).toBeNull();
      fireEvent.click(screen.getAllByRole('button', { name: 'Đóng' })[0]);

      fireEvent.click(screen.getByRole('tab', { name: 'Lịch sử công tác' }));
      expect(await screen.findByText('DOIT/2026/QĐCNS/4')).toBeTruthy();
      expect(screen.queryByRole('button', { name: /Cử đi công tác/ })).toBeNull();
    });

    it('Kế toán xem hồ sơ đã lưu trữ: có băng nhưng không có nút Khôi phục', async () => {
      renderDetail(9, ['ROLE_KE_TOAN']);
      await screen.findByRole('heading', { name: 'Mai Lưu Trữ' });
      expect(screen.getByText('Hồ sơ đã lưu trữ (20/10/2026, hr01)')).toBeTruthy();
      expect(screen.queryByRole('button', { name: 'Khôi phục' })).toBeNull();
    });
  });
});
