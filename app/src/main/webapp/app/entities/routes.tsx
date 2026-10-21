import React from 'react';
import { NavLink, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import PayrollRules from 'app/modules/payroll/rules/payroll-rules';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';

import Department from './department';
import WorkSite from './work-site';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
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
        <li className="nav-item">
          <NavLink to="/danh-muc/muc-luat" className="nav-link">
            Mức luật định
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        {/* prettier-ignore */}
        <Route path="phong-ban/*" element={<Department />} />
        <Route path="noi-lam-viec/*" element={<WorkSite />} />
        <Route path="muc-luat" element={<PayrollRules canEdit={hasAnyAuthority(authorities, [AUTHORITIES.ADMIN])} />} />
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
