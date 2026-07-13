package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.Category}.
 */
@Service
@Transactional
public class CategoryService {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;

    private final CategoryMapper categoryMapper;

    private final CurrentUserService currentUserService;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper, CurrentUserService currentUserService) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Save a category.
     *
     * @param categoryDTO the entity to save.
     * @return the persisted entity.
     */
    public CategoryDTO save(CategoryDTO categoryDTO) {
        LOG.debug("Request to save Category : {}", categoryDTO);
        Category category = categoryMapper.toEntity(categoryDTO);
        category.setUser(currentUserService.getCurrentUser());
        applyParentCategoryOnCreate(category, categoryDTO.getParentCategory());
        category.setName(normalizeName(category.getName()));
        validateChildCategoryTypeMatchesParent(category);
        validateUniqueSiblingNameForOwner(
            category.getUser().getId(),
            category.getCategoryType(),
            resolveParentCategoryId(category),
            category.getName(),
            null
        );
        category = categoryRepository.save(category);
        return categoryMapper.toDto(category);
    }

    /**
     * Update a category.
     *
     * @param categoryDTO the entity to save.
     * @return the persisted entity.
     */
    public CategoryDTO update(CategoryDTO categoryDTO) {
        LOG.debug("Request to update Category : {}", categoryDTO);
        Category existingCategory = findAccessibleEntity(categoryDTO.getId()).orElseThrow();
        validateParentCategoryImmutable(existingCategory, categoryDTO.getParentCategory());
        Category category = categoryMapper.toEntity(categoryDTO);
        category.setUser(existingCategory.getUser());
        category.setParentCategory(existingCategory.getParentCategory());
        category.setName(normalizeName(category.getName()));
        validateCategoryTypeChange(existingCategory, existingCategory.getCategoryType(), category.getCategoryType());
        validateUniqueSiblingNameForOwner(
            existingCategory.getUser().getId(),
            category.getCategoryType(),
            resolveParentCategoryId(category),
            category.getName(),
            existingCategory.getId()
        );
        category = categoryRepository.save(category);
        return categoryMapper.toDto(category);
    }

    /**
     * Partially update a category.
     *
     * @param categoryDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<CategoryDTO> partialUpdate(CategoryDTO categoryDTO) {
        LOG.debug("Request to partially update Category : {}", categoryDTO);

        return findAccessibleEntity(categoryDTO.getId())
            .map(existingCategory -> {
                boolean parentProvided = categoryDTO.getParentCategory() != null;
                boolean nameProvided = categoryDTO.getName() != null;
                boolean categoryTypeProvided = categoryDTO.getCategoryType() != null;

                CategoryType previousCategoryType = existingCategory.getCategoryType();

                if (parentProvided) {
                    validateParentCategoryImmutable(existingCategory, categoryDTO.getParentCategory());
                }

                categoryMapper.partialUpdate(existingCategory, categoryDTO);
                if (nameProvided) {
                    existingCategory.setName(normalizeName(categoryDTO.getName()));
                }
                if (categoryTypeProvided) {
                    validateCategoryTypeChange(existingCategory, previousCategoryType, existingCategory.getCategoryType());
                }
                if (nameProvided || categoryTypeProvided) {
                    validateUniqueSiblingNameForOwner(
                        existingCategory.getUser().getId(),
                        existingCategory.getCategoryType(),
                        resolveParentCategoryId(existingCategory),
                        existingCategory.getName(),
                        existingCategory.getId()
                    );
                }
                return existingCategory;
            })
            .map(categoryRepository::save)
            .map(categoryMapper::toDto);
    }

    /**
     * Get all the categories with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<CategoryDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return categoryRepository.findAllWithEagerRelationships(pageable).map(categoryMapper::toDto);
        }
        return categoryRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(categoryMapper::toDto);
    }

    /**
     * Get one category by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<CategoryDTO> findOne(Long id) {
        LOG.debug("Request to get Category : {}", id);
        return findAccessibleEntity(id).map(categoryMapper::toDto);
    }

    /**
     * Returns whether the current user can access the category.
     *
     * @param id the id of the entity.
     * @return true when the category exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the category by id.
     *
     * @param id the id of the entity.
     * @return true when the category was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete Category : {}", id);
        Optional<Category> category = findAccessibleEntity(id);
        if (category.isEmpty()) {
            return false;
        }
        Long categoryId = category.orElseThrow().getId();
        if (categoryRepository.existsByParentCategoryId(categoryId)) {
            throw new IllegalArgumentException("Category with child categories cannot be deleted");
        }
        unlinkCategoryFromAllRelationships(categoryId);
        categoryRepository.deleteById(categoryId);
        return true;
    }

    private void unlinkCategoryFromAllRelationships(Long categoryId) {
        categoryRepository.clearFinancialTransactionCategoryReferences(categoryId);
        categoryRepository.clearFinancialSubscriptionCategoryReferences(categoryId);
        categoryRepository.deleteBudgetCategoryLinksByCategoryId(categoryId);
        categoryRepository.clearTransactionRuleResultingCategoryReferences(categoryId);
    }

    private Optional<Category> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return categoryRepository.findOneWithEagerRelationships(id);
        }
        return categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyParentCategoryOnCreate(Category category, CategoryDTO parentCategoryDTO) {
        if (parentCategoryDTO == null || parentCategoryDTO.getId() == null) {
            category.setParentCategory(null);
            return;
        }
        Long parentId = parentCategoryDTO.getId();
        Category parent = findAccessibleEntity(parentId).orElseThrow(() -> new IllegalArgumentException("Parent category is not accessible")
        );
        category.setParentCategory(parent);
    }

    private void validateParentCategoryImmutable(Category existingCategory, CategoryDTO parentCategoryDTO) {
        Long existingParentId = resolveParentCategoryId(existingCategory);
        Long requestedParentId = null;
        if (parentCategoryDTO != null && parentCategoryDTO.getId() != null) {
            requestedParentId = parentCategoryDTO.getId();
        }
        if (Objects.equals(existingParentId, requestedParentId)) {
            return;
        }
        throw new IllegalArgumentException("Parent category cannot be changed");
    }

    private void validateCategoryTypeChange(Category existingCategory, CategoryType previousCategoryType, CategoryType newCategoryType) {
        if (newCategoryType == null || newCategoryType.equals(previousCategoryType)) {
            return;
        }
        if (isCategoryInUse(existingCategory.getId())) {
            throw new IllegalArgumentException("Category type cannot be changed while category is in use");
        }
        validateChildCategoryTypeMatchesParent(existingCategory.getParentCategory(), newCategoryType);
    }

    private void validateChildCategoryTypeMatchesParent(Category category) {
        validateChildCategoryTypeMatchesParent(category.getParentCategory(), category.getCategoryType());
    }

    private void validateChildCategoryTypeMatchesParent(Category parentCategory, CategoryType categoryType) {
        if (parentCategory == null || categoryType == null) {
            return;
        }
        if (!categoryType.equals(parentCategory.getCategoryType())) {
            throw new IllegalArgumentException("Child category type must match parent category type");
        }
    }

    private boolean isCategoryInUse(Long categoryId) {
        return (
            categoryRepository.existsByParentCategoryId(categoryId) ||
            categoryRepository.existsFinancialTransactionByCategoryId(categoryId) ||
            categoryRepository.existsFinancialSubscriptionByCategoryId(categoryId) ||
            categoryRepository.existsBudgetCategoryLinkByCategoryId(categoryId) ||
            categoryRepository.existsTransactionRuleByResultingCategoryId(categoryId)
        );
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    private Long resolveParentCategoryId(Category category) {
        if (category.getParentCategory() == null) {
            return null;
        }
        return category.getParentCategory().getId();
    }

    private void validateUniqueSiblingNameForOwner(
        Long userId,
        CategoryType categoryType,
        Long parentCategoryId,
        String name,
        Long excludeCategoryId
    ) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (categoryRepository.existsByOwnerTypeParentAndNormalizedName(userId, categoryType, parentCategoryId, name, excludeCategoryId)) {
            throw new IllegalArgumentException("Category name already exists for this scope");
        }
    }
}
