package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One user-comprehensible, field-addressable validation diagnostic. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalyticsDiagnostic(
    AnalyticsDiagnosticCode code,
    String field,
    String message,
    Integer line,
    Integer column,
    Integer span,
    String identifier,
    List<String> allowedValues
) {
    public AnalyticsDiagnostic {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    /** Keeps existing contract producers concise while the DSL adds optional source metadata. */
    public AnalyticsDiagnostic(AnalyticsDiagnosticCode code, String field, String message) {
        this(code, field, message, null, null, null, null, List.of());
    }

    public AnalyticsDiagnostic atSource(int line, int column, int span, String identifier) {
        return new AnalyticsDiagnostic(code, field, message, line, column, span, identifier, allowedValues);
    }
}
