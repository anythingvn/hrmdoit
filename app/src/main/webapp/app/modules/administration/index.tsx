import React from 'react';
import { NavLink, Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Configuration from './configuration/configuration';
import Docs from './docs/docs';
import Health from './health/health';
import Logs from './logs/logs';
import MailSettings from './mail-settings/mail-settings';
import Metrics from './metrics/metrics';
import UserManagement from './user-management';

const AdministrationRoutes = () => (
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
    </ul>
    <ErrorBoundaryRoutes>
      <Route path="cau-hinh-email" element={<MailSettings />} />
      <Route path="user-management/*" element={<UserManagement />} />
      <Route path="health" element={<Health />} />
      <Route path="metrics" element={<Metrics />} />
      <Route path="configuration" element={<Configuration />} />
      <Route path="logs" element={<Logs />} />
      <Route path="docs" element={<Docs />} />
    </ErrorBoundaryRoutes>
  </div>
);

export default AdministrationRoutes;
