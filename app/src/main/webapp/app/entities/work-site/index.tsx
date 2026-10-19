import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import WorkSite from './work-site';
import WorkSiteDetail from './work-site-detail';
import WorkSiteUpdate from './work-site-update';

const WorkSiteRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<WorkSite />} />
    <Route path="new" element={<WorkSiteUpdate />} />
    <Route path=":id">
      <Route index element={<WorkSiteDetail />} />
      <Route path="edit" element={<WorkSiteUpdate />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default WorkSiteRoutes;
