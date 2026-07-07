package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiAccessTokenPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ApiAccessTokenPermission entity.
 */
@Repository
public interface ApiAccessTokenPermissionRepository extends JpaRepository<ApiAccessTokenPermission, Long> {
    default Optional<ApiAccessTokenPermission> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<ApiAccessTokenPermission> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ApiAccessTokenPermission> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken",
        countQuery = "select count(apiAccessTokenPermission) from ApiAccessTokenPermission apiAccessTokenPermission"
    )
    Page<ApiAccessTokenPermission> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken"
    )
    List<ApiAccessTokenPermission> findAllWithToOneRelationships();

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken where apiAccessTokenPermission.id =:id"
    )
    Optional<ApiAccessTokenPermission> findOneWithToOneRelationships(@Param("id") Long id);
}
