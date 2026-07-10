package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.TagMapper;
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
class TagServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TagService tagService;

    private User currentUser;
    private Tag tag;
    private TagDTO tagDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        tag = new Tag();
        tag.setId(10L);
        tag.setName("Travel");
        tag.setUser(currentUser);

        tagDTO = new TagDTO();
        tagDTO.setId(10L);
        tagDTO.setName("Travel");
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Tag mappedEntity = new Tag();
        mappedEntity.setName("Travel");
        Tag savedEntity = new Tag();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(tagMapper.toEntity(tagDTO)).thenReturn(mappedEntity);
        when(tagRepository.existsByUserIdAndNormalizedName(2L, "Travel", null)).thenReturn(false);
        when(tagRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(tagMapper.toDto(savedEntity)).thenReturn(tagDTO);

        tagService.save(tagDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getName()).isEqualTo("Travel");
        verify(tagRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        Tag mappedEntity = new Tag();
        mappedEntity.setId(10L);
        mappedEntity.setName("Travel");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));
        when(tagMapper.toEntity(tagDTO)).thenReturn(mappedEntity);
        when(tagRepository.existsByUserIdAndNormalizedName(2L, "Travel", 10L)).thenReturn(false);
        when(tagRepository.save(mappedEntity)).thenReturn(tag);
        when(tagMapper.toDto(tag)).thenReturn(tagDTO);

        tagService.update(tagDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void updateShouldFailWhenTagIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.update(tagDTO)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersTag() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(tagService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(tagRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(tag));
        when(tagMapper.toDto(tag)).thenReturn(tagDTO);

        Optional<TagDTO> result = tagService.findOne(10L);

        assertThat(result).contains(tagDTO);
        verify(tagRepository).findOneWithEagerRelationships(10L);
        verify(tagRepository, never()).findOneWithToOneRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenTagIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(tagService.delete(10L)).isFalse();
        verify(tagRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleTag() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThat(tagService.delete(10L)).isTrue();
        verify(tagRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<Tag> page = new PageImpl<>(java.util.List.of(tag));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findAllWithToOneRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(tagMapper.toDto(tag)).thenReturn(tagDTO);

        Page<TagDTO> result = tagService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(tagDTO);
        verify(tagRepository).findAllWithToOneRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenTagIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(tagService.partialUpdate(tagDTO)).isEmpty();
        verify(tagRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectDuplicateNameForSameUser() {
        Tag mappedEntity = new Tag();
        mappedEntity.setName("Comida");

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(tagMapper.toEntity(tagDTO)).thenReturn(mappedEntity);
        when(tagRepository.existsByUserIdAndNormalizedName(2L, "Comida", null)).thenReturn(true);

        tagDTO.setName(" COMIDA ");

        assertThatThrownBy(() -> tagService.save(tagDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tag name already exists");
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectDuplicateNameForOwner() {
        Tag mappedEntity = new Tag();
        mappedEntity.setName("Comida");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));
        when(tagMapper.toEntity(tagDTO)).thenReturn(mappedEntity);
        when(tagRepository.existsByUserIdAndNormalizedName(2L, "Comida", 10L)).thenReturn(true);

        tagDTO.setName("comida");

        assertThatThrownBy(() -> tagService.update(tagDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tag name already exists");
        verify(tagRepository, never()).save(any());
    }

    @Test
    void saveShouldAllowSameNameForDifferentOwner() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setLogin("other-user");

        Tag mappedEntity = new Tag();
        mappedEntity.setName("Comida");

        tagDTO.setName("Comida");

        when(currentUserService.getCurrentUser()).thenReturn(otherUser);
        when(tagMapper.toEntity(tagDTO)).thenReturn(mappedEntity);
        when(tagRepository.existsByUserIdAndNormalizedName(99L, "Comida", null)).thenReturn(false);
        when(tagRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(tagMapper.toDto(mappedEntity)).thenReturn(tagDTO);

        tagService.save(tagDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(otherUser);
        assertThat(mappedEntity.getName()).isEqualTo("Comida");
        verify(tagRepository).existsByUserIdAndNormalizedName(99L, "Comida", null);
    }
}
