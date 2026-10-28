package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.util.List;

/** Server-resolved owner and account scope. It is transient and cannot be supplied by a client. */
record FinancialAnalyticsExecutionScope(
    ValidatedFinancialAnalyticsRequest validatedRequest,
    String ownerLogin,
    List<FinancialAccount> accounts
) {
    FinancialAnalyticsExecutionScope {
        accounts = List.copyOf(accounts);
    }
}
