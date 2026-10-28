package com.fintrack.app.service.analytics;

import java.util.List;
import java.util.regex.Pattern;

/** The only text formatter for a normalized canonical Analytics definition. */
final class FinancialAnalyticsDslFormatter {

    private static final Pattern BARE_LITERAL = Pattern.compile("[A-Z0-9_][A-Z0-9_-]*");

    String format(QueryDefinition definition) {
        StringBuilder output = new StringBuilder("SELECT ").append(String.join(", ", definition.measures()));
        if (!definition.groupings().isEmpty()) {
            output.append(" BY ").append(String.join(", ", definition.groupings()));
        }
        if (!definition.analyticalPredicates().isEmpty()) {
            output.append(" WHERE ");
            for (int index = 0; index < definition.analyticalPredicates().size(); index++) {
                if (index > 0) {
                    output.append(" AND ");
                }
                appendPredicate(output, definition.analyticalPredicates().get(index));
            }
        }
        output.append(" USING ").append(definition.dateBasis());
        if (definition.sort() != null) {
            output.append(" ORDER BY ").append(definition.sort().groupingId()).append(' ').append(definition.sort().direction());
        }
        output.append(" LIMIT ").append(definition.limit());
        return output.toString();
    }

    private void appendPredicate(StringBuilder output, AnalyticalPredicate predicate) {
        output.append(predicate.attributeId()).append(' ').append(predicate.operatorId()).append(' ');
        if (FinancialSemanticCatalog.IN.equals(predicate.operatorId())) {
            output.append('(').append(String.join(", ", predicate.values().stream().map(this::literal).toList())).append(')');
        } else if (FinancialSemanticCatalog.BETWEEN.equals(predicate.operatorId())) {
            output.append(literal(predicate.values().get(0))).append(" AND ").append(literal(predicate.values().get(1)));
        } else {
            output.append(literal(predicate.values().get(0)));
        }
    }

    private String literal(String value) {
        return BARE_LITERAL.matcher(value).matches() ? value : "'" + value.replace("'", "''") + "'";
    }
}
