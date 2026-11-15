package com.fintrack.app.service.analytics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the restricted semantic language straight into {@link SemanticQueryDefinition}. The catalog remains the
 * authority for whether an enum identifier is available in a particular dataset; this parser only owns syntax.
 */
final class SemanticAnalyticsDslParser {

    private final SemanticAnalyticsDslLexer lexer = new SemanticAnalyticsDslLexer();
    private final SemanticAnalyticsCatalogService catalog;

    SemanticAnalyticsDslParser(SemanticAnalyticsCatalogService catalog) {
        this.catalog = catalog;
    }

    ParsedDefinition parse(String source) {
        return new Cursor(lexer.tokenize(source)).parse();
    }

    record ParsedDefinition(SemanticQueryDefinition definition, SourceMap sourceMap) {}

    static final class SourceMap {

        private final Map<String, SemanticAnalyticsDslToken> tokenByPath = new HashMap<>();
        private SemanticAnalyticsDslToken fallback;

        void record(String path, SemanticAnalyticsDslToken token) {
            tokenByPath.putIfAbsent(path, token);
            fallback = fallback == null ? token : fallback;
        }

        AnalyticsDiagnostic withSource(AnalyticsDiagnostic diagnostic) {
            SemanticAnalyticsDslToken token = tokenFor(diagnostic.field());
            return token == null ? diagnostic : diagnostic.atSource(token.line(), token.column(), token.span(), token.text());
        }

        private SemanticAnalyticsDslToken tokenFor(String path) {
            if (path == null) {
                return fallback;
            }
            SemanticAnalyticsDslToken exact = tokenByPath.get(path);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, SemanticAnalyticsDslToken> entry : tokenByPath.entrySet()) {
                if (path.startsWith(entry.getKey()) || entry.getKey().startsWith(path)) {
                    return entry.getValue();
                }
            }
            return fallback;
        }
    }

    private final class Cursor {

        private final List<SemanticAnalyticsDslToken> tokens;
        private final SourceMap sourceMap = new SourceMap();
        private int index;

        private Cursor(List<SemanticAnalyticsDslToken> tokens) {
            this.tokens = tokens;
            sourceMap.record("text", tokens.get(0));
        }

        private ParsedDefinition parse() {
            expectKeyword("SELECT", "A semantic query must begin with SELECT.");
            List<SemanticAggregateProjection> projections = parseProjections();
            expectKeyword("FROM", "SELECT projections must be followed by FROM.");
            SemanticAnalyticsDslToken datasetToken = identifier("A semantic dataset is required after FROM.");
            SemanticDataset dataset = enumeration(
                datasetToken,
                SemanticDataset.class,
                AnalyticsDiagnosticCode.UNKNOWN_DATASET,
                "Unknown semantic dataset."
            );
            sourceMap.record("definition.dataset", datasetToken);

            SemanticField timeBinding = catalog.timeBinding(dataset);
            if (peekKeyword("TIME")) {
                SemanticAnalyticsDslToken time = current();
                advance();
                expectKeyword("BINDING", "TIME must be followed by BINDING.");
                SemanticAnalyticsDslToken fieldToken = identifier("TIME BINDING requires a semantic field.");
                timeBinding = enumeration(
                    fieldToken,
                    SemanticField.class,
                    AnalyticsDiagnosticCode.UNKNOWN_FIELD,
                    "Unknown semantic field."
                );
                sourceMap.record("definition.timeBinding", fieldToken);
                sourceMap.record("definition.timeBinding.keyword", time);
            }

            List<SemanticPredicate> predicates = List.of();
            if (peekKeyword("WHERE")) {
                SemanticAnalyticsDslToken clause = current();
                advance();
                sourceMap.record("definition.predicates", clause);
                predicates = parsePredicates("definition.predicates", false);
            }

            List<SemanticField> groupBy = List.of();
            if (peekKeyword("GROUP")) {
                advance();
                expectKeyword("BY", "GROUP must be followed by BY.");
                groupBy = parseFields("definition.groupBy");
            }

            List<SemanticOrder> orderBy = List.of();
            if (peekKeyword("ORDER")) {
                advance();
                expectKeyword("BY", "ORDER must be followed by BY.");
                orderBy = parseOrders(projections);
            }

            Integer limit = null;
            if (peekKeyword("LIMIT")) {
                advance();
                SemanticAnalyticsDslToken token = current();
                if (!peek(SemanticAnalyticsDslToken.Type.NUMBER)) {
                    throw error(AnalyticsDiagnosticCode.INVALID_LIMIT, token, "LIMIT must be a positive whole number.");
                }
                advance();
                sourceMap.record("definition.limit", token);
                try {
                    limit = Integer.valueOf(token.text());
                } catch (NumberFormatException exception) {
                    throw error(AnalyticsDiagnosticCode.INVALID_LIMIT, token, "LIMIT must be a supported whole number.");
                }
            }

            if (!peek(SemanticAnalyticsDslToken.Type.END)) {
                if (clauseKeyword(current())) {
                    throw error(
                        AnalyticsDiagnosticCode.DUPLICATE_CLAUSE,
                        current(),
                        "Each semantic clause may appear only once and in canonical order."
                    );
                }
                throw error(AnalyticsDiagnosticCode.TRAILING_GARBAGE, current(), "Unexpected text after the semantic query.");
            }
            if (timeBinding == null) {
                throw error(AnalyticsDiagnosticCode.UNKNOWN_FIELD, datasetToken, "The selected dataset has no registered time binding.");
            }
            return new ParsedDefinition(
                new SemanticQueryDefinition(
                    SemanticAnalyticsSchema.VERSION,
                    SemanticAnalyticsSchema.SEMANTIC_MODEL,
                    dataset,
                    timeBinding,
                    projections,
                    predicates,
                    groupBy,
                    orderBy,
                    limit
                ),
                sourceMap
            );
        }

        private List<SemanticAggregateProjection> parseProjections() {
            List<SemanticAggregateProjection> projections = new ArrayList<>();
            do {
                int projectionIndex = projections.size();
                SemanticAnalyticsDslToken functionToken = identifier("SELECT requires an aggregate function.");
                SemanticAggregateFunction function = enumeration(
                    functionToken,
                    SemanticAggregateFunction.class,
                    AnalyticsDiagnosticCode.UNKNOWN_AGGREGATION,
                    "Unknown aggregate function."
                );
                expect(SemanticAnalyticsDslToken.Type.LEFT_PAREN, "An aggregate function requires an opening parenthesis.");
                SemanticAnalyticsDslToken fieldToken = identifier("An aggregate field is required.");
                SemanticField field = enumeration(
                    fieldToken,
                    SemanticField.class,
                    AnalyticsDiagnosticCode.UNKNOWN_FIELD,
                    "Unknown semantic field."
                );
                List<SemanticPredicate> conditions = List.of();
                if (peekKeyword("FILTER")) {
                    advance();
                    expectKeyword("WHERE", "FILTER must be followed by WHERE.");
                    conditions = parsePredicates("definition.projections[" + projectionIndex + "].conditions", true);
                }
                expect(SemanticAnalyticsDslToken.Type.RIGHT_PAREN, "An aggregate function requires a closing parenthesis.");
                expectKeyword("AS", "Every aggregate projection requires AS and a stable alias.");
                SemanticAnalyticsDslToken alias = identifier("A projection alias is required after AS.");
                sourceMap.record("definition.projections[" + projectionIndex + "]", functionToken);
                sourceMap.record("definition.projections[" + projectionIndex + "].field", fieldToken);
                sourceMap.record("definition.projections[" + projectionIndex + "].alias", alias);
                projections.add(new SemanticAggregateProjection(alias.text(), function, field, conditions));
                if (!peek(SemanticAnalyticsDslToken.Type.COMMA)) {
                    break;
                }
                advance();
            } while (true);
            return List.copyOf(projections);
        }

        private List<SemanticPredicate> parsePredicates(String path, boolean stopAtRightParen) {
            List<SemanticPredicate> predicates = new ArrayList<>();
            do {
                int predicateIndex = predicates.size();
                SemanticAnalyticsDslToken fieldToken = identifier("A predicate field is required.");
                SemanticField field = enumeration(
                    fieldToken,
                    SemanticField.class,
                    AnalyticsDiagnosticCode.UNKNOWN_FIELD,
                    "Unknown semantic field."
                );
                SemanticPredicateOperator operator;
                if (peek(SemanticAnalyticsDslToken.Type.EQUALS)) {
                    advance();
                    operator = SemanticPredicateOperator.EQUALS;
                } else {
                    SemanticAnalyticsDslToken operatorToken = identifier("A predicate operator is required.");
                    operator = enumeration(
                        operatorToken,
                        SemanticPredicateOperator.class,
                        AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE,
                        "Use = or an approved predicate operator."
                    );
                }
                List<String> values;
                if (
                    operator == SemanticPredicateOperator.IN ||
                    operator == SemanticPredicateOperator.HAS_ANY ||
                    operator == SemanticPredicateOperator.HAS_ALL
                ) {
                    expect(SemanticAnalyticsDslToken.Type.LEFT_PAREN, "IN must be followed by a parenthesized value list.");
                    List<String> parsed = new ArrayList<>();
                    parsed.add(value());
                    while (peek(SemanticAnalyticsDslToken.Type.COMMA)) {
                        advance();
                        parsed.add(value());
                    }
                    expect(SemanticAnalyticsDslToken.Type.RIGHT_PAREN, "IN value lists must end with a closing parenthesis.");
                    values = List.copyOf(parsed);
                } else if (operator == SemanticPredicateOperator.IS_UNCATEGORIZED || operator == SemanticPredicateOperator.HAS_NONE) {
                    values = List.of();
                } else {
                    values = List.of(value());
                }
                sourceMap.record(path + "[" + predicateIndex + "]", fieldToken);
                predicates.add(new SemanticPredicate(field, operator, values));
                if (!peekKeyword("AND")) {
                    break;
                }
                advance();
            } while (true);
            if (stopAtRightParen && !peek(SemanticAnalyticsDslToken.Type.RIGHT_PAREN)) {
                throw error(
                    AnalyticsDiagnosticCode.UNEXPECTED_TOKEN,
                    current(),
                    "Calculation conditions must end before the aggregate closing parenthesis."
                );
            }
            return List.copyOf(predicates);
        }

        private List<SemanticField> parseFields(String path) {
            List<SemanticField> fields = new ArrayList<>();
            do {
                int fieldIndex = fields.size();
                SemanticAnalyticsDslToken token = identifier("A semantic field is required.");
                sourceMap.record(path + "[" + fieldIndex + "]", token);
                fields.add(enumeration(token, SemanticField.class, AnalyticsDiagnosticCode.UNKNOWN_FIELD, "Unknown semantic field."));
                if (!peek(SemanticAnalyticsDslToken.Type.COMMA)) {
                    break;
                }
                advance();
            } while (true);
            return List.copyOf(fields);
        }

        private List<SemanticOrder> parseOrders(List<SemanticAggregateProjection> projections) {
            List<SemanticOrder> order = new ArrayList<>();
            do {
                int orderIndex = order.size();
                SemanticAnalyticsDslToken targetToken = identifier("ORDER BY requires a grouping or calculation alias.");
                SemanticAnalyticsDslToken directionToken = identifier("ORDER BY requires ASC or DESC.");
                sourceMap.record("definition.orderBy[" + orderIndex + "]", targetToken);
                SemanticSortDirection direction = enumeration(
                    directionToken,
                    SemanticSortDirection.class,
                    AnalyticsDiagnosticCode.INVALID_SORT,
                    "ORDER BY requires ASC or DESC."
                );
                String alias = projections
                    .stream()
                    .map(SemanticAggregateProjection::alias)
                    .filter(candidate -> candidate.equalsIgnoreCase(targetToken.text()))
                    .findFirst()
                    .orElse(null);
                if (alias != null) {
                    order.add(SemanticOrder.projection(alias, direction));
                } else {
                    order.add(
                        new SemanticOrder(
                            enumeration(
                                targetToken,
                                SemanticField.class,
                                AnalyticsDiagnosticCode.UNKNOWN_FIELD,
                                "Unknown grouping or calculation alias."
                            ),
                            direction
                        )
                    );
                }
                if (!peek(SemanticAnalyticsDslToken.Type.COMMA)) {
                    break;
                }
                advance();
            } while (true);
            return List.copyOf(order);
        }

        private String value() {
            SemanticAnalyticsDslToken token = current();
            if (
                peek(SemanticAnalyticsDslToken.Type.WORD) ||
                peek(SemanticAnalyticsDslToken.Type.NUMBER) ||
                peek(SemanticAnalyticsDslToken.Type.STRING)
            ) {
                advance();
                return token.text();
            }
            throw error(
                AnalyticsDiagnosticCode.INVALID_LITERAL,
                token,
                "A predicate value must be an identifier, number, or quoted string."
            );
        }

        private SemanticAnalyticsDslToken identifier(String message) {
            SemanticAnalyticsDslToken token = current();
            if (!peek(SemanticAnalyticsDslToken.Type.WORD)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, token, message);
            }
            advance();
            return token;
        }

        private <E extends Enum<E>> E enumeration(
            SemanticAnalyticsDslToken token,
            Class<E> type,
            AnalyticsDiagnosticCode code,
            String message
        ) {
            try {
                return Enum.valueOf(type, token.upperText());
            } catch (IllegalArgumentException exception) {
                throw SemanticAnalyticsDslLexer.failure(
                    code,
                    message,
                    token.startOffset(),
                    token.line(),
                    token.column(),
                    token.span(),
                    token.text(),
                    java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()
                );
            }
        }

        private void expectKeyword(String keyword, String message) {
            if (!peekKeyword(keyword)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, current(), message);
            }
            advance();
        }

        private void expect(SemanticAnalyticsDslToken.Type type, String message) {
            if (!peek(type)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, current(), message);
            }
            advance();
        }

        private boolean clauseKeyword(SemanticAnalyticsDslToken token) {
            return (
                token.type() == SemanticAnalyticsDslToken.Type.WORD &&
                switch (token.upperText()) {
                    case "SELECT", "FROM", "TIME", "WHERE", "GROUP", "ORDER", "LIMIT" -> true;
                    default -> false;
                }
            );
        }

        private boolean peekKeyword(String keyword) {
            return peek(SemanticAnalyticsDslToken.Type.WORD) && keyword.equals(current().upperText());
        }

        private boolean peek(SemanticAnalyticsDslToken.Type type) {
            return current().type() == type;
        }

        private SemanticAnalyticsDslToken current() {
            return tokens.get(index);
        }

        private void advance() {
            index++;
        }

        private SemanticAnalyticsDslException error(AnalyticsDiagnosticCode code, SemanticAnalyticsDslToken token, String message) {
            return SemanticAnalyticsDslLexer.failure(
                code,
                message,
                token.startOffset(),
                token.line(),
                token.column(),
                token.span(),
                token.text(),
                List.of()
            );
        }
    }
}
