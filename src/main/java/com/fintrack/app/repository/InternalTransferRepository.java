package com.fintrack.app.repository;

import com.fintrack.app.domain.InternalTransfer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the InternalTransfer entity.
 */
@Repository
public interface InternalTransferRepository extends JpaRepository<InternalTransfer, Long> {
    boolean existsByOutgoingTransactionId(Long outgoingTransactionId);

    boolean existsByIncomingTransactionId(Long incomingTransactionId);

    @Query(
        "select count(internalTransfer) > 0 from InternalTransfer internalTransfer where internalTransfer.outgoingTransaction.id = :transactionId or internalTransfer.incomingTransaction.id = :transactionId"
    )
    boolean existsByTransactionIdInEitherRole(@Param("transactionId") Long transactionId);

    @Modifying
    @Query(
        "delete from InternalTransfer internalTransfer where internalTransfer.outgoingTransaction.id = :transactionId or internalTransfer.incomingTransaction.id = :transactionId"
    )
    void deleteByTransactionIdInEitherRole(@Param("transactionId") Long transactionId);

    default Optional<InternalTransfer> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<InternalTransfer> findOneWithEagerRelationshipsByIdAndAccountUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndAccountUserLogin(id, login);
    }

    default List<InternalTransfer> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default List<InternalTransfer> findAllWithEagerRelationshipsByAccountUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByAccountUserLogin(login);
    }

    @Query(
        "select internalTransfer from InternalTransfer internalTransfer left join fetch internalTransfer.outgoingTransaction outgoingTransaction left join fetch outgoingTransaction.account outgoingAccount left join fetch outgoingAccount.user left join fetch internalTransfer.incomingTransaction incomingTransaction left join fetch incomingTransaction.account incomingAccount left join fetch incomingAccount.user"
    )
    List<InternalTransfer> findAllWithToOneRelationships();

    @Query(
        "select internalTransfer from InternalTransfer internalTransfer left join fetch internalTransfer.outgoingTransaction outgoingTransaction left join fetch outgoingTransaction.account outgoingAccount left join fetch outgoingAccount.user left join fetch internalTransfer.incomingTransaction incomingTransaction left join fetch incomingTransaction.account incomingAccount left join fetch incomingAccount.user where internalTransfer.id = :id"
    )
    Optional<InternalTransfer> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select internalTransfer from InternalTransfer internalTransfer left join fetch internalTransfer.outgoingTransaction outgoingTransaction left join fetch outgoingTransaction.account outgoingAccount left join fetch outgoingAccount.user left join fetch internalTransfer.incomingTransaction incomingTransaction left join fetch incomingTransaction.account incomingAccount left join fetch incomingAccount.user where internalTransfer.id = :id and outgoingAccount.user.login = :login and incomingAccount.user.login = :login"
    )
    Optional<InternalTransfer> findOneWithToOneRelationshipsByIdAndAccountUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select internalTransfer from InternalTransfer internalTransfer left join fetch internalTransfer.outgoingTransaction outgoingTransaction left join fetch outgoingTransaction.account outgoingAccount left join fetch outgoingAccount.user left join fetch internalTransfer.incomingTransaction incomingTransaction left join fetch incomingTransaction.account incomingAccount left join fetch incomingAccount.user where outgoingAccount.user.login = :login and incomingAccount.user.login = :login"
    )
    List<InternalTransfer> findAllWithToOneRelationshipsByAccountUserLogin(@Param("login") String login);
}
