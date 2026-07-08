import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialTransactions } from 'app/entities/financial-transaction/financial-transaction.reducer';
import { getEntities as getTransactionRules } from 'app/entities/transaction-rule/transaction-rule.reducer';
import { getEntities as getFinancialSubscriptions } from 'app/entities/financial-subscription/financial-subscription.reducer';
import { getEntities as getBudgets } from 'app/entities/budget/budget.reducer';
import { createEntity, getEntity, reset, updateEntity } from './tag.reducer';

export const TagUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialTransactions = useAppSelector(state => state.financialTransaction.entities);
  const transactionRules = useAppSelector(state => state.transactionRule.entities);
  const financialSubscriptions = useAppSelector(state => state.financialSubscription.entities);
  const budgets = useAppSelector(state => state.budget.entities);
  const tagEntity = useAppSelector(state => state.tag.entity);
  const loading = useAppSelector(state => state.tag.loading);
  const updating = useAppSelector(state => state.tag.updating);
  const updateSuccess = useAppSelector(state => state.tag.updateSuccess);

  const handleClose = () => {
    navigate('/tag');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialTransactions({}));
    dispatch(getTransactionRules({}));
    dispatch(getFinancialSubscriptions({}));
    dispatch(getBudgets({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...tagEntity,
      ...values,
      financialTransactions: mapIdList(values.financialTransactions),
      transactionRules: mapIdList(values.transactionRules),
      subscriptions: mapIdList(values.subscriptions),
      budgets: mapIdList(values.budgets),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          createdAt: displayDefaultDateTime(),
          updatedAt: displayDefaultDateTime(),
        }
      : {
          ...tagEntity,
          createdAt: convertDateTimeFromServer(tagEntity.createdAt),
          updatedAt: convertDateTimeFromServer(tagEntity.updatedAt),
          financialTransactions: tagEntity?.financialTransactions?.map(e => e.id.toString()),
          transactionRules: tagEntity?.transactionRules?.map(e => e.id.toString()),
          subscriptions: tagEntity?.subscriptions?.map(e => e.id.toString()),
          budgets: tagEntity?.budgets?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.tag.home.createOrEditLabel" data-cy="TagCreateUpdateHeading">
            <Translate contentKey="fintrackApp.tag.home.createOrEditLabel">Create or edit a Tag</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="tag-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.tag.name')}
                id="tag-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.tag.description')}
                id="tag-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  maxLength: { value: 250, message: translate('entity.validation.maxlength', { max: 250 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.tag.color')}
                id="tag-color"
                name="color"
                data-cy="color"
                type="text"
                validate={{
                  pattern: {
                    value: /^#[0-9A-Fa-f]{6}$/,
                    message: translate('entity.validation.pattern', { pattern: '^#[0-9A-Fa-f]{6}$' }),
                  },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.tag.active')}
                id="tag-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('fintrackApp.tag.createdAt')}
                id="tag-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.tag.updatedAt')}
                id="tag-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.tag.financialTransactions')}
                id="tag-financialTransactions"
                data-cy="financialTransactions"
                type="select"
                multiple
                name="financialTransactions"
              >
                <option value="" key="0" />
                {financialTransactions
                  ? financialTransactions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.tag.transactionRules')}
                id="tag-transactionRules"
                data-cy="transactionRules"
                type="select"
                multiple
                name="transactionRules"
              >
                <option value="" key="0" />
                {transactionRules
                  ? transactionRules.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.tag.subscriptions')}
                id="tag-subscriptions"
                data-cy="subscriptions"
                type="select"
                multiple
                name="subscriptions"
              >
                <option value="" key="0" />
                {financialSubscriptions
                  ? financialSubscriptions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.tag.budgets')}
                id="tag-budgets"
                data-cy="budgets"
                type="select"
                multiple
                name="budgets"
              >
                <option value="" key="0" />
                {budgets
                  ? budgets.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/tag" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default TagUpdate;
