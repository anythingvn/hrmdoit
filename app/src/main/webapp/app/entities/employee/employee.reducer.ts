import axios from 'axios';
import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { IEmployee } from 'app/shared/model/employee.model';
import { cleanEntity } from 'app/shared/util/entity-utils';
import { serializeAxiosError } from 'app/shared/reducers/reducer.utils';

export const apiUrl = 'api/employees';

export interface IEmployeeFilters {
  q?: string;
  status?: string;
  departmentId?: string;
  currentSiteId?: string;
  currentContractType?: string;
  personType?: string;
}

export interface IEmployeeQuery extends IEmployeeFilters {
  page?: number;
  size?: number;
  sort?: string;
}

/** Tham số lọc theo cú pháp criteria của JHipster; bỏ qua ô để trống. */
export const filterParams = (f: IEmployeeFilters): Record<string, string> => {
  const p: Record<string, string> = {};
  const q = f.q?.trim();
  if (q) p['q.contains'] = q;
  if (f.status) p['status.equals'] = f.status;
  if (f.departmentId) p['departmentId.equals'] = f.departmentId;
  if (f.currentSiteId) p['currentSiteId.equals'] = f.currentSiteId;
  if (f.currentContractType) p['currentContractType.equals'] = f.currentContractType;
  if (f.personType) p['personType.equals'] = f.personType;
  return p;
};

const toQueryString = (params: Record<string, string | number>) =>
  Object.entries(params)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');

export const getEntities = createAsyncThunk(
  'employee/fetch_entity_list',
  async (query: IEmployeeQuery) => {
    const params: Record<string, string | number> = { ...filterParams(query), page: query.page ?? 0, size: query.size ?? 20 };
    if (query.sort) params.sort = query.sort;
    return axios.get<IEmployee[]>(`${apiUrl}?${toQueryString(params)}`);
  },
  { serializeError: serializeAxiosError },
);

export const getEntity = createAsyncThunk('employee/fetch_entity', async (id: string | number) => axios.get<IEmployee>(`${apiUrl}/${id}`), {
  serializeError: serializeAxiosError,
});

export const createEntity = createAsyncThunk(
  'employee/create_entity',
  async (entity: IEmployee) => axios.post<IEmployee>(apiUrl, cleanEntity(entity)),
  { serializeError: serializeAxiosError },
);

export const updateEntity = createAsyncThunk(
  'employee/update_entity',
  async (entity: IEmployee) => axios.put<IEmployee>(`${apiUrl}/${entity.id}`, cleanEntity(entity)),
  { serializeError: serializeAxiosError },
);

/** Xuất Excel theo bộ lọc hiện tại. Chỉ chuyển tiếp sort nằm trong danh sách cho phép (mã, họ tên). */
export const exportParams = (query: IEmployeeFilters & { sort?: string }): Record<string, string> => {
  const p = filterParams(query);
  const field = query.sort?.split(',')[0];
  if (field === 'code' || field === 'fullName') {
    p.sort = query.sort;
  }
  return p;
};

export const exportEntities = (query: IEmployeeFilters & { sort?: string }) =>
  axios.get<Blob>(`${apiUrl}/export.xlsx?${toQueryString(exportParams(query))}`, { responseType: 'blob' });

interface IState {
  loading: boolean;
  updating: boolean;
  updateSuccess: boolean;
  errorMessage: string | null;
  entities: IEmployee[];
  entity: IEmployee;
  totalItems: number;
}

const initialState: IState = {
  loading: false,
  updating: false,
  updateSuccess: false,
  errorMessage: null,
  entities: [],
  entity: {},
  totalItems: 0,
};

export const EmployeeSlice = createSlice({
  name: 'employee',
  initialState,
  reducers: {
    reset: () => initialState,
  },
  extraReducers(builder) {
    builder
      .addCase(getEntities.pending, state => {
        state.loading = true;
        state.errorMessage = null;
      })
      .addCase(getEntities.fulfilled, (state, action) => {
        state.loading = false;
        state.entities = action.payload.data;
        state.totalItems = parseInt(String(action.payload.headers?.['x-total-count'] ?? action.payload.data.length), 10);
      })
      .addCase(getEntities.rejected, (state, action) => {
        state.loading = false;
        state.errorMessage = action.error.message ?? 'Lỗi';
      })
      .addCase(getEntity.pending, state => {
        state.loading = true;
        state.updateSuccess = false;
      })
      .addCase(getEntity.fulfilled, (state, action) => {
        state.loading = false;
        state.entity = action.payload.data;
      })
      .addCase(getEntity.rejected, state => {
        state.loading = false;
      })
      .addCase(createEntity.pending, state => {
        state.updating = true;
        state.updateSuccess = false;
      })
      .addCase(updateEntity.pending, state => {
        state.updating = true;
        state.updateSuccess = false;
      })
      .addCase(createEntity.fulfilled, (state, action) => {
        state.updating = false;
        state.updateSuccess = true;
        state.entity = action.payload.data;
      })
      .addCase(updateEntity.fulfilled, (state, action) => {
        state.updating = false;
        state.updateSuccess = true;
        state.entity = action.payload.data;
      })
      .addCase(createEntity.rejected, state => {
        state.updating = false;
      })
      .addCase(updateEntity.rejected, state => {
        state.updating = false;
      });
  },
});

export const { reset } = EmployeeSlice.actions;
export default EmployeeSlice.reducer;
