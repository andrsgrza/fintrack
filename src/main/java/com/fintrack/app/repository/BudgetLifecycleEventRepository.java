package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetLifecycleEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetLifecycleEventRepository extends JpaRepository<BudgetLifecycleEvent, Long> {
    @Query("select event from BudgetLifecycleEvent event where event.budget.id = :budgetId order by event.sequenceNumber asc")
    List<BudgetLifecycleEvent> findAllByBudgetIdOrderBySequenceNumber(@Param("budgetId") Long budgetId);

    @Query(
        "select event from BudgetLifecycleEvent event left join fetch event.targetEvent " +
        "where event.budget.id in :budgetIds order by event.budget.id asc, event.sequenceNumber asc"
    )
    List<BudgetLifecycleEvent> findAllByBudgetIdInOrderByBudgetIdAndSequenceNumber(@Param("budgetIds") List<Long> budgetIds);

    @Query("select event from BudgetLifecycleEvent event where event.id = :id and event.budget.id = :budgetId")
    Optional<BudgetLifecycleEvent> findOneByIdAndBudgetId(@Param("id") Long id, @Param("budgetId") Long budgetId);

    /** Delete cancellation records before their self-referenced requested event. */
    @Modifying
    @Query("delete from BudgetLifecycleEvent event where event.budget.id = :budgetId and event.targetEvent is not null")
    void deleteTargetingByBudgetId(@Param("budgetId") Long budgetId);

    @Modifying
    @Query("delete from BudgetLifecycleEvent event where event.budget.id = :budgetId")
    void deleteByBudgetId(@Param("budgetId") Long budgetId);
}
