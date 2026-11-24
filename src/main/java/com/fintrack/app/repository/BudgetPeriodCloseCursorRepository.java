package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetPeriodCloseCursor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetPeriodCloseCursorRepository extends JpaRepository<BudgetPeriodCloseCursor, Long> {
    Optional<BudgetPeriodCloseCursor> findOneByBudgetId(Long budgetId);

    @Query("select cursor from BudgetPeriodCloseCursor cursor join fetch cursor.budget budget join fetch budget.user where cursor.id = :id")
    Optional<BudgetPeriodCloseCursor> findOneWithBudgetAndUser(@Param("id") Long id);

    void deleteByBudgetId(Long budgetId);
}
