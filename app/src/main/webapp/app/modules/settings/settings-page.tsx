import './settings.scss';

import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import MailSettings from 'app/modules/administration/mail-settings/mail-settings';
import LeaveSettings from 'app/modules/leave/leave-settings';
import ReminderStatus from 'app/modules/work-calendar/reminder-status';
import WorkCalendar from 'app/modules/work-calendar/work-calendar';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import CompanyTab from './company-tab';
import LocationsTab from './locations-tab';
import SelfPunchSettings from './self-punch-settings';

interface ISettingsTab {
  path: string;
  label: string;
  roles: string[];
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
  // HR xem được cài đặt nghỉ phép (như trang Quản lý nghỉ phép cũ), chỉ Quản trị sửa.
  {
    path: 'nghi-phep',
    label: 'Nghỉ phép',
    roles: ADMIN_HR,
    render: authorities => <LeaveSettings canEdit={hasAnyAuthority(authorities, ADMIN_ONLY)} />,
  },
];

/** Trang "Cài đặt" (Quản trị, HR): cấu hình chung của công ty gom một chỗ, mỗi phần một tab có link riêng. */
export const SettingsPage = () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const tabs = SETTINGS_TABS.filter(t => hasAnyAuthority(authorities, t.roles));
  const home = tabs.length > 0 ? `/cai-dat/${tabs[0].path}` : '/';
  return (
    <div className="settings-page">
      <div className="page-title-bar" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <h1 className="h2 mb-3">Cài đặt</h1>
      <nav aria-label="Cài đặt">
        <ul className="nav nav-pills mb-3">
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
          <Route key={t.path} path={t.path} element={t.render(authorities)} />
        ))}
        {/* Trang chính, tab không có quyền xem hoặc gõ sai: về tab đầu tiên được thấy. */}
        <Route path="*" element={<Navigate to={home} replace />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};

export default SettingsPage;
