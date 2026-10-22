export interface IDepartment {
  id?: number;
  name?: string;
  active?: boolean;
  /** Trưởng phòng (nhân viên đang làm, loại NHAN_VIEN); null = chưa có. */
  headId?: number | null;
  /** Họ tên trưởng phòng (chỉ đọc). */
  headName?: string | null;
}

export const defaultValue: Readonly<IDepartment> = {
  active: false,
};
