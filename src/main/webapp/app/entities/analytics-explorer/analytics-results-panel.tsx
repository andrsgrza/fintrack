import React, { useEffect, useId, useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button } from 'reactstrap';

import { ExplorerVisualization, SemanticAnalyticsResult, SemanticQueryDefinition } from './analytics-explorer.model';
import { AnalyticsResultTable } from './analytics-result-table';
import {
  copy,
  formatTemporalBucket,
  isCategoryField,
  isTemporalField,
  temporalBucketIdentity,
  temporalBucketsInContext,
  label,
  rowLabel,
  temporalSortKey,
  visualizationTitle,
} from './analytics-explorer-utils';
import {
  visualizationEligibility as compatibilityEligibility,
  visualizationResultIncompatibility,
} from './analytics-visualization-compatibility';

export { visualizationEligibility } from './analytics-visualization-compatibility';
export type { VisualizationEligibility } from './analytics-visualization-compatibility';

type Row = SemanticAnalyticsResult['rows'][number];
type Value = Row['values'][number];
type Unit = Value['unit'];
export type ChartSeries = { key: string; semanticId: string; label: string; color: string; rank: number; sortKey: number | null };
export type ResultPanel = { currency: string | null; unit: Unit; rows: Row[]; values: Array<{ row: Row; value: Value }> };
export type ChartPoint = { x: string; xId: string; sortKey: number | null; [key: string]: string | number | boolean | null };
export type ChartRows = {
  data: ChartPoint[];
  series: ChartSeries[];
  hidden: number;
  hasPlottableValues: boolean;
  temporal: boolean;
  temporalSeries: boolean;
  fillsContext: boolean;
  missingPeriods: number;
};

type Props = {
  definition: SemanticQueryDefinition | null;
  result: SemanticAnalyticsResult | null;
  visualization: ExplorerVisualization;
  locale: string;
  loading: boolean;
  stale: boolean;
  seriesLimit: number | 'ALL';
  drillTrail: string[];
  onDrillDown: (categoryId: string, labelText: string) => void;
  onDrillBack: () => void;
  /** Allows recommended and future personal widgets to reuse the exact renderer without nesting Explorer chrome. */
  compact?: boolean;
};

const palette = ['#2f6493', '#5f8f3e', '#9b5d97', '#ce7b32', '#55758f', '#a95656', '#8c7c35', '#4f7f73', '#6b6b9f', '#96705b'];
export const formatMoney = (value: number, currency: string | null, locale: string) => {
  const code = currency ?? 'MXN';
  const language = locale.startsWith('es') ? 'es-MX' : 'en-US';
  const formatted = new Intl.NumberFormat(language, {
    style: 'currency',
    currency: code,
    currencyDisplay: 'narrowSymbol',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
  return `${formatted} ${code}`;
};
const display = (value: number | null, unit: Unit, currency: string | null, locale: string) => {
  if (value === null) return '—';
  if (unit === 'MONEY') return formatMoney(value, currency, locale);
  if (unit === 'PERCENT') return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(value / 100);
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value);
};

/** Query aliases are contract identifiers. Presentation gives the common financial aliases a person-facing name. */
const presentationAlias = (alias: string, locale: string) => {
  const spanish = locale.startsWith('es');
  const known: Record<string, [string, string]> = {
    posicion_financiera: ['Posición financiera registrada', 'Registered financial position'],
    ingresos: ['Ingresos', 'Income'],
    gastos: ['Gastos', 'Expenses'],
    flujo_neto: ['Flujo neto', 'Net flow'],
    saldo_registrado: ['Saldo registrado', 'Recorded balance'],
    deuda_tarjeta: ['Deuda de tarjetas', 'Card debt'],
    limite_credito: ['Límite de crédito', 'Credit limit'],
    credito_disponible: ['Crédito disponible', 'Available credit'],
    utilizacion_credito: ['Utilización de crédito', 'Credit utilization'],
    sin_categoria: ['Gastos sin categoría', 'Uncategorized expenses'],
    transferencias: ['Transferencias internas', 'Internal transfers'],
    importe_transferido: ['Importe transferido', 'Transferred amount'],
  };
  const translated = known[alias.toLocaleLowerCase()];
  if (translated) return translated[spanish ? 0 : 1];
  const humanized = alias.replace(/_/g, ' ').trim();
  return humanized ? `${humanized.charAt(0).toLocaleUpperCase(locale)}${humanized.slice(1)}` : alias;
};

/** Backend codes remain stable; dashboard copy explains their financial meaning without exposing implementation wording. */
const warningText = (code: string, message: string, locale: string) => {
  switch (code) {
    case 'FINANCIAL_POSITION_AT_CUTOFF':
      return copy(
        locale,
        'La posición usa un solo corte por cuenta: saldos compatibles menos deuda registrada de tarjetas, siempre por moneda.',
        'Position uses one cutoff per account: compatible balances less recorded card debt, always per currency.',
      );
    case 'ACCOUNT_POSITION_AT_CUTOFF':
      return copy(
        locale,
        'Cada cuenta se muestra una sola vez, con su saldo registrado a la fecha de corte.',
        'Each account appears once with its recorded balance at the cutoff.',
      );
    case 'NON_ADDITIVE_ACCOUNT_POSITION':
      return copy(
        locale,
        'Los saldos históricos se leen por cuenta y fecha; no se suman entre fechas.',
        'Historical balances are read per account and date; they are not added across dates.',
      );
    case 'CREDIT_DEBT_POINT_IN_TIME':
      return copy(
        locale,
        'La deuda se muestra por tarjeta y fecha de observación; no se suma entre fechas.',
        'Debt is shown per card and observation date; it is not added across dates.',
      );
    case 'INVESTMENT_BOOK_BALANCE_NOT_MARKET_VALUE':
      return copy(
        locale,
        'Las inversiones muestran su saldo registrado, no valor de mercado ni rendimiento.',
        'Investments show their recorded balance, not market value or return.',
      );
    case 'CREDIT_DETAILS_UNAVAILABLE':
      return copy(
        locale,
        'Algunas tarjetas no tienen límite registrado; sus valores derivados no están disponibles.',
        'Some cards have no recorded limit, so their derived values are unavailable.',
      );
    case 'CREDIT_UTILIZATION_UNAVAILABLE':
      return copy(
        locale,
        'La utilización requiere un límite de crédito positivo registrado; la deuda sigue disponible.',
        'Utilization needs a recorded positive credit limit; debt remains available.',
      );
    case 'TAG_GROUPS_OVERLAP':
    case 'NON_ADDITIVE_OVERLAPPING_TAG_GROUPS':
      return copy(
        locale,
        'Las etiquetas pueden superponerse; sus totales no son partes exclusivas de un mismo total.',
        'Tags can overlap, so their totals are not exclusive parts of one total.',
      );
    case 'TRANSFER_EVENT_VOLUME_NOT_ECONOMIC_FLOW':
      return copy(
        locale,
        'Cada transferencia se cuenta una vez; no es un ingreso ni un gasto económico.',
        'Each transfer is counted once; it is neither economic income nor expense.',
      );
    case 'NO_APPLICABLE_CREDIT_ACCOUNTS':
      return copy(locale, 'No hay tarjetas de crédito aplicables para este contexto.', 'No credit cards apply to this context.');
    case 'NO_APPLICABLE_POSITION_ACCOUNTS':
      return copy(
        locale,
        'No hay cuentas de activo o tarjetas aplicables para este contexto.',
        'No asset or card accounts apply to this context.',
      );
    default:
      return message;
  }
};

const panelKey = (currency: string | null, unit: Unit) => `${currency ?? 'NON_MONETARY'}:${unit}`;
const dimensionLabel = (row: Row, index: number, locale: string) => {
  const dimension = row.dimensions[index];
  if (!dimension) return '—';
  return isTemporalField(dimension.field)
    ? formatTemporalBucket(dimension.field, dimension.value, locale)
    : (dimension.metadata?.label ?? dimension.value ?? '—');
};
const dimensionId = (row: Row, index: number) => {
  const dimension = row.dimensions[index];
  if (dimension && isTemporalField(dimension.field)) return temporalBucketIdentity(dimension.field, dimension.value);
  return dimension?.metadata?.stableId ?? `${dimension?.field}:${dimension?.value ?? '—'}`;
};
const colorFor = (key: string) => palette[Math.abs([...key].reduce((total, char) => total + char.charCodeAt(0), 0)) % palette.length];
const semanticColor = (value: string | null | undefined, fallbackIdentity: string) => {
  const candidate = value?.trim();
  return candidate && /^#[0-9a-f]{3,8}$/i.test(candidate) ? candidate : colorFor(fallbackIdentity);
};
const resultPanels = (result: SemanticAnalyticsResult): ResultPanel[] => {
  const panels = new Map<string, ResultPanel>();
  result.rows.forEach(row => {
    row.values.forEach(value => {
      const key = panelKey(row.currencyCode, value.unit);
      const current = panels.get(key) ?? { currency: row.currencyCode, unit: value.unit, rows: [], values: [] };
      if (!current.rows.includes(row)) current.rows.push(row);
      current.values.push({ row, value });
      panels.set(key, current);
    });
  });
  return [...panels.values()];
};

const plottableValue = (value: Value) => {
  if (value.value === null || ['NO_DATA', 'UNAVAILABLE', 'NOT_APPLICABLE'].includes(value.availability)) return null;
  return Number.isFinite(value.value) ? value.value : null;
};

/**
 * Returned semantic rows, including backend-derived zeroes, stay intact. Only a bucket absent from that result
 * becomes a null presentation point, so the chart never converts an unknown or unavailable period into zero.
 */
export const chartRows = (
  panel: ResultPanel,
  limit: number | 'ALL',
  locale: string,
  effectiveContext?: SemanticAnalyticsResult['effectiveContext'],
  _visualization: ExplorerVisualization = 'BAR',
): ChartRows => {
  const valuesByX = new Map<string, ChartPoint>();
  const ranks = new Map<string, number>();
  const seriesMetadata = new Map<string, { semanticId: string; color: string; sortKey: number | null }>();
  const dimensions = panel.rows.find(row => row.dimensions.length)?.dimensions ?? [];
  // `groupBy[0]` is always the axis. The chart never changes the semantic grouping order merely because a line has
  // a temporal comparison: Category + Month means category X values and one line per month.
  const xIndex = 0;
  const xField = dimensions[xIndex]?.field ?? '';
  const projectionAliases = new Set(panel.values.map(({ value }) => value.alias));
  panel.values.forEach(({ row, value }) => {
    const xDimension = row.dimensions[xIndex];
    const xId = xDimension ? dimensionId(row, xIndex) : 'TOTAL';
    const x = xDimension ? String(dimensionLabel(row, xIndex, locale)) : copy(locale, 'Total', 'Total');
    const seriesDimensions = row.dimensions.slice(1);
    const seriesIdentity = seriesDimensions
      .map(dimension =>
        isTemporalField(dimension.field)
          ? temporalBucketIdentity(dimension.field, dimension.value)
          : (dimension.metadata?.stableId ?? `${dimension.field}:${dimension.value ?? '—'}`),
      )
      .join('::');
    const seriesText = seriesDimensions
      .map(dimension =>
        isTemporalField(dimension.field)
          ? formatTemporalBucket(dimension.field, dimension.value, locale)
          : (dimension.metadata?.label ?? dimension.value ?? '—'),
      )
      .join(' · ');
    const key = `${value.alias}::${seriesIdentity}`;
    const seriesLabel = seriesDimensions.length
      ? projectionAliases.size === 1 && seriesDimensions.every(dimension => isTemporalField(dimension.field))
        ? seriesText
        : `${presentationAlias(value.alias, locale)} · ${seriesText}`
      : presentationAlias(value.alias, locale);
    // A secondary category or tag owns its configured colour. When the secondary member is a period, colours come
    // from a deterministic palette instead of accidentally borrowing a category colour from the primary axis.
    const colorDimension =
      seriesDimensions.find(dimension => !isTemporalField(dimension.field)) ??
      (seriesDimensions.length === 0 && xDimension && !isTemporalField(xDimension.field) ? xDimension : undefined);
    const colorIdentity =
      colorDimension?.metadata?.stableId ?? (seriesDimensions.length ? `SERIES:${seriesIdentity}` : `MEASURE:${value.alias}`);
    const semanticId = `${value.alias}:${seriesIdentity || colorIdentity}`;
    const seriesSortKey =
      seriesDimensions.length === 1 && isTemporalField(seriesDimensions[0].field)
        ? temporalSortKey(seriesDimensions[0].field, seriesDimensions[0].value)
        : null;
    const item =
      valuesByX.get(xId) ??
      ({ x, xId, sortKey: xDimension ? temporalSortKey(xDimension.field, xDimension.value) : null } satisfies ChartPoint);
    item[key] = plottableValue(value);
    item[`${key}::label`] = seriesLabel;
    item[`${key}::derived`] = value.derived === true;
    item[`${key}::color`] = semanticColor(colorDimension?.metadata?.color, colorIdentity);
    valuesByX.set(xId, item);
    ranks.set(key, (ranks.get(key) ?? 0) + Math.abs(plottableValue(value) ?? 0));
    if (!seriesMetadata.has(key)) {
      seriesMetadata.set(key, { semanticId, color: semanticColor(colorDimension?.metadata?.color, colorIdentity), sortKey: seriesSortKey });
    }
  });
  const series = [...ranks.entries()]
    .map(([key, rank]) => ({
      key,
      label: String([...valuesByX.values()].find(row => row[`${key}::label`])?.[`${key}::label`] ?? key),
      semanticId: seriesMetadata.get(key)?.semanticId ?? key,
      color: seriesMetadata.get(key)?.color ?? colorFor(key),
      rank,
      sortKey: seriesMetadata.get(key)?.sortKey ?? null,
    }))
    .sort((left, right) => {
      if (left.sortKey !== null && right.sortKey !== null && left.sortKey !== right.sortKey) return left.sortKey - right.sortKey;
      if (left.sortKey !== null) return -1;
      if (right.sortKey !== null) return 1;
      return left.semanticId.localeCompare(right.semanticId) || left.key.localeCompare(right.key);
    });
  const selected = limit === 'ALL' ? series : series.slice(0, limit);
  const selectedKeys = new Set(selected.map(seriesItem => seriesItem.key));
  const temporal = isTemporalField(xField);
  const temporalSeries = dimensions.slice(1).some(dimension => isTemporalField(dimension.field));
  let missingPeriods = 0;
  const contextBuckets =
    temporal && effectiveContext ? temporalBucketsInContext(xField, effectiveContext.from, effectiveContext.through) : [];
  contextBuckets.forEach(bucket => {
    const xId = temporalBucketIdentity(xField, bucket.value);
    if (!valuesByX.has(xId)) {
      valuesByX.set(xId, {
        x: formatTemporalBucket(xField, bucket.value, locale),
        xId,
        sortKey: bucket.sortKey,
      });
      missingPeriods += 1;
    }
  });
  const rows = [...valuesByX.values()];
  if (temporal) rows.sort((left, right) => (left.sortKey ?? 0) - (right.sortKey ?? 0) || left.xId.localeCompare(right.xId));
  const data = rows.map(item => {
    const next: ChartPoint = { x: item.x, xId: item.xId, sortKey: item.sortKey };
    selected.forEach(seriesItem => {
      next[seriesItem.key] = typeof item[seriesItem.key] === 'number' ? item[seriesItem.key] : null;
      next[`${seriesItem.key}::derived`] = item[`${seriesItem.key}::derived`] === true;
      next[`${seriesItem.key}::color`] = item[`${seriesItem.key}::color`] ?? seriesItem.color;
    });
    return next;
  });
  return {
    data,
    series: selected,
    hidden: series.filter(seriesItem => !selectedKeys.has(seriesItem.key)).length,
    hasPlottableValues: data.some(row => selected.some(seriesItem => typeof row[seriesItem.key] === 'number')),
    temporal,
    temporalSeries,
    fillsContext: contextBuckets.length > 0,
    missingPeriods,
  };
};

const niceStep = (value: number) => {
  const magnitude = 10 ** Math.floor(Math.log10(Math.max(value, Number.EPSILON)));
  const normalized = value / magnitude;
  const base = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return base * magnitude;
};

/** Keeps axis values numeric and adds only visual padding/ticks; it never changes a plotted amount. */
export const chartAxisScale = (values: Array<number | null>) => {
  const numeric = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  if (!numeric.length) return null;
  const minimum = Math.min(...numeric);
  const maximum = Math.max(...numeric);
  let lower = minimum >= 0 ? 0 : minimum;
  let upper = maximum <= 0 ? 0 : maximum;
  if (lower === upper) {
    const padding = Math.max(Math.abs(lower) * 0.2, 1);
    lower -= padding;
    upper += padding;
  }
  const step = niceStep((upper - lower) / 4);
  lower = Math.floor(lower / step) * step;
  upper = Math.ceil(upper / step) * step;
  const ticks: number[] = [];
  for (let tick = lower; tick <= upper + step / 1000; tick += step) ticks.push(Number(tick.toPrecision(12)));
  return { domain: [lower, upper] as [number, number], ticks };
};

const panelTitle = (panel: ResultPanel, locale: string) => {
  if (panel.unit === 'MONEY') {
    return panel.currency
      ? copy(locale, `Importes en ${panel.currency}`, `Amounts in ${panel.currency}`)
      : copy(locale, 'Importes', 'Amounts');
  }
  if (panel.unit === 'PERCENT') return copy(locale, 'Porcentajes', 'Percentages');
  return copy(locale, 'Cantidad de resultados', 'Result count');
};

type LegendEntry = { key: string; semanticId: string; label: string; color: string };

/**
 * Presentation-only, identity-keyed series controls.  The complete semantic result remains in the table even when a
 * person hides a series here; neither the query definition nor the visualization context is changed.
 */
const InteractiveChartLegend = ({
  entries,
  hidden,
  onToggle,
  onShowAll,
  onHideAll,
  onIsolate,
  locale,
}: {
  entries: LegendEntry[];
  hidden: ReadonlySet<string>;
  onToggle: (key: string) => void;
  onShowAll: () => void;
  onHideAll: () => void;
  onIsolate: (key: string) => void;
  locale: string;
}) => {
  const [search, setSearch] = useState('');
  const matches = entries.filter(entry => entry.label.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  const shouldSearch = entries.length > 8;
  return (
    <section
      className="border-top mt-2 pt-2"
      aria-label={copy(locale, 'Leyenda de series', 'Series legend')}
      data-cy="analyticsInteractiveLegend"
    >
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
        <span className="small fw-semibold">
          {copy(locale, 'Series', 'Series')} ({entries.length})
        </span>
        <div className="d-flex gap-1">
          <Button type="button" color="secondary" outline size="sm" onClick={onShowAll} data-cy="analyticsLegendShowAll">
            {copy(locale, 'Mostrar todas', 'Show all')}
          </Button>
          <Button type="button" color="secondary" outline size="sm" onClick={onHideAll} data-cy="analyticsLegendHideAll">
            {copy(locale, 'Ocultar todas', 'Hide all')}
          </Button>
        </div>
      </div>
      {shouldSearch ? (
        <input
          type="search"
          className="form-control form-control-sm mb-2"
          value={search}
          onChange={event => setSearch(event.target.value)}
          placeholder={copy(locale, 'Buscar una serie', 'Search a series')}
          aria-label={copy(locale, 'Buscar una serie en la leyenda', 'Search a series in the legend')}
          data-cy="analyticsLegendSearch"
        />
      ) : null}
      <div className="overflow-auto pe-1" style={{ maxHeight: 176 }}>
        <ul className="list-unstyled mb-0 d-grid gap-1">
          {matches.map(entry => {
            const isHidden = hidden.has(entry.key);
            return (
              <li key={entry.key} className="d-flex align-items-center gap-1">
                <Button
                  type="button"
                  color="link"
                  className={`text-start text-decoration-none flex-grow-1 p-1 rounded ${isHidden ? 'text-muted opacity-50' : 'text-body'}`}
                  aria-pressed={!isHidden}
                  onClick={() => onToggle(entry.key)}
                  data-cy={`analyticsLegendToggle-${entry.semanticId}`}
                >
                  <span aria-hidden="true" className="me-1" style={{ color: entry.color }}>
                    ●
                  </span>
                  {entry.label}
                </Button>
                <Button
                  type="button"
                  color="secondary"
                  outline
                  size="sm"
                  onClick={() => onIsolate(entry.key)}
                  aria-label={copy(locale, `Mostrar sólo ${entry.label}`, `Show only ${entry.label}`)}
                >
                  {copy(locale, 'Sólo', 'Only')}
                </Button>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
};

const FinancialTooltip = ({
  active,
  label: pointLabel,
  payload,
  currency,
  unit,
  locale,
}: {
  active?: boolean;
  label?: string | number;
  payload?: Array<{
    dataKey?: string | number;
    name?: string;
    value?: number;
    color?: string;
    fill?: string;
    stroke?: string;
    payload?: ChartPoint;
  }>;
  currency: string | null;
  unit: Unit;
  locale: string;
}) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border rounded-2 p-2 shadow-sm small">
      {pointLabel ? <div className="fw-semibold mb-1">{pointLabel}</div> : null}
      {payload.map(item => {
        const derived = item.dataKey ? item.payload?.[`${item.dataKey}::derived`] === true : false;
        const pointColor =
          item.color ??
          item.fill ??
          item.stroke ??
          (item.dataKey && typeof item.payload?.[`${item.dataKey}::color`] === 'string'
            ? String(item.payload?.[`${item.dataKey}::color`])
            : undefined);
        return (
          <div key={item.name}>
            {pointColor ? (
              <span aria-hidden="true" className="me-1" style={{ color: pointColor }}>
                ●
              </span>
            ) : null}
            {item.name}: {display(item.value ?? null, unit, currency, locale)}
            {derived ? (
              <span className="d-block text-muted">
                {copy(locale, 'Sin movimientos registrados en este período.', 'No registered movements in this period.')}
              </span>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

const SemanticChart = ({
  panel,
  visualization,
  seriesLimit,
  locale,
  effectiveContext,
  horizontal,
}: {
  panel: ResultPanel;
  visualization: ExplorerVisualization;
  seriesLimit: number | 'ALL';
  locale: string;
  effectiveContext: SemanticAnalyticsResult['effectiveContext'];
  horizontal: boolean;
}) => {
  const chart = useMemo(
    () => chartRows(panel, seriesLimit, locale, effectiveContext, visualization),
    [panel, seriesLimit, locale, effectiveContext, visualization],
  );
  const [hiddenSeries, setHiddenSeries] = useState<Set<string>>(() => new Set());
  const identity = chart.series.map(series => series.key).join('|');
  useEffect(() => {
    setHiddenSeries(current => new Set([...current].filter(key => chart.series.some(series => series.key === key))));
  }, [identity]);
  const visibleSeries = chart.series.filter(series => !hiddenSeries.has(series.key));
  const scale = useMemo(
    () => chartAxisScale(chart.data.flatMap(point => visibleSeries.map(series => point[series.key] as number | null))),
    [chart.data, visibleSeries],
  );
  const legend = chart.series.map(series => ({ ...series }));
  const toggle = (key: string) =>
    setHiddenSeries(current => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  const hideAll = () => setHiddenSeries(new Set(chart.series.map(series => series.key)));
  const showAll = () => setHiddenSeries(new Set());
  const isolate = (key: string) => setHiddenSeries(new Set(chart.series.filter(series => series.key !== key).map(series => series.key)));
  if (!chart.data.length || !chart.series.length) return null;
  if (!chart.hasPlottableValues) {
    return (
      <div className="border rounded-2 p-3 text-muted" data-cy="analyticsChartNoPlottableValues">
        {copy(
          locale,
          'No hay valores numéricos disponibles para graficar. La tabla conserva la disponibilidad de cada período.',
          'There are no numeric values available to chart. The table preserves each period’s availability.',
        )}
      </div>
    );
  }
  if (visualization === 'DONUT') {
    const series = chart.series[0];
    const data = panel.values.flatMap(({ row, value }) => {
      if (typeof plottableValue(value) !== 'number') return [];
      const dimension = row.dimensions[0];
      const semanticId = dimension?.metadata?.stableId ?? `${dimension?.field}:${dimension?.value}`;
      return [
        {
          key: `${series.key}::${semanticId}`,
          semanticId,
          name: dimension ? dimensionLabel(row, 0, locale) : String(rowLabel(row, locale)),
          value: plottableValue(value) ?? 0,
          color: semanticColor(dimension?.metadata?.color, semanticId),
        },
      ];
    });
    const visibleSlices = data.filter(slice => !hiddenSeries.has(slice.key));
    return (
      <>
        {visibleSlices.length ? (
          <div style={{ height: 340 }} role="img" aria-label={copy(locale, 'Gráfica de dona', 'Donut chart')}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart margin={{ top: 16, right: 24, bottom: 44, left: 24 }}>
                <Tooltip content={<FinancialTooltip currency={panel.currency} unit={panel.unit} locale={locale} />} />
                <Pie
                  data={visibleSlices}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="45%"
                  innerRadius={56}
                  outerRadius={108}
                  paddingAngle={1}
                  label
                  isAnimationActive={false}
                >
                  {visibleSlices.map(slice => (
                    <Cell key={slice.key} fill={slice.color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <div className="border rounded-2 p-3 text-muted">{copy(locale, 'Todas las series están ocultas.', 'All series are hidden.')}</div>
        )}
        <InteractiveChartLegend
          entries={data.map(slice => ({ ...slice, label: String(slice.name) }))}
          hidden={hiddenSeries}
          onToggle={toggle}
          onShowAll={showAll}
          onHideAll={hideAll}
          onIsolate={isolate}
          locale={locale}
        />
      </>
    );
  }
  const bars = visualization === 'BAR' || visualization === 'GROUPED_BAR' || visualization === 'STACKED_BAR';
  const stackId = visualization === 'STACKED_BAR' ? 'semantic-stack' : undefined;
  const yAxisWidth = panel.unit === 'MONEY' ? 136 : panel.unit === 'PERCENT' ? 104 : 88;
  const yAxis = (
    <YAxis
      domain={scale?.domain ?? ['auto', 'auto']}
      ticks={scale?.ticks}
      tickFormatter={value => display(Number(value), panel.unit, panel.currency, locale)}
      width={yAxisWidth}
      tickMargin={8}
      tick={{ fontSize: 12 }}
    />
  );
  const horizontalValueAxis = (
    <XAxis
      type="number"
      domain={scale?.domain ?? ['auto', 'auto']}
      ticks={scale?.ticks}
      tickFormatter={value => display(Number(value), panel.unit, panel.currency, locale)}
      tickMargin={8}
      tick={{ fontSize: 12 }}
    />
  );
  return (
    <>
      <div style={{ height: 390 }} role="img" aria-label={visualizationTitle(locale, visualization)} data-cy="analyticsSemanticChart">
        <ResponsiveContainer width="100%" height="100%">
          {bars ? (
            <BarChart data={chart.data} layout={horizontal ? 'vertical' : undefined} margin={{ top: 16, right: 24, bottom: 44, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              {horizontal ? (
                <>
                  {horizontalValueAxis}
                  <YAxis type="category" dataKey="x" width={168} tick={{ fontSize: 12 }} />
                  <ReferenceLine x={0} stroke="#5f6368" strokeWidth={1.5} data-cy="analyticsZeroBaseline" />
                </>
              ) : (
                <>
                  <XAxis
                    dataKey="x"
                    angle={-24}
                    textAnchor="end"
                    interval="preserveStartEnd"
                    minTickGap={24}
                    height={76}
                    tick={{ fontSize: 12 }}
                  />
                  {yAxis}
                </>
              )}
              <Tooltip content={<FinancialTooltip currency={panel.currency} unit={panel.unit} locale={locale} />} />
              {visibleSeries.map(series => (
                <Bar key={series.key} dataKey={series.key} name={series.label} fill={series.color} stackId={stackId}>
                  {chart.data.map((point, pointIndex) => (
                    <Cell
                      key={`${series.key}-${point.xId}-${pointIndex}`}
                      fill={typeof point[`${series.key}::color`] === 'string' ? String(point[`${series.key}::color`]) : series.color}
                    />
                  ))}
                </Bar>
              ))}
            </BarChart>
          ) : (
            <LineChart data={chart.data} margin={{ top: 16, right: 24, bottom: 44, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="x"
                angle={-24}
                textAnchor="end"
                interval="preserveStartEnd"
                minTickGap={24}
                height={76}
                tick={{ fontSize: 12 }}
              />
              {yAxis}
              <Tooltip content={<FinancialTooltip currency={panel.currency} unit={panel.unit} locale={locale} />} />
              {visibleSeries.map((series, index) => (
                <Line
                  key={series.key}
                  type="linear"
                  dataKey={series.key}
                  name={series.label}
                  stroke={series.color}
                  strokeWidth={2.5}
                  strokeDasharray={index % 4 === 0 ? undefined : index % 4 === 1 ? '7 4' : index % 4 === 2 ? '3 3' : '10 3 2 3'}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                />
              ))}
            </LineChart>
          )}
        </ResponsiveContainer>
      </div>
      <InteractiveChartLegend
        entries={legend}
        hidden={hiddenSeries}
        onToggle={toggle}
        onShowAll={showAll}
        onHideAll={hideAll}
        onIsolate={isolate}
        locale={locale}
      />
      {chart.temporal ? (
        <p className="small text-muted mb-0" data-cy="analyticsTemporalChartNote">
          {chart.fillsContext
            ? copy(
                locale,
                'La gráfica respeta todo el período del contexto. Los ceros con la nota correspondiente son períodos válidos sin movimientos registrados; una ausencia de resultado sigue siendo un hueco.',
                'The chart respects the complete context period. Zeroes with the corresponding note are valid periods without registered movements; an absent result remains a gap.',
              )
            : copy(locale, 'Un período ausente no representa $0.', 'A missing period does not mean $0.')}
        </p>
      ) : null}
      {visualization === 'LINE' && chart.temporalSeries && !chart.temporal ? (
        <p className="small text-muted mb-0" data-cy="analyticsTemporalSeriesLineNote">
          {copy(
            locale,
            'Cada línea representa un período y sus puntos comparan los grupos del eje. Para comparar grupos sin unirlos, las barras agrupadas pueden ser más claras.',
            'Each line represents a period and its points compare the axis groups. Grouped bars can be clearer when you do not want to connect those groups.',
          )}
        </p>
      ) : null}
    </>
  );
};

const Kpis = ({ panel, locale, compact }: { panel: ResultPanel; locale: string; compact: boolean }) => (
  <div className="row g-2">
    {panel.values.map(({ row, value }, index) => (
      <div className={compact ? 'col-12' : 'col-12 col-sm-6 col-lg-3'} key={`${value.alias}-${index}`}>
        <div className="bg-light rounded-2 p-3 h-100">
          <div className="small text-muted">{presentationAlias(value.alias, locale)}</div>
          <div className="fs-5 fw-semibold">{display(value.value, value.unit, row.currencyCode, locale)}</div>
          <div className="small text-muted">{label(locale, value.availability)}</div>
        </div>
      </div>
    ))}
  </div>
);

/** Presentation adapts only returned semantic results. It never changes the query definition or context. */
export const AnalyticsResultsPanel = ({
  definition,
  result,
  visualization,
  locale,
  loading,
  stale,
  seriesLimit,
  drillTrail,
  onDrillDown,
  onDrillBack,
  compact = false,
}: Props) => {
  const options = compatibilityEligibility(definition, result, locale);
  const selectedOption = options.find(option => option.option === visualization);
  const incompatibility = !selectedOption?.enabled
    ? selectedOption?.reason
    : visualizationResultIncompatibility(visualization, result, locale);
  // An old saved widget remains intact. Its renderer falls back to the full semantic table with a clear explanation
  // instead of mutating the presentation choice or making its financial result disappear.
  const current = incompatibility ? 'TABLE' : visualization;
  const panels = result ? resultPanels(result) : [];
  const [detailsOpen, setDetailsOpen] = useState(false);
  const detailsId = useId();
  return (
    <section className={compact ? '' : 'card border shadow-sm'} data-cy="analyticsResults" aria-live="polite">
      <div className={compact ? '' : 'card-body p-3 p-md-4'}>
        {!compact ? (
          <div className="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
            <div>
              <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Resultados', 'Results')}</p>
              <h2 className="h5 mb-1">{copy(locale, 'Vista previa de la consulta', 'Query preview')}</h2>
              <p className="small text-muted mb-0">
                {copy(
                  locale,
                  'Los resultados siempre provienen de la última ejecución explícita.',
                  'Results always come from the last explicit run.',
                )}
              </p>
            </div>
          </div>
        ) : null}
        {stale ? (
          <div className="alert alert-warning py-2" data-cy="analyticsPreviewStale">
            {copy(
              locale,
              'El resultado está desactualizado. Ejecuta de nuevo para aplicar el borrador y el contexto actuales.',
              'The result is stale. Run again to apply the current draft and context.',
            )}
          </div>
        ) : null}
        {loading ? <div className="border rounded-2 p-4 text-center">{copy(locale, 'Calculando…', 'Calculating…')}</div> : null}
        {!loading && !result ? (
          <div className="border rounded-2 p-4 text-muted">
            {copy(locale, 'Define una consulta y ejecútala para ver resultados.', 'Define and run a query to see results.')}
          </div>
        ) : null}
        {!loading && result && !result.rows.length ? (
          <div
            className={compact ? 'border-start border-3 border-info ps-2 small text-muted' : 'border rounded-2 p-4 text-muted'}
            data-cy="analyticsNoData"
          >
            <p className="mb-2">
              {result.availability === 'NOT_APPLICABLE'
                ? copy(locale, 'Esta consulta no aplica al contexto seleccionado.', 'This query does not apply to the selected context.')
                : copy(
                    locale,
                    'No hay datos para este contexto; no equivale a cero.',
                    'There is no data for this context; this is not zero.',
                  )}
            </p>
            {result.availability && result.availability !== 'COMPLETE' ? (
              <p className="small mb-2" data-cy="analyticsResultAvailability">
                {copy(locale, 'Disponibilidad del resultado', 'Result availability')}: {label(locale, result.availability)}
              </p>
            ) : null}
            {result.warnings?.map(warning => (
              <div
                className={compact ? 'border-start border-3 border-warning ps-2 mb-0' : 'alert alert-warning py-2 mb-0'}
                key={warning.code}
                data-cy={`analyticsWarning-${warning.code}`}
              >
                {warningText(warning.code, warning.message, locale)}
              </div>
            ))}
          </div>
        ) : null}
        {!loading && result?.rows.length ? (
          <>
            {!compact ? (
              <p className="small text-muted" data-cy="analyticsEffectiveContext">
                {copy(locale, 'Contexto efectivo', 'Effective context')}: {result.effectiveContext.from} — {result.effectiveContext.through}
              </p>
            ) : null}
            {drillTrail.length ? (
              <div className="alert alert-info py-2 d-flex justify-content-between gap-2 align-items-center">
                <span>{copy(locale, `Explorando: ${drillTrail.join(' › ')}`, `Exploring: ${drillTrail.join(' › ')}`)}</span>
                <Button type="button" size="sm" color="secondary" outline onClick={onDrillBack}>
                  {copy(locale, 'Volver', 'Back')}
                </Button>
              </div>
            ) : null}
            {result.availability && result.availability !== 'COMPLETE' ? (
              <div
                className={compact ? 'small border-start border-info border-3 ps-2 mb-2' : 'alert alert-info py-2'}
                data-cy="analyticsResultAvailability"
              >
                {copy(locale, 'Disponibilidad del resultado', 'Result availability')}: {label(locale, result.availability)}
              </div>
            ) : null}
            {result.warnings?.map(warning => (
              <div
                className={compact ? 'small border-start border-warning border-3 ps-2 mb-2' : 'alert alert-warning py-2'}
                key={warning.code}
                data-cy={`analyticsWarning-${warning.code}`}
              >
                {warningText(warning.code, warning.message, locale)}
              </div>
            ))}
            {incompatibility ? (
              <div className="alert alert-warning py-2" data-cy="analyticsVisualizationIncompatibility">
                {copy(
                  locale,
                  `La presentación “${visualizationTitle(locale, visualization)}” no es compatible con este resultado: ${incompatibility} Se muestra la tabla de resultados sin modificar la consulta guardada.`,
                  `The “${visualizationTitle(locale, visualization)}” presentation is not compatible with this result: ${incompatibility} The result table is shown without changing the saved query.`,
                )}
              </div>
            ) : null}
            {panels.length > 1 ? (
              <div className={compact ? 'small border-start border-info border-3 ps-2 mb-2' : 'alert alert-info py-2'}>
                {copy(
                  locale,
                  'Los importes se presentan por moneda y unidad; FINTRACK no los mezcla ni convierte.',
                  'Amounts are shown by currency and unit; FINTRACK never mixes or converts them.',
                )}
              </div>
            ) : null}
            {current === 'TABLE' ? (
              <AnalyticsResultTable definition={definition} rows={result.rows} locale={locale} onDrillDown={onDrillDown} />
            ) : null}
            {current !== 'TABLE'
              ? panels.map(panel => {
                  const chart = current !== 'KPI';
                  const chartState = chart ? chartRows(panel, seriesLimit, locale, result.effectiveContext, current) : null;
                  return (
                    <section
                      className={compact ? 'mb-3' : 'border rounded-2 p-3 mb-3'}
                      key={panelKey(panel.currency, panel.unit)}
                      data-cy={`analyticsCurrencyFacet-${panel.currency ?? 'NON_MONETARY'}-${panel.unit}`}
                    >
                      {!compact || current !== 'KPI' ? <h3 className="h6">{panelTitle(panel, locale)}</h3> : null}
                      {current === 'KPI' ? <Kpis panel={panel} locale={locale} compact={compact} /> : null}
                      {chart ? (
                        <SemanticChart
                          panel={panel}
                          visualization={current}
                          seriesLimit={seriesLimit}
                          locale={locale}
                          effectiveContext={result.effectiveContext}
                          horizontal={definition?.groupBy[0] === 'ACCOUNT_ID' && current === 'BAR'}
                        />
                      ) : null}
                      {chartState && chartState.hidden ? (
                        <p className="small text-muted mt-2">
                          {copy(
                            locale,
                            `${chartState.hidden} series no se muestran en la gráfica; la tabla conserva el resultado completo.`,
                            `${chartState.hidden} series are hidden from the chart; the table preserves the complete result.`,
                          )}
                        </p>
                      ) : null}
                    </section>
                  );
                })
              : null}
            {!compact && current !== 'TABLE' ? (
              <AnalyticsResultTable definition={definition} rows={result.rows} locale={locale} onDrillDown={onDrillDown} />
            ) : null}
            {compact && current !== 'TABLE' ? (
              <div className="border-top pt-2">
                <Button
                  type="button"
                  color="secondary"
                  outline
                  size="sm"
                  aria-expanded={detailsOpen}
                  aria-controls={detailsId}
                  onClick={() => setDetailsOpen(open => !open)}
                  data-cy="analyticsResultDetailsToggle"
                >
                  {detailsOpen
                    ? copy(locale, 'Ocultar tabla de resultados', 'Hide results table')
                    : copy(locale, 'Ver tabla de resultados', 'View results table')}
                </Button>
                <p className="small text-muted mt-2 mb-0">
                  {copy(
                    locale,
                    'La tabla conserva los resultados agrupados; no es una lista de transacciones individuales.',
                    'The table preserves grouped results; it is not a list of individual transactions.',
                  )}
                </p>
                {detailsOpen ? (
                  <div id={detailsId} className="mt-2">
                    <AnalyticsResultTable definition={definition} rows={result.rows} locale={locale} onDrillDown={onDrillDown} />
                  </div>
                ) : null}
              </div>
            ) : null}
          </>
        ) : null}
      </div>
    </section>
  );
};
