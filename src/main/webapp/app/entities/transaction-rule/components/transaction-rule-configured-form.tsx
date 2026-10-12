import React, { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Badge, Button, Col, Form, FormFeedback, FormGroup, FormText, Input, Label, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { buildTransactionRuleConditionSummary } from 'app/entities/transaction-rule-condition/transaction-rule-condition-display';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { ContextualRuleSaveAction, IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { ProductConditionLogicControl } from 'app/shared/ui/product-condition-logic-control';
import { ProductSection } from 'app/shared/ui/product-page';
import { ProductStatusToggle } from 'app/shared/ui/product-status-control';
import { ProductTagSelector, ProductTagSelectorTag } from 'app/shared/ui/product-tag-selector';
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

type RuleCategory = ICategorySelectable | null | undefined;

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
const isCompleteCondition = (condition: LocalTransactionRuleCondition) =>
  Boolean(
    condition.field &&
      condition.operator &&
      condition.value?.trim() &&
      (condition.operator !== RuleOperator.BETWEEN || condition.secondValue?.trim()),
  );
const serverErrorMessage = error => error?.response?.data?.detail ?? error?.response?.data?.message;
const categoryLabel = (category: RuleCategory) => [category?.parentCategoryName, category?.name].filter(Boolean).join(' › ');

const categoryIdentityStyle = (color?: string | null): React.CSSProperties | undefined =>
  color
    ? {
        backgroundColor: `${color}14`,
        borderColor: `${color}80`,
        borderInlineStart: `0.35rem solid ${color}`,
      }
    : undefined;

const RuleCategoryIdentity = ({ category, dataCy = 'transactionRuleCategoryIdentity' }: { category: RuleCategory; dataCy?: string }) => {
  if (!category?.name) {
    return (
      <span className="text-muted small" data-cy={`${dataCy}Empty`}>
        <Translate contentKey="fintrackApp.transactionRule.editor.noCategory">No category selected</Translate>
      </span>
    );
  }

  return (
    <span
      className="d-inline-flex flex-wrap align-items-center gap-1 rounded-1 border px-2 py-1 small fw-semibold"
      style={categoryIdentityStyle(category.color)}
      data-cy={dataCy}
      data-testid={dataCy}
      data-color-treatment="category"
    >
      <span>{categoryLabel(category)}</span>
      {category.categoryType ? (
        <Badge color="light" className="border text-dark fw-normal">
          {translate(`fintrackApp.CategoryType.${category.categoryType}`)}
        </Badge>
      ) : null}
      {category.active === false ? (
        <span className="text-muted" data-cy={`${dataCy}Inactive`} data-testid={`${dataCy}Inactive`}>
          · <Translate contentKey="fintrackApp.category.status.inactive">Inactive</Translate>
        </span>
      ) : null}
    </span>
  );
};

const RuleCategorySelector = ({
  categories,
  selectedCategory,
  onChange,
  disabled,
  loading,
  error,
}: {
  categories: ICategorySelectable[];
  selectedCategory: RuleCategory;
  onChange: (categoryId: string) => void;
  disabled: boolean;
  loading: boolean;
  error: boolean;
}) => {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const normalizedQuery = query.trim().toLocaleLowerCase();
  const eligibleCategories = categories.filter(
    category => category.active !== false && (!normalizedQuery || categoryLabel(category).toLocaleLowerCase().includes(normalizedQuery)),
  );

  const selectCategory = (category: ICategorySelectable) => {
    onChange(category.id?.toString() ?? '');
    setOpen(false);
    setQuery('');
  };

  return (
    <div data-cy="transactionRuleCategorySelector">
      <div className="d-flex flex-wrap align-items-center gap-2">
        <RuleCategoryIdentity category={selectedCategory} />
        {selectedCategory?.id !== undefined ? (
          <Button
            type="button"
            color="secondary"
            outline
            size="sm"
            disabled={disabled}
            onClick={() => onChange('')}
            data-cy="transactionRuleCategoryClear"
          >
            <Translate contentKey="entity.action.remove">Remove</Translate>
          </Button>
        ) : null}
        <Button
          type="button"
          color="secondary"
          outline
          size="sm"
          disabled={disabled || loading}
          onClick={() => setOpen(current => !current)}
          aria-expanded={open}
          data-cy="transactionRuleCategorySelect"
        >
          {loading ? (
            <Translate contentKey="fintrackApp.transactionRule.editor.loadingCategories">Loading categories…</Translate>
          ) : (
            <>
              {selectedCategory?.id !== undefined ? <FontAwesomeIcon icon="pencil-alt" /> : <FontAwesomeIcon icon="plus" />}{' '}
              <Translate contentKey="fintrackApp.transactionRule.editor.selectCategory">Choose category</Translate>
            </>
          )}
        </Button>
      </div>
      {selectedCategory?.active === false ? (
        <FormText className="d-block mt-2" data-cy="transactionRuleInactiveCategoryHelp">
          <Translate contentKey="fintrackApp.transactionRule.editor.inactiveCategoryHistorical">
            This inactive category is retained as a historical output. It will not be newly suggested until it is reactivated.
          </Translate>
        </FormText>
      ) : null}
      {error ? (
        <div className="small text-danger mt-2" role="alert" data-cy="transactionRuleCategoryLoadError">
          <Translate contentKey="fintrackApp.transactionRule.editor.categoriesLoadFailed">
            Categories could not be loaded. The existing result is unchanged.
          </Translate>
        </div>
      ) : null}
      {open && !disabled ? (
        <div className="border rounded-2 bg-white p-2 mt-2" data-cy="transactionRuleCategoryPicker">
          <label className="visually-hidden" htmlFor="transaction-rule-category-search">
            <Translate contentKey="fintrackApp.transactionRule.editor.searchCategories">Search categories</Translate>
          </label>
          <Input
            id="transaction-rule-category-search"
            type="search"
            value={query}
            onChange={event => setQuery(event.target.value)}
            placeholder={translate('fintrackApp.transactionRule.editor.searchCategories')}
            data-cy="transactionRuleCategorySearch"
          />
          <div className="d-flex flex-column align-items-stretch gap-1 mt-2" role="listbox">
            {eligibleCategories.length ? (
              eligibleCategories.map(category => (
                <Button
                  type="button"
                  color="light"
                  className="border text-start"
                  key={category.id}
                  onClick={() => selectCategory(category)}
                  data-cy="transactionRuleCategoryOption"
                  data-category-id={category.id}
                  style={categoryIdentityStyle(category.color)}
                >
                  <span className="fw-semibold">{categoryLabel(category)}</span>
                  {category.categoryType ? (
                    <span className="text-muted small ms-2">{translate(`fintrackApp.CategoryType.${category.categoryType}`)}</span>
                  ) : null}
                </Button>
              ))
            ) : (
              <span className="text-muted small px-1 py-2" data-cy="transactionRuleCategoryNoOptions">
                <Translate contentKey="fintrackApp.transactionRule.editor.noCategoryMatches">No active categories found</Translate>
              </span>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
};

const RuleConfigurationPreview = ({
  conditionLogic,
  conditions,
  category,
  tags,
}: {
  conditionLogic: RuleConditionLogic;
  conditions: LocalTransactionRuleCondition[];
  category: RuleCategory;
  tags: ProductTagSelectorTag[];
}) => {
  const completeConditions = conditions.filter(condition => condition.field && condition.operator && condition.value?.trim());

  return (
    <ProductSection
      title={<Translate contentKey="fintrackApp.transactionRule.editor.configurationPreview">Configuration summary</Translate>}
      dataCy="transactionRuleConfigurationPreview"
    >
      <div className="small text-muted mb-3">
        <Translate contentKey="fintrackApp.transactionRule.editor.configurationPreviewHelp">
          This describes the rule configuration; it does not evaluate a specific transaction.
        </Translate>
      </div>
      <h4 className="h6 mb-1">
        <Translate contentKey="fintrackApp.transactionRule.sections.when">When</Translate>
      </h4>
      <p className="mb-2" data-cy="transactionRuleConfigurationLogic">
        <Translate contentKey={`fintrackApp.transactionRule.when.${conditionLogic === RuleConditionLogic.ALL ? 'all' : 'any'}`}>
          Apply when all conditions match.
        </Translate>
      </p>
      {completeConditions.length ? (
        <ul className="mb-3 ps-3" data-cy="transactionRuleConfigurationConditions">
          {completeConditions.map((condition, index) => (
            <li key={condition.clientId ?? condition.id ?? index}>{buildTransactionRuleConditionSummary(condition, translate)}</li>
          ))}
        </ul>
      ) : (
        <p className="text-muted mb-3">
          <Translate contentKey="fintrackApp.transactionRule.summary.noConditions">No conditions configured.</Translate>
        </p>
      )}
      <h4 className="h6 mb-1">
        <Translate contentKey="fintrackApp.transactionRule.sections.then">Then</Translate>
      </h4>
      <div className="d-flex flex-column align-items-start gap-2" data-cy="transactionRuleConfigurationResults">
        {category?.id !== undefined ? (
          <div>
            <span className="small text-muted me-2">
              <Translate contentKey="fintrackApp.transactionRule.then.assignCategory">Assign category</Translate>
            </span>
            <RuleCategoryIdentity category={category} dataCy="transactionRulePreviewCategory" />
          </div>
        ) : null}
        {tags.length ? (
          <div className="d-flex flex-wrap align-items-center gap-2">
            <span className="small text-muted">
              <Translate contentKey="fintrackApp.transactionRule.then.addTags">Add tags</Translate>
            </span>
            {tags.map((tag, index) => (
              <span
                className="d-inline-flex align-items-center rounded-pill border px-2 py-1 small fw-semibold"
                style={categoryIdentityStyle(tag.color)}
                data-cy="transactionRulePreviewTag"
                data-color-treatment="tag"
                key={tag.id ?? `${tag.name}-${index}`}
              >
                {tag.name}
                {tag.active === false ? (
                  <span className="ms-1 text-muted">
                    · <Translate contentKey="fintrackApp.category.status.inactive">Inactive</Translate>
                  </span>
                ) : null}
              </span>
            ))}
          </div>
        ) : null}
        {category?.id === undefined && !tags.length ? (
          <span className="text-muted small">
            <Translate contentKey="fintrackApp.transactionRule.result.empty">No result configured</Translate>
          </span>
        ) : null}
      </div>
    </ProductSection>
  );
};

/** One configured TransactionRule editor shared by standalone and contextual compositions. */
export const TransactionRuleConfiguredForm = ({ initialValue, saveActions, onSaved, onCancel }: TransactionRuleConfiguredFormProps) => {
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState(false);
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
  const savingRef = useRef(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const selectedCategory = useMemo<ICategorySelectable | null>(() => {
    const selectableCategory = categories.find(category => category.id?.toString() === selectedCategoryId);
    if (selectableCategory) return selectableCategory;
    if (initialValue?.resultingCategory?.id?.toString() === selectedCategoryId) {
      return initialValue.resultingCategory as ICategorySelectable;
    }
    return null;
  }, [categories, initialValue?.resultingCategory, selectedCategoryId]);
  const requiredFlow = requiredFlowForCategory(selectedCategory);

  const selectedTags = useMemo<ProductTagSelectorTag[]>(
    () =>
      selectedTagIds.map(tagId => {
        const selectableTag = tags.find(tag => tag.id?.toString() === tagId);
        return selectableTag ?? initialValue?.resultingTags?.find(tag => tag.id?.toString() === tagId) ?? { id: Number(tagId) };
      }),
    [initialValue?.resultingTags, selectedTagIds, tags],
  );

  useEffect(() => {
    let mounted = true;
    const categoryIds = initialValue?.resultingCategory?.id === undefined ? [] : [initialValue.resultingCategory.id];
    const tagIds = initialValue?.resultingTags?.map(tag => tag.id).filter((id): id is number => id !== undefined) ?? [];
    setCatalogLoading(true);
    setCatalogError(false);
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
          setCatalogError(true);
        }
      })
      .finally(() => {
        if (mounted) setCatalogLoading(false);
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
      if (current.some(condition => condition.field === TransactionRuleField.FLOW)) return current;
      return normalizePositions([...current, createRequiredFlowCondition(requiredFlow, current.length)]);
    });
  }, [requiredFlow]);

  useEffect(() => {
    if (requiredFlow && conditionLogic === RuleConditionLogic.ANY) setConditionLogic(RuleConditionLogic.ALL);
  }, [conditionLogic, requiredFlow]);

  const validationMessages = useMemo(() => {
    const messages: string[] = [];
    const creatingRule = initialValue?.id === undefined;
    if (!ruleName.trim()) messages.push(translate('entity.validation.required'));
    if (!conditions.length) messages.push(translate('fintrackApp.transactionRule.validation.conditionsRequired'));
    if (conditions.length && conditions.some(condition => !isCompleteCondition(condition))) {
      messages.push(translate('fintrackApp.transactionRule.validation.conditionsIncomplete'));
    }
    if (!hasAnyOutput(selectedCategoryId, selectedTagIds))
      messages.push(translate('fintrackApp.transactionRule.validation.outputRequired'));
    if (catalogError) {
      messages.push(translate('fintrackApp.transactionRule.validation.outputsUnavailable'));
    }
    if (creatingRule && (selectedCategory?.active === false || selectedTags.some(tag => tag.active === false))) {
      messages.push(translate('fintrackApp.transactionRule.validation.inactiveOutputsCannotBeUsedForNewRule'));
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
  }, [
    catalogError,
    conditions,
    conditionLogic,
    initialValue?.id,
    requiredFlow,
    ruleName,
    selectedCategory,
    selectedCategoryId,
    selectedTagIds,
    selectedTags,
  ]);

  const save = async (event: FormEvent, action: ContextualRuleSaveAction) => {
    event.preventDefault();
    if (savingRef.current) return;
    setSubmitAttempted(true);
    if (validationMessages.length) return;
    savingRef.current = true;
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
      setErrorMessage(serverErrorMessage(requestError) ?? translate('fintrackApp.transactionRule.configuredSaveFailed'));
    } finally {
      savingRef.current = false;
      setSavingAction(null);
    }
  };

  return (
    <Form noValidate onSubmit={event => save(event, 'SAVE')} data-cy="transactionRuleContextualForm">
      {errorMessage ? (
        <Alert color="danger" fade={false} role="alert" data-cy="transactionRuleConfiguredError">
          {errorMessage}
        </Alert>
      ) : null}
      {submitAttempted && validationMessages.length ? (
        <Alert
          color="danger"
          className="py-2 mb-3"
          data-cy="configuredValidationMessages"
          data-testid="configuredValidationMessages"
          fade={false}
          role="alert"
        >
          <Translate contentKey="fintrackApp.transactionRule.editor.validationSummary">
            Review the highlighted rule configuration sections before saving.
          </Translate>
          <ul className="mb-0 mt-1 small">
            {validationMessages.map(message => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </Alert>
      ) : null}

      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionRule.sections.identity">Identity</Translate>}
        dataCy="transactionRuleIdentitySection"
      >
        <FormGroup>
          <Label for="contextual-transaction-rule-name">
            <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>
          </Label>
          <Input
            id="contextual-transaction-rule-name"
            data-cy="name"
            value={ruleName}
            onChange={event => setRuleName(event.target.value)}
            invalid={submitAttempted && !ruleName.trim()}
            required
            maxLength={100}
          />
          {submitAttempted && !ruleName.trim() ? <FormFeedback>{translate('entity.validation.required')}</FormFeedback> : null}
        </FormGroup>
        <FormGroup className="mb-0">
          <Label for="contextual-transaction-rule-description">
            <Translate contentKey="fintrackApp.transactionRule.description">Description</Translate>
          </Label>
          <Input
            id="contextual-transaction-rule-description"
            data-cy="description"
            type="textarea"
            value={ruleDescription}
            onChange={event => setRuleDescription(event.target.value)}
            maxLength={500}
          />
        </FormGroup>
      </ProductSection>

      <ProductSection
        className="mt-3"
        title={<Translate contentKey="fintrackApp.transactionRule.sections.matching">Matching conditions</Translate>}
        dataCy="transactionRuleMatchingSection"
      >
        <ProductConditionLogicControl
          idPrefix="contextual-transaction-rule"
          dataCyPrefix="transactionRule"
          value={conditionLogic}
          onChange={setConditionLogic}
          label={translate('fintrackApp.transactionRule.conditionLogic')}
          allLabel={translate('fintrackApp.RuleConditionLogic.ALL')}
          anyLabel={translate('fintrackApp.RuleConditionLogic.ANY')}
          help={translate(`fintrackApp.transactionRule.when.${conditionLogic === RuleConditionLogic.ALL ? 'all' : 'any'}`)}
          anyDisabled={Boolean(requiredFlow)}
          restrictionHelp={
            requiredFlow
              ? translate(
                  requiredFlow === TransactionFlow.OUT
                    ? 'fintrackApp.transactionRule.editor.expenseFlowNotice'
                    : 'fintrackApp.transactionRule.editor.incomeFlowNotice',
                )
              : undefined
          }
          restrictionDataCy="transactionRuleRequiredFlowNotice"
        />
        <TransactionRuleLocalConditionsEditor
          conditions={conditions}
          onChange={setConditions}
          lockedFlow={requiredFlow}
          showValidation={submitAttempted}
        />
      </ProductSection>

      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionRule.sections.result">Results</Translate>}
        className="mt-3"
        dataCy="transactionRuleResultsSection"
      >
        <Row className="g-4">
          <Col md="6">
            <Label className="d-block" for="transaction-rule-category-search">
              <Translate contentKey="fintrackApp.transactionRule.resultingCategory">Resulting category</Translate>
            </Label>
            <RuleCategorySelector
              categories={categories}
              selectedCategory={selectedCategory}
              onChange={setSelectedCategoryId}
              disabled={savingAction !== null}
              loading={catalogLoading}
              error={catalogError}
            />
          </Col>
          <Col md="6">
            <div className="small fw-semibold mb-2">
              <Translate contentKey="fintrackApp.transactionRule.resultingTags">Resulting tags</Translate>
            </div>
            <ProductTagSelector
              id="contextual-transaction-rule-tags"
              dataCy="transactionRuleResultTags"
              selectedTags={selectedTags}
              availableTags={tags}
              disabled={savingAction !== null}
              loading={catalogLoading}
              error={catalogError}
              onChange={nextTags => setSelectedTagIds(nextTags.map(tag => tag.id?.toString()).filter((id): id is string => Boolean(id)))}
            />
            {selectedTags.some(tag => tag.active === false) ? (
              <FormText className="d-block mt-2" data-cy="transactionRuleInactiveTagHelp">
                <Translate contentKey="fintrackApp.transactionRule.editor.inactiveTagsHistorical">
                  Inactive tags are retained as historical outputs and are not newly offered for selection.
                </Translate>
              </FormText>
            ) : null}
          </Col>
        </Row>
        <FormText className="d-block mt-3">
          <Translate contentKey="fintrackApp.transactionRule.editor.outputHelp">
            Choose a category, tags, or both. Only active outputs can be newly selected.
          </Translate>
        </FormText>
      </ProductSection>

      <div className="mt-3">
        <RuleConfigurationPreview conditionLogic={conditionLogic} conditions={conditions} category={selectedCategory} tags={selectedTags} />
      </div>

      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionRule.sections.status">Status</Translate>}
        className="mt-3"
        dataCy="transactionRuleStatusSection"
      >
        <ProductStatusToggle
          id="contextual-transaction-rule-active"
          name="transactionRuleActive"
          active={active}
          onActiveChange={setActive}
          label={translate('fintrackApp.transactionRule.active')}
          help={translate('fintrackApp.transactionRule.editor.statusHelp')}
          activeLabel={translate('fintrackApp.transactionRule.status.active')}
          inactiveLabel={translate('fintrackApp.transactionRule.status.inactive')}
          dataCyPrefix="transactionRule"
          disabled={savingAction !== null}
        />
      </ProductSection>

      <FormText className="d-block my-3">
        <Translate contentKey="fintrackApp.transactionRule.priorityServerManagedHelp">
          Rule order is managed from the rules list. New rules are added last.
        </Translate>
      </FormText>
      <div className="d-flex flex-wrap gap-2">
        <Button type="button" color="secondary" outline onClick={onCancel} disabled={savingAction !== null}>
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        {saveActions.map(saveAction => (
          <Button
            type={saveAction.action === 'SAVE' ? 'submit' : 'button'}
            color="primary"
            key={saveAction.action}
            data-cy={saveAction.dataCy}
            data-testid={saveAction.dataCy}
            disabled={savingAction !== null || catalogLoading}
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
