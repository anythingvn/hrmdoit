import { IDepartment } from 'app/shared/model/department.model';

export type PersonType = 'NHAN_VIEN' | 'THUC_TAP' | 'DICH_VU';
export type InsuranceExemption = 'LUONG_HUU' | 'CONG_TAC_VIEN' | 'HD_DUOI_1_THANG' | 'THU_VIEC_RIENG' | 'KHAC';
export type EmploymentStatus = 'DANG_LAM_VIEC' | 'DA_NGHI';
export type ContractTypeCode = 'HDDT' | 'HDTV' | 'HDLD_XDTH' | 'HDLD_KXDTH' | 'HDDV';

export const PERSON_TYPE_LABEL: Record<PersonType, string> = {
  NHAN_VIEN: 'Nhân viên',
  THUC_TAP: 'Thực tập (học việc)',
  DICH_VU: 'HĐ dịch vụ',
};
/** Lý do không thuộc diện đóng BHXH, BHYT, BHTN bắt buộc. */
export const INSURANCE_EXEMPTION_LABEL: Record<InsuranceExemption, string> = {
  LUONG_HUU: 'Đang hưởng lương hưu',
  CONG_TAC_VIEN: 'Cộng tác viên, HĐ dịch vụ',
  HD_DUOI_1_THANG: 'HĐLĐ dưới 1 tháng',
  THU_VIEC_RIENG: 'Thử việc theo HĐ thử việc riêng',
  KHAC: 'Khác',
};
export const STATUS_LABEL: Record<EmploymentStatus, string> = { DANG_LAM_VIEC: 'Đang làm việc', DA_NGHI: 'Đã nghỉ' };
export const CONTRACT_TYPE_LABEL: Record<ContractTypeCode, string> = {
  HDDT: 'HĐ đào tạo',
  HDTV: 'HĐ thử việc',
  HDLD_XDTH: 'HĐLĐ xác định thời hạn',
  HDLD_KXDTH: 'HĐLĐ không xác định thời hạn',
  HDDV: 'HĐ dịch vụ',
};

export interface IEmployee {
  id?: number;
  code?: string | null;
  personType?: PersonType;
  fullName?: string;
  gender?: 'NAM' | 'NU' | null;
  birthDate?: string | null;
  email?: string | null;
  phone?: string | null;
  residence?: string | null;
  education?: string | null;
  // Trường nhạy cảm: chỉ gửi lên, server không trả về.
  idNumber?: string | null;
  idIssueDate?: string | null;
  idIssuePlace?: string | null;
  taxCode?: string | null;
  bankAccount?: string | null;
  bankName?: string | null;
  socialInsuranceNo?: string | null;
  socialInsuranceStart?: string | null;
  socialInsuranceSalary?: number | null;
  dependants?: number | null;
  position?: string | null;
  status?: EmploymentStatus;
  joinDate?: string | null;
  note?: string | null;
  /** null = thuộc diện đóng BHXH bắt buộc. */
  insuranceExemption?: InsuranceExemption | null;
  insuranceExemptionNote?: string | null;
  /** Mức phép năm gốc: 12, 14 hoặc 16 ngày (chưa cộng thâm niên). */
  annualLeaveDays?: number | null;
  /** Số năm đóng BHXH trước khi vào DO IT (tính hạn mức nghỉ ốm). */
  priorSocialInsuranceYears?: number | null;
  department?: IDepartment | null;
  manager?: { id?: number; fullName?: string; code?: string } | null;
  currentContractType?: ContractTypeCode | null;
  currentSiteName?: string | null;
  /** Tên đăng nhập của tài khoản gắn với hồ sơ (chỉ đọc). */
  userLogin?: string | null;
  /** Ngày làm việc cuối của lịch nghỉ việc đã hẹn, chưa áp dụng (nhãn "Sắp nghỉ"). Chỉ đọc. */
  scheduledTermination?: string | null;
  /** Lịch nghỉ việc đã quá hạn mà chưa áp dụng được. Chỉ đọc. */
  scheduledTerminationOverdue?: boolean | null;
  /** Lý do chưa áp dụng được (nếu job đã chạy). Chỉ đọc. */
  scheduledTerminationError?: string | null;
  /** Có lần cho nghỉ việc đã áp dụng để hoàn tác (chỉ có ở hồ sơ chi tiết). Chỉ đọc. */
  terminationUndoable?: boolean | null;
}

/** Mức phép năm gốc HR chọn được trên hồ sơ. */
export const ANNUAL_LEAVE_DAY_OPTIONS = [12, 14, 16] as const;
