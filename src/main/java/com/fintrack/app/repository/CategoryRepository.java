package com.fintrack.app.repository;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.enumeration.CategoryType;
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

    @Query("select category from Category category where category.id = :id and category.user.login = :login")
    Optional<Category> findOneByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select category from Category category left join fetch category.user left join fetch category.parentCategory where category.id = :id and category.user.login = :login"
    )
    Optional<Category> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        value = "select category from Category category left join fetch category.user left join fetch category.parentCategory where category.user.login = :login",
        countQuery = "select count(category) from Category category where category.user.login = :login"
    )
    Page<Category> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query(
        "select category from Category category left join fetch category.user left join fetch category.parentCategory where category.user.login = :login"
    )
    List<Category> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    @Query(
        "select case when count(category) > 0 then true else false end from Category category " +
        "where category.user.id = :userId " +
        "and category.categoryType = :categoryType " +
        "and ((:parentCategoryId is null and category.parentCategory is null) or category.parentCategory.id = :parentCategoryId) " +
        "and lower(trim(category.name)) = lower(trim(:name)) " +
        "and (:excludeId is null or category.id <> :excludeId)"
    )
    boolean existsByOwnerTypeParentAndNormalizedName(
        @Param("userId") Long userId,
        @Param("categoryType") CategoryType categoryType,
        @Param("parentCategoryId") Long parentCategoryId,
        @Param("name") String name,
        @Param("excludeId") Long excludeId
    );

    @Query(
        "select case when count(category) > 0 then true else false end from Category category where category.parentCategory.id = :categoryId"
    )
    boolean existsByParentCategoryId(@Param("categoryId") Long categoryId);

    @Query("select case when count(ft) > 0 then true else false end from FinancialTransaction ft where ft.category.id = :categoryId")
    boolean existsFinancialTransactionByCategoryId(@Param("categoryId") Long categoryId);

    @Query("select case when count(fs) > 0 then true else false end from FinancialSubscription fs where fs.category.id = :categoryId")
    boolean existsFinancialSubscriptionByCategoryId(@Param("categoryId") Long categoryId);

    @Query("select case when count(b) > 0 then true else false end from Budget b join b.categories c where c.id = :categoryId")
    boolean existsBudgetCategoryLinkByCategoryId(@Param("categoryId") Long categoryId);

    @Query("select case when count(tr) > 0 then true else false end from TransactionRule tr where tr.resultingCategory.id = :categoryId")
    boolean existsTransactionRuleByResultingCategoryId(@Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FinancialTransaction ft set ft.category = null where ft.category.id = :categoryId")
    void clearFinancialTransactionCategoryReferences(@Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FinancialSubscription fs set fs.category = null where fs.category.id = :categoryId")
    void clearFinancialSubscriptionCategoryReferences(@Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from rel_budget__categories where categories_id = :categoryId", nativeQuery = true)
    void deleteBudgetCategoryLinksByCategoryId(@Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransactionRule tr set tr.resultingCategory = null, tr.active = false where tr.resultingCategory.id = :categoryId")
    void clearTransactionRuleResultingCategoryReferences(@Param("categoryId") Long categoryId);
}
