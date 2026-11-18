package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetNotificationPreference;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetNotificationPreferenceRepository extends JpaRepository<BudgetNotificationPreference, Long> {
    @Query(
        "select distinct preference from BudgetNotificationPreference preference " +
        "join fetch preference.budget budget join fetch budget.user " +
        "left join fetch budget.revisions revision left join fetch revision.accounts left join fetch revision.categories left join fetch revision.tags " +
        "left join fetch preference.thresholds " +
        "where budget.user.login = :login and preference.percentageAlertsEnabled = true"
    )
    List<BudgetNotificationPreference> findAllEnabledForOwnerEvaluation(@Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        // PostgreSQL cannot lock the nullable side of a collection fetch join. The preference is the sole
        // mutation lock here; thresholds remain available lazily inside the surrounding transaction.
        "select preference from BudgetNotificationPreference preference " +
        "join fetch preference.budget budget " +
        "where budget.id = :budgetId and budget.user.login = :login"
    )
    Optional<BudgetNotificationPreference> findOneForOwnedMutation(@Param("budgetId") Long budgetId, @Param("login") String login);

    @Query("select preference from BudgetNotificationPreference preference where preference.budget.id = :budgetId")
    Optional<BudgetNotificationPreference> findOneByBudgetId(@Param("budgetId") Long budgetId);

    void deleteByBudgetId(Long budgetId);
}
