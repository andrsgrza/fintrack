package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

/** Backend-owned semantic definition for one governed metric. No definition contains executable SQL. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record FinancialMetricDefinition(
    String id,
    String displayNameEn,
    String displayNameEs,
    String descriptionEn,
    String descriptionEs,
    String subjectArea,
    FinancialMetricTimeMode timeMode,
    FinancialMetricExecutorSpecialization executorSpecialization,
    FinancialMetricApplicability applicability,
    Set<String> supportedDateBases,
    Set<String> compatibleGroupings,
    Set<String> supportedPredicates,
    FinancialMetricUnit unit,
    FinancialMetricCurrencyBehavior currencyBehavior,
    FinancialMetricAdditivity additivity,
    InternalTransferTreatment internalTransferTreatment,
    Set<HistoricalAvailabilityConstraint> historicalAvailabilityConstraints,
    List<String> knownWarnings
) {
    public FinancialMetricDefinition {
        supportedDateBases = Set.copyOf(supportedDateBases);
        compatibleGroupings = Set.copyOf(compatibleGroupings);
        supportedPredicates = Set.copyOf(supportedPredicates);
        historicalAvailabilityConstraints = Set.copyOf(historicalAvailabilityConstraints);
        knownWarnings = List.copyOf(knownWarnings);
    }
}
