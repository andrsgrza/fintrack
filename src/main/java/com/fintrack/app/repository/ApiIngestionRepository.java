package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ApiIngestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ApiIngestionRepository extends JpaRepository<ApiIngestion, Long>, JpaSpecificationExecutor<ApiIngestion> {
    default Optional<ApiIngestion> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<ApiIngestion> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ApiIngestion> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken",
        countQuery = "select count(apiIngestion) from ApiIngestion apiIngestion"
    )
    Page<ApiIngestion> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken"
    )
    List<ApiIngestion> findAllWithToOneRelationships();

    @Query(
        "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken where apiIngestion.id =:id"
    )
    Optional<ApiIngestion> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken where apiIngestion.id = :id and account.user.login = :login"
    )
    Optional<ApiIngestion> findOneWithToOneRelationshipsByUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken where account.user.login = :login"
    )
    List<ApiIngestion> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    @Query(
        value = "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.transactionIngestion transactionIngestion left join fetch transactionIngestion.account account left join fetch account.user left join fetch apiIngestion.apiAccessToken apiAccessToken where account.user.login = :login",
        countQuery = "select count(apiIngestion) from ApiIngestion apiIngestion join apiIngestion.transactionIngestion transactionIngestion join transactionIngestion.account account join account.user user where user.login = :login"
    )
    Page<ApiIngestion> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    boolean existsByTransactionIngestionId(Long transactionIngestionId);

    boolean existsByRequestId(String requestId);
}
