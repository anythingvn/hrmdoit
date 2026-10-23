import axios from 'axios';

import { CreditStatus, IHours, LeavePart } from './credit';

/** Kiểu dữ liệu và lời gọi API của phần Chấm công. */

export type AutoKind = 'PHEP_NAM' | 'NGHI_BU' | 'VIEC_RIENG' | 'OM' | 'KHONG_LUONG' | 'CONG_TAC' | 'LE' | 'NGHI' | 'NGOAI_HD';

export const AUTO_LABEL: Record<AutoKind, string> = {
  PHEP_NAM: 'Nghỉ phép năm',
  NGHI_BU: 'Nghỉ bù',
  VIEC_RIENG: 'Nghỉ việc riêng có lương',
  OM: 'Nghỉ ốm',
  KHONG_LUONG: 'Nghỉ không lương',
  CONG_TAC: 'Công tác',
  LE: 'Lễ',
  NGHI: 'Nghỉ',
  NGOAI_HD: 'Ngoài thời gian làm việc',
};

/** Loại nghỉ có lương: nửa ngày nghỉ được tính vào công thực tế. */
export const PAID_LEAVE: AutoKind[] = ['PHEP_NAM', 'NGHI_BU', 'VIEC_RIENG'];

export interface IDayView {
  employeeId: number;
  code: string;
  name: string;
  day: string;
  group: string;
  groupLabel: string;
  autoKind?: AutoKind | null;
  credit?: number | null;
  actualCredit: number;
  status?: CreditStatus | null;
  in?: string | null;
  out?: string | null;
  lateMinutes: number;
  earlyMinutes: number;
  remote: boolean;
  /** Nghỉ nửa ngày (nửa còn lại phải gõ giờ). */
  leavePart?: LeavePart | null;
  /** Người gõ đã đánh dấu Vắng. */
  markedAbsent: boolean;
}

export interface IDayGroup {
  group: string;
  label: string;
  hours: IHours;
  rows: IDayView[];
}

export interface IDayBoard {
  date: string;
  graceMinutes: number;
  halfDayThresholdMinutes: number;
  groups: IDayGroup[];
}

export interface IDayEntry {
  employeeId: number;
  in: string | null;
  out: string | null;
  absent: boolean;
}

/** Ô tự điền cả ngày (nghỉ, lễ, công tác, ngày nghỉ, ngoài hợp đồng): không có ô nhập giờ. */
export const isAutoFilled = (v: IDayView) => !!v.autoKind && !v.leavePart;

/** Người trên bảng tháng. */
export interface IBoardEmployee {
  id: number;
  code: string;
  name: string;
  departmentId?: number | null;
  departmentName?: string | null;
  managerId?: number | null;
  managerName?: string | null;
}

/** Cột tổng của một dòng (trùng các cột ảnh chụp chốt công). */
export interface ITotals {
  standardDays: number;
  actualDays: number;
  workDays: number;
  paidLeaveDays: number;
  unpaidLeaveDays: number;
  sickDays: number;
  holidayDays: number;
  tripDays: number;
  lateCount: number;
  lateMinutes: number;
  earlyCount: number;
  halfDays: number;
  absentDays: number;
  missingDays: number;
}

export interface IBoardRow {
  employee: IBoardEmployee;
  days: IDayView[];
  totals: ITotals;
  /** Tháng đã chốt mà số tính lại khác ảnh chụp. */
  changedAfterClose: boolean;
}

export type MonthStatus = 'DANG_MO' | 'DA_CHOT';

export interface IMonthBoard {
  month: string;
  status: MonthStatus;
  rows: IBoardRow[];
}

/** Ô đỏ của một người khi chốt (lỗi attendanceunresolved). */
export interface IUnresolved {
  employeeId: number;
  code: string;
  name: string;
  dates: string[];
}

/** Ô đỏ: thiếu giờ hoặc chưa nhập. */
export const isRedCell = (v: IDayView) => v.status === 'THIEU_GIO' || v.status === 'CHUA_NHAP';

const SHORT_AUTO: Record<AutoKind, string> = {
  PHEP_NAM: 'P',
  NGHI_BU: 'NB',
  VIEC_RIENG: 'VR',
  OM: 'Ô',
  KHONG_LUONG: 'KL',
  CONG_TAC: 'CT',
  LE: 'L',
  NGHI: '',
  NGOAI_HD: '',
};

/** Chữ ngắn trong ô bảng tháng: giờ vào/ra, nhãn tự điền viết tắt, "V" vắng, "?" chưa nhập. Đầy đủ ở {@link cellTitle}. */
export const cellShort = (v: IDayView): string => {
  if (isAutoFilled(v)) return SHORT_AUTO[v.autoKind as AutoKind];
  const prefix = v.leavePart && v.autoKind ? `${SHORT_AUTO[v.autoKind]}½ ` : '';
  if (v.markedAbsent) return `${prefix}V`;
  if (v.status === 'CHUA_NHAP') return `${prefix}?`;
  if (!v.in && !v.out) return prefix.trim();
  return `${prefix}${v.in ?? '?'}–${v.out ?? '?'}`;
};

/** Chữ đầy đủ của ô (title, Excel): nhãn tự điền, giờ, Vắng, Thiếu giờ, Chưa nhập. */
export const cellTitle = (v: IDayView): string => {
  if (isAutoFilled(v)) return AUTO_LABEL[v.autoKind as AutoKind];
  const leave = v.leavePart && v.autoKind ? `${AUTO_LABEL[v.autoKind]} buổi ${v.leavePart === 'SANG' ? 'sáng' : 'chiều'}` : '';
  let base = '';
  if (v.markedAbsent) base = 'Vắng';
  else if (v.status === 'CHUA_NHAP') base = 'Chưa nhập';
  else if (v.in || v.out) base = `${v.in ?? '?'}–${v.out ?? '?'}${v.status === 'THIEU_GIO' ? ' (thiếu giờ)' : ''}`;
  if (v.lateMinutes > 0) base += ` · muộn ${v.lateMinutes}'`;
  if (v.earlyMinutes > 0) base += ` · về sớm ${v.earlyMinutes}'`;
  return [leave, base].filter(Boolean).join(' · ');
};

/** "2026-11" → "11/2026". */
export const monthVn = (month: string) => `${month.slice(5, 7)}/${month.slice(0, 4)}`;

/** Cộng/trừ tháng của "yyyy-MM". */
export const shiftMonth = (month: string, delta: number) => {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

/** Tháng trước tháng hiện tại: tháng thường được xem và chốt. */
export const previousMonth = (now = new Date()) => shiftMonth(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`, -1);

export const attendanceApi = {
  day: (date: string) => axios.get<IDayBoard>('api/attendance/day', { params: { date } }).then(r => r.data),
  saveDay: (date: string, entries: IDayEntry[]) => axios.put<IDayView[]>('api/attendance/day', { date, entries }).then(r => r.data),
  month: (month: string) => axios.get<IMonthBoard>('api/attendance/month', { params: { month } }).then(r => r.data),
  my: (month: string) => axios.get<IMonthBoard>('api/attendance/my', { params: { month } }).then(r => r.data),
  close: (month: string, force: boolean) =>
    axios.post(`api/attendance/month/${month}/close`, null, { params: { force } }).then(r => r.data),
  reopen: (month: string, reason: string) => axios.post(`api/attendance/month/${month}/reopen`, { reason }).then(r => r.data),
};
