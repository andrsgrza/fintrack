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
@Repository
public interface ApiIngestionRepository extends JpaRepository<ApiIngestion, Long> {
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
        value = "select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.apiAccessToken",
        countQuery = "select count(apiIngestion) from ApiIngestion apiIngestion"
    )
    Page<ApiIngestion> findAllWithToOneRelationships(Pageable pageable);

    @Query("select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.apiAccessToken")
    List<ApiIngestion> findAllWithToOneRelationships();

    @Query("select apiIngestion from ApiIngestion apiIngestion left join fetch apiIngestion.apiAccessToken where apiIngestion.id =:id")
    Optional<ApiIngestion> findOneWithToOneRelationships(@Param("id") Long id);
}
