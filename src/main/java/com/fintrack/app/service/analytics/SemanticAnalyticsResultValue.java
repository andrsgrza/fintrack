package com.fintrack.app.service.analytics;

import java.math.BigDecimal;

/**
 * {@code derived} identifies a calendar row produced from a valid empty period rather than a row returned by the
 * financial query. It lets presentation say "no registered movements" without changing a real financial value.
 */
public record SemanticAnalyticsResultValue(
    String alias,
    BigDecimal value,
    SemanticUnit unit,
    SemanticResultAvailability availability,
    boolean derived
) {}
