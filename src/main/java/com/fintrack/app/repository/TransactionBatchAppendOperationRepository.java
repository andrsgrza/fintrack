package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionBatchAppendOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Bounded append receipts used solely to make a CSV append idempotent.
 *
 * The query deliberately joins through the batch owner, so a receipt can never be used to learn about another
 * user's workspace or reconstruct draft rows.
 */
@Repository
public interface TransactionBatchAppendOperationRepository extends JpaRepository<TransactionBatchAppendOperation, Long> {
    @EntityGraph(attributePaths = { "transactionBatch" })
    @Query(
        "select operation from TransactionBatchAppendOperation operation " +
        "where operation.transactionBatch.id = :batchId and operation.transactionBatch.user.login = :login " +
        "and operation.operationKey = :operationKey"
    )
    Optional<TransactionBatchAppendOperation> findOneByBatchIdAndOwnerLoginAndOperationKey(
        @Param("batchId") Long batchId,
        @Param("login") String login,
        @Param("operationKey") String operationKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TransactionBatchAppendOperation operation where operation.transactionBatch.id = :batchId")
    void deleteByTransactionBatchId(@Param("batchId") Long batchId);

    boolean existsByTransactionBatchId(Long transactionBatchId);
}
