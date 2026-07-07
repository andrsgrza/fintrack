package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface FinancialTransactionRepositoryWithBagRelationships {
    Optional<FinancialTransaction> fetchBagRelationships(Optional<FinancialTransaction> financialTransaction);

    List<FinancialTransaction> fetchBagRelationships(List<FinancialTransaction> financialTransactions);

    Page<FinancialTransaction> fetchBagRelationships(Page<FinancialTransaction> financialTransactions);
}
