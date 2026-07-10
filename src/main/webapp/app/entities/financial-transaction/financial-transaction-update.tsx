import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getFinancialSubscriptions } from 'app/entities/financial-subscription/financial-subscription.reducer';
import { getEntities as getTransactionIngestions } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionOrigin } from 'app/shared/model/enumerations/transaction-origin.model';
import { createEntity, getEntity, reset, updateEntity } from './financial-transaction.reducer';

export const FinancialTransactionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const financialSubscriptions = useAppSelector(state => state.financialSubscription.entities);
  const transactionIngestions = useAppSelector(state => state.transactionIngestion.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const updating = useAppSelector(state => state.financialTransaction.updating);
  const updateSuccess = useAppSelector(state => state.financialTransaction.updateSuccess);
  const transactionFlowValues = Object.keys(TransactionFlow);
  const transactionOriginValues = Object.keys(TransactionOrigin);

  const handleClose = () => {
    navigate(`/financial-transaction${location.search}`);
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialAccounts({}));
    dispatch(getCategories({}));
    dispatch(getFinancialSubscriptions({}));
    dispatch(getTransactionIngestions({}));
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
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);

    const entity = {
      ...financialTransactionEntity,
      ...values,
      account: financialAccounts.find(it => it.id.toString() === values.account?.toString()),
      category: categories.find(it => it.id.toString() === values.category?.toString()),
      financialSubscription: financialSubscriptions.find(it => it.id.toString() === values.financialSubscription?.toString()),
      tags: mapIdList(values.tags),
    };

    if (isNew) {
      entity.origin = 'MANUAL';
      entity.transactionIngestion = null;
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          flow: 'IN',
          origin: 'MANUAL',
          createdAt: displayDefaultDateTime(),
          updatedAt: displayDefaultDateTime(),
        }
      : {
          flow: 'IN',
          origin: 'MANUAL',
          ...financialTransactionEntity,
          createdAt: convertDateTimeFromServer(financialTransactionEntity.createdAt),
          updatedAt: convertDateTimeFromServer(financialTransactionEntity.updatedAt),
          account: financialTransactionEntity?.account?.id,
          category: financialTransactionEntity?.category?.id,
          financialSubscription: financialTransactionEntity?.financialSubscription?.id,
          transactionIngestion: financialTransactionEntity?.transactionIngestion?.id,
          tags: financialTransactionEntity?.tags?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.financialTransaction.home.createOrEditLabel" data-cy="FinancialTransactionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.financialTransaction.home.createOrEditLabel">
              Create or edit a FinancialTransaction
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
                  id="financial-transaction-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.transactionDate')}
                id="financial-transaction-transactionDate"
                name="transactionDate"
                data-cy="transactionDate"
                type="date"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.postingDate')}
                id="financial-transaction-postingDate"
                name="postingDate"
                data-cy="postingDate"
                type="date"
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.description')}
                id="financial-transaction-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.amount')}
                id="financial-transaction-amount"
                name="amount"
                data-cy="amount"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0.01, message: translate('entity.validation.min', { min: 0.01 }) },
                  validate: v => (isNumber(v) && Number(v) > 0) || translate('entity.validation.min', { min: 0.01 }),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.flow')}
                id="financial-transaction-flow"
                name="flow"
                data-cy="flow"
                type="select"
              >
                {transactionFlowValues.map(transactionFlow => (
                  <option value={transactionFlow} key={transactionFlow}>
                    {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.origin')}
                id="financial-transaction-origin"
                name="origin"
                data-cy="origin"
                type="select"
                readOnly={isNew}
                disabled={isNew}
              >
                {transactionOriginValues.map(transactionOrigin => (
                  <option value={transactionOrigin} key={transactionOrigin}>
                    {translate(`fintrackApp.TransactionOrigin.${transactionOrigin}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.externalReference')}
                id="financial-transaction-externalReference"
                name="externalReference"
                data-cy="externalReference"
                type="text"
                validate={{
                  maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.notes')}
                id="financial-transaction-notes"
                name="notes"
                data-cy="notes"
                type="text"
                validate={{
                  maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.createdAt')}
                id="financial-transaction-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.updatedAt')}
                id="financial-transaction-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="financial-transaction-account"
                name="account"
                data-cy="account"
                label={translate('fintrackApp.financialTransaction.account')}
                type="select"
                required
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
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <ValidatedField
                id="financial-transaction-category"
                name="category"
                data-cy="category"
                label={translate('fintrackApp.financialTransaction.category')}
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
                id="financial-transaction-financialSubscription"
                name="financialSubscription"
                data-cy="financialSubscription"
                label={translate('fintrackApp.financialTransaction.financialSubscription')}
                type="select"
              >
                <option value="" key="0" />
                {financialSubscriptions
                  ? financialSubscriptions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              {!isNew ? (
                <ValidatedField
                  id="financial-transaction-transactionIngestion"
                  name="transactionIngestion"
                  data-cy="transactionIngestion"
                  label={translate('fintrackApp.financialTransaction.transactionIngestion')}
                  type="select"
                  readOnly
                  disabled
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
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.financialTransaction.tags')}
                id="financial-transaction-tags"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-transaction" replace color="info">
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

export default FinancialTransactionUpdate;
