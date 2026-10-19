import { CONTRACT_TYPE_LABEL, PERSON_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';

import { formatMoney } from '../money';
import { isoToVn } from '../vn-date';
import { CONTRACT_STATUS_LABEL, LOCATION_TYPE_LABEL, TERMINATION_TYPE_LABEL } from './detail-api';

/** Đổi tên cột và giá trị thô của nhật ký (Envers) sang tiếng Việt, ngày dd/MM/yyyy, tiền 1.234.567. */

const FIELD_LABEL: Record<string, string> = {
  code: 'Mã nhân viên',
  personType: 'Loại hồ sơ',
  fullName: 'Họ và tên',
  gender: 'Giới tính',
  birthDate: 'Ngày sinh',
  email: 'Email',
  phone: 'Số điện thoại',
  residence: 'Địa chỉ',
  education: 'Trình độ',
  idNumber: 'Số CCCD',
  idIssueDate: 'Ngày cấp CCCD',
  idIssuePlace: 'Nơi cấp CCCD',
  taxCode: 'Mã số thuế',
  bankAccount: 'Số tài khoản',
  bankName: 'Ngân hàng',
  socialInsuranceNo: 'Số sổ BHXH',
  socialInsuranceStart: 'Bắt đầu đóng BHXH',
  socialInsuranceSalary: 'Mức lương đóng BHXH',
  dependants: 'Số người phụ thuộc',
  position: 'Chức danh',
  status: 'Trạng thái',
  joinDate: 'Ngày vào',
  lastWorkingDay: 'Ngày làm việc cuối',
  terminationType: 'Hình thức nghỉ việc',
  terminationReason: 'Lý do nghỉ việc',
  terminationDate: 'Ngày nghỉ việc',
  note: 'Ghi chú',
  department: 'Phòng ban',
  manager: 'Quản lý trực tiếp',
  user: 'Tài khoản',
  contractNo: 'Số hợp đồng',
  type: 'Loại',
  termMonths: 'Thời hạn (tháng)',
  startDate: 'Ngày bắt đầu',
  endDate: 'Ngày kết thúc',
  salary: 'Mức lương',
  salaryBasis: 'Lương tính theo',
  grossAmount: 'Lương Gross',
  raisePolicy: 'Chính sách tăng lương',
  signedFileId: 'File hợp đồng đã ký',
  effectiveDate: 'Ngày hiệu lực',
  amount: 'Mức lương',
  basis: 'Lương tính theo',
  reviewCycleMonths: 'Chu kỳ xét lương (tháng)',
  nextReviewOverride: 'Ngày xét lương kế tiếp',
  name: 'Tên phụ cấp',
  amountPerMonth: 'Số tiền mỗi tháng',
  fromDate: 'Từ ngày',
  toDate: 'Đến ngày',
  unit: 'Đơn vị',
  address: 'Địa chỉ',
  decisionNo: 'Số quyết định',
  site: 'Nơi làm việc',
  fileName: 'Tên file',
  contentType: 'Loại file',
  sizeBytes: 'Dung lượng',
  uploadedBy: 'Người tải lên',
  uploadedAt: 'Thời điểm tải lên',
};

/** Tên theo thực thể khi một cột trùng tên mang nghĩa khác. */
const ENTITY_FIELD_LABEL: Record<string, string> = {
  'EmploymentContract.type': 'Loại hợp đồng',
  'WorkAssignment.type': 'Loại nơi làm việc',
  'EmploymentContract.status': 'Trạng thái hợp đồng',
  'Employee.status': 'Tình trạng',
};

/** Cột kỹ thuật không hiển thị. */
const HIDDEN = new Set(['storagePath']);

const DATE_FIELDS = new Set([
  'birthDate',
  'idIssueDate',
  'socialInsuranceStart',
  'joinDate',
  'lastWorkingDay',
  'terminationDate',
  'startDate',
  'endDate',
  'effectiveDate',
  'nextReviewOverride',
  'fromDate',
  'toDate',
]);

const MONEY_FIELDS = new Set(['socialInsuranceSalary', 'salary', 'grossAmount', 'amount', 'amountPerMonth']);

const BASIS_LABEL: Record<string, string> = { NET: 'Net', GROSS: 'Gross' };
const GENDER_LABEL: Record<string, string> = { NAM: 'Nam', NU: 'Nữ' };

const ENUMS: Record<string, Record<string, string>> = {
  'Employee.status': STATUS_LABEL,
  'EmploymentContract.status': CONTRACT_STATUS_LABEL,
  'EmploymentContract.type': CONTRACT_TYPE_LABEL,
  'WorkAssignment.type': LOCATION_TYPE_LABEL,
  personType: PERSON_TYPE_LABEL,
  terminationType: TERMINATION_TYPE_LABEL,
  salaryBasis: BASIS_LABEL,
  basis: BASIS_LABEL,
  gender: GENDER_LABEL,
};

/** Quan hệ: server chỉ có id, hiện "<nhãn> #id". */
const RELATIONS: Record<string, string> = {
  department: 'Phòng ban',
  manager: 'Nhân viên',
  user: 'Tài khoản',
  site: 'Nơi làm việc',
  signedFileId: 'Hồ sơ',
};

export const isHiddenField = (field: string) => HIDDEN.has(field);

export const fieldLabel = (entity: string, field: string): string =>
  ENTITY_FIELD_LABEL[`${entity}.${field}`] ?? FIELD_LABEL[field] ?? field;

export const formatValue = (entity: string, field: string, value?: string | null): string => {
  if (value == null || value === '') return '—';
  if (value === '***') return value;
  if (DATE_FIELDS.has(field)) return isoToVn(value) || value;
  if (field === 'uploadedAt') {
    const m = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(value);
    return m ? `${isoToVn(m[1])} ${m[2]}` : value;
  }
  if (MONEY_FIELDS.has(field) && /^-?\d+(\.0+)?$/.test(value)) return formatMoney(Number(value));
  if (field === 'sizeBytes' && /^\d+$/.test(value)) return `${formatMoney(Number(value))} byte`;
  const labels = ENUMS[`${entity}.${field}`] ?? ENUMS[field];
  if (labels) return labels[value] ?? value;
  if (RELATIONS[field]) return `${RELATIONS[field]} #${value}`;
  return value;
};
