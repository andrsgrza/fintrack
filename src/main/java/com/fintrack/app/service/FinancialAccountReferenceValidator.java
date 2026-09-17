package com.fintrack.app.service;

import com.fintrack.app.domain.FinancialAccount;

/**
 * Guards creation or reassignment of a FinancialAccount reference in product workflows.
 *
 * <p>It must not be used to revalidate an already persisted reference while completing historical work.
 */
public final class FinancialAccountReferenceValidator {

    private FinancialAccountReferenceValidator() {}

    public static void validateActiveForNewReference(FinancialAccount account) {
        if (account == null || !Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalArgumentException("Financial account must be active for new references");
        }
    }
}
