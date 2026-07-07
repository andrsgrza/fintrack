package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionRuleCondition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionRuleCondition entity.
 */
@Repository
public interface TransactionRuleConditionRepository extends JpaRepository<TransactionRuleCondition, Long> {
    default Optional<TransactionRuleCondition> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<TransactionRuleCondition> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionRuleCondition> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule",
        countQuery = "select count(transactionRuleCondition) from TransactionRuleCondition transactionRuleCondition"
    )
    Page<TransactionRuleCondition> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule"
    )
    List<TransactionRuleCondition> findAllWithToOneRelationships();

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule where transactionRuleCondition.id =:id"
    )
    Optional<TransactionRuleCondition> findOneWithToOneRelationships(@Param("id") Long id);
}
