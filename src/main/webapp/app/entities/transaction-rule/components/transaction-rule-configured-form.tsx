import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, FormGroup, FormText, Input, Label } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { ContextualRuleSaveAction, IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { mapIdList } from 'app/shared/util/entity-utils';
import { createConfiguredTransactionRule, updateConfiguredTransactionRule } from '../transaction-rule-configured.service';
import { isCategoryFlowCompatible, isExactFlowEqualsCondition, requiredFlowForCategory } from '../transaction-rule-configured-validation';
import TransactionRuleLocalConditionsEditor, { LocalTransactionRuleCondition } from './transaction-rule-local-conditions-editor';

interface TransactionRuleConfiguredFormProps {
  initialValue?: ITransactionRuleConfigured;
  saveActions: IContextualRuleSaveAction[];
  onSaved: (rule: ITransactionRuleConfigured, action: ContextualRuleSaveAction) => void | Promise<void>;
  onCancel: () => void;
}

const configuredCondition = (condition: LocalTransactionRuleCondition, index: number): ITransactionRuleCondition => ({
  id: condition.id,
  field: condition.field,
  operator: condition.operator,
  value: condition.value,
  secondValue: condition.secondValue ?? null,
  caseSensitive: condition.caseSensitive ?? false,
  position: index,
});

const asLocalConditions = (
  conditions: ITransactionRuleCondition[] = [],
  category?: ICategorySelectable | null,
): LocalTransactionRuleCondition[] => {
  const requiredFlow = requiredFlowForCategory(category);
  return conditions.map((condition, index) => ({
    ...condition,
    position: condition.position ?? index,
    clientId: condition.id ? `persisted-${condition.id}` : `loaded-${index}`,
    autoRequiredFlow: requiredFlow ? isExactFlowEqualsCondition(condition, requiredFlow) : false,
  }));
};

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
const serverErrorMessage = error => error?.response?.data?.detail ?? error?.response?.data?.message;

/** One configured TransactionRule editor shared by standalone and contextual compositions. */
export const TransactionRuleConfiguredForm = ({ initialValue, saveActions, onSaved, onCancel }: TransactionRuleConfiguredFormProps) => {
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [ruleName, setRuleName] = useState(initialValue?.name ?? '');
  const [ruleDescription, setRuleDescription] = useState(initialValue?.description ?? '');
  const [conditions, setConditions] = useState<LocalTransactionRuleCondition[]>(
    asLocalConditions(initialValue?.conditions, initialValue?.resultingCategory),
  );
  const [selectedCategoryId, setSelectedCategoryId] = useState(initialValue?.resultingCategory?.id?.toString() ?? '');
  const [selectedTagIds, setSelectedTagIds] = useState(initialValue?.resultingTags?.map(tag => tag.id?.toString()).filter(Boolean) ?? []);
  const [conditionLogic, setConditionLogic] = useState<RuleConditionLogic>(
    (initialValue?.conditionLogic as RuleConditionLogic | undefined) ?? RuleConditionLogic.ALL,
  );
  const [active, setActive] = useState(initialValue?.active ?? true);
  const [savingAction, setSavingAction] = useState<ContextualRuleSaveAction | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedCategory = useMemo<ICategorySelectable | null>(() => {
    const selectableCategory = categories.find(category => category.id?.toString() === selectedCategoryId);
    if (selectableCategory) {
      return selectableCategory;
    }
    // Keep the persisted output's type available while the selectable list loads.
    // Otherwise an edit can briefly remove its required FLOW condition before the
    // historical/current category is returned by the selector endpoint.
    if (initialValue?.resultingCategory?.id?.toString() === selectedCategoryId) {
      return initialValue.resultingCategory as ICategorySelectable;
    }
    return null;
  }, [categories, initialValue?.resultingCategory, selectedCategoryId]);
  const requiredFlow = requiredFlowForCategory(selectedCategory);

  useEffect(() => {
    let mounted = true;
    const categoryIds = initialValue?.resultingCategory?.id === undefined ? [] : [initialValue.resultingCategory.id];
    const tagIds = initialValue?.resultingTags?.map(tag => tag.id).filter((id): id is number => id !== undefined) ?? [];
    Promise.all([getSelectableCategories(categoryIds), getSelectableTags(tagIds)])
      .then(([nextCategories, nextTags]) => {
        if (mounted) {
          setCategories(nextCategories);
          setTags(nextTags);
        }
      })
      .catch(() => {
        if (mounted) {
          setCategories([]);
          setTags([]);
        }
      });
    return () => {
      mounted = false;
    };
  }, [initialValue?.resultingCategory?.id, initialValue?.resultingTags]);

  useEffect(() => {
    if (!requiredFlow) {
      setConditions(current => normalizePositions(current.filter(condition => !condition.autoRequiredFlow)));
      return;
    }
    setConditions(current => {
      const autoCondition = current.find(condition => condition.autoRequiredFlow);
      if (autoCondition) {
        return normalizePositions(
          current.map(condition =>
            condition.autoRequiredFlow
              ? { ...condition, field: TransactionRuleField.FLOW, operator: RuleOperator.EQUALS, value: requiredFlow, secondValue: null }
              : condition,
          ),
        );
      }
      if (current.some(condition => condition.field === TransactionRuleField.FLOW)) {
        return current;
      }
      return normalizePositions([...current, createRequiredFlowCondition(requiredFlow, current.length)]);
    });
  }, [requiredFlow]);

  useEffect(() => {
    if (requiredFlow && conditionLogic === RuleConditionLogic.ANY) {
      setConditionLogic(RuleConditionLogic.ALL);
    }
  }, [conditionLogic, requiredFlow]);

  const validationMessages = useMemo(() => {
    const messages: string[] = [];
    if (!ruleName.trim()) {
      messages.push(translate('entity.validation.required'));
    }
    if (!conditions.length) {
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
  }, [conditions, conditionLogic, requiredFlow, ruleName, selectedCategory, selectedCategoryId, selectedTagIds]);

  const save = async (event: FormEvent, action: ContextualRuleSaveAction) => {
    event.preventDefault();
    if (validationMessages.length) {
      return;
    }
    setErrorMessage(null);
    setSavingAction(action);
    const entity: ITransactionRuleConfigured = {
      id: initialValue?.id,
      name: ruleName,
      description: ruleDescription,
      conditionLogic,
      active,
      resultingCategory: selectedCategoryId ? { id: Number(selectedCategoryId) } : null,
      resultingTags: mapIdList(selectedTagIds),
      conditions: normalizePositions(conditions).map(configuredCondition),
    };
    try {
      const response = entity.id ? await updateConfiguredTransactionRule(entity) : await createConfiguredTransactionRule(entity);
      await onSaved(response.data, action);
    } catch (requestError) {
      setErrorMessage(serverErrorMessage(requestError) ?? translate('fintrackApp.transactionIngestion.workflow.ruleCreation.saveFailed'));
    } finally {
      setSavingAction(null);
    }
  };

  return (
    <Form onSubmit={event => save(event, 'SAVE')} data-cy="transactionRuleContextualForm">
      <FormText className="d-block mb-3">
        <Translate contentKey="fintrackApp.transactionRule.configuredCreateEditHelp">
          Configure metadata, outputs, conditions, and active state before saving.
        </Translate>
      </FormText>
      {errorMessage ? (
        <Alert color="danger" fade={false}>
          {errorMessage}
        </Alert>
      ) : null}
      {validationMessages.length ? (
        <Alert color="warning" data-cy="configuredValidationMessages" fade={false}>
          <ul className="mb-0">
            {validationMessages.map(message => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </Alert>
      ) : null}
      <FormText className="d-block mb-3">
        <Translate contentKey="fintrackApp.transactionRule.priorityServerManagedHelp">
          Rule order is managed from the rules list. New rules are added last.
        </Translate>
      </FormText>
      <h3 id="transaction-rule-identity-heading" className="mt-4">
        <Translate contentKey="fintrackApp.transactionRule.sections.identity">Identity</Translate>
      </h3>
      <FormGroup>
        <Label for="contextual-transaction-rule-name">
          <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>
        </Label>
        <Input
          id="contextual-transaction-rule-name"
          data-cy="name"
          value={ruleName}
          onChange={event => setRuleName(event.target.value)}
          required
          maxLength={100}
        />
      </FormGroup>
      <h3 id="transaction-rule-result-heading" className="mt-4">
        <Translate contentKey="fintrackApp.transactionRule.sections.result">Result</Translate>
      </h3>
      <FormGroup>
        <Label for="contextual-transaction-rule-description">
          <Translate contentKey="fintrackApp.transactionRule.description">Description</Translate>
        </Label>
        <Input
          id="contextual-transaction-rule-description"
          data-cy="description"
          value={ruleDescription}
          onChange={event => setRuleDescription(event.target.value)}
          maxLength={500}
        />
      </FormGroup>
      <h3 id="transaction-rule-matching-heading" className="mt-4">
        <Translate contentKey="fintrackApp.transactionRule.sections.matching">Matching logic</Translate>
      </h3>
      <FormGroup>
        <Label for="contextual-transaction-rule-category">
          <Translate contentKey="fintrackApp.transactionRule.resultingCategory">Resulting category</Translate>
        </Label>
        <Input
          id="contextual-transaction-rule-category"
          data-cy="resultingCategory"
          data-testid="contextualTransactionRuleCategory"
          type="select"
          value={selectedCategoryId}
          onChange={event => setSelectedCategoryId(event.target.value)}
        >
          <option value="" />
          {categories.map(category => (
            <option value={category.id} key={category.id}>
              {category.name}
              {category.active === false ? ` (${translate('fintrackApp.category.inactive')})` : ''}
            </option>
          ))}
        </Input>
      </FormGroup>
      <FormGroup>
        <Label for="contextual-transaction-rule-tags">
          <Translate contentKey="fintrackApp.transactionRule.resultingTags">Resulting tags</Translate>
        </Label>
        <Input
          id="contextual-transaction-rule-tags"
          data-cy="resultingTags"
          data-testid="contextualTransactionRuleTags"
          type="select"
          multiple
          value={selectedTagIds}
          onChange={event =>
            setSelectedTagIds(Array.from((event.currentTarget as unknown as HTMLSelectElement).selectedOptions).map(option => option.value))
          }
        >
          {tags.map(tag => (
            <option value={tag.id} key={tag.id}>
              {tag.name}
              {tag.active === false ? ` (${translate('fintrackApp.tag.inactive')})` : ''}
            </option>
          ))}
        </Input>
      </FormGroup>
      <FormGroup>
        <Label for="contextual-transaction-rule-logic">
          <Translate contentKey="fintrackApp.transactionRule.conditionLogic">Condition logic</Translate>
        </Label>
        <Input
          id="contextual-transaction-rule-logic"
          data-cy="conditionLogic"
          type="select"
          value={conditionLogic}
          onChange={event => setConditionLogic(event.target.value as RuleConditionLogic)}
        >
          {Object.values(RuleConditionLogic).map(value => (
            <option value={value} disabled={Boolean(requiredFlow) && value === RuleConditionLogic.ANY} key={value}>
              {translate(`fintrackApp.RuleConditionLogic.${value}`)}
            </option>
          ))}
        </Input>
      </FormGroup>
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
      <FormGroup check className="mb-3">
        <Input
          id="contextual-transaction-rule-active"
          data-cy="active"
          type="checkbox"
          checked={active}
          onChange={event => setActive(event.target.checked)}
        />
        <Label check for="contextual-transaction-rule-active">
          <Translate contentKey="fintrackApp.transactionRule.active">Active</Translate>
        </Label>
      </FormGroup>
      <div className="d-flex flex-wrap gap-2">
        <Button type="button" color="secondary" onClick={onCancel} disabled={savingAction !== null}>
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        {saveActions.map(saveAction => (
          <Button
            type={saveAction.action === 'SAVE' ? 'submit' : 'button'}
            color="primary"
            key={saveAction.action}
            data-cy={saveAction.dataCy}
            data-testid={saveAction.dataCy}
            disabled={savingAction !== null || validationMessages.length > 0}
            onClick={saveAction.action === 'SAVE' ? undefined : event => save(event, saveAction.action)}
          >
            {savingAction === saveAction.action ? <FontAwesomeIcon icon="sync" spin /> : <FontAwesomeIcon icon="save" />}{' '}
            <Translate contentKey={saveAction.labelKey}>{saveAction.label}</Translate>
          </Button>
        ))}
      </div>
    </Form>
  );
};

export default TransactionRuleConfiguredForm;
