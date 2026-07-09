package com.fintrack.app.repository;

import com.fintrack.app.domain.FileIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FileIngestion entity.
 */
@Repository
public interface FileIngestionRepository extends JpaRepository<FileIngestion, Long> {
    default Optional<FileIngestion> findOneWithRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<FileIngestion> findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndTransactionIngestionAccountUserLogin(id, login);
    }

    default List<FileIngestion> findAllWithRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default List<FileIngestion> findAllWithRelationshipsByTransactionIngestionAccountUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByTransactionIngestionAccountUserLogin(login);
    }

    boolean existsByTransactionIngestionId(Long transactionIngestionId);

    @Query(
        "select fileIngestion from FileIngestion fileIngestion left join fetch fileIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user"
    )
    List<FileIngestion> findAllWithToOneRelationships();

    @Query(
        "select fileIngestion from FileIngestion fileIngestion left join fetch fileIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user where fileIngestion.id = :id"
    )
    Optional<FileIngestion> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select fileIngestion from FileIngestion fileIngestion left join fetch fileIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user where fileIngestion.id = :id and account.user.login = :login"
    )
    Optional<FileIngestion> findOneWithToOneRelationshipsByIdAndTransactionIngestionAccountUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    @Query(
        "select fileIngestion from FileIngestion fileIngestion left join fetch fileIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user where account.user.login = :login"
    )
    List<FileIngestion> findAllWithToOneRelationshipsByTransactionIngestionAccountUserLogin(@Param("login") String login);
}
