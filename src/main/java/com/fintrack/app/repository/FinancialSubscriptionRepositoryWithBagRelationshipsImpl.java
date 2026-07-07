package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialSubscription;
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
public class FinancialSubscriptionRepositoryWithBagRelationshipsImpl implements FinancialSubscriptionRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String FINANCIALSUBSCRIPTIONS_PARAMETER = "financialSubscriptions";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<FinancialSubscription> fetchBagRelationships(Optional<FinancialSubscription> financialSubscription) {
        return financialSubscription.map(this::fetchTags);
    }

    @Override
    public Page<FinancialSubscription> fetchBagRelationships(Page<FinancialSubscription> financialSubscriptions) {
        return new PageImpl<>(
            fetchBagRelationships(financialSubscriptions.getContent()),
            financialSubscriptions.getPageable(),
            financialSubscriptions.getTotalElements()
        );
    }

    @Override
    public List<FinancialSubscription> fetchBagRelationships(List<FinancialSubscription> financialSubscriptions) {
        return Optional.of(financialSubscriptions).map(this::fetchTags).orElse(Collections.emptyList());
    }

    FinancialSubscription fetchTags(FinancialSubscription result) {
        return entityManager
            .createQuery(
                "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.tags where financialSubscription.id = :id",
                FinancialSubscription.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<FinancialSubscription> fetchTags(List<FinancialSubscription> financialSubscriptions) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, financialSubscriptions.size()).forEach(index -> order.put(financialSubscriptions.get(index).getId(), index));
        List<FinancialSubscription> result = entityManager
            .createQuery(
                "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.tags where financialSubscription in :financialSubscriptions",
                FinancialSubscription.class
            )
            .setParameter(FINANCIALSUBSCRIPTIONS_PARAMETER, financialSubscriptions)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
