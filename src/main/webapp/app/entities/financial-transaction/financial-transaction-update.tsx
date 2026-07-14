import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './financial-transaction.reducer';

const isCategoryCompatibleWithFlow = (category, flow) => {
  if (!category?.categoryType || !flow) {
    return true;
  }
  if (flow === 'OUT') {
    return category.categoryType === 'EXPENSE' || category.categoryType === 'BOTH';
  }
  if (flow === 'IN') {
    return category.categoryType === 'INCOME' || category.categoryType === 'BOTH';
  }
  return true;
};

const toOptionalNumber = value => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return typeof value === 'number' ? value : Number(value);
};

export const FinancialTransactionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const updating = useAppSelector(state => state.financialTransaction.updating);
  const updateSuccess = useAppSelector(state => state.financialTransaction.updateSuccess);
  const transactionFlowValues = Object.keys(TransactionFlow);
  const [selectedFlow, setSelectedFlow] = useState<keyof typeof TransactionFlow>('IN');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [selectedAccountId, setSelectedAccountId] = useState('');

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
    dispatch(getTags({}));
  }, []);

  useEffect(() => {
    if (isNew) {
      setSelectedFlow('IN');
      setSelectedCategoryId('');
      setSelectedAccountId('');
      return;
    }
    if (financialTransactionEntity?.id) {
      setSelectedFlow((financialTransactionEntity.flow as keyof typeof TransactionFlow) ?? 'IN');
      setSelectedCategoryId(financialTransactionEntity.category?.id?.toString() ?? '');
      setSelectedAccountId(financialTransactionEntity.account?.id?.toString() ?? '');
    }
  }, [isNew, financialTransactionEntity?.id]);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const selectedAccount = useMemo(
    () =>
      financialAccounts.find(it => it.id?.toString() === selectedAccountId) ?? (!isNew ? financialTransactionEntity.account : undefined),
    [financialAccounts, selectedAccountId, financialTransactionEntity.account, isNew],
  );

  const filteredCategories = useMemo(
    () => categories.filter(category => isCategoryCompatibleWithFlow(category, selectedFlow)),
    [categories, selectedFlow],
  );

  const handleFlowChange = event => {
    const nextFlow = event.target.value as keyof typeof TransactionFlow;
    setSelectedFlow(nextFlow);
    const currentCategory = categories.find(category => category.id?.toString() === selectedCategoryId);
    if (currentCategory && !isCategoryCompatibleWithFlow(currentCategory, nextFlow)) {
      setSelectedCategoryId('');
    }
  };

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }

    const mutableEntity = {
      id: values.id,
      transactionDate: values.transactionDate,
      postingDate: values.postingDate || null,
      description: values.description,
      amount: toOptionalNumber(values.amount),
      flow: selectedFlow,
      category: selectedCategoryId ? categories.find(it => it.id?.toString() === selectedCategoryId) : null,
      tags: mapIdList(values.tags),
      notes: values.notes || null,
    };

    if (isNew) {
      dispatch(
        createEntity({
          ...mutableEntity,
          account: financialAccounts.find(it => it.id?.toString() === (selectedAccountId || values.account?.toString())),
          origin: 'MANUAL',
        }),
      );
    } else {
      dispatch(partialUpdateEntity(mutableEntity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          flow: 'IN',
        }
      : {
          flow: 'IN',
          ...financialTransactionEntity,
          account: financialTransactionEntity?.account?.id,
          category: financialTransactionEntity?.category?.id,
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
                id="financial-transaction-account"
                name="account"
                data-cy="account"
                label={translate('fintrackApp.financialTransaction.account')}
                type={isNew ? 'select' : 'text'}
                readOnly={!isNew}
                disabled={!isNew}
                value={!isNew ? (financialTransactionEntity.account?.name ?? '') : undefined}
                onChange={event => setSelectedAccountId(event.target.value)}
              >
                {isNew ? (
                  <>
                    <option value="" key="0" />
                    {financialAccounts
                      ? financialAccounts.map(otherEntity => (
                          <option value={otherEntity.id} key={otherEntity.id}>
                            {otherEntity.name}
                          </option>
                        ))
                      : null}
                  </>
                ) : null}
              </ValidatedField>
              {isNew ? (
                <FormText>
                  <Translate contentKey="entity.validation.required">This field is required.</Translate>
                </FormText>
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
                label={translate('fintrackApp.financialTransaction.flow')}
                id="financial-transaction-flow"
                name="flow"
                data-cy="flow"
                type="select"
                value={selectedFlow}
                onChange={handleFlowChange}
              >
                {transactionFlowValues.map(transactionFlow => (
                  <option value={transactionFlow} key={transactionFlow}>
                    {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
                  </option>
                ))}
              </ValidatedField>
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
              {selectedAccount?.currency ? <FormText>{selectedAccount.currency}</FormText> : null}
              <ValidatedField
                id="financial-transaction-category"
                name="category"
                data-cy="category"
                label={translate('fintrackApp.financialTransaction.category')}
                type="select"
                value={selectedCategoryId}
                onChange={event => setSelectedCategoryId(event.target.value)}
              >
                <option value="" key="0" />
                {filteredCategories
                  ? filteredCategories.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
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
