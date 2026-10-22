/** Nhãn tiếng Việt, màu lịch và định dạng số dùng chung cho phần Nghỉ phép & đơn từ. */

export type LeaveType = 'PHEP_NAM' | 'VIEC_RIENG' | 'OM' | 'KHONG_LUONG' | 'NGHI_BU';
export type LeaveStatus = 'CHO_DUYET' | 'DA_DUYET' | 'TU_CHOI' | 'DA_RUT' | 'CHO_HUY' | 'DA_HUY';
export type DayPart = 'CA_NGAY' | 'SANG' | 'CHIEU';
export type PersonalReason = 'KET_HON' | 'CON_KET_HON' | 'TANG';
export type OvertimeStatus = 'CHO_DUYET' | 'DA_DUYET' | 'TU_CHOI' | 'DA_RUT' | 'CHO_XAC_NHAN_THEM' | 'HOAN_TAT' | 'DA_HUY';
export type Compensation = 'TRA_TIEN' | 'NGHI_BU';
export type ApproverKind = 'TRUONG_NHOM' | 'TRUONG_PHONG' | 'BGD' | 'QUAN_TRI';
/** Loại trên lịch: các loại đơn nghỉ, đi công tác (lịch sử vị trí), nghỉ việc (lịch nghỉ việc đã hẹn). */
export type CalendarType = LeaveType | 'CONG_TAC' | 'NGHI_VIEC';

export const LEAVE_TYPES: LeaveType[] = ['PHEP_NAM', 'VIEC_RIENG', 'OM', 'KHONG_LUONG', 'NGHI_BU'];

export const LEAVE_TYPE_LABEL: Record<LeaveType, string> = {
  PHEP_NAM: 'Nghỉ phép năm',
  VIEC_RIENG: 'Nghỉ việc riêng có lương',
  OM: 'Nghỉ ốm',
  KHONG_LUONG: 'Nghỉ không lương',
  NGHI_BU: 'Nghỉ bù',
};

/** Tên ngắn trên ô lịch ("Tên · Loại"). */
export const CALENDAR_TYPE_LABEL: Record<CalendarType, string> = {
  PHEP_NAM: 'Phép',
  VIEC_RIENG: 'Việc riêng',
  OM: 'Ốm',
  KHONG_LUONG: 'Không lương',
  NGHI_BU: 'Nghỉ bù',
  CONG_TAC: 'Công tác',
  NGHI_VIEC: 'Nghỉ việc',
};

/** Màu nền / chữ / viền theo loại (kiểu A trên canvas). */
export const CALENDAR_COLOR: Record<CalendarType, { bg: string; fg: string; border?: string }> = {
  PHEP_NAM: { bg: '#FBE1D9', fg: '#8F2712' },
  VIEC_RIENG: { bg: '#FBE1D9', fg: '#8F2712' },
  OM: { bg: '#F3ECD6', fg: '#5E4A12' },
  KHONG_LUONG: { bg: '#E2E0D4', fg: '#33404C' },
  NGHI_BU: { bg: '#E6EEDD', fg: '#2F4A1B' },
  CONG_TAC: { bg: '#DDE7F0', fg: '#1F3240' },
  NGHI_VIEC: { bg: '#FFFFFF', fg: '#8F2712', border: '#E84424' },
};

export const LEAVE_STATUS_LABEL: Record<LeaveStatus, string> = {
  CHO_DUYET: 'Chờ duyệt',
  DA_DUYET: 'Đã duyệt',
  TU_CHOI: 'Từ chối',
  DA_RUT: 'Đã rút',
  CHO_HUY: 'Chờ duyệt hủy',
  DA_HUY: 'Đã hủy',
};

export const OVERTIME_STATUS_LABEL: Record<OvertimeStatus, string> = {
  CHO_DUYET: 'Chờ duyệt',
  DA_DUYET: 'Đã duyệt, chờ xác nhận giờ',
  TU_CHOI: 'Từ chối',
  DA_RUT: 'Đã rút',
  CHO_XAC_NHAN_THEM: 'Chờ duyệt phần giờ vượt',
  HOAN_TAT: 'Hoàn tất',
  DA_HUY: 'Đã hủy',
};

/** Kiểu nhãn trạng thái: ok (xanh), wait (vàng), no (đỏ), old (xám). */
export type StatusTone = 'ok' | 'wait' | 'no' | 'old';

const TONE: Record<string, StatusTone> = {
  CHO_DUYET: 'wait',
  CHO_HUY: 'wait',
  CHO_XAC_NHAN_THEM: 'wait',
  DA_DUYET: 'ok',
  HOAN_TAT: 'ok',
  TU_CHOI: 'no',
  DA_RUT: 'old',
  DA_HUY: 'old',
};

export const statusTone = (status: string): StatusTone => TONE[status] ?? 'old';

export const COMPENSATION_LABEL: Record<Compensation, string> = { TRA_TIEN: 'Trả tiền', NGHI_BU: 'Nghỉ bù' };

export const PART_LABEL: Record<DayPart, string> = { CA_NGAY: 'Cả ngày', SANG: 'Buổi sáng', CHIEU: 'Buổi chiều' };

/** Lý do nghỉ việc riêng có lương (Điều 115) và số ngày tối đa. */
export const PERSONAL_REASONS: { value: PersonalReason; label: string; maxDays: number }[] = [
  { value: 'KET_HON', label: 'Bản thân kết hôn', maxDays: 3 },
  { value: 'CON_KET_HON', label: 'Con kết hôn', maxDays: 1 },
  { value: 'TANG', label: 'Cha, mẹ (cả bên vợ/chồng), vợ/chồng, con mất', maxDays: 3 },
];

export const APPROVER_KIND_LABEL: Record<ApproverKind, string> = {
  TRUONG_NHOM: 'Trưởng nhóm',
  TRUONG_PHONG: 'Trưởng phòng',
  BGD: 'Ban giám đốc',
  QUAN_TRI: 'Quản trị',
};

export const LEDGER_REASON_LABEL: Record<string, string> = {
  CONG_THANG: 'Cộng phép tháng',
  DAU_KY: 'Số dư đầu kỳ',
  DUYET_DON: 'Trừ khi duyệt đơn',
  HUY_DON: 'Hoàn lại khi hủy đơn',
  HET_HAN: 'Hết hạn phép năm trước',
  CHUYEN_NAM: 'Chuyển sang năm sau',
  DIEU_CHINH: 'HR điều chỉnh',
  NGHI_VIEC: 'Chốt khi nghỉ việc',
  HOAN_TAC: 'Hoàn tác nghỉ việc',
  LAM_THEM: 'Cộng giờ làm thêm',
};

export const POOL_LABEL: Record<string, string> = { PHEP_NAM: 'Phép năm (ngày)', NGHI_BU: 'Nghỉ bù (giờ)' };

export const BUCKET_LABEL: Record<string, string> = { NAM_TRUOC: 'Năm trước', NAM_NAY: 'Năm nay' };

/** Số kiểu Việt Nam: 5.5 → "5,5"; 2 → "2"; trống → "—". */
export const num = (n?: number | string | null): string => {
  if (n == null || n === '') return '—';
  const v = Number(n);
  if (Number.isNaN(v)) return '—';
  return String(Math.round(v * 100) / 100).replace('.', ',');
};

/** "08:30:00" → "08:30". */
export const hhmm = (t?: string | null): string => (t ? t.slice(0, 5) : '');
