package com.fintrack.app.service.analytics;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-executing semantic-language boundary. Parsing and formatting share the execution validator's catalog-backed
 * normalization, so a formatted definition never describes a query that the semantic engine cannot accept.
 */
@Service
@Transactional(readOnly = true)
public class SemanticAnalyticsDslService {

    private final SemanticAnalyticsValidator validator;
    private final SemanticAnalyticsDslParser parser;
    private final SemanticAnalyticsDslFormatter formatter = new SemanticAnalyticsDslFormatter();

    public SemanticAnalyticsDslService(SemanticAnalyticsValidator validator, SemanticAnalyticsCatalogService catalog) {
        this.validator = validator;
        this.parser = new SemanticAnalyticsDslParser(catalog);
    }

    public SemanticAnalyticsDslParseResult parse(String text) {
        SemanticAnalyticsDslParser.ParsedDefinition parsed = parser.parse(text);
        SemanticQueryDefinition normalized;
        try {
            normalized = validator.normalizeDefinitionOrThrow(parsed.definition());
        } catch (SemanticAnalyticsValidationException exception) {
            List<AnalyticsDiagnostic> diagnostics = exception.getDiagnostics().stream().map(parsed.sourceMap()::withSource).toList();
            throw new SemanticAnalyticsDslException(diagnostics);
        }
        return new SemanticAnalyticsDslParseResult(normalized, formatter.format(normalized));
    }

    public String format(SemanticQueryDefinition definition) {
        return formatter.format(validator.normalizeDefinitionOrThrow(definition));
    }
}
