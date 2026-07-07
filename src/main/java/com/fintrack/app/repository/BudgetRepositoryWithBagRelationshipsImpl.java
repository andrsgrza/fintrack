package com.fintrack.app.repository;

import com.fintrack.app.domain.Budget;
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
public class BudgetRepositoryWithBagRelationshipsImpl implements BudgetRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String BUDGETS_PARAMETER = "budgets";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Budget> fetchBagRelationships(Optional<Budget> budget) {
        return budget.map(this::fetchAccounts).map(this::fetchCategories).map(this::fetchTags);
    }

    @Override
    public Page<Budget> fetchBagRelationships(Page<Budget> budgets) {
        return new PageImpl<>(fetchBagRelationships(budgets.getContent()), budgets.getPageable(), budgets.getTotalElements());
    }

    @Override
    public List<Budget> fetchBagRelationships(List<Budget> budgets) {
        return Optional.of(budgets)
            .map(this::fetchAccounts)
            .map(this::fetchCategories)
            .map(this::fetchTags)
            .orElse(Collections.emptyList());
    }

    Budget fetchAccounts(Budget result) {
        return entityManager
            .createQuery("select budget from Budget budget left join fetch budget.accounts where budget.id = :id", Budget.class)
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<Budget> fetchAccounts(List<Budget> budgets) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, budgets.size()).forEach(index -> order.put(budgets.get(index).getId(), index));
        List<Budget> result = entityManager
            .createQuery("select budget from Budget budget left join fetch budget.accounts where budget in :budgets", Budget.class)
            .setParameter(BUDGETS_PARAMETER, budgets)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }

    Budget fetchCategories(Budget result) {
        return entityManager
            .createQuery("select budget from Budget budget left join fetch budget.categories where budget.id = :id", Budget.class)
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<Budget> fetchCategories(List<Budget> budgets) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, budgets.size()).forEach(index -> order.put(budgets.get(index).getId(), index));
        List<Budget> result = entityManager
            .createQuery("select budget from Budget budget left join fetch budget.categories where budget in :budgets", Budget.class)
            .setParameter(BUDGETS_PARAMETER, budgets)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }

    Budget fetchTags(Budget result) {
        return entityManager
            .createQuery("select budget from Budget budget left join fetch budget.tags where budget.id = :id", Budget.class)
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<Budget> fetchTags(List<Budget> budgets) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, budgets.size()).forEach(index -> order.put(budgets.get(index).getId(), index));
        List<Budget> result = entityManager
            .createQuery("select budget from Budget budget left join fetch budget.tags where budget in :budgets", Budget.class)
            .setParameter(BUDGETS_PARAMETER, budgets)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
