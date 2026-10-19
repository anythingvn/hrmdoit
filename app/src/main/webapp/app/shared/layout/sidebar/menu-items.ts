import { AUTHORITIES } from 'app/config/constants';

export type MenuItem = { key: string; label: string; path: string; icon: string };

const has = (authorities: string[], ...roles: string[]) => roles.some(r => (authorities ?? []).includes(r));

/** Mục menu theo vai trò. KH2–KH4 thêm mục vào đây. */
export const menuFor = (authorities: string[]): MenuItem[] => {
  const items: MenuItem[] = [{ key: 'nhan-vien', label: 'Nhân viên', path: '/nhan-vien', icon: 'users' }];
  if (has(authorities, AUTHORITIES.ADMIN, AUTHORITIES.HR)) {
    items.push({ key: 'danh-muc', label: 'Danh mục', path: '/danh-muc/phong-ban', icon: 'folder' });
  }
  if (has(authorities, AUTHORITIES.ADMIN)) {
    items.push({ key: 'quan-tri', label: 'Quản trị', path: '/admin/user-management', icon: 'settings' });
  }
  return items;
};
