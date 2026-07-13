package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FinancialTransaction entity.
 *
 * When extending this class, extend FinancialTransactionRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface FinancialTransactionRepository
    extends
        FinancialTransactionRepositoryWithBagRelationships,
        JpaRepository<FinancialTransaction, Long>,
        JpaSpecificationExecutor<FinancialTransaction> {
    default Optional<FinancialTransaction> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationships(id));
    }

    default List<FinancialTransaction> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<FinancialTransaction> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    @Query(
        value = "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount",
        countQuery = "select count(financialTransaction) from FinancialTransaction financialTransaction"
    )
    Page<FinancialTransaction> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount"
    )
    List<FinancialTransaction> findAllWithToOneRelationships();

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount where financialTransaction.id =:id"
    )
    Optional<FinancialTransaction> findOneWithToOneRelationships(@Param("id") Long id);

    default Optional<FinancialTransaction> findOneAccessibleByIdAndAccountUserLogin(Long id, String login) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationshipsByIdAndAccountUserLogin(id, login));
    }

    default Page<FinancialTransaction> findAllAccessibleWithToOneRelationshipsByAccountUserLogin(String login, Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationshipsByAccountUserLogin(login, pageable));
    }

    default List<FinancialTransaction> findAllAccessibleWithToOneRelationshipsByAccountUserLogin(String login) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationshipsByAccountUserLogin(login));
    }

    @Query(
        value = "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount where financialTransaction.account.user.login = :login",
        countQuery = "select count(financialTransaction) from FinancialTransaction financialTransaction where financialTransaction.account.user.login = :login"
    )
    Page<FinancialTransaction> findAllWithToOneRelationshipsByAccountUserLogin(@Param("login") String login, Pageable pageable);

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount where financialTransaction.account.user.login = :login"
    )
    List<FinancialTransaction> findAllWithToOneRelationshipsByAccountUserLogin(@Param("login") String login);

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account account left join fetch account.user left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription left join fetch financialTransaction.transactionIngestion transactionIngestion left join fetch transactionIngestion.account transactionIngestionAccount where financialTransaction.id = :id and financialTransaction.account.user.login = :login"
    )
    Optional<FinancialTransaction> findOneWithToOneRelationshipsByIdAndAccountUserLogin(@Param("id") Long id, @Param("login") String login);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_financial_transaction__tags where financial_transaction_id = :financialTransactionId",
        nativeQuery = true
    )
    void deleteTagLinksByFinancialTransactionId(@Param("financialTransactionId") Long financialTransactionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_financial_transaction__tags where financial_transaction_id in (select id from financial_transaction where transaction_ingestion_id = :transactionIngestionId)",
        nativeQuery = true
    )
    void deleteTagLinksByTransactionIngestionId(@Param("transactionIngestionId") Long transactionIngestionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from FinancialTransaction financialTransaction where financialTransaction.transactionIngestion.id = :transactionIngestionId"
    )
    void deleteByTransactionIngestionId(@Param("transactionIngestionId") Long transactionIngestionId);

    @Query(
        "select min(financialTransaction.transactionDate) from FinancialTransaction financialTransaction where financialTransaction.account.id = :accountId"
    )
    Optional<LocalDate> findEarliestTransactionDateByAccountId(@Param("accountId") Long accountId);

    List<FinancialTransaction> findByAccountIdAndTransactionDateBetween(Long accountId, LocalDate startDate, LocalDate endDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_financial_transaction__tags where financial_transaction_id in (select id from financial_transaction where account_id = :accountId)",
        nativeQuery = true
    )
    void deleteTagLinksByAccountId(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FinancialTransaction financialTransaction where financialTransaction.account.id = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_financial_transaction__tags where financial_transaction_id in (select ft.id from financial_transaction ft join transaction_ingestion ti on ft.transaction_ingestion_id = ti.id where ti.account_id = :accountId)",
        nativeQuery = true
    )
    void deleteTagLinksByTransactionIngestionAccountId(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from FinancialTransaction financialTransaction where financialTransaction.transactionIngestion.id in (select transactionIngestion.id from TransactionIngestion transactionIngestion where transactionIngestion.account.id = :accountId)"
    )
    void deleteByTransactionIngestionAccountId(@Param("accountId") Long accountId);
}
