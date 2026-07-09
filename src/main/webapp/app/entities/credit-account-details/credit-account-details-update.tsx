import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { createEntity, getEntity, reset, updateEntity } from './credit-account-details.reducer';

export const CreditAccountDetailsUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const creditAccountDetailsEntity = useAppSelector(state => state.creditAccountDetails.entity);
  const loading = useAppSelector(state => state.creditAccountDetails.loading);
  const updating = useAppSelector(state => state.creditAccountDetails.updating);
  const updateSuccess = useAppSelector(state => state.creditAccountDetails.updateSuccess);

  const handleClose = () => {
    navigate('/credit-account-details');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialAccounts({}));
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
    if (values.creditLimit !== undefined && typeof values.creditLimit !== 'number') {
      values.creditLimit = Number(values.creditLimit);
    }
    if (values.statementDay !== undefined && typeof values.statementDay !== 'number') {
      values.statementDay = Number(values.statementDay);
    }
    if (values.paymentDueDay !== undefined && typeof values.paymentDueDay !== 'number') {
      values.paymentDueDay = Number(values.paymentDueDay);
    }
    if (values.annualInterestRate !== undefined && typeof values.annualInterestRate !== 'number') {
      values.annualInterestRate = Number(values.annualInterestRate);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...creditAccountDetailsEntity,
      ...values,
      account: financialAccounts.find(it => it.id.toString() === values.account?.toString()),
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
          ...creditAccountDetailsEntity,
          createdAt: convertDateTimeFromServer(creditAccountDetailsEntity.createdAt),
          updatedAt: convertDateTimeFromServer(creditAccountDetailsEntity.updatedAt),
          account: creditAccountDetailsEntity?.account?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.creditAccountDetails.home.createOrEditLabel" data-cy="CreditAccountDetailsCreateUpdateHeading">
            <Translate contentKey="fintrackApp.creditAccountDetails.home.createOrEditLabel">
              Create or edit a CreditAccountDetails
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
                  id="credit-account-details-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.creditLimit')}
                id="credit-account-details-creditLimit"
                name="creditLimit"
                data-cy="creditLimit"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.statementDay')}
                id="credit-account-details-statementDay"
                name="statementDay"
                data-cy="statementDay"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 1, message: translate('entity.validation.min', { min: 1 }) },
                  max: { value: 31, message: translate('entity.validation.max', { max: 31 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.paymentDueDay')}
                id="credit-account-details-paymentDueDay"
                name="paymentDueDay"
                data-cy="paymentDueDay"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 1, message: translate('entity.validation.min', { min: 1 }) },
                  max: { value: 31, message: translate('entity.validation.max', { max: 31 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.annualInterestRate')}
                id="credit-account-details-annualInterestRate"
                name="annualInterestRate"
                data-cy="annualInterestRate"
                type="text"
                validate={{
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.createdAt')}
                id="credit-account-details-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.creditAccountDetails.updatedAt')}
                id="credit-account-details-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="credit-account-details-account"
                name="account"
                data-cy="account"
                label={translate('fintrackApp.creditAccountDetails.account')}
                type="select"
                required
              >
                <option value="" key="0" />
                {financialAccounts
                  ? financialAccounts
                      .filter(otherEntity => otherEntity.accountType === 'CREDIT_CARD')
                      .map(otherEntity => (
                        <option value={otherEntity.id} key={otherEntity.id}>
                          {otherEntity.name}
                        </option>
                      ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/credit-account-details" replace color="info">
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

export default CreditAccountDetailsUpdate;
