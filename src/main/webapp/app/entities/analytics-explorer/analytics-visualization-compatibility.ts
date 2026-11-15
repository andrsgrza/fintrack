import { ExplorerVisualization, SemanticAnalyticsResult, SemanticQueryDefinition } from './analytics-explorer.model';
import { copy, isTemporalField } from './analytics-explorer-utils';

export type VisualizationEligibility = { option: ExplorerVisualization; enabled: boolean; reason?: string };

const pointInTimeDatasets = new Set(['FINANCIAL_POSITION_SNAPSHOT', 'ACCOUNT_POSITION_SNAPSHOT', 'CREDIT_ACCOUNT_SNAPSHOT']);

const hasTagGrouping = (definition: SemanticQueryDefinition | null) => definition?.groupBy.includes('TAG_ID') ?? false;

const hasOnlyAdditiveCalculations = (definition: SemanticQueryDefinition | null) =>
  definition?.projections.every(projection => projection.function !== 'AVG') ?? false;

const isPercentCalculation = (definition: SemanticQueryDefinition | null) =>
  definition?.projections.some(projection => projection.field === 'CREDIT_UTILIZATION_PERCENT') ?? false;

/**
 * The presentation contract is derived solely from the canonical query shape. It intentionally does not know a
 * dashboard, widget, metric name, or financial dataset implementation detail beyond point-in-time additivity.
 */
export const visualizationEligibility = (
  definition: SemanticQueryDefinition | null,
  _result: SemanticAnalyticsResult | null,
  locale: string,
): VisualizationEligibility[] => {
  const groupBy = definition?.groupBy ?? [];
  const temporal = groupBy.some(isTemporalField);
  const tagGrouping = hasTagGrouping(definition);
  const pointInTime = definition ? pointInTimeDatasets.has(definition.dataset) : false;
  const additive = hasOnlyAdditiveCalculations(definition);
  const oneProjection = definition?.projections.length === 1;
  const groupCount = groupBy.length;
  const comparableGroups = groupCount > 1 || !oneProjection;
  const barReason = copy(locale, 'Necesita al menos un desglose.', 'Requires at least one breakdown.');
  const donutReason = tagGrouping
    ? copy(locale, 'Las etiquetas se solapan; una dona sugeriría partes excluyentes.', 'Tags overlap; a donut would imply exclusive parts.')
    : isPercentCalculation(definition)
      ? copy(
          locale,
          'Una dona no representa porcentajes como partes de un total.',
          'A donut cannot represent percentages as parts of a total.',
        )
      : copy(
          locale,
          'La dona requiere un cálculo aditivo, un único desglose no temporal y un único resultado.',
          'A donut requires one additive calculation, one non-temporal breakdown, and one result.',
        );

  return [
    {
      option: 'KPI',
      enabled: groupCount === 0,
      reason: copy(locale, 'Los indicadores muestran un total sin desglose.', 'Indicators show one total without a breakdown.'),
    },
    {
      option: 'LINE',
      enabled: temporal,
      reason: copy(locale, 'Las líneas requieren un desglose temporal.', 'Lines require a temporal breakdown.'),
    },
    { option: 'BAR', enabled: groupCount > 0, reason: barReason },
    {
      option: 'GROUPED_BAR',
      enabled: comparableGroups,
      reason: copy(locale, 'Requiere una comparación o más de un cálculo.', 'Requires a comparison or more than one calculation.'),
    },
    {
      option: 'STACKED_BAR',
      enabled: comparableGroups && additive && !tagGrouping && !(pointInTime && temporal),
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
      enabled: groupCount === 1 && !temporal && oneProjection && additive && !tagGrouping && !isPercentCalculation(definition),
      reason: donutReason,
    },
    { option: 'TABLE', enabled: true },
  ];
};

/** Runtime data can add one restriction that is not knowable from the AST: a donut must not hide negative values. */
export const visualizationResultIncompatibility = (
  visualization: ExplorerVisualization,
  result: SemanticAnalyticsResult | null,
  locale: string,
): string | null => {
  if (
    visualization === 'DONUT' &&
    result?.rows.some(row =>
      row.values.some(
        value => value.value !== null && value.value < 0 && !['NO_DATA', 'UNAVAILABLE', 'NOT_APPLICABLE'].includes(value.availability),
      ),
    )
  ) {
    return copy(
      locale,
      'Una dona no representa importes negativos sin ocultar su significado financiero.',
      'A donut cannot represent negative amounts without hiding their financial meaning.',
    );
  }
  return null;
};
