package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface TransactionRuleRepositoryWithBagRelationships {
    Optional<TransactionRule> fetchBagRelationships(Optional<TransactionRule> transactionRule);

    List<TransactionRule> fetchBagRelationships(List<TransactionRule> transactionRules);

    Page<TransactionRule> fetchBagRelationships(Page<TransactionRule> transactionRules);
}
