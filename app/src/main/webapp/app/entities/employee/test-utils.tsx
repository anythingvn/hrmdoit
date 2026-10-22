import { configureStore } from '@reduxjs/toolkit';

import employee from './employee.reducer';

export const makeStore = (authorities: string[], account: Record<string, unknown> = {}) =>
  configureStore({
    reducer: {
      employee,
      authentication: (
        state = { account: { authorities, login: 'test', ...account }, isAuthenticated: true, sessionHasBeenFetched: true },
      ) => state,
    } as any,
  });
