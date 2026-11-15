package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticVisualizationCompatibilityTest {

    @Test
    void appliesTheSameShapeRulesToScalarAndGroupedQueries() {
        SemanticQueryDefinition scalar = ledger(List.of(total()), List.of());
        SemanticQueryDefinition category = ledger(List.of(total()), List.of(SemanticField.CATEGORY_ROOT));
        SemanticQueryDefinition categoryAndMonth = ledger(
            List.of(total()),
            List.of(SemanticField.CATEGORY_ROOT, SemanticField.TRANSACTION_MONTH)
        );
        SemanticQueryDefinition monthAndCategory = ledger(
            List.of(total()),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ROOT)
        );

        assertThat(SemanticVisualizationCompatibility.isCompatible(scalar, PersonalDashboardVisualizationType.KPI)).isTrue();
        assertThat(SemanticVisualizationCompatibility.isCompatible(category, PersonalDashboardVisualizationType.KPI)).isFalse();
        assertThat(SemanticVisualizationCompatibility.isCompatible(category, PersonalDashboardVisualizationType.BAR)).isTrue();
        assertThat(SemanticVisualizationCompatibility.isCompatible(categoryAndMonth, PersonalDashboardVisualizationType.LINE)).isTrue();
        assertThat(SemanticVisualizationCompatibility.isCompatible(monthAndCategory, PersonalDashboardVisualizationType.LINE)).isTrue();
    }

    @Test
    void protectsNonExclusiveAndNonAdditiveShapesFromMisleadingPresentations() {
        SemanticQueryDefinition tags = ledger(List.of(total()), List.of(SemanticField.TAG_ID));
        SemanticQueryDefinition average = ledger(
            List.of(new SemanticAggregateProjection("average", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_AMOUNT)),
            List.of(SemanticField.CATEGORY_ROOT)
        );
        SemanticQueryDefinition historicalPosition = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            SemanticField.OBSERVATION_DATE,
            List.of(new SemanticAggregateProjection("balance", SemanticAggregateFunction.SUM, SemanticField.POSITION_BALANCE)),
            List.of(),
            List.of(SemanticField.OBSERVATION_MONTH, SemanticField.ACCOUNT_ID),
            List.of(),
            100
        );
        SemanticQueryDefinition utilization = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
            SemanticField.OBSERVATION_DATE,
            List.of(
                new SemanticAggregateProjection("utilization", SemanticAggregateFunction.SUM, SemanticField.CREDIT_UTILIZATION_PERCENT)
            ),
            List.of(),
            List.of(SemanticField.ACCOUNT_ID),
            List.of(),
            100
        );

        assertThat(SemanticVisualizationCompatibility.isCompatible(tags, PersonalDashboardVisualizationType.DONUT)).isFalse();
        assertThat(SemanticVisualizationCompatibility.isCompatible(tags, PersonalDashboardVisualizationType.STACKED_BAR)).isFalse();
        assertThat(SemanticVisualizationCompatibility.isCompatible(average, PersonalDashboardVisualizationType.DONUT)).isFalse();
        assertThat(
            SemanticVisualizationCompatibility.isCompatible(historicalPosition, PersonalDashboardVisualizationType.STACKED_BAR)
        ).isFalse();
        assertThat(SemanticVisualizationCompatibility.isCompatible(utilization, PersonalDashboardVisualizationType.DONUT)).isFalse();
        assertThat(SemanticVisualizationCompatibility.isCompatible(utilization, PersonalDashboardVisualizationType.BAR)).isTrue();
    }

    private SemanticQueryDefinition ledger(List<SemanticAggregateProjection> projections, List<SemanticField> groups) {
        return new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            projections,
            List.of(),
            groups,
            List.of(),
            100
        );
    }

    private SemanticAggregateProjection total() {
        return new SemanticAggregateProjection("total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT);
    }
}
