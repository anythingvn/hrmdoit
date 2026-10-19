import { configureStore } from '@reduxjs/toolkit';

import employee from './employee.reducer';

export const makeStore = (authorities: string[]) =>
  configureStore({
    reducer: {
      employee,
      authentication: (state = { account: { authorities, login: 'test' }, isAuthenticated: true, sessionHasBeenFetched: true }) => state,
    } as any,
  });
