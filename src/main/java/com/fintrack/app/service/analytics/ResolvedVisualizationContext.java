package com.fintrack.app.service.analytics;

import java.time.LocalDate;
import java.util.List;

/** Backend-resolved execution context used by a future executor, never persisted as a frozen relative period. */
public record ResolvedVisualizationContext(LocalDate from, LocalDate through, List<Long> accountIds, List<String> currencyCodes) {
    public ResolvedVisualizationContext {
        accountIds = List.copyOf(accountIds);
        currencyCodes = List.copyOf(currencyCodes);
    }
}
