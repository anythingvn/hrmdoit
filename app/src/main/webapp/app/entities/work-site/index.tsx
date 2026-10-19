import React from 'react';
import { Route } from 'react-router-dom';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import WorkSite from './work-site';
import WorkSiteDetail from './work-site-detail';
import WorkSiteUpdate from './work-site-update';
import WorkSiteDeleteDialog from './work-site-delete-dialog';

const WorkSiteRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<WorkSite />} />
    <Route path="new" element={<WorkSiteUpdate />} />
    <Route path=":id">
      <Route index element={<WorkSiteDetail />} />
      <Route path="edit" element={<WorkSiteUpdate />} />
      <Route path="delete" element={<WorkSiteDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default WorkSiteRoutes;
