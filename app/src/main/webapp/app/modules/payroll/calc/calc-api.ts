import axios from 'axios';

/** Kiểu dữ liệu và lời gọi API của màn bản tính lương nháp. */

export interface ICalcColumn {
  field: string;
  header: string;
  subHeader?: string | null;
  /** Cột nhập tay (thưởng, thừa/thiếu, giảm trừ khác...). */
  input: boolean;
}

export interface ICalcLine {
  id: number;
  employeeId: number;
  code: string;
  fullName: string;
  position?: string | null;
  amounts: Record<string, number>;
  manual: Record<string, number>;
  overrides: Record<string, number>;
  errors: string[];
  warnings: string[];
  stale?: boolean;
}

export interface ICalcDraft {
  id: number;
  period: string;
  status: 'DANG_LAM' | 'DA_XAC_NHAN' | 'HUY';
  lines: ICalcLine[];
  totals: Record<string, number>;
  canConfirm: boolean;
  warningsAcknowledged: boolean;
  columns: ICalcColumn[];
}

/** Cột tổng: máy chủ luôn tính, không sửa được. */
export const TOTAL_FIELDS = new Set(['TONG_LUONG_GOP', 'TONG_BH_NLD', 'TONG_BH_NSDLD', 'THUC_NHAN', 'TONG_CHI_PHI']);

/** Cột số ngày / số người, không phải tiền. */
export const COUNT_FIELDS = new Set(['NGAY_CONG_CHUAN', 'NGAY_CONG_THUC_TE', 'SO_NGUOI_PHU_THUOC']);

/** Đọc số từ ô nhập ("1,5" hay "1.5"); trống thì null, sai thì NaN. */
export const parseCell = (text: string): number | null => {
  const t = text.trim().replace(',', '.');
  if (t === '') return null;
  return /^-?\d+(\.\d+)?$/.test(t) ? Number(t) : NaN;
};

export const openCalc = (period: string) => axios.post<ICalcDraft>(`api/payroll/periods/${period}/calc`).then(r => r.data);
export const getCalc = (id: number | string) => axios.get<ICalcDraft>(`api/payroll/calc/${id}`).then(r => r.data);
export const updateCalcLine = (
  id: number,
  lineId: number,
  body: { manual?: Record<string, number | null>; overrides?: Record<string, number | null> },
) => axios.put<ICalcLine>(`api/payroll/calc/${id}/lines/${lineId}`, body).then(r => r.data);
export const removeCalcLine = (id: number, lineId: number) => axios.delete(`api/payroll/calc/${id}/lines/${lineId}`);
export const recalcDraft = (id: number) => axios.post<ICalcDraft>(`api/payroll/calc/${id}/recalculate`).then(r => r.data);
export const acknowledgeDraft = (id: number) => axios.post<ICalcDraft>(`api/payroll/calc/${id}/acknowledge`).then(r => r.data);
export const confirmDraft = (id: number, warningsAcknowledged: boolean) =>
  axios.post<{ period: string; version: number }>(`api/payroll/calc/${id}/confirm`, { warningsAcknowledged }).then(r => r.data);
