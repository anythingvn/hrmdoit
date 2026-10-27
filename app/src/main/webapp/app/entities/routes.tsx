import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';
import { PageHeader } from 'app/shared/ui';

import Department from './department';
import WorkSite from './work-site';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  return (
    <div className="doit-page">
      <PageHeader title="Danh mục" />
      <ul className="nav nav-tabs" aria-label="Danh mục">
        <li className="nav-item">
          <NavLink to="/danh-muc/phong-ban" className="nav-link">
            Phòng ban
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink to="/danh-muc/noi-lam-viec" className="nav-link">
            Nơi làm việc
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        {/* prettier-ignore */}
        <Route path="phong-ban/*" element={<Department />} />
        <Route path="noi-lam-viec/*" element={<WorkSite />} />
        {/* Mức luật định đã chuyển sang Cài đặt → Bảng lương. */}
        <Route path="muc-luat" element={<Navigate to="/cai-dat/bang-luong" replace />} />
        {/* Lịch làm việc đã chuyển sang Cài đặt → Lịch & giờ làm. */}
        <Route path="lich-lam-viec" element={<Navigate to="/cai-dat/lich-lam-viec" replace />} />
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
