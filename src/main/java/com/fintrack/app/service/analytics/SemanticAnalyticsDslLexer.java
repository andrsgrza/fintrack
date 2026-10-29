package com.fintrack.app.service.analytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded lexer for the semantic language. It recognizes only the grammar punctuation, words and SQL-style quoted
 * literals; it never exposes persistence identifiers or executable SQL tokens.
 */
final class SemanticAnalyticsDslLexer {

    static final int MAX_INPUT_LENGTH = 8192;
    static final int MAX_TOKEN_COUNT = 512;

    List<SemanticAnalyticsDslToken> tokenize(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw failure(AnalyticsDiagnosticCode.EMPTY_QUERY, "A semantic query is required.", 0, 1, 1, 1, null, List.of());
        }
        if (source.length() > MAX_INPUT_LENGTH) {
            throw failure(
                AnalyticsDiagnosticCode.INPUT_TOO_LONG,
                "A semantic query may contain at most %d characters.".formatted(MAX_INPUT_LENGTH),
                0,
                1,
                1,
                1,
                null,
                List.of()
            );
        }
        List<SemanticAnalyticsDslToken> tokens = new ArrayList<>();
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
            switch (character) {
                case ',' -> {
                    add(tokens, new SemanticAnalyticsDslToken(SemanticAnalyticsDslToken.Type.COMMA, ",", start, ++index, line, column++));
                    continue;
                }
                case '(' -> {
                    add(
                        tokens,
                        new SemanticAnalyticsDslToken(SemanticAnalyticsDslToken.Type.LEFT_PAREN, "(", start, ++index, line, column++)
                    );
                    continue;
                }
                case ')' -> {
                    add(
                        tokens,
                        new SemanticAnalyticsDslToken(SemanticAnalyticsDslToken.Type.RIGHT_PAREN, ")", start, ++index, line, column++)
                    );
                    continue;
                }
                case '=' -> {
                    add(tokens, new SemanticAnalyticsDslToken(SemanticAnalyticsDslToken.Type.EQUALS, "=", start, ++index, line, column++));
                    continue;
                }
                case '\'' -> {
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
                            null,
                            List.of()
                        );
                    }
                    add(
                        tokens,
                        new SemanticAnalyticsDslToken(
                            SemanticAnalyticsDslToken.Type.STRING,
                            value.toString(),
                            start,
                            index,
                            tokenLine,
                            tokenColumn
                        )
                    );
                    continue;
                }
                default -> {
                    // handled below
                }
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
                SemanticAnalyticsDslToken.Type type = startsNumeric && value.chars().allMatch(Character::isDigit)
                    ? SemanticAnalyticsDslToken.Type.NUMBER
                    : SemanticAnalyticsDslToken.Type.WORD;
                add(tokens, new SemanticAnalyticsDslToken(type, value, start, index, tokenLine, tokenColumn));
                continue;
            }
            throw failure(
                AnalyticsDiagnosticCode.INVALID_TOKEN,
                "Unsupported character in a semantic query.",
                start,
                tokenLine,
                tokenColumn,
                1,
                String.valueOf(character),
                List.of()
            );
        }
        add(tokens, new SemanticAnalyticsDslToken(SemanticAnalyticsDslToken.Type.END, "", source.length(), source.length(), line, column));
        return List.copyOf(tokens);
    }

    private void add(List<SemanticAnalyticsDslToken> tokens, SemanticAnalyticsDslToken token) {
        if (token.type() != SemanticAnalyticsDslToken.Type.END && tokens.size() >= MAX_TOKEN_COUNT) {
            throw failure(
                AnalyticsDiagnosticCode.TOO_MANY_TOKENS,
                "A semantic query may contain at most %d tokens.".formatted(MAX_TOKEN_COUNT),
                token.startOffset(),
                token.line(),
                token.column(),
                token.span(),
                null,
                List.of()
            );
        }
        tokens.add(token);
    }

    static SemanticAnalyticsDslException failure(
        AnalyticsDiagnosticCode code,
        String message,
        int offset,
        int line,
        int column,
        int span,
        String identifier,
        List<String> allowedValues
    ) {
        return new SemanticAnalyticsDslException(
            List.of(new AnalyticsDiagnostic(code, "text", message, line, column, span, identifier, allowedValues))
        );
    }
}
