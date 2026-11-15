import React, { useMemo, useState } from 'react';
import { Button } from 'reactstrap';

import { SemanticAnalyticsResult, SemanticQueryDefinition } from './analytics-explorer.model';
import { copy, formatTemporalBucket, isCategoryField, isTemporalField, label, temporalBucketIdentity } from './analytics-explorer-utils';

type Row = SemanticAnalyticsResult['rows'][number];
type Dimension = Row['dimensions'][number];
type Value = Row['values'][number];

type Props = {
  definition: SemanticQueryDefinition | null;
  rows: Row[];
  locale: string;
  onDrillDown: (categoryId: string, labelText: string) => void;
};

type TableMode = 'DETAILED' | 'PIVOT';
type DimensionEntry = { dimension: Dimension; id: string; label: string };
type PivotCell = { row: Row; value: Value };
type PivotModel = {
  fields: string[];
  measures: string[];
  rowDimensions: DimensionEntry[];
  columnDimensions: DimensionEntry[];
  cells: Map<string, PivotCell>;
  unavailableReason: string | null;
};

const monetaryFormatter = (value: number, currency: string | null, locale: string) => {
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

const displayValue = (value: Value, currency: string | null, locale: string) => {
  if (value.value === null) return '—';
  if (value.unit === 'MONEY') return monetaryFormatter(value.value, currency, locale);
  if (value.unit === 'PERCENT')
    return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(value.value / 100);
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value.value);
};

const presentationAlias = (alias: string, locale: string) => {
  const translations: Record<string, [string, string]> = {
    posicion_financiera: ['Posición financiera registrada', 'Registered financial position'],
    ingresos: ['Ingresos', 'Income'],
    gastos: ['Gastos', 'Expenses'],
    flujo_neto: ['Flujo neto', 'Net flow'],
    saldo_registrado: ['Saldo registrado', 'Recorded balance'],
    deuda_tarjeta: ['Deuda de tarjetas', 'Card debt'],
    limite_credito: ['Límite de crédito', 'Credit limit'],
    credito_disponible: ['Crédito disponible', 'Available credit'],
    utilizacion_credito: ['Utilización de crédito', 'Credit utilization'],
  };
  const translated = translations[alias.toLocaleLowerCase()];
  if (translated) return translated[locale.startsWith('es') ? 0 : 1];
  const humanized = alias.replace(/_/g, ' ').trim();
  return humanized ? `${humanized.charAt(0).toLocaleUpperCase(locale)}${humanized.slice(1)}` : alias;
};

const dimensionId = (dimension: Dimension) =>
  isTemporalField(dimension.field)
    ? temporalBucketIdentity(dimension.field, dimension.value)
    : (dimension.metadata?.stableId ?? `${dimension.field}:${String(dimension.value ?? '—')}`);

const dimensionLabel = (dimension: Dimension, locale: string) =>
  isTemporalField(dimension.field)
    ? formatTemporalBucket(dimension.field, dimension.value, locale)
    : (dimension.metadata?.label ?? String(dimension.value ?? '—'));

const availableCategory = (dimension: Dimension) =>
  isCategoryField(dimension.field) && !!dimension.metadata?.stableId && !dimension.metadata.synthetic && dimension.value !== null;

const DimensionValue = ({
  dimension,
  locale,
  onDrillDown,
}: {
  dimension: Dimension;
  locale: string;
  onDrillDown: Props['onDrillDown'];
}) => (
  <>
    <span>{dimensionLabel(dimension, locale)}</span>
    {availableCategory(dimension) ? (
      <Button
        type="button"
        color="link"
        size="sm"
        className="p-0 ms-2 align-baseline"
        onClick={() => onDrillDown(String(dimension.value), dimension.metadata?.label ?? dimensionLabel(dimension, locale))}
      >
        {copy(locale, 'Ver subcategorías', 'View subcategories')}
      </Button>
    ) : null}
  </>
);

const availabilityNote = (value: Value, locale: string) => {
  if (value.derived) {
    return value.availability === 'NO_DATA'
      ? copy(locale, 'Sin movimientos registrados; no hay promedio.', 'No registered movements; there is no average.')
      : copy(locale, 'Sin movimientos registrados.', 'No registered movements.');
  }
  return value.availability === 'COMPLETE' ? null : label(locale, value.availability);
};

const ValueCell = ({ value, currency, locale }: { value: Value; currency: string | null; locale: string }) => {
  const note = availabilityNote(value, locale);
  return (
    <>
      {displayValue(value, currency, locale)}
      {note ? <span className="small text-muted d-block">{note}</span> : null}
    </>
  );
};

/** Result rows are the execution authority. A draft may be stale while its last executed result remains visible. */
const fieldsFor = (definition: SemanticQueryDefinition | null, rows: Row[]) =>
  rows[0]?.dimensions.map(dimension => dimension.field) ?? definition?.groupBy ?? [];

const measuresFor = (rows: Row[]) => {
  const aliases = new Set<string>();
  rows.forEach(row => row.values.forEach(value => aliases.add(value.alias)));
  return [...aliases];
};

const keyFor = (...parts: string[]) => parts.join('\u0001');

/** Builds a display-only matrix. It never combines, sums, or otherwise recalculates semantic rows. */
export const pivotModelFor = (definition: SemanticQueryDefinition | null, rows: Row[], locale: string): PivotModel => {
  const fields = fieldsFor(definition, rows);
  const measures = measuresFor(rows);
  const model: PivotModel = {
    fields,
    measures,
    rowDimensions: [],
    columnDimensions: [],
    cells: new Map(),
    unavailableReason:
      fields.length === 2
        ? null
        : copy(locale, 'La tabla dinámica requiere exactamente dos desgloses.', 'The pivot table requires exactly two breakdowns.'),
  };
  if (model.unavailableReason) return model;

  const rowById = new Map<string, DimensionEntry>();
  const columnById = new Map<string, DimensionEntry>();
  for (const row of rows) {
    const first = row.dimensions[0];
    const second = row.dimensions[1];
    if (!first || !second || first.field !== fields[0] || second.field !== fields[1]) {
      model.unavailableReason = copy(
        locale,
        'El resultado no conserva las dos dimensiones solicitadas; se muestra en detalle.',
        'The result does not retain the two requested dimensions, so it is shown in detail.',
      );
      return model;
    }
    const rowId = dimensionId(first);
    const columnId = dimensionId(second);
    rowById.set(rowId, { dimension: first, id: rowId, label: dimensionLabel(first, locale) });
    columnById.set(columnId, { dimension: second, id: columnId, label: dimensionLabel(second, locale) });
    for (const value of row.values) {
      const cellKey = keyFor(rowId, columnId, value.alias);
      if (model.cells.has(cellKey)) {
        model.unavailableReason = copy(
          locale,
          'Varias filas semánticas ocuparían la misma celda dinámica. Para no mezclar importes, se muestra el detalle.',
          'Multiple semantic rows would occupy the same pivot cell. To avoid mixing amounts, the detailed table is shown.',
        );
        return model;
      }
      model.cells.set(cellKey, { row, value });
    }
  }
  model.rowDimensions = [...rowById.values()];
  model.columnDimensions = [...columnById.values()];
  return model;
};

const DetailedTable = ({ definition, rows, locale, onDrillDown }: Props) => {
  const fields = fieldsFor(definition, rows);
  const measures = measuresFor(rows);
  return (
    <table className="table table-sm mb-0" data-cy="analyticsDetailedResultTable">
      <thead>
        <tr>
          {fields.map(field => (
            <th scope="col" key={field}>
              {label(locale, field)}
            </th>
          ))}
          {measures.map(alias => (
            <th scope="col" key={alias} className="text-end">
              {presentationAlias(alias, locale)}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={keyFor(...row.dimensions.map(dimensionId), row.currencyCode ?? 'NON_MONETARY', String(index))}>
            {fields.map((field, dimensionIndex) => {
              const dimension = row.dimensions[dimensionIndex];
              const content = dimension ? <DimensionValue dimension={dimension} locale={locale} onDrillDown={onDrillDown} /> : '—';
              return dimensionIndex === 0 ? (
                <th scope="row" key={`${field}-${dimensionIndex}`}>
                  {content}
                </th>
              ) : (
                <td key={`${field}-${dimensionIndex}`}>{content}</td>
              );
            })}
            {measures.map(alias => {
              const value = row.values.find(candidate => candidate.alias === alias);
              return (
                <td className="text-end" key={alias}>
                  {value ? <ValueCell value={value} currency={row.currencyCode} locale={locale} /> : '—'}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
};

const PivotTable = ({ model, locale, onDrillDown }: { model: PivotModel; locale: string; onDrillDown: Props['onDrillDown'] }) => {
  const firstField = model.fields[0];
  const secondField = model.fields[1];
  return (
    <table className="table table-sm mb-0" data-cy="analyticsPivotResultTable">
      <thead>
        <tr>
          <th scope="col" rowSpan={2}>
            {label(locale, firstField)}
          </th>
          {model.columnDimensions.map(column => (
            <th scope="colgroup" colSpan={model.measures.length} key={column.id} className="text-center">
              <DimensionValue dimension={column.dimension} locale={locale} onDrillDown={onDrillDown} />
            </th>
          ))}
        </tr>
        <tr>
          {model.columnDimensions.flatMap(column =>
            model.measures.map(alias => (
              <th scope="col" key={keyFor(column.id, alias)} className="text-end">
                {presentationAlias(alias, locale)}
              </th>
            )),
          )}
        </tr>
      </thead>
      <tbody>
        {model.rowDimensions.map(rowDimension => (
          <tr key={rowDimension.id}>
            <th scope="row">
              <DimensionValue dimension={rowDimension.dimension} locale={locale} onDrillDown={onDrillDown} />
            </th>
            {model.columnDimensions.flatMap(column =>
              model.measures.map(alias => {
                const cell = model.cells.get(keyFor(rowDimension.id, column.id, alias));
                return (
                  <td className="text-end" key={keyFor(column.id, alias)}>
                    {cell ? (
                      <ValueCell value={cell.value} currency={cell.row.currencyCode} locale={locale} />
                    ) : (
                      <span className="text-muted" aria-label={copy(locale, 'Sin resultado', 'No result')}>
                        —
                      </span>
                    )}
                  </td>
                );
              }),
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
};

/** The one shared semantic table used by Explorer, dashboard TABLE widgets, and expanded chart/KPI details. */
export const AnalyticsResultTable = ({ definition, rows, locale, onDrillDown }: Props) => {
  const [mode, setMode] = useState<TableMode>('DETAILED');
  const pivot = useMemo(() => pivotModelFor(definition, rows, locale), [definition, rows, locale]);
  const pivotAvailable = !pivot.unavailableReason;
  const selectedMode = mode === 'PIVOT' && pivotAvailable ? 'PIVOT' : 'DETAILED';

  return (
    <div className="mt-3" data-cy="analyticsResultTable">
      {pivot.fields.length === 2 ? (
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
          <div className="btn-group btn-group-sm" role="group" aria-label={copy(locale, 'Presentación de tabla', 'Table presentation')}>
            <Button
              type="button"
              color={selectedMode === 'DETAILED' ? 'primary' : 'secondary'}
              outline={selectedMode !== 'DETAILED'}
              onClick={() => setMode('DETAILED')}
              data-cy="analyticsTableModeDetailed"
              aria-pressed={selectedMode === 'DETAILED'}
            >
              {copy(locale, 'Detallada', 'Detailed')}
            </Button>
            <Button
              type="button"
              color={selectedMode === 'PIVOT' ? 'primary' : 'secondary'}
              outline={selectedMode !== 'PIVOT'}
              onClick={() => setMode('PIVOT')}
              disabled={!pivotAvailable}
              title={pivot.unavailableReason ?? undefined}
              data-cy="analyticsTableModePivot"
              aria-pressed={selectedMode === 'PIVOT'}
            >
              {copy(locale, 'Dinámica', 'Pivot')}
            </Button>
          </div>
          {pivot.unavailableReason ? (
            <span className="small text-muted" data-cy="analyticsPivotUnavailable">
              {pivot.unavailableReason}
            </span>
          ) : null}
        </div>
      ) : null}
      <div className="table-responsive" data-cy="analyticsResultTableScroll">
        {selectedMode === 'PIVOT' ? (
          <PivotTable model={pivot} locale={locale} onDrillDown={onDrillDown} />
        ) : (
          <DetailedTable definition={definition} rows={rows} locale={locale} onDrillDown={onDrillDown} />
        )}
      </div>
      {selectedMode === 'PIVOT' ? (
        <p className="small text-muted mt-2 mb-0">
          {copy(
            locale,
            `Filas: ${label(locale, firstFieldOrEmpty(pivot))}. Columnas: ${label(locale, secondFieldOrEmpty(pivot))}. La tabla no recalcula resultados.`,
            `Rows: ${label(locale, firstFieldOrEmpty(pivot))}. Columns: ${label(locale, secondFieldOrEmpty(pivot))}. The table does not recalculate results.`,
          )}
        </p>
      ) : null}
    </div>
  );
};

const firstFieldOrEmpty = (model: PivotModel) => model.fields[0] ?? '';
const secondFieldOrEmpty = (model: PivotModel) => model.fields[1] ?? '';
