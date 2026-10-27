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

/** Dòng đã bỏ khỏi bản tính (khôi phục được). */
export interface ICalcExcluded {
  lineId: number;
  employeeId: number;
  code: string;
  fullName: string;
}

export interface ICalcDraft {
  id: number;
  period: string;
  status: 'DANG_LAM' | 'DA_XAC_NHAN' | 'DA_HUY';
  lines: ICalcLine[];
  totals: Record<string, number>;
  canConfirm: boolean;
  warningsAcknowledged: boolean;
  columns: ICalcColumn[];
  excluded?: ICalcExcluded[];
  /** Cảnh báo chung của cả bản tính (vd chưa khai báo mức luật cho kỳ); phải xác nhận như cảnh báo dòng. */
  notices?: string[];
}

/** Cột tổng: máy chủ luôn tính, không sửa được. */
export const TOTAL_FIELDS = new Set(['TONG_LUONG_GOP', 'TONG_BH_NLD', 'TONG_BH_NSDLD', 'THUC_NHAN', 'TONG_CHI_PHI']);

/** Cột số ngày / số người, không phải tiền. */
export const COUNT_FIELDS = new Set(['NGAY_CONG_CHUAN', 'NGAY_CONG_THUC_TE', 'SO_NGUOI_PHU_THUOC']);

/**
 * Đọc số từ ô nhập; trống thì null, sai thì NaN. Ô ngày công / số người: "20,5" hay "20.5" là phần lẻ. Ô tiền: số đồng nguyên,
 * dấu chấm là phân cách hàng nghìn ("500.000", "2.000.000", "1500000"), được âm (thừa/thiếu); dấu phẩy, phần lẻ → sai.
 */
export const parseCell = (text: string, field: string): number | null => {
  const t = text.trim();
  if (t === '') return null;
  if (COUNT_FIELDS.has(field)) {
    const d = t.replace(',', '.');
    return /^\d+(\.\d+)?$/.test(d) ? Number(d) : NaN;
  }
  const negative = t.startsWith('-');
  const digits = negative ? t.slice(1).trim() : t;
  if (!/^\d+$/.test(digits) && !/^\d{1,3}(\.\d{3})+$/.test(digits)) return NaN;
  const n = Number(digits.replace(/\./g, ''));
  return negative ? -n : n;
};

export const openCalc = (period: string) => axios.post<ICalcDraft>(`api/payroll/periods/${period}/calc`).then(r => r.data);
export const getCalc = (id: number | string) => axios.get<ICalcDraft>(`api/payroll/calc/${id}`).then(r => r.data);
export const updateCalcLine = (
  id: number,
  lineId: number,
  body: { manual?: Record<string, number | null>; overrides?: Record<string, number | null> },
) => axios.put<ICalcLine>(`api/payroll/calc/${id}/lines/${lineId}`, body).then(r => r.data);
export const removeCalcLine = (id: number, lineId: number) => axios.delete(`api/payroll/calc/${id}/lines/${lineId}`);
export const restoreCalcLine = (id: number, lineId: number) =>
  axios.post<ICalcDraft>(`api/payroll/calc/${id}/lines/${lineId}/restore`).then(r => r.data);
export const cancelDraft = (id: number) => axios.post<ICalcDraft>(`api/payroll/calc/${id}/cancel`).then(r => r.data);
export const recalcDraft = (id: number) => axios.post<ICalcDraft>(`api/payroll/calc/${id}/recalculate`).then(r => r.data);
export const acknowledgeDraft = (id: number) => axios.post<ICalcDraft>(`api/payroll/calc/${id}/acknowledge`).then(r => r.data);
/** Xác nhận: máy chủ tính lại trước và dùng trạng thái "Đã xem và xác nhận" đã ghi qua acknowledgeDraft (không nhận cờ từ đây). */
export const confirmDraft = (id: number) =>
  axios.post<{ period: string; version: number }>(`api/payroll/calc/${id}/confirm`, {}).then(r => r.data);
