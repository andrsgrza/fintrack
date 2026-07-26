import axios from 'axios';
import { createAsyncThunk, isFulfilled, isPending } from '@reduxjs/toolkit';
import { cleanEntity } from 'app/shared/util/entity-utils';
import { EntityState, createEntitySlice, serializeAxiosError } from 'app/shared/reducers/reducer.utils';
import { IDescriptionNormalizationRuleCondition, defaultValue } from 'app/shared/model/description-normalization-rule-condition.model';

const initialState: EntityState<IDescriptionNormalizationRuleCondition> = {
  loading: false,
  errorMessage: null,
  entities: [],
  entity: defaultValue,
  updating: false,
  updateSuccess: false,
};

const apiUrl = 'api/description-normalization-rule-conditions';

export const getEntity = createAsyncThunk(
  'descriptionNormalizationRuleCondition/fetch_entity',
  async (id: string | number) => axios.get<IDescriptionNormalizationRuleCondition>(`${apiUrl}/${id}`),
  { serializeError: serializeAxiosError },
);

export const createEntity = createAsyncThunk(
  'descriptionNormalizationRuleCondition/create_entity',
  async (entity: IDescriptionNormalizationRuleCondition) => axios.post<IDescriptionNormalizationRuleCondition>(apiUrl, cleanEntity(entity)),
  { serializeError: serializeAxiosError },
);

export const partialUpdateEntity = createAsyncThunk(
  'descriptionNormalizationRuleCondition/partial_update_entity',
  async (entity: IDescriptionNormalizationRuleCondition) =>
    axios.patch<IDescriptionNormalizationRuleCondition>(`${apiUrl}/${entity.id}`, cleanEntity(entity)),
  { serializeError: serializeAxiosError },
);

export const deleteEntity = createAsyncThunk(
  'descriptionNormalizationRuleCondition/delete_entity',
  async (id: string | number) => axios.delete<IDescriptionNormalizationRuleCondition>(`${apiUrl}/${id}`),
  { serializeError: serializeAxiosError },
);

export const DescriptionNormalizationRuleConditionSlice = createEntitySlice({
  name: 'descriptionNormalizationRuleCondition',
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
      .addMatcher(isFulfilled(createEntity, partialUpdateEntity), (state, action) => {
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
      .addMatcher(isPending(createEntity, partialUpdateEntity, deleteEntity), state => {
        state.errorMessage = null;
        state.updateSuccess = false;
        state.updating = true;
      });
  },
});

export const { reset } = DescriptionNormalizationRuleConditionSlice.actions;
export default DescriptionNormalizationRuleConditionSlice.reducer;
