import React from 'react';
import { NavLink, Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';

import Department from './department';
import WorkSite from './work-site';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  return (
    <div>
      <div className="page-title-bar" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <h1 className="h2 mb-3">Danh mục</h1>
      <ul className="nav nav-pills mb-3" aria-label="Danh mục">
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
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
