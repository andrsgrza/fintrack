package com.fintrack.app.repository;

import com.fintrack.app.domain.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Tag entity.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long>, JpaSpecificationExecutor<Tag> {
    @Query("select tag from Tag tag where tag.user.login = ?#{authentication.name}")
    List<Tag> findByUserIsCurrentUser();

    default Optional<Tag> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<Tag> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<Tag> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(value = "select tag from Tag tag left join fetch tag.user", countQuery = "select count(tag) from Tag tag")
    Page<Tag> findAllWithToOneRelationships(Pageable pageable);

    @Query("select tag from Tag tag left join fetch tag.user")
    List<Tag> findAllWithToOneRelationships();

    @Query("select tag from Tag tag left join fetch tag.user where tag.id =:id")
    Optional<Tag> findOneWithToOneRelationships(@Param("id") Long id);

    @Query("select tag from Tag tag where tag.id = :id and tag.user.login = :login")
    Optional<Tag> findOneByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query("select tag from Tag tag left join fetch tag.user where tag.id = :id and tag.user.login = :login")
    Optional<Tag> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        value = "select tag from Tag tag left join fetch tag.user where tag.user.login = :login",
        countQuery = "select count(tag) from Tag tag where tag.user.login = :login"
    )
    Page<Tag> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query("select tag from Tag tag left join fetch tag.user where tag.user.login = :login")
    List<Tag> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    @Query(
        "select case when count(tag) > 0 then true else false end from Tag tag " +
        "where tag.user.id = :userId and lower(trim(tag.name)) = lower(trim(:name)) " +
        "and (:excludeId is null or tag.id <> :excludeId)"
    )
    boolean existsByUserIdAndNormalizedName(@Param("userId") Long userId, @Param("name") String name, @Param("excludeId") Long excludeId);
}
