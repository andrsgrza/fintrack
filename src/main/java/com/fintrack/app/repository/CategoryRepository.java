package com.fintrack.app.repository;

import com.fintrack.app.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Category entity.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
    @Query("select category from Category category where category.user.login = ?#{authentication.name}")
    List<Category> findByUserIsCurrentUser();

    default Optional<Category> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<Category> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<Category> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select category from Category category left join fetch category.user left join fetch category.parentCategory",
        countQuery = "select count(category) from Category category"
    )
    Page<Category> findAllWithToOneRelationships(Pageable pageable);

    @Query("select category from Category category left join fetch category.user left join fetch category.parentCategory")
    List<Category> findAllWithToOneRelationships();

    @Query(
        "select category from Category category left join fetch category.user left join fetch category.parentCategory where category.id =:id"
    )
    Optional<Category> findOneWithToOneRelationships(@Param("id") Long id);
}
