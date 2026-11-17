package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetNotification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetNotificationRepository extends JpaRepository<BudgetNotification, Long> {
    Optional<BudgetNotification> findOneByOutboxEventId(Long outboxEventId);

    void deleteByBudgetId(Long budgetId);
}
