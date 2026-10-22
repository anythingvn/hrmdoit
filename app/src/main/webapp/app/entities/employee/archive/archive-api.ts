import axios from 'axios';

import { PersonType } from 'app/shared/model/employee.model';

/** Một dòng của vùng lưu trữ ({@code GET api/employees/archived}). Không có dữ liệu nhạy cảm. */
export interface IArchivedEmployee {
  id: number;
  code?: string | null;
  fullName?: string | null;
  departmentName?: string | null;
  lastWorkingDay?: string | null;
  archivedAt?: string | null;
  archivedBy?: string | null;
  archiveNote?: string | null;
}

export interface IArchiveRef {
  id: number;
  code?: string | null;
  name?: string | null;
}

/** Kết quả lưu trữ: những người và phòng ban còn nhận hồ sơ này làm quản lý / trưởng phòng. */
export interface IArchiveResult {
  managedEmployees: IArchiveRef[];
  headedDepartments: IArchiveRef[];
}

export interface IRehireRequest {
  joinDate: string;
  departmentId: number | null;
  position: string | null;
  personType: PersonType | null;
}

/** Một dòng nhật ký xóa hẳn (chỉ tài khoản admin xem được). */
export interface IDeletionLog {
  id: number;
  deletedAt: string;
  deletedBy: string;
  reason: string;
  employeeCode?: string | null;
  employeeName?: string | null;
}

export interface IPage<T> {
  items: T[];
  total: number;
}

/** Tài khoản quản trị hệ thống: người duy nhất được xóa hẳn và xem nhật ký xóa. */
export const SYSTEM_ADMIN_LOGIN = 'admin';

const page = <T>(r: { data?: T[]; headers?: Record<string, any> }): IPage<T> => {
  const items = r?.data ?? [];
  const header = Number(r?.headers?.['x-total-count']);
  return { items, total: Number.isFinite(header) ? header : items.length };
};

/** Gọi REST của lưu trữ, khôi phục, nhận làm lại và xóa hẳn. */
export const archiveApi = {
  list: async (q: string, pageIndex: number, size: number): Promise<IPage<IArchivedEmployee>> =>
    page(await axios.get<IArchivedEmployee[]>('api/employees/archived', { params: { q, page: pageIndex, size } })),
  archive: async (id: number, note: string): Promise<IArchiveResult> =>
    (await axios.post<IArchiveResult>(`api/employees/${id}/archive`, { note: note.trim() || null })).data,
  restore: (id: number) => axios.post(`api/employees/${id}/restore`),
  rehire: (id: number, body: IRehireRequest) => axios.post(`api/employees/${id}/rehire`, body),
  deletePermanently: (id: number, confirmCode: string, reason: string) =>
    axios.delete(`api/employees/${id}`, { data: { confirmCode, reason } }),
  deletions: async (pageIndex: number, size: number): Promise<IPage<IDeletionLog>> =>
    page(await axios.get<IDeletionLog[]>('api/admin/employee-deletions', { params: { page: pageIndex, size } })),
};
