package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.UserDashboardPreference;
import com.fintrack.app.repository.UserDashboardPreferenceRepository;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.dto.UserDashboardPreferenceDTO;
import com.fintrack.app.service.mapper.UserDashboardPreferenceMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDashboardPreferenceServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private UserDashboardPreferenceRepository userDashboardPreferenceRepository;

    @Mock
    private UserDashboardPreferenceMapper userDashboardPreferenceMapper;

    @Mock
    private CurrentUserService currentUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserDashboardPreferenceService userDashboardPreferenceService;

    private User currentUser;
    private User otherUser;
    private UserDashboardPreference userDashboardPreference;
    private UserDashboardPreferenceDTO userDashboardPreferenceDTO;

    @BeforeEach
    void setUp() {
        userDashboardPreferenceService = new UserDashboardPreferenceService(
            userDashboardPreferenceRepository,
            userDashboardPreferenceMapper,
            currentUserService,
            objectMapper
        );

        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setLogin("other");

        userDashboardPreference = new UserDashboardPreference();
        userDashboardPreference.setId(100L);
        userDashboardPreference.setConfiguration("{}");
        userDashboardPreference.setCreatedAt(Instant.now());
        userDashboardPreference.setUpdatedAt(Instant.now());
        userDashboardPreference.setUser(currentUser);

        userDashboardPreferenceDTO = new UserDashboardPreferenceDTO();
        userDashboardPreferenceDTO.setId(100L);
        userDashboardPreferenceDTO.setConfiguration("{}");
        userDashboardPreferenceDTO.setCreatedAt(Instant.now());
        userDashboardPreferenceDTO.setUpdatedAt(Instant.now());
    }

    @Test
    void saveShouldAssignCurrentUser() {
        UserDashboardPreference mappedEntity = new UserDashboardPreference();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(false);
        when(userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO)).thenReturn(mappedEntity);
        when(userDashboardPreferenceRepository.save(mappedEntity)).thenReturn(userDashboardPreference);
        when(userDashboardPreferenceMapper.toDto(userDashboardPreference)).thenReturn(userDashboardPreferenceDTO);

        userDashboardPreferenceService.save(userDashboardPreferenceDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(userDashboardPreferenceRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectDuplicatePreferenceForUser() {
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(true);

        assertThatThrownBy(() -> userDashboardPreferenceService.save(userDashboardPreferenceDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User already has dashboard preferences");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        UserDashboardPreference mappedEntity = new UserDashboardPreference();
        mappedEntity.setId(100L);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(3L);
        userDashboardPreferenceDTO.setUser(otherUserDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(userDashboardPreference)
        );
        when(userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO)).thenReturn(mappedEntity);
        when(userDashboardPreferenceRepository.save(mappedEntity)).thenReturn(userDashboardPreference);
        when(userDashboardPreferenceMapper.toDto(userDashboardPreference)).thenReturn(userDashboardPreferenceDTO);

        userDashboardPreferenceService.update(userDashboardPreferenceDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void partialUpdateShouldRejectNullUser() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("user");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(userDashboardPreference)
        );

        assertThatThrownBy(() -> userDashboardPreferenceService.partialUpdate(userDashboardPreferenceDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User cannot be null");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveOwnerWhenUserFieldAbsent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("configuration", "{\"widgets\":[]}");

        userDashboardPreferenceDTO.setConfiguration("{\"widgets\":[]}");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(userDashboardPreference)
        );
        when(userDashboardPreferenceRepository.save(userDashboardPreference)).thenReturn(userDashboardPreference);
        when(userDashboardPreferenceMapper.toDto(userDashboardPreference)).thenReturn(userDashboardPreferenceDTO);

        Optional<UserDashboardPreferenceDTO> result = userDashboardPreferenceService.partialUpdate(userDashboardPreferenceDTO, patchNode);

        assertThat(result).isPresent();
        assertThat(userDashboardPreference.getUser()).isEqualTo(currentUser);
    }

    @Test
    void deleteShouldReturnFalseWhenPreferenceIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(userDashboardPreferenceService.delete(100L)).isFalse();
        verify(userDashboardPreferenceRepository, never()).deleteById(any());
    }

    @Test
    void saveShouldRejectNullConfiguration() {
        userDashboardPreferenceDTO.setConfiguration(null);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(false);

        assertThatThrownBy(() -> userDashboardPreferenceService.save(userDashboardPreferenceDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration is required");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectBlankConfiguration() {
        userDashboardPreferenceDTO.setConfiguration("   ");

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(false);

        assertThatThrownBy(() -> userDashboardPreferenceService.save(userDashboardPreferenceDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration is required");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectInvalidJsonConfiguration() {
        userDashboardPreferenceDTO.setConfiguration("not-json");

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(false);

        assertThatThrownBy(() -> userDashboardPreferenceService.save(userDashboardPreferenceDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration must be valid JSON");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectJsonPrimitiveConfiguration() {
        userDashboardPreferenceDTO.setConfiguration("\"hello\"");

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userDashboardPreferenceRepository.existsByUserId(2L)).thenReturn(false);

        assertThatThrownBy(() -> userDashboardPreferenceService.save(userDashboardPreferenceDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration must be a JSON object or array");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullConfiguration() throws Exception {
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("configuration");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(userDashboardPreference)
        );

        assertThatThrownBy(() -> userDashboardPreferenceService.partialUpdate(userDashboardPreferenceDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration cannot be null");

        verify(userDashboardPreferenceRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldSkipConfigurationValidationWhenFieldAbsent() throws Exception {
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("updatedAt", Instant.now().toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(userDashboardPreference)
        );
        when(userDashboardPreferenceRepository.save(userDashboardPreference)).thenReturn(userDashboardPreference);
        when(userDashboardPreferenceMapper.toDto(userDashboardPreference)).thenReturn(userDashboardPreferenceDTO);

        Optional<UserDashboardPreferenceDTO> result = userDashboardPreferenceService.partialUpdate(userDashboardPreferenceDTO, patchNode);

        assertThat(result).isPresent();
        verify(userDashboardPreferenceRepository).save(userDashboardPreference);
    }

    @Test
    void findAllShouldUseScopedQueryForRegularUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(userDashboardPreferenceRepository.findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            java.util.List.of(userDashboardPreference)
        );
        when(userDashboardPreferenceMapper.toDto(userDashboardPreference)).thenReturn(userDashboardPreferenceDTO);

        assertThat(userDashboardPreferenceService.findAll()).hasSize(1);
        verify(userDashboardPreferenceRepository).findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN);
        verify(userDashboardPreferenceRepository, never()).findAllWithEagerRelationships();
    }
}
