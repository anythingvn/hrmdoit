import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { ContractTypeCode } from 'app/shared/model/employee.model';

/** Kiểu dữ liệu và tiện ích dùng chung cho trang chi tiết nhân viên. */

export type SalaryBasis = 'NET' | 'GROSS';
export type ContractStatus = 'NHAP' | 'DA_KY' | 'DA_KET_THUC' | 'DA_THANH_LY';
export type WorkLocationType = 'VAN_PHONG' | 'KHACH_HANG' | 'TU_XA' | 'CONG_TAC';
export type TerminationType = 'TU_NGHI' | 'HET_HAN_HD' | 'KHONG_DAT_THU_VIEC' | 'CONG_TY_CHAM_DUT' | 'KHAC';

export interface ICurrentSalary {
  amount: number;
  basis: SalaryBasis;
  grossAmount?: number | null;
  effectiveDate?: string | null;
  source?: string | null;
}

export interface IContract {
  id?: number;
  contractNo?: string | null;
  type?: ContractTypeCode;
  termMonths?: number | null;
  startDate?: string;
  endDate?: string | null;
  position?: string | null;
  salary?: number | null;
  salaryBasis?: SalaryBasis | null;
  grossAmount?: number | null;
  raisePolicy?: string | null;
  status?: ContractStatus;
  employee?: { id: number };
}

export interface ISalaryChange {
  id?: number;
  effectiveDate?: string;
  amount?: number;
  basis?: SalaryBasis;
  grossAmount?: number | null;
  reviewCycleMonths?: number | null;
  note?: string | null;
  employee?: { id: number };
}

export interface IAllowance {
  id?: number;
  name?: string;
  amountPerMonth?: number;
  fromDate?: string;
  toDate?: string | null;
  employee?: { id: number };
}

export interface ISegment {
  /** id giai đoạn (WorkAssignment); null/không có khi là khoảng trống. */
  id?: number | null;
  from: string;
  to?: string | null;
  type?: WorkLocationType;
  unit?: string | null;
  label?: string | null;
}

export interface IDocument {
  id: number;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
  uploadedBy?: string | null;
  uploadedAt?: string;
}

export interface IHistoryEntry {
  at: string;
  login?: string | null;
  entity: string;
  action: string;
  changes: Array<{ field: string; before?: string | null; after?: string | null }>;
}

export const CONTRACT_STATUS_LABEL: Record<ContractStatus, string> = {
  NHAP: 'Nháp',
  DA_KY: 'Đã ký',
  DA_KET_THUC: 'Đã kết thúc',
  DA_THANH_LY: 'Đã thanh lý',
};

export const LOCATION_TYPE_LABEL: Record<WorkLocationType, string> = {
  VAN_PHONG: 'Văn phòng',
  KHACH_HANG: 'Tại khách hàng',
  TU_XA: 'Làm từ xa',
  CONG_TAC: 'Công tác / biệt phái',
};

export const TERMINATION_TYPE_LABEL: Record<TerminationType, string> = {
  TU_NGHI: 'Tự nghỉ',
  HET_HAN_HD: 'Hết hạn hợp đồng',
  KHONG_DAT_THU_VIEC: 'Không đạt thử việc',
  CONG_TY_CHAM_DUT: 'Công ty chấm dứt',
  KHAC: 'Khác',
};

const ERROR_TEXT: Record<string, string> = {
  'error.badterm': 'Thời hạn hợp đồng không nằm trong danh sách cho phép',
  'error.indefinitehasend': 'Hợp đồng không xác định thời hạn không có ngày kết thúc',
  'error.endbeforestart': 'Ngày kết thúc trước ngày bắt đầu',
  'error.alreadyterminated': 'Nhân viên đã nghỉ việc',
  'error.terminatebeforeassignment':
    'Ngày làm việc cuối trước một giai đoạn làm việc đã bắt đầu. Kiểm tra lại ngày, hoặc xóa / sửa giai đoạn đó trong "Lịch sử công tác" trước khi cho nghỉ việc.',
  'error.assignmentnotfound': 'Không tìm thấy giai đoạn làm việc này của nhân viên',
  'error.terminatebeforecontract': 'Ngày làm việc cuối trước ngày bắt đầu của hợp đồng đã ký',
  'error.lastworkingdayrequired': 'Cần ngày làm việc cuối cùng',
  'error.terminationtyperequired': 'Cần chọn hình thức nghỉ việc',
  'error.filetypenotallowed': 'Chỉ nhận file PDF, JPEG hoặc PNG',
  'error.filetypemismatch': 'Loại file không khớp với đuôi file',
  'error.filecontentmismatch': 'Nội dung file không đúng định dạng',
  'error.filenamemissing': 'Thiếu tên file',
  'error.idnotfound': 'Không tìm thấy dữ liệu',
};

/** Câu báo lỗi tiếng Việt từ lỗi axios: theo mã lỗi đã biết, rồi `detail` của server, rồi câu chung. */
export const errorText = (err: any, fallback = 'Không lưu được, vui lòng thử lại'): string => {
  const res = err?.response;
  if (res?.status === 413) return 'File vượt quá dung lượng cho phép (20 MB)';
  if (res?.status === 403) return 'Bạn không có quyền thực hiện thao tác này';
  const key: string | undefined = res?.data?.message;
  if (key && key !== 'error.assignmentconflict' && ERROR_TEXT[key]) return ERROR_TEXT[key];
  return res?.data?.detail ?? fallback;
};

/** Ngày kết thúc HĐ có thời hạn = ngày bắt đầu + số tháng − 1 ngày (cuối tháng thì lùi về ngày cuối như Java plusMonths). */
export const contractEnd = (startIso: string, months: number): string => {
  const [y, m, d] = startIso.split('-').map(Number);
  const totalMonth = m - 1 + months;
  const ty = y + Math.floor(totalMonth / 12);
  const tm = totalMonth % 12;
  const lastDay = new Date(Date.UTC(ty, tm + 1, 0)).getUTCDate();
  const end = new Date(Date.UTC(ty, tm, Math.min(d, lastDay)) - 86400000);
  return end.toISOString().slice(0, 10);
};

/** "Trần Minh Quân" thành "MQ". */
export const initials = (name?: string): string => {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(-2)
    .map(p => p[0].toUpperCase())
    .join('');
};

/** Quyền theo vai trò của người đang đăng nhập. */
export const useRoles = () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  return {
    authorities,
    canWrite: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]),
    canSeeSensitive: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN]),
    sensitiveFull: hasAnyAuthority(authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]),
  };
};
