package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.domain.enumeration.ApiPermission;
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
    boolean existsByApiAccessTokenIdAndPermission(Long apiAccessTokenId, ApiPermission permission);

    default Optional<ApiAccessTokenPermission> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<ApiAccessTokenPermission> findOneWithEagerRelationshipsByIdAndTokenUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndTokenUserLogin(id, login);
    }

    default List<ApiAccessTokenPermission> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ApiAccessTokenPermission> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<ApiAccessTokenPermission> findAllWithEagerRelationshipsByTokenUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByTokenUserLogin(login);
    }

    @Query(
        value = "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken apiAccessToken left join fetch apiAccessToken.user",
        countQuery = "select count(apiAccessTokenPermission) from ApiAccessTokenPermission apiAccessTokenPermission"
    )
    Page<ApiAccessTokenPermission> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken apiAccessToken left join fetch apiAccessToken.user"
    )
    List<ApiAccessTokenPermission> findAllWithToOneRelationships();

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessTokenPermission.id =:id"
    )
    Optional<ApiAccessTokenPermission> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessTokenPermission.id = :id and apiAccessToken.user.login = :login"
    )
    Optional<ApiAccessTokenPermission> findOneWithToOneRelationshipsByIdAndTokenUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    @Query(
        "select apiAccessTokenPermission from ApiAccessTokenPermission apiAccessTokenPermission left join fetch apiAccessTokenPermission.apiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessToken.user.login = :login"
    )
    List<ApiAccessTokenPermission> findAllWithToOneRelationshipsByTokenUserLogin(@Param("login") String login);
}
