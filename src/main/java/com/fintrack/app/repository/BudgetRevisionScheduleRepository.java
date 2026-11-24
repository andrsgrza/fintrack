package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetRevisionSchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRevisionScheduleRepository extends JpaRepository<BudgetRevisionSchedule, Long> {
    @Query(
        "select schedule from BudgetRevisionSchedule schedule left join fetch schedule.revision " +
        "where schedule.budget.id = :budgetId order by schedule.sequenceNumber asc"
    )
    List<BudgetRevisionSchedule> findAllByBudgetIdOrderBySequenceNumber(@Param("budgetId") Long budgetId);

    @Query(
        "select schedule from BudgetRevisionSchedule schedule left join fetch schedule.revision " +
        "where schedule.budget.id in :budgetIds order by schedule.budget.id asc, schedule.sequenceNumber asc"
    )
    List<BudgetRevisionSchedule> findAllByBudgetIdInOrderByBudgetIdAndSequenceNumber(@Param("budgetIds") List<Long> budgetIds);

    @Query(
        "select schedule from BudgetRevisionSchedule schedule left join fetch schedule.revision " +
        "where schedule.id = :id and schedule.budget.id = :budgetId"
    )
    Optional<BudgetRevisionSchedule> findOneByIdAndBudgetId(@Param("id") Long id, @Param("budgetId") Long budgetId);

    /** Delete cancellation records before their self-referenced scheduled record. */
    @Modifying
    @Query("delete from BudgetRevisionSchedule schedule where schedule.budget.id = :budgetId and schedule.targetSchedule is not null")
    void deleteTargetingByBudgetId(@Param("budgetId") Long budgetId);

    @Modifying
    @Query("delete from BudgetRevisionSchedule schedule where schedule.budget.id = :budgetId")
    void deleteByBudgetId(@Param("budgetId") Long budgetId);
}
