package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionCandidate entity.
 */
@Repository
public interface TransactionCandidateRepository
    extends JpaRepository<TransactionCandidate, Long>, JpaSpecificationExecutor<TransactionCandidate> {
    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query("select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id")
    Optional<TransactionCandidate> findOneWithRelationships(@Param("id") Long id);

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query(
        value = "select distinct transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.user.login = :login",
        countQuery = "select count(transactionCandidate) from TransactionCandidate transactionCandidate where transactionCandidate.user.login = :login"
    )
    Page<TransactionCandidate> findAllWithRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query(
        "select distinct transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.user.login = :login"
    )
    List<TransactionCandidate> findAllWithRelationshipsByUserLogin(@Param("login") String login);

    @EntityGraph(attributePaths = { "account", "category" })
    @Query(
        value = """
        select distinct transactionCandidate
        from TransactionCandidate transactionCandidate
        where transactionCandidate.user.login = :login
          and transactionCandidate.source = :source
          and transactionCandidate.status in :statuses
        """,
        countQuery = """
        select count(distinct transactionCandidate)
        from TransactionCandidate transactionCandidate
        where transactionCandidate.user.login = :login
          and transactionCandidate.source = :source
          and transactionCandidate.status in :statuses
        """
    )
    Page<TransactionCandidate> findRecoverableManualDraftsByUserLogin(
        @Param("login") String login,
        @Param("source") TransactionCandidateSource source,
        @Param("statuses") Collection<TransactionCandidateStatus> statuses,
        Pageable pageable
    );

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query(
        "select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id and transactionCandidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneWithRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query(
        "select distinct transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.ingestionRecord.id = :ingestionRecordId and transactionCandidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneWithRelationshipsByIngestionRecordIdAndUserLogin(
        @Param("ingestionRecordId") Long ingestionRecordId,
        @Param("login") String login
    );

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "transactionIngestion",
            "transactionIngestion.account",
            "transactionIngestion.account.user",
            "ingestionRecord",
            "ingestionRecord.transactionIngestion",
            "ingestionRecord.transactionIngestion.account",
            "ingestionRecord.transactionIngestion.account.user",
            "financialTransaction",
            "financialTransaction.account",
            "financialTransaction.account.user",
            "tags",
            "tags.user",
        }
    )
    @Query(
        "select distinct transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.transactionIngestion.id = :transactionIngestionId and transactionCandidate.user.login = :login"
    )
    List<TransactionCandidate> findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
        @Param("transactionIngestionId") Long transactionIngestionId,
        @Param("login") String login
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id and transactionCandidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneByIdAndUserLoginForPosting(@Param("id") Long id, @Param("login") String login);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_transaction_candidate__tags where transaction_candidate_id = :transactionCandidateId",
        nativeQuery = true
    )
    void deleteTagLinksByTransactionCandidateId(@Param("transactionCandidateId") Long transactionCandidateId);

    long countByUserLogin(String login);
}
