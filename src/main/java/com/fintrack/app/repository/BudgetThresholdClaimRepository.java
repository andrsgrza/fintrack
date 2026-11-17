package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetThresholdClaim;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetThresholdClaimRepository extends JpaRepository<BudgetThresholdClaim, Long> {
    boolean existsByBudgetIdAndPeriodStartAndNormalizedPercentage(Long budgetId, LocalDate periodStart, BigDecimal normalizedPercentage);

    void deleteByBudgetId(Long budgetId);
}
