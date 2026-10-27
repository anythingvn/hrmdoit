import { beforeAll, describe, expect, it, vi } from 'vitest';
import React from 'react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import { configureStore } from '@reduxjs/toolkit';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';

import { AUTHORITIES, ALL_DOIT_ROLES } from 'app/config/constants';
import { Authority } from 'app/shared/jhipster/constants';

import PrivateRoute, { hasAnyAuthority } from './private-route';

const TestComp = () => <div>Test</div>;

describe('private-route component', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('vi', {
      'error.http.403': 'You are not authorized to access this page.',
    });
  });

  const wrapper = (Elem: React.JSX.Element, authentication) => {
    const store = configureStore({
      reducer: {
        authentication: () => authentication,
      },
    });
    return render(
      <Provider store={store}>
        <MemoryRouter>{Elem}</MemoryRouter>
      </Provider>,
    );
  };

  it('máy chủ đang khởi động lại: hiện thông báo kết nối lại, không chuyển về trang đăng nhập', () => {
    const { getByRole, queryByText } = wrapper(
      <PrivateRoute>
        <TestComp />
      </PrivateRoute>,
      { isAuthenticated: false, sessionHasBeenFetched: false, serverUnavailable: true, account: {} },
    );
    expect(getByRole('status').textContent).toContain('Máy chủ đang khởi động lại');
    expect(queryByText('Test')).toBeNull();
  });

  it('should throw error when falsy children are provided', () => {
    const originalError = console.error;
    console.error = vi.fn();
    expect(() =>
      wrapper(<PrivateRoute>{null}</PrivateRoute>, {
        isAuthenticated: true,
        sessionHasBeenFetched: true,
        account: {
          authorities: [],
        },
      }),
    ).toThrow(Error);
    console.error = originalError;
  });

  it('should render an error message when the user has no authorities', () => {
    const { container } = wrapper(
      <PrivateRoute>
        <TestComp />
      </PrivateRoute>,
      {
        isAuthenticated: true,
        sessionHasBeenFetched: true,
        account: {
          authorities: [],
        },
      },
    );
    expect(container.innerHTML).toMatch(/<div class="insufficient-authority"><div class="alert alert-danger">.*<\/div><\/div>/);
  });

  it('should render a route for the component provided when authenticated', () => {
    const { container } = wrapper(
      <PrivateRoute>
        <TestComp />
      </PrivateRoute>,
      {
        isAuthenticated: true,
        sessionHasBeenFetched: true,
        account: {
          authorities: ['ADMIN'],
        },
      },
    );
    expect(container.innerHTML).toEqual('<div>Test</div>');
  });

  it('should redirect when not authenticated', () => {
    const { container } = wrapper(
      <Routes>
        <Route
          path="/"
          element={
            <PrivateRoute>
              <TestComp />
            </PrivateRoute>
          }
        />
        <Route path="/login" element={<div>Login</div>} />
      </Routes>,
      {
        isAuthenticated: false,
        sessionHasBeenFetched: true,
        account: {
          authorities: ['ADMIN'],
        },
      },
    );
    expect(container.innerHTML).not.toEqual('<div>Test</div>');
    expect(container.innerHTML).toEqual('<div>Login</div>');
  });
});

describe('hasAnyAuthority', () => {
  it('should return false when authorities is invalid', () => {
    expect(hasAnyAuthority(undefined, undefined)).toEqual(false);
    expect(hasAnyAuthority(null, [])).toEqual(false);
    expect(hasAnyAuthority([], [])).toEqual(false);
    expect(hasAnyAuthority([], [Authority.USER])).toEqual(false);
  });

  it('should return true when authorities is valid and hasAnyAuthorities is empty', () => {
    expect(hasAnyAuthority([Authority.USER], [])).toEqual(true);
  });

  it('should return true when authorities is valid and hasAnyAuthorities contains an authority', () => {
    expect(hasAnyAuthority([Authority.USER], [Authority.USER])).toEqual(true);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], [Authority.USER])).toEqual(true);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], [Authority.USER, Authority.ADMIN])).toEqual(true);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], [Authority.USER, 'ROLEADMIN'])).toEqual(true);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], [Authority.ADMIN])).toEqual(true);
  });

  it('should return false when authorities is valid and hasAnyAuthorities does not contain an authority', () => {
    expect(hasAnyAuthority([Authority.USER], [Authority.ADMIN])).toEqual(false);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], ['ROLE_USERSS'])).toEqual(false);
    expect(hasAnyAuthority([Authority.USER, Authority.ADMIN], ['ROLEUSER', 'ROLEADMIN'])).toEqual(false);
  });
});

describe('DOIT roles', () => {
  it('admits an account holding only ROLE_HR, ROLE_KE_TOAN or ROLE_BGD', () => {
    [AUTHORITIES.HR, AUTHORITIES.KE_TOAN, AUTHORITIES.BGD].forEach(role => {
      expect(hasAnyAuthority([role], ALL_DOIT_ROLES)).toEqual(true);
    });
  });
});

describe('mật khẩu mặc định', () => {
  const renderAt = (path: string, account: any) => {
    const store = configureStore({
      reducer: {
        authentication: () => ({ isAuthenticated: true, sessionHasBeenFetched: true, account }),
      },
    });
    return render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path="/nhan-vien"
              element={
                <PrivateRoute hasAnyAuthorities={[AUTHORITIES.HR]}>
                  <TestComp />
                </PrivateRoute>
              }
            />
            <Route
              path="/account/password"
              element={
                <PrivateRoute hasAnyAuthorities={[AUTHORITIES.HR]}>
                  <div>Trang đổi mật khẩu</div>
                </PrivateRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
  };

  it('đang dùng mật khẩu mặc định thì mọi trang chuyển tới trang đổi mật khẩu', () => {
    const { container } = renderAt('/nhan-vien', { authorities: [AUTHORITIES.HR], mustChangePassword: true });
    expect(container.innerHTML).toContain('Trang đổi mật khẩu');
    expect(container.innerHTML).not.toContain('Test');
  });

  it('đã đổi mật khẩu thì vào trang bình thường', () => {
    const { container } = renderAt('/nhan-vien', { authorities: [AUTHORITIES.HR], mustChangePassword: false });
    expect(container.innerHTML).toContain('Test');
  });
});
