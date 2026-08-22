import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { ICategory } from 'app/shared/model/category.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { mapIdList } from 'app/shared/util/entity-utils';
import TransactionRuleLocalConditionsEditor, { LocalTransactionRuleCondition } from './components/transaction-rule-local-conditions-editor';
import {
  createConfiguredTransactionRule,
  getConfiguredTransactionRule,
  updateConfiguredTransactionRule,
} from './transaction-rule-configured.service';
import { isCategoryFlowCompatible, isExactFlowEqualsCondition, requiredFlowForCategory } from './transaction-rule-configured-validation';

const ruleConditionLogicValues: Array<keyof typeof RuleConditionLogic> = ['ALL', 'ANY'];

const configuredCondition = (condition: LocalTransactionRuleCondition, index: number): ITransactionRuleCondition => ({
  id: condition.id,
  field: condition.field,
  operator: condition.operator,
  value: condition.value,
  secondValue: condition.secondValue ?? null,
  caseSensitive: condition.caseSensitive ?? false,
  position: index,
});

const asLocalConditions = (conditions: ITransactionRuleCondition[] = []): LocalTransactionRuleCondition[] =>
  conditions.map((condition, index) => ({
    ...condition,
    position: condition.position ?? index,
    clientId: condition.id ? `persisted-${condition.id}` : `loaded-${index}`,
  }));

const createRequiredFlowCondition = (flow: keyof typeof TransactionFlow, position: number): LocalTransactionRuleCondition => ({
  clientId: `auto-flow-${flow}-${Date.now()}`,
  field: TransactionRuleField.FLOW,
  operator: RuleOperator.EQUALS,
  value: flow,
  secondValue: null,
  caseSensitive: false,
  position,
  autoRequiredFlow: true,
});

const normalizePositions = (conditions: LocalTransactionRuleCondition[]) =>
  conditions.map((condition, index) => ({ ...condition, position: index }));

const hasAnyOutput = (categoryId: string, tagIds: string[]) => Boolean(categoryId) || tagIds.length > 0;

export const TransactionRuleUpdate = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);

  const [configuredRule, setConfiguredRule] = useState<ITransactionRuleConfigured | null>(null);
  const [conditions, setConditions] = useState<LocalTransactionRuleCondition[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [conditionLogic, setConditionLogic] = useState<keyof typeof RuleConditionLogic>(RuleConditionLogic.ALL);
  const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedCategory = useMemo<ICategory | null>(
    () => categories.find(category => category.id?.toString() === selectedCategoryId) ?? null,
    [categories, selectedCategoryId],
  );
  const requiredFlow = requiredFlowForCategory(selectedCategory);
  const formKey = isNew ? 'new-configured-rule' : `configured-rule-${configuredRule?.id ?? 'loading'}`;
  const isEntityLoaded = isNew || configuredRule?.id?.toString() === id;

  useEffect(() => {
    dispatch(getCategories({}));
    dispatch(getTags({}));
  }, []);

  useEffect(() => {
    if (isNew || !id) {
      setConfiguredRule(null);
      setConditions([]);
      setSelectedCategoryId('');
      setSelectedTagIds([]);
      setConditionLogic(RuleConditionLogic.ALL);
      setActive(true);
      return;
    }

    setLoading(true);
    getConfiguredTransactionRule(id)
      .then(response => {
        const rule = response.data;
        setConfiguredRule(rule);
        setConditions(asLocalConditions(rule.conditions));
        setSelectedCategoryId(rule.resultingCategory?.id?.toString() ?? '');
        setSelectedTagIds(rule.resultingTags?.map(tag => tag.id?.toString()).filter(Boolean) ?? []);
        setConditionLogic(rule.conditionLogic ?? RuleConditionLogic.ALL);
        setActive(rule.active ?? false);
      })
      .catch(() => setErrorMessage(translate('fintrackApp.transactionRule.configuredLoadFailed')))
      .finally(() => setLoading(false));
  }, [isNew, id]);

  useEffect(() => {
    const nextRequiredFlow = requiredFlowForCategory(selectedCategory);
    setConditions(currentConditions => {
      const autoCondition = currentConditions.find(condition => condition.autoRequiredFlow);

      if (!nextRequiredFlow) {
        return normalizePositions(currentConditions.filter(condition => !condition.autoRequiredFlow));
      }

      if (autoCondition) {
        return normalizePositions(
          currentConditions.map(condition =>
            condition.autoRequiredFlow
              ? {
                  ...condition,
                  field: TransactionRuleField.FLOW,
                  operator: RuleOperator.EQUALS,
                  value: nextRequiredFlow,
                  secondValue: null,
                  caseSensitive: false,
                }
              : condition,
          ),
        );
      }

      const hasFlowCondition = currentConditions.some(condition => condition.field === TransactionRuleField.FLOW);
      if (hasFlowCondition) {
        return currentConditions;
      }

      return normalizePositions([...currentConditions, createRequiredFlowCondition(nextRequiredFlow, currentConditions.length)]);
    });
  }, [selectedCategory?.id, selectedCategory?.categoryType]);

  const validationMessages = useMemo(() => {
    const messages: string[] = [];
    if (conditions.length === 0) {
      messages.push(translate('fintrackApp.transactionRule.validation.conditionsRequired'));
    }
    if (!hasAnyOutput(selectedCategoryId, selectedTagIds)) {
      messages.push(translate('fintrackApp.transactionRule.validation.outputRequired'));
    }
    if (requiredFlow && conditionLogic !== RuleConditionLogic.ALL) {
      messages.push(translate('fintrackApp.transactionRule.validation.expenseIncomeRequireAll'));
    }
    if (requiredFlow && !isCategoryFlowCompatible(selectedCategory, conditionLogic, conditions)) {
      messages.push(
        requiredFlow === TransactionFlow.OUT
          ? translate('fintrackApp.transactionRule.validation.expenseRequiresFlowOut')
          : translate('fintrackApp.transactionRule.validation.incomeRequiresFlowIn'),
      );
      if (
        conditions.some(condition => condition.field === TransactionRuleField.FLOW && !isExactFlowEqualsCondition(condition, requiredFlow))
      ) {
        messages.push(translate('fintrackApp.transactionRule.validation.categoryIncompatibleWithExistingFlow'));
      }
    }
    return Array.from(new Set(messages));
  }, [conditions, conditionLogic, requiredFlow, selectedCategory, selectedCategoryId, selectedTagIds]);

  const saveDisabled = saving || validationMessages.length > 0;

  const defaultValues = () =>
    isNew
      ? {
          active: true,
          conditionLogic: RuleConditionLogic.ALL,
        }
      : {
          ...configuredRule,
          conditionLogic,
          active,
          resultingCategory: selectedCategoryId,
          resultingTags: selectedTagIds,
        };

  const saveEntity = values => {
    setErrorMessage(null);
    if (validationMessages.length > 0) {
      return;
    }

    const entity: ITransactionRuleConfigured = {
      id: configuredRule?.id,
      name: values.name,
      description: values.description,
      conditionLogic,
      active,
      resultingCategory: selectedCategoryId ? { id: Number(selectedCategoryId) } : null,
      resultingTags: mapIdList(selectedTagIds),
      conditions: normalizePositions(conditions).map(configuredCondition),
    };

    setSaving(true);
    const request = isNew ? createConfiguredTransactionRule(entity) : updateConfiguredTransactionRule(entity);
    request
      .then(() => navigate('/transaction-rule'))
      .catch(() => setErrorMessage(translate('fintrackApp.transactionRule.configuredSaveFailed')))
      .finally(() => setSaving(false));
  };

  const handleCategoryChange = event => {
    setSelectedCategoryId(event.target.value);
  };

  const handleTagsChange = event => {
    setSelectedTagIds(Array.from(event.target.selectedOptions).map((option: HTMLOptionElement) => option.value));
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
          {loading || (!isEntityLoaded && !errorMessage) ? (
            <p>Loading...</p>
          ) : errorMessage && !isEntityLoaded ? (
            <Alert color="danger" fade={false} data-cy="configuredLoadError">
              {errorMessage}
            </Alert>
          ) : (
            <ValidatedForm key={formKey} defaultValues={defaultValues()} onSubmit={saveEntity}>
              <FormText className="d-block mb-3">
                <Translate contentKey="fintrackApp.transactionRule.configuredCreateEditHelp">
                  Configure metadata, outputs, conditions, and active state before saving.
                </Translate>
              </FormText>
              <FormText className="d-block mb-3">
                <Translate contentKey="fintrackApp.transactionRule.priorityServerManagedHelp">
                  Rule order is managed from the rules list. New rules are added last.
                </Translate>
              </FormText>

              {errorMessage ? (
                <Alert color="danger" fade={false} data-cy="configuredSaveError">
                  {errorMessage}
                </Alert>
              ) : null}
              {validationMessages.length > 0 ? (
                <Alert color="warning" fade={false} data-cy="configuredValidationMessages">
                  <ul className="mb-0">
                    {validationMessages.map(message => (
                      <li key={message}>{message}</li>
                    ))}
                  </ul>
                </Alert>
              ) : null}

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

              <h3 id="transaction-rule-identity-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.identity">Identity</Translate>
              </h3>
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

              <h3 id="transaction-rule-result-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.result">Result</Translate>
              </h3>
              <ValidatedField
                id="transaction-rule-resultingCategory"
                name="resultingCategory"
                data-cy="resultingCategory"
                label={translate('fintrackApp.transactionRule.resultingCategory')}
                type="select"
                value={selectedCategoryId}
                onChange={handleCategoryChange}
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
                label={translate('fintrackApp.transactionRule.resultingTags')}
                id="transaction-rule-resultingTags"
                data-cy="resultingTags"
                type="select"
                multiple
                name="resultingTags"
                value={selectedTagIds}
                onChange={handleTagsChange}
              >
                {tags
                  ? tags.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>

              <h3 id="transaction-rule-matching-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.matching">Matching logic</Translate>
              </h3>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.conditionLogic')}
                id="transaction-rule-conditionLogic"
                name="conditionLogic"
                data-cy="conditionLogic"
                type="select"
                value={conditionLogic}
                onChange={event => setConditionLogic(event.target.value as keyof typeof RuleConditionLogic)}
              >
                {ruleConditionLogicValues.map(ruleConditionLogic => (
                  <option
                    value={ruleConditionLogic}
                    key={ruleConditionLogic}
                    disabled={Boolean(requiredFlow) && ruleConditionLogic === 'ANY'}
                  >
                    {translate(`fintrackApp.RuleConditionLogic.${ruleConditionLogic}`)}
                  </option>
                ))}
              </ValidatedField>
              {requiredFlow ? (
                <FormText className="d-block mb-2">
                  <Translate contentKey="fintrackApp.transactionRule.requiredFlowHelp">
                    The selected category requires a compatible Flow condition.
                  </Translate>
                </FormText>
              ) : null}

              <TransactionRuleLocalConditionsEditor conditions={conditions} onChange={setConditions} lockedFlow={requiredFlow} />

              <h3 id="transaction-rule-status-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.status">Status</Translate>
              </h3>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.active')}
                id="transaction-rule-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
                checked={active}
                onChange={event => setActive(event.target.checked)}
              />
              <FormText>
                <Translate contentKey="fintrackApp.transactionRule.activeRequiresCondition">
                  Active rules require at least one condition.
                </Translate>
              </FormText>

              <div className="mt-3">
                <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-rule" replace color="info">
                  <FontAwesomeIcon icon="arrow-left" />
                  &nbsp;
                  <span className="d-none d-md-inline">
                    <Translate contentKey="entity.action.back">Back</Translate>
                  </span>
                </Button>
                &nbsp;
                <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={saveDisabled}>
                  <FontAwesomeIcon icon="save" />
                  &nbsp;
                  <Translate contentKey="entity.action.save">Save</Translate>
                </Button>
              </div>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionRuleUpdate;
