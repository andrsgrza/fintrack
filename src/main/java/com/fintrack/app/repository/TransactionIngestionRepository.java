package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionIngestion entity.
 */
@Repository
public interface TransactionIngestionRepository
    extends JpaRepository<TransactionIngestion, Long>, JpaSpecificationExecutor<TransactionIngestion> {
    default Optional<TransactionIngestion> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<TransactionIngestion> findOneWithEagerRelationshipsByIdAndAccountUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndAccountUserLogin(id, login);
    }

    default List<TransactionIngestion> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionIngestion> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<TransactionIngestion> findAllWithEagerRelationshipsByAccountUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByAccountUserLogin(login);
    }

    @Query(
        value = "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.account",
        countQuery = "select count(transactionIngestion) from TransactionIngestion transactionIngestion"
    )
    Page<TransactionIngestion> findAllWithToOneRelationships(Pageable pageable);

    @Query("select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.account")
    List<TransactionIngestion> findAllWithToOneRelationships();

    @Query(
        "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.account where transactionIngestion.id =:id"
    )
    Optional<TransactionIngestion> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user where transactionIngestion.id = :id and account.user.login = :login"
    )
    Optional<TransactionIngestion> findOneWithToOneRelationshipsByIdAndAccountUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select transactionIngestion from TransactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user where account.user.login = :login"
    )
    List<TransactionIngestion> findAllWithToOneRelationshipsByAccountUserLogin(@Param("login") String login);
}
