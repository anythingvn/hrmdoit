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

export const attendanceApi = {
  day: (date: string) => axios.get<IDayBoard>('api/attendance/day', { params: { date } }).then(r => r.data),
  saveDay: (date: string, entries: IDayEntry[]) => axios.put<IDayView[]>('api/attendance/day', { date, entries }).then(r => r.data),
};
