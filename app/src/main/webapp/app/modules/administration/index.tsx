import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import { useAppSelector } from 'app/config/store';
import { SYSTEM_ADMIN_LOGIN } from 'app/entities/employee/archive/archive-api';
import { PageHeader } from 'app/shared/ui';

import Configuration from './configuration/configuration';
import Docs from './docs/docs';
import EmployeeDeletions from './employee-deletions/employee-deletions';
import Health from './health/health';
import Logs from './logs/logs';
import Metrics from './metrics/metrics';
import UserManagement from './user-management';

const AdministrationRoutes = () => {
  // Nhật ký xóa hẳn nhân viên: chỉ tài khoản admin (server kiểm tra lại).
  const systemAdmin = useAppSelector(state => state.authentication.account?.login) === SYSTEM_ADMIN_LOGIN;
  return (
    <div className="doit-page">
      <PageHeader title="Quản trị" />
      <ul className="nav nav-tabs" aria-label="Quản trị">
        <li className="nav-item">
          <NavLink to="/admin/user-management" className="nav-link">
            Tài khoản
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
        {/* Cấu hình email đã chuyển sang Cài đặt → Email. */}
        <Route path="cau-hinh-email" element={<Navigate to="/cai-dat/email" replace />} />
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
