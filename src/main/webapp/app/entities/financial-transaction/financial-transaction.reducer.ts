import axios from 'axios';
import { createAsyncThunk, isFulfilled, isPending } from '@reduxjs/toolkit';
import { cleanEntity } from 'app/shared/util/entity-utils';
import { EntityState, IQueryParams, createEntitySlice, isRejectedAction, serializeAxiosError } from 'app/shared/reducers/reducer.utils';
import { IFinancialTransaction, defaultValue } from 'app/shared/model/financial-transaction.model';

const initialState = {
  loading: false,
  errorMessage: null,
  entities: [],
  entity: defaultValue,
  updating: false,
  totalItems: 0,
  updateSuccess: false,
  ingestionRecordParentCandidates: [] as IFinancialTransaction[],
  loadingIngestionRecordParentCandidates: false,
  currentListRequestId: null as string | null,
};

export type FinancialTransactionState = typeof initialState;

const apiUrl = 'api/financial-transactions';

// Actions

export const getEntities = createAsyncThunk(
  'financialTransaction/fetch_entity_list',
  async ({ page, size, sort, query }: IQueryParams) => {
    const queryParameters = new URLSearchParams(query);
    if (sort) {
      queryParameters.set('page', String(page));
      queryParameters.set('size', String(size));
      queryParameters.set('sort', sort);
    }
    queryParameters.set('cacheBuster', String(new Date().getTime()));
    const requestUrl = `${apiUrl}?${queryParameters.toString()}`;
    return axios.get<IFinancialTransaction[]>(requestUrl);
  },
  { serializeError: serializeAxiosError },
);

export const getEntity = createAsyncThunk(
  'financialTransaction/fetch_entity',
  async (id: string | number) => {
    const requestUrl = `${apiUrl}/${id}`;
    return axios.get<IFinancialTransaction>(requestUrl);
  },
  { serializeError: serializeAxiosError },
);

export const createEntity = createAsyncThunk(
  'financialTransaction/create_entity',
  async (entity: IFinancialTransaction, thunkAPI) => {
    const result = await axios.post<IFinancialTransaction>(apiUrl, cleanEntity(entity));
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const updateEntity = createAsyncThunk(
  'financialTransaction/update_entity',
  async (entity: IFinancialTransaction, thunkAPI) => {
    const result = await axios.put<IFinancialTransaction>(`${apiUrl}/${entity.id}`, cleanEntity(entity));
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const partialUpdateEntity = createAsyncThunk(
  'financialTransaction/partial_update_entity',
  async (entity: IFinancialTransaction, thunkAPI) => {
    const result = await axios.patch<IFinancialTransaction>(`${apiUrl}/${entity.id}`, cleanEntity(entity));
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const getEntitiesWhereIngestionRecordIsNull = createAsyncThunk(
  'financialTransaction/fetch_ingestion_record_parent_candidates',
  async () => axios.get<IFinancialTransaction[]>(`${apiUrl}/ingestion-record-is-null`),
  { serializeError: serializeAxiosError },
);

export const deleteEntity = createAsyncThunk(
  'financialTransaction/delete_entity',
  async (id: string | number, thunkAPI) => {
    const requestUrl = `${apiUrl}/${id}`;
    const result = await axios.delete<IFinancialTransaction>(requestUrl);
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

// slice

export const FinancialTransactionSlice = createEntitySlice({
  name: 'financialTransaction',
  initialState: initialState as EntityState<IFinancialTransaction>,
  extraReducers(builder) {
    builder
      .addCase(getEntities.pending, (state, action) => {
        const typedState = state as FinancialTransactionState;
        typedState.errorMessage = null;
        typedState.updateSuccess = false;
        typedState.loading = true;
        typedState.currentListRequestId = action.meta?.requestId ?? null;
      })
      .addCase(getEntities.fulfilled, (state, action) => {
        const typedState = state as FinancialTransactionState;
        const requestId = action.meta?.requestId;
        if (requestId && typedState.currentListRequestId !== requestId) {
          return;
        }
        const { data, headers } = action.payload;
        typedState.loading = false;
        typedState.currentListRequestId = null;
        typedState.entities = data;
        typedState.totalItems = parseInt(headers['x-total-count'], 10);
      })
      .addCase(getEntities.rejected, (state, action) => {
        const typedState = state as FinancialTransactionState;
        const requestId = action.meta?.requestId;
        if (!requestId || typedState.currentListRequestId === requestId) {
          typedState.loading = false;
          typedState.currentListRequestId = null;
        }
      })
      .addCase(getEntity.fulfilled, (state, action) => {
        state.loading = false;
        state.entity = action.payload.data;
      })
      .addCase(deleteEntity.fulfilled, state => {
        state.updating = false;
        state.updateSuccess = true;
        state.entity = {};
      })
      .addCase(getEntitiesWhereIngestionRecordIsNull.pending, state => {
        (state as FinancialTransactionState).loadingIngestionRecordParentCandidates = true;
      })
      .addCase(getEntitiesWhereIngestionRecordIsNull.fulfilled, (state, action) => {
        const typedState = state as FinancialTransactionState;
        typedState.loadingIngestionRecordParentCandidates = false;
        typedState.ingestionRecordParentCandidates = action.payload.data;
      })
      .addCase(getEntitiesWhereIngestionRecordIsNull.rejected, state => {
        (state as FinancialTransactionState).loadingIngestionRecordParentCandidates = false;
      })
      .addMatcher(isFulfilled(createEntity, updateEntity, partialUpdateEntity), (state, action) => {
        state.updating = false;
        state.loading = false;
        state.updateSuccess = true;
        state.entity = action.payload.data;
      })
      .addMatcher(isPending(getEntity), state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.loading = true;
      })
      .addMatcher(isPending(createEntity, updateEntity, partialUpdateEntity, deleteEntity), state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.updating = true;
      })
      .addMatcher(isRejectedAction, (state, action) => {
        if (action.type === getEntities.rejected.type) {
          return;
        }
        state.loading = false;
        state.updating = false;
        state.updateSuccess = false;
        state.errorMessage = null;
      });
  },
  skipRejectionHandling: true,
});

export const { reset } = FinancialTransactionSlice.actions;

// Reducer
export default FinancialTransactionSlice.reducer;
