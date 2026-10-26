import './settings.scss';

import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { SYSTEM_ADMIN_LOGIN } from 'app/entities/employee/archive/archive-api';
import EmployeeDeletions from 'app/modules/administration/employee-deletions/employee-deletions';
import MailSettings from 'app/modules/administration/mail-settings/mail-settings';
import UserManagementRoutes from 'app/modules/administration/user-management';
import LeaveSettings from 'app/modules/leave/leave-settings';
import ReminderStatus from 'app/modules/work-calendar/reminder-status';
import WorkCalendar from 'app/modules/work-calendar/work-calendar';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import { PageHeader } from 'app/shared/ui';

import BackupTab from './backup-tab';
import CompanyTab from './company-tab';
import LocationsTab from './locations-tab';
import SelfPunchSettings from './self-punch-settings';

interface ISettingsTab {
  path: string;
  label: string;
  roles: string[];
  /** Điều kiện thêm ngoài vai trò (vd chỉ tài khoản admin hệ thống); máy chủ vẫn kiểm tra lại. */
  visible?: (login?: string) => boolean;
  render: (authorities: string[]) => React.ReactNode;
}

const ADMIN_ONLY = [AUTHORITIES.ADMIN];
const ADMIN_HR = [AUTHORITIES.ADMIN, AUTHORITIES.HR];

/** Các tab theo thứ tự hiển thị; quyền xem từng tab (máy chủ kiểm tra lại quyền sửa). */
export const SETTINGS_TABS: ISettingsTab[] = [
  { path: 'cong-ty', label: 'Công ty', roles: ADMIN_ONLY, render: () => <CompanyTab /> },
  {
    path: 'lich-lam-viec',
    label: 'Lịch & giờ làm',
    roles: ADMIN_HR,
    // Giữ nguyên quyền con của trang Lịch làm việc cũ: ngày làm trong tuần chỉ Quản trị; ngày lễ, giờ làm cả HR.
    render: authorities => (
      <>
        {hasAnyAuthority(authorities, ADMIN_ONLY) && <ReminderStatus />}
        <WorkCalendar
          canEditWeek={hasAnyAuthority(authorities, ADMIN_ONLY)}
          canEditHolidays
          canEditHours={hasAnyAuthority(authorities, ADMIN_HR)}
        />
      </>
    ),
  },
  {
    path: 'dia-diem',
    label: 'Địa điểm chấm công',
    roles: ADMIN_HR,
    // Khối Tự chấm công (thông báo vị trí, thời hạn giữ tọa độ): HR xem, chỉ Quản trị sửa.
    render: authorities => (
      <>
        <LocationsTab />
        <SelfPunchSettings canEdit={hasAnyAuthority(authorities, ADMIN_ONLY)} />
      </>
    ),
  },
  { path: 'email', label: 'Email', roles: ADMIN_ONLY, render: () => <MailSettings /> },
  { path: 'sao-luu', label: 'Sao lưu', roles: ADMIN_ONLY, render: () => <BackupTab /> },
  // Quản lý tài khoản (trước ở menu Quản trị); các màn con new, :login, :login/edit, :login/delete nằm trong tab.
  { path: 'tai-khoan', label: 'Tài khoản', roles: ADMIN_ONLY, render: () => <UserManagementRoutes /> },
  // HR xem được cài đặt nghỉ phép (như trang Quản lý nghỉ phép cũ), chỉ Quản trị sửa.
  {
    path: 'nghi-phep',
    label: 'Nghỉ phép',
    roles: ADMIN_HR,
    render: authorities => <LeaveSettings canEdit={hasAnyAuthority(authorities, ADMIN_ONLY)} />,
  },
  // Nhật ký xóa hẳn nhân viên: chỉ tài khoản admin hệ thống.
  {
    path: 'nhat-ky-xoa-nhan-vien',
    label: 'Nhật ký xóa nhân viên',
    roles: ADMIN_ONLY,
    visible: login => login === SYSTEM_ADMIN_LOGIN,
    render: () => <EmployeeDeletions />,
  },
];

/** Trang "Cài đặt" (Quản trị, HR): cấu hình chung của công ty gom một chỗ, mỗi phần một tab có link riêng. */
export const SettingsPage = () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const login: string | undefined = useAppSelector(state => state.authentication.account?.login);
  const tabs = SETTINGS_TABS.filter(t => hasAnyAuthority(authorities, t.roles) && (t.visible?.(login) ?? true));
  const home = tabs.length > 0 ? `/cai-dat/${tabs[0].path}` : '/';
  return (
    <div className="settings-page doit-page">
      <PageHeader title="Cài đặt" />
      <nav aria-label="Cài đặt">
        <ul className="nav nav-tabs">
          {tabs.map(t => (
            <li className="nav-item" key={t.path}>
              <NavLink to={`/cai-dat/${t.path}`} className="nav-link">
                {t.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <ErrorBoundaryRoutes>
        {tabs.map(t => (
          <Route key={t.path} path={`${t.path}/*`} element={t.render(authorities)} />
        ))}
        {/* Trang chính, tab không có quyền xem hoặc gõ sai: về tab đầu tiên được thấy. */}
        <Route path="*" element={<Navigate to={home} replace />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};

export default SettingsPage;
