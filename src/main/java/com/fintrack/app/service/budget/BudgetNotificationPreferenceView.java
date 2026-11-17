package com.fintrack.app.service.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read model intentionally keeps absent preferences distinct from a disabled persisted preference. */
public record BudgetNotificationPreferenceView(
    Long budgetId,
    boolean configured,
    Long version,
    boolean percentageAlertsEnabled,
    List<BigDecimal> thresholdPercentages,
    boolean periodCloseEnabled,
    Instant updatedAt
) {}
