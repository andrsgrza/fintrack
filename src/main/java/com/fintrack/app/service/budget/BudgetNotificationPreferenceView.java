package com.fintrack.app.service.budget;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read model intentionally keeps absent preferences distinct from a disabled persisted preference. */
public record BudgetNotificationPreferenceView(
    Long budgetId,
    boolean configured,
    Long version,
    boolean percentageAlertsEnabled,
    @JsonSerialize(contentUsing = BudgetDecimalJson.Serializer.class) List<BigDecimal> thresholdPercentages,
    boolean periodCloseEnabled,
    Instant periodCloseEnabledSince,
    Instant updatedAt
) {}
