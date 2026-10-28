package com.fintrack.app.service.analytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Deliberately small lexer for the governed Analytics definition language. It recognizes no SQL syntax beyond the
 * grammar's punctuation, and it keeps exact positions for API diagnostics.
 */
final class FinancialAnalyticsDslLexer {

    static final int MAX_INPUT_LENGTH = 8192;
    static final int MAX_TOKEN_COUNT = 512;

    List<FinancialAnalyticsDslToken> tokenize(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw failure(AnalyticsDiagnosticCode.EMPTY_QUERY, "A query definition is required.", 0, 1, 1, 1, null);
        }
        if (source.length() > MAX_INPUT_LENGTH) {
            throw failure(
                AnalyticsDiagnosticCode.INPUT_TOO_LONG,
                "A query definition may contain at most %d characters.".formatted(MAX_INPUT_LENGTH),
                0,
                1,
                1,
                1,
                null
            );
        }

        List<FinancialAnalyticsDslToken> tokens = new ArrayList<>();
        int index = 0;
        int line = 1;
        int column = 1;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (Character.isWhitespace(character)) {
                if (character == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
                index++;
                continue;
            }
            int start = index;
            int tokenLine = line;
            int tokenColumn = column;
            if (character == ',') {
                add(tokens, new FinancialAnalyticsDslToken(FinancialAnalyticsDslToken.Type.COMMA, ",", start, ++index, line, column++));
                continue;
            }
            if (character == '(') {
                add(
                    tokens,
                    new FinancialAnalyticsDslToken(FinancialAnalyticsDslToken.Type.LEFT_PAREN, "(", start, ++index, line, column++)
                );
                continue;
            }
            if (character == ')') {
                add(
                    tokens,
                    new FinancialAnalyticsDslToken(FinancialAnalyticsDslToken.Type.RIGHT_PAREN, ")", start, ++index, line, column++)
                );
                continue;
            }
            if (character == '=') {
                add(tokens, new FinancialAnalyticsDslToken(FinancialAnalyticsDslToken.Type.EQUALS, "=", start, ++index, line, column++));
                continue;
            }
            if (character == '\'') {
                StringBuilder value = new StringBuilder();
                index++;
                column++;
                boolean terminated = false;
                while (index < source.length()) {
                    char quoted = source.charAt(index++);
                    if (quoted == '\'') {
                        if (index < source.length() && source.charAt(index) == '\'') {
                            value.append('\'');
                            index++;
                            column += 2;
                            continue;
                        }
                        column++;
                        terminated = true;
                        break;
                    }
                    value.append(quoted);
                    if (quoted == '\n') {
                        line++;
                        column = 1;
                    } else {
                        column++;
                    }
                }
                if (!terminated) {
                    throw failure(
                        AnalyticsDiagnosticCode.INVALID_LITERAL,
                        "String literals use single quotes and escape a quote by doubling it.",
                        start,
                        tokenLine,
                        tokenColumn,
                        Math.max(1, index - start),
                        null
                    );
                }
                add(
                    tokens,
                    new FinancialAnalyticsDslToken(
                        FinancialAnalyticsDslToken.Type.STRING,
                        value.toString(),
                        start,
                        index,
                        tokenLine,
                        tokenColumn
                    )
                );
                continue;
            }
            if (Character.isLetter(character) || character == '_' || Character.isDigit(character)) {
                boolean startsNumeric = Character.isDigit(character);
                index++;
                column++;
                while (index < source.length()) {
                    char part = source.charAt(index);
                    if (!(Character.isLetterOrDigit(part) || part == '_' || part == '-')) {
                        break;
                    }
                    index++;
                    column++;
                }
                String value = source.substring(start, index);
                FinancialAnalyticsDslToken.Type type = startsNumeric && value.chars().allMatch(Character::isDigit)
                    ? FinancialAnalyticsDslToken.Type.NUMBER
                    : FinancialAnalyticsDslToken.Type.WORD;
                add(tokens, new FinancialAnalyticsDslToken(type, value, start, index, tokenLine, tokenColumn));
                continue;
            }
            throw failure(
                AnalyticsDiagnosticCode.INVALID_TOKEN,
                "Unsupported character in an Analytics definition.",
                start,
                tokenLine,
                tokenColumn,
                1,
                String.valueOf(character)
            );
        }
        add(
            tokens,
            new FinancialAnalyticsDslToken(FinancialAnalyticsDslToken.Type.END, "", source.length(), source.length(), line, column)
        );
        return List.copyOf(tokens);
    }

    private void add(List<FinancialAnalyticsDslToken> tokens, FinancialAnalyticsDslToken token) {
        if (token.type() != FinancialAnalyticsDslToken.Type.END && tokens.size() >= MAX_TOKEN_COUNT) {
            throw failure(
                AnalyticsDiagnosticCode.TOO_MANY_TOKENS,
                "A query definition may contain at most %d tokens.".formatted(MAX_TOKEN_COUNT),
                token.startOffset(),
                token.line(),
                token.column(),
                token.span(),
                null
            );
        }
        tokens.add(token);
    }

    static FinancialAnalyticsDslException failure(
        AnalyticsDiagnosticCode code,
        String message,
        int offset,
        int line,
        int column,
        int span,
        String identifier
    ) {
        return new FinancialAnalyticsDslException(
            List.of(new AnalyticsDiagnostic(code, "text", message, line, column, span, identifier, List.of()))
        );
    }
}
