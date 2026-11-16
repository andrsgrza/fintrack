package com.fintrack.app.repository;

import com.fintrack.app.domain.BudgetRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Link cleanup leaves SELECTED modes intact so later evaluation can report incomplete configuration. */
@Repository
public interface BudgetRevisionRepository extends JpaRepository<BudgetRevision, Long> {
    @Query("select revision from BudgetRevision revision where revision.budget.id = :budgetId order by revision.revisionNumber asc")
    List<BudgetRevision> findAllByBudgetIdOrderByRevisionNumber(@Param("budgetId") Long budgetId);

    @Modifying
    @Query(value = "delete from rel_budget_revision__accounts where accounts_id = :accountId", nativeQuery = true)
    void deleteAccountLinksByAccountId(@Param("accountId") Long accountId);

    @Modifying
    @Query(value = "delete from rel_budget_revision__categories where categories_id = :categoryId", nativeQuery = true)
    void deleteCategoryLinksByCategoryId(@Param("categoryId") Long categoryId);

    @Modifying
    @Query(value = "delete from rel_budget_revision__tags where tags_id = :tagId", nativeQuery = true)
    void deleteTagLinksByTagId(@Param("tagId") Long tagId);

    @Query(value = "select count(*) from rel_budget_revision__accounts where accounts_id = :accountId", nativeQuery = true)
    long countAccountLinksByAccountId(@Param("accountId") Long accountId);
}
