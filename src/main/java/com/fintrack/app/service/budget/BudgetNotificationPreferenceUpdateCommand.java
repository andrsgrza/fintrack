package com.fintrack.app.service.budget;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;

/** Full-replacement command for optional, mutable notification preferences. */
public record BudgetNotificationPreferenceUpdateCommand(
    Long expectedVersion,
    @NotNull Boolean percentageAlertsEnabled,
    Set<BigDecimal> thresholdPercentages,
    @NotNull Boolean periodCloseEnabled
) {}
