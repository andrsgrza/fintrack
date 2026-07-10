package com.fintrack.app.repository;

import com.fintrack.app.domain.UserDashboardPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the UserDashboardPreference entity.
 */
@Repository
public interface UserDashboardPreferenceRepository extends JpaRepository<UserDashboardPreference, Long> {
    boolean existsByUserId(Long userId);

    default Optional<UserDashboardPreference> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<UserDashboardPreference> findOneWithEagerRelationshipsByIdAndUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndUserLogin(id, login);
    }

    default List<UserDashboardPreference> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<UserDashboardPreference> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<UserDashboardPreference> findAllWithEagerRelationshipsByUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByUserLogin(login);
    }

    @Query(
        value = "select userDashboardPreference from UserDashboardPreference userDashboardPreference left join fetch userDashboardPreference.user",
        countQuery = "select count(userDashboardPreference) from UserDashboardPreference userDashboardPreference"
    )
    Page<UserDashboardPreference> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select userDashboardPreference from UserDashboardPreference userDashboardPreference left join fetch userDashboardPreference.user"
    )
    List<UserDashboardPreference> findAllWithToOneRelationships();

    @Query(
        "select userDashboardPreference from UserDashboardPreference userDashboardPreference left join fetch userDashboardPreference.user where userDashboardPreference.id =:id"
    )
    Optional<UserDashboardPreference> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select userDashboardPreference from UserDashboardPreference userDashboardPreference left join fetch userDashboardPreference.user where userDashboardPreference.id = :id and userDashboardPreference.user.login = :login"
    )
    Optional<UserDashboardPreference> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select userDashboardPreference from UserDashboardPreference userDashboardPreference left join fetch userDashboardPreference.user where userDashboardPreference.user.login = :login"
    )
    List<UserDashboardPreference> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);
}
