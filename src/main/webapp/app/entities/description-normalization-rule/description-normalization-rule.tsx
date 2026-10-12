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
import { IDescriptionNormalizationRule } from 'app/shared/model/description-normalization-rule.model';
import { IDescriptionNormalizationRuleConfiguredCondition } from 'app/shared/model/description-normalization-rule-configured.model';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import {
  formatDescriptionNormalizationConditionSummary,
  formatDescriptionNormalizationLogic,
  formatDescriptionNormalizationRuleStatus,
} from './description-normalization-rule-display';

const apiUrl = 'api/description-normalization-rules';

type RuleWithConditions = IDescriptionNormalizationRule & {
  conditions: IDescriptionNormalizationRuleConfiguredCondition[];
};

const sortRules = (rules: RuleWithConditions[]) =>
  [...rules].sort(
    (firstRule, secondRule) =>
      (firstRule.priority ?? Number.MAX_SAFE_INTEGER) - (secondRule.priority ?? Number.MAX_SAFE_INTEGER) ||
      (firstRule.id ?? Number.MAX_SAFE_INTEGER) - (secondRule.id ?? Number.MAX_SAFE_INTEGER),
  );

type SortableRuleCardProps = {
  rule: RuleWithConditions;
  index: number;
  ruleCount: number;
  reordering: boolean;
  reorderAvailable: boolean;
  onMove: (fromIndex: number, toIndex: number) => void;
};

const SortableRuleCard = ({ rule, index, ruleCount, reordering, reorderAvailable, onMove }: SortableRuleCardProps) => {
  const { attributes, listeners, setActivatorNodeRef, setNodeRef, transform, transition, isDragging } = useSortable({
    id: rule.id ?? `missing-${index}`,
    disabled: !reorderAvailable || reordering,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.55 : 1,
  };
  const status = formatDescriptionNormalizationRuleStatus(rule.active, translate);
  const summary = formatDescriptionNormalizationConditionSummary(rule, translate, 2);

  return (
    <article
      ref={setNodeRef}
      style={style}
      className={`border rounded-3 bg-white p-3 p-md-4 ${isDragging ? 'shadow' : 'shadow-sm'}`}
      data-cy={`descriptionNormalizationRuleCard-${rule.id}`}
      data-testid={`descriptionNormalizationRuleCard-${rule.id}`}
    >
      <div className="d-flex flex-column flex-md-row gap-3 justify-content-between">
        <div className="d-flex gap-3 flex-grow-1 min-w-0">
          {reorderAvailable ? (
            <Button
              type="button"
              color="link"
              className="p-0 text-muted align-self-start"
              aria-label={translate('fintrackApp.descriptionNormalizationRule.reorder.dragHandle', { name: rule.name ?? '' })}
              title={translate('fintrackApp.descriptionNormalizationRule.reorder.dragHandle', { name: rule.name ?? '' })}
              data-cy={`descriptionNormalizationRuleDragHandle-${rule.id}`}
              data-testid={`descriptionNormalizationRuleDragHandle-${rule.id}`}
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
              <span className="small fw-semibold text-muted" data-cy={`descriptionNormalizationRulePosition-${rule.id}`}>
                {translate('fintrackApp.descriptionNormalizationRule.reorder.position', { position: index + 1 })}
              </span>
              <span className={`badge rounded-pill ${rule.active ? 'text-bg-success' : 'text-bg-secondary'}`}>{status}</span>
            </div>
            <h3 className="h5 mb-2">
              <Link to={`/description-normalization-rule/${rule.id}`} className="text-decoration-none">
                {rule.name}
              </Link>
            </h3>
            <div className="small text-uppercase text-muted fw-semibold mb-1">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.summary.matching">Matching</Translate>
            </div>
            <p className="mb-2" data-cy={`descriptionNormalizationRuleSummary-${rule.id}`}>
              <span className="fw-semibold">{formatDescriptionNormalizationLogic(rule.conditionOperator, translate)}: </span>
              {summary}
            </p>
            <div className="small text-uppercase text-muted fw-semibold mb-1">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.summary.result">Result</Translate>
            </div>
            <p className="mb-0">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.summary.then">Then replace the description with</Translate>{' '}
              <span className="fw-semibold">“{rule.resultingDescription}”</span>
            </p>
          </div>
        </div>
        <div className="d-flex flex-wrap align-content-start justify-content-md-end gap-2 flex-shrink-0">
          {reorderAvailable ? (
            <div className="btn-group" role="group" aria-label={translate('fintrackApp.descriptionNormalizationRule.reorder.actions')}>
              <Button
                color="secondary"
                outline
                size="sm"
                disabled={reordering || index === 0}
                onClick={() => onMove(index, index - 1)}
                aria-label={translate('fintrackApp.descriptionNormalizationRule.moveUp')}
                title={translate('fintrackApp.descriptionNormalizationRule.moveUp')}
                data-cy={`descriptionNormalizationRuleMoveUp-${rule.id}`}
                data-testid={`descriptionNormalizationRuleMoveUp-${rule.id}`}
              >
                <FontAwesomeIcon icon={faArrowUp} />
              </Button>
              <Button
                color="secondary"
                outline
                size="sm"
                disabled={reordering || index === ruleCount - 1}
                onClick={() => onMove(index, index + 1)}
                aria-label={translate('fintrackApp.descriptionNormalizationRule.moveDown')}
                title={translate('fintrackApp.descriptionNormalizationRule.moveDown')}
                data-cy={`descriptionNormalizationRuleMoveDown-${rule.id}`}
                data-testid={`descriptionNormalizationRuleMoveDown-${rule.id}`}
              >
                <FontAwesomeIcon icon={faArrowDown} />
              </Button>
            </div>
          ) : null}
          <Button tag={Link} to={`/description-normalization-rule/${rule.id}`} color="secondary" outline size="sm">
            <FontAwesomeIcon icon="eye" /> <Translate contentKey="entity.action.view">View</Translate>
          </Button>
          <Button tag={Link} to={`/description-normalization-rule/${rule.id}/edit`} color="primary" size="sm">
            <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
          </Button>
          <ProductActionsMenu
            label={translate('fintrackApp.descriptionNormalizationRule.actions')}
            dataCy={`descriptionNormalizationRuleActions-${rule.id}`}
          >
            <DropdownItem tag={Link} to={`/description-normalization-rule/${rule.id}/delete`} className="text-danger">
              <FontAwesomeIcon icon="trash" className="me-2" />
              <Translate contentKey="entity.action.delete">Delete</Translate>
            </DropdownItem>
          </ProductActionsMenu>
        </div>
      </div>
    </article>
  );
};

export const DescriptionNormalizationRule = () => {
  const account = useAppSelector(state => state.authentication?.account);
  const isAdmin = account?.authorities?.includes(AUTHORITIES.ADMIN) ?? false;
  const [rules, setRules] = useState<RuleWithConditions[]>([]);
  const confirmedRules = useRef<RuleWithConditions[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
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
      const response = await axios.get<IDescriptionNormalizationRule[]>(`${apiUrl}?size=1000&sort=priority,ASC&sort=id,ASC`);
      const configuredRules = await Promise.all(
        response.data.map(async rule => {
          const conditionsResponse = await axios.get<IDescriptionNormalizationRuleConfiguredCondition[]>(`${apiUrl}/${rule.id}/conditions`);
          return { ...rule, conditions: conditionsResponse.data };
        }),
      );
      const nextRules = sortRules(configuredRules);
      confirmedRules.current = nextRules;
      setRules(nextRules);
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

  const persistOrder = async (nextRules: RuleWithConditions[]) => {
    if (reordering || isAdmin) return;

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
    if (!over || active.id === over.id || reordering || isAdmin) return;
    const fromIndex = rules.findIndex(rule => rule.id === active.id);
    const toIndex = rules.findIndex(rule => rule.id === over.id);
    move(fromIndex, toIndex);
  };

  const ruleIds = useMemo(() => rules.map(rule => rule.id ?? `missing-${rule.name}`), [rules]);
  const reorderAvailable = !isAdmin;

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="description-normalization-rule-heading"
        dataCy="DescriptionNormalizationRuleHeading"
        title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.home.title">Description Normalization Rules</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.subtitle">
            Standardize original descriptions from file imports before transactions are reviewed.
          </Translate>
        }
        actions={
          <>
            <Button tag={Link} to="/rules" color="secondary" outline size="sm" data-cy="descriptionNormalizationRuleBackToRules">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="fintrackApp.rules.back">Back to rules</Translate>
            </Button>
            <Button
              color="secondary"
              outline
              size="sm"
              onClick={() => void load()}
              disabled={loading || reordering}
              data-cy="descriptionNormalizationRuleRefresh"
            >
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.refreshListLabel">Refresh list</Translate>
            </Button>
            <Button tag={Link} to="/description-normalization-rule/new" color="primary" size="sm" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" />{' '}
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.createLabel">Create rule</Translate>
            </Button>
          </>
        }
      />

      <ProductSection title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.reorder.title">Evaluation order</Translate>}>
        <p className="mb-0 text-muted">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.reorder.explanation">
            Active rules are evaluated from top to bottom. The first matching rule determines the description; inactive rules do not
            participate.
          </Translate>
        </p>
        {isAdmin ? (
          <p
            className="small text-muted mt-2 mb-0"
            data-cy="descriptionNormalizationRuleAdminReorderUnavailable"
            data-testid="descriptionNormalizationRuleAdminReorderUnavailable"
          >
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.reorder.adminUnavailable">
              Reordering is unavailable here because this administrator list can include rules owned by different users.
            </Translate>
          </p>
        ) : null}
      </ProductSection>

      {reorderError ? (
        <div
          className="alert alert-danger mt-3"
          role="alert"
          data-cy="descriptionNormalizationRuleReorderError"
          data-testid="descriptionNormalizationRuleReorderError"
        >
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.reorder.failed">
            The order could not be saved. The last confirmed order has been restored.
          </Translate>
        </div>
      ) : null}
      {loadError ? (
        <div className="alert alert-danger mt-3" role="alert" data-cy="descriptionNormalizationRuleLoadError">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.loadFailed">
            Description rules could not be loaded. Try refreshing the list.
          </Translate>
        </div>
      ) : null}

      {loading && rules.length === 0 ? (
        <p className="text-muted mt-4" role="status" aria-live="polite" data-cy="descriptionNormalizationRuleLoading">
          <Spinner tag="span" size="sm" className="me-2" />
          <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </p>
      ) : null}

      {!loading && !loadError && rules.length === 0 ? (
        <ProductSection
          className="mt-3"
          title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.empty.title">No description rules yet</Translate>}
        >
          <p className="text-muted mb-3">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.empty.body">
              Create a rule to standardize original descriptions from future file imports before you classify them.
            </Translate>
          </p>
          <Button
            tag={Link}
            to="/description-normalization-rule/new"
            color="primary"
            data-cy="descriptionNormalizationRuleEmptyCreate"
            data-testid="descriptionNormalizationRuleEmptyCreate"
          >
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.createLabel">Create rule</Translate>
          </Button>
        </ProductSection>
      ) : null}

      {rules.length > 0 ? (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext items={ruleIds} strategy={verticalListSortingStrategy}>
            <div className="d-grid gap-3 mt-3" aria-busy={reordering} data-cy="descriptionNormalizationRuleList">
              {rules.map((rule, index) => (
                <SortableRuleCard
                  key={rule.id}
                  rule={rule}
                  index={index}
                  ruleCount={rules.length}
                  reordering={reordering}
                  reorderAvailable={reorderAvailable}
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

export default DescriptionNormalizationRule;
