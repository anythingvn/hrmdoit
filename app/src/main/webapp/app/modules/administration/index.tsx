import React from 'react';
import { NavLink, Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import { useAppSelector } from 'app/config/store';
import { SYSTEM_ADMIN_LOGIN } from 'app/entities/employee/archive/archive-api';

import Configuration from './configuration/configuration';
import Docs from './docs/docs';
import EmployeeDeletions from './employee-deletions/employee-deletions';
import Health from './health/health';
import Logs from './logs/logs';
import MailSettings from './mail-settings/mail-settings';
import Metrics from './metrics/metrics';
import UserManagement from './user-management';

const AdministrationRoutes = () => {
  // Nhật ký xóa hẳn nhân viên: chỉ tài khoản admin (server kiểm tra lại).
  const systemAdmin = useAppSelector(state => state.authentication.account?.login) === SYSTEM_ADMIN_LOGIN;
  return (
    <div>
      <ul className="nav nav-pills mb-3" aria-label="Quản trị">
        <li className="nav-item">
          <NavLink to="/admin/user-management" className="nav-link">
            Tài khoản
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink to="/admin/cau-hinh-email" className="nav-link">
            Cấu hình email
          </NavLink>
        </li>
        {systemAdmin && (
          <li className="nav-item">
            <NavLink to="/admin/nhat-ky-xoa-nhan-vien" className="nav-link">
              Nhật ký xóa nhân viên
            </NavLink>
          </li>
        )}
      </ul>
      <ErrorBoundaryRoutes>
        <Route path="cau-hinh-email" element={<MailSettings />} />
        <Route path="nhat-ky-xoa-nhan-vien" element={<EmployeeDeletions />} />
        <Route path="user-management/*" element={<UserManagement />} />
        <Route path="health" element={<Health />} />
        <Route path="metrics" element={<Metrics />} />
        <Route path="configuration" element={<Configuration />} />
        <Route path="logs" element={<Logs />} />
        <Route path="docs" element={<Docs />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};

export default AdministrationRoutes;
