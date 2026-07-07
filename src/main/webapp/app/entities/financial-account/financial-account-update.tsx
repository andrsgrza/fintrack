import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getUsers } from 'app/modules/administration/user-management/user-management.reducer';
import { getEntities as getBudgets } from 'app/entities/budget/budget.reducer';
import { getEntities as getTransactionIngestions } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { getEntities as getApiAccessTokens } from 'app/entities/api-access-token/api-access-token.reducer';
import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';
import { createEntity, getEntity, reset, updateEntity } from './financial-account.reducer';

export const FinancialAccountUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const users = useAppSelector(state => state.userManagement.users);
  const budgets = useAppSelector(state => state.budget.entities);
  const transactionIngestions = useAppSelector(state => state.transactionIngestion.entities);
  const apiAccessTokens = useAppSelector(state => state.apiAccessToken.entities);
  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  const loading = useAppSelector(state => state.financialAccount.loading);
  const updating = useAppSelector(state => state.financialAccount.updating);
  const updateSuccess = useAppSelector(state => state.financialAccount.updateSuccess);
  const accountTypeValues = Object.keys(AccountType);
  const currencyCodeValues = Object.keys(CurrencyCode);

  const handleClose = () => {
    navigate('/financial-account');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getUsers({}));
    dispatch(getBudgets({}));
    dispatch(getTransactionIngestions({}));
    dispatch(getApiAccessTokens({}));
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
    if (values.initialBalance !== undefined && typeof values.initialBalance !== 'number') {
      values.initialBalance = Number(values.initialBalance);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...financialAccountEntity,
      ...values,
      user: users.find(it => it.id.toString() === values.user?.toString()),
      budgets: mapIdList(values.budgets),
      transactionIngestions: mapIdList(values.transactionIngestions),
      apiAccessTokens: mapIdList(values.apiAccessTokens),
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
          accountType: 'DEBIT',
          currency: 'MXN',
          ...financialAccountEntity,
          createdAt: convertDateTimeFromServer(financialAccountEntity.createdAt),
          updatedAt: convertDateTimeFromServer(financialAccountEntity.updatedAt),
          user: financialAccountEntity?.user?.id,
          budgets: financialAccountEntity?.budgets?.map(e => e.id.toString()),
          transactionIngestions: financialAccountEntity?.transactionIngestions?.map(e => e.id.toString()),
          apiAccessTokens: financialAccountEntity?.apiAccessTokens?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.financialAccount.home.createOrEditLabel" data-cy="FinancialAccountCreateUpdateHeading">
            <Translate contentKey="fintrackApp.financialAccount.home.createOrEditLabel">Create or edit a FinancialAccount</Translate>
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
                  id="financial-account-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.financialAccount.name')}
                id="financial-account-name"
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
                label={translate('fintrackApp.financialAccount.institutionName')}
                id="financial-account-institutionName"
                name="institutionName"
                data-cy="institutionName"
                type="text"
                validate={{
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.accountType')}
                id="financial-account-accountType"
                name="accountType"
                data-cy="accountType"
                type="select"
              >
                {accountTypeValues.map(accountType => (
                  <option value={accountType} key={accountType}>
                    {translate(`fintrackApp.AccountType.${accountType}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialAccount.currency')}
                id="financial-account-currency"
                name="currency"
                data-cy="currency"
                type="select"
              >
                {currencyCodeValues.map(currencyCode => (
                  <option value={currencyCode} key={currencyCode}>
                    {translate(`fintrackApp.CurrencyCode.${currencyCode}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialAccount.initialBalance')}
                id="financial-account-initialBalance"
                name="initialBalance"
                data-cy="initialBalance"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.initialBalanceDate')}
                id="financial-account-initialBalanceDate"
                name="initialBalanceDate"
                data-cy="initialBalanceDate"
                type="date"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.lastFourDigits')}
                id="financial-account-lastFourDigits"
                name="lastFourDigits"
                data-cy="lastFourDigits"
                type="text"
                validate={{
                  pattern: { value: /^[0-9]{4}$/, message: translate('entity.validation.pattern', { pattern: '^[0-9]{4}$' }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.description')}
                id="financial-account-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.color')}
                id="financial-account-color"
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
                label={translate('fintrackApp.financialAccount.icon')}
                id="financial-account-icon"
                name="icon"
                data-cy="icon"
                type="text"
                validate={{
                  maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.active')}
                id="financial-account-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.includeInNetWorth')}
                id="financial-account-includeInNetWorth"
                name="includeInNetWorth"
                data-cy="includeInNetWorth"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.createdAt')}
                id="financial-account-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.updatedAt')}
                id="financial-account-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="financial-account-user"
                name="user"
                data-cy="user"
                label={translate('fintrackApp.financialAccount.user')}
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
                label={translate('fintrackApp.financialAccount.budgets')}
                id="financial-account-budgets"
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
              <ValidatedField
                label={translate('fintrackApp.financialAccount.transactionIngestions')}
                id="financial-account-transactionIngestions"
                data-cy="transactionIngestions"
                type="select"
                multiple
                name="transactionIngestions"
              >
                <option value="" key="0" />
                {transactionIngestions
                  ? transactionIngestions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialAccount.apiAccessTokens')}
                id="financial-account-apiAccessTokens"
                data-cy="apiAccessTokens"
                type="select"
                multiple
                name="apiAccessTokens"
              >
                <option value="" key="0" />
                {apiAccessTokens
                  ? apiAccessTokens.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-account" replace color="info">
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

export default FinancialAccountUpdate;
