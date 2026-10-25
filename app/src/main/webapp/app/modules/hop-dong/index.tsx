import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import DocumentTemplates from 'app/entities/document-template/document-templates';
import DocumentNumbers from 'app/modules/administration/document-numbers/document-numbers';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';
import { PageHeader } from 'app/shared/ui';

/** Mục "Hợp đồng & mẫu" (Quản trị, Nhân sự): mẫu văn bản và dãy số văn bản. */
export default () => {
  const authorities: string[] = useAppSelector(state => state.authentication.account.authorities ?? []);
  const isAdmin = hasAnyAuthority(authorities, [AUTHORITIES.ADMIN]);
  return (
    <div className="doit-page">
      <PageHeader title={<>Hợp đồng &amp; mẫu</>} />
      <ul className="nav nav-tabs" aria-label="Hợp đồng và mẫu">
        <li className="nav-item">
          <NavLink to="/hop-dong/mau-van-ban" className="nav-link">
            Mẫu văn bản
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink to="/hop-dong/so-van-ban" className="nav-link">
            Số văn bản
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        <Route index element={<Navigate to="mau-van-ban" replace />} />
        <Route path="mau-van-ban" element={<DocumentTemplates />} />
        <Route path="so-van-ban" element={<DocumentNumbers canEdit={isAdmin} />} />
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
