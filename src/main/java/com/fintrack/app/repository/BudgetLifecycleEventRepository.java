package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetLifecycleEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetLifecycleEventRepository extends JpaRepository<BudgetLifecycleEvent, Long> {
    @Query("select event from BudgetLifecycleEvent event where event.budget.id = :budgetId order by event.sequenceNumber asc")
    List<BudgetLifecycleEvent> findAllByBudgetIdOrderBySequenceNumber(@Param("budgetId") Long budgetId);

    @Query("select event from BudgetLifecycleEvent event where event.id = :id and event.budget.id = :budgetId")
    Optional<BudgetLifecycleEvent> findOneByIdAndBudgetId(@Param("id") Long id, @Param("budgetId") Long budgetId);
}
