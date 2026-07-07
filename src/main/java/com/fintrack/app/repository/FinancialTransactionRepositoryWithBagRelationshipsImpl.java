package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialTransaction;
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
public class FinancialTransactionRepositoryWithBagRelationshipsImpl implements FinancialTransactionRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String FINANCIALTRANSACTIONS_PARAMETER = "financialTransactions";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<FinancialTransaction> fetchBagRelationships(Optional<FinancialTransaction> financialTransaction) {
        return financialTransaction.map(this::fetchTags);
    }

    @Override
    public Page<FinancialTransaction> fetchBagRelationships(Page<FinancialTransaction> financialTransactions) {
        return new PageImpl<>(
            fetchBagRelationships(financialTransactions.getContent()),
            financialTransactions.getPageable(),
            financialTransactions.getTotalElements()
        );
    }

    @Override
    public List<FinancialTransaction> fetchBagRelationships(List<FinancialTransaction> financialTransactions) {
        return Optional.of(financialTransactions).map(this::fetchTags).orElse(Collections.emptyList());
    }

    FinancialTransaction fetchTags(FinancialTransaction result) {
        return entityManager
            .createQuery(
                "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.tags where financialTransaction.id = :id",
                FinancialTransaction.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<FinancialTransaction> fetchTags(List<FinancialTransaction> financialTransactions) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, financialTransactions.size()).forEach(index -> order.put(financialTransactions.get(index).getId(), index));
        List<FinancialTransaction> result = entityManager
            .createQuery(
                "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.tags where financialTransaction in :financialTransactions",
                FinancialTransaction.class
            )
            .setParameter(FINANCIALTRANSACTIONS_PARAMETER, financialTransactions)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
