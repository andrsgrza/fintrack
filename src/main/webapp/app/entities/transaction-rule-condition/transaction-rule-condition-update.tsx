import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getTransactionRules } from 'app/entities/transaction-rule/transaction-rule.reducer';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { createEntity, getEntity, reset, updateEntity } from './transaction-rule-condition.reducer';

export const TransactionRuleConditionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const transactionRules = useAppSelector(state => state.transactionRule.entities);
  const transactionRuleConditionEntity = useAppSelector(state => state.transactionRuleCondition.entity);
  const loading = useAppSelector(state => state.transactionRuleCondition.loading);
  const updating = useAppSelector(state => state.transactionRuleCondition.updating);
  const updateSuccess = useAppSelector(state => state.transactionRuleCondition.updateSuccess);
  const transactionRuleFieldValues = Object.keys(TransactionRuleField);
  const ruleOperatorValues = Object.keys(RuleOperator);

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
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

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
      transactionRule: transactionRules.find(it => it.id.toString() === values.transactionRule?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          field: 'DESCRIPTION',
          operator: 'EQUALS',
          ...transactionRuleConditionEntity,
          transactionRule: transactionRuleConditionEntity?.transactionRule?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRuleCondition.home.createOrEditLabel" data-cy="TransactionRuleConditionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.transactionRuleCondition.home.createOrEditLabel">
              Create or edit a TransactionRuleCondition
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
              >
                {ruleOperatorValues.map(ruleOperator => (
                  <option value={ruleOperator} key={ruleOperator}>
                    {translate(`fintrackApp.RuleOperator.${ruleOperator}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.value')}
                id="transaction-rule-condition-value"
                name="value"
                data-cy="value"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.secondValue')}
                id="transaction-rule-condition-secondValue"
                name="secondValue"
                data-cy="secondValue"
                type="text"
                validate={{
                  maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionRuleCondition.caseSensitive')}
                id="transaction-rule-condition-caseSensitive"
                name="caseSensitive"
                data-cy="caseSensitive"
                check
                type="checkbox"
              />
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
