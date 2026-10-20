import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Department from './department';
import DepartmentDetail from './department-detail';
import DepartmentUpdate from './department-update';

const DepartmentRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Department />} />
    <Route path="new" element={<DepartmentUpdate />} />
    <Route path=":id">
      <Route index element={<DepartmentDetail />} />
      <Route path="edit" element={<DepartmentUpdate />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default DepartmentRoutes;
