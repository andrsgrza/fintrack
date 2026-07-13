import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';
import { createEntity, getEntity, reset, updateEntity } from './financial-account.reducer';
import {
  createEntity as createCreditAccountDetails,
  getEntityByAccountId as getCreditAccountDetailsByAccountId,
  reset as resetCreditAccountDetails,
  updateEntity as updateCreditAccountDetails,
} from 'app/entities/credit-account-details/credit-account-details.reducer';
import { getInitialBalanceHelpKey, getInitialBalanceLabelKey } from './financial-account-labels';
import CreditCardDetailsFormSection, { creditCardDetailsFieldNames } from './components/credit-card-details-form-section';
import { ICreditAccountDetails } from 'app/shared/model/credit-account-details.model';

const resetOpeningPositionFields = (form: HTMLFormElement | null) => {
  if (!form) {
    return;
  }
  ['initialBalance', 'initialBalanceDate'].forEach(fieldName => {
    const field = form.elements.namedItem(fieldName) as HTMLInputElement | null;
    if (field) {
      field.value = '';
      field.dispatchEvent(new Event('input', { bubbles: true }));
      field.dispatchEvent(new Event('change', { bubbles: true }));
    }
  });
};

export const FinancialAccountUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  const creditAccountDetailsEntity = useAppSelector(state => state.creditAccountDetails.entity);
  const loading = useAppSelector(state => state.financialAccount.loading);
  const creditAccountDetailsLoading = useAppSelector(state => state.creditAccountDetails.loading);
  const updating = useAppSelector(state => state.financialAccount.updating);
  const accountTypeValues = Object.keys(AccountType);
  const currencyCodeValues = Object.keys(CurrencyCode);
  const [selectedAccountType, setSelectedAccountType] = useState<keyof typeof AccountType>('DEBIT');
  const [compositionError, setCompositionError] = useState<string | null>(null);
  const [creditCardDetailsFormValues, setCreditCardDetailsFormValues] = useState<Record<string, string>>({});

  const handleClose = () => {
    navigate('/financial-account');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
      dispatch(resetCreditAccountDetails());
    } else {
      dispatch(getEntity(id));
    }
  }, []);

  useEffect(() => {
    if (!isNew && financialAccountEntity.accountType) {
      setSelectedAccountType(financialAccountEntity.accountType as keyof typeof AccountType);
    }
  }, [isNew, financialAccountEntity.accountType]);

  useEffect(() => {
    if (!isNew && financialAccountEntity.id && financialAccountEntity.accountType === 'CREDIT_CARD') {
      dispatch(resetCreditAccountDetails());
      dispatch(getCreditAccountDetailsByAccountId(financialAccountEntity.id));
    }
  }, [isNew, financialAccountEntity.id, financialAccountEntity.accountType]);

  const toNumber = value => (value !== undefined && typeof value !== 'number' ? Number(value) : value);
  const toOptionalNumber = value => {
    if (value === undefined || value === '') {
      return null;
    }
    return typeof value !== 'number' ? Number(value) : value;
  };
  const getCreditCardDetailsValue = (values, fieldName, existingValue?) =>
    creditCardDetailsFormValues[fieldName] ?? values[fieldName] ?? existingValue;

  const buildCreditAccountDetailsEntity = (values, account, existingDetails: Partial<ICreditAccountDetails> = {}) => ({
    ...existingDetails,
    creditLimit: toNumber(getCreditCardDetailsValue(values, creditCardDetailsFieldNames.creditLimit, existingDetails?.creditLimit)),
    statementDay: toNumber(getCreditCardDetailsValue(values, creditCardDetailsFieldNames.statementDay, existingDetails?.statementDay)),
    paymentDueDay: toNumber(getCreditCardDetailsValue(values, creditCardDetailsFieldNames.paymentDueDay, existingDetails?.paymentDueDay)),
    annualInterestRate: toOptionalNumber(
      getCreditCardDetailsValue(values, creditCardDetailsFieldNames.annualInterestRate, existingDetails?.annualInterestRate),
    ),
    account,
  });

  const getPayloadData = action => action?.payload?.data ?? action?.payload;
  const throwIfRejected = action => {
    if (action?.error) {
      throw action.error;
    }
  };

  const saveEntity = async values => {
    setCompositionError(null);
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.initialBalance !== undefined && typeof values.initialBalance !== 'number') {
      values.initialBalance = Number(values.initialBalance);
    }
    const entity = {
      ...financialAccountEntity,
      ...values,
      active: isNew ? true : values.active,
    };

    try {
      const financialAccountAction = isNew ? await dispatch(createEntity(entity)) : await dispatch(updateEntity(entity));
      throwIfRejected(financialAccountAction);
      const savedFinancialAccount = getPayloadData(financialAccountAction);

      if (savedFinancialAccount?.accountType === 'CREDIT_CARD') {
        const creditCardAccount = { id: savedFinancialAccount.id, name: savedFinancialAccount.name };
        const existingDetails = !isNew && creditAccountDetailsEntity?.id ? creditAccountDetailsEntity : {};
        const creditCardDetails = buildCreditAccountDetailsEntity(values, creditCardAccount, existingDetails);

        if (!isNew && creditAccountDetailsEntity?.id) {
          const creditAccountDetailsAction = await dispatch(updateCreditAccountDetails(creditCardDetails));
          throwIfRejected(creditAccountDetailsAction);
        } else {
          const creditAccountDetailsAction = await dispatch(createCreditAccountDetails(creditCardDetails));
          throwIfRejected(creditAccountDetailsAction);
        }
      }

      handleClose();
    } catch (error) {
      setCompositionError(translate('fintrackApp.creditAccountDetails.composition.saveError'));
    }
  };

  const defaultFormValues = useMemo(
    () =>
      isNew
        ? {}
        : {
            accountType: 'DEBIT',
            currency: 'MXN',
            ...financialAccountEntity,
            [creditCardDetailsFieldNames.creditLimit]: creditAccountDetailsEntity?.creditLimit,
            [creditCardDetailsFieldNames.statementDay]: creditAccountDetailsEntity?.statementDay,
            [creditCardDetailsFieldNames.paymentDueDay]: creditAccountDetailsEntity?.paymentDueDay,
            [creditCardDetailsFieldNames.annualInterestRate]: creditAccountDetailsEntity?.annualInterestRate,
          },
    [isNew, financialAccountEntity, creditAccountDetailsEntity],
  );

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.financialAccount.home.createOrEditLabel" data-cy="FinancialAccountCreateUpdateHeading">
            <Translate contentKey={isNew ? 'fintrackApp.financialAccount.home.createTitle' : 'fintrackApp.financialAccount.home.editTitle'}>
              {isNew ? 'Create Financial Account' : 'Edit Financial Account'}
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm
              key={`${financialAccountEntity?.id ?? 'new'}-${creditAccountDetailsEntity?.id ?? 'missing'}`}
              defaultValues={defaultFormValues}
              onSubmit={saveEntity}
            >
              {compositionError ? <Alert color="danger">{compositionError}</Alert> : null}
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
                disabled={!isNew}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                  const nextAccountType = event.target.value as keyof typeof AccountType;
                  setSelectedAccountType(nextAccountType);
                  setCreditCardDetailsFormValues({});
                  resetOpeningPositionFields(event.target.form);
                }}
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
                disabled={!isNew}
              >
                {currencyCodeValues.map(currencyCode => (
                  <option value={currencyCode} key={currencyCode}>
                    {translate(`fintrackApp.CurrencyCode.${currencyCode}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate(getInitialBalanceLabelKey(selectedAccountType))}
                id="financial-account-initialBalance"
                name="initialBalance"
                data-cy="initialBalance"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <FormText>
                <Translate key={selectedAccountType} contentKey={getInitialBalanceHelpKey(selectedAccountType)}>
                  Opening position when you started tracking this account.
                </Translate>
              </FormText>
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
              {selectedAccountType === 'CREDIT_CARD' ? (
                creditAccountDetailsLoading && !isNew ? (
                  <p>Loading...</p>
                ) : (
                  <CreditCardDetailsFormSection
                    details={creditAccountDetailsEntity}
                    values={creditCardDetailsFormValues}
                    onFieldChange={(fieldName, value) =>
                      setCreditCardDetailsFormValues(previousValues => ({ ...previousValues, [fieldName]: value }))
                    }
                  />
                )
              ) : null}
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
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.financialAccount.active')}
                  id="financial-account-active"
                  name="active"
                  data-cy="active"
                  check
                  type="checkbox"
                />
              ) : null}
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
