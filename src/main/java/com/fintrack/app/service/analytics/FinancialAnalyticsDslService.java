package com.fintrack.app.service.analytics;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parses and formats a restricted financial definition language. It never executes SQL or Analytics calculations;
 * semantic authority remains the shared catalog and validator.
 */
@Service
@Transactional(readOnly = true)
public class FinancialAnalyticsDslService {

    private final FinancialAnalyticsValidator validator;
    private final FinancialAnalyticsDslParser parser = new FinancialAnalyticsDslParser();
    private final FinancialAnalyticsDslFormatter formatter = new FinancialAnalyticsDslFormatter();

    public FinancialAnalyticsDslService(FinancialAnalyticsValidator validator) {
        this.validator = validator;
    }

    public FinancialAnalyticsDslParseResult parse(String text) {
        FinancialAnalyticsDslParser.ParsedQueryDefinition parsed = parser.parse(text);
        AnalyticsDefinitionValidationResult validation = validator.validateDefinition(parsed.definition());
        if (!validation.isValid()) {
            throw new FinancialAnalyticsDslException(decorate(validation.diagnostics(), parsed.sourceMap()));
        }
        QueryDefinition definition = validation.normalizedDefinition().orElseThrow();
        return new FinancialAnalyticsDslParseResult(definition, formatter.format(definition));
    }

    public String format(QueryDefinition definition) {
        AnalyticsDefinitionValidationResult validation = validator.validateDefinition(definition);
        if (!validation.isValid()) {
            throw new FinancialAnalyticsDslException(validation.diagnostics());
        }
        return formatter.format(validation.normalizedDefinition().orElseThrow());
    }

    private List<AnalyticsDiagnostic> decorate(List<AnalyticsDiagnostic> diagnostics, FinancialAnalyticsDslParser.SourceMap sourceMap) {
        return diagnostics
            .stream()
            .map(diagnostic -> {
                FinancialAnalyticsDslToken token = sourceMap.tokenFor(diagnostic);
                return token == null ? diagnostic : diagnostic.atSource(token.line(), token.column(), token.span(), token.text());
            })
            .toList();
    }
}
