import reducer, { createEntity, deleteEntity, getEntity, partialUpdateEntity } from './description-normalization-rule-condition.reducer';

describe('descriptionNormalizationRuleCondition reducer', () => {
  it('returns initial state', () => {
    expect(reducer(undefined, { type: '' })).toMatchObject({
      loading: false,
      updating: false,
      updateSuccess: false,
      entities: [],
    });
  });

  it('tracks fetch loading and success', () => {
    let state = reducer(undefined, { type: getEntity.pending.type });
    expect(state.loading).toBe(true);
    state = reducer(state, { type: getEntity.fulfilled.type, payload: { data: { id: 1, value: 'Uber' } } });
    expect(state.loading).toBe(false);
    expect(state.entity).toMatchObject({ id: 1, value: 'Uber' });
  });

  it('tracks create/update/delete success', () => {
    let state = reducer(undefined, { type: createEntity.fulfilled.type, payload: { data: { id: 1 } } });
    expect(state.updateSuccess).toBe(true);
    state = reducer(state, { type: partialUpdateEntity.fulfilled.type, payload: { data: { id: 1, value: 'Lyft' } } });
    expect(state.entity).toMatchObject({ value: 'Lyft' });
    state = reducer(state, { type: deleteEntity.fulfilled.type });
    expect(state.entity).toEqual({});
  });
});
