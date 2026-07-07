import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getUsers } from 'app/modules/administration/user-management/user-management.reducer';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { SubscriptionStatus } from 'app/shared/model/enumerations/subscription-status.model';
import { RecurrenceUnit } from 'app/shared/model/enumerations/recurrence-unit.model';
import { createEntity, getEntity, reset, updateEntity } from './financial-subscription.reducer';

export const FinancialSubscriptionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const users = useAppSelector(state => state.userManagement.users);
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const financialSubscriptionEntity = useAppSelector(state => state.financialSubscription.entity);
  const loading = useAppSelector(state => state.financialSubscription.loading);
  const updating = useAppSelector(state => state.financialSubscription.updating);
  const updateSuccess = useAppSelector(state => state.financialSubscription.updateSuccess);
  const subscriptionStatusValues = Object.keys(SubscriptionStatus);
  const recurrenceUnitValues = Object.keys(RecurrenceUnit);

  const handleClose = () => {
    navigate('/financial-subscription');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getUsers({}));
    dispatch(getFinancialAccounts({}));
    dispatch(getCategories({}));
    dispatch(getTags({}));
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
    if (values.expectedAmount !== undefined && typeof values.expectedAmount !== 'number') {
      values.expectedAmount = Number(values.expectedAmount);
    }
    if (values.amountTolerancePercentage !== undefined && typeof values.amountTolerancePercentage !== 'number') {
      values.amountTolerancePercentage = Number(values.amountTolerancePercentage);
    }
    if (values.intervalCount !== undefined && typeof values.intervalCount !== 'number') {
      values.intervalCount = Number(values.intervalCount);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...financialSubscriptionEntity,
      ...values,
      user: users.find(it => it.id.toString() === values.user?.toString()),
      account: financialAccounts.find(it => it.id.toString() === values.account?.toString()),
      category: categories.find(it => it.id.toString() === values.category?.toString()),
      tags: mapIdList(values.tags),
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
          status: 'ACTIVE',
          recurrenceUnit: 'DAY',
          ...financialSubscriptionEntity,
          createdAt: convertDateTimeFromServer(financialSubscriptionEntity.createdAt),
          updatedAt: convertDateTimeFromServer(financialSubscriptionEntity.updatedAt),
          user: financialSubscriptionEntity?.user?.id,
          account: financialSubscriptionEntity?.account?.id,
          category: financialSubscriptionEntity?.category?.id,
          tags: financialSubscriptionEntity?.tags?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.financialSubscription.home.createOrEditLabel" data-cy="FinancialSubscriptionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.financialSubscription.home.createOrEditLabel">
              Create or edit a FinancialSubscription
            </Translate>
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
                  id="financial-subscription-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.name')}
                id="financial-subscription-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.description')}
                id="financial-subscription-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.status')}
                id="financial-subscription-status"
                name="status"
                data-cy="status"
                type="select"
              >
                {subscriptionStatusValues.map(subscriptionStatus => (
                  <option value={subscriptionStatus} key={subscriptionStatus}>
                    {translate(`fintrackApp.SubscriptionStatus.${subscriptionStatus}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.expectedAmount')}
                id="financial-subscription-expectedAmount"
                name="expectedAmount"
                data-cy="expectedAmount"
                type="text"
                validate={{
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.amountTolerancePercentage')}
                id="financial-subscription-amountTolerancePercentage"
                name="amountTolerancePercentage"
                data-cy="amountTolerancePercentage"
                type="text"
                validate={{
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  max: { value: 100, message: translate('entity.validation.max', { max: 100 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.currency')}
                id="financial-subscription-currency"
                name="currency"
                data-cy="currency"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  pattern: { value: /^[A-Z]{3}$/, message: translate('entity.validation.pattern', { pattern: '^[A-Z]{3}$' }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.recurrenceUnit')}
                id="financial-subscription-recurrenceUnit"
                name="recurrenceUnit"
                data-cy="recurrenceUnit"
                type="select"
              >
                {recurrenceUnitValues.map(recurrenceUnit => (
                  <option value={recurrenceUnit} key={recurrenceUnit}>
                    {translate(`fintrackApp.RecurrenceUnit.${recurrenceUnit}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.intervalCount')}
                id="financial-subscription-intervalCount"
                name="intervalCount"
                data-cy="intervalCount"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 1, message: translate('entity.validation.min', { min: 1 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.startDate')}
                id="financial-subscription-startDate"
                name="startDate"
                data-cy="startDate"
                type="date"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.nextExpectedDate')}
                id="financial-subscription-nextExpectedDate"
                name="nextExpectedDate"
                data-cy="nextExpectedDate"
                type="date"
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.endDate')}
                id="financial-subscription-endDate"
                name="endDate"
                data-cy="endDate"
                type="date"
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.automaticPayment')}
                id="financial-subscription-automaticPayment"
                name="automaticPayment"
                data-cy="automaticPayment"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.notes')}
                id="financial-subscription-notes"
                name="notes"
                data-cy="notes"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.createdAt')}
                id="financial-subscription-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.updatedAt')}
                id="financial-subscription-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="financial-subscription-user"
                name="user"
                data-cy="user"
                label={translate('fintrackApp.financialSubscription.user')}
                type="select"
                required
              >
                <option value="" key="0" />
                {users
                  ? users.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.login}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <ValidatedField
                id="financial-subscription-account"
                name="account"
                data-cy="account"
                label={translate('fintrackApp.financialSubscription.account')}
                type="select"
              >
                <option value="" key="0" />
                {financialAccounts
                  ? financialAccounts.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="financial-subscription-category"
                name="category"
                data-cy="category"
                label={translate('fintrackApp.financialSubscription.category')}
                type="select"
              >
                <option value="" key="0" />
                {categories
                  ? categories.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialSubscription.tags')}
                id="financial-subscription-tags"
                data-cy="tags"
                type="select"
                multiple
                name="tags"
              >
                <option value="" key="0" />
                {tags
                  ? tags.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-subscription" replace color="info">
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

export default FinancialSubscriptionUpdate;
