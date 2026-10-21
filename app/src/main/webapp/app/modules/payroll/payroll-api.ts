import { formatMoney } from 'app/entities/employee/money';

/** Kiểu dữ liệu và tiện ích dùng chung cho các màn Bảng lương. */

export type Severity = 'ERROR' | 'WARNING';

export interface IIssue {
  severity: Severity;
  excelRow: number;
  employeeCode?: string | null;
  field?: string | null;
  fieldLabel?: string | null;
  message: string;
}

export interface IRow {
  excelRow: number;
  fileCode?: string | null;
  fileName: string;
  position?: string | null;
  employeeId?: number | null;
  employeeCode?: string | null;
  employeeName?: string | null;
  mappedManually: boolean;
}

export interface IColumn {
  field: string;
  label: string;
  column?: string | null;
  required: boolean;
}

export interface IImportCheck {
  id: number;
  period: string;
  fileName: string;
  status: 'DANG_LAM' | 'DA_XAC_NHAN' | 'HUY';
  warningsAcknowledged: boolean;
  columns: IColumn[];
  missingRequired: string[];
  rows: IRow[];
  issues: IIssue[];
  errorCount: number;
  warningCount: number;
  canConfirm: boolean;
}

export interface ICandidate {
  id: number;
  code: string;
  fullName: string;
  position?: string | null;
  personType: 'NHAN_VIEN' | 'DICH_VU';
  status?: string;
  exactName: boolean;
}

export type PeriodStatus = 'NHAP' | 'DA_DUYET';

export const PERIOD_STATUS_LABEL: Record<PeriodStatus, string> = { NHAP: 'Nháp', DA_DUYET: 'Đã duyệt' };

/** "2026-06" thành "06/2026". */
export const periodText = (p?: string | null) => (p ? `${p.slice(5, 7)}/${p.slice(0, 4)}` : '');

/** Tiền dạng 1.234.567; trống thì "—". */
export const money = (n?: number | null) => (n == null ? '—' : formatMoney(n));
