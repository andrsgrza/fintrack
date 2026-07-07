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
import { BudgetPeriod } from 'app/shared/model/enumerations/budget-period.model';
import { BudgetStatus } from 'app/shared/model/enumerations/budget-status.model';
import { TagMatchMode } from 'app/shared/model/enumerations/tag-match-mode.model';
import { createEntity, getEntity, reset, updateEntity } from './budget.reducer';

export const BudgetUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const users = useAppSelector(state => state.userManagement.users);
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const budgetEntity = useAppSelector(state => state.budget.entity);
  const loading = useAppSelector(state => state.budget.loading);
  const updating = useAppSelector(state => state.budget.updating);
  const updateSuccess = useAppSelector(state => state.budget.updateSuccess);
  const budgetPeriodValues = Object.keys(BudgetPeriod);
  const budgetStatusValues = Object.keys(BudgetStatus);
  const tagMatchModeValues = Object.keys(TagMatchMode);

  const handleClose = () => {
    navigate('/budget');
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
    if (values.amount !== undefined && typeof values.amount !== 'number') {
      values.amount = Number(values.amount);
    }
    if (values.warningPercentage !== undefined && typeof values.warningPercentage !== 'number') {
      values.warningPercentage = Number(values.warningPercentage);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...budgetEntity,
      ...values,
      user: users.find(it => it.id.toString() === values.user?.toString()),
      accounts: mapIdList(values.accounts),
      categories: mapIdList(values.categories),
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
          period: 'WEEKLY',
          status: 'ACTIVE',
          tagMatchMode: 'ANY',
          ...budgetEntity,
          createdAt: convertDateTimeFromServer(budgetEntity.createdAt),
          updatedAt: convertDateTimeFromServer(budgetEntity.updatedAt),
          user: budgetEntity?.user?.id,
          accounts: budgetEntity?.accounts?.map(e => e.id.toString()),
          categories: budgetEntity?.categories?.map(e => e.id.toString()),
          tags: budgetEntity?.tags?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.budget.home.createOrEditLabel" data-cy="BudgetCreateUpdateHeading">
            <Translate contentKey="fintrackApp.budget.home.createOrEditLabel">Create or edit a Budget</Translate>
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
                  id="budget-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.budget.name')}
                id="budget-name"
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
                label={translate('fintrackApp.budget.amount')}
                id="budget-amount"
                name="amount"
                data-cy="amount"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.budget.currency')}
                id="budget-currency"
                name="currency"
                data-cy="currency"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  pattern: { value: /^[A-Z]{3}$/, message: translate('entity.validation.pattern', { pattern: '^[A-Z]{3}$' }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.budget.period')}
                id="budget-period"
                name="period"
                data-cy="period"
                type="select"
              >
                {budgetPeriodValues.map(budgetPeriod => (
                  <option value={budgetPeriod} key={budgetPeriod}>
                    {translate(`fintrackApp.BudgetPeriod.${budgetPeriod}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.budget.startDate')}
                id="budget-startDate"
                name="startDate"
                data-cy="startDate"
                type="date"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.budget.endDate')}
                id="budget-endDate"
                name="endDate"
                data-cy="endDate"
                type="date"
              />
              <ValidatedField
                label={translate('fintrackApp.budget.status')}
                id="budget-status"
                name="status"
                data-cy="status"
                type="select"
              >
                {budgetStatusValues.map(budgetStatus => (
                  <option value={budgetStatus} key={budgetStatus}>
                    {translate(`fintrackApp.BudgetStatus.${budgetStatus}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.budget.tagMatchMode')}
                id="budget-tagMatchMode"
                name="tagMatchMode"
                data-cy="tagMatchMode"
                type="select"
              >
                {tagMatchModeValues.map(tagMatchMode => (
                  <option value={tagMatchMode} key={tagMatchMode}>
                    {translate(`fintrackApp.TagMatchMode.${tagMatchMode}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.budget.warningPercentage')}
                id="budget-warningPercentage"
                name="warningPercentage"
                data-cy="warningPercentage"
                type="text"
                validate={{
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  max: { value: 100, message: translate('entity.validation.max', { max: 100 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.budget.createdAt')}
                id="budget-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.budget.updatedAt')}
                id="budget-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="budget-user"
                name="user"
                data-cy="user"
                label={translate('fintrackApp.budget.user')}
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
                label={translate('fintrackApp.budget.accounts')}
                id="budget-accounts"
                data-cy="accounts"
                type="select"
                multiple
                name="accounts"
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
                label={translate('fintrackApp.budget.categories')}
                id="budget-categories"
                data-cy="categories"
                type="select"
                multiple
                name="categories"
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
                label={translate('fintrackApp.budget.tags')}
                id="budget-tags"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/budget" replace color="info">
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

export default BudgetUpdate;
