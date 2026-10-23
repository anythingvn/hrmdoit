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
  /** Giờ vào / ra gõ tay (nguồn GO_TAY): giá trị cho ô nhập của màn gõ. */
  manualIn?: string | null;
  manualOut?: string | null;
  /** Mọi lần chấm của ngày (cả chờ duyệt, bị từ chối), theo giờ. */
  punches?: IPunchView[];
  /** Số lần tự chấm đang chờ duyệt. */
  pendingCount?: number;
}

/** Một lần chấm; tọa độ, sai số, IP chỉ có khi người xem là chính người đó, người duyệt ngày đó, HR hoặc Quản trị. */
export interface IPunchView {
  id: number;
  kind: 'VAO' | 'RA';
  time: string;
  source: 'GO_TAY' | 'APP' | 'GIAI_TRINH';
  status?: 'CHAP_NHAN' | 'CHO_DUYET' | 'TU_CHOI' | 'DA_DUYET' | null;
  method?: 'IP' | 'GPS' | 'TU_XA' | null;
  siteName?: string | null;
  distanceM?: number | null;
  reviewReason?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  accuracyM?: number | null;
  clientIp?: string | null;
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
  /** Dòng của chính người xem (không ai gõ giờ cho mình). */
  self?: boolean;
  /** Các ngày (yyyy-MM-dd) người xem gõ được cho người này: đã qua, trong phạm vi quyền, tháng đang mở. */
  editableDays?: string[];
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

/** Hôm nay theo giờ máy, dạng yyyy-MM-dd. */
export const todayIso = (now = new Date()) =>
  `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

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

export type PunchStatus = 'CHAP_NHAN' | 'CHO_DUYET' | 'TU_CHOI' | 'DA_DUYET';

/** Kết quả một lần tự chấm (cũng là một dòng của màn hôm nay). */
export interface IPunchResult {
  id: number;
  kind: 'VAO' | 'RA';
  time: string;
  status: PunchStatus;
  method?: 'IP' | 'GPS' | 'TU_XA' | null;
  siteName?: string | null;
  distanceM?: number | null;
  message?: string | null;
}

/** Màn chấm công hôm nay của chính mình. */
export interface ITodayView {
  day: string;
  siteName?: string | null;
  siteKind?: string | null;
  start?: string | null;
  end?: string | null;
  consentRequired: boolean;
  noticeText?: string | null;
  punches: IPunchResult[];
}

export interface IPunchRequest {
  kind: 'VAO' | 'RA';
  latitude?: number;
  longitude?: number;
  accuracy?: number;
}

/** Nhãn và màu theo trạng thái lần chấm (màn chấm công của tôi, bảng của người duyệt). */
export const PUNCH_STATUS: Record<PunchStatus, { label: string; alert: string; bg: string }> = {
  CHAP_NHAN: { label: 'Được tính', alert: 'success', bg: 'success' },
  DA_DUYET: { label: 'Được tính (đã duyệt)', alert: 'success', bg: 'success' },
  CHO_DUYET: { label: 'Chờ duyệt', alert: 'warning', bg: 'warning' },
  TU_CHOI: { label: 'Từ chối', alert: 'danger', bg: 'danger' },
};

/** Nguồn một lần chấm, vd "Tự chấm · IP", "Tự chấm · GPS ±140 m · chờ duyệt", "Giải trình"; lần gõ tay không ghi nguồn. */
export const punchSourceText = (p: IPunchView): string => {
  if (p.source === 'GIAI_TRINH') return 'Giải trình';
  if (p.source !== 'APP') return '';
  const parts = ['Tự chấm'];
  if (p.method === 'IP') parts.push('IP');
  else if (p.method === 'TU_XA') parts.push('từ xa');
  else if (p.method === 'GPS') parts.push(p.accuracyM === null || p.accuracyM === undefined ? 'GPS' : `GPS ±${Math.round(p.accuracyM)} m`);
  if (p.status === 'CHO_DUYET') parts.push('chờ duyệt');
  else if (p.status === 'TU_CHOI') parts.push('bị từ chối');
  else if (p.status === 'DA_DUYET') parts.push('đã duyệt');
  return parts.join(' · ');
};

/** Mục chờ duyệt: lần tự chấm (PUNCH) hoặc giải trình (EXPLANATION). */
export interface IPendingItem {
  type: 'PUNCH' | 'EXPLANATION';
  id: number;
  employeeId: number;
  employeeCode?: string | null;
  employeeName: string;
  day: string;
  summary: string;
  detail?: string | null;
}

export type ExplanationStatus = 'DANG_CHO' | 'DA_DUYET' | 'TU_CHOI' | 'DA_RUT';

export interface IExplanation {
  id: number;
  workDay: string;
  proposedIn?: string | null;
  proposedOut?: string | null;
  reason: string;
  status: ExplanationStatus;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionNote?: string | null;
}

export interface IExplanationRequest {
  workDay: string;
  proposedIn: string | null;
  proposedOut: string | null;
  reason: string;
}

export const attendanceApi = {
  day: (date: string) => axios.get<IDayBoard>('api/attendance/day', { params: { date } }).then(r => r.data),
  saveDay: (date: string, entries: IDayEntry[]) => axios.put<IDayView[]>('api/attendance/day', { date, entries }).then(r => r.data),
  month: (month: string) => axios.get<IMonthBoard>('api/attendance/month', { params: { month } }).then(r => r.data),
  my: (month: string) => axios.get<IMonthBoard>('api/attendance/my', { params: { month } }).then(r => r.data),
  close: (month: string, force: boolean) =>
    axios.post(`api/attendance/month/${month}/close`, null, { params: { force } }).then(r => r.data),
  reopen: (month: string, reason: string) => axios.post(`api/attendance/month/${month}/reopen`, { reason }).then(r => r.data),
  today: () => axios.get<ITodayView>('api/attendance/punches/today').then(r => r.data),
  punch: (req: IPunchRequest) => axios.post<IPunchResult>('api/attendance/punches', req).then(r => r.data),
  consent: () => axios.post('api/attendance/consent').then(r => r.data),
  submitExplanation: (req: IExplanationRequest) => axios.post<IExplanation>('api/attendance/explanations', req).then(r => r.data),
  withdrawExplanation: (id: number) => axios.post<IExplanation>(`api/attendance/explanations/${id}/withdraw`).then(r => r.data),
  approvePunch: (id: number) => axios.post<IDayView>(`api/attendance/punches/${id}/approve`).then(r => r.data),
  rejectPunch: (id: number, note: string) => axios.post<IDayView>(`api/attendance/punches/${id}/reject`, { note }).then(r => r.data),
  approveExplanation: (id: number) => axios.post<IExplanation>(`api/attendance/explanations/${id}/approve`).then(r => r.data),
  rejectExplanation: (id: number, note: string) =>
    axios.post<IExplanation>(`api/attendance/explanations/${id}/reject`, { note }).then(r => r.data),
  pending: () => axios.get<IPendingItem[]>('api/attendance/pending').then(r => r.data ?? []),
  myExplanations: (month: string) => axios.get<IExplanation[]>('api/attendance/explanations/mine', { params: { month } }).then(r => r.data),
};
