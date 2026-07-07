package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface FinancialSubscriptionRepositoryWithBagRelationships {
    Optional<FinancialSubscription> fetchBagRelationships(Optional<FinancialSubscription> financialSubscription);

    List<FinancialSubscription> fetchBagRelationships(List<FinancialSubscription> financialSubscriptions);

    Page<FinancialSubscription> fetchBagRelationships(Page<FinancialSubscription> financialSubscriptions);
}
