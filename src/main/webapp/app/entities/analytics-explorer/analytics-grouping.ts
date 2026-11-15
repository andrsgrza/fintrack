import { SemanticDatasetDefinition, SemanticQueryDefinition } from './analytics-explorer.model';
import { isCategoryField, isTemporalField } from './analytics-explorer-utils';

/**
 * Grouping is part of the semantic query.  This module deliberately has no React
 * state: Explorer, templates, and saved widgets all use the same ordered groupBy
 * contract and the same order reconciliation rules.
 */
export const groupingChoiceFor = (field: string | undefined): string => (field && isTemporalField(field) ? 'TIME' : (field ?? ''));
export const isTemporalGrouping = (field: string | undefined) => !!field && isTemporalField(field);

export const temporalOptionsFor = (dataset: SemanticDatasetDefinition) => dataset.groupings.filter(isTemporalField);

/**
 * A dashboard can temporarily execute a different compatible interval. The
 * returned definition is an in-memory execution copy; callers retain the saved
 * definition and discard this copy on reload.
 */
export const replaceTemporalGrouping = (definition: SemanticQueryDefinition, field: string): SemanticQueryDefinition => {
  if (!isTemporalField(field)) return definition;
  const position = definition.groupBy.findIndex(isTemporalField);
  return position < 0 ? definition : replaceGrouping(definition, position as 0 | 1, field);
};

export const categorySelectionFor = (definition: SemanticQueryDefinition) =>
  definition.predicates.find(predicate => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF')?.values[0] ?? '';

const unique = <T>(items: T[]) => items.filter((item, index) => items.indexOf(item) === index);

/**
 * Replaces one ordered grouping without ever leaving stale ORDER BY fields behind.
 * Projection ordering stays exactly as authored; grouping ordering follows the
 * semantic position that was replaced.  This is also used by category drill-down.
 */
export const replaceGrouping = (definition: SemanticQueryDefinition, position: 0 | 1, field: string): SemanticQueryDefinition => {
  const previous = [...definition.groupBy];
  const next = [...previous];
  if (field) next[position] = field;
  else next.splice(position, 1);

  const requested = next.filter(Boolean);
  // The API deliberately rejects a double temporal dimension.  The UI disables
  // it, and this guard keeps programmatic callers from creating an invalid AST.
  if (requested.filter(isTemporalField).length > 1) return definition;
  const normalized = unique(requested).slice(0, 2);

  const orderBy = definition.orderBy.flatMap(order => {
    if (!order.field) return [order];
    const previousPosition = previous.indexOf(order.field);
    if (previousPosition < 0) return normalized.includes(order.field) ? [order] : [];
    const replacement = normalized[previousPosition];
    return replacement ? [{ ...order, field: replacement, projectionAlias: null }] : [];
  });
  const seen = new Set<string>();
  return {
    ...definition,
    groupBy: normalized,
    orderBy: orderBy.filter(order => {
      const identity = order.field ? `field:${order.field}` : `projection:${order.projectionAlias ?? ''}`;
      if (seen.has(identity)) return false;
      seen.add(identity);
      return true;
    }),
  };
};

/** Reuses the canonical category-child grouping and replaces only its required scope predicate. */
export const groupCategoryChildren = (definition: SemanticQueryDefinition, position: 0 | 1, categoryId: string) => {
  const grouped = replaceGrouping(definition, position, 'CATEGORY_BRANCH_CHILD');
  if (grouped === definition || !categoryId) return grouped;
  return {
    ...grouped,
    predicates: [
      ...grouped.predicates.filter(predicate => !(predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF')),
      { field: 'CATEGORY_ID', operator: 'DESCENDANT_OF' as const, values: [categoryId] },
    ],
  };
};

/** Finds the only category grouping eligible for a drill-down. */
export const categoryGroupingIndex = (definition: SemanticQueryDefinition) => definition.groupBy.findIndex(isCategoryField);
