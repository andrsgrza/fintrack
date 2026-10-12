import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { DndContext, DragEndEvent, KeyboardSensor, MouseSensor, TouchSensor, closestCenter, useSensor, useSensors } from '@dnd-kit/core';
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Button, DropdownItem, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowDown, faArrowUp, faGripVertical } from '@fortawesome/free-solid-svg-icons';

import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { ICategory } from 'app/shared/model/category.model';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ITag } from 'app/shared/model/tag.model';
import {
  buildTransactionRuleManagerConditionSummary,
  formatTransactionRuleConditionLogic,
  formatTransactionRuleStatus,
} from './transaction-rule-display';
import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { TransactionCategory, TransactionTagChips } from 'app/entities/financial-transaction/transaction-presentation';

const apiUrl = 'api/transaction-rules';

type RuleWithConditions = ITransactionRule & {
  conditions: ITransactionRuleCondition[];
  conditionsError?: boolean;
};

type RuleCatalogs = {
  categories: ICategory[];
  tags: ITag[];
  accounts: IFinancialAccount[];
};

const sortRules = (rules: RuleWithConditions[]) =>
  [...rules].sort(
    (firstRule, secondRule) =>
      (firstRule.priority ?? Number.MAX_SAFE_INTEGER) - (secondRule.priority ?? Number.MAX_SAFE_INTEGER) ||
      (firstRule.id ?? Number.MAX_SAFE_INTEGER) - (secondRule.id ?? Number.MAX_SAFE_INTEGER),
  );

const withCatalogIdentity = (rule: RuleWithConditions, catalogs: RuleCatalogs): RuleWithConditions => {
  const categoriesById = new Map(
    catalogs.categories.filter(category => category.id !== undefined).map(category => [category.id, category]),
  );
  const tagsById = new Map(catalogs.tags.filter(tag => tag.id !== undefined).map(tag => [tag.id, tag]));

  return {
    ...rule,
    resultingCategory:
      rule.resultingCategory?.id !== undefined
        ? (categoriesById.get(rule.resultingCategory.id) ?? rule.resultingCategory)
        : rule.resultingCategory,
    resultingTags: (rule.resultingTags ?? []).map(tag => (tag.id !== undefined ? (tagsById.get(tag.id) ?? tag) : tag)),
  };
};

const fetchConditions = async (rules: ITransactionRule[]): Promise<RuleWithConditions[]> => {
  const results: RuleWithConditions[] = [];
  const batchSize = 6;

  for (let index = 0; index < rules.length; index += batchSize) {
    const batch = rules.slice(index, index + batchSize);
    const resolvedBatch = await Promise.all(
      batch.map(async rule => {
        try {
          const response = await axios.get<ITransactionRuleCondition[]>(`${apiUrl}/${rule.id}/conditions`);
          return { ...rule, conditions: response.data };
        } catch {
          return { ...rule, conditions: [], conditionsError: true };
        }
      }),
    );
    results.push(...resolvedBatch);
  }

  return results;
};

type SortableRuleCardProps = {
  rule: RuleWithConditions;
  index: number;
  ruleCount: number;
  catalogs: RuleCatalogs;
  locale: string;
  reordering: boolean;
  reorderAvailable: boolean;
  dependentDataUnavailable: boolean;
  onMove: (fromIndex: number, toIndex: number) => void;
};

const SortableRuleCard = ({
  rule,
  index,
  ruleCount,
  catalogs,
  locale,
  reordering,
  reorderAvailable,
  dependentDataUnavailable,
  onMove,
}: SortableRuleCardProps) => {
  const { attributes, listeners, setActivatorNodeRef, setNodeRef, transform, transition, isDragging } = useSortable({
    id: rule.id ?? `missing-${index}`,
    disabled: !reorderAvailable || reordering,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.55 : 1,
  };
  const accountsById = useMemo(
    () => new Map(catalogs.accounts.filter(account => account.id !== undefined).map(account => [account.id, account])),
    [catalogs.accounts],
  );
  const visibleConditions = rule.conditions.slice(0, 2);
  const extraConditions = rule.conditions.length - visibleConditions.length;
  const hasInactiveOutputs = rule.resultingCategory?.active === false || (rule.resultingTags ?? []).some(tag => tag.active === false);

  return (
    <article
      ref={setNodeRef}
      style={style}
      className={`border rounded-3 bg-white p-3 p-md-4 ${isDragging ? 'shadow' : 'shadow-sm'}`}
      data-cy={`transactionRuleCard-${rule.id}`}
      data-testid={`transactionRuleCard-${rule.id}`}
    >
      <div className="d-flex flex-column flex-md-row gap-3 justify-content-between">
        <div className="d-flex gap-3 flex-grow-1 min-w-0">
          {reorderAvailable ? (
            <Button
              type="button"
              color="link"
              className="p-0 text-muted align-self-start"
              aria-label={translate('fintrackApp.transactionRule.reorder.dragHandle', { name: rule.name ?? '' })}
              title={translate('fintrackApp.transactionRule.reorder.dragHandle', { name: rule.name ?? '' })}
              data-cy={`transactionRuleDragHandle-${rule.id}`}
              data-testid={`transactionRuleDragHandle-${rule.id}`}
              innerRef={setActivatorNodeRef}
              style={{ touchAction: 'none' }}
              {...attributes}
              {...listeners}
            >
              <FontAwesomeIcon icon={faGripVertical} size="lg" />
            </Button>
          ) : null}
          <div className="flex-grow-1 min-w-0">
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <span className="small fw-semibold text-muted" data-cy={`transactionRulePosition-${rule.id}`}>
                {translate('fintrackApp.transactionRule.reorder.position', { position: index + 1 })}
              </span>
              <span className={`badge rounded-pill ${rule.active ? 'text-bg-success' : 'text-bg-secondary'}`}>
                {formatTransactionRuleStatus(rule, translate)}
              </span>
            </div>
            <h3 className="h5 mb-2">
              <Link to={`/transaction-rule/${rule.id}`} className="text-decoration-none">
                {rule.name}
              </Link>
            </h3>

            <div className="small text-uppercase text-muted fw-semibold mb-1">
              <Translate contentKey="fintrackApp.transactionRule.summary.matching">Matching</Translate>
            </div>
            {rule.conditionsError ? (
              <p className="mb-2 text-danger small" data-cy={`transactionRuleConditionsUnavailable-${rule.id}`}>
                <Translate contentKey="fintrackApp.transactionRule.summary.conditionsUnavailable">
                  Conditions could not be loaded.
                </Translate>
              </p>
            ) : (
              <div
                className="mb-2 text-break"
                data-cy={`transactionRuleSummary-${rule.id}`}
                data-testid={`transactionRuleSummary-${rule.id}`}
              >
                <span className="fw-semibold">
                  <Translate contentKey="fintrackApp.transactionRule.summary.if">If</Translate>{' '}
                  {formatTransactionRuleConditionLogic(rule.conditionLogic, translate)}:
                </span>{' '}
                {visibleConditions.length ? (
                  visibleConditions.map((condition, conditionIndex) => (
                    <React.Fragment key={condition.id ?? `${condition.field}-${condition.operator}-${conditionIndex}`}>
                      {conditionIndex > 0 ? <span className="text-muted"> · </span> : null}
                      {buildTransactionRuleManagerConditionSummary(condition, translate, accountsById, locale)}
                    </React.Fragment>
                  ))
                ) : (
                  <span className="text-muted">
                    <Translate contentKey="fintrackApp.transactionRule.summary.noConditions">No conditions configured.</Translate>
                  </span>
                )}
                {extraConditions > 0 ? (
                  <span className="text-muted">
                    {' '}
                    {translate('fintrackApp.transactionRule.summary.moreConditions', { count: extraConditions })}
                  </span>
                ) : null}
              </div>
            )}

            <div className="small text-uppercase text-muted fw-semibold mb-1">
              <Translate contentKey="fintrackApp.transactionRule.summary.result">Suggestions</Translate>
            </div>
            {rule.resultingCategory || rule.resultingTags?.length ? (
              <div className="d-flex flex-wrap align-items-center gap-2" data-cy={`transactionRuleResults-${rule.id}`}>
                {rule.resultingCategory ? <TransactionCategory category={rule.resultingCategory} /> : null}
                {rule.resultingTags?.length ? <TransactionTagChips tags={rule.resultingTags} maxVisible={4} /> : null}
                {hasInactiveOutputs ? (
                  <span className="small text-muted" data-cy={`transactionRuleInactiveOutput-${rule.id}`}>
                    <Translate contentKey="fintrackApp.transactionRule.summary.inactiveHistoricalOutput">
                      Inactive historical output
                    </Translate>
                  </span>
                ) : null}
              </div>
            ) : (
              <p className="mb-0 text-muted small">
                <Translate contentKey="fintrackApp.transactionRule.result.empty">No result configured</Translate>
              </p>
            )}
            {dependentDataUnavailable ? (
              <p className="mb-0 mt-2 text-muted small" data-cy={`transactionRuleResultIdentityUnavailable-${rule.id}`}>
                <Translate contentKey="fintrackApp.transactionRule.summary.outputDetailsUnavailable">
                  Some output identity details are temporarily unavailable.
                </Translate>
              </p>
            ) : null}
          </div>
        </div>

        <div className="d-flex flex-wrap align-content-start justify-content-md-end gap-2 flex-shrink-0">
          {reorderAvailable ? (
            <div className="btn-group" role="group" aria-label={translate('fintrackApp.transactionRule.reorder.actions')}>
              <Button
                color="secondary"
                outline
                size="sm"
                disabled={reordering || index === 0}
                onClick={() => onMove(index, index - 1)}
                aria-label={translate('fintrackApp.transactionRule.moveUp')}
                title={translate('fintrackApp.transactionRule.moveUp')}
                data-cy={`transactionRuleMoveUp-${rule.id}`}
                data-testid={`transactionRuleMoveUp-${rule.id}`}
              >
                <FontAwesomeIcon icon={faArrowUp} />
              </Button>
              <Button
                color="secondary"
                outline
                size="sm"
                disabled={reordering || index === ruleCount - 1}
                onClick={() => onMove(index, index + 1)}
                aria-label={translate('fintrackApp.transactionRule.moveDown')}
                title={translate('fintrackApp.transactionRule.moveDown')}
                data-cy={`transactionRuleMoveDown-${rule.id}`}
                data-testid={`transactionRuleMoveDown-${rule.id}`}
              >
                <FontAwesomeIcon icon={faArrowDown} />
              </Button>
            </div>
          ) : null}
          <Button
            tag={Link}
            to={`/transaction-rule/${rule.id}`}
            color="secondary"
            outline
            size="sm"
            data-cy={`transactionRuleView-${rule.id}`}
          >
            <FontAwesomeIcon icon="eye" /> <Translate contentKey="entity.action.view">View</Translate>
          </Button>
          <Button tag={Link} to={`/transaction-rule/${rule.id}/edit`} color="primary" size="sm" data-cy={`transactionRuleEdit-${rule.id}`}>
            <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
          </Button>
          <ProductActionsMenu label={translate('fintrackApp.transactionRule.actions')} dataCy={`transactionRuleActions-${rule.id}`}>
            <DropdownItem
              tag={Link}
              to={`/transaction-rule/${rule.id}/delete`}
              className="text-danger"
              data-cy={`transactionRuleDelete-${rule.id}`}
            >
              <FontAwesomeIcon icon="trash" className="me-2" />
              <Translate contentKey="entity.action.delete">Delete</Translate>
            </DropdownItem>
          </ProductActionsMenu>
        </div>
      </div>
    </article>
  );
};

export const TransactionRule = () => {
  const account = useAppSelector(state => state.authentication?.account);
  const locale = useAppSelector(state => state.locale?.currentLocale) || 'en';
  const isAdmin = account?.authorities?.includes(AUTHORITIES.ADMIN) ?? false;
  const [rules, setRules] = useState<RuleWithConditions[]>([]);
  const [catalogs, setCatalogs] = useState<RuleCatalogs>({ categories: [], tags: [], accounts: [] });
  const confirmedRules = useRef<RuleWithConditions[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [dependentDataUnavailable, setDependentDataUnavailable] = useState(false);
  const [reordering, setReordering] = useState(false);
  const [reorderError, setReorderError] = useState(false);

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const rulesResponse = await axios.get<ITransactionRule[]>(`${apiUrl}?sort=priority,ASC&sort=id,ASC`);
      const [categoryResult, tagResult, accountResult] = await Promise.allSettled([
        axios.get<ICategory[]>('api/categories?sort=name,ASC'),
        axios.get<ITag[]>('api/tags?sort=name,ASC'),
        axios.get<IFinancialAccount[]>('api/financial-accounts?sort=name,ASC'),
      ]);
      const nextCatalogs: RuleCatalogs = {
        categories: categoryResult.status === 'fulfilled' ? categoryResult.value.data : [],
        tags: tagResult.status === 'fulfilled' ? tagResult.value.data : [],
        accounts: accountResult.status === 'fulfilled' ? accountResult.value.data : [],
      };
      const nextRules = sortRules((await fetchConditions(rulesResponse.data)).map(rule => withCatalogIdentity(rule, nextCatalogs)));
      confirmedRules.current = nextRules;
      setCatalogs(nextCatalogs);
      setRules(nextRules);
      setDependentDataUnavailable(
        categoryResult.status === 'rejected' || tagResult.status === 'rejected' || accountResult.status === 'rejected',
      );
    } catch {
      setLoadError(true);
      setRules(confirmedRules.current);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const reorderAvailable = !isAdmin && rules.length > 0 && rules.every(rule => rule.id !== undefined);
  const ruleIds = useMemo(() => rules.map(rule => rule.id ?? `missing-${rule.name}`), [rules]);

  const persistOrder = async (nextRules: RuleWithConditions[]) => {
    if (reordering || !reorderAvailable) return;

    setReorderError(false);
    setReordering(true);
    setRules(nextRules);
    try {
      await axios.put(`${apiUrl}/reorder`, { orderedIds: nextRules.map(rule => rule.id) });
      await load();
    } catch {
      setRules(confirmedRules.current);
      setReorderError(true);
    } finally {
      setReordering(false);
    }
  };

  const move = (fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= rules.length || toIndex >= rules.length) return;
    void persistOrder(arrayMove(rules, fromIndex, toIndex));
  };

  const onDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id || reordering || !reorderAvailable) return;
    const fromIndex = rules.findIndex(rule => rule.id === active.id);
    const toIndex = rules.findIndex(rule => rule.id === over.id);
    move(fromIndex, toIndex);
  };

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="transaction-rule-heading"
        dataCy="TransactionRuleHeading"
        title={<Translate contentKey="fintrackApp.transactionRule.home.title">Classification Rules</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.transactionRule.home.subtitle">
            Configure category and tag suggestions for transaction review.
          </Translate>
        }
        actions={
          <>
            <Button tag={Link} to="/rules" color="secondary" outline size="sm" data-cy="transactionRuleBackToRules">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="fintrackApp.rules.back">Back to rules</Translate>
            </Button>
            <Button
              color="secondary"
              outline
              size="sm"
              onClick={() => void load()}
              disabled={loading || reordering}
              data-cy="transactionRuleRefresh"
            >
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.transactionRule.home.refreshListLabel">Refresh list</Translate>
            </Button>
            <Button tag={Link} to="/transaction-rule/new" color="primary" size="sm" id="jh-create-entity" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.transactionRule.home.createLabel">Create rule</Translate>
            </Button>
          </>
        }
      />

      <ProductSection title={<Translate contentKey="fintrackApp.transactionRule.reorder.title">Evaluation order</Translate>}>
        <p className="mb-0 text-muted">
          <Translate contentKey="fintrackApp.transactionRule.reorder.explanation">
            Active rules are evaluated in order. Multiple rules can match: the first eligible category wins, and eligible tags from matching
            rules accumulate without duplicates. Inactive rules do not participate.
          </Translate>
        </p>
        {isAdmin ? (
          <p
            className="small text-muted mt-2 mb-0"
            data-cy="transactionRuleAdminReorderUnavailable"
            data-testid="transactionRuleAdminReorderUnavailable"
          >
            <Translate contentKey="fintrackApp.transactionRule.reorder.adminUnavailable">
              Reordering is unavailable here because this administrator list can include rules owned by different users.
            </Translate>
          </p>
        ) : null}
      </ProductSection>

      {reorderError ? (
        <div className="alert alert-danger mt-3" role="alert" data-cy="transactionRuleReorderError">
          <Translate contentKey="fintrackApp.transactionRule.reorder.failed">
            The order could not be saved. The last confirmed order has been restored.
          </Translate>
        </div>
      ) : null}
      {loadError ? (
        <div className="alert alert-danger mt-3" role="alert" data-cy="transactionRuleLoadError">
          <Translate contentKey="fintrackApp.transactionRule.home.loadFailed">
            Classification rules could not be loaded. Try refreshing the list.
          </Translate>
        </div>
      ) : null}
      {dependentDataUnavailable ? (
        <div className="alert alert-warning mt-3 mb-0" role="alert" data-cy="transactionRuleDependentDataWarning">
          <Translate contentKey="fintrackApp.transactionRule.summary.outputDetailsUnavailable">
            Some output identity details are temporarily unavailable.
          </Translate>
        </div>
      ) : null}

      {loading && rules.length === 0 ? (
        <p className="text-muted mt-4" role="status" aria-live="polite" data-cy="transactionRuleLoading">
          <Spinner tag="span" size="sm" className="me-2" />
          <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </p>
      ) : null}

      {!loading && !loadError && rules.length === 0 ? (
        <ProductSection
          className="mt-3"
          title={<Translate contentKey="fintrackApp.transactionRule.empty.title">No classification rules yet</Translate>}
        >
          <p className="text-muted mb-3">
            <Translate contentKey="fintrackApp.transactionRule.empty.body">
              Create a rule to suggest a category, tags, or both during transaction review.
            </Translate>
          </p>
          <Button
            tag={Link}
            to="/transaction-rule/new"
            color="primary"
            data-cy="transactionRuleEmptyCreate"
            data-testid="transactionRuleEmptyCreate"
          >
            <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.transactionRule.home.createLabel">Create rule</Translate>
          </Button>
        </ProductSection>
      ) : null}

      {rules.length > 0 ? (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext items={ruleIds} strategy={verticalListSortingStrategy}>
            <div className="d-grid gap-3 mt-3" aria-busy={reordering} data-cy="transactionRuleList">
              {rules.map((rule, index) => (
                <SortableRuleCard
                  key={rule.id}
                  rule={rule}
                  index={index}
                  ruleCount={rules.length}
                  catalogs={catalogs}
                  locale={locale}
                  reordering={reordering}
                  reorderAvailable={reorderAvailable}
                  dependentDataUnavailable={dependentDataUnavailable}
                  onMove={move}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      ) : null}
    </ProductPage>
  );
};

export default TransactionRule;
