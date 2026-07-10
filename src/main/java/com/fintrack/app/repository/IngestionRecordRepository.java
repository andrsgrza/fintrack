package com.fintrack.app.repository;

import com.fintrack.app.domain.IngestionRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the IngestionRecord entity.
 */
@SuppressWarnings("unused")
@Repository
public interface IngestionRecordRepository extends JpaRepository<IngestionRecord, Long>, JpaSpecificationExecutor<IngestionRecord> {
    default Optional<IngestionRecord> findOneWithRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<IngestionRecord> findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndTransactionIngestionAccountUserLogin(id, login);
    }

    @Query(
        "select ingestionRecord from IngestionRecord ingestionRecord left join fetch ingestionRecord.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch ingestionRecord.financialTransaction financialTransaction where ingestionRecord.id = :id"
    )
    Optional<IngestionRecord> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select ingestionRecord from IngestionRecord ingestionRecord left join fetch ingestionRecord.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch ingestionRecord.financialTransaction financialTransaction where ingestionRecord.id = :id and account.user.login = :login"
    )
    Optional<IngestionRecord> findOneWithToOneRelationshipsByIdAndTransactionIngestionAccountUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    boolean existsByFinancialTransactionId(Long financialTransactionId);

    boolean existsByTransactionIngestionIdAndRecordIndex(Long transactionIngestionId, Integer recordIndex);
}
