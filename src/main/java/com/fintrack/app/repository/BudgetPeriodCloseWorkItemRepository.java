package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetPeriodCloseWorkItem;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetPeriodCloseWorkItemRepository extends JpaRepository<BudgetPeriodCloseWorkItem, Long> {
    boolean existsByBudgetIdAndPeriodStart(Long budgetId, LocalDate periodStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select work from BudgetPeriodCloseWorkItem work where work.id = :id")
    Optional<BudgetPeriodCloseWorkItem> findOneForProcessing(@Param("id") Long id);

    @Query("select work from BudgetPeriodCloseWorkItem work where work.budget.id = :budgetId order by work.periodStart asc")
    List<BudgetPeriodCloseWorkItem> findAllByBudgetIdOrderByPeriodStartAsc(@Param("budgetId") Long budgetId);

    @Query(
        "select min(work.logicalClosedAt) from BudgetPeriodCloseWorkItem work " +
        "where work.state in ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED')"
    )
    Optional<Instant> findOldestOutstandingLogicalClosedAt();

    void deleteByBudgetId(Long budgetId);
}
