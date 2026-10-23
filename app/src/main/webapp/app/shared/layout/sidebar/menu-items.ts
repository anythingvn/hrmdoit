import { ALL_DOIT_ROLES, AUTHORITIES, BUSINESS_ROLES } from 'app/config/constants';

/** Mục con hiện dưới mục cha khi đang ở trong mục cha (vd "Nhân viên đã lưu trữ" dưới "Nhân viên"). */
export type SubMenuItem = { label: string; path: string };

export type MenuItem = { key: string; label: string; path: string; icon: string; children?: SubMenuItem[] };

const has = (authorities: string[], ...roles: string[]) => roles.some(r => (authorities ?? []).includes(r));

/** Cờ từ {@code GET /api/account} cho phần nghỉ phép. */
export interface LeaveMenuFlags {
  /** Hồ sơ gắn tài khoản là NHAN_VIEN đang làm việc (THUC_TAP, DICH_VU không có). */
  leaveApplicable?: boolean;
  /** Là người duyệt đơn (trưởng nhóm, trưởng phòng, người duyệt của đơn đang chờ, BGĐ, HR, Quản trị). */
  isApprover?: boolean;
  /** Xem "Lịch nghỉ": trưởng nhóm, trưởng phòng (BGĐ, HR, Quản trị theo vai trò). */
  seesLeaveCalendar?: boolean;
  /** Gõ giờ chấm công: Quản trị, HR, trưởng nhóm, trưởng nhóm onsite. */
  entersAttendance?: boolean;
}

/**
 * Mục menu theo vai trò; nhiều vai trò thì gộp mục. KH2–KH4 thêm mục vào đây.
 * "Hồ sơ của tôi" hiện với ROLE_NHAN_VIEN và với mọi tài khoản đã gắn hồ sơ nhân viên ({@code hasEmployeeProfile}).
 * "Nghỉ phép" theo cờ {@code leaveApplicable}; "Duyệt đơn" theo cờ {@code isApprover} hoặc vai trò HR, Quản trị, BGĐ; "Lịch nghỉ" theo cờ
 * {@code seesLeaveCalendar} hoặc vai trò BGĐ, HR, Quản trị; "Quản lý nghỉ phép" cho HR, Quản trị; "Tổng hợp nghỉ & làm thêm" (cạnh
 * "Bảng lương") cho Kế toán, HR, Quản trị. "Chấm công" (gõ giờ theo ngày) theo cờ {@code entersAttendance} hoặc vai trò HR, Quản trị. Mục con "Nhân viên đã lưu trữ" dưới "Nhân viên" cho Quản trị, HR.
 */
export const menuFor = (authorities: string[], hasEmployeeProfile = false, leave: LeaveMenuFlags = {}): MenuItem[] => {
  const items: MenuItem[] = [];
  const nhanVienOnly = has(authorities, AUTHORITIES.NHAN_VIEN) && !has(authorities, ...ALL_DOIT_ROLES);
  const business = has(authorities, ...BUSINESS_ROLES);
  if (business) {
    items.push({ key: 'tong-quan', label: 'Tổng quan', path: '/tong-quan', icon: 'home' });
  }
  if (!nhanVienOnly) {
    const archived = has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)
      ? { children: [{ label: 'Nhân viên đã lưu trữ', path: '/nhan-vien/luu-tru' }] }
      : {};
    items.push({ key: 'nhan-vien', label: 'Nhân viên', path: '/nhan-vien', icon: 'users', ...archived });
  }
  if (leave.leaveApplicable) {
    items.push({ key: 'nghi-phep', label: 'Nghỉ phép', path: '/nghi-phep', icon: 'calendar' });
  }
  if (leave.isApprover || has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.BGD)) {
    items.push({ key: 'duyet-don', label: 'Duyệt đơn', path: '/duyet-don', icon: 'check' });
  }
  if (leave.seesLeaveCalendar || has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.BGD)) {
    items.push({ key: 'lich-nghi', label: 'Lịch nghỉ', path: '/lich-nghi', icon: 'calendar' });
  }
  if (leave.entersAttendance || has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'cham-cong', label: 'Chấm công', path: '/cham-cong', icon: 'clock' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'quan-ly-nghi-phep', label: 'Quản lý nghỉ phép', path: '/quan-ly-nghi-phep', icon: 'users' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'hop-dong', label: 'Hợp đồng & mẫu', path: '/hop-dong', icon: 'file' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.KE_TOAN)) {
    items.push({ key: 'bang-luong', label: 'Bảng lương', path: '/bang-luong', icon: 'file' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.KE_TOAN, AUTHORITIES.HR)) {
    items.push({ key: 'tong-hop-nghi', label: 'Tổng hợp nghỉ & làm thêm', path: '/tong-hop-nghi', icon: 'chart' });
  }
  if (business) {
    items.push({ key: 'bao-cao', label: 'Báo cáo', path: '/bao-cao', icon: 'chart' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'danh-muc', label: 'Danh mục', path: '/danh-muc/phong-ban', icon: 'folder' });
  }
  if (has(authorities, AUTHORITIES.ADMIN)) {
    items.push({ key: 'quan-tri', label: 'Quản trị', path: '/admin/user-management', icon: 'settings' });
  }
  if (hasEmployeeProfile || has(authorities, AUTHORITIES.NHAN_VIEN)) {
    items.push({ key: 'ho-so-cua-toi', label: 'Hồ sơ của tôi', path: '/ho-so-cua-toi', icon: 'user' });
  }
  return items;
};
