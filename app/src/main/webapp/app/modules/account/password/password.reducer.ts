import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import axios from 'axios';

import { serializeAxiosError } from 'app/shared/reducers/reducer.utils';

const initialState = {
  loading: false,
  errorMessage: null as string | null,
  successMessage: null as string | null,
  updateSuccess: false,
  updateFailure: false,
};

export type PasswordState = Readonly<typeof initialState>;

const apiUrl = 'api/account';

/** Câu báo khi mật khẩu mới trùng mật khẩu mặc định (server trả 400 error.defaultpassword). */
export const DEFAULT_PASSWORD_ERROR = 'Mật khẩu mới không được trùng mật khẩu mặc định';

interface IPassword {
  currentPassword: string;
  newPassword: string;
}

// Actions

export const savePassword = createAsyncThunk(
  'password/update_password',
  async (password: IPassword) => axios.post(`${apiUrl}/change-password`, password),
  { serializeError: serializeAxiosError },
);

export const PasswordSlice = createSlice({
  name: 'password',
  initialState,
  reducers: {
    reset() {
      return initialState;
    },
  },
  extraReducers(builder) {
    builder
      .addCase(savePassword.pending, state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.loading = true;
      })
      .addCase(savePassword.rejected, (state, action) => {
        state.loading = false;
        state.updateSuccess = false;
        state.updateFailure = true;
        const key = (action.error as any)?.response?.data?.message;
        state.errorMessage = key === 'error.defaultpassword' ? DEFAULT_PASSWORD_ERROR : 'password.messages.error';
      })
      .addCase(savePassword.fulfilled, state => {
        state.loading = false;
        state.updateSuccess = true;
        state.updateFailure = false;
        state.successMessage = 'password.messages.success';
      });
  },
});

export const { reset } = PasswordSlice.actions;

// Reducer
export default PasswordSlice.reducer;
