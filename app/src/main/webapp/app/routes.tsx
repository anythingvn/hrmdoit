import React, { Suspense } from 'react';
import { Route } from 'react-router';

import { ALL_DOIT_ROLES, ANY_LOGIN_ROLES, AUTHORITIES, BUSINESS_ROLES } from 'app/config/constants';
import EmployeeRoutes from 'app/entities/employee';
import EntitiesRoutes from 'app/entities/routes';
import PasswordResetFinish from 'app/modules/account/password-reset/finish/password-reset-finish';
import PasswordResetInit from 'app/modules/account/password-reset/init/password-reset-init';
import Login from 'app/modules/login/login';
import Logout from 'app/modules/login/logout';
import HomeRedirect from 'app/modules/my-profile/home-redirect';
import PrivateRoute from 'app/shared/auth/private-route';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import PageNotFound from 'app/shared/error/page-not-found';

const loading = <div>loading ...</div>;

const Account = React.lazy(() => import('app/modules/account'));

const Admin = React.lazy(() => import('app/modules/administration'));

const MyProfile = React.lazy(() => import('app/modules/my-profile/my-profile'));

const HopDong = React.lazy(() => import('app/modules/hop-dong'));

const Payroll = React.lazy(() => import('app/modules/payroll'));

const Dashboard = React.lazy(() => import('app/modules/dashboard/dashboard'));

const HrReport = React.lazy(() => import('app/modules/reports/hr-report'));

const MyLeave = React.lazy(() => import('app/modules/leave/my-leave'));

const ApprovalInbox = React.lazy(() => import('app/modules/leave/approval-inbox'));

const LeaveCalendarPage = React.lazy(() => import('app/modules/leave/leave-calendar-page'));

const HrLeave = React.lazy(() => import('app/modules/leave/hr-leave'));

const AccountingSummary = React.lazy(() => import('app/modules/leave/accounting-summary'));

const DayEntry = React.lazy(() => import('app/modules/attendance/day-entry'));
const MonthBoard = React.lazy(() => import('app/modules/attendance/month-board'));
const MyAttendance = React.lazy(() => import('app/modules/attendance/my-attendance'));

const AppRoutes = () => {
  return (
    <div className="view-routes">
      <Suspense fallback={loading}>
        <ErrorBoundaryRoutes>
          <Route index element={<HomeRedirect />} />
          <Route
            path="tong-quan"
            element={
              <PrivateRoute hasAnyAuthorities={BUSINESS_ROLES}>
                <Dashboard />
              </PrivateRoute>
            }
          />
          <Route
            path="bao-cao"
            element={
              <PrivateRoute hasAnyAuthorities={BUSINESS_ROLES}>
                <HrReport />
              </PrivateRoute>
            }
          />
          <Route
            path="ho-so-cua-toi"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <MyProfile />
              </PrivateRoute>
            }
          />
          <Route
            path="nghi-phep"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <MyLeave />
              </PrivateRoute>
            }
          />
          <Route
            path="duyet-don"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <ApprovalInbox />
              </PrivateRoute>
            }
          />
          <Route
            path="lich-nghi"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <LeaveCalendarPage />
              </PrivateRoute>
            }
          />
          <Route
            path="quan-ly-nghi-phep"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN, AUTHORITIES.HR]}>
                <HrLeave />
              </PrivateRoute>
            }
          />
          <Route
            path="tong-hop-nghi"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN]}>
                <AccountingSummary />
              </PrivateRoute>
            }
          />
          <Route
            path="cham-cong"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <DayEntry />
              </PrivateRoute>
            }
          />
          <Route
            path="bang-cong"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <MonthBoard />
              </PrivateRoute>
            }
          />
          <Route
            path="bang-cong-cua-toi"
            element={
              <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                <MyAttendance />
              </PrivateRoute>
            }
          />
          <Route
            path="nhan-vien/*"
            element={
              <PrivateRoute hasAnyAuthorities={ALL_DOIT_ROLES}>
                <EmployeeRoutes />
              </PrivateRoute>
            }
          />
          <Route path="login" element={<Login />} />
          <Route path="logout" element={<Logout />} />
          <Route path="account">
            <Route
              path="*"
              element={
                <PrivateRoute hasAnyAuthorities={ANY_LOGIN_ROLES}>
                  <Account />
                </PrivateRoute>
              }
            />
            <Route path="reset">
              <Route path="request" element={<PasswordResetInit />} />
              <Route path="finish" element={<PasswordResetFinish />} />
            </Route>
          </Route>
          <Route
            path="admin/*"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN]}>
                <Admin />
              </PrivateRoute>
            }
          />
          <Route
            path="hop-dong/*"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN, AUTHORITIES.HR]}>
                <HopDong />
              </PrivateRoute>
            }
          />
          <Route
            path="bang-luong/*"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN, AUTHORITIES.KE_TOAN]}>
                <Payroll />
              </PrivateRoute>
            }
          />
          <Route
            path="danh-muc/*"
            element={
              <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN, AUTHORITIES.HR]}>
                <EntitiesRoutes />
              </PrivateRoute>
            }
          />
          <Route path="*" element={<PageNotFound />} />
        </ErrorBoundaryRoutes>
      </Suspense>
    </div>
  );
};

export default AppRoutes;
