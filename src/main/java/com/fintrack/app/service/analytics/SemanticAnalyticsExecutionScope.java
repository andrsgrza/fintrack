package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.util.List;

/** Resolved server-only scope. Clients cannot provide an owner or bypass account authorization. */
record SemanticAnalyticsExecutionScope(
    SemanticQueryDefinition definition,
    ResolvedVisualizationContext context,
    String ownerLogin,
    List<FinancialAccount> accounts,
    SemanticAnalyticsReferenceCatalog references
) {
    SemanticAnalyticsExecutionScope {
        accounts = List.copyOf(accounts);
    }

    SemanticAnalyticsExecutionScope(
        SemanticQueryDefinition definition,
        ResolvedVisualizationContext context,
        String ownerLogin,
        List<FinancialAccount> accounts
    ) {
        this(definition, context, ownerLogin, accounts, new SemanticAnalyticsReferenceCatalog(List.of(), List.of()));
    }
}
