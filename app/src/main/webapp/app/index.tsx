import React from 'react';

import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { bindActionCreators } from 'redux';

import AppComponent from 'app/app';
import setupAxiosInterceptors from 'app/config/axios-interceptor';
import { loadIcons } from 'app/config/icon-loader';
import getStore from 'app/config/store';
import { registerLocale } from 'app/config/translation';
import ErrorBoundary from 'app/shared/error/error-boundary';
import { clearAuthentication, getSession } from 'app/shared/reducers/authentication';

const store = getStore();
// Load the translations before rendering.
await registerLocale(store);

const actions = bindActionCreators({ clearAuthentication }, store.dispatch);
setupAxiosInterceptors(
  () => actions.clearAuthentication('login.error.unauthorized'),
  // Đang dùng mật khẩu mặc định: tải lại tài khoản (có cờ mustChangePassword) để chuyển tới trang đổi mật khẩu.
  () => {
    if (!store.getState().authentication.account?.mustChangePassword) {
      void store.dispatch(getSession());
    }
  },
);

loadIcons();

const rootEl = document.getElementById('root');
const root = createRoot(rootEl!);

root.render(
  <ErrorBoundary>
    <Provider store={store}>
      <AppComponent />
    </Provider>
  </ErrorBoundary>,
);
