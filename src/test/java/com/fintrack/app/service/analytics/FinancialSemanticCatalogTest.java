package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.enumeration.AccountType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FinancialSemanticCatalogTest {

    private final FinancialSemanticCatalog catalog = new FinancialSemanticCatalog();

    @Test
    void exposesAllGovernedMetricsWithStableFinancialSemantics() {
        FinancialAnalyticsCatalog response = catalog.catalog(timePolicy());

        assertThat(response.schemaVersion()).isEqualTo(AnalyticsSchema.VERSION);
        assertThat(response.semanticModel()).isEqualTo(AnalyticsSchema.SEMANTIC_MODEL);
        assertThat(response.metrics()).hasSize(16);
        assertThat(response.metrics().stream().map(FinancialMetricDefinition::id)).containsExactly(
            "INCOME_TOTAL",
            "EXPENSE_TOTAL",
            "NET_ECONOMIC_FLOW",
            "ECONOMIC_TRANSACTION_COUNT",
            "AVERAGE_EXPENSE",
            "ACCOUNT_INFLOW",
            "ACCOUNT_OUTFLOW",
            "ACCOUNT_NET_MOVEMENT",
            "ACCOUNT_BALANCE",
            "ACCOUNT_SIGNED_POSITION",
            "NET_RECORDED_POSITION",
            "CREDIT_CARD_DEBT",
            "CREDIT_LIMIT",
            "AVAILABLE_CREDIT",
            "CREDIT_UTILIZATION",
            "INTERNAL_TRANSFER_COUNT"
        );
        assertThat(response.supportedRelativePeriods()).containsExactly(
            "TODAY",
            "THIS_MONTH",
            "PREVIOUS_MONTH",
            "LAST_30_DAYS",
            "LAST_6_CALENDAR_MONTHS",
            "THIS_YEAR"
        );
        assertThat(response.attributes())
            .extracting(FinancialSemanticAttributeDefinition::id)
            .contains(
                FinancialSemanticCatalog.TRANSACTION_DATE,
                FinancialSemanticCatalog.POSTING_DATE,
                FinancialSemanticCatalog.ACCOUNT,
                FinancialSemanticCatalog.ACCOUNT_TYPE,
                FinancialSemanticCatalog.CURRENCY,
                FinancialSemanticCatalog.CATEGORY,
                FinancialSemanticCatalog.ROOT_CATEGORY,
                FinancialSemanticCatalog.TAG,
                FinancialSemanticCatalog.TRANSFER_STATUS,
                FinancialSemanticCatalog.DAY,
                FinancialSemanticCatalog.WEEK,
                FinancialSemanticCatalog.MONTH,
                FinancialSemanticCatalog.YEAR
            );
    }

    @Test
    void keepsCreditDebtIndependentFromCreditLimitAndPreservesTransferPolicies() {
        FinancialMetricDefinition debt = metric("CREDIT_CARD_DEBT");
        FinancialMetricDefinition limit = metric("CREDIT_LIMIT");
        FinancialMetricDefinition income = metric("INCOME_TOTAL");
        FinancialMetricDefinition movement = metric("ACCOUNT_INFLOW");
        FinancialMetricDefinition transfers = metric("INTERNAL_TRANSFER_COUNT");

        assertThat(debt.applicability().eligibleAccountTypes()).containsExactly(AccountType.CREDIT_CARD);
        assertThat(debt.historicalAvailabilityConstraints()).containsExactly(
            HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE
        );
        assertThat(debt.historicalAvailabilityConstraints()).doesNotContain(HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT);
        assertThat(limit.historicalAvailabilityConstraints()).contains(HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT);
        assertThat(income.internalTransferTreatment()).isEqualTo(InternalTransferTreatment.EXCLUDE_LINKED_LEGS);
        assertThat(movement.internalTransferTreatment()).isEqualTo(InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS);
        assertThat(transfers.internalTransferTreatment()).isEqualTo(InternalTransferTreatment.COUNT_RELATIONSHIPS);
    }

    @Test
    void makesMonetaryCurrencyPartitioningAnExplicitResultContract() {
        FinancialMetricResultShape incomeShape = FinancialMetricResultShape.forMetric(metric("INCOME_TOTAL"));
        FinancialMetricResultShape countShape = FinancialMetricResultShape.forMetric(metric("ECONOMIC_TRANSACTION_COUNT"));

        assertThat(incomeShape.unit()).isEqualTo(FinancialMetricUnit.MONEY);
        assertThat(incomeShape.explicitCurrencyFacetRequired()).isTrue();
        assertThat(incomeShape.userGroupingCapacityConsumed()).isFalse();
        assertThat(countShape.unit()).isEqualTo(FinancialMetricUnit.COUNT);
        assertThat(countShape.explicitCurrencyFacetRequired()).isFalse();
    }

    @Test
    void exposesPerMetricApplicabilityWithoutGloballyCollapsingAccountTypes() {
        assertThat(metric("ACCOUNT_BALANCE").applicability().eligibleAccountTypes()).containsExactlyInAnyOrder(
            AccountType.DEBIT,
            AccountType.CASH,
            AccountType.INVESTMENT
        );
        assertThat(metric("ACCOUNT_SIGNED_POSITION").applicability().eligibleAccountTypes()).containsExactlyInAnyOrder(
            AccountType.values()
        );
    }

    private FinancialMetricDefinition metric(String id) {
        return catalog.metric(id).orElseThrow();
    }

    private FinancialMetricTimePolicy timePolicy() {
        return new FinancialMetricTimePolicy(Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC));
    }
}
