import React from 'react';
import { NavLink, Navigate, Route } from 'react-router';

import DocumentTemplates from 'app/entities/document-template/document-templates';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';
import { PageHeader } from 'app/shared/ui';

/** Mục "Hợp đồng & mẫu" (Quản trị, Nhân sự): mẫu văn bản. */
export default () => {
  return (
    <div className="doit-page">
      <PageHeader title={<>Hợp đồng &amp; mẫu</>} />
      <ul className="nav nav-tabs" aria-label="Hợp đồng và mẫu">
        <li className="nav-item">
          <NavLink to="/hop-dong/mau-van-ban" className="nav-link">
            Mẫu văn bản
          </NavLink>
        </li>
      </ul>
      <ErrorBoundaryRoutes>
        <Route index element={<Navigate to="mau-van-ban" replace />} />
        <Route path="mau-van-ban" element={<DocumentTemplates />} />
        {/* Số văn bản đã chuyển sang Cài đặt → Mã & số văn bản. */}
        <Route path="so-van-ban" element={<Navigate to="/cai-dat/ma-so-van-ban" replace />} />
        <Route path="*" element={<PageNotFound />} />
      </ErrorBoundaryRoutes>
    </div>
  );
};
