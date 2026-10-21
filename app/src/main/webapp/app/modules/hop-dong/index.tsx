import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import DocumentNumbers from 'app/modules/administration/document-numbers/document-numbers';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';

/** Mục "Hợp đồng & mẫu" (Quản trị, Nhân sự): dãy số văn bản; mẫu văn bản thêm ở KH2 Task 3. */
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
      <h1 className="h2 mb-3">Hợp đồng &amp; mẫu</h1>
      <ul className="nav nav-pills mb-3" aria-label="Hợp đồng và mẫu">
        <li className="nav-item">
          <NavLink to="/hop-dong/so-van-ban" className="nav-link">
            Số văn bản
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        <Route index element={<Navigate to="so-van-ban" replace />} />
        <Route path="so-van-ban" element={<DocumentNumbers canEdit={isAdmin} />} />
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
