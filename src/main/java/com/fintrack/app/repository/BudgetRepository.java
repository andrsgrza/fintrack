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

    default List<Budget> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<Budget> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    @Query(value = "select budget from Budget budget left join fetch budget.user", countQuery = "select count(budget) from Budget budget")
    Page<Budget> findAllWithToOneRelationships(Pageable pageable);

    @Query("select budget from Budget budget left join fetch budget.user")
    List<Budget> findAllWithToOneRelationships();

    @Query("select budget from Budget budget left join fetch budget.user where budget.id =:id")
    Optional<Budget> findOneWithToOneRelationships(@Param("id") Long id);
}
