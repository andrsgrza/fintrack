import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getFinancialSubscriptions } from 'app/entities/financial-subscription/financial-subscription.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './transaction-rule.reducer';
import TransactionRuleConditionsCollectionEditor from './components/transaction-rule-conditions-collection-editor';

export const TransactionRuleUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const categories = useAppSelector(state => state.category.entities);
  const financialSubscriptions = useAppSelector(state => state.financialSubscription.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const transactionRuleEntity = useAppSelector(state => state.transactionRule.entity);
  const loading = useAppSelector(state => state.transactionRule.loading);
  const updating = useAppSelector(state => state.transactionRule.updating);
  const updateSuccess = useAppSelector(state => state.transactionRule.updateSuccess);
  const ruleConditionLogicValues = Object.keys(RuleConditionLogic);
  const [conditionsState, setConditionsState] = useState({ count: 0, loaded: false, failed: false });

  const activeDisabled = !isNew && (!conditionsState.loaded || conditionsState.failed || conditionsState.count === 0);

  const handleClose = () => {
    navigate('/transaction-rule');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getCategories({}));
    dispatch(getFinancialSubscriptions({}));
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
    if (values.priority !== undefined && typeof values.priority !== 'number') {
      values.priority = Number(values.priority);
    }
    const entity = {
      ...values,
      id: isNew ? undefined : transactionRuleEntity.id,
      active: isNew ? false : values.active,
      resultingCategory: values.resultingCategory ? categories.find(it => it.id.toString() === values.resultingCategory?.toString()) : null,
      resultingFinancialSubscription: values.resultingFinancialSubscription
        ? financialSubscriptions.find(it => it.id.toString() === values.resultingFinancialSubscription?.toString())
        : null,
      resultingTags: mapIdList(values.resultingTags),
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
          active: false,
          conditionLogic: 'ALL',
        }
      : {
          conditionLogic: 'ALL',
          ...transactionRuleEntity,
          resultingCategory: transactionRuleEntity?.resultingCategory?.id,
          resultingFinancialSubscription: transactionRuleEntity?.resultingFinancialSubscription?.id,
          resultingTags: transactionRuleEntity?.resultingTags?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRule.home.createOrEditLabel" data-cy="TransactionRuleCreateUpdateHeading">
            <Translate contentKey={isNew ? 'fintrackApp.transactionRule.createTitle' : 'fintrackApp.transactionRule.editTitle'}>
              {isNew ? 'Create Transaction Rule' : 'Edit Transaction Rule'}
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
                  id="transaction-rule-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.transactionRule.name')}
                id="transaction-rule-name"
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
                label={translate('fintrackApp.transactionRule.description')}
                id="transaction-rule-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionRule.priority')}
                id="transaction-rule-priority"
                name="priority"
                data-cy="priority"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionRule.conditionLogic')}
                id="transaction-rule-conditionLogic"
                name="conditionLogic"
                data-cy="conditionLogic"
                type="select"
              >
                {ruleConditionLogicValues.map(ruleConditionLogic => (
                  <option value={ruleConditionLogic} key={ruleConditionLogic}>
                    {translate(`fintrackApp.RuleConditionLogic.${ruleConditionLogic}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.resultingDescription')}
                id="transaction-rule-resultingDescription"
                name="resultingDescription"
                data-cy="resultingDescription"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              {!isNew ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.transactionRule.active')}
                    id="transaction-rule-active"
                    name="active"
                    data-cy="active"
                    check
                    type="checkbox"
                    disabled={activeDisabled}
                  />
                  <FormText>
                    {activeDisabled ? (
                      <Translate contentKey="fintrackApp.transactionRule.activeDisabledNoConditions">
                        Add at least one condition before activating this rule.
                      </Translate>
                    ) : (
                      <Translate contentKey="fintrackApp.transactionRule.activeRequiresCondition">
                        Active rules require at least one condition.
                      </Translate>
                    )}
                  </FormText>
                </>
              ) : null}
              <ValidatedField
                id="transaction-rule-resultingCategory"
                name="resultingCategory"
                data-cy="resultingCategory"
                label={translate('fintrackApp.transactionRule.resultingCategory')}
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
                id="transaction-rule-resultingFinancialSubscription"
                name="resultingFinancialSubscription"
                data-cy="resultingFinancialSubscription"
                label={translate('fintrackApp.transactionRule.resultingFinancialSubscription')}
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
              <ValidatedField
                label={translate('fintrackApp.transactionRule.resultingTags')}
                id="transaction-rule-resultingTags"
                data-cy="resultingTags"
                type="select"
                multiple
                name="resultingTags"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-rule" replace color="info">
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
          {!loading && !isNew ? (
            <TransactionRuleConditionsCollectionEditor
              transactionRuleId={transactionRuleEntity.id}
              onConditionsStateChange={setConditionsState}
              onConditionMutation={() => dispatch(getEntity(id))}
            />
          ) : null}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionRuleUpdate;
