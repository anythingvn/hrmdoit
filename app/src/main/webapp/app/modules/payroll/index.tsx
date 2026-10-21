import React from 'react';
import { NavLink, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ImportWizard from './import-wizard';
import PeriodDetail from './period-detail';
import PeriodList from './period-list';
import PayrollRules from './rules/payroll-rules';

/** Mục "Bảng lương" (Kế toán, Quản trị): kỳ lương, import, mức luật định. */
export default () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const isAdmin = hasAnyAuthority(authorities, [AUTHORITIES.ADMIN]);
  return (
    <div>
      <div className="page-title-bar" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <h1 className="h2 mb-3">Bảng lương</h1>
      <ul className="nav nav-pills mb-3" aria-label="Bảng lương">
        <li className="nav-item">
          <NavLink to="/bang-luong" end className="nav-link">
            Kỳ lương
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink to="/bang-luong/muc-luat" className="nav-link">
            Mức luật định
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        <Route index element={<PeriodList />} />
        <Route path="muc-luat" element={<PayrollRules canEdit={isAdmin} />} />
        <Route path="import/:id" element={<ImportWizard />} />
        <Route path=":period" element={<PeriodDetail canApprove={isAdmin} />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
