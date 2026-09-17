import axios from 'axios';
import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';
import {
  IFinancialAccountConfiguredRequest,
  IFinancialAccountConfiguredResponse,
} from 'app/shared/model/financial-account-configured.model';
import { getEntity, reset } from './financial-account.reducer';
import {
  getEntityByAccountId as getCreditAccountDetailsByAccountId,
  reset as resetCreditAccountDetails,
} from 'app/entities/credit-account-details/credit-account-details.reducer';
import { getInitialBalanceHelpKey, getInitialBalanceLabelKey } from './financial-account-labels';
import CreditCardDetailsFormSection, { creditCardDetailsFieldNames } from './components/credit-card-details-form-section';

const resetOpeningPositionFields = (form: HTMLFormElement | null) => {
  if (!form) return;
  ['initialBalance', 'initialBalanceDate'].forEach(fieldName => {
    const field = form.elements.namedItem(fieldName) as HTMLInputElement | null;
    if (field) {
      field.value = '';
      field.dispatchEvent(new Event('input', { bubbles: true }));
      field.dispatchEvent(new Event('change', { bubbles: true }));
    }
  });
};

const toNumber = (value: unknown) => (value !== undefined && value !== null && value !== '' ? Number(value) : value);

const creditCardDetailsPropertyByFieldName = {
  [creditCardDetailsFieldNames.creditLimit]: 'creditLimit',
  [creditCardDetailsFieldNames.statementDay]: 'statementDay',
  [creditCardDetailsFieldNames.paymentDueDay]: 'paymentDueDay',
  [creditCardDetailsFieldNames.annualInterestRate]: 'annualInterestRate',
} as const;

const validateCreditCardDetails = (values: Record<string, unknown>) => {
  const errors: Record<string, string> = {};
  const requiredNonNegative = [creditCardDetailsFieldNames.creditLimit];
  const requiredDay = [creditCardDetailsFieldNames.statementDay, creditCardDetailsFieldNames.paymentDueDay];

  requiredNonNegative.forEach(fieldName => {
    const value = values[fieldName];
    if (value === '' || value === undefined || value === null) {
      errors[fieldName] = translate('entity.validation.required');
    } else if (!Number.isFinite(Number(value))) {
      errors[fieldName] = translate('entity.validation.number');
    } else if (Number(value) < 0) {
      errors[fieldName] = translate('entity.validation.min', { min: 0 });
    }
  });
  requiredDay.forEach(fieldName => {
    const value = values[fieldName];
    if (value === '' || value === undefined || value === null) {
      errors[fieldName] = translate('entity.validation.required');
    } else if (!Number.isInteger(Number(value))) {
      errors[fieldName] = translate('entity.validation.number');
    } else if (Number(value) < 1) {
      errors[fieldName] = translate('entity.validation.min', { min: 1 });
    } else if (Number(value) > 31) {
      errors[fieldName] = translate('entity.validation.max', { max: 31 });
    }
  });

  const annualInterestRate = values[creditCardDetailsFieldNames.annualInterestRate];
  if (annualInterestRate !== '' && annualInterestRate !== undefined && annualInterestRate !== null) {
    if (!Number.isFinite(Number(annualInterestRate))) {
      errors[creditCardDetailsFieldNames.annualInterestRate] = translate('entity.validation.number');
    } else if (Number(annualInterestRate) < 0) {
      errors[creditCardDetailsFieldNames.annualInterestRate] = translate('entity.validation.min', { min: 0 });
    }
  }
  return errors;
};

const getSaveError = (error: unknown) => {
  if (axios.isAxiosError(error)) {
    const detail = error.response?.data?.detail;
    if (typeof detail === 'string' && detail.trim()) return detail;
    const message = error.response?.data?.message;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return translate('fintrackApp.financialAccount.configured.saveError');
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
  const [selectedAccountType, setSelectedAccountType] = useState<keyof typeof AccountType>('DEBIT');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [creditCardDetailsFormValues, setCreditCardDetailsFormValues] = useState<Record<string, string>>({});
  const [creditCardDetailsValidationErrors, setCreditCardDetailsValidationErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
      dispatch(resetCreditAccountDetails());
    } else {
      dispatch(getEntity(id));
    }
  }, [dispatch, id, isNew]);

  useEffect(() => {
    if (!isNew && financialAccountEntity.accountType) {
      setSelectedAccountType(financialAccountEntity.accountType as keyof typeof AccountType);
    }
  }, [financialAccountEntity.accountType, isNew]);

  useEffect(() => {
    if (!isNew && financialAccountEntity.id && financialAccountEntity.accountType === 'CREDIT_CARD') {
      dispatch(resetCreditAccountDetails());
      dispatch(getCreditAccountDetailsByAccountId(financialAccountEntity.id));
    }
  }, [dispatch, financialAccountEntity.accountType, financialAccountEntity.id, isNew]);

  const defaultFormValues = useMemo(
    () =>
      isNew
        ? { accountType: 'DEBIT', currency: 'MXN', active: true }
        : {
            ...financialAccountEntity,
            [creditCardDetailsFieldNames.creditLimit]: creditAccountDetailsEntity?.creditLimit,
            [creditCardDetailsFieldNames.statementDay]: creditAccountDetailsEntity?.statementDay,
            [creditCardDetailsFieldNames.paymentDueDay]: creditAccountDetailsEntity?.paymentDueDay,
            [creditCardDetailsFieldNames.annualInterestRate]: creditAccountDetailsEntity?.annualInterestRate,
          },
    [creditAccountDetailsEntity, financialAccountEntity, isNew],
  );

  const saveEntity = async values => {
    setSaveError(null);
    const accountType = values.accountType as keyof typeof AccountType;
    const creditFieldValue = fieldName => {
      const propertyName = creditCardDetailsPropertyByFieldName[fieldName];
      return creditCardDetailsFormValues[fieldName] ?? values[fieldName] ?? creditAccountDetailsEntity?.[propertyName] ?? '';
    };
    const creditCardDetailsValues = Object.fromEntries(
      Object.keys(creditCardDetailsPropertyByFieldName).map(fieldName => [fieldName, creditFieldValue(fieldName)]),
    );
    if (accountType === 'CREDIT_CARD') {
      const errors = validateCreditCardDetails(creditCardDetailsValues);
      setCreditCardDetailsValidationErrors(errors);
      if (Object.keys(errors).length > 0) {
        return;
      }
    }
    const request: IFinancialAccountConfiguredRequest = {
      financialAccount: {
        name: values.name,
        institutionName: values.institutionName || null,
        accountType,
        currency: values.currency,
        initialBalance: toNumber(values.initialBalance) as number,
        initialBalanceDate: values.initialBalanceDate,
        lastFourDigits: values.lastFourDigits || null,
        description: values.description || null,
        color: values.color || null,
        icon: values.icon || null,
        active: isNew ? true : Boolean(values.active),
      },
    };
    if (accountType === 'CREDIT_CARD') {
      request.creditAccountDetails = {
        creditLimit: toNumber(creditFieldValue(creditCardDetailsFieldNames.creditLimit)) as number,
        statementDay: toNumber(creditFieldValue(creditCardDetailsFieldNames.statementDay)) as number,
        paymentDueDay: toNumber(creditFieldValue(creditCardDetailsFieldNames.paymentDueDay)) as number,
        annualInterestRate:
          creditFieldValue(creditCardDetailsFieldNames.annualInterestRate) === ''
            ? null
            : (toNumber(creditFieldValue(creditCardDetailsFieldNames.annualInterestRate)) as number | null),
      };
    }

    setSaving(true);
    try {
      const response = isNew
        ? await axios.post<IFinancialAccountConfiguredResponse>('api/financial-accounts/configured', request)
        : await axios.put<IFinancialAccountConfiguredResponse>(`api/financial-accounts/${id}/configured`, request);
      navigate(`/financial-account/${response.data.financialAccount.id}`);
    } catch (error) {
      setSaveError(getSaveError(error));
    } finally {
      setSaving(false);
    }
  };

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
              noValidate
            >
              {saveError ? (
                <Alert color="danger" fade={false} data-cy="configuredSaveError">
                  {saveError}
                </Alert>
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
                validate={{ maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) } }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.currency')}
                id="financial-account-currency"
                name="currency"
                data-cy="currency"
                type="select"
                disabled={!isNew}
              >
                {Object.keys(CurrencyCode).map(currencyCode => (
                  <option value={currencyCode} key={currencyCode}>
                    {translate(`fintrackApp.CurrencyCode.${currencyCode}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialAccount.lastFourDigits')}
                id="financial-account-lastFourDigits"
                name="lastFourDigits"
                data-cy="lastFourDigits"
                type="text"
                validate={{ pattern: { value: /^[0-9]{4}$/, message: translate('entity.validation.pattern', { pattern: '^[0-9]{4}$' }) } }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialAccount.description')}
                id="financial-account-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{ maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) } }}
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
                validate={{ maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) } }}
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
                  setCreditCardDetailsValidationErrors({});
                  resetOpeningPositionFields(event.target.form);
                }}
              >
                {Object.keys(AccountType).map(accountType => (
                  <option value={accountType} key={accountType}>
                    {translate(`fintrackApp.AccountType.${accountType}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate(getInitialBalanceLabelKey(selectedAccountType))}
                id="financial-account-initialBalance"
                name="initialBalance"
                data-cy="initialBalance"
                type="number"
                step="0.01"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  validate: value => isNumber(value) || translate('entity.validation.number'),
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
                validate={{ required: { value: true, message: translate('entity.validation.required') } }}
              />
              {selectedAccountType === 'CREDIT_CARD' ? (
                creditAccountDetailsLoading && !isNew ? (
                  <p>Loading...</p>
                ) : (
                  <CreditCardDetailsFormSection
                    details={creditAccountDetailsEntity}
                    values={creditCardDetailsFormValues}
                    validationErrors={creditCardDetailsValidationErrors}
                    onFieldChange={(fieldName, value) => {
                      setCreditCardDetailsFormValues(previous => ({ ...previous, [fieldName]: value }));
                      setCreditCardDetailsValidationErrors(previous => {
                        const { [fieldName]: ignored, ...remaining } = previous;
                        return remaining;
                      });
                    }}
                  />
                )
              ) : null}
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
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={saving}>
                <FontAwesomeIcon icon="save" />
                &nbsp;<Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default FinancialAccountUpdate;
