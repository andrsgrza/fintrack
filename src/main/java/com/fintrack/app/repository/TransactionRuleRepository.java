package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionRule entity.
 *
 * When extending this class, extend TransactionRuleRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface TransactionRuleRepository
    extends TransactionRuleRepositoryWithBagRelationships, JpaRepository<TransactionRule, Long>, JpaSpecificationExecutor<TransactionRule> {
    @Query("select transactionRule from TransactionRule transactionRule where transactionRule.user.login = ?#{authentication.name}")
    List<TransactionRule> findByUserIsCurrentUser();

    default Optional<TransactionRule> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationships(id));
    }

    default List<TransactionRule> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<TransactionRule> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    @Query(
        value = "select transactionRule from TransactionRule transactionRule left join fetch transactionRule.user left join fetch transactionRule.resultingCategory left join fetch transactionRule.resultingFinancialSubscription",
        countQuery = "select count(transactionRule) from TransactionRule transactionRule"
    )
    Page<TransactionRule> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select transactionRule from TransactionRule transactionRule left join fetch transactionRule.user left join fetch transactionRule.resultingCategory left join fetch transactionRule.resultingFinancialSubscription"
    )
    List<TransactionRule> findAllWithToOneRelationships();

    @Query(
        "select transactionRule from TransactionRule transactionRule left join fetch transactionRule.user left join fetch transactionRule.resultingCategory left join fetch transactionRule.resultingFinancialSubscription where transactionRule.id =:id"
    )
    Optional<TransactionRule> findOneWithToOneRelationships(@Param("id") Long id);
}
