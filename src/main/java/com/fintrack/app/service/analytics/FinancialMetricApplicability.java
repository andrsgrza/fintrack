package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.enumeration.AccountType;
import java.util.Set;

/** Account-type eligibility is evaluated per metric after the caller's account scope is resolved. */
public record FinancialMetricApplicability(Set<AccountType> eligibleAccountTypes) {
    public FinancialMetricApplicability {
        eligibleAccountTypes = Set.copyOf(eligibleAccountTypes);
    }

    public boolean supports(AccountType accountType) {
        return eligibleAccountTypes.contains(accountType);
    }
}
