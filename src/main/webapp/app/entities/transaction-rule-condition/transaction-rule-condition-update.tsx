import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getTransactionRules } from 'app/entities/transaction-rule/transaction-rule.reducer';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionOrigin } from 'app/shared/model/enumerations/transaction-origin.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './transaction-rule-condition.reducer';
import {
  getAllowedOperators,
  getSecondValueInputKind,
  getValueInputKind,
  requiresSecondValue,
  supportsCaseSensitive as fieldSupportsCaseSensitive,
} from './transaction-rule-condition-form-helpers';

export const TransactionRuleConditionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const [searchParams] = useSearchParams();
  const isNew = id === undefined;
  const requestedTransactionRuleId = searchParams.get('transactionRuleId');

  const transactionRules = useAppSelector(state => state.transactionRule.entities);
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const transactionRuleConditionEntity = useAppSelector(state => state.transactionRuleCondition.entity);
  const loading = useAppSelector(state => state.transactionRuleCondition.loading);
  const updating = useAppSelector(state => state.transactionRuleCondition.updating);
  const updateSuccess = useAppSelector(state => state.transactionRuleCondition.updateSuccess);
  const transactionRuleFieldValues = Object.keys(TransactionRuleField);
  const [selectedField, setSelectedField] = useState(TransactionRuleField.DESCRIPTION);
  const [selectedOperator, setSelectedOperator] = useState(RuleOperator.EQUALS);
  const [value, setValue] = useState('');
  const [secondValue, setSecondValue] = useState('');
  const [caseSensitive, setCaseSensitive] = useState(false);

  const allowedOperators = getAllowedOperators(selectedField);
  const isBetweenOperator = requiresSecondValue(selectedOperator);
  const supportsCaseSensitive = fieldSupportsCaseSensitive(selectedField);
  const valueInputKind = getValueInputKind(selectedField, selectedOperator);
  const secondValueInputKind = getSecondValueInputKind(selectedField);

  const handleClose = () => {
    navigate('/transaction-rule-condition');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getTransactionRules({}));
  }, []);

  useEffect(() => {
    if (selectedField === TransactionRuleField.ACCOUNT) {
      dispatch(getFinancialAccounts({}));
    }
  }, [selectedField]);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  useEffect(() => {
    if (!isNew && transactionRuleConditionEntity.field) {
      setSelectedField(transactionRuleConditionEntity.field);
    }
    if (!isNew && transactionRuleConditionEntity.operator) {
      setSelectedOperator(transactionRuleConditionEntity.operator);
    }
    if (!isNew) {
      setValue(transactionRuleConditionEntity.value ?? '');
      setSecondValue(transactionRuleConditionEntity.secondValue ?? '');
      setCaseSensitive(Boolean(transactionRuleConditionEntity.caseSensitive));
    }
  }, [
    isNew,
    transactionRuleConditionEntity.field,
    transactionRuleConditionEntity.operator,
    transactionRuleConditionEntity.value,
    transactionRuleConditionEntity.secondValue,
    transactionRuleConditionEntity.caseSensitive,
  ]);

  const resetConditionValues = (nextField: TransactionRuleField, nextOperator: RuleOperator) => {
    setValue('');
    setSecondValue('');
    if (!fieldSupportsCaseSensitive(nextField)) {
      setCaseSensitive(false);
    }
    if (!requiresSecondValue(nextOperator)) {
      setSecondValue('');
    }
  };

  const handleFieldChange = event => {
    const nextField = event.target.value as TransactionRuleField;
    const nextAllowedOperators = getAllowedOperators(nextField);
    const nextOperator = nextAllowedOperators.includes(selectedOperator) ? selectedOperator : nextAllowedOperators[0];
    setSelectedField(nextField);
    setSelectedOperator(nextOperator);
    resetConditionValues(nextField, nextOperator);
  };

  const handleOperatorChange = event => {
    const nextOperator = event.target.value as RuleOperator;
    const currentInputKind = getValueInputKind(selectedField, selectedOperator);
    const nextInputKind = getValueInputKind(selectedField, nextOperator);
    setSelectedOperator(nextOperator);
    if (!requiresSecondValue(nextOperator)) {
      setSecondValue('');
    }
    if (currentInputKind !== nextInputKind) {
      setValue('');
    }
    if (!fieldSupportsCaseSensitive(selectedField)) {
      setCaseSensitive(false);
    }
  };

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.position !== undefined && typeof values.position !== 'number') {
      values.position = Number(values.position);
    }

    const entity = {
      ...transactionRuleConditionEntity,
      ...values,
      field: selectedField,
      operator: selectedOperator,
      value,
      secondValue: isBetweenOperator ? secondValue : null,
      caseSensitive: supportsCaseSensitive ? caseSensitive : false,
      transactionRule: isNew ? transactionRules.find(it => it.id.toString() === values.transactionRule?.toString()) : undefined,
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(partialUpdateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          field: TransactionRuleField.DESCRIPTION,
          operator: RuleOperator.EQUALS,
          caseSensitive: false,
          transactionRule: requestedTransactionRuleId ?? undefined,
        }
      : {
          field: TransactionRuleField.DESCRIPTION,
          operator: RuleOperator.EQUALS,
          ...transactionRuleConditionEntity,
          transactionRule: transactionRuleConditionEntity?.transactionRule?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRuleCondition.home.createOrEditLabel" data-cy="TransactionRuleConditionCreateUpdateHeading">
            <Translate
              contentKey={isNew ? 'fintrackApp.transactionRuleCondition.createTitle' : 'fintrackApp.transactionRuleCondition.editTitle'}
            >
              {isNew ? 'Create Transaction Rule Condition' : 'Edit Transaction Rule Condition'}
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
                  id="transaction-rule-condition-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.field')}
                id="transaction-rule-condition-field"
                name="field"
                data-cy="field"
                type="select"
                value={selectedField}
                onChange={handleFieldChange}
              >
                {transactionRuleFieldValues.map(transactionRuleField => (
                  <option value={transactionRuleField} key={transactionRuleField}>
                    {translate(`fintrackApp.TransactionRuleField.${transactionRuleField}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.operator')}
                id="transaction-rule-condition-operator"
                name="operator"
                data-cy="operator"
                type="select"
                value={selectedOperator}
                onChange={handleOperatorChange}
              >
                {allowedOperators.map(ruleOperator => (
                  <option value={ruleOperator} key={ruleOperator}>
                    {translate(`fintrackApp.RuleOperator.${ruleOperator}`)}
                  </option>
                ))}
              </ValidatedField>
              {valueInputKind === 'flow-select' ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRuleCondition.value')}
                  id="transaction-rule-condition-value"
                  name="value"
                  data-cy="value"
                  type="select"
                  value={value}
                  onChange={event => setValue(event.target.value)}
                >
                  <option value="" key="0" />
                  {Object.keys(TransactionFlow).map(transactionFlow => (
                    <option value={transactionFlow} key={transactionFlow}>
                      {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
                    </option>
                  ))}
                </ValidatedField>
              ) : null}
              {valueInputKind === 'origin-select' ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRuleCondition.value')}
                  id="transaction-rule-condition-value"
                  name="value"
                  data-cy="value"
                  type="select"
                  value={value}
                  onChange={event => setValue(event.target.value)}
                >
                  <option value="" key="0" />
                  {Object.keys(TransactionOrigin).map(transactionOrigin => (
                    <option value={transactionOrigin} key={transactionOrigin}>
                      {translate(`fintrackApp.TransactionOrigin.${transactionOrigin}`)}
                    </option>
                  ))}
                </ValidatedField>
              ) : null}
              {valueInputKind === 'account-select' ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRuleCondition.value')}
                  id="transaction-rule-condition-value"
                  name="value"
                  data-cy="value"
                  type="select"
                  value={value}
                  onChange={event => setValue(event.target.value)}
                >
                  <option value="" key="0" />
                  {financialAccounts
                    ? financialAccounts.map(account => (
                        <option value={account.id} key={account.id}>
                          {account.name}
                        </option>
                      ))
                    : null}
                </ValidatedField>
              ) : null}
              {valueInputKind === 'text' || valueInputKind === 'number' || valueInputKind === 'date' ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.transactionRuleCondition.value')}
                    id="transaction-rule-condition-value"
                    name="value"
                    data-cy="value"
                    type={valueInputKind}
                    value={value}
                    step={valueInputKind === 'number' ? '0.01' : undefined}
                    onChange={event => setValue(event.target.value)}
                    validate={{
                      maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                    }}
                  />
                  {selectedOperator === RuleOperator.IN || selectedOperator === RuleOperator.NOT_IN ? (
                    <FormText>
                      <Translate contentKey={`fintrackApp.transactionRuleCondition.help.${selectedField}`}>
                        Use comma-separated values.
                      </Translate>
                    </FormText>
                  ) : null}
                </>
              ) : null}
              {isBetweenOperator ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRuleCondition.secondValue')}
                  id="transaction-rule-condition-secondValue"
                  name="secondValue"
                  data-cy="secondValue"
                  type={secondValueInputKind}
                  value={secondValue}
                  step={secondValueInputKind === 'number' ? '0.01' : undefined}
                  onChange={event => setSecondValue(event.target.value)}
                  validate={{
                    maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                  }}
                />
              ) : null}
              {supportsCaseSensitive ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRuleCondition.caseSensitive')}
                  id="transaction-rule-condition-caseSensitive"
                  name="caseSensitive"
                  data-cy="caseSensitive"
                  check
                  type="checkbox"
                  checked={caseSensitive}
                  onChange={event => setCaseSensitive(event.target.checked)}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.position')}
                id="transaction-rule-condition-position"
                name="position"
                data-cy="position"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                id="transaction-rule-condition-transactionRule"
                name="transactionRule"
                data-cy="transactionRule"
                label={translate('fintrackApp.transactionRuleCondition.transactionRule')}
                type="select"
                required
                disabled={!isNew}
              >
                <option value="" key="0" />
                {transactionRules
                  ? transactionRules.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-rule-condition" replace color="info">
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

export default TransactionRuleConditionUpdate;
