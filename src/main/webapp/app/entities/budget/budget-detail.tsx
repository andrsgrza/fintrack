import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { useAppSelector } from 'app/config/store';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  BudgetEvaluation,
  BudgetEvaluationHistory,
  BudgetHistory,
  BudgetOperationalPeriod,
  BudgetOperationalStatus,
  BudgetOverviewItem,
  BudgetRevision,
  getBudgetCurrentEvaluation,
  getBudgetEffectiveRevision,
  getBudgetEvaluationHistory,
  getBudgetHistory,
  getBudgetOperationalPeriods,
  getBudgetOverview,
} from './budget-api';
import { BudgetLifecycleActions, BudgetLifecycleStatus } from './budget-lifecycle-actions';
import { BudgetDetailNotificationPreferences } from './budget-detail-notification-preferences';
import {
  absoluteBudgetDecimal,
  boundedBudgetProgress,
  browserTimeZone,
  copy,
  dateRange,
  exceedsBudgetProgress,
  formatBudgetAmount,
  formatBudgetDate,
  formatBudgetPercentage,
  isBudgetDecimalNegative,
  userFacingError,
} from './budget-utils';

const HISTORY_PAGE_SIZE = 10;

type ScopeNames = { accounts: string[]; categories: string[]; tags: string[] };

const statusLabel = (locale: string, status: BudgetOperationalStatus) =>
  copy(
    locale,
    ({ SCHEDULED: 'Programado', ACTIVE: 'Activo', PAUSED: 'Pausado', COMPLETED: 'Completado' } as Record<string, string>)[status],
    ({ SCHEDULED: 'Scheduled', ACTIVE: 'Active', PAUSED: 'Paused', COMPLETED: 'Completed' } as Record<string, string>)[status],
  );

const typeLabel = (locale: string, type: BudgetOverviewItem['type']) =>
  type === 'EXPENSE' ? copy(locale, 'Límite de gasto', 'Expense limit') : copy(locale, 'Meta de ingreso', 'Income goal');

const evaluationLabel = (locale: string, evaluation: BudgetEvaluation) => {
  const labels: Record<string, [string, string]> = {
    INCOMPLETE_CONFIGURATION: ['Configuración incompleta', 'Incomplete configuration'],
    SEMANTIC_UNAVAILABLE: ['Información financiera no disponible', 'Financial information unavailable'],
    SEMANTIC_EXECUTION_FAILED: ['No se pudo calcular', 'Could not be calculated'],
    SEMANTIC_CONTRACT_ERROR: ['Resultado financiero incompatible', 'Incompatible financial result'],
    NOT_YET_EVALUATED: ['Aún no se evalúa', 'Not evaluated yet'],
    NOT_EVALUATED: ['Sin evaluación financiera', 'No financial evaluation'],
  };
  const value = labels[evaluation.evaluationStatus];
  return value ? copy(locale, value[0], value[1]) : null;
};

const goalLabel = (locale: string, evaluation: BudgetEvaluation) => {
  const labels: Record<string, [string, string]> = {
    WITHIN_LIMIT: ['Dentro del límite', 'Within limit'],
    FINISHED_WITHIN_LIMIT: ['Cerró dentro del límite', 'Finished within limit'],
    LIMIT_REACHED: ['Límite consumido', 'Limit reached'],
    EXCEEDED: ['Límite excedido', 'Limit exceeded'],
    GOAL_PENDING: ['Meta pendiente', 'Goal pending'],
    GOAL_NOT_MET: ['Meta no alcanzada', 'Goal not met'],
    GOAL_REACHED: ['Meta alcanzada', 'Goal reached'],
    GOAL_EXCEEDED: ['Meta superada', 'Goal exceeded'],
  };
  const value = labels[evaluation.goalStatus];
  return value ? copy(locale, value[0], value[1]) : null;
};

const categoryLabel = (category: ICategorySelectable) =>
  category.parentCategoryName ? `${category.parentCategoryName} › ${category.name ?? ''}` : (category.name ?? '');

const unavailableReference = (locale: string, kind: 'account' | 'category' | 'tag') => {
  const labels = {
    account: ['Cuenta histórica no disponible', 'Historical account unavailable'],
    category: ['Categoría histórica no disponible', 'Historical category unavailable'],
    tag: ['Etiqueta histórica no disponible', 'Historical tag unavailable'],
  } as const;
  return copy(locale, labels[kind][0], labels[kind][1]);
};

const calendarDescription = (revision: BudgetRevision, locale: string) => {
  const units: Record<BudgetRevision['intervalUnit'], [string, string]> = {
    DAY: ['día', 'day'],
    WEEK: ['semana', 'week'],
    MONTH: ['mes', 'month'],
    QUARTER: ['trimestre', 'quarter'],
    YEAR: ['año', 'year'],
  };
  const [spanishUnit, englishUnit] = units[revision.intervalUnit];
  const amount = revision.intervalCount;
  const base = copy(
    locale,
    `Cada ${amount} ${spanishUnit}${amount === 1 ? '' : revision.intervalUnit === 'MONTH' ? 'es' : 's'}`,
    `Every ${amount} ${englishUnit}${amount === 1 ? '' : 's'}`,
  );
  const alignment =
    revision.alignment === 'NATURAL'
      ? copy(locale, 'alineado al calendario', 'aligned to the calendar')
      : revision.alignment === 'CUSTOM'
        ? copy(
            locale,
            `con regla personalizada desde ${formatBudgetDate(revision.anchorDate, locale)}`,
            `with a custom rule from ${formatBudgetDate(revision.anchorDate, locale)}`,
          )
        : copy(locale, `desde ${formatBudgetDate(revision.anchorDate, locale)}`, `from ${formatBudgetDate(revision.anchorDate, locale)}`);
  if (revision.recurrenceMode === 'ONCE') return `${base}; ${copy(locale, 'una sola vez', 'one time')}; ${alignment}.`;
  if (revision.termination === 'AFTER_N_PERIODS') {
    return `${base}; ${copy(locale, `termina después de ${revision.terminationPeriodCount} períodos`, `ends after ${revision.terminationPeriodCount} periods`)}; ${alignment}.`;
  }
  if (revision.termination === 'END_DATE') {
    return `${base}; ${copy(locale, `termina el ${formatBudgetDate(revision.recurrenceEndDate ?? revision.anchorDate, locale)}`, `ends on ${formatBudgetDate(revision.recurrenceEndDate ?? revision.anchorDate, locale)}`)}; ${alignment}.`;
  }
  return `${base}; ${copy(locale, 'recurrente', 'recurring')}; ${alignment}.`;
};

const ScopeSummary = ({ locale, revision, names }: { locale: string; revision: BudgetRevision; names: ScopeNames | null }) => {
  const selected = (value: string[], empty: string) => (value.length ? value.join(', ') : empty);
  const allAccounts = copy(locale, 'Todas las cuentas de esta moneda', 'All accounts in this currency');
  const allCategories = copy(locale, 'Todas las categorías compatibles', 'All compatible categories');
  const noTags = copy(locale, 'Sin filtro de etiquetas', 'No tag filter');
  const categoryValue =
    revision.categoryScopeMode === 'ALL'
      ? allCategories
      : `${selected(names?.categories ?? [], unavailableReference(locale, 'category'))} · ${copy(locale, 'incluye subcategorías', 'includes subcategories')}`;
  const tagValue =
    revision.tagScopeMode === 'NONE'
      ? noTags
      : `${copy(locale, revision.tagMatchMode === 'ALL' ? 'Todas: ' : 'Cualquiera: ', revision.tagMatchMode === 'ALL' ? 'All: ' : 'Any: ')}${selected(names?.tags ?? [], unavailableReference(locale, 'tag'))}`;

  return (
    <dl className="row mb-0 small" data-cy="budgetScopeSummary">
      <dt className="col-sm-4 text-muted">{copy(locale, 'Cuentas', 'Accounts')}</dt>
      <dd className="col-sm-8">
        {revision.accountScopeMode === 'ALL' ? allAccounts : selected(names?.accounts ?? [], unavailableReference(locale, 'account'))}
      </dd>
      <dt className="col-sm-4 text-muted">{copy(locale, 'Categorías', 'Categories')}</dt>
      <dd className="col-sm-8">{categoryValue}</dd>
      <dt className="col-sm-4 text-muted">{copy(locale, 'Etiquetas', 'Tags')}</dt>
      <dd className="col-sm-8 mb-0">{tagValue}</dd>
    </dl>
  );
};

const EvaluationSummary = ({ locale, item, evaluation }: { locale: string; item: BudgetOverviewItem; evaluation: BudgetEvaluation }) => {
  const progress = evaluation.progressPercentage;
  const width = boundedBudgetProgress(progress) ?? 0;
  const exceedsProgress = exceedsBudgetProgress(progress);
  const actualLabel = item.type === 'EXPENSE' ? copy(locale, 'Gastado', 'Spent') : copy(locale, 'Ingresado', 'Received');
  const targetLabel = item.type === 'EXPENSE' ? copy(locale, 'Límite', 'Limit') : copy(locale, 'Meta', 'Goal');
  const unavailable = evaluationLabel(locale, evaluation) ?? evaluation.message;

  return (
    <div data-cy="budgetDetailEvaluation">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div>
          <div className="text-muted text-uppercase small fw-semibold">{copy(locale, 'Período seleccionado', 'Selected period')}</div>
          <strong>{dateRange(evaluation.startInclusive, evaluation.endExclusive, locale)}</strong>
        </div>
        <span className="badge text-bg-light border text-dark">{statusLabel(locale, evaluation.operationalStatus)}</span>
      </div>
      <div className="row g-3">
        <div className="col-12 col-sm-4">
          <div className="small text-muted text-uppercase fw-semibold">{targetLabel}</div>
          <div className="fs-5 fw-semibold" data-cy="budgetDetailTarget">
            {formatBudgetAmount(evaluation.targetAmount, item.currency, locale)}
          </div>
        </div>
        <div className="col-12 col-sm-4">
          <div className="small text-muted text-uppercase fw-semibold">{actualLabel}</div>
          <div className="fs-5 fw-semibold" data-cy="budgetDetailActual">
            {evaluation.evaluationApplicable ? formatBudgetAmount(evaluation.actualAmount, item.currency, locale) : '—'}
          </div>
        </div>
        <div className="col-12 col-sm-4">
          <div className="small text-muted text-uppercase fw-semibold">{copy(locale, 'Avance', 'Progress')}</div>
          <div className="fs-5 fw-semibold">
            {evaluation.evaluationApplicable && progress !== null ? formatBudgetPercentage(progress, locale) : '—'}
          </div>
        </div>
      </div>
      {evaluation.evaluationApplicable && progress !== null ? (
        <>
          <div className="d-flex justify-content-between gap-2 small mt-3">
            <span>{goalLabel(locale, evaluation)}</span>
            {evaluation.remainingAmount === null ? null : !isBudgetDecimalNegative(evaluation.remainingAmount) ? (
              <span>
                {copy(locale, 'Restante', 'Remaining')}: {formatBudgetAmount(evaluation.remainingAmount, item.currency, locale)}
              </span>
            ) : (
              <span>
                {copy(locale, 'Excedido', 'Exceeded')}:{' '}
                {formatBudgetAmount(absoluteBudgetDecimal(evaluation.remainingAmount), item.currency, locale)}
              </span>
            )}
          </div>
          <div
            className="progress mt-2"
            role="progressbar"
            aria-label={copy(locale, 'Avance del presupuesto', 'Budget progress')}
            aria-valuenow={width}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuetext={formatBudgetPercentage(progress, locale)}
            data-cy="budgetDetailProgress"
          >
            <div
              className={`progress-bar ${exceedsProgress ? 'bg-danger' : item.type === 'EXPENSE' ? 'bg-warning' : 'bg-success'}`}
              style={{ width: `${width}%` }}
            />
          </div>
        </>
      ) : (
        <div className="small border-start border-3 border-info ps-2 mt-3" role="status" data-cy="budgetEvaluationState">
          {unavailable ??
            copy(locale, 'No hay información financiera para este período.', 'There is no financial information for this period.')}
        </div>
      )}
    </div>
  );
};

const useBudgetLifecycle = ({
  budgetId,
  item,
  locale,
  refreshKey,
  setError,
}: {
  budgetId: number;
  item: BudgetOverviewItem | null;
  locale: string;
  refreshKey: number;
  setError: React.Dispatch<React.SetStateAction<string | null>>;
}) => {
  const [history, setHistory] = useState<BudgetHistory | null>(null);
  const [currentEvaluation, setCurrentEvaluation] = useState<BudgetEvaluation | null>(null);
  const [nextPeriod, setNextPeriod] = useState<BudgetOperationalPeriod | null>(null);
  const loadedBudgetId = useRef<number | null>(null);

  useEffect(() => {
    const validBudget = !!item && Number.isSafeInteger(budgetId) && budgetId > 0;
    const changedBudget = loadedBudgetId.current !== budgetId;
    loadedBudgetId.current = budgetId;
    if (!validBudget || changedBudget) {
      setHistory(null);
      setCurrentEvaluation(null);
      setNextPeriod(null);
    }
    if (!validBudget) return;
    let active = true;
    void getBudgetHistory(budgetId)
      .then(async nextHistory => {
        if (!active) return;
        setHistory(nextHistory);
        try {
          const current = await getBudgetCurrentEvaluation(budgetId);
          if (!active) return;
          setCurrentEvaluation(current);
          if (current.operationalStatus === 'COMPLETED') return;
          const following = await getBudgetOperationalPeriods(budgetId, {
            firstIndex: current.periodIndex + 1,
            lastIndex: current.periodIndex + 1,
          });
          if (active) setNextPeriod(following[0] ?? null);
        } catch {
          // A scheduled or completed calendar may not have a current period. Its only available action remains deletion.
        }
      })
      .catch(problem => {
        if (active)
          setError(
            userFacingError(
              problem,
              copy(locale, 'No se pudieron cargar las acciones del presupuesto.', 'Budget actions could not be loaded.'),
            ),
          );
      });
    return () => {
      active = false;
    };
  }, [budgetId, item, locale, refreshKey, setError]);

  return { currentEvaluation, history, nextPeriod };
};

const BudgetDetailHeaderActions = ({
  budgetId,
  item,
  lifecycleCurrentEvaluation,
  lifecycleHistory,
  lifecycleItem,
  lifecycleNextPeriod,
  locale,
  onChanged,
  onSuccess,
  onDeleted,
  referenceDate,
  timeZone,
}: {
  budgetId: number;
  item: BudgetOverviewItem | null;
  lifecycleCurrentEvaluation: BudgetEvaluation | null;
  lifecycleHistory: BudgetHistory | null;
  lifecycleItem: BudgetOverviewItem | null;
  lifecycleNextPeriod: BudgetOperationalPeriod | null;
  locale: string;
  onChanged: () => void;
  onSuccess: (message: string) => void;
  onDeleted: () => void;
  referenceDate: string;
  timeZone: string;
}) => (
  <div className="d-flex flex-wrap gap-2">
    {item && lifecycleHistory ? (
      <BudgetLifecycleActions
        locale={locale}
        budget={lifecycleItem ?? item}
        history={lifecycleHistory}
        currentPeriodStart={lifecycleCurrentEvaluation?.startInclusive ?? null}
        nextPeriodStart={lifecycleNextPeriod?.startInclusive ?? null}
        nextPeriodAvailable={!!lifecycleNextPeriod && lifecycleNextPeriod.status !== 'COMPLETED'}
        timeZone={timeZone}
        onChanged={onChanged}
        onSuccess={onSuccess}
        onDeleted={onDeleted}
      />
    ) : null}
    {lifecycleItem?.operationalStatus !== 'COMPLETED' ? (
      <Button tag={Link} color="primary" to={`/budgets/${budgetId}/edit`} data-cy="editBudget">
        {copy(locale, 'Editar presupuesto', 'Edit budget')}
      </Button>
    ) : null}
    <Button tag={Link} color="secondary" outline to={`/budgets?referenceDate=${referenceDate}`} data-cy="backToBudgets">
      {copy(locale, 'Volver a presupuestos', 'Back to budgets')}
    </Button>
  </div>
);

const BudgetDetail = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const { budgetId: rawBudgetId } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const budgetId = Number(rawBudgetId);
  const timeZone = useMemo(browserTimeZone, []);
  const referenceDate = searchParams.get('referenceDate');
  const [item, setItem] = useState<BudgetOverviewItem | null>(null);
  const [evaluation, setEvaluation] = useState<BudgetEvaluation | null>(null);
  const [previousEvaluation, setPreviousEvaluation] = useState<BudgetEvaluation | null>(null);
  const [nextEvaluation, setNextEvaluation] = useState<BudgetEvaluation | null>(null);
  const [revision, setRevision] = useState<BudgetRevision | null>(null);
  const [scopeNames, setScopeNames] = useState<ScopeNames | null>(null);
  const [history, setHistory] = useState<BudgetEvaluationHistory | null>(null);
  const [historyFirst, setHistoryFirst] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lifecycleNotice, setLifecycleNotice] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const lifecycle = useBudgetLifecycle({ budgetId, item, locale, refreshKey, setError });

  const chooseReferenceDate = (value: string) => {
    if (!value) return;
    const next = new URLSearchParams(searchParams);
    next.set('referenceDate', value);
    setSearchParams(next);
  };

  useEffect(() => {
    if (!Number.isSafeInteger(budgetId) || budgetId <= 0 || referenceDate) return;
    let active = true;
    setLoading(true);
    setError(null);
    // The browser's local midnight must not choose the financial current period. The server resolves it from this
    // Budget's effective assignment, then the URL records the selected historical period explicitly.
    void getBudgetCurrentEvaluation(budgetId)
      .then(current => {
        if (active) chooseReferenceDate(current.startInclusive);
      })
      .catch(async () => {
        try {
          const budgetHistory = await getBudgetHistory(budgetId);
          const firstScheduled = budgetHistory.schedules.find(schedule => schedule.active);
          if (!firstScheduled) throw new Error('No Budget period is available.');
          if (active) chooseReferenceDate(firstScheduled.effectivePeriodStart);
        } catch (problem) {
          if (active) {
            setError(
              userFacingError(problem, copy(locale, 'No se pudo resolver el período actual.', 'The current period could not be resolved.')),
            );
            setLoading(false);
          }
        }
      });
    return () => {
      active = false;
    };
  }, [budgetId, locale, referenceDate, searchParams, setSearchParams]);

  useEffect(() => {
    if (!referenceDate) return;
    if (!Number.isSafeInteger(budgetId) || budgetId <= 0) {
      setNotFound(true);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    setNotFound(false);
    void getBudgetOverview({ referenceDate })
      .then(overview => {
        if (!active) return;
        const next = overview.budgets.find(candidate => candidate.budgetId === budgetId) ?? null;
        setItem(next);
        setEvaluation(next?.evaluation ?? null);
        setPreviousEvaluation(null);
        setNextEvaluation(null);
        setRevision(null);
        setScopeNames(null);
        setHistoryFirst(
          next?.evaluation ? Math.max(0, Math.floor(next.evaluation.periodIndex / HISTORY_PAGE_SIZE) * HISTORY_PAGE_SIZE) : 0,
        );
        setNotFound(!next);
      })
      .catch(problem => {
        if (active)
          setError(userFacingError(problem, copy(locale, 'No se pudo cargar el presupuesto.', 'The budget could not be loaded.')));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [budgetId, locale, referenceDate, refreshKey]);

  useEffect(() => {
    if (!item?.evaluation || !Number.isSafeInteger(budgetId) || budgetId <= 0) return;
    let active = true;
    setDetailLoading(true);
    setError(null);
    const index = item.evaluation.periodIndex;
    const firstPeriodIndex = Math.max(0, index - 1);
    void getBudgetEvaluationHistory(budgetId, { firstPeriodIndex, size: 3 })
      .then(result => {
        if (!active) return;
        setEvaluation(result.evaluations.find(candidate => candidate.periodIndex === index) ?? item.evaluation);
        setPreviousEvaluation(result.evaluations.find(candidate => candidate.periodIndex === index - 1) ?? null);
        const following = result.evaluations.find(candidate => candidate.periodIndex === index + 1) ?? null;
        setNextEvaluation(following?.operationalStatus === 'COMPLETED' ? null : following);
      })
      .catch(problem => {
        if (active) setError(userFacingError(problem, copy(locale, 'No se pudo cargar el período.', 'The period could not be loaded.')));
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [budgetId, item?.evaluation, locale]);

  useEffect(() => {
    if (!evaluation || !Number.isSafeInteger(budgetId) || budgetId <= 0) return;
    let active = true;
    void getBudgetEffectiveRevision(budgetId, evaluation.startInclusive)
      .then(next => {
        if (!active) return;
        setRevision(next);
      })
      .catch(problem => {
        if (active)
          setError(
            userFacingError(
              problem,
              copy(locale, 'No se pudo cargar la configuración histórica.', 'The historical configuration could not be loaded.'),
            ),
          );
      });
    return () => {
      active = false;
    };
  }, [budgetId, evaluation?.startInclusive, locale]);

  useEffect(() => {
    if (!revision) return;
    let active = true;
    const requestedAccountIds = revision.accountScopeMode === 'SELECTED' ? revision.accountIds : [];
    const requestedCategoryIds = revision.categoryScopeMode === 'SELECTED' ? revision.categoryIds : [];
    const requestedTagIds = revision.tagScopeMode === 'SELECTED' ? revision.tagIds : [];
    void Promise.all([
      Promise.all(
        requestedAccountIds.map(id =>
          getSelectableFinancialAccounts(id).then(accounts => accounts.find(account => account.id === id) ?? null),
        ),
      ),
      requestedCategoryIds.length ? getSelectableCategories(requestedCategoryIds) : Promise.resolve([] as ICategorySelectable[]),
      requestedTagIds.length ? getSelectableTags(requestedTagIds) : Promise.resolve([] as ITagSelectable[]),
    ])
      .then(([accounts, categories, tags]) => {
        if (!active) return;
        const accountName = (account: IFinancialAccountSelectable | null) => account?.name ?? unavailableReference(locale, 'account');
        const categoryName = (id: number) => {
          const category = categories.find(candidate => candidate.id === id);
          return category ? categoryLabel(category) : unavailableReference(locale, 'category');
        };
        const tagName = (id: number) => tags.find(candidate => candidate.id === id)?.name ?? unavailableReference(locale, 'tag');
        setScopeNames({
          accounts: accounts.map(accountName),
          categories: requestedCategoryIds.map(categoryName),
          tags: requestedTagIds.map(tagName),
        });
      })
      .catch(() => {
        if (active) {
          setScopeNames({
            accounts: requestedAccountIds.map(() => unavailableReference(locale, 'account')),
            categories: requestedCategoryIds.map(() => unavailableReference(locale, 'category')),
            tags: requestedTagIds.map(() => unavailableReference(locale, 'tag')),
          });
        }
      });
    return () => {
      active = false;
    };
  }, [locale, revision]);

  useEffect(() => {
    if (!item || !Number.isSafeInteger(budgetId) || budgetId <= 0) return;
    let active = true;
    void getBudgetEvaluationHistory(budgetId, { firstPeriodIndex: historyFirst, size: HISTORY_PAGE_SIZE + 1 })
      .then(next => {
        if (active) setHistory(next);
      })
      .catch(problem => {
        if (active)
          setError(
            userFacingError(
              problem,
              copy(locale, 'No se pudo cargar el historial de períodos.', 'The period history could not be loaded.'),
            ),
          );
      });
    return () => {
      active = false;
    };
  }, [budgetId, historyFirst, item, locale]);

  const historyRows = (history?.evaluations ?? [])
    .filter(candidate => candidate.operationalStatus !== 'COMPLETED')
    .slice(0, HISTORY_PAGE_SIZE);
  const hasNextHistoryPage =
    (history?.evaluations ?? []).filter(candidate => candidate.operationalStatus !== 'COMPLETED').length > HISTORY_PAGE_SIZE;
  const selectedIndex = evaluation?.periodIndex ?? null;
  const canGoPrevious = previousEvaluation !== null;
  const canGoNext = nextEvaluation !== null;
  const goToPeriod = (period: BudgetEvaluation) => chooseReferenceDate(period.startInclusive);
  const lifecycleItem = lifecycle.currentEvaluation ? { ...item, operationalStatus: lifecycle.currentEvaluation.operationalStatus } : item;
  const refreshLifecycle = () => setRefreshKey(current => current + 1);
  const returnToOverviewAfterDeletion = () => {
    const next = new URLSearchParams({ notice: 'deleted' });
    if (referenceDate) next.set('referenceDate', referenceDate);
    navigate(`/budgets?${next.toString()}`);
  };
  return (
    <ProductPage wide className="budget-detail">
      <ProductPageHeader
        title={item?.name ?? copy(locale, 'Presupuesto', 'Budget')}
        subtitle={
          item?.description ??
          copy(
            locale,
            'Consulta el período, configuración e historial sin modificar este presupuesto.',
            'Review the period, configuration, and history without changing this budget.',
          )
        }
        actions={
          <BudgetDetailHeaderActions
            budgetId={budgetId}
            item={item}
            lifecycleCurrentEvaluation={lifecycle.currentEvaluation}
            lifecycleHistory={lifecycle.history}
            lifecycleItem={lifecycleItem}
            lifecycleNextPeriod={lifecycle.nextPeriod}
            locale={locale}
            onChanged={refreshLifecycle}
            onSuccess={setLifecycleNotice}
            onDeleted={returnToOverviewAfterDeletion}
            referenceDate={referenceDate ?? ''}
            timeZone={timeZone}
          />
        }
        dataCy="budgetDetailHeading"
      />

      {error ? (
        <div className="alert alert-danger" role="alert" data-cy="budgetDetailError">
          {error}
        </div>
      ) : null}
      {lifecycleNotice ? (
        <div className="alert alert-success" role="status" data-cy="budgetLifecycleNotice">
          {lifecycleNotice}
        </div>
      ) : null}
      {loading ? <div className="text-center py-5 text-muted">{copy(locale, 'Cargando presupuesto…', 'Loading budget…')}</div> : null}
      {!loading && item ? (
        <>
          <div className="d-flex flex-wrap align-items-center gap-2 mb-4" data-cy="budgetDetailIdentity">
            <span className="badge text-bg-light border text-dark">{typeLabel(locale, item.type)}</span>
            <span className="badge text-bg-light border text-dark">{item.currency}</span>
            <span className="badge text-bg-light border text-dark">{statusLabel(locale, lifecycleItem.operationalStatus)}</span>
          </div>
          <BudgetLifecycleStatus
            locale={locale}
            history={lifecycle.history}
            currentPeriodStart={lifecycle.currentEvaluation?.startInclusive ?? null}
          />
        </>
      ) : null}
      {!loading && notFound ? (
        <ProductSection title={copy(locale, 'No se encontró el presupuesto', 'Budget not found')}>
          <p className="mb-0">
            {copy(
              locale,
              'No existe o no tienes acceso a este presupuesto.',
              'It does not exist or you do not have access to this budget.',
            )}
          </p>
        </ProductSection>
      ) : null}
      {!loading && item ? (
        <>
          <ProductSection title={copy(locale, 'Período', 'Period')} className="mb-4">
            <div className="row g-3 align-items-end">
              <div className="col-12 col-md-5">
                <Label for="budget-detail-reference-date">{copy(locale, 'Ir a una fecha', 'Go to a date')}</Label>
                <Input
                  id="budget-detail-reference-date"
                  type="date"
                  value={referenceDate ?? ''}
                  onChange={event => chooseReferenceDate(event.target.value)}
                  data-cy="budgetDetailReferenceDate"
                />
              </div>
              {historyRows.length ? (
                <div className="col-12 col-md-7">
                  <Label for="budget-period-picker">{copy(locale, 'Períodos cargados', 'Loaded periods')}</Label>
                  <Input
                    id="budget-period-picker"
                    type="select"
                    value={selectedIndex ?? ''}
                    onChange={event => {
                      const selected = historyRows.find(candidate => candidate.periodIndex === Number(event.target.value));
                      if (selected) goToPeriod(selected);
                    }}
                    data-cy="budgetPeriodPicker"
                  >
                    {historyRows.map(candidate => (
                      <option key={candidate.periodIndex} value={candidate.periodIndex}>
                        {dateRange(candidate.startInclusive, candidate.endExclusive, locale)}
                      </option>
                    ))}
                  </Input>
                </div>
              ) : null}
            </div>
            <p className="small text-muted mb-0 mt-3">
              {copy(
                locale,
                'La zona horaria efectiva se conserva por período; cambiar la zona del navegador no reinterpreta este historial.',
                'The effective time zone is preserved by period; changing this browser’s zone does not reinterpret this history.',
              )}
            </p>
          </ProductSection>

          {!item.hasPeriodAtReferenceDate || !evaluation ? (
            <ProductSection title={copy(locale, 'No hay período programado', 'No scheduled period')} className="mb-4">
              <div className="small border-start border-3 border-secondary ps-2" role="status" data-cy="budgetNoScheduledPeriod">
                {item.referenceMessage ??
                  copy(
                    locale,
                    'No hay un período programado para la fecha seleccionada.',
                    'There is no scheduled period for the selected date.',
                  )}
              </div>
            </ProductSection>
          ) : (
            <>
              <ProductSection title={copy(locale, 'Resumen del período', 'Period summary')} className="mb-4">
                <div className="d-flex flex-wrap justify-content-between gap-2 mb-4">
                  <Button
                    color="secondary"
                    outline
                    onClick={() => previousEvaluation && goToPeriod(previousEvaluation)}
                    disabled={!canGoPrevious || detailLoading}
                    data-cy="budgetPreviousPeriod"
                  >
                    {copy(locale, '← Período anterior', '← Previous period')}
                  </Button>
                  <Button
                    color="secondary"
                    outline
                    onClick={() => nextEvaluation && goToPeriod(nextEvaluation)}
                    disabled={!canGoNext || detailLoading}
                    data-cy="budgetNextPeriod"
                  >
                    {copy(locale, 'Período siguiente →', 'Next period →')}
                  </Button>
                </div>
                <EvaluationSummary locale={locale} item={item} evaluation={evaluation} />
              </ProductSection>

              <ProductSection
                title={copy(locale, 'Configuración aplicable a este período', 'Configuration for this period')}
                className="mb-4"
              >
                {revision ? (
                  <div className="row g-4">
                    <div className="col-12 col-lg-5">
                      <div className="small text-muted text-uppercase fw-semibold mb-1">{copy(locale, 'Calendario', 'Calendar')}</div>
                      <p className="mb-0" data-cy="budgetCalendarDescription">
                        {calendarDescription(revision, locale)}
                      </p>
                      <div className="small text-muted mt-2">
                        {copy(locale, `Revisión ${revision.number}`, `Revision ${revision.number}`)}
                      </div>
                    </div>
                    <div className="col-12 col-lg-7">
                      <div className="small text-muted text-uppercase fw-semibold mb-1">{copy(locale, 'Alcance', 'Scope')}</div>
                      <ScopeSummary locale={locale} revision={revision} names={scopeNames} />
                    </div>
                  </div>
                ) : (
                  <div className="text-muted small">
                    {copy(locale, 'Cargando configuración histórica…', 'Loading historical configuration…')}
                  </div>
                )}
              </ProductSection>
            </>
          )}

          <BudgetDetailNotificationPreferences
            budgetId={budgetId}
            type={item.type}
            completed={lifecycleItem?.operationalStatus === 'COMPLETED'}
            locale={locale}
            refreshKey={refreshKey}
          />

          <ProductSection title={copy(locale, 'Historial de períodos', 'Period history')}>
            <p className="small text-muted">
              {copy(
                locale,
                'Los importes históricos se vuelven a evaluar con los movimientos actuales. No se crean períodos después de que termina el calendario.',
                'Historical amounts are evaluated again from current financial records. No periods are created after the schedule ends.',
              )}
            </p>
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-3" data-cy="budgetPeriodHistory">
                <thead>
                  <tr>
                    <th scope="col">{copy(locale, 'Período', 'Period')}</th>
                    <th scope="col">{copy(locale, 'Objetivo', 'Target')}</th>
                    <th scope="col">{copy(locale, 'Resultado', 'Result')}</th>
                    <th scope="col">{copy(locale, 'Estado', 'State')}</th>
                    <th scope="col">
                      <span className="visually-hidden">{copy(locale, 'Abrir período', 'Open period')}</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {historyRows.map(candidate => (
                    <tr key={candidate.periodIndex} data-cy={`budgetHistoryPeriod-${candidate.periodIndex}`}>
                      <td>
                        <strong>{dateRange(candidate.startInclusive, candidate.endExclusive, locale)}</strong>
                      </td>
                      <td>{formatBudgetAmount(candidate.targetAmount, item.currency, locale)}</td>
                      <td>
                        {candidate.evaluationApplicable ? (
                          <>
                            {formatBudgetAmount(candidate.actualAmount, item.currency, locale)}
                            {candidate.progressPercentage === null ? null : (
                              <span className="small text-muted ms-1">
                                ({formatBudgetPercentage(candidate.progressPercentage, locale)})
                              </span>
                            )}
                          </>
                        ) : (
                          <span className="text-muted">—</span>
                        )}
                      </td>
                      <td>
                        {candidate.evaluationApplicable ? (
                          goalLabel(locale, candidate)
                        ) : (
                          <span className="small">
                            {evaluationLabel(locale, candidate) ?? candidate.message ?? statusLabel(locale, candidate.operationalStatus)}
                          </span>
                        )}
                      </td>
                      <td className="text-end">
                        <Button
                          color="secondary"
                          outline
                          size="sm"
                          onClick={() => goToPeriod(candidate)}
                          data-cy={`openBudgetPeriod-${candidate.periodIndex}`}
                        >
                          {copy(locale, 'Ver', 'View')}
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {!historyRows.length ? (
                    <tr>
                      <td colSpan={5} className="text-muted">
                        {copy(locale, 'No hay más períodos programados.', 'There are no more scheduled periods.')}
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
            <div className="d-flex flex-wrap justify-content-between gap-2">
              <Button
                color="secondary"
                outline
                size="sm"
                onClick={() => setHistoryFirst(current => Math.max(0, current - HISTORY_PAGE_SIZE))}
                disabled={historyFirst === 0}
                data-cy="budgetHistoryPrevious"
              >
                {copy(locale, '← Anteriores', '← Previous')}
              </Button>
              <Button
                color="secondary"
                outline
                size="sm"
                onClick={() => setHistoryFirst(current => current + HISTORY_PAGE_SIZE)}
                disabled={!hasNextHistoryPage}
                data-cy="budgetHistoryNext"
              >
                {copy(locale, 'Siguientes →', 'Next →')}
              </Button>
            </div>
          </ProductSection>
        </>
      ) : null}
    </ProductPage>
  );
};

export default BudgetDetail;
