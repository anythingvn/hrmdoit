import React from 'react';
import { Translate } from 'react-jhipster';
import { Navigate, PathRouteProps, useLocation } from 'react-router';

import { useAppSelector } from 'app/config/store';
import ErrorBoundary from 'app/shared/error/error-boundary';

/** Trang đổi mật khẩu (bắt buộc khi đang dùng mật khẩu mặc định). */
export const CHANGE_PASSWORD_PATH = '/account/password';

interface IOwnProps extends PathRouteProps {
  hasAnyAuthorities?: string[];
  children: React.ReactNode;
}

const PrivateRoute = ({ children, hasAnyAuthorities = [], ...rest }: IOwnProps) => {
  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  const sessionHasBeenFetched = useAppSelector(state => state.authentication.sessionHasBeenFetched);
  const account = useAppSelector(state => state.authentication.account);
  const isAuthorized = hasAnyAuthority(account.authorities, hasAnyAuthorities);
  const pageLocation = useLocation();

  if (!children) {
    throw new Error(`A component needs to be specified for private route for path ${rest.path}`);
  }

  if (!sessionHasBeenFetched) {
    return <div></div>;
  }

  if (isAuthenticated) {
    // Đang dùng mật khẩu mặc định: phải đổi mật khẩu trước khi vào trang khác (server cũng chặn mọi API khác).
    if (account?.mustChangePassword && pageLocation.pathname !== CHANGE_PASSWORD_PATH) {
      return <Navigate to={CHANGE_PASSWORD_PATH} replace />;
    }
    if (isAuthorized) {
      return <ErrorBoundary>{children}</ErrorBoundary>;
    }

    return (
      <div className="insufficient-authority">
        <div className="alert alert-danger">
          <Translate contentKey="error.http.403">You are not authorized to access this page.</Translate>
        </div>
      </div>
    );
  }

  return (
    <Navigate
      to={{
        pathname: '/login',
        search: pageLocation.search,
      }}
      replace
      state={{ from: pageLocation }}
    />
  );
};

export const hasAnyAuthority = (authorities: string[], hasAnyAuthorities: string[]) => {
  if (authorities && authorities.length !== 0) {
    if (hasAnyAuthorities.length === 0) {
      return true;
    }
    return hasAnyAuthorities.some(auth => authorities.includes(auth));
  }
  return false;
};

/**
 * Checks authentication before showing the children and redirects to the
 * login page if the user is not authenticated.
 * If hasAnyAuthorities is provided the authorization status is also
 * checked and an error message is shown if the user is not authorized.
 */
export default PrivateRoute;
