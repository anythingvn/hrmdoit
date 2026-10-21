import { ALL_DOIT_ROLES, AUTHORITIES } from 'app/config/constants';

export type MenuItem = { key: string; label: string; path: string; icon: string };

const has = (authorities: string[], ...roles: string[]) => roles.some(r => (authorities ?? []).includes(r));

/**
 * Mục menu theo vai trò; nhiều vai trò thì gộp mục. KH2–KH4 thêm mục vào đây.
 * "Hồ sơ của tôi" hiện với ROLE_NHAN_VIEN và với mọi tài khoản đã gắn hồ sơ nhân viên ({@code hasEmployeeProfile}).
 */
export const menuFor = (authorities: string[], hasEmployeeProfile = false): MenuItem[] => {
  const items: MenuItem[] = [];
  const nhanVienOnly = has(authorities, AUTHORITIES.NHAN_VIEN) && !has(authorities, ...ALL_DOIT_ROLES);
  if (!nhanVienOnly) {
    items.push({ key: 'nhan-vien', label: 'Nhân viên', path: '/nhan-vien', icon: 'users' });
  }
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'hop-dong', label: 'Hợp đồng & mẫu', path: '/hop-dong', icon: 'file' });
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
