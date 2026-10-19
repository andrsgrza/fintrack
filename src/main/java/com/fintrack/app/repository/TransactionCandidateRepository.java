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
            "internalTransferCounterpartAccount",
            "internalTransferCounterpartAccount.user",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
        }
    )
    @Query("select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id")
    Optional<TransactionCandidate> findOneWithRelationships(@Param("id") Long id);

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "internalTransferCounterpartAccount",
            "internalTransferCounterpartAccount.user",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
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
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "internalTransferCounterpartTransaction.account.user",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
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
          and transactionCandidate.transactionBatch is null
          and transactionCandidate.status in :statuses
        """,
        countQuery = """
        select count(distinct transactionCandidate)
        from TransactionCandidate transactionCandidate
        where transactionCandidate.user.login = :login
          and transactionCandidate.source = :source
          and transactionCandidate.transactionBatch is null
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
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "internalTransferCounterpartTransaction.account.user",
            "category",
            "category.user",
            "transactionBatch",
            "transactionBatch.account",
            "transactionBatch.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
            "tagSuppressions",
            "tagSuppressions.tag",
            "tagSuppressions.tag.user",
            "inputIssues",
        }
    )
    @Query(
        "select distinct candidate from TransactionCandidate candidate where candidate.id = :candidateId and candidate.transactionBatch.id = :batchId and candidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneBatchCandidateWithRelationships(
        @Param("batchId") Long batchId,
        @Param("candidateId") Long candidateId,
        @Param("login") String login
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "internalTransferCounterpartTransaction.account.user",
            "category",
            "category.user",
            "transactionBatch",
            "transactionBatch.account",
            "transactionBatch.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
            "tagSuppressions",
            "tagSuppressions.tag",
            "tagSuppressions.tag.user",
            "inputIssues",
        }
    )
    @Query(
        "select distinct candidate from TransactionCandidate candidate where candidate.id = :candidateId and candidate.transactionBatch.id = :batchId and candidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneBatchCandidateForMutation(
        @Param("batchId") Long batchId,
        @Param("candidateId") Long candidateId,
        @Param("login") String login
    );

    @EntityGraph(
        attributePaths = {
            "account",
            "category",
            "tagAssociations",
            "tagAssociations.tag",
            "tagSuppressions",
            "tagSuppressions.tag",
            "inputIssues",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
        }
    )
    @Query(
        "select distinct candidate from TransactionCandidate candidate where candidate.transactionBatch.id = :batchId and candidate.user.login = :login order by candidate.rowPosition asc, candidate.id asc"
    )
    List<TransactionCandidate> findAllBatchCandidatesWithRelationships(@Param("batchId") Long batchId, @Param("login") String login);

    /** Stable locked aggregate read used only by internal batch publication. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
            "internalTransferCounterpartTransaction.account.user",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
            "tagSuppressions",
            "tagSuppressions.tag",
            "inputIssues",
        }
    )
    @Query(
        "select distinct candidate from TransactionCandidate candidate " +
        "where candidate.transactionBatch.id = :batchId and candidate.user.login = :login " +
        "order by candidate.rowPosition asc, candidate.id asc"
    )
    List<TransactionCandidate> findAllBatchCandidatesForPublication(@Param("batchId") Long batchId, @Param("login") String login);

    boolean existsByTransactionBatchId(Long transactionBatchId);

    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "tagAssociations.tag.user",
        }
    )
    @Query(
        "select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id and transactionCandidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneWithRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(
        attributePaths = {
            "user",
            "account",
            "account.user",
            "category",
            "category.user",
            "tagAssociations",
            "tagAssociations.tag",
            "internalTransferCounterpartAccount",
            "internalTransferCounterpartAccount.user",
            "internalTransferCounterpartTransaction",
            "internalTransferCounterpartTransaction.account",
        }
    )
    @Query(
        "select transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.id = :id and transactionCandidate.user.login = :login"
    )
    Optional<TransactionCandidate> findOneByIdAndUserLoginForPosting(@Param("id") Long id, @Param("login") String login);

    boolean existsByCategoryId(Long categoryId);

    @Query(
        "select case when count(transactionCandidateTag) > 0 then true else false end from TransactionCandidateTag transactionCandidateTag where transactionCandidateTag.tag.id = :tagId"
    )
    boolean existsByTagId(@Param("tagId") Long tagId);

    @Query(
        "select case when count(suppression) > 0 then true else false end from TransactionCandidateTagSuppression suppression where suppression.tag.id = :tagId"
    )
    boolean existsTagSuppressionByTagId(@Param("tagId") Long tagId);

    @Query(
        "select case when count(transactionCandidate) > 0 then true else false end from TransactionCandidate transactionCandidate where transactionCandidate.account.id = :accountId"
    )
    boolean existsAccountCandidateOutsideWorkflowCleanup(@Param("accountId") Long accountId);

    boolean existsByAccountId(Long accountId);

    /**
     * Loads every candidate that could be part of an account deletion aggregate.
     */
    @EntityGraph(attributePaths = { "account", "transactionBatch", "transactionBatch.account" })
    @Query(
        "select distinct transactionCandidate from TransactionCandidate transactionCandidate where transactionCandidate.account.id = :accountId"
    )
    List<TransactionCandidate> findAllForAccountDeletionPreview(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_transaction_candidate__tags where transaction_candidate_id in (select id from transaction_candidate where account_id = :accountId)",
        nativeQuery = true
    )
    void deleteTagLinksByAccountId(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "delete from rel_transaction_candidate__tags where transaction_candidate_id in (select id from transaction_candidate where transaction_batch_id = :batchId)",
        nativeQuery = true
    )
    void deleteTagLinksByTransactionBatchId(@Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TransactionCandidate candidate where candidate.transactionBatch.id = :batchId")
    void deleteByTransactionBatchId(@Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TransactionCandidate transactionCandidate where transactionCandidate.account.id = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);

    long countByUserLogin(String login);
}
