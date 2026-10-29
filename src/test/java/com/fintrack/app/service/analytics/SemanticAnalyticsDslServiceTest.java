package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure contract tests for the bounded text syntax and canonical roundtrip behavior. */
class SemanticAnalyticsDslServiceTest {

    private final SemanticAnalyticsDslService service = new SemanticAnalyticsDslService(
        new SemanticAnalyticsValidator(null, null, null, new SemanticAnalyticsCatalogService()),
        new SemanticAnalyticsCatalogService()
    );

    @Test
    void parsesAllRegisteredAggregateFunctionsPredicateOperatorsAndDatasetsIntoTheOneSemanticAst() {
        assertRoundTrips(
            "SELECT COUNT(TRANSACTION_ID) AS transaction_count FROM TRANSACTION_LEDGER " +
            "GROUP BY ACCOUNT_INSTITUTION ORDER BY ACCOUNT_INSTITUTION ASC LIMIT 100",
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticAggregateFunction.COUNT
        );
        assertRoundTrips(
            "SELECT AVG(TRANSACTION_AMOUNT) AS average_expense FROM TRANSACTION_LEDGER " +
            "WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED GROUP BY CATEGORY_ID LIMIT 100",
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticAggregateFunction.AVG
        );
        assertRoundTrips(
            "SELECT SUM(TRANSACTION_AMOUNT) AS total_expense FROM TRANSACTION_LEDGER " +
            "WHERE ACCOUNT_TYPE IN ('DEBIT', 'CASH') GROUP BY CATEGORY_ID, ACCOUNT_TYPE " +
            "ORDER BY CATEGORY_ID ASC, ACCOUNT_TYPE DESC LIMIT 100",
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticAggregateFunction.SUM
        );
        assertRoundTrips(
            "SELECT SUM(POSITION_BALANCE) AS balance FROM ACCOUNT_POSITION_SNAPSHOT " +
            "TIME BINDING OBSERVATION_DATE GROUP BY ACCOUNT_ID, OBSERVATION_MONTH " +
            "ORDER BY OBSERVATION_MONTH ASC LIMIT 500",
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            SemanticAggregateFunction.SUM
        );
        assertRoundTrips(
            "SELECT SUM(CREDIT_DEBT) AS debt FROM CREDIT_ACCOUNT_SNAPSHOT " +
            "TIME BINDING OBSERVATION_DATE GROUP BY ACCOUNT_ID, OBSERVATION_MONTH LIMIT 100",
            SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
            SemanticAggregateFunction.SUM
        );
        assertRoundTrips(
            "SELECT COUNT(TRANSFER_ID) AS transfer_count, SUM(TRANSFER_AMOUNT) AS transfer_volume " +
            "FROM INTERNAL_TRANSFER_EVENT GROUP BY TRANSFER_MONTH, DESTINATION_ACCOUNT_ID LIMIT 100",
            SemanticDataset.INTERNAL_TRANSFER_EVENT,
            SemanticAggregateFunction.COUNT
        );
    }

    @Test
    void canonicalFormatterEscapesQuotedLiteralsAndMaterializesDefaultsWithoutContext() {
        SemanticAnalyticsDslParseResult parsed = service.parse(
            "SELECT COUNT(TRANSACTION_ID) AS count_by_type FROM TRANSACTION_LEDGER " +
            "WHERE ACCOUNT_TYPE IN ('DEBIT', 'CASH') GROUP BY ACCOUNT_TYPE"
        );

        assertThat(parsed.canonicalText()).isEqualTo(
            "SELECT COUNT(TRANSACTION_ID) AS count_by_type FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE " +
            "WHERE ACCOUNT_TYPE IN ('DEBIT', 'CASH') GROUP BY ACCOUNT_TYPE ORDER BY ACCOUNT_TYPE ASC LIMIT 100"
        );
        assertThat(parsed.canonicalText()).doesNotContain("ABSOLUTE_RANGE", "accountIds", "currencyCodes", "owner");
        assertThat(service.format(parsed.definition())).isEqualTo(parsed.canonicalText());
    }

    @Test
    void roundTrips_per_calculation_conditions_and_hierarchy_and_tag_predicates_through_the_same_ast() {
        SemanticAnalyticsDslParseResult parsed = service.parse(
            "SELECT COUNT(TRANSACTION_ID FILTER WHERE TRANSACTION_FLOW = OUT) AS expense_count, " +
            "AVG(TRANSACTION_AMOUNT FILTER WHERE TAG_ID HAS_ALL ('11', '12')) AS tagged_average " +
            "FROM TRANSACTION_LEDGER WHERE CATEGORY_ID DESCENDANT_OF 7 AND TAG_ID HAS_NONE " +
            "GROUP BY CATEGORY_BRANCH_CHILD ORDER BY tagged_average DESC LIMIT 25"
        );

        assertThat(parsed.definition().projections()).hasSize(2);
        assertThat(parsed.definition().projections().get(0).conditions()).containsExactly(
            new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, java.util.List.of("OUT"))
        );
        assertThat(parsed.definition().projections().get(1).conditions()).containsExactly(
            new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_ALL, java.util.List.of("11", "12"))
        );
        assertThat(parsed.definition().predicates()).containsExactly(
            new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.DESCENDANT_OF, java.util.List.of("7")),
            new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_NONE, java.util.List.of())
        );
        assertThat(service.parse(parsed.canonicalText()).definition()).isEqualTo(parsed.definition());
        assertThat(parsed.canonicalText()).contains("FILTER WHERE", "HAS_ALL", "HAS_NONE", "CATEGORY_BRANCH_CHILD");
    }

    @Test
    void roundTrips_temporal_primary_and_comparison_breakdowns_through_the_same_ast() {
        SemanticAnalyticsDslParseResult parsed = service.parse(
            "SELECT SUM(TRANSACTION_AMOUNT FILTER WHERE TRANSACTION_FLOW = IN AND TRANSFER_LINK_STATUS = UNLINKED) AS income, " +
            "SUM(TRANSACTION_AMOUNT FILTER WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED) AS expense " +
            "FROM TRANSACTION_LEDGER GROUP BY TRANSACTION_MONTH, CATEGORY_ROOT " +
            "ORDER BY TRANSACTION_MONTH ASC, CATEGORY_ROOT ASC LIMIT 100"
        );

        assertThat(parsed.definition().groupBy()).containsExactly(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ROOT);
        assertThat(parsed.definition().projections()).hasSize(2);
        assertThat(service.parse(parsed.canonicalText()).definition()).isEqualTo(parsed.definition());
        assertThat(parsed.canonicalText()).contains("TRANSACTION_MONTH", "CATEGORY_ROOT", "FILTER WHERE");
    }

    @Test
    void reportsBoundedSourceAwareDiagnosticsForMalformedOrNonSemanticSql() {
        assertThatThrownBy(() -> service.parse("SELECT COUNT(TRANSACTION_ID) AS c FROM financial_transaction LIMIT 1"))
            .isInstanceOf(SemanticAnalyticsDslException.class)
            .satisfies(exception -> {
                SemanticAnalyticsDslException dslException = (SemanticAnalyticsDslException) exception;
                assertThat(dslException.getDiagnostics())
                    .singleElement()
                    .satisfies(diagnostic -> {
                        assertThat(diagnostic.code()).isEqualTo(AnalyticsDiagnosticCode.UNKNOWN_DATASET);
                        assertThat(diagnostic.line()).isEqualTo(1);
                        assertThat(diagnostic.column()).isPositive();
                        assertThat(diagnostic.allowedValues()).contains(
                            "TRANSACTION_LEDGER",
                            "ACCOUNT_POSITION_SNAPSHOT",
                            "CREDIT_ACCOUNT_SNAPSHOT",
                            "INTERNAL_TRANSFER_EVENT"
                        );
                    });
            });

        assertThatThrownBy(() ->
            service.parse(
                "SELECT COUNT(TRANSACTION_ID) AS c FROM TRANSACTION_LEDGER " + "WHERE TRANSACTION_FLOW = OUT WHERE ACCOUNT_TYPE = DEBIT"
            )
        )
            .isInstanceOf(SemanticAnalyticsDslException.class)
            .satisfies(exception ->
                assertThat(((SemanticAnalyticsDslException) exception).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.DUPLICATE_CLAUSE)
            );
    }

    private void assertRoundTrips(String text, SemanticDataset dataset, SemanticAggregateFunction aggregate) {
        SemanticAnalyticsDslParseResult parsed = service.parse(text);
        assertThat(parsed.definition().dataset()).isEqualTo(dataset);
        assertThat(parsed.definition().projections().get(0).function()).isEqualTo(aggregate);
        assertThat(service.parse(parsed.canonicalText()).definition()).isEqualTo(parsed.definition());
        assertThat(service.parse(service.parse(parsed.canonicalText()).canonicalText()).canonicalText()).isEqualTo(parsed.canonicalText());
    }
}
