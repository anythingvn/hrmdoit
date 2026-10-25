import React from 'react';
import { Link, NavLink, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import { DocumentTemplates } from 'app/entities/document-template/document-templates';
import { PageHeader } from 'app/shared/ui';

import ImportWizard from './import-wizard';
import PeriodDetail from './period-detail';
import PeriodList from './period-list';
import PayrollRules from './rules/payroll-rules';

/** Mục "Bảng lương" (Kế toán, Quản trị): kỳ lương, import, mức luật định. */
export default () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const isAdmin = hasAnyAuthority(authorities, [AUTHORITIES.ADMIN]);
  return (
    <div className="doit-page">
      <PageHeader title="Bảng lương" />
      <ul className="nav nav-tabs" aria-label="Bảng lương">
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
        <li className="nav-item">
          <NavLink to="/bang-luong/mau-phieu-luong" className="nav-link">
            Mẫu phiếu lương
          </NavLink>
        </li>
        <li className="nav-item">
          {/* Trang riêng (HR cũng xem được, không vào được Bảng lương). */}
          <Link to="/tong-hop-nghi" className="nav-link">
            Tổng hợp nghỉ &amp; làm thêm
          </Link>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        <Route index element={<PeriodList />} />
        <Route path="muc-luat" element={<PayrollRules canEdit={isAdmin} />} />
        <Route path="mau-phieu-luong" element={<DocumentTemplates />} />
        <Route path="import/:id" element={<ImportWizard />} />
        <Route path=":period" element={<PeriodDetail canApprove={isAdmin} />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
