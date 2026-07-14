package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.TagMapper;
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
class TagServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";
    private static final Instant EXISTING_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXISTING_UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant CLIENT_CREATED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant CLIENT_UPDATED_AT = Instant.parse("2000-01-02T00:00:00Z");

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        tag = new Tag();
        tag.setId(10L);
        tag.setName("Travel");
        tag.setUser(currentUser);
        tag.setCreatedAt(EXISTING_CREATED_AT);
        tag.setUpdatedAt(EXISTING_UPDATED_AT);

        tagDTO = new TagDTO();
        tagDTO.setId(10L);
        tagDTO.setName("Travel");
        tagDTO.setCreatedAt(EXISTING_CREATED_AT);
        tagDTO.setUpdatedAt(EXISTING_UPDATED_AT);

        objectMapper = new ObjectMapper();
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Tag mappedEntity = new Tag();
        mappedEntity.setName("Travel");
        mappedEntity.setCreatedAt(CLIENT_CREATED_AT);
        mappedEntity.setUpdatedAt(CLIENT_UPDATED_AT);
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
        assertThat(mappedEntity.getCreatedAt()).isNotNull().isNotEqualTo(CLIENT_CREATED_AT);
        assertThat(mappedEntity.getUpdatedAt()).isNotNull().isNotEqualTo(CLIENT_UPDATED_AT);
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
        assertThat(mappedEntity.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(mappedEntity.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
    }

    @Test
    void updateShouldRejectChangedCreatedAt() {
        tagDTO.setCreatedAt(CLIENT_CREATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.update(tagDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullCreatedAt() {
        tagDTO.setCreatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.update(tagDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectChangedUpdatedAt() {
        tagDTO.setUpdatedAt(CLIENT_UPDATED_AT);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.update(tagDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullUpdatedAt() {
        tagDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.update(tagDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveCreatedAtAndSetUpdatedAt() {
        tagDTO.setCreatedAt(null);
        tagDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));
        when(tagRepository.save(tag)).thenReturn(tag);
        when(tagMapper.toDto(tag)).thenReturn(tagDTO);

        tagService.partialUpdate(tagDTO, objectMapper.createObjectNode().put("id", 10L).put("description", "Updated"));

        assertThat(tag.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(tag.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
        verify(tagRepository).save(tag);
    }

    @Test
    void partialUpdateShouldAllowSameTimestampsAsNoOp() {
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.put("createdAt", EXISTING_CREATED_AT.toString());
        patchNode.put("updatedAt", EXISTING_UPDATED_AT.toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));
        when(tagRepository.save(tag)).thenReturn(tag);
        when(tagMapper.toDto(tag)).thenReturn(tagDTO);

        tagService.partialUpdate(tagDTO, patchNode);

        assertThat(tag.getCreatedAt()).isEqualTo(EXISTING_CREATED_AT);
        assertThat(tag.getUpdatedAt()).isNotNull().isNotEqualTo(EXISTING_UPDATED_AT);
    }

    @Test
    void partialUpdateShouldRejectChangedCreatedAt() {
        tagDTO.setCreatedAt(CLIENT_CREATED_AT);
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.put("createdAt", CLIENT_CREATED_AT.toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.partialUpdate(tagDTO, patchNode)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullCreatedAt() {
        tagDTO.setCreatedAt(null);
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.putNull("createdAt");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.partialUpdate(tagDTO, patchNode)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectChangedUpdatedAt() {
        tagDTO.setUpdatedAt(CLIENT_UPDATED_AT);
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.put("updatedAt", CLIENT_UPDATED_AT.toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.partialUpdate(tagDTO, patchNode)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullUpdatedAt() {
        tagDTO.setUpdatedAt(null);
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.putNull("updatedAt");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThatThrownBy(() -> tagService.partialUpdate(tagDTO, patchNode)).isInstanceOf(IllegalArgumentException.class);
        verify(tagRepository, never()).save(any());
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
    void deleteShouldUnlinkRelationshipsBeforeDeletingTag() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(tag));

        assertThat(tagService.delete(10L)).isTrue();

        verify(tagRepository).deleteFinancialTransactionTagLinksByTagId(10L);
        verify(tagRepository).deleteTransactionRuleResultingTagLinksByTagId(10L);
        verify(tagRepository).deleteFinancialSubscriptionTagLinksByTagId(10L);
        verify(tagRepository).deleteBudgetTagLinksByTagId(10L);
        verify(tagRepository).deleteById(10L);
    }

    @Test
    void deleteShouldReturnFalseWhenTagIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(tagService.delete(10L)).isFalse();
        verify(tagRepository, never()).deleteFinancialTransactionTagLinksByTagId(any());
        verify(tagRepository, never()).deleteById(any());
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
