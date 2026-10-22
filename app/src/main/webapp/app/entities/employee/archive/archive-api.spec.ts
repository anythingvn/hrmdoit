import { vi } from 'vitest';
import axios from 'axios';

import { archiveApi } from './archive-api';

vi.mock('axios');
const mocked = vi.mocked(axios, true);

describe('archiveApi', () => {
  beforeEach(() => {
    mocked.get.mockReset();
    mocked.post.mockReset();
    mocked.delete.mockReset();
  });

  it('vùng lưu trữ: tìm, phân trang, đọc tổng số từ header', async () => {
    mocked.get.mockResolvedValue({ data: [{ id: 1, code: 'NV1' }], headers: { 'x-total-count': '42' } } as any);
    const r = await archiveApi.list('quan', 2, 20);
    expect(mocked.get).toHaveBeenCalledWith('api/employees/archived', { params: { q: 'quan', page: 2, size: 20 } });
    expect(r).toEqual({ items: [{ id: 1, code: 'NV1' }], total: 42 });
  });

  it('lưu trữ gửi ghi chú (rỗng thì null) và trả danh sách cần đổi quản lý', async () => {
    mocked.post.mockResolvedValue({ data: { managedEmployees: [], headedDepartments: [] } } as any);
    await archiveApi.archive(5, '  ');
    expect(mocked.post).toHaveBeenCalledWith('api/employees/5/archive', { note: null });
    await archiveApi.archive(5, ' Nghỉ lâu ');
    expect(mocked.post).toHaveBeenLastCalledWith('api/employees/5/archive', { note: 'Nghỉ lâu' });
  });

  it('khôi phục, nhận làm lại, xóa hẳn (body trong data), nhật ký xóa', async () => {
    mocked.post.mockResolvedValue({ data: {} } as any);
    mocked.delete.mockResolvedValue({ data: null } as any);
    mocked.get.mockResolvedValue({ data: [], headers: {} } as any);
    await archiveApi.restore(5);
    expect(mocked.post).toHaveBeenCalledWith('api/employees/5/restore');
    await archiveApi.rehire(5, { joinDate: '2026-11-02', departmentId: null, position: null, personType: null });
    expect(mocked.post).toHaveBeenLastCalledWith('api/employees/5/rehire', {
      joinDate: '2026-11-02',
      departmentId: null,
      position: null,
      personType: null,
    });
    await archiveApi.deletePermanently(5, 'NV0005', 'Trùng hồ sơ');
    expect(mocked.delete).toHaveBeenCalledWith('api/employees/5', { data: { confirmCode: 'NV0005', reason: 'Trùng hồ sơ' } });
    const log = await archiveApi.deletions(0, 20);
    expect(mocked.get).toHaveBeenLastCalledWith('api/admin/employee-deletions', { params: { page: 0, size: 20 } });
    expect(log).toEqual({ items: [], total: 0 });
  });
});
