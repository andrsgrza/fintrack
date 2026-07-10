package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
        applyParentCategory(category, categoryDTO.getParentCategory(), null);
        category.setName(normalizeName(category.getName()));
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
        Category category = categoryMapper.toEntity(categoryDTO);
        category.setUser(existingCategory.getUser());
        applyParentCategory(category, categoryDTO.getParentCategory(), existingCategory.getId());
        category.setName(normalizeName(category.getName()));
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

                categoryMapper.partialUpdate(existingCategory, categoryDTO);
                if (parentProvided) {
                    applyParentCategory(existingCategory, categoryDTO.getParentCategory(), existingCategory.getId());
                }
                if (nameProvided) {
                    existingCategory.setName(normalizeName(categoryDTO.getName()));
                }
                if (nameProvided || parentProvided || categoryTypeProvided) {
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
        categoryRepository.deleteById(id);
        return true;
    }

    private Optional<Category> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return categoryRepository.findOneWithEagerRelationships(id);
        }
        return categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyParentCategory(Category category, CategoryDTO parentCategoryDTO, Long categoryId) {
        if (parentCategoryDTO == null || parentCategoryDTO.getId() == null) {
            category.setParentCategory(null);
            return;
        }
        Long parentId = parentCategoryDTO.getId();
        if (categoryId != null && parentId.equals(categoryId)) {
            throw new IllegalArgumentException("Category cannot be its own parent");
        }
        Category parent = findAccessibleEntity(parentId).orElseThrow(() -> new IllegalArgumentException("Parent category is not accessible")
        );
        validateNoCycle(categoryId, parent);
        category.setParentCategory(parent);
    }

    private void validateNoCycle(Long categoryId, Category parent) {
        if (categoryId == null) {
            return;
        }
        Set<Long> visitedParentIds = new HashSet<>();
        Category current = parent;
        while (current != null && current.getId() != null) {
            Long currentId = current.getId();
            if (currentId.equals(categoryId)) {
                throw new IllegalArgumentException("Category hierarchy cannot contain a cycle");
            }
            if (!visitedParentIds.add(currentId)) {
                throw new IllegalArgumentException("Category hierarchy cannot contain a cycle");
            }
            Category parentCategory = current.getParentCategory();
            if (parentCategory == null || parentCategory.getId() == null) {
                break;
            }
            current = categoryRepository.findOneWithToOneRelationships(parentCategory.getId()).orElse(parentCategory);
        }
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
