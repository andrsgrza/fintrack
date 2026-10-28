package com.fintrack.app.service.analytics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps the restricted text syntax directly to the one canonical {@link QueryDefinition} contract. */
final class FinancialAnalyticsDslParser {

    private final FinancialAnalyticsDslLexer lexer = new FinancialAnalyticsDslLexer();

    ParsedQueryDefinition parse(String source) {
        return new Cursor(lexer.tokenize(source)).parse();
    }

    record ParsedQueryDefinition(QueryDefinition definition, SourceMap sourceMap) {}

    static final class SourceMap {

        private final Map<String, List<FinancialAnalyticsDslToken>> tokensByField = new HashMap<>();
        private final List<FinancialAnalyticsDslToken> predicateTokens = new ArrayList<>();

        void record(String field, FinancialAnalyticsDslToken token) {
            tokensByField.computeIfAbsent(field, ignored -> new ArrayList<>()).add(token);
        }

        void predicate(FinancialAnalyticsDslToken token) {
            predicateTokens.add(token);
        }

        FinancialAnalyticsDslToken tokenFor(AnalyticsDiagnostic diagnostic) {
            String field = diagnostic.field();
            if (field != null && field.startsWith("definition.analyticalPredicates[")) {
                int end = field.indexOf(']');
                if (end > 0) {
                    try {
                        int index = Integer.parseInt(field.substring("definition.analyticalPredicates[".length(), end));
                        if (index >= 0 && index < predicateTokens.size()) {
                            return predicateTokens.get(index);
                        }
                    } catch (NumberFormatException ignored) {
                        // A field emitted by a future validator revision still receives a useful fallback below.
                    }
                }
            }
            List<FinancialAnalyticsDslToken> candidates = tokensByField.get(field);
            if (candidates != null) {
                for (FinancialAnalyticsDslToken candidate : candidates) {
                    if (
                        diagnostic.message() != null &&
                        diagnostic.message().toUpperCase(java.util.Locale.ROOT).contains(candidate.upperText())
                    ) {
                        return candidate;
                    }
                }
                return candidates.get(0);
            }
            List<FinancialAnalyticsDslToken> text = tokensByField.get("text");
            return text == null ? null : text.get(0);
        }
    }

    private static final class Cursor {

        private static final Set<Clause> SEEN_NONE = EnumSet.noneOf(Clause.class);

        private final List<FinancialAnalyticsDslToken> tokens;
        private final SourceMap sourceMap = new SourceMap();
        private final Set<Clause> seen = EnumSet.copyOf(SEEN_NONE);
        private int index;
        private Clause lastClause = Clause.SELECT;

        Cursor(List<FinancialAnalyticsDslToken> tokens) {
            this.tokens = tokens;
            sourceMap.record("text", tokens.get(0));
        }

        ParsedQueryDefinition parse() {
            expectKeyword("SELECT", "An Analytics definition must begin with SELECT.");
            List<String> measures = parseIdentifierList("definition.measures");
            List<String> groupings = List.of();
            List<AnalyticalPredicate> predicates = List.of();
            String dateBasis = null;
            AnalyticsSort sort = null;
            Integer limit = null;

            while (!peek(FinancialAnalyticsDslToken.Type.END)) {
                Clause clause = clauseAtCurrentToken();
                if (clause == null) {
                    throw error(
                        AnalyticsDiagnosticCode.TRAILING_GARBAGE,
                        current(),
                        "Unexpected text after a complete clause. Only BY, WHERE, USING, ORDER BY, and LIMIT are permitted."
                    );
                }
                if (seen.contains(clause)) {
                    throw error(AnalyticsDiagnosticCode.DUPLICATE_CLAUSE, current(), "Each Analytics clause may appear at most once.");
                }
                if (clause.ordinal() < lastClause.ordinal()) {
                    throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, current(), "Analytics clauses must use canonical clause order.");
                }
                seen.add(clause);
                lastClause = clause;
                switch (clause) {
                    case BY -> {
                        advance();
                        groupings = parseIdentifierList("definition.groupings");
                    }
                    case WHERE -> {
                        advance();
                        predicates = parsePredicates();
                    }
                    case USING -> {
                        advance();
                        FinancialAnalyticsDslToken token = parseIdentifier("A date basis is required after USING.");
                        sourceMap.record("definition.dateBasis", token);
                        dateBasis = token.text();
                    }
                    case ORDER -> {
                        advance();
                        expectKeyword("BY", "ORDER must be followed by BY.");
                        FinancialAnalyticsDslToken grouping = parseIdentifier("A grouping is required after ORDER BY.");
                        sourceMap.record("definition.sort", grouping);
                        FinancialAnalyticsDslToken direction = parseIdentifier("ORDER BY requires ASC or DESC.");
                        sort = new AnalyticsSort(grouping.text(), direction.text());
                    }
                    case LIMIT -> {
                        advance();
                        FinancialAnalyticsDslToken value = current();
                        if (!peek(FinancialAnalyticsDslToken.Type.NUMBER)) {
                            throw error(AnalyticsDiagnosticCode.INVALID_LIMIT, value, "LIMIT must be a positive whole number.");
                        }
                        advance();
                        sourceMap.record("definition.limit", value);
                        try {
                            limit = Integer.valueOf(value.text());
                        } catch (NumberFormatException exception) {
                            throw error(AnalyticsDiagnosticCode.INVALID_LIMIT, value, "LIMIT must be a supported whole number.");
                        }
                    }
                    case SELECT -> throw new IllegalStateException("SELECT is consumed before the clause loop.");
                }
            }
            return new ParsedQueryDefinition(
                new QueryDefinition(
                    AnalyticsSchema.VERSION,
                    AnalyticsSchema.SEMANTIC_MODEL,
                    measures,
                    groupings,
                    predicates,
                    dateBasis,
                    sort,
                    limit
                ),
                sourceMap
            );
        }

        private List<String> parseIdentifierList(String field) {
            List<String> values = new ArrayList<>();
            FinancialAnalyticsDslToken first = parseIdentifier("An identifier is required.");
            sourceMap.record(field, first);
            values.add(first.text());
            while (peek(FinancialAnalyticsDslToken.Type.COMMA)) {
                advance();
                FinancialAnalyticsDslToken token = parseIdentifier("An identifier is required after a comma.");
                sourceMap.record(field, token);
                values.add(token.text());
            }
            return List.copyOf(values);
        }

        private List<AnalyticalPredicate> parsePredicates() {
            List<AnalyticalPredicate> predicates = new ArrayList<>();
            predicates.add(parsePredicate());
            while (peekKeyword("AND")) {
                advance();
                predicates.add(parsePredicate());
            }
            return List.copyOf(predicates);
        }

        private AnalyticalPredicate parsePredicate() {
            FinancialAnalyticsDslToken attribute = parseIdentifier("A predicate attribute is required.");
            sourceMap.predicate(attribute);
            if (peek(FinancialAnalyticsDslToken.Type.EQUALS)) {
                advance();
                return new AnalyticalPredicate(attribute.text(), FinancialSemanticCatalog.EQUALS, List.of(parseValue()));
            }
            FinancialAnalyticsDslToken operator = parseIdentifier("A predicate operator is required.");
            String normalizedOperator = operator.upperText();
            if (FinancialSemanticCatalog.EQUALS.equals(normalizedOperator)) {
                return new AnalyticalPredicate(attribute.text(), normalizedOperator, List.of(parseValue()));
            }
            if (FinancialSemanticCatalog.IN.equals(normalizedOperator)) {
                expect(FinancialAnalyticsDslToken.Type.LEFT_PAREN, "IN must be followed by a parenthesized value list.");
                List<String> values = new ArrayList<>();
                values.add(parseValue());
                while (peek(FinancialAnalyticsDslToken.Type.COMMA)) {
                    advance();
                    values.add(parseValue());
                }
                expect(FinancialAnalyticsDslToken.Type.RIGHT_PAREN, "IN value lists must end with ).");
                return new AnalyticalPredicate(attribute.text(), normalizedOperator, values);
            }
            if (FinancialSemanticCatalog.BETWEEN.equals(normalizedOperator)) {
                String first = parseValue();
                expectKeyword("AND", "BETWEEN requires AND between its two values.");
                return new AnalyticalPredicate(attribute.text(), normalizedOperator, List.of(first, parseValue()));
            }
            throw error(
                AnalyticsDiagnosticCode.UNEXPECTED_TOKEN,
                operator,
                "Supported predicate operators are EQUALS, =, IN, and BETWEEN."
            );
        }

        private String parseValue() {
            FinancialAnalyticsDslToken token = current();
            if (
                peek(FinancialAnalyticsDslToken.Type.WORD) ||
                peek(FinancialAnalyticsDslToken.Type.NUMBER) ||
                peek(FinancialAnalyticsDslToken.Type.STRING)
            ) {
                advance();
                return token.text();
            }
            throw error(
                AnalyticsDiagnosticCode.INVALID_LITERAL,
                token,
                "A predicate value must be an identifier, number, ISO date, or quoted string."
            );
        }

        private FinancialAnalyticsDslToken parseIdentifier(String message) {
            FinancialAnalyticsDslToken token = current();
            if (!peek(FinancialAnalyticsDslToken.Type.WORD)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, token, message);
            }
            advance();
            return token;
        }

        private Clause clauseAtCurrentToken() {
            if (!peek(FinancialAnalyticsDslToken.Type.WORD)) {
                return null;
            }
            return switch (current().upperText()) {
                case "BY" -> Clause.BY;
                case "WHERE" -> Clause.WHERE;
                case "USING" -> Clause.USING;
                case "ORDER" -> Clause.ORDER;
                case "LIMIT" -> Clause.LIMIT;
                default -> null;
            };
        }

        private void expectKeyword(String keyword, String message) {
            if (!peekKeyword(keyword)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, current(), message);
            }
            advance();
        }

        private void expect(FinancialAnalyticsDslToken.Type type, String message) {
            if (!peek(type)) {
                throw error(AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, current(), message);
            }
            advance();
        }

        private boolean peekKeyword(String keyword) {
            return peek(FinancialAnalyticsDslToken.Type.WORD) && keyword.equals(current().upperText());
        }

        private boolean peek(FinancialAnalyticsDslToken.Type type) {
            return current().type() == type;
        }

        private FinancialAnalyticsDslToken current() {
            return tokens.get(index);
        }

        private void advance() {
            index++;
        }

        private FinancialAnalyticsDslException error(AnalyticsDiagnosticCode code, FinancialAnalyticsDslToken token, String message) {
            return FinancialAnalyticsDslLexer.failure(
                code,
                message,
                token.startOffset(),
                token.line(),
                token.column(),
                token.span(),
                token.text()
            );
        }
    }

    private enum Clause {
        SELECT,
        BY,
        WHERE,
        USING,
        ORDER,
        LIMIT,
    }
}
