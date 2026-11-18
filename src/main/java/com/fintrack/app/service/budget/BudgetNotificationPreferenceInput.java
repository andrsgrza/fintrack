package com.fintrack.app.service.budget;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Optional notification settings supplied while a Budget is created. The settings deliberately remain outside the
 * immutable financial revision: they control delivery only and never change a Budget's financial meaning.
 */
public record BudgetNotificationPreferenceInput(
    Boolean percentageAlertsEnabled,
    Set<BigDecimal> thresholdPercentages,
    Boolean periodCloseEnabled
) {}
