package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialAnalyticsValidatorTest {

    private FinancialAnalyticsValidator validator;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserLogin()).thenReturn("user");
        validator = new FinancialAnalyticsValidator(
            new FinancialSemanticCatalog(),
            new FinancialMetricTimePolicy(Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)),
            currentUserService,
            mock(FinancialAccountRepository.class),
            mock(CategoryRepository.class),
            mock(TagRepository.class)
        );
    }

    @Test
    void normalizesOptionalFieldsIdempotentlyWithoutBroadeningScope() {
        FinancialAnalyticsExecutionRequest request = new FinancialAnalyticsExecutionRequest(
            AnalyticsSchema.VERSION,
            new QueryDefinition(
                AnalyticsSchema.VERSION,
                AnalyticsSchema.SEMANTIC_MODEL,
                List.of(" income_total "),
                List.of("month"),
                List.of(
                    new AnalyticalPredicate("flow", "equals", List.of("in")),
                    new AnalyticalPredicate("currency", "in", List.of("usd", "mxn", "usd"))
                ),
                null,
                null,
                null
            ),
            new VisualizationContext(
                AnalyticsSchema.VERSION,
                new VisualizationTime("relative", "this_month", null, null),
                List.of(),
                List.of("mxn")
            )
        );

        AnalyticsValidationResult first = validator.validate(request);
        FinancialAnalyticsExecutionRequest normalized = first.normalizedRequest().orElseThrow().request();
        AnalyticsValidationResult second = validator.validate(normalized);

        assertThat(first.isValid()).isTrue();
        assertThat(normalized.definition().dateBasis()).isEqualTo(FinancialSemanticCatalog.TRANSACTION_DATE);
        assertThat(normalized.definition().limit()).isEqualTo(AnalyticsSchema.DEFAULT_LIMIT);
        assertThat(normalized.definition().sort()).isEqualTo(new AnalyticsSort(FinancialSemanticCatalog.MONTH, "ASC"));
        assertThat(normalized.definition().analyticalPredicates()).containsExactly(
            new AnalyticalPredicate(FinancialSemanticCatalog.CURRENCY, FinancialSemanticCatalog.IN, List.of("MXN", "USD")),
            new AnalyticalPredicate(FinancialSemanticCatalog.FLOW, FinancialSemanticCatalog.EQUALS, List.of("IN"))
        );
        assertThat(normalized.context().accountIds()).isEmpty();
        assertThat(normalized.context().currencyCodes()).containsExactly("MXN");
        assertThat(second.normalizedRequest().orElseThrow().request()).isEqualTo(normalized);
    }

    @Test
    void rejectsMixedExecutorsTimeModesAndDuplicateMeasures() {
        AnalyticsValidationResult transferMix = validator.validate(request(List.of("INCOME_TOTAL", "INTERNAL_TRANSFER_COUNT")));
        AnalyticsValidationResult positionMix = validator.validate(request(List.of("INCOME_TOTAL", "CREDIT_CARD_DEBT")));
        AnalyticsValidationResult duplicate = validator.validate(request(List.of("INCOME_TOTAL", "INCOME_TOTAL")));

        assertThat(transferMix.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_EXECUTOR);
        assertThat(positionMix.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_TIME_MODE);
        assertThat(duplicate.diagnostics()).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.DUPLICATE_MEASURE);
    }

    @Test
    void rejectsUnsupportedGroupingDateBasisAndComplexity() {
        QueryDefinition invalid = new QueryDefinition(
            AnalyticsSchema.VERSION,
            AnalyticsSchema.SEMANTIC_MODEL,
            List.of("CREDIT_CARD_DEBT"),
            List.of("CATEGORY", "TAG", "MONTH"),
            List.of(),
            "POSTING_DATE",
            new AnalyticsSort("CATEGORY", "UP"),
            AnalyticsSchema.MAX_LIMIT + 1
        );
        AnalyticsValidationResult result = validator.validate(
            new FinancialAnalyticsExecutionRequest(
                AnalyticsSchema.VERSION,
                invalid,
                new VisualizationContext(
                    AnalyticsSchema.VERSION,
                    new VisualizationTime("ABSOLUTE_RANGE", null, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)),
                    List.of(),
                    List.of()
                )
            )
        );

        assertThat(result.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(
                AnalyticsDiagnosticCode.INVALID_GROUPING_COUNT,
                AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING,
                AnalyticsDiagnosticCode.UNSUPPORTED_DATE_BASIS,
                AnalyticsDiagnosticCode.INVALID_SORT,
                AnalyticsDiagnosticCode.INVALID_LIMIT,
                AnalyticsDiagnosticCode.INVALID_DATE_RANGE
            );
    }

    @Test
    void rejectsUnsupportedSchemaAndUnknownMetricOrAttribute() {
        QueryDefinition definition = new QueryDefinition(
            99,
            "UNSUPPORTED",
            List.of("UNKNOWN_METRIC"),
            List.of("UNKNOWN_ATTRIBUTE"),
            List.of(new AnalyticalPredicate("UNKNOWN_ATTRIBUTE", "IN", List.of("x"))),
            FinancialSemanticCatalog.TRANSACTION_DATE,
            null,
            1
        );
        AnalyticsValidationResult result = validator.validate(
            new FinancialAnalyticsExecutionRequest(
                99,
                definition,
                new VisualizationContext(99, new VisualizationTime("RELATIVE", "TODAY", null, null), List.of(), List.of("ZZZ"))
            )
        );

        assertThat(result.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(
                AnalyticsDiagnosticCode.UNKNOWN_SCHEMA_VERSION,
                AnalyticsDiagnosticCode.UNKNOWN_SEMANTIC_MODEL,
                AnalyticsDiagnosticCode.UNKNOWN_METRIC,
                AnalyticsDiagnosticCode.UNKNOWN_ATTRIBUTE,
                AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE
            );
    }

    @Test
    void rejectsUnsupportedOperatorsAndInvalidReferenceIdentifiers() {
        FinancialAnalyticsExecutionRequest request = new FinancialAnalyticsExecutionRequest(
            AnalyticsSchema.VERSION,
            new QueryDefinition(
                AnalyticsSchema.VERSION,
                AnalyticsSchema.SEMANTIC_MODEL,
                List.of("EXPENSE_TOTAL"),
                List.of(),
                List.of(
                    new AnalyticalPredicate(FinancialSemanticCatalog.CURRENCY, FinancialSemanticCatalog.BETWEEN, List.of("MXN", "USD")),
                    new AnalyticalPredicate(FinancialSemanticCatalog.ACCOUNT, FinancialSemanticCatalog.EQUALS, List.of("not-an-id"))
                ),
                FinancialSemanticCatalog.TRANSACTION_DATE,
                null,
                10
            ),
            new VisualizationContext(AnalyticsSchema.VERSION, new VisualizationTime("RELATIVE", "TODAY", null, null), List.of(), List.of())
        );

        AnalyticsValidationResult result = validator.validate(request);

        assertThat(result.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.UNSUPPORTED_PREDICATE, AnalyticsDiagnosticCode.INVALID_REFERENCE_ID);
    }

    private FinancialAnalyticsExecutionRequest request(List<String> measures) {
        return new FinancialAnalyticsExecutionRequest(
            AnalyticsSchema.VERSION,
            new QueryDefinition(
                AnalyticsSchema.VERSION,
                AnalyticsSchema.SEMANTIC_MODEL,
                measures,
                List.of(),
                List.of(),
                FinancialSemanticCatalog.TRANSACTION_DATE,
                null,
                10
            ),
            new VisualizationContext(AnalyticsSchema.VERSION, new VisualizationTime("RELATIVE", "TODAY", null, null), List.of(), List.of())
        );
    }
}
