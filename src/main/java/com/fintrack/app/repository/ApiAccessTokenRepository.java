package com.fintrack.app.repository;

import com.fintrack.app.domain.ApiAccessToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ApiAccessToken entity.
 */
@Repository
public interface ApiAccessTokenRepository extends JpaRepository<ApiAccessToken, Long> {
    @Query("select apiAccessToken from ApiAccessToken apiAccessToken where apiAccessToken.user.login = ?#{authentication.name}")
    List<ApiAccessToken> findByUserIsCurrentUser();

    default Optional<ApiAccessToken> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<ApiAccessToken> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ApiAccessToken> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.user",
        countQuery = "select count(apiAccessToken) from ApiAccessToken apiAccessToken"
    )
    Page<ApiAccessToken> findAllWithToOneRelationships(Pageable pageable);

    @Query("select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.user")
    List<ApiAccessToken> findAllWithToOneRelationships();

    @Query("select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessToken.id =:id")
    Optional<ApiAccessToken> findOneWithToOneRelationships(@Param("id") Long id);
}
