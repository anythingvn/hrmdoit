import axios from 'axios';

import { errorText } from 'app/entities/employee/detail/detail-api';

import {
  ApproverKind,
  CalendarType,
  Compensation,
  DayPart,
  LeaveStatus,
  LeaveType,
  OvertimeStatus,
  PersonalReason,
  num,
} from './leave-labels';

/** Kiểu dữ liệu và lời gọi API của phần Nghỉ phép & đơn từ (Task 2–5). */

export interface ILeaveBalance {
  employeeId: number;
  code?: string;
  fullName?: string;
  departmentName?: string | null;
  annual: { previousYear: number; currentYear: number; total: number; requestable: number; previousYearExpiry?: string | null };
  compensatory: { previousYear: number; currentYear: number; total: number };
  sick?: { usedDays: number; limitDays: number } | null;
  overtime?: { monthHours: number; monthCap: number } | null;
}

export interface ILeaveRequest {
  id: number;
  employeeId: number;
  employeeCode?: string;
  employeeName?: string;
  type: LeaveType;
  personalReason?: PersonalReason | null;
  fromDate: string;
  toDate: string;
  firstPart?: DayPart | null;
  lastPart?: DayPart | null;
  days?: number | null;
  hours?: number | null;
  reasonText?: string | null;
  status: LeaveStatus;
  approverKind?: ApproverKind;
  approverName?: string | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionNote?: string | null;
  onBehalf?: boolean;
  sickDocId?: number | null;
  systemNote?: string | null;
  createdAt?: string;
  sickOverLimit?: boolean | null;
  /** Chỉ có trong hộp duyệt: số dư hiện tại của người xin. */
  balance?: { annualTotal: number; annualRequestable: number; compensatoryHours: number } | null;
}

export interface ILeaveRequestCreate {
  type: LeaveType;
  personalReason?: PersonalReason | null;
  fromDate: string;
  toDate: string;
  firstPart?: DayPart;
  lastPart?: DayPart;
  reasonText?: string;
}

export interface IOvertime {
  id: number;
  employeeId: number;
  employeeCode?: string;
  employeeName?: string;
  workDate: string;
  startTime: string;
  endTime: string;
  plannedHours: number;
  actualHours?: number | null;
  rate: number;
  night: boolean;
  content?: string | null;
  status: OvertimeStatus;
  compensation?: Compensation | null;
  approverKind?: ApproverKind;
  approverName?: string | null;
  decidedBy?: string | null;
  decisionNote?: string | null;
  autoConfirmed?: boolean;
  createdAt?: string;
}

export interface ILedgerEntry {
  id: number;
  pool: string;
  bucket: string;
  year: number;
  amount: number;
  effectiveDate: string;
  reason: string;
  refType?: string | null;
  refId?: number | null;
  note?: string | null;
  createdBy?: string | null;
}

export interface ICalendarDay {
  date: string;
  weekend: boolean;
  holidayName?: string | null;
}

export interface ICalendarItem {
  employeeId: number;
  employeeName: string;
  department?: string | null;
  type: CalendarType;
  from: string;
  to: string;
  firstPart?: DayPart | null;
  lastPart?: DayPart | null;
  pending: boolean;
}

export interface ICalendar {
  days: ICalendarDay[];
  items: ICalendarItem[];
}

export interface IBulkResult {
  id: number;
  ok: boolean;
  errorKey?: string | null;
  message?: string | null;
}

export interface IDepartmentOption {
  id: number;
  name: string;
}

export interface ILeaveSettings {
  advanceDays: number;
  directorThresholdDays: number;
  otMonthCap: number;
  otYearCap: number;
  carryExpiryMonth: number;
  carryExpiryDay: number;
  pastRequestDays: number;
  otConfirmDay: number;
}

export interface ILeaveAdjustment {
  employeeId: number;
  pool: 'PHEP_NAM' | 'NGHI_BU';
  bucket: 'NAM_TRUOC' | 'NAM_NAY';
  amount: number;
  note: string;
}

export interface IOpeningCheck {
  rowCount: number;
  rows: {
    excelRow: number;
    code: string;
    fullName?: string | null;
    annualRemaining?: number | null;
    annualPreviousYear?: number | null;
    compensatoryHours?: number | null;
  }[];
  issues: { excelRow: number; code?: string | null; message: string }[];
  errorCount: number;
  canSave: boolean;
}

/** Tab "Nghỉ phép" trên hồ sơ nhân viên. */
export interface IEmployeeLeave {
  balance: ILeaveBalance;
  requests: ILeaveRequest[];
  ledger: ILedgerEntry[];
}

export interface IAccountingRow {
  employeeId: number;
  code?: string | null;
  fullName?: string | null;
  departmentName?: string | null;
  status?: string | null;
  annualDays: number;
  personalDays: number;
  sickDays: number;
  unpaidDays: number;
  compensatoryDays: number;
  ot150Hours: number;
  ot200Hours: number;
  ot300Hours: number;
  nightCount: number;
  /** Dương: trả tiền phép chưa nghỉ; âm: trừ lương phép đã ứng. */
  settlementDays?: number | null;
  settlementCompensatoryHours?: number | null;
  /** Dòng đảo ngược do hoàn tác nghỉ việc sau khi kỳ lương tháng chốt đã duyệt. */
  settlementReversal?: boolean;
  /** Hồ sơ đã lưu trữ: hiện nhãn "Lưu trữ". */
  archived?: boolean;
}

export interface IAccounting {
  month: string;
  rows: IAccountingRow[];
}

const R = 'api/leave/requests';
const OT = 'api/leave/overtime';

export const leaveApi = {
  myBalance: () => axios.get<ILeaveBalance>('api/leave/balances/me').then(r => r.data),
  ledger: (employeeId: number) => axios.get<ILedgerEntry[]>(`api/leave/ledger/${employeeId}`).then(r => r.data ?? []),
  calendar: (month: string, params: { departmentId?: number; type?: string } = {}) =>
    axios.get<ICalendar>('api/leave/calendar', { params: { month, ...params } }).then(r => r.data),

  submit: (dto: ILeaveRequestCreate) => axios.post<ILeaveRequest>(R, dto).then(r => r.data),
  mine: () => axios.get<ILeaveRequest[]>(`${R}/mine`).then(r => r.data ?? []),
  inbox: () => axios.get<ILeaveRequest[]>(`${R}/inbox`).then(r => r.data ?? []),
  /** withdraw, approve, reject, cancel-request, cancel-approve, cancel-reject. */
  act: (id: number, action: string, note?: string) =>
    axios.post<ILeaveRequest>(`${R}/${id}/${action}`, note ? { note } : {}).then(r => r.data),
  bulkApprove: (ids: number[], note?: string) => axios.post<IBulkResult[]>(`${R}/bulk-approve`, { ids, note }).then(r => r.data ?? []),
  /** Từ chối nhiều đơn với cùng lý do (bắt buộc). */
  bulkReject: (ids: number[], note: string) => axios.post<IBulkResult[]>(`${R}/bulk-reject`, { ids, note }).then(r => r.data ?? []),
  uploadSickDocument(id: number, file: File) {
    const form = new FormData();
    form.append('file', file);
    return axios.post<ILeaveRequest>(`${R}/${id}/sick-document`, form).then(r => r.data);
  },
  sickDocumentUrl: (id: number) => `${R}/${id}/sick-document`,

  submitOvertime: (dto: { workDate: string; startTime: string; endTime: string; content?: string }) =>
    axios.post<IOvertime>(OT, dto).then(r => r.data),
  myOvertime: () => axios.get<IOvertime[]>(`${OT}/mine`).then(r => r.data ?? []),
  overtimeInbox: () => axios.get<IOvertime[]>(`${OT}/inbox`).then(r => r.data ?? []),
  /** approve, reject, withdraw, approve-extra, reject-extra, compensation. */
  actOvertime: (id: number, action: string, body: { compensation?: Compensation; note?: string } = {}) =>
    axios.post<IOvertime>(`${OT}/${id}/${action}`, body).then(r => r.data),
  confirmOvertime: (id: number, actualHours?: number) => axios.post<IOvertime>(`${OT}/${id}/confirm`, { actualHours }).then(r => r.data),

  calendarDepartments: () => axios.get<IDepartmentOption[]>('api/leave/calendar/departments').then(r => r.data ?? []),
  employeeLeave: (employeeId: number) => axios.get<IEmployeeLeave>(`api/leave/employees/${employeeId}`).then(r => r.data),

  companyBalances: (departmentId?: number) =>
    axios.get<ILeaveBalance[]>('api/leave/balances', departmentId ? { params: { departmentId } } : undefined).then(r => r.data ?? []),
  adjust: (dto: ILeaveAdjustment) => axios.post<ILedgerEntry[]>('api/leave/adjustments', dto).then(r => r.data),
  settings: () => axios.get<ILeaveSettings>('api/leave/settings').then(r => r.data),
  saveSettings: (dto: ILeaveSettings) => axios.put<ILeaveSettings>('api/leave/settings', dto).then(r => r.data),
  openingTemplateUrl: 'api/leave/opening/template',
  openingCheck(file: File) {
    const form = new FormData();
    form.append('file', file);
    return axios.post<IOpeningCheck>('api/leave/opening/check', form).then(r => r.data);
  },
  openingSave(file: File) {
    const form = new FormData();
    form.append('file', file);
    return axios.post<{ savedCount: number }>('api/leave/opening', form).then(r => r.data);
  },

  accounting: (month: string) => axios.get<IAccounting>('api/leave/accounting', { params: { month } }).then(r => r.data),
  accountingExportUrl: 'api/leave/accounting/export.xlsx',
};

/** Lỗi mà câu của server có số liệu cụ thể (ngày, giờ, hạn mức): ưu tiên câu của server. */
const DETAIL_FIRST = new Set([
  'error.overlap',
  'error.overtimelimit',
  'error.overtimeoverlap',
  'error.overtimeperiodlocked',
  'error.leaveperiodlocked',
  'error.personalleaveexceeded',
  'error.leavepasttoofar',
  'error.leavepartinvalid',
  'error.pendingtermination',
]);

/**
 * Câu báo lỗi cho phần nghỉ phép: số dư không đủ thì kèm số còn lại server trả về; lỗi có số liệu thì lấy câu của server; còn lại theo
 * {@link errorText}.
 *
 * @param unit "ngày" (phép năm) hoặc "giờ" (nghỉ bù)
 */
export const leaveErrorText = (err: any, unit = 'ngày', fallback?: string): string => {
  const data = err?.response?.data;
  if (data?.message === 'error.insufficientbalance' && data?.remaining != null) {
    return `Không đủ số dư: còn ${num(data.remaining)} ${unit}.`;
  }
  if (DETAIL_FIRST.has(data?.message) && data?.detail) {
    return data.detail;
  }
  return errorText(err, fallback);
};

/** "2026-10-01" → Date (UTC, không lệch múi giờ). */
const utc = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};

export const isoOf = (d: Date) => d.toISOString().slice(0, 10);

/** Các ngày từ {@code from} đến {@code to} (gồm cả hai đầu). */
export const daysBetween = (from: string, to: string): string[] => {
  const out: string[] = [];
  const end = utc(to).getTime();
  for (let t = utc(from).getTime(); t <= end && out.length < 400; t += 86400000) {
    out.push(isoOf(new Date(t)));
  }
  return out;
};

/** Thứ Bảy / Chủ nhật. */
export const isWeekendIso = (iso: string) => {
  const w = utc(iso).getUTCDay();
  return w === 0 || w === 6;
};

/**
 * Số ngày ước tính chỉ để hiển thị trước khi gửi: đếm Thứ Hai–Thứ Sáu, trừ nửa ngày ở đầu/cuối. Chưa trừ ngày lễ và lịch làm việc
 * riêng của công ty; số chính thức do server tính khi gửi.
 */
export const previewDays = (from: string, to: string, firstPart: DayPart = 'CA_NGAY', lastPart: DayPart = 'CA_NGAY'): number => {
  if (!from || !to || from > to) return 0;
  const all = daysBetween(from, to);
  let n = all.filter(d => !isWeekendIso(d)).length;
  if (n === 0) return 0;
  if (from === to) return firstPart === 'CA_NGAY' ? 1 : 0.5;
  if (firstPart !== 'CA_NGAY' && !isWeekendIso(from)) n -= 0.5;
  if (lastPart !== 'CA_NGAY' && !isWeekendIso(to)) n -= 0.5;
  return n;
};

/** Tháng hiện tại "yyyy-MM" theo giờ Việt Nam. */
export const currentMonth = (now = new Date()): string => {
  const p = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).formatToParts(now);
  const get = (t: string) => p.find(x => x.type === t)?.value;
  return `${get('year')}-${get('month')}`;
};

/** Hôm nay "yyyy-MM-dd" theo giờ Việt Nam. */
export const todayIso = (now = new Date()): string =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);

/** Cộng {@code delta} tháng vào "yyyy-MM". */
export const shiftMonth = (month: string, delta: number): string => {
  const [y, m] = month.split('-').map(Number);
  const total = y * 12 + (m - 1) + delta;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
};
