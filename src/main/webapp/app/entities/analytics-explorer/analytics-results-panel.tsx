import React from 'react';
import { Button, ButtonGroup } from 'reactstrap';

import { ExplorerVisualization, SemanticAnalyticsResult } from './analytics-explorer.model';
import { copy, label, rowLabel, visualizationOptions } from './analytics-explorer-utils';

type Props = {
  result: SemanticAnalyticsResult | null;
  visualization: ExplorerVisualization;
  locale: string;
  loading: boolean;
  stale: boolean;
  onVisualizationChange: (visualization: ExplorerVisualization) => void;
};

const display = (value: number | null, unit: 'COUNT' | 'MONEY', currency: string | null, locale: string) => {
  if (value === null) return '—';
  if (unit === 'MONEY') return new Intl.NumberFormat(locale, { style: 'currency', currency: currency ?? 'MXN' }).format(value);
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value);
};

/** Presentation only: every financial value, state, and currency facet arrives from the server. */
export const AnalyticsResultsPanel = ({ result, visualization, locale, loading, stale, onVisualizationChange }: Props) => {
  const options = visualizationOptions(result);
  const chosen = options.includes(visualization) ? visualization : options[0];
  return (
    <section data-cy="analyticsResults" aria-live="polite">
      <div className="d-flex flex-wrap justify-content-between gap-2 mb-3">
        <div>
          <h3 className="h6 mb-1">{copy(locale, 'Resultados', 'Results')}</h3>
          <p className="small text-muted mb-0">
            {copy(locale, 'Sólo se muestran cálculos devueltos por el servidor.', 'Only server-returned calculations are displayed.')}
          </p>
        </div>
        {result ? (
          <ButtonGroup aria-label={copy(locale, 'Visualización', 'Visualization')}>
            {options.map(option => (
              <Button
                key={option}
                type="button"
                size="sm"
                color={chosen === option ? 'primary' : 'secondary'}
                outline={chosen !== option}
                onClick={() => onVisualizationChange(option)}
              >
                {option}
              </Button>
            ))}
          </ButtonGroup>
        ) : null}
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
          {copy(locale, 'No hay datos para este contexto; no equivale a cero.', 'There is no data for this context; this is not zero.')}
        </div>
      ) : null}
      {!loading && result?.rows.length ? <ResultBody result={result} visualization={chosen} locale={locale} /> : null}
    </section>
  );
};

const ResultBody = ({
  result,
  visualization,
  locale,
}: {
  result: SemanticAnalyticsResult;
  visualization: ExplorerVisualization;
  locale: string;
}) => {
  const facets = new Map<string, SemanticAnalyticsResult['rows']>();
  result.rows.forEach(row => {
    const key = row.currencyCode ?? 'NON_MONETARY';
    facets.set(key, [...(facets.get(key) ?? []), row]);
  });
  return (
    <>
      <p className="small text-muted" data-cy="analyticsEffectiveContext">
        {copy(locale, 'Contexto efectivo', 'Effective context')}: {result.effectiveContext.from} — {result.effectiveContext.through}
      </p>
      {result.warnings?.map(warning => (
        <div className="alert alert-warning py-2" key={warning.code} data-cy={`analyticsWarning-${warning.code}`}>
          {warning.message}
        </div>
      ))}
      {facets.size > 1 ? (
        <div className="alert alert-info py-2">
          {copy(
            locale,
            'Los importes se presentan por moneda; FINTRACK no mezcla ni convierte monedas.',
            'Amounts are shown by currency; FINTRACK does not mix or convert currencies.',
          )}
        </div>
      ) : null}
      {[...facets.entries()].map(([currency, rows]) => (
        <section className="border rounded-2 p-3 mb-3" key={currency} data-cy={`analyticsCurrencyFacet-${currency}`}>
          {currency !== 'NON_MONETARY' ? <h4 className="h6">{currency}</h4> : null}
          {visualization === 'KPI' ? <Kpis rows={rows} locale={locale} /> : null}
          {visualization === 'TABLE' ? <Table rows={rows} locale={locale} /> : null}
          {visualization === 'BAR' || visualization === 'DONUT' || visualization === 'LINE' ? (
            <>
              <Bars rows={rows} locale={locale} />
              <Table rows={rows} locale={locale} />
            </>
          ) : null}
        </section>
      ))}
    </>
  );
};

const Kpis = ({ rows, locale }: { rows: SemanticAnalyticsResult['rows']; locale: string }) => (
  <div className="row g-2">
    {rows.flatMap(row =>
      row.values.map(value => (
        <div className="col-12 col-sm-6 col-lg-3" key={`${rowLabel(row, locale)}-${value.alias}`}>
          <div className="bg-light rounded-2 p-3 h-100">
            <div className="small text-muted">{value.alias}</div>
            <div className="fs-5 fw-semibold">{display(value.value, value.unit, row.currencyCode, locale)}</div>
            <div className="small text-muted">{label(locale, value.availability)}</div>
          </div>
        </div>
      )),
    )}
  </div>
);

const Table = ({ rows, locale }: { rows: SemanticAnalyticsResult['rows']; locale: string }) => (
  <div className="table-responsive mt-3">
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
        {rows.map((row, index) => (
          <tr key={`${rowLabel(row, locale)}-${index}`}>
            <th scope="row">{rowLabel(row, locale)}</th>
            {row.values.map(value => (
              <td key={value.alias}>
                {display(value.value, value.unit, row.currencyCode, locale)}{' '}
                {value.availability !== 'COMPLETE' ? <span className="small text-muted">{label(locale, value.availability)}</span> : null}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const Bars = ({ rows, locale }: { rows: SemanticAnalyticsResult['rows']; locale: string }) => {
  const max = Math.max(1, ...rows.flatMap(row => row.values.map(value => Math.abs(value.value ?? 0))));
  return (
    <div className="d-flex flex-column gap-2">
      {rows.flatMap((row, index) =>
        row.values.map(value => (
          <div key={`${rowLabel(row, locale)}-${value.alias}-${index}`}>
            <div className="small d-flex justify-content-between">
              <span>
                {rowLabel(row, locale)} · {value.alias}
              </span>
              <span>{display(value.value, value.unit, row.currencyCode, locale)}</span>
            </div>
            <div className="progress" role="img" aria-label={`${rowLabel(row, locale)} ${value.alias}`}>
              <div className="progress-bar" style={{ width: `${Math.min(100, (Math.abs(value.value ?? 0) / max) * 100)}%` }} />
            </div>
          </div>
        )),
      )}
    </div>
  );
};
