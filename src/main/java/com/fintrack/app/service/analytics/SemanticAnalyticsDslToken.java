package com.fintrack.app.service.analytics;

/** One bounded lexical token. Positions are one-based for API diagnostics. */
record SemanticAnalyticsDslToken(Type type, String text, int startOffset, int endOffset, int line, int column) {
    enum Type {
        WORD,
        NUMBER,
        STRING,
        COMMA,
        LEFT_PAREN,
        RIGHT_PAREN,
        EQUALS,
        END,
    }

    int span() {
        return Math.max(1, endOffset - startOffset);
    }

    String upperText() {
        return text.toUpperCase(java.util.Locale.ROOT);
    }
}
