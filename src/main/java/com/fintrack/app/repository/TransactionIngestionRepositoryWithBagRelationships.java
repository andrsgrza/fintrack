package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface TransactionIngestionRepositoryWithBagRelationships {
    Optional<TransactionIngestion> fetchBagRelationships(Optional<TransactionIngestion> transactionIngestion);

    List<TransactionIngestion> fetchBagRelationships(List<TransactionIngestion> transactionIngestions);

    Page<TransactionIngestion> fetchBagRelationships(Page<TransactionIngestion> transactionIngestions);
}
