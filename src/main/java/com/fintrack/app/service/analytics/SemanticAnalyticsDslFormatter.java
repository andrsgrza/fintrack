package com.fintrack.app.service.analytics;

/** The sole canonical text representation of a normalized {@link SemanticQueryDefinition}. */
final class SemanticAnalyticsDslFormatter {

    String format(SemanticQueryDefinition definition) {
        StringBuilder output = new StringBuilder("SELECT ");
        for (int index = 0; index < definition.projections().size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            SemanticAggregateProjection projection = definition.projections().get(index);
            output.append(projection.function().name()).append('(').append(projection.field().name());
            if (!projection.conditions().isEmpty()) {
                output.append(" FILTER WHERE ");
                appendPredicates(output, projection.conditions());
            }
            output.append(") AS ").append(projection.alias());
        }
        output.append(" FROM ").append(definition.dataset().name());
        output.append(" TIME BINDING ").append(definition.timeBinding().name());
        if (!definition.predicates().isEmpty()) {
            output.append(" WHERE ");
            appendPredicates(output, definition.predicates());
        }
        appendFields(output, " GROUP BY ", definition.groupBy());
        if (!definition.orderBy().isEmpty()) {
            output.append(" ORDER BY ");
            for (int index = 0; index < definition.orderBy().size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                SemanticOrder order = definition.orderBy().get(index);
                output
                    .append(order.field() == null ? order.projectionAlias() : order.field().name())
                    .append(' ')
                    .append(order.direction().name());
            }
        }
        return output.append(" LIMIT ").append(definition.limit()).toString();
    }

    private void appendFields(StringBuilder output, String clause, java.util.List<SemanticField> fields) {
        if (fields.isEmpty()) {
            return;
        }
        output.append(clause);
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append(fields.get(index).name());
        }
    }

    private void appendPredicate(StringBuilder output, SemanticPredicate predicate) {
        output.append(predicate.field().name()).append(' ').append(predicate.operator().name());
        if (
            predicate.operator() == SemanticPredicateOperator.IN ||
            predicate.operator() == SemanticPredicateOperator.DESCENDANT_OF_ANY ||
            predicate.operator() == SemanticPredicateOperator.HAS_ANY ||
            predicate.operator() == SemanticPredicateOperator.HAS_ALL
        ) {
            output.append(" (");
            for (int index = 0; index < predicate.values().size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                output.append(literal(predicate.values().get(index)));
            }
            output.append(')');
        } else if (
            predicate.operator() != SemanticPredicateOperator.IS_UNCATEGORIZED && predicate.operator() != SemanticPredicateOperator.HAS_NONE
        ) {
            output.append(' ').append(literal(predicate.values().get(0)));
        }
    }

    private void appendPredicates(StringBuilder output, java.util.List<SemanticPredicate> predicates) {
        for (int index = 0; index < predicates.size(); index++) {
            if (index > 0) output.append(" AND ");
            appendPredicate(output, predicates.get(index));
        }
    }

    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
