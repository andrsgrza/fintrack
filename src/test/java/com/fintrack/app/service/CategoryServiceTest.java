package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";
    private static final Instant EXISTING_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXISTING_UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant CLIENT_CREATED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant CLIENT_UPDATED_AT = Instant.parse("2000-01-02T00:00:00Z");

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CategoryService categoryService;

    private User currentUser;
    private Category category;
    private CategoryDTO categoryDTO;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        category = new Category();
        category.setId(10L);
        category.setName("Food");
        category.setCategoryType(CategoryType.EXPENSE);
        category.setUser(currentUser);
        category.setCreatedAt(EXISTING_CREATED_AT);
        category.setUpdatedAt(EXISTING_UPDATED_AT);

        categoryDTO = new CategoryDTO();
        categoryDTO.setId(10L);
        categoryDTO.setName("Food");
        categoryDTO.setCategoryType(CategoryType.EXPENSE);
        categoryDTO.setCreatedAt(EXISTING_CREATED_AT);
        categoryDTO.setUpdatedAt(EXISTING_UPDATED_AT);
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Category mappedEntity = new Category();
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.EXPENSE);
        mappedEntity.setCreatedAt(CLIENT_CREATED_AT);
        mappedEntity.setUpdatedAt(CLIENT_UPDATED_AT);
        Category savedEntity = new Category();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, null, "Food", null)).thenReturn(false);
        when(categoryRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(categoryMapper.toDto(savedEntity)).thenReturn(categoryDTO);

        categoryService.save(categoryDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getCreatedAt()).isNotNull().isNotEqualTo(CLIENT_CREATED_AT);
        assertThat(mappedEntity.getUpdatedAt()).isNotNull().isNotEqualTo(CLIENT_UPDATED_AT);
        verify(categoryRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        Category mappedEntity = new Category();
        mappedEntity.setId(10L);
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.EXPENSE);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, null, "Food", 10L)).thenReturn(false);
        when(categoryRepository.save(mappedEntity)).thenReturn(category);
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        categoryService.update(categoryDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(mappedEntity.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
    }

    @Test
    void updateShouldRejectChangedCreatedAt() {
        categoryDTO.setCreatedAt(CLIENT_CREATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullCreatedAt() {
        categoryDTO.setCreatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectChangedUpdatedAt() {
        categoryDTO.setUpdatedAt(CLIENT_UPDATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullUpdatedAt() {
        categoryDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveCreatedAtAndSetUpdatedAt() {
        categoryDTO.setName("Groceries");
        categoryDTO.setCreatedAt(null);
        categoryDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, null, "Groceries", 10L)).thenReturn(
            false
        );
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        categoryService.partialUpdate(categoryDTO, objectMapper.createObjectNode().put("id", 10L).put("name", "Groceries"));

        assertThat(category.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(category.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
    }

    @Test
    void partialUpdateShouldAllowSameTimestampsAsNoOp() {
        categoryDTO.setCreatedAt(EXISTING_CREATED_AT);
        categoryDTO.setUpdatedAt(EXISTING_UPDATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        categoryService.partialUpdate(
            categoryDTO,
            objectMapper
                .createObjectNode()
                .put("id", 10L)
                .put("createdAt", EXISTING_CREATED_AT.toString())
                .put("updatedAt", EXISTING_UPDATED_AT.toString())
        );

        assertThat(category.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(category.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
    }

    @Test
    void partialUpdateShouldRejectChangedCreatedAt() {
        categoryDTO.setCreatedAt(CLIENT_CREATED_AT);
        categoryDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() ->
            categoryService.partialUpdate(
                categoryDTO,
                objectMapper.createObjectNode().put("id", 10L).put("createdAt", CLIENT_CREATED_AT.toString())
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullCreatedAt() {
        categoryDTO.setCreatedAt(null);
        categoryDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() ->
            categoryService.partialUpdate(categoryDTO, objectMapper.createObjectNode().put("id", 10L).putNull("createdAt"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectChangedUpdatedAt() {
        categoryDTO.setCreatedAt(null);
        categoryDTO.setUpdatedAt(CLIENT_UPDATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() ->
            categoryService.partialUpdate(
                categoryDTO,
                objectMapper.createObjectNode().put("id", 10L).put("updatedAt", CLIENT_UPDATED_AT.toString())
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullUpdatedAt() {
        categoryDTO.setCreatedAt(null);
        categoryDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() ->
            categoryService.partialUpdate(categoryDTO, objectMapper.createObjectNode().put("id", 10L).putNull("updatedAt"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenCategoryIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(categoryDTO)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersCategory() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(categoryService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(categoryRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(category));
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        Optional<CategoryDTO> result = categoryService.findOne(10L);

        assertThat(result).contains(categoryDTO);
        verify(categoryRepository).findOneWithEagerRelationships(10L);
        verify(categoryRepository, never()).findOneWithToOneRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenCategoryIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(categoryService.delete(10L)).isFalse();
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRejectWhenCategoryHasChildren() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentCategoryId(10L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Category with child categories cannot be deleted");
        verify(categoryRepository, never()).clearFinancialTransactionCategoryReferences(any());
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldUnlinkRelationshipsBeforeDeletingCategory() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentCategoryId(10L)).thenReturn(false);

        assertThat(categoryService.delete(10L)).isTrue();

        verify(categoryRepository).clearFinancialTransactionCategoryReferences(10L);
        verify(categoryRepository).clearFinancialSubscriptionCategoryReferences(10L);
        verify(categoryRepository).deleteBudgetCategoryLinksByCategoryId(10L);
        verify(categoryRepository).clearTransactionRuleResultingCategoryReferences(10L);
        verify(categoryRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<Category> page = new PageImpl<>(java.util.List.of(category));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findAllWithToOneRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        Page<CategoryDTO> result = categoryService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(categoryDTO);
        verify(categoryRepository).findAllWithToOneRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenCategoryIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(categoryService.partialUpdate(categoryDTO)).isEmpty();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectParentCategoryChange() {
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(20L);
        categoryDTO.setParentCategory(parentDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parent category cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectParentCategoryChange() {
        Category parent = new Category();
        parent.setId(20L);

        Category existingCategory = new Category();
        existingCategory.setId(10L);
        existingCategory.setName("Food");
        existingCategory.setCategoryType(CategoryType.EXPENSE);
        existingCategory.setUser(currentUser);

        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(20L);
        categoryDTO.setParentCategory(parentDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(existingCategory)
        );

        assertThatThrownBy(() -> categoryService.partialUpdate(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parent category cannot be changed");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectDuplicateSiblingName() {
        Category mappedEntity = new Category();
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.EXPENSE);
        mappedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, null, "Food", null)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.save(categoryDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void saveShouldAllowSameNameUnderDifferentParent() {
        Category mappedEntity = new Category();
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.EXPENSE);
        mappedEntity.setUser(currentUser);

        Category parent = new Category();
        parent.setId(30L);
        parent.setCategoryType(CategoryType.EXPENSE);
        mappedEntity.setParentCategory(parent);

        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(30L);
        categoryDTO.setParentCategory(parentDTO);

        Category savedEntity = new Category();
        savedEntity.setId(11L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, 30L, "Food", null)).thenReturn(false);
        when(categoryRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(categoryMapper.toDto(savedEntity)).thenReturn(categoryDTO);

        categoryService.save(categoryDTO);

        verify(categoryRepository).existsByOwnerTypeParentAndNormalizedName(2L, CategoryType.EXPENSE, 30L, "Food", null);
    }

    @Test
    void saveShouldRejectChildCategoryTypeMismatchWithParent() {
        Category parent = new Category();
        parent.setId(30L);
        parent.setCategoryType(CategoryType.EXPENSE);

        Category mappedEntity = new Category();
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.INCOME);
        mappedEntity.setParentCategory(parent);

        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(30L);
        categoryDTO.setParentCategory(parentDTO);
        categoryDTO.setCategoryType(CategoryType.INCOME);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> categoryService.save(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Child category type must match parent category type");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectCategoryTypeChangeWhenCategoryIsInUse() {
        categoryDTO.setCategoryType(CategoryType.INCOME);

        Category mappedEntity = new Category();
        mappedEntity.setId(10L);
        mappedEntity.setName("Food");
        mappedEntity.setCategoryType(CategoryType.INCOME);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.existsByParentCategoryId(10L)).thenReturn(false);
        when(categoryRepository.existsFinancialTransactionByCategoryId(10L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(categoryDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Category type cannot be changed while category is in use");
        verify(categoryRepository, never()).save(any());
    }
}
