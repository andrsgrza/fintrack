package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FinancialSubscription entity.
 *
 * When extending this class, extend FinancialSubscriptionRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface FinancialSubscriptionRepository
    extends
        FinancialSubscriptionRepositoryWithBagRelationships,
        JpaRepository<FinancialSubscription, Long>,
        JpaSpecificationExecutor<FinancialSubscription> {
    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription where financialSubscription.user.login = ?#{authentication.name}"
    )
    List<FinancialSubscription> findByUserIsCurrentUser();

    default Optional<FinancialSubscription> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationships(id));
    }

    default Optional<FinancialSubscription> findOneWithEagerRelationshipsByIdAndUserLogin(Long id, String login) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationshipsByIdAndUserLogin(id, login));
    }

    default List<FinancialSubscription> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<FinancialSubscription> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    default Page<FinancialSubscription> findAllWithEagerRelationshipsByUserLogin(String login, Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationshipsByUserLogin(login, pageable));
    }

    @Query(
        value = "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category",
        countQuery = "select count(financialSubscription) from FinancialSubscription financialSubscription"
    )
    Page<FinancialSubscription> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category"
    )
    List<FinancialSubscription> findAllWithToOneRelationships();

    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category where financialSubscription.id =:id"
    )
    Optional<FinancialSubscription> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription where financialSubscription.id = :id and financialSubscription.user.login = :login"
    )
    Optional<FinancialSubscription> findOneByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category where financialSubscription.id = :id and financialSubscription.user.login = :login"
    )
    Optional<FinancialSubscription> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        value = "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category where financialSubscription.user.login = :login",
        countQuery = "select count(financialSubscription) from FinancialSubscription financialSubscription where financialSubscription.user.login = :login"
    )
    Page<FinancialSubscription> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query(
        "select financialSubscription from FinancialSubscription financialSubscription left join fetch financialSubscription.user left join fetch financialSubscription.account left join fetch financialSubscription.category where financialSubscription.user.login = :login"
    )
    List<FinancialSubscription> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    @Query(
        "select case when count(ft) > 0 then true else false end from FinancialTransaction ft where ft.financialSubscription.id = :subscriptionId"
    )
    boolean existsFinancialTransactionByFinancialSubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FinancialTransaction ft set ft.financialSubscription = null where ft.financialSubscription.id = :subscriptionId")
    void clearFinancialTransactionSubscriptionReferences(@Param("subscriptionId") Long subscriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update TransactionRule tr set tr.resultingFinancialSubscription = null, tr.active = false where tr.resultingFinancialSubscription.id = :subscriptionId"
    )
    void clearTransactionRuleResultingSubscriptionReferences(@Param("subscriptionId") Long subscriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from rel_financial_subscription__tags where financial_subscription_id = :subscriptionId", nativeQuery = true)
    void deleteTagLinksByFinancialSubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update FinancialSubscription financialSubscription set financialSubscription.account = null where financialSubscription.account.id = :accountId"
    )
    void clearAccountByAccountId(@Param("accountId") Long accountId);
}
