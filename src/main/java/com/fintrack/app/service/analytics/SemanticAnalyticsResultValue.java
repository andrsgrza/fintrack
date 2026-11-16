package com.fintrack.app.service.analytics;

import java.math.BigDecimal;

/**
 * {@code derived} identifies a scope-applicable result produced from a valid absence of observations rather than a
 * row returned by the financial query. It lets presentation say "no registered movements" without changing a real
 * financial value. This includes calendar completion and an otherwise empty scalar ledger query.
 */
public record SemanticAnalyticsResultValue(
    String alias,
    BigDecimal value,
    SemanticUnit unit,
    SemanticResultAvailability availability,
    boolean derived
) {}
