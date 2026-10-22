import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PrivateRoute from 'app/shared/auth/private-route';
import { AUTHORITIES } from 'app/config/constants';

import EmployeeList from './employee-list';
import EmployeeForm from './employee-form';
import EmployeeDetail from './detail/employee-detail';
import EmployeeImport from './import/employee-import';

const WRITE_ROLES = [AUTHORITIES.ADMIN, AUTHORITIES.HR];

const EmployeeRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<EmployeeList />} />
    <Route
      path="moi"
      element={
        <PrivateRoute hasAnyAuthorities={WRITE_ROLES}>
          <EmployeeForm />
        </PrivateRoute>
      }
    />
    <Route
      path="nhap-excel"
      element={
        <PrivateRoute hasAnyAuthorities={WRITE_ROLES}>
          <EmployeeImport />
        </PrivateRoute>
      }
    />
    <Route path=":id">
      <Route index element={<EmployeeDetail />} />
      <Route
        path="sua"
        element={
          <PrivateRoute hasAnyAuthorities={WRITE_ROLES}>
            <EmployeeForm />
          </PrivateRoute>
        }
      />
    </Route>
  </ErrorBoundaryRoutes>
);

export default EmployeeRoutes;
