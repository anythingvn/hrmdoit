import { IDepartment } from 'app/shared/model/department.model';

export type PersonType = 'NHAN_VIEN' | 'DICH_VU';
export type EmploymentStatus = 'DANG_LAM_VIEC' | 'DA_NGHI';
export type ContractTypeCode = 'HDDT' | 'HDTV' | 'HDLD_XDTH' | 'HDLD_KXDTH' | 'HDDV';

export const PERSON_TYPE_LABEL: Record<PersonType, string> = { NHAN_VIEN: 'Nhân viên', DICH_VU: 'HĐ dịch vụ' };
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
  department?: IDepartment | null;
  manager?: { id?: number; fullName?: string; code?: string } | null;
  currentContractType?: ContractTypeCode | null;
  currentSiteName?: string | null;
}
