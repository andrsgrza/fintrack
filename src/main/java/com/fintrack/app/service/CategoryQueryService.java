package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.Category;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.service.criteria.CategoryCriteria;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Category} entities in the database.
 * The main input is a {@link CategoryCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link CategoryDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class CategoryQueryService extends QueryService<Category> {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryQueryService.class);

    private final CategoryRepository categoryRepository;

    private final CategoryMapper categoryMapper;

    private final CurrentUserService currentUserService;

    public CategoryQueryService(
        CategoryRepository categoryRepository,
        CategoryMapper categoryMapper,
        CurrentUserService currentUserService
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Return a {@link List} of {@link CategoryDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> findByCriteria(CategoryCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<Category> specification = createSpecification(criteria);
        return categoryMapper.toDto(categoryRepository.findAll(specification));
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(CategoryCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Category> specification = createSpecification(criteria);
        return categoryRepository.count(specification);
    }

    /**
     * Function to convert {@link CategoryCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Category> createSpecification(CategoryCriteria criteria) {
        Specification<Category> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), Category_.id),
                buildStringSpecification(criteria.getName(), Category_.name),
                buildStringSpecification(criteria.getDescription(), Category_.description),
                buildSpecification(criteria.getCategoryType(), Category_.categoryType),
                buildStringSpecification(criteria.getColor(), Category_.color),
                buildStringSpecification(criteria.getIcon(), Category_.icon),
                buildSpecification(criteria.getActive(), Category_.active),
                buildRangeSpecification(criteria.getCreatedAt(), Category_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), Category_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(Category_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getParentCategoryId(), root ->
                    root.join(Category_.parentCategory, JoinType.LEFT).get(Category_.id)
                ),
                buildSpecification(criteria.getFinancialTransactionsId(), root ->
                    root.join(Category_.financialTransactions, JoinType.LEFT).get(FinancialTransaction_.id)
                ),
                buildSpecification(criteria.getChildCategoriesId(), root ->
                    root.join(Category_.childCategories, JoinType.LEFT).get(Category_.id)
                ),
                buildSpecification(criteria.getTransactionRulesId(), root ->
                    root.join(Category_.transactionRules, JoinType.LEFT).get(TransactionRule_.id)
                ),
                buildSpecification(criteria.getSubscriptionsId(), root ->
                    root.join(Category_.subscriptions, JoinType.LEFT).get(FinancialSubscription_.id)
                ),
                buildSpecification(criteria.getBudgetsId(), root -> root.join(Category_.budgets, JoinType.LEFT).get(Budget_.id))
            );
            if (!currentUserService.isAdmin()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        root.join(Category_.user, JoinType.INNER).get(User_.login),
                        currentUserService.getCurrentUserLogin()
                    )
                );
            }
        }
        return specification;
    }
}
