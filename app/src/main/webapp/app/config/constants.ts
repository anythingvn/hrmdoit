export const AUTHORITIES = {
  ADMIN: 'ROLE_ADMIN',
  USER: 'ROLE_USER',
  HR: 'ROLE_HR',
  KE_TOAN: 'ROLE_KE_TOAN',
  BGD: 'ROLE_BGD',
  /** Nhân viên thường: chỉ xem hồ sơ của mình. */
  NHAN_VIEN: 'ROLE_NHAN_VIEN',
};

/** Vai trò nghiệp vụ (được vào danh sách nhân viên…); ROLE_NHAN_VIEN không có ở đây. */
export const ALL_DOIT_ROLES = [AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN, AUTHORITIES.BGD, AUTHORITIES.USER];

/** Mọi vai trò đăng nhập được (trang tài khoản, Hồ sơ của tôi). */
/** Vai trò nghiệp vụ: xem Tổng quan, Nhắc việc và Báo cáo. */
export const BUSINESS_ROLES = [AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN, AUTHORITIES.BGD];

export const ANY_LOGIN_ROLES = [...ALL_DOIT_ROLES, AUTHORITIES.NHAN_VIEN];

/** Tên vai trò tiếng Việt. */
export const ROLE_LABELS: Record<string, string> = {
  [AUTHORITIES.ADMIN]: 'Quản trị',
  [AUTHORITIES.HR]: 'Nhân sự',
  [AUTHORITIES.KE_TOAN]: 'Kế toán',
  [AUTHORITIES.BGD]: 'Ban giám đốc',
  [AUTHORITIES.NHAN_VIEN]: 'Nhân viên',
  [AUTHORITIES.USER]: 'Người dùng',
};

export const messages = {
  DATA_ERROR_ALERT: 'Internal Error',
};

export const APP_DATE_FORMAT = 'DD/MM/YY HH:mm';
export const APP_TIMESTAMP_FORMAT = 'DD/MM/YY HH:mm:ss';
export const APP_LOCAL_DATE_FORMAT = 'DD/MM/YYYY';
export const APP_LOCAL_DATETIME_FORMAT = 'YYYY-MM-DDTHH:mm';
export const APP_WHOLE_NUMBER_FORMAT = '0,0';
export const APP_TWO_DIGITS_AFTER_POINT_NUMBER_FORMAT = '0,0.[00]';
