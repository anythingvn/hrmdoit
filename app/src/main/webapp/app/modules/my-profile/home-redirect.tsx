import React from 'react';
import { Navigate } from 'react-router';

import { ALL_DOIT_ROLES, AUTHORITIES, BUSINESS_ROLES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { CHANGE_PASSWORD_PATH, hasAnyAuthority } from 'app/shared/auth/private-route';

/** Trang chủ: vai trò nghiệp vụ về Tổng quan, nhân viên thường (chỉ ROLE_NHAN_VIEN) về "Hồ sơ của tôi", còn lại về danh sách nhân viên. */
export const HomeRedirect = () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account?.authorities ?? []);
  const sessionHasBeenFetched = useAppSelector(state => state.authentication.sessionHasBeenFetched);
  // Chờ biết vai trò rồi mới chuyển, tránh đưa nhân viên thường vào trang danh sách (bị 403).
  const mustChangePassword: boolean = useAppSelector(state => !!state.authentication.account?.mustChangePassword);
  if (!sessionHasBeenFetched) return <div></div>;
  if (mustChangePassword) return <Navigate to={CHANGE_PASSWORD_PATH} replace />;
  const nhanVienOnly = hasAnyAuthority(authorities, [AUTHORITIES.NHAN_VIEN]) && !hasAnyAuthority(authorities, ALL_DOIT_ROLES);
  if (hasAnyAuthority(authorities, BUSINESS_ROLES)) return <Navigate to="/tong-quan" replace />;
  return <Navigate to={nhanVienOnly ? '/ho-so-cua-toi' : '/nhan-vien'} replace />;
};

export default HomeRedirect;
