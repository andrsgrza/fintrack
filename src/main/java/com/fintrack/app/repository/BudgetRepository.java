package com.fintrack.app.repository;

import com.fintrack.app.domain.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Budget entity.
 *
 * When extending this class, extend BudgetRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface BudgetRepository
    extends BudgetRepositoryWithBagRelationships, JpaRepository<Budget, Long>, JpaSpecificationExecutor<Budget> {
    @Query("select budget from Budget budget where budget.user.login = ?#{authentication.name}")
    List<Budget> findByUserIsCurrentUser();

    default Optional<Budget> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationships(id));
    }

    default Optional<Budget> findOneWithEagerRelationshipsByIdAndUserLogin(Long id, String login) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationshipsByIdAndUserLogin(id, login));
    }

    default List<Budget> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<Budget> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    default Page<Budget> findAllWithEagerRelationshipsByUserLogin(String login, Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationshipsByUserLogin(login, pageable));
    }

    @Query(value = "select budget from Budget budget left join fetch budget.user", countQuery = "select count(budget) from Budget budget")
    Page<Budget> findAllWithToOneRelationships(Pageable pageable);

    @Query("select budget from Budget budget left join fetch budget.user")
    List<Budget> findAllWithToOneRelationships();

    @Query("select budget from Budget budget left join fetch budget.user where budget.id =:id")
    Optional<Budget> findOneWithToOneRelationships(@Param("id") Long id);

    @Query("select budget from Budget budget where budget.id = :id and budget.user.login = :login")
    Optional<Budget> findOneByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query("select budget from Budget budget left join fetch budget.user where budget.id = :id and budget.user.login = :login")
    Optional<Budget> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        value = "select budget from Budget budget left join fetch budget.user where budget.user.login = :login",
        countQuery = "select count(budget) from Budget budget where budget.user.login = :login"
    )
    Page<Budget> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query("select budget from Budget budget left join fetch budget.user where budget.user.login = :login")
    List<Budget> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from rel_budget__accounts where budget_id = :budgetId", nativeQuery = true)
    void deleteAccountLinksByBudgetId(@Param("budgetId") Long budgetId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from rel_budget__categories where budget_id = :budgetId", nativeQuery = true)
    void deleteCategoryLinksByBudgetId(@Param("budgetId") Long budgetId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from rel_budget__tags where budget_id = :budgetId", nativeQuery = true)
    void deleteTagLinksByBudgetId(@Param("budgetId") Long budgetId);
}
