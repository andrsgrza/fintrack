package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetTimeZoneAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetTimeZoneAssignmentRepository extends JpaRepository<BudgetTimeZoneAssignment, Long> {
    List<BudgetTimeZoneAssignment> findAllByBudgetIdOrderByEffectivePeriodStartAsc(Long budgetId);

    void deleteByBudgetId(Long budgetId);
}
