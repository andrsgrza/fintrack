import axios from 'axios';
import { createAsyncThunk, isFulfilled, isPending } from '@reduxjs/toolkit';
import { ASC } from 'app/shared/util/pagination.constants';
import { cleanEntity } from 'app/shared/util/entity-utils';
import { EntityState, IQueryParams, createEntitySlice, serializeAxiosError } from 'app/shared/reducers/reducer.utils';
import { IDescriptionNormalizationRule, defaultValue } from 'app/shared/model/description-normalization-rule.model';

const initialState: EntityState<IDescriptionNormalizationRule> = {
  loading: false,
  errorMessage: null,
  entities: [],
  entity: defaultValue,
  updating: false,
  updateSuccess: false,
};

const apiUrl = 'api/description-normalization-rules';

export const getEntities = createAsyncThunk(
  'descriptionNormalizationRule/fetch_entity_list',
  async ({ sort }: IQueryParams) => {
    const requestUrl = `${apiUrl}?${sort ? `sort=${sort}&` : ''}cacheBuster=${new Date().getTime()}`;
    return axios.get<IDescriptionNormalizationRule[]>(requestUrl);
  },
  { serializeError: serializeAxiosError },
);

export const getEntity = createAsyncThunk(
  'descriptionNormalizationRule/fetch_entity',
  async (id: string | number) => axios.get<IDescriptionNormalizationRule>(`${apiUrl}/${id}`),
  { serializeError: serializeAxiosError },
);

export const createEntity = createAsyncThunk(
  'descriptionNormalizationRule/create_entity',
  async (entity: IDescriptionNormalizationRule, thunkAPI) => {
    const result = await axios.post<IDescriptionNormalizationRule>(apiUrl, cleanEntity(entity));
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const partialUpdateEntity = createAsyncThunk(
  'descriptionNormalizationRule/partial_update_entity',
  async (entity: IDescriptionNormalizationRule, thunkAPI) => {
    const result = await axios.patch<IDescriptionNormalizationRule>(`${apiUrl}/${entity.id}`, cleanEntity(entity));
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const deleteEntity = createAsyncThunk(
  'descriptionNormalizationRule/delete_entity',
  async (id: string | number, thunkAPI) => {
    const result = await axios.delete<IDescriptionNormalizationRule>(`${apiUrl}/${id}`);
    thunkAPI.dispatch(getEntities({}));
    return result;
  },
  { serializeError: serializeAxiosError },
);

export const DescriptionNormalizationRuleSlice = createEntitySlice({
  name: 'descriptionNormalizationRule',
  initialState,
  extraReducers(builder) {
    builder
      .addCase(getEntity.fulfilled, (state, action) => {
        state.loading = false;
        state.entity = action.payload.data;
      })
      .addCase(deleteEntity.fulfilled, state => {
        state.updating = false;
        state.updateSuccess = true;
        state.entity = {};
      })
      .addMatcher(isFulfilled(getEntities), (state, action) => {
        const { data } = action.payload;
        return {
          ...state,
          loading: false,
          entities: data.sort((a, b) => {
            if (!action.meta?.arg?.sort) return 1;
            const order = action.meta.arg.sort.split(',')[1];
            const predicate = action.meta.arg.sort.split(',')[0];
            return order === ASC ? (a[predicate] < b[predicate] ? -1 : 1) : b[predicate] < a[predicate] ? -1 : 1;
          }),
        };
      })
      .addMatcher(isFulfilled(createEntity, partialUpdateEntity), (state, action) => {
        state.updating = false;
        state.loading = false;
        state.updateSuccess = true;
        state.entity = action.payload.data;
      })
      .addMatcher(isPending(getEntities, getEntity), state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.loading = true;
      })
      .addMatcher(isPending(createEntity, partialUpdateEntity, deleteEntity), state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.updating = true;
      });
  },
});

export const { reset } = DescriptionNormalizationRuleSlice.actions;
export default DescriptionNormalizationRuleSlice.reducer;
