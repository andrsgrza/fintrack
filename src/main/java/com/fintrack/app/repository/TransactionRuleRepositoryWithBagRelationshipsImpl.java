package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionRule;
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
public class TransactionRuleRepositoryWithBagRelationshipsImpl implements TransactionRuleRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String TRANSACTIONRULES_PARAMETER = "transactionRules";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<TransactionRule> fetchBagRelationships(Optional<TransactionRule> transactionRule) {
        return transactionRule.map(this::fetchResultingTags);
    }

    @Override
    public Page<TransactionRule> fetchBagRelationships(Page<TransactionRule> transactionRules) {
        return new PageImpl<>(
            fetchBagRelationships(transactionRules.getContent()),
            transactionRules.getPageable(),
            transactionRules.getTotalElements()
        );
    }

    @Override
    public List<TransactionRule> fetchBagRelationships(List<TransactionRule> transactionRules) {
        return Optional.of(transactionRules).map(this::fetchResultingTags).orElse(Collections.emptyList());
    }

    TransactionRule fetchResultingTags(TransactionRule result) {
        return entityManager
            .createQuery(
                "select transactionRule from TransactionRule transactionRule left join fetch transactionRule.resultingTags where transactionRule.id = :id",
                TransactionRule.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<TransactionRule> fetchResultingTags(List<TransactionRule> transactionRules) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, transactionRules.size()).forEach(index -> order.put(transactionRules.get(index).getId(), index));
        List<TransactionRule> result = entityManager
            .createQuery(
                "select transactionRule from TransactionRule transactionRule left join fetch transactionRule.resultingTags where transactionRule in :transactionRules",
                TransactionRule.class
            )
            .setParameter(TRANSACTIONRULES_PARAMETER, transactionRules)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
