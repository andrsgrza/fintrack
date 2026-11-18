package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetNotification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetNotificationRepository extends JpaRepository<BudgetNotification, Long> {
    Optional<BudgetNotification> findOneByOutboxEventId(Long outboxEventId);

    void deleteByBudgetId(Long budgetId);

    @EntityGraph(attributePaths = "budget")
    Page<BudgetNotification> findAllByUserLoginOrderByEventAtDescIdDesc(String login, Pageable pageable);

    @EntityGraph(attributePaths = "budget")
    Page<BudgetNotification> findAllByUserLoginAndReadAtIsNullOrderByEventAtDescIdDesc(String login, Pageable pageable);

    long countByUserLoginAndReadAtIsNull(String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from BudgetNotification notification where notification.id = :id and notification.user.login = :login")
    Optional<BudgetNotification> findOneOwnedForReadMutation(@Param("id") Long id, @Param("login") String login);

    /**
     * The materialization cutoff is captured before this update. Notifications materialized afterwards are deliberately
     * excluded, even if they arrive while the bulk update is running.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update BudgetNotification notification set notification.readAt = :readAt " +
        "where notification.user.login = :login and notification.readAt is null and notification.materializedAt <= :cutoff"
    )
    int markAllExistingUnreadAsRead(@Param("login") String login, @Param("readAt") Instant readAt, @Param("cutoff") Instant cutoff);
}
