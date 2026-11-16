package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetCommandReceiptRepository extends JpaRepository<BudgetCommandReceipt, Long> {
    @Query(
        "select receipt from BudgetCommandReceipt receipt where receipt.budget.id = :budgetId " +
        "and receipt.commandType = :commandType and receipt.clientRequestId = :clientRequestId"
    )
    Optional<BudgetCommandReceipt> findByBudgetCommandAndRequestId(
        @Param("budgetId") Long budgetId,
        @Param("commandType") String commandType,
        @Param("clientRequestId") String clientRequestId
    );
}
