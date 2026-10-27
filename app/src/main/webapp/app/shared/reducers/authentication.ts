import { Storage } from 'react-jhipster';

import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import axios, { AxiosResponse } from 'axios';

import { AppThunk } from 'app/config/store';
import { AUTHENTICATION_TOKEN_KEY } from 'app/shared/jhipster/constants';
import { setLocale } from 'app/shared/reducers/locale';

import { serializeAxiosError } from './reducer.utils';

export const initialState = {
  loading: false,
  isAuthenticated: false,
  loginSuccess: false,
  loginError: false, // Errors returned from server side
  /** Câu báo riêng của server khi bị từ chối (vd 429 đăng nhập sai quá nhiều lần); null thì dùng câu chung. */
  loginErrorDetail: null as string | null,
  showModalLogin: false,
  account: {} as any,
  errorMessage: null as unknown as string, // Errors returned from server side
  redirectMessage: null as unknown as string,
  sessionHasBeenFetched: false,
  /** Không gọi được máy chủ (đang khởi động lại, vd build lại Docker) trong khi trình duyệt vẫn còn token: chưa kết luận phiên. */
  serverUnavailable: false,
  logoutUrl: null as unknown as string,
};

export type AuthenticationState = Readonly<typeof initialState>;

// Actions

/** Khoảng chờ giữa hai lần thử lại khi máy chủ chưa sẵn sàng. */
export const SESSION_RETRY_MS = 3000;

const hasStoredToken = () => Boolean(Storage.local.get(AUTHENTICATION_TOKEN_KEY) || Storage.session.get(AUTHENTICATION_TOKEN_KEY));

/** Lỗi do máy chủ chưa chạy hoặc đang khởi động (không có phản hồi, 502/503/504), không phải do phiên đăng nhập. */
const isServerUnavailable = (err: any) => {
  const status = err?.response?.status;
  return status === undefined ? Boolean(err?.isAxiosError) : status === 502 || status === 503 || status === 504;
};

let retryTimer: ReturnType<typeof setTimeout> | null = null;

export const getSession = (): AppThunk => async (dispatch, getState) => {
  const result = await dispatch(getAccount());
  if (getAccount.rejected.match(result) && (result.payload as { serverUnavailable?: boolean } | undefined)?.serverUnavailable) {
    // Máy chủ đang khởi động lại: giữ token, thử lại sau ít giây thay vì đưa về trang đăng nhập.
    if (!retryTimer) {
      retryTimer = setTimeout(() => {
        retryTimer = null;
        void dispatch(getSession());
      }, SESSION_RETRY_MS);
    }
    return;
  }

  const { account } = getState().authentication;
  if (account?.langKey) {
    const langKey = Storage.session.get('locale', account.langKey);
    await dispatch(setLocale(langKey));
  }
};

export const getAccount = createAsyncThunk(
  'authentication/get_account',
  async (_: void, { rejectWithValue }) => {
    try {
      return await axios.get<any>('api/account');
    } catch (err) {
      if (isServerUnavailable(err) && hasStoredToken()) {
        return rejectWithValue({ serverUnavailable: true });
      }
      throw err;
    }
  },
  {
    serializeError: serializeAxiosError,
  },
);

interface IAuthParams {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export const authenticate = createAsyncThunk(
  'authentication/login',
  async (auth: IAuthParams) => axios.post<any>('api/authenticate', auth),
  {
    serializeError: serializeAxiosError,
  },
);

export const login: (username: string, password: string, rememberMe?: boolean) => AppThunk =
  (username, password, rememberMe = false) =>
  async dispatch => {
    const result = await dispatch(authenticate({ username, password, rememberMe }));
    const response = result.payload as AxiosResponse;
    const bearerToken = response?.headers?.authorization;
    if (bearerToken?.startsWith('Bearer ')) {
      const jwt = bearerToken.slice(7, bearerToken.length);
      if (rememberMe) {
        Storage.local.set(AUTHENTICATION_TOKEN_KEY, jwt);
      } else {
        Storage.session.set(AUTHENTICATION_TOKEN_KEY, jwt);
      }
    }
    dispatch(getSession());
  };

export const clearAuthToken = () => {
  if (Storage.local.get(AUTHENTICATION_TOKEN_KEY)) {
    Storage.local.remove(AUTHENTICATION_TOKEN_KEY);
  }
  if (Storage.session.get(AUTHENTICATION_TOKEN_KEY)) {
    Storage.session.remove(AUTHENTICATION_TOKEN_KEY);
  }
};

export const logout: () => AppThunk = () => dispatch => {
  clearAuthToken();
  dispatch(logoutSession());
};

export const clearAuthentication = messageKey => dispatch => {
  clearAuthToken();
  dispatch(authError(messageKey));
  dispatch(clearAuth());
};

export const AuthenticationSlice = createSlice({
  name: 'authentication',
  initialState,
  reducers: {
    logoutSession() {
      return {
        ...initialState,
        showModalLogin: true,
      };
    },
    authError(state, action) {
      return {
        ...state,
        showModalLogin: true,
        redirectMessage: action.payload,
      };
    },
    clearAuth(state) {
      return {
        ...state,
        loading: false,
        showModalLogin: true,
        isAuthenticated: false,
      };
    },
  },
  extraReducers(builder) {
    builder
      .addCase(authenticate.rejected, (state, action) => {
        const response = (action.error as { response?: { status?: number; data?: { detail?: string } } }).response;
        return {
          ...initialState,
          errorMessage: action.error.message!,
          showModalLogin: true,
          loginError: true,
          loginErrorDetail:
            response?.status === 429 ? (response.data?.detail ?? 'Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.') : null,
        };
      })
      .addCase(authenticate.fulfilled, state => ({
        ...state,
        loading: false,
        loginError: false,
        showModalLogin: false,
        loginSuccess: true,
      }))
      .addCase(getAccount.rejected, (state, action) => {
        if ((action.payload as { serverUnavailable?: boolean } | undefined)?.serverUnavailable) {
          return { ...state, loading: false, serverUnavailable: true };
        }
        return {
          ...state,
          loading: false,
          isAuthenticated: false,
          sessionHasBeenFetched: true,
          serverUnavailable: false,
          showModalLogin: true,
          errorMessage: action.error.message!,
        };
      })
      .addCase(getAccount.fulfilled, (state, action) => {
        const isAuthenticated = action.payload?.data?.activated;
        return {
          ...state,
          isAuthenticated,
          loading: false,
          sessionHasBeenFetched: true,
          serverUnavailable: false,
          account: action.payload.data,
        };
      })
      .addCase(authenticate.pending, state => {
        state.loading = true;
      })
      .addCase(getAccount.pending, state => {
        state.loading = true;
      });
  },
});

export const { logoutSession, authError, clearAuth } = AuthenticationSlice.actions;

// Reducer
export default AuthenticationSlice.reducer;
