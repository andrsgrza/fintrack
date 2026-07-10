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

    default Optional<TransactionRuleCondition> findOneWithEagerRelationshipsByIdAndRuleUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndRuleUserLogin(id, login);
    }

    default List<TransactionRuleCondition> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionRuleCondition> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<TransactionRuleCondition> findAllWithEagerRelationshipsByRuleUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByRuleUserLogin(login);
    }

    @Query(
        value = "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule transactionRule left join fetch transactionRule.user",
        countQuery = "select count(transactionRuleCondition) from TransactionRuleCondition transactionRuleCondition"
    )
    Page<TransactionRuleCondition> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule transactionRule left join fetch transactionRule.user"
    )
    List<TransactionRuleCondition> findAllWithToOneRelationships();

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule transactionRule left join fetch transactionRule.user where transactionRuleCondition.id =:id"
    )
    Optional<TransactionRuleCondition> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule transactionRule left join fetch transactionRule.user where transactionRuleCondition.id = :id and transactionRule.user.login = :login"
    )
    Optional<TransactionRuleCondition> findOneWithToOneRelationshipsByIdAndRuleUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    @Query(
        "select transactionRuleCondition from TransactionRuleCondition transactionRuleCondition left join fetch transactionRuleCondition.transactionRule transactionRule left join fetch transactionRule.user where transactionRule.user.login = :login"
    )
    List<TransactionRuleCondition> findAllWithToOneRelationshipsByRuleUserLogin(@Param("login") String login);
}
