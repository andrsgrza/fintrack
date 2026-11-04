import React, { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button } from 'reactstrap';

import { ExplorerVisualization, SemanticAnalyticsResult, SemanticQueryDefinition } from './analytics-explorer.model';
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

type Row = SemanticAnalyticsResult['rows'][number];
type Value = Row['values'][number];
type Unit = Value['unit'];
export type ChartSeries = { key: string; label: string; color: string; rank: number };
export type ResultPanel = { currency: string | null; unit: Unit; rows: Row[]; values: Array<{ row: Row; value: Value }> };
export type ChartPoint = { x: string; xId: string; sortKey: number | null; [key: string]: string | number | boolean | null };
export type ChartRows = {
  data: ChartPoint[];
  series: ChartSeries[];
  hidden: number;
  hasPlottableValues: boolean;
  temporal: boolean;
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

export type VisualizationEligibility = { option: ExplorerVisualization; enabled: boolean; reason?: string };

/** Uses the draft definition before execution and the returned shape afterwards. Presentation never mutates the query AST. */
export const visualizationEligibility = (
  definition: SemanticQueryDefinition | null,
  result: SemanticAnalyticsResult | null,
  locale: string,
): VisualizationEligibility[] => {
  const fields = result?.rows.length ? (result.rows[0]?.dimensions.map(dimension => dimension.field) ?? []) : (definition?.groupBy ?? []);
  const temporal = isTemporalField(fields[0] ?? '');
  const tagGrouping = fields.includes('TAG_ID');
  const pointInTime = ['ACCOUNT_POSITION_SNAPSHOT', 'CREDIT_ACCOUNT_SNAPSHOT'].includes(definition?.dataset ?? '');
  const additive = !!definition?.projections.every(projection => projection.function !== 'AVG');
  const groupCount = fields.length;
  const oneProjection = definition?.projections.length === 1;
  const barReason = copy(locale, 'Necesita al menos un desglose.', 'Requires at least one breakdown.');
  return [
    {
      option: 'KPI',
      enabled: groupCount === 0,
      reason: copy(locale, 'Los indicadores muestran un total sin desglose.', 'Indicators show one total without a breakdown.'),
    },
    {
      option: 'LINE',
      enabled: temporal,
      reason: copy(
        locale,
        'Las líneas requieren un desglose temporal como primer eje.',
        'Lines require a temporal breakdown as the first axis.',
      ),
    },
    { option: 'BAR', enabled: groupCount > 0, reason: barReason },
    {
      option: 'GROUPED_BAR',
      enabled: groupCount > 1 || !oneProjection,
      reason: copy(locale, 'Requiere una comparación o más de un cálculo.', 'Requires a comparison or more than one calculation.'),
    },
    {
      option: 'STACKED_BAR',
      enabled: (groupCount > 1 || !oneProjection) && additive && !tagGrouping && !(pointInTime && temporal),
      reason: tagGrouping
        ? copy(
            locale,
            'Las etiquetas se solapan y no pueden apilarse como partes exclusivas.',
            'Tags overlap and cannot be stacked as exclusive parts.',
          )
        : pointInTime && temporal
          ? copy(
              locale,
              'Los saldos y la deuda histórica no se suman entre fechas.',
              'Historical balances and debt are not summed across dates.',
            )
          : copy(
              locale,
              'Sólo se apilan medidas aditivas con grupos comparables.',
              'Only additive measures with comparable groups can be stacked.',
            ),
    },
    {
      option: 'DONUT',
      enabled: groupCount === 1 && !temporal && oneProjection && additive && !tagGrouping,
      reason: tagGrouping
        ? copy(
            locale,
            'Las etiquetas se solapan; una dona sugeriría partes excluyentes.',
            'Tags overlap; a donut would imply exclusive parts.',
          )
        : copy(
            locale,
            'La dona requiere un cálculo aditivo y un único desglose no temporal.',
            'A donut requires one additive calculation and one non-temporal breakdown.',
          ),
    },
    { option: 'TABLE', enabled: true },
  ];
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
): ChartRows => {
  const valuesByX = new Map<string, ChartPoint>();
  const ranks = new Map<string, number>();
  panel.values.forEach(({ row, value }) => {
    const primary = row.dimensions[0];
    const xId = primary ? dimensionId(row, 0) : 'TOTAL';
    const x = primary ? String(dimensionLabel(row, 0, locale)) : copy(locale, 'Total', 'Total');
    const secondDimension = row.dimensions.length > 1 ? dimensionId(row, 1) : '';
    const secondLabel = row.dimensions.length > 1 ? String(dimensionLabel(row, 1, locale)) : '';
    const key = `${value.alias}::${secondDimension}`;
    const seriesLabel = row.dimensions.length > 1 ? `${value.alias} · ${secondLabel}` : value.alias;
    const item =
      valuesByX.get(xId) ?? ({ x, xId, sortKey: primary ? temporalSortKey(primary.field, primary.value) : null } satisfies ChartPoint);
    item[key] = plottableValue(value);
    item[`${key}::label`] = seriesLabel;
    item[`${key}::derived`] = value.derived === true;
    valuesByX.set(xId, item);
    ranks.set(key, (ranks.get(key) ?? 0) + Math.abs(plottableValue(value) ?? 0));
  });
  const series = [...ranks.entries()]
    .map(([key, rank]) => ({
      key,
      label: String([...valuesByX.values()].find(row => row[`${key}::label`])?.[`${key}::label`] ?? key),
      color: colorFor(key),
      rank,
    }))
    .sort((left, right) => right.rank - left.rank || left.key.localeCompare(right.key));
  const selected = limit === 'ALL' ? series : series.slice(0, limit);
  const selectedKeys = new Set(selected.map(seriesItem => seriesItem.key));
  const primaryField = panel.rows.find(row => row.dimensions[0])?.dimensions[0]?.field ?? '';
  const temporal = isTemporalField(primaryField);
  let missingPeriods = 0;
  const contextBuckets =
    temporal && effectiveContext ? temporalBucketsInContext(primaryField, effectiveContext.from, effectiveContext.through) : [];
  contextBuckets.forEach(bucket => {
    const xId = temporalBucketIdentity(primaryField, bucket.value);
    if (!valuesByX.has(xId)) {
      valuesByX.set(xId, {
        x: formatTemporalBucket(primaryField, bucket.value, locale),
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
    });
    return next;
  });
  return {
    data,
    series: selected,
    hidden: series.filter(seriesItem => !selectedKeys.has(seriesItem.key)).length,
    hasPlottableValues: data.some(row => selected.some(seriesItem => typeof row[seriesItem.key] === 'number')),
    temporal,
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

const ChartLegend = ({ series }: { series: ChartSeries[] }) => (
  <ul className="list-inline small mb-0" aria-label="Series">
    {series.map(seriesItem => (
      <li className="list-inline-item me-3" key={seriesItem.key}>
        <span aria-hidden="true" style={{ color: seriesItem.color }}>
          ●
        </span>{' '}
        {seriesItem.label}
      </li>
    ))}
  </ul>
);

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
  payload?: Array<{ dataKey?: string | number; name?: string; value?: number; payload?: ChartPoint }>;
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
        return (
          <div key={item.name}>
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
}: {
  panel: ResultPanel;
  visualization: ExplorerVisualization;
  seriesLimit: number | 'ALL';
  locale: string;
  effectiveContext: SemanticAnalyticsResult['effectiveContext'];
}) => {
  const chart = useMemo(() => chartRows(panel, seriesLimit, locale, effectiveContext), [panel, seriesLimit, locale, effectiveContext]);
  const scale = useMemo(() => chartAxisScale(panel.values.map(({ value }) => plottableValue(value))), [panel]);
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
    const data = chart.data.flatMap(row => (typeof row[series.key] === 'number' ? [{ name: String(row.x), value: row[series.key] }] : []));
    return (
      <div style={{ height: 340 }} role="img" aria-label={copy(locale, 'Gráfica de dona', 'Donut chart')}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart margin={{ top: 16, right: 24, bottom: 44, left: 24 }}>
            <Tooltip content={<FinancialTooltip currency={panel.currency} unit={panel.unit} locale={locale} />} />
            <Legend />
            <Pie
              data={data}
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
              {data.map((slice, index) => (
                <Cell key={slice.name} fill={palette[index % palette.length]} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
      </div>
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
  return (
    <>
      <div style={{ height: 390 }} role="img" aria-label={visualizationTitle(locale, visualization)} data-cy="analyticsSemanticChart">
        <ResponsiveContainer width="100%" height="100%">
          {bars ? (
            <BarChart data={chart.data} margin={{ top: 16, right: 24, bottom: 44, left: 0 }}>
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
              <Legend />
              {chart.series.map(series => (
                <Bar key={series.key} dataKey={series.key} name={series.label} fill={series.color} stackId={stackId} />
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
              <Legend />
              {chart.series.map(series => (
                <Line
                  key={series.key}
                  type="linear"
                  dataKey={series.key}
                  name={series.label}
                  stroke={series.color}
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                />
              ))}
            </LineChart>
          )}
        </ResponsiveContainer>
      </div>
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
    </>
  );
};

const Kpis = ({ panel, locale }: { panel: ResultPanel; locale: string }) => (
  <div className="row g-2">
    {panel.values.map(({ row, value }, index) => (
      <div className="col-12 col-sm-6 col-lg-3" key={`${value.alias}-${index}`}>
        <div className="bg-light rounded-2 p-3 h-100">
          <div className="small text-muted">{value.alias}</div>
          <div className="fs-5 fw-semibold">{display(value.value, value.unit, row.currencyCode, locale)}</div>
          <div className="small text-muted">{label(locale, value.availability)}</div>
        </div>
      </div>
    ))}
  </div>
);

const ResultTable = ({
  rows,
  locale,
  onDrillDown,
}: {
  rows: Row[];
  locale: string;
  onDrillDown: (categoryId: string, labelText: string) => void;
}) => (
  <div className="table-responsive mt-3" data-cy="analyticsResultTable">
    <table className="table table-sm mb-0">
      <thead>
        <tr>
          <th scope="col">{copy(locale, 'Grupo', 'Group')}</th>
          {rows[0]?.values.map(value => (
            <th scope="col" key={value.alias}>
              {value.alias}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => {
          const category = row.dimensions.find(
            dimension => isCategoryField(dimension.field) && !!dimension.metadata?.stableId && !dimension.metadata.synthetic,
          );
          return (
            <tr key={`${rowLabel(row, locale)}-${index}`}>
              <th scope="row">
                <span>{rowLabel(row, locale)}</span>
                {category ? (
                  <Button
                    type="button"
                    color="link"
                    size="sm"
                    className="p-0 ms-2"
                    onClick={() => onDrillDown(String(category.value), category.metadata.label)}
                  >
                    {copy(locale, 'Ver subcategorías', 'View subcategories')}
                  </Button>
                ) : null}
              </th>
              {row.values.map(value => (
                <td key={value.alias}>
                  {display(value.value, value.unit, row.currencyCode, locale)}
                  {value.derived ? (
                    <span className="small text-muted d-block">
                      {value.availability === 'NO_DATA'
                        ? copy(locale, 'Sin movimientos registrados; no hay promedio.', 'No registered movements; no average.')
                        : copy(locale, 'Sin movimientos registrados.', 'No registered movements.')}
                    </span>
                  ) : value.availability !== 'COMPLETE' ? (
                    <span className="small text-muted d-block">{label(locale, value.availability)}</span>
                  ) : null}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
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
}: Props) => {
  const options = visualizationEligibility(definition, result, locale);
  const current = options.find(option => option.option === visualization)?.enabled ? visualization : 'TABLE';
  const panels = result ? resultPanels(result) : [];
  return (
    <section className="card border shadow-sm" data-cy="analyticsResults" aria-live="polite">
      <div className="card-body p-3 p-md-4">
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
          <div className="border rounded-2 p-4 text-muted" data-cy="analyticsNoData">
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
              <div className="alert alert-warning py-2 mb-0" key={warning.code} data-cy={`analyticsWarning-${warning.code}`}>
                {warning.message}
              </div>
            ))}
          </div>
        ) : null}
        {!loading && result?.rows.length ? (
          <>
            <p className="small text-muted" data-cy="analyticsEffectiveContext">
              {copy(locale, 'Contexto efectivo', 'Effective context')}: {result.effectiveContext.from} — {result.effectiveContext.through}
            </p>
            {drillTrail.length ? (
              <div className="alert alert-info py-2 d-flex justify-content-between gap-2 align-items-center">
                <span>{copy(locale, `Explorando: ${drillTrail.join(' › ')}`, `Exploring: ${drillTrail.join(' › ')}`)}</span>
                <Button type="button" size="sm" color="secondary" outline onClick={onDrillBack}>
                  {copy(locale, 'Volver', 'Back')}
                </Button>
              </div>
            ) : null}
            {result.availability && result.availability !== 'COMPLETE' ? (
              <div className="alert alert-info py-2" data-cy="analyticsResultAvailability">
                {copy(locale, 'Disponibilidad del resultado', 'Result availability')}: {label(locale, result.availability)}
              </div>
            ) : null}
            {result.warnings?.map(warning => (
              <div className="alert alert-warning py-2" key={warning.code} data-cy={`analyticsWarning-${warning.code}`}>
                {warning.message}
              </div>
            ))}
            {panels.length > 1 ? (
              <div className="alert alert-info py-2">
                {copy(
                  locale,
                  'Los importes se presentan por moneda y unidad; FINTRACK no los mezcla ni convierte.',
                  'Amounts are shown by currency and unit; FINTRACK never mixes or converts them.',
                )}
              </div>
            ) : null}
            {panels.map(panel => {
              const chart = current !== 'TABLE' && current !== 'KPI';
              const chartState = chart ? chartRows(panel, seriesLimit, locale, result.effectiveContext) : null;
              return (
                <section
                  className="border rounded-2 p-3 mb-3"
                  key={panelKey(panel.currency, panel.unit)}
                  data-cy={`analyticsCurrencyFacet-${panel.currency ?? 'NON_MONETARY'}-${panel.unit}`}
                >
                  <h3 className="h6">{panelTitle(panel, locale)}</h3>
                  {current === 'KPI' ? <Kpis panel={panel} locale={locale} /> : null}
                  {chart ? (
                    <SemanticChart
                      panel={panel}
                      visualization={current}
                      seriesLimit={seriesLimit}
                      locale={locale}
                      effectiveContext={result.effectiveContext}
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
                  {chartState ? <ChartLegend series={chartState.series} /> : null}
                </section>
              );
            })}
            <ResultTable rows={result.rows} locale={locale} onDrillDown={onDrillDown} />
          </>
        ) : null}
      </div>
    </section>
  );
};
