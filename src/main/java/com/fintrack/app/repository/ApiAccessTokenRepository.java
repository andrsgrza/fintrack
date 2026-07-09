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
    boolean existsByTokenHash(String tokenHash);

    default Optional<ApiAccessToken> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<ApiAccessToken> findOneWithEagerRelationshipsByIdAndUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndUserLogin(id, login);
    }

    default List<ApiAccessToken> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ApiAccessToken> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<ApiAccessToken> findAllWithEagerRelationshipsByUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByUserLogin(login);
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

    @Query(
        "select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessToken.id = :id and apiAccessToken.user.login = :login"
    )
    Optional<ApiAccessToken> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select apiAccessToken from ApiAccessToken apiAccessToken left join fetch apiAccessToken.user where apiAccessToken.user.login = :login"
    )
    List<ApiAccessToken> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);
}
