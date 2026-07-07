package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionIngestion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Utility repository to load bag relationships based on https://vladmihalcea.com/hibernate-multiplebagfetchexception/
 */
public class TransactionIngestionRepositoryWithBagRelationshipsImpl implements TransactionIngestionRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String TRANSACTIONINGESTIONS_PARAMETER = "transactionIngestions";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<TransactionIngestion> fetchBagRelationships(Optional<TransactionIngestion> transactionIngestion) {
        return transactionIngestion.map(this::fetchAccounts);
    }

    @Override
    public Page<TransactionIngestion> fetchBagRelationships(Page<TransactionIngestion> transactionIngestions) {
        return new PageImpl<>(
            fetchBagRelationships(transactionIngestions.getContent()),
            transactionIngestions.getPageable(),
            transactionIngestions.getTotalElements()
        );
    }

    @Override
    public List<TransactionIngestion> fetchBagRelationships(List<TransactionIngestion> transactionIngestions) {
        return Optional.of(transactionIngestions).map(this::fetchAccounts).orElse(Collections.emptyList());
    }

    TransactionIngestion fetchAccounts(TransactionIngestion result) {
        return entityManager
            .createQuery(
                "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.accounts where transactionIngestion.id = :id",
                TransactionIngestion.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<TransactionIngestion> fetchAccounts(List<TransactionIngestion> transactionIngestions) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, transactionIngestions.size()).forEach(index -> order.put(transactionIngestions.get(index).getId(), index));
        List<TransactionIngestion> result = entityManager
            .createQuery(
                "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.accounts where transactionIngestion in :transactionIngestions",
                TransactionIngestion.class
            )
            .setParameter(TRANSACTIONINGESTIONS_PARAMETER, transactionIngestions)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
