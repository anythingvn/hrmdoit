import React from 'react';
import { Navigate, Route, useParams } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Configuration from './configuration/configuration';
import Docs from './docs/docs';
import Health from './health/health';
import Logs from './logs/logs';
import Metrics from './metrics/metrics';

/** Địa chỉ cũ /admin/user-management/... → /cai-dat/tai-khoan/... (giữ phần đuôi: new, :login, :login/edit, ...). */
const UserManagementRedirect = () => {
  const tail = useParams()['*'];
  return <Navigate to={`/cai-dat/tai-khoan${tail ? `/${tail}` : ''}`} replace />;
};

/** Các trang kỹ thuật còn lại của /admin; tài khoản và nhật ký xóa nhân viên đã chuyển sang Cài đặt. */
const AdministrationRoutes = () => (
  <div className="doit-page">
    <ErrorBoundaryRoutes>
      {/* Cấu hình email đã chuyển sang Cài đặt → Email. */}
      <Route path="cau-hinh-email" element={<Navigate to="/cai-dat/email" replace />} />
      <Route path="nhat-ky-xoa-nhan-vien" element={<Navigate to="/cai-dat/nhat-ky-xoa-nhan-vien" replace />} />
      <Route path="user-management/*" element={<UserManagementRedirect />} />
      <Route path="health" element={<Health />} />
      <Route path="metrics" element={<Metrics />} />
      <Route path="configuration" element={<Configuration />} />
      <Route path="logs" element={<Logs />} />
      <Route path="docs" element={<Docs />} />
    </ErrorBoundaryRoutes>
  </div>
);

export default AdministrationRoutes;
