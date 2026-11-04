import axios from 'axios';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert } from 'reactstrap';

import { useAppSelector } from 'app/config/store';
import { AnalyticsContextControls } from 'app/entities/analytics-explorer/analytics-context-controls';
import {
  executeSemanticQuery,
  getExplorerAccounts,
  getSemanticAnalyticsCatalog,
} from 'app/entities/analytics-explorer/analytics-explorer-api';
import {
  AnalyticsProblem,
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';
import { AnalyticsResultsPanel } from 'app/entities/analytics-explorer/analytics-results-panel';
import { copy } from 'app/entities/analytics-explorer/analytics-explorer-utils';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  FinancialVisualizationDefinition,
  RecommendedDashboardTemplate,
  recommendedDashboardTemplates,
} from './recommended-dashboard-templates';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type WidgetState = { loading: boolean; result: SemanticAnalyticsResult | null; error: string | null };
type DrillState = { definition: SemanticQueryDefinition; label: string };
type WidgetInstance = FinancialVisualizationDefinition & { definition: SemanticQueryDefinition; trail: DrillState[] };
type TrendGranularity = 'AUTO' | 'TRANSACTION_DAY' | 'TRANSACTION_WEEK' | 'TRANSACTION_MONTH' | 'TRANSACTION_YEAR';

const today = () => {
  const parts = new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  }).formatToParts(new Date());
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find(part => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
};
const currentContext = (schemaVersion: number): VisualizationContext => {
  const date = today();
  return {
    schemaVersion,
    time: { mode: 'ABSOLUTE_RANGE', relativePeriod: null, from: date, through: date },
    accountIds: [],
    currencyCodes: [],
  };
};
const contextKey = (context: VisualizationContext) => JSON.stringify(context);
const widgetKey = (widget: WidgetInstance) => JSON.stringify({ definition: widget.definition, contextPolicy: widget.contextPolicy });
const title = (locale: string, value: { es: string; en: string }) => copy(locale, value.es, value.en);
const dashboardById = (templates: RecommendedDashboardTemplate[], id: string | undefined) =>
  templates.find(template => template.id === id) ?? templates[0];
const blankState = (): WidgetState => ({ loading: true, result: null, error: null });
const diagnosticsFrom = (error: unknown) => {
  if (axios.isAxiosError<AnalyticsProblem>(error)) {
    const data = error.response?.data;
    if (data?.diagnostics?.length) return data.diagnostics.map(diagnostic => diagnostic.message).join(' ');
    if (data?.detail) return data.detail;
  }
  return error instanceof Error ? error.message : 'Semantic analytics request failed.';
};
const isCurrentOnlyContext = (context: VisualizationContext) => {
  const date = today();
  return context.time.mode === 'ABSOLUTE_RANGE' && context.time.from === date && context.time.through === date;
};
const daysInContext = (context: VisualizationContext | null) => {
  if (!context || context.time.mode !== 'ABSOLUTE_RANGE' || !context.time.from || !context.time.through) return null;
  const from = Date.parse(`${context.time.from}T00:00:00Z`);
  const through = Date.parse(`${context.time.through}T00:00:00Z`);
  return Number.isFinite(from) && Number.isFinite(through) ? Math.max(1, Math.floor((through - from) / 86_400_000) + 1) : null;
};
/** Auto is presentation-controlled: it derives an executable grouping without embedding period/account/currency in the widget AST. */
const resolvedTrendGrouping = (granularity: TrendGranularity, context: VisualizationContext | null) => {
  if (granularity !== 'AUTO') return granularity;
  const days = daysInContext(context);
  if (days === null) return 'TRANSACTION_MONTH';
  if (days <= 45) return 'TRANSACTION_DAY';
  if (days <= 210) return 'TRANSACTION_WEEK';
  if (days <= 730) return 'TRANSACTION_MONTH';
  return 'TRANSACTION_YEAR';
};
const trendGroupingLabel = (locale: string, grouping: TrendGranularity) =>
  ({
    AUTO: copy(locale, 'Automático', 'Automatic'),
    TRANSACTION_DAY: copy(locale, 'Día', 'Day'),
    TRANSACTION_WEEK: copy(locale, 'Semana', 'Week'),
    TRANSACTION_MONTH: copy(locale, 'Mes', 'Month'),
    TRANSACTION_YEAR: copy(locale, 'Año', 'Year'),
  })[grouping];
const formatAsOf = (locale: string, isoDate: string) =>
  new Intl.DateTimeFormat(locale.startsWith('es') ? 'es-MX' : 'en-US', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(`${isoDate}T12:00:00Z`),
  );

/**
 * Executes the existing canonical endpoint with at most three in-flight requests. Identical AST/context pairs share
 * one promise, and the monotonically increasing token prevents an older context from overwriting newer results.
 */
const useWidgetResults = (widgets: WidgetInstance[], context: VisualizationContext | null) => {
  const [states, setStates] = useState<Record<string, WidgetState>>({});
  const sequence = useRef(0);
  const completedForCurrentContext = useRef(new Map<string, SemanticAnalyticsResult>());
  const priorContextKey = useRef<string | null>(null);
  const signature = `${context ? contextKey(context) : 'none'}:${widgets.map(widget => `${widget.id}:${widgetKey(widget)}`).join('|')}`;

  useEffect(() => {
    if (!context) return;
    const token = ++sequence.current;
    const nextContextKey = contextKey(context);
    if (priorContextKey.current !== nextContextKey) completedForCurrentContext.current.clear();
    priorContextKey.current = nextContextKey;
    const requestCache = new Map<string, Promise<SemanticAnalyticsResult>>();
    const compatible = widgets.filter(widget => widget.contextPolicy !== 'CURRENT_ONLY' || isCurrentOnlyContext(context));
    const incompatible = widgets.filter(widget => !compatible.includes(widget));
    const requestKeyFor = (widget: WidgetInstance) => `${widgetKey(widget)}:${nextContextKey}`;
    const pending = compatible.filter(widget => !completedForCurrentContext.current.has(requestKeyFor(widget)));
    setStates(
      Object.fromEntries(
        widgets.map(widget => {
          if (incompatible.includes(widget))
            return [widget.id, { loading: false, result: null, error: 'CURRENT_ONLY_CONTEXT' } satisfies WidgetState];
          const cached = completedForCurrentContext.current.get(requestKeyFor(widget));
          return [widget.id, cached ? ({ loading: false, result: cached, error: null } satisfies WidgetState) : blankState()];
        }),
      ),
    );
    let index = 0;
    const run = async () => {
      while (index < pending.length) {
        const widget = pending[index++];
        const key = requestKeyFor(widget);
        const request = requestCache.get(key) ?? executeSemanticQuery(widget.definition, context);
        requestCache.set(key, request);
        try {
          const result = await request;
          if (sequence.current === token) {
            completedForCurrentContext.current.set(key, result);
            setStates(previous => ({ ...previous, [widget.id]: { loading: false, result, error: null } }));
          }
        } catch (error) {
          if (sequence.current === token) {
            setStates(previous => ({ ...previous, [widget.id]: { loading: false, result: null, error: diagnosticsFrom(error) } }));
          }
        }
      }
    };
    void Promise.all(Array.from({ length: Math.min(3, pending.length) }, () => run()));
  }, [signature]);

  return states;
};

const DashboardNav = ({
  templates,
  selected,
  locale,
}: {
  templates: RecommendedDashboardTemplate[];
  selected: RecommendedDashboardTemplate;
  locale: string;
}) => (
  <nav className="nav nav-pills gap-1 mb-3" aria-label={copy(locale, 'Dashboards recomendados', 'Recommended dashboards')}>
    {templates.map(template => (
      <Link
        className={`nav-link ${template.id === selected.id ? 'active' : ''}`}
        key={template.id}
        to={`/analytics/dashboards/${template.id}`}
      >
        {title(locale, template.title)}
      </Link>
    ))}
    <Link className="nav-link ms-md-auto" to="/analytics/explorer">
      {copy(locale, 'Explorador de métricas', 'Metric explorer')}
    </Link>
  </nav>
);

const WidgetCard = ({
  widget,
  state,
  locale,
  context,
  onDrillDown,
  onDrillBack,
}: {
  widget: WidgetInstance;
  state: WidgetState | undefined;
  locale: string;
  context: VisualizationContext;
  onDrillDown: (widget: WidgetInstance, categoryId: string, categoryLabel: string) => void;
  onDrillBack: (widget: WidgetInstance) => void;
}) => {
  const span = widget.span === 'FULL' ? 'col-12' : widget.span === 'KPI' ? 'col-12 col-md-6 col-xl-3' : 'col-12 col-xl-6';
  const visualization: ExplorerVisualization = widget.visualization;
  const policyMessage = state?.error === 'CURRENT_ONLY_CONTEXT';
  const positionAsOf =
    ['FINANCIAL_POSITION_SNAPSHOT', 'ACCOUNT_POSITION_SNAPSHOT'].includes(widget.definition.dataset) &&
    (widget.id === 'registered-position' || widget.id === 'position-by-account');
  const effectiveThrough = state?.result?.effectiveContext.through ?? context.time.through;
  return (
    <article className={span} data-cy={`recommendedWidget-${widget.id}`}>
      <div className="card border shadow-sm h-100">
        <div className="card-body p-3 p-md-4">
          <h2 className="h5 mb-1">{title(locale, widget.title)}</h2>
          <p className="small text-muted mb-3">{title(locale, widget.description)}</p>
          {positionAsOf && effectiveThrough ? (
            <p className="small fw-semibold mb-3" data-cy={`recommendedWidgetAsOf-${widget.id}`}>
              {copy(locale, `Al ${formatAsOf(locale, effectiveThrough)}`, `As of ${formatAsOf(locale, effectiveThrough)}`)}
            </p>
          ) : null}
          {policyMessage ? (
            <div className="small border-start border-info border-3 ps-2 mb-0" role="status">
              {copy(
                locale,
                'Esta medida usa información actual de crédito. Selecciona únicamente la fecha de hoy para verla; FINTRACK no inventa historial de límites.',
                'This measure uses current credit facts. Select today only to view it; FINTRACK does not invent limit history.',
              )}
            </div>
          ) : state?.error ? (
            <Alert color="danger" className="mb-0" role="alert">
              {copy(locale, 'Este widget no pudo cargarse:', 'This widget could not load:')} {state.error}
            </Alert>
          ) : (
            <AnalyticsResultsPanel
              definition={widget.definition}
              result={state?.result ?? null}
              visualization={visualization}
              locale={locale}
              loading={state?.loading ?? true}
              stale={false}
              seriesLimit="ALL"
              drillTrail={widget.trail.map(item => item.label)}
              onDrillDown={(categoryId, categoryLabel) => onDrillDown(widget, categoryId, categoryLabel)}
              onDrillBack={() => onDrillBack(widget)}
              compact
            />
          )}
        </div>
      </div>
    </article>
  );
};

/** Three official immutable templates rendered with the Explorer's shared semantic result and Recharts layer. */
const RecommendedDashboards = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const { dashboardId } = useParams<{ dashboardId: string }>();
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [context, setContext] = useState<VisualizationContext | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [overrides, setOverrides] = useState<Record<string, { definition: SemanticQueryDefinition; trail: DrillState[] }>>({});
  const [overviewGranularity, setOverviewGranularity] = useState<TrendGranularity>('AUTO');

  useEffect(() => {
    let active = true;
    void Promise.all([getSemanticAnalyticsCatalog(), getExplorerAccounts()])
      .then(([nextCatalog, nextAccounts]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setContext(currentContext(nextCatalog.schemaVersion));
        setAccounts(
          nextAccounts
            .filter(account => account.id != null && account.name)
            .map(account => ({ id: account.id, name: account.name, accountType: account.accountType, currency: account.currency })),
        );
      })
      .catch(requestError => {
        if (active) setError(diagnosticsFrom(requestError));
      });
    return () => {
      active = false;
    };
  }, []);

  const templates = useMemo(() => (catalog ? recommendedDashboardTemplates(catalog) : []), [catalog]);
  const dashboard = dashboardById(templates, dashboardId);
  const widgets = useMemo<WidgetInstance[]>(() => {
    if (!dashboard) return [];
    const grouping = resolvedTrendGrouping(overviewGranularity, context);
    return dashboard.widgets.map(widget => {
      const base = overrides[widget.id]?.definition ?? widget.definition;
      const definition =
        dashboard.id === 'financial-overview' && widget.id === 'economic-trend' && !overrides[widget.id]
          ? { ...base, groupBy: [grouping], orderBy: [{ field: grouping, projectionAlias: null, direction: 'ASC' as const }] }
          : base;
      return { ...widget, definition, trail: overrides[widget.id]?.trail ?? [] };
    });
  }, [context, dashboard, overviewGranularity, overrides]);
  const states = useWidgetResults(widgets, context);

  const drillDown = (widget: WidgetInstance, categoryId: string, categoryLabel: string) => {
    if (widget.definition.dataset !== 'TRANSACTION_LEDGER') return;
    const groupedCategory = widget.definition.groupBy.findIndex(field =>
      ['CATEGORY_ID', 'CATEGORY_ROOT', 'CATEGORY_SUBCATEGORY', 'CATEGORY_BRANCH_CHILD'].includes(field),
    );
    if (groupedCategory < 0) return;
    const definition = JSON.parse(JSON.stringify(widget.definition)) as SemanticQueryDefinition;
    definition.predicates = definition.predicates.filter(
      predicate => !(predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF'),
    );
    definition.predicates.push({ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: [categoryId] });
    const previousCategoryGrouping = definition.groupBy[groupedCategory];
    definition.groupBy[groupedCategory] = 'CATEGORY_BRANCH_CHILD';
    // The semantic contract only permits ordering by an active grouping or a selected calculation. Keep the
    // category ordering aligned with the grouping replaced by drill-down; projection ordering remains unchanged.
    definition.orderBy = definition.orderBy.map(order =>
      order.field === previousCategoryGrouping ? { ...order, field: 'CATEGORY_BRANCH_CHILD', projectionAlias: null } : order,
    );
    setOverrides(previous => ({
      ...previous,
      [widget.id]: { definition, trail: [...widget.trail, { definition: widget.definition, label: categoryLabel }] },
    }));
  };
  const drillBack = (widget: WidgetInstance) => {
    const previous = widget.trail[widget.trail.length - 1];
    if (!previous) return;
    setOverrides(current => ({
      ...current,
      [widget.id]: { definition: previous.definition, trail: widget.trail.slice(0, -1) },
    }));
  };

  return (
    <ProductPage wide className="recommended-dashboards">
      <ProductPageHeader
        headingId="recommended-dashboards-heading"
        title={dashboard ? title(locale, dashboard.title) : copy(locale, 'Dashboards recomendados', 'Recommended dashboards')}
        subtitle={
          dashboard
            ? title(locale, dashboard.description)
            : copy(
                locale,
                'Tres vistas financieras construidas con consultas semánticas verificadas.',
                'Three financial views built with verified semantic queries.',
              )
        }
      />
      {error ? <Alert color="danger">{error}</Alert> : null}
      {!catalog || !context || !dashboard ? (
        <div className="border rounded-2 p-4 text-center">{copy(locale, 'Cargando dashboards…', 'Loading dashboards…')}</div>
      ) : (
        <div className="d-flex flex-column gap-4">
          <DashboardNav templates={templates} selected={dashboard} locale={locale} />
          <ProductSection title={<span className="visually-hidden">{copy(locale, 'Contexto del dashboard', 'Dashboard context')}</span>}>
            <AnalyticsContextControls
              catalog={catalog}
              context={context}
              accounts={accounts}
              locale={locale}
              disabled={false}
              onChange={setContext}
            />
          </ProductSection>
          {dashboard.id === 'financial-overview' ? (
            <div className="d-flex flex-wrap align-items-center justify-content-end gap-2">
              <label className="small fw-semibold" htmlFor="overview-trend-granularity">
                {copy(locale, 'Tendencia por', 'Trend by')}
              </label>
              <select
                id="overview-trend-granularity"
                className="form-select form-select-sm w-auto"
                value={overviewGranularity}
                onChange={event => setOverviewGranularity(event.target.value as TrendGranularity)}
                data-cy="overviewTrendGranularity"
              >
                {(['AUTO', 'TRANSACTION_DAY', 'TRANSACTION_WEEK', 'TRANSACTION_MONTH', 'TRANSACTION_YEAR'] as TrendGranularity[]).map(
                  grouping => (
                    <option key={grouping} value={grouping}>
                      {trendGroupingLabel(locale, grouping)}
                    </option>
                  ),
                )}
              </select>
              {overviewGranularity === 'AUTO' ? (
                <span className="small text-muted">
                  {copy(
                    locale,
                    `Ahora: ${trendGroupingLabel(locale, resolvedTrendGrouping('AUTO', context))}`,
                    `Now: ${trendGroupingLabel(locale, resolvedTrendGrouping('AUTO', context))}`,
                  )}
                </span>
              ) : null}
            </div>
          ) : null}
          <p className="small text-muted mb-0">
            {copy(
              locale,
              'El período, las cuentas y las monedas se aplican a cada widget. Cambiarlos nunca altera sus consultas semánticas.',
              'Period, accounts, and currencies apply to every widget. Changing them never alters their semantic queries.',
            )}
          </p>
          <div className="row g-3">
            {widgets.map(widget => (
              <WidgetCard
                key={widget.id}
                widget={widget}
                state={states[widget.id]}
                locale={locale}
                context={context}
                onDrillDown={drillDown}
                onDrillBack={drillBack}
              />
            ))}
          </div>
        </div>
      )}
    </ProductPage>
  );
};

export default RecommendedDashboards;
