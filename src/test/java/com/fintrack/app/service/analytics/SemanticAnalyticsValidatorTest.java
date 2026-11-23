package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticAnalyticsValidatorTest {

    private final SemanticAnalyticsValidator validator = new SemanticAnalyticsValidator(
        null,
        null,
        null,
        new SemanticAnalyticsCatalogService()
    );

    @Test
    void normalizesDeterministicDefaultsForTheLedgerContract() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("transactionCount", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID)),
                List.of(),
                List.of(SemanticField.ACCOUNT_INSTITUTION),
                List.of(),
                null
            ),
            diagnostics
        );

        assertThat(diagnostics).isEmpty();
        assertThat(normalized).isPresent();
        assertThat(normalized.orElseThrow().limit()).isEqualTo(SemanticAnalyticsSchema.DEFAULT_LIMIT);
        assertThat(normalized.orElseThrow().orderBy()).containsExactly(
            new SemanticOrder(SemanticField.ACCOUNT_INSTITUTION, SemanticSortDirection.ASC)
        );
    }

    @Test
    void rejectsUnknownDatasetAndAggregationsOutsideTheGovernedVocabulary() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                null,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("bad", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_ID)),
                List.of(),
                List.of(),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(normalized).isEmpty();
        assertThat(diagnostics).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.UNKNOWN_DATASET);
    }

    @Test
    void rejectsAFieldAndFunctionCombinationThatTheLedgerDoesNotExpose() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("bad", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_ID)),
                List.of(),
                List.of(),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(normalized).isEmpty();
        assertThat(diagnostics).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.INVALID_AGGREGATION);
    }

    @Test
    void acceptsAnAsOfPositionPerAccountButRejectsUngroupedHistoricalBalances() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
                SemanticField.OBSERVATION_DATE,
                List.of(new SemanticAggregateProjection("balance", SemanticAggregateFunction.SUM, SemanticField.POSITION_BALANCE)),
                List.of(),
                List.of(SemanticField.ACCOUNT_ID),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(diagnostics).isEmpty();
        assertThat(normalized).isPresent();

        diagnostics.clear();
        normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
                SemanticField.OBSERVATION_DATE,
                List.of(new SemanticAggregateProjection("balance", SemanticAggregateFunction.SUM, SemanticField.POSITION_BALANCE)),
                List.of(),
                List.of(),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(normalized).isEmpty();
        assertThat(diagnostics).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.NON_ADDITIVE_TIME_AGGREGATION);
    }

    @Test
    void acceptsOnlyTheGovernedCurrencyPartitionedFinancialPositionKpi() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.FINANCIAL_POSITION_SNAPSHOT,
                SemanticField.OBSERVATION_DATE,
                List.of(new SemanticAggregateProjection("position", SemanticAggregateFunction.SUM, SemanticField.FINANCIAL_POSITION)),
                List.of(),
                List.of(),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(diagnostics).isEmpty();
        assertThat(normalized).isPresent();
    }

    @Test
    void acceptsCompatibleCountAndAverageProjectionsAndOrdersByTheirAlias() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(
                    new SemanticAggregateProjection("expense_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
                    new SemanticAggregateProjection("average_expense", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_AMOUNT)
                ),
                List.of(),
                List.of(SemanticField.CATEGORY_ID),
                List.of(SemanticOrder.projection("average_expense", SemanticSortDirection.DESC)),
                10
            ),
            diagnostics
        );

        assertThat(diagnostics).isEmpty();
        assertThat(normalized).isPresent();
        assertThat(normalized.orElseThrow().orderBy()).containsExactly(
            SemanticOrder.projection("average_expense", SemanticSortDirection.DESC)
        );
    }

    @Test
    void requires_one_descendant_category_condition_when_grouping_a_category_branch() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT)),
                List.of(),
                List.of(SemanticField.CATEGORY_BRANCH_CHILD),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(normalized).isEmpty();
        assertThat(diagnostics).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.INVALID_CATEGORY_BRANCH_GROUPING);
    }

    @Test
    void accepts_tag_conditions_only_as_transaction_ledger_calculation_conditions() {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();

        var normalized = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(
                    new SemanticAggregateProjection(
                        "tagged_total",
                        SemanticAggregateFunction.SUM,
                        SemanticField.TRANSACTION_AMOUNT,
                        List.of(new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_ANY, List.of("42")))
                    )
                ),
                List.of(),
                List.of(),
                List.of(),
                10
            ),
            diagnostics
        );

        assertThat(diagnostics).isEmpty();
        assertThat(normalized).isPresent();
        assertThat(normalized.orElseThrow().projections().get(0).conditions()).containsExactly(
            new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_ANY, List.of("42"))
        );
    }

    @Test
    void rejects_broken_or_cyclic_owner_scoped_category_hierarchies_before_execution() {
        assertThatThrownBy(() ->
            new SemanticAnalyticsReferenceCatalog(
                List.of(
                    new SemanticAnalyticsReferenceCatalog.CategoryReference(
                        1L,
                        "Orphan",
                        "#123456",
                        true,
                        2L,
                        com.fintrack.app.domain.enumeration.CategoryType.EXPENSE
                    )
                ),
                List.of()
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new SemanticAnalyticsReferenceCatalog(
                List.of(
                    new SemanticAnalyticsReferenceCatalog.CategoryReference(
                        1L,
                        "One",
                        "#123456",
                        true,
                        2L,
                        com.fintrack.app.domain.enumeration.CategoryType.EXPENSE
                    ),
                    new SemanticAnalyticsReferenceCatalog.CategoryReference(
                        2L,
                        "Two",
                        "#123456",
                        true,
                        1L,
                        com.fintrack.app.domain.enumeration.CategoryType.EXPENSE
                    )
                ),
                List.of()
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allows_one_temporal_primary_breakdown_and_one_comparison_but_rejects_two_time_buckets() {
        List<AnalyticsDiagnostic> supportedDiagnostics = new ArrayList<>();
        var supported = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT)),
                List.of(),
                List.of(SemanticField.TRANSACTION_MONTH, SemanticField.ACCOUNT_ID),
                List.of(),
                10
            ),
            supportedDiagnostics
        );

        assertThat(supportedDiagnostics).isEmpty();
        assertThat(supported).isPresent();

        List<AnalyticsDiagnostic> unsupportedDiagnostics = new ArrayList<>();
        var unsupported = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.TRANSACTION_LEDGER,
                SemanticField.TRANSACTION_DATE,
                List.of(new SemanticAggregateProjection("total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT)),
                List.of(),
                List.of(SemanticField.TRANSACTION_MONTH, SemanticField.TRANSACTION_YEAR),
                List.of(),
                10
            ),
            unsupportedDiagnostics
        );

        assertThat(unsupported).isEmpty();
        assertThat(unsupportedDiagnostics)
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING_COMBINATION);
    }

    @Test
    void governs_credit_observations_and_transfer_time_buckets_without_restoring_named_metrics() {
        List<AnalyticsDiagnostic> creditDiagnostics = new ArrayList<>();
        var credit = validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
                SemanticField.OBSERVATION_DATE,
                List.of(new SemanticAggregateProjection("debt", SemanticAggregateFunction.SUM, SemanticField.CREDIT_DEBT)),
                List.of(),
                List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH),
                List.of(),
                10
            ),
            creditDiagnostics
        );
        assertThat(creditDiagnostics).isEmpty();
        assertThat(credit).isPresent();

        List<AnalyticsDiagnostic> missingAccountDiagnostics = new ArrayList<>();
        validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
                SemanticField.OBSERVATION_DATE,
                List.of(new SemanticAggregateProjection("debt", SemanticAggregateFunction.SUM, SemanticField.CREDIT_DEBT)),
                List.of(),
                List.of(SemanticField.OBSERVATION_MONTH),
                List.of(),
                10
            ),
            missingAccountDiagnostics
        );
        assertThat(missingAccountDiagnostics)
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.INVALID_CREDIT_CALCULATION);

        List<AnalyticsDiagnostic> transferDiagnostics = new ArrayList<>();
        validator.validateDefinition(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                SemanticDataset.INTERNAL_TRANSFER_EVENT,
                SemanticField.TRANSFER_DATE,
                List.of(new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSFER_ID)),
                List.of(),
                List.of(SemanticField.TRANSFER_MONTH, SemanticField.TRANSFER_WEEK),
                List.of(),
                10
            ),
            transferDiagnostics
        );
        assertThat(transferDiagnostics)
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING_COMBINATION);
    }
}
