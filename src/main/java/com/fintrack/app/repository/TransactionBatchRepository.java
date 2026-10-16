package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for the internal TransactionBatch workspace aggregate. */
@Repository
public interface TransactionBatchRepository extends JpaRepository<TransactionBatch, Long> {
    @EntityGraph(attributePaths = { "user", "account", "account.user" })
    @Query("select batch from TransactionBatch batch where batch.id = :id and batch.user.login = :login")
    Optional<TransactionBatch> findOneWithRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "account", "account.user" })
    @Query("select batch from TransactionBatch batch where batch.id = :id and batch.user.login = :login and batch.status = :status")
    Optional<TransactionBatch> findOneForOwnedStatusMutation(
        @Param("id") Long id,
        @Param("login") String login,
        @Param("status") TransactionBatchStatus status
    );

    @EntityGraph(attributePaths = { "user", "account", "account.user" })
    @Query(
        "select batch from TransactionBatch batch where batch.user.login = :login and batch.status = :status order by batch.updatedAt desc, batch.id desc"
    )
    List<TransactionBatch> findAllByUserLoginAndStatus(@Param("login") String login, @Param("status") TransactionBatchStatus status);

    boolean existsByAccountId(Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TransactionBatch batch where batch.account.id = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);
}
