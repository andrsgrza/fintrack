package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetNotificationOutboxRepository extends JpaRepository<BudgetNotificationOutbox, Long> {
    @Query(
        "select event.id from BudgetNotificationOutbox event " +
        "where event.state = :state and event.nextAttemptAt <= :now order by event.eventAt asc, event.id asc"
    )
    List<Long> findPendingIdsForProcessing(
        @Param("state") BudgetNotificationOutboxState state,
        @Param("now") Instant now,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BudgetNotificationOutbox event where event.id = :id")
    java.util.Optional<BudgetNotificationOutbox> findOneForProcessing(@Param("id") Long id);

    void deleteByBudgetId(Long budgetId);
}
