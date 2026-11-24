package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.Budget;
import java.time.LocalDate;

/**
 * Server-only execution authority for a canonical semantic query. It deliberately accepts a persisted owner
 * aggregate, never an owner identifier supplied by a request body. Budget evaluation can therefore run without an
 * interactive SecurityContext while retaining the same owner-scoped validator and executor used by Analytics.
 */
public final class SemanticAnalyticsTrustedOwnerContext {

    private final String ownerLogin;
    private final LocalDate maximumObservationDate;

    private SemanticAnalyticsTrustedOwnerContext(String ownerLogin, LocalDate maximumObservationDate) {
        this.ownerLogin = ownerLogin;
        this.maximumObservationDate = maximumObservationDate;
    }

    public static SemanticAnalyticsTrustedOwnerContext forBudget(Budget budget, LocalDate maximumObservationDate) {
        if (
            budget == null ||
            budget.getId() == null ||
            budget.getUser() == null ||
            budget.getUser().getLogin() == null ||
            budget.getUser().getLogin().isBlank()
        ) {
            throw new IllegalArgumentException("A persisted owner is required.");
        }
        if (maximumObservationDate == null) throw new IllegalArgumentException("A trusted local observation date is required.");
        return new SemanticAnalyticsTrustedOwnerContext(budget.getUser().getLogin(), maximumObservationDate);
    }

    String ownerLogin() {
        return ownerLogin;
    }

    LocalDate maximumObservationDate() {
        return maximumObservationDate;
    }
}
