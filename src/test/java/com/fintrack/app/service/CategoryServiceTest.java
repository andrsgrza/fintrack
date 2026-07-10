package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
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

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        category = new Category();
        category.setId(10L);
        category.setName("Food");
        category.setUser(currentUser);

        categoryDTO = new CategoryDTO();
        categoryDTO.setId(10L);
        categoryDTO.setName("Food");
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Category mappedEntity = new Category();
        Category savedEntity = new Category();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(categoryMapper.toDto(savedEntity)).thenReturn(categoryDTO);

        categoryService.save(categoryDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(categoryRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        Category mappedEntity = new Category();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.save(mappedEntity)).thenReturn(category);
        when(categoryMapper.toDto(category)).thenReturn(categoryDTO);

        categoryService.update(categoryDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
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
    void deleteShouldRemoveAccessibleCategory() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));

        assertThat(categoryService.delete(10L)).isTrue();
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
    void updateShouldRejectSelfAsParent() {
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(10L);
        categoryDTO.setParentCategory(parentDTO);

        Category mappedEntity = new Category();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> categoryService.update(categoryDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectInaccessibleParent() {
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(20L);
        categoryDTO.setParentCategory(parentDTO);

        Category mappedEntity = new Category();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(category));
        when(categoryMapper.toEntity(categoryDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(20L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(categoryDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }
}
