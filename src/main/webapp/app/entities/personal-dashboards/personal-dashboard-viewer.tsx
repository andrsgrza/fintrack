import React, { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button } from 'reactstrap';

import { useAppSelector } from 'app/config/store';
import { AnalyticsContextControls } from 'app/entities/analytics-explorer/analytics-context-controls';
import { getExplorerAccounts, getSemanticAnalyticsCatalog } from 'app/entities/analytics-explorer/analytics-explorer-api';
import {
  SemanticAnalyticsCatalog,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';
import { AnalyticsResultsPanel } from 'app/entities/analytics-explorer/analytics-results-panel';
import {
  categoryGroupingIndex,
  groupCategoryChildren,
  isTemporalGrouping,
  replaceTemporalGrouping,
  temporalOptionsFor,
} from 'app/entities/analytics-explorer/analytics-grouping';
import { copy, datasetById, label } from 'app/entities/analytics-explorer/analytics-explorer-utils';
import { dashboardDiagnostics, useDashboardWidgetResults } from 'app/entities/recommended-dashboards/recommended-dashboards';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { getPersonalDashboard, PersonalDashboard, PersonalDashboardWidget } from './personal-dashboard-api';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type DrillState = { definition: SemanticQueryDefinition; label: string };
type ViewerWidget = PersonalDashboardWidget & { definition: SemanticQueryDefinition; trail: DrillState[]; contextPolicy?: 'CURRENT_ONLY' };

const widthClass = (width: PersonalDashboardWidget['width']) =>
  width === 'FULL' ? 'col-12' : width === 'COMPACT' ? 'col-12 col-md-6 col-xl-4' : 'col-12 col-xl-6';
const asOfDatasets = new Set(['FINANCIAL_POSITION_SNAPSHOT', 'ACCOUNT_POSITION_SNAPSHOT']);
const formatAsOf = (locale: string, isoDate: string) =>
  new Intl.DateTimeFormat(locale.startsWith('es') ? 'es-MX' : 'en-US', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(`${isoDate}T12:00:00Z`),
  );
const policyFor = (widget: PersonalDashboardWidget): 'CURRENT_ONLY' | undefined =>
  widget.visualizationOptions?.contextPolicy === 'CURRENT_ONLY' ? 'CURRENT_ONLY' : undefined;
const seriesLimitFor = (widget: PersonalDashboardWidget): number | 'ALL' => {
  const value = widget.visualizationOptions?.seriesLimit;
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : 'ALL';
};
const temporalOptionsForDefinition = (catalog: SemanticAnalyticsCatalog, definition: SemanticQueryDefinition) => {
  const dataset = datasetById(catalog, definition.dataset);
  return dataset ? temporalOptionsFor(dataset) : [];
};

const PersonalWidgetCard = ({
  widget,
  state,
  locale,
  dashboardId,
  temporalOptions,
  onTemporalIntervalChange,
  onDrillDown,
  onDrillBack,
}: {
  widget: ViewerWidget;
  state: ReturnType<typeof useDashboardWidgetResults>[string] | undefined;
  locale: string;
  dashboardId: number;
  temporalOptions: string[];
  onTemporalIntervalChange: (widgetId: number, interval: string) => void;
  onDrillDown: (widget: ViewerWidget, categoryId: string, categoryLabel: string) => void;
  onDrillBack: (widget: ViewerWidget) => void;
}) => {
  const currentOnly = state?.error === 'CURRENT_ONLY_CONTEXT';
  const asOf = asOfDatasets.has(widget.definition.dataset) && state?.result?.effectiveContext.through;
  const temporalGrouping = widget.definition.groupBy.find(isTemporalGrouping);
  return (
    <article className={widthClass(widget.width)} data-cy={`personalDashboardWidget-${widget.id}`}>
      <div className="card border shadow-sm h-100">
        <div className="card-body p-3 p-md-4">
          <h2 className="h5 mb-1">{widget.title}</h2>
          {widget.description ? <p className="small text-muted mb-3">{widget.description}</p> : null}
          <div className="d-flex align-items-center justify-content-end gap-2 mb-2">
            {temporalGrouping && temporalOptions.length > 1 ? (
              <>
                <label className="small fw-semibold mb-0" htmlFor={`personal-dashboard-widget-interval-${widget.id}`}>
                  {copy(locale, 'Intervalo', 'Interval')}
                </label>
                <select
                  id={`personal-dashboard-widget-interval-${widget.id}`}
                  className="form-select form-select-sm w-auto"
                  value={temporalGrouping}
                  onChange={event => onTemporalIntervalChange(widget.id, event.target.value)}
                  data-cy={`personalDashboardWidgetInterval-${widget.id}`}
                >
                  {temporalOptions.map(interval => (
                    <option key={interval} value={interval}>
                      {label(locale, interval)}
                    </option>
                  ))}
                </select>
              </>
            ) : null}
            <Button
              tag={Link}
              to={`/analytics/explorer?dashboardId=${dashboardId}&widgetId=${widget.id}`}
              color="secondary"
              outline
              size="sm"
              data-cy={`personalDashboardEditWidget-${widget.id}`}
            >
              {copy(locale, 'Editar visualización', 'Edit visualization')}
            </Button>
          </div>
          {asOf ? (
            <p className="small fw-semibold mb-3">{copy(locale, `Al ${formatAsOf(locale, asOf)}`, `As of ${formatAsOf(locale, asOf)}`)}</p>
          ) : null}
          {currentOnly ? (
            <div className="small border-start border-info border-3 ps-2" role="status">
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
              visualization={widget.visualizationType}
              locale={locale}
              loading={state?.loading ?? true}
              stale={false}
              seriesLimit={seriesLimitFor(widget)}
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

/** A stored dashboard executes its saved canonical widgets against a temporary, viewer-level context only. */
const PersonalDashboardViewer = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const { dashboardId } = useParams<{ dashboardId: string }>();
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [dashboard, setDashboard] = useState<PersonalDashboard | null>(null);
  const [context, setContext] = useState<VisualizationContext | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [drillStates, setDrillStates] = useState<Record<number, { definition: SemanticQueryDefinition; trail: DrillState[] }>>({});
  // This is deliberately not stored in the dashboard. It only changes the
  // canonical query copy sent by this rendered widget until the next reload.
  const [temporaryIntervals, setTemporaryIntervals] = useState<Record<number, string>>({});

  useEffect(() => {
    let active = true;
    setDashboard(null);
    setContext(null);
    setDrillStates({});
    setTemporaryIntervals({});
    void Promise.all([getSemanticAnalyticsCatalog(), getExplorerAccounts(), getPersonalDashboard(dashboardId ?? '')])
      .then(([nextCatalog, nextAccounts, nextDashboard]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setAccounts(
          nextAccounts
            .filter(account => account.id != null && account.name)
            .map(account => ({ id: account.id, name: account.name, accountType: account.accountType, currency: account.currency })),
        );
        setDashboard(nextDashboard);
        // This is a local copy: changing controls below never invokes a dashboard mutation endpoint.
        setContext(nextDashboard.defaultContext);
      })
      .catch(requestError => active && setError(dashboardDiagnostics(requestError)));
    return () => {
      active = false;
    };
  }, [dashboardId]);

  const widgets = useMemo<ViewerWidget[]>(
    () =>
      (dashboard?.widgets ?? [])
        .filter(widget => widget.visible)
        .map(widget => {
          const canonicalDefinition = drillStates[widget.id]?.definition ?? widget.definition;
          const interval = temporaryIntervals[widget.id];
          return {
            ...widget,
            definition: interval ? replaceTemporalGrouping(canonicalDefinition, interval) : canonicalDefinition,
            trail: drillStates[widget.id]?.trail ?? [],
            contextPolicy: policyFor(widget),
          };
        }),
    [dashboard, drillStates, temporaryIntervals],
  );
  const states = useDashboardWidgetResults(widgets, context);

  const drillDown = (widget: ViewerWidget, categoryId: string, categoryLabel: string) => {
    if (widget.definition.dataset !== 'TRANSACTION_LEDGER') return;
    const groupingIndex = categoryGroupingIndex(widget.definition);
    if (groupingIndex < 0) return;
    const definition = groupCategoryChildren(widget.definition, groupingIndex as 0 | 1, categoryId);
    setDrillStates(previous => ({
      ...previous,
      [widget.id]: { definition, trail: [...widget.trail, { definition: widget.definition, label: categoryLabel }] },
    }));
  };
  const drillBack = (widget: ViewerWidget) => {
    const previous = widget.trail[widget.trail.length - 1];
    if (!previous) return;
    setDrillStates(current => ({ ...current, [widget.id]: { definition: previous.definition, trail: widget.trail.slice(0, -1) } }));
  };

  return (
    <ProductPage wide className="personal-dashboard-viewer">
      <ProductPageHeader
        headingId="personal-dashboard-heading"
        title={dashboard?.name ?? copy(locale, 'Dashboard personal', 'Personal dashboard')}
        subtitle={
          dashboard?.description ??
          copy(locale, 'Una vista personal basada en consultas financieras guardadas.', 'A personal view based on saved financial queries.')
        }
        actions={
          <Button tag={Link} to="/analytics/dashboards" color="secondary" outline>
            {copy(locale, 'Volver a dashboards', 'Back to dashboards')}
          </Button>
        }
      />
      {error ? <Alert color="danger">{error}</Alert> : null}
      {!catalog || !dashboard || !context ? (
        <div className="border rounded-2 p-4 text-center">
          {error
            ? copy(locale, 'No fue posible abrir este dashboard.', 'This dashboard could not be opened.')
            : copy(locale, 'Cargando dashboard…', 'Loading dashboard…')}
        </div>
      ) : (
        <div className="d-flex flex-column gap-4">
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
          <p className="small text-muted mb-0" data-cy="personalDashboardContextNotice">
            {copy(
              locale,
              'Los cambios de período, cuentas y monedas son temporales para esta vista; el dashboard guardado no se modifica.',
              'Period, account, and currency changes are temporary for this view; the saved dashboard is not changed.',
            )}
          </p>
          {!widgets.length ? (
            <section className="border rounded-2 p-4 text-center" data-cy="personalDashboardViewerEmptyState">
              <h2 className="h5">
                {copy(locale, 'Este dashboard aún no tiene visualizaciones', 'This dashboard has no visualizations yet')}
              </h2>
              <p className="small text-muted mb-0">
                {copy(
                  locale,
                  'Crea una consulta en el Explorador de métricas y guárdala aquí cuando esté lista.',
                  'Create a query in Metric Explorer and save it here when it is ready.',
                )}
              </p>
              <Button
                tag={Link}
                to={`/analytics/explorer?dashboardId=${dashboard.id}`}
                color="primary"
                size="sm"
                className="mt-3"
                data-cy="personalDashboardAddVisualization"
              >
                {copy(locale, 'Añadir visualización', 'Add visualization')}
              </Button>
            </section>
          ) : (
            <div className="row g-3" data-cy="personalDashboardWidgets">
              {widgets.map(widget => (
                <PersonalWidgetCard
                  key={widget.id}
                  widget={widget}
                  state={states[widget.id]}
                  locale={locale}
                  dashboardId={dashboard.id}
                  temporalOptions={temporalOptionsForDefinition(catalog, widget.definition)}
                  onTemporalIntervalChange={(widgetId, interval) =>
                    setTemporaryIntervals(previous => ({ ...previous, [widgetId]: interval }))
                  }
                  onDrillDown={drillDown}
                  onDrillBack={drillBack}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </ProductPage>
  );
};

export default PersonalDashboardViewer;
