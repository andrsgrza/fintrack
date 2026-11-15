package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import java.util.Set;

/**
 * The one persisted-presentation contract for canonical semantic queries. It intentionally derives every decision
 * from the AST shape and governed field semantics, never from widget IDs, dashboard templates, or metric names.
 */
public final class SemanticVisualizationCompatibility {

    private static final Set<SemanticDataset> POINT_IN_TIME_DATASETS = Set.of(
        SemanticDataset.FINANCIAL_POSITION_SNAPSHOT,
        SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
        SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT
    );

    private SemanticVisualizationCompatibility() {}

    public static boolean isCompatible(SemanticQueryDefinition definition, PersonalDashboardVisualizationType visualization) {
        boolean temporalGrouping = definition.groupBy().stream().anyMatch(SemanticVisualizationCompatibility::isTemporal);
        boolean pointInTime = POINT_IN_TIME_DATASETS.contains(definition.dataset());
        boolean additive = definition
            .projections()
            .stream()
            .noneMatch(projection -> projection.function() == SemanticAggregateFunction.AVG);
        boolean tags = definition.groupBy().contains(SemanticField.TAG_ID);
        boolean percentage = definition
            .projections()
            .stream()
            .anyMatch(projection -> projection.field() == SemanticField.CREDIT_UTILIZATION_PERCENT);
        int groups = definition.groupBy().size();
        boolean comparableGroups = groups > 1 || definition.projections().size() > 1;

        return switch (visualization) {
            case TABLE -> true;
            case KPI -> groups == 0;
            case LINE -> temporalGrouping;
            case BAR -> groups > 0;
            case GROUPED_BAR -> comparableGroups;
            case STACKED_BAR -> comparableGroups && additive && !tags && !(pointInTime && temporalGrouping);
            case DONUT -> groups == 1 && !temporalGrouping && definition.projections().size() == 1 && additive && !tags && !percentage;
        };
    }

    public static boolean isTemporal(SemanticField field) {
        return switch (field) {
            case TRANSACTION_DATE,
                TRANSACTION_DAY,
                TRANSACTION_WEEK,
                TRANSACTION_MONTH,
                TRANSACTION_YEAR,
                OBSERVATION_DATE,
                OBSERVATION_MONTH,
                TRANSFER_DATE,
                TRANSFER_DAY,
                TRANSFER_WEEK,
                TRANSFER_MONTH,
                TRANSFER_YEAR -> true;
            default -> false;
        };
    }
}
