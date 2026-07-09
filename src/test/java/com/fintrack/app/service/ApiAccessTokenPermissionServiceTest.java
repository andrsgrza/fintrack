package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ApiPermission;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenPermissionMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiAccessTokenPermissionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    @Mock
    private ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApiAccessTokenService apiAccessTokenService;

    @InjectMocks
    private ApiAccessTokenPermissionService apiAccessTokenPermissionService;

    private User currentUser;
    private ApiAccessToken apiAccessToken;
    private ApiAccessToken anotherApiAccessToken;
    private ApiAccessTokenPermission apiAccessTokenPermission;
    private ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO;
    private ApiAccessTokenDTO apiAccessTokenDTO;
    private ApiAccessTokenDTO anotherApiAccessTokenDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        apiAccessToken = new ApiAccessToken();
        apiAccessToken.setId(10L);
        apiAccessToken.setName("token-a");
        apiAccessToken.setUser(currentUser);

        anotherApiAccessToken = new ApiAccessToken();
        anotherApiAccessToken.setId(11L);
        anotherApiAccessToken.setName("token-b");
        anotherApiAccessToken.setUser(currentUser);

        apiAccessTokenPermission = new ApiAccessTokenPermission();
        apiAccessTokenPermission.setId(100L);
        apiAccessTokenPermission.setPermission(ApiPermission.CREATE_TRANSACTIONS);
        apiAccessTokenPermission.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        apiAccessTokenPermission.setApiAccessToken(apiAccessToken);

        apiAccessTokenPermissionDTO = new ApiAccessTokenPermissionDTO();
        apiAccessTokenPermissionDTO.setId(100L);
        apiAccessTokenPermissionDTO.setPermission(ApiPermission.CREATE_TRANSACTIONS);

        apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(10L);
        anotherApiAccessTokenDTO = new ApiAccessTokenDTO();
        anotherApiAccessTokenDTO.setId(11L);
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);
    }

    @Test
    void saveShouldResolveAccessibleApiAccessTokenAndAssignCreatedAt() {
        ApiAccessTokenPermission mappedEntity = new ApiAccessTokenPermission();

        when(apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO)).thenReturn(mappedEntity);
        when(apiAccessTokenService.findAccessibleApiAccessTokenEntity(10L)).thenReturn(Optional.of(apiAccessToken));
        when(apiAccessTokenPermissionRepository.existsByApiAccessTokenIdAndPermission(10L, ApiPermission.CREATE_TRANSACTIONS)).thenReturn(
            false
        );
        when(apiAccessTokenPermissionRepository.save(mappedEntity)).thenReturn(apiAccessTokenPermission);
        when(apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission)).thenReturn(apiAccessTokenPermissionDTO);

        apiAccessTokenPermissionService.save(apiAccessTokenPermissionDTO);

        assertThat(mappedEntity.getApiAccessToken()).isEqualTo(apiAccessToken);
        assertThat(mappedEntity.getCreatedAt()).isNotNull();
        verify(apiAccessTokenPermissionRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectDuplicatePermissionForToken() {
        ApiAccessTokenPermission mappedEntity = new ApiAccessTokenPermission();

        when(apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO)).thenReturn(mappedEntity);
        when(apiAccessTokenService.findAccessibleApiAccessTokenEntity(10L)).thenReturn(Optional.of(apiAccessToken));
        when(apiAccessTokenPermissionRepository.existsByApiAccessTokenIdAndPermission(10L, ApiPermission.CREATE_TRANSACTIONS)).thenReturn(
            true
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.save(apiAccessTokenPermissionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Permission already exists for this api access token");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectApiAccessTokenChange() {
        apiAccessTokenPermissionDTO.setApiAccessToken(anotherApiAccessTokenDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.update(apiAccessTokenPermissionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Api access token cannot be changed");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void updateShouldSucceedWhenImmutableFieldsAreUnchanged() {
        apiAccessTokenPermissionDTO.setCreatedAt(apiAccessTokenPermission.getCreatedAt());
        ApiAccessTokenPermission mappedEntity = new ApiAccessTokenPermission();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );
        when(apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO)).thenReturn(mappedEntity);
        when(apiAccessTokenPermissionRepository.save(mappedEntity)).thenReturn(apiAccessTokenPermission);
        when(apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission)).thenReturn(apiAccessTokenPermissionDTO);

        apiAccessTokenPermissionService.update(apiAccessTokenPermissionDTO);

        assertThat(mappedEntity.getApiAccessToken()).isEqualTo(apiAccessToken);
        assertThat(mappedEntity.getPermission()).isEqualTo(ApiPermission.CREATE_TRANSACTIONS);
        assertThat(mappedEntity.getCreatedAt()).isEqualTo(apiAccessTokenPermission.getCreatedAt());
    }

    @Test
    void updateShouldRejectCreatedAtChange() {
        apiAccessTokenPermissionDTO.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.update(apiAccessTokenPermissionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectCreatedAtChange() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("createdAt", "2026-02-01T00:00:00Z");
        apiAccessTokenPermissionDTO.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.partialUpdate(apiAccessTokenPermissionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveApiAccessTokenWhenFieldAbsent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);

        apiAccessTokenPermissionDTO.setApiAccessToken(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );
        when(apiAccessTokenPermissionRepository.save(apiAccessTokenPermission)).thenReturn(apiAccessTokenPermission);
        when(apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission)).thenReturn(apiAccessTokenPermissionDTO);

        Optional<ApiAccessTokenPermissionDTO> result = apiAccessTokenPermissionService.partialUpdate(
            apiAccessTokenPermissionDTO,
            patchNode
        );

        assertThat(result).isPresent();
        assertThat(apiAccessTokenPermission.getApiAccessToken()).isEqualTo(apiAccessToken);
    }

    @Test
    void partialUpdateShouldRejectNullApiAccessToken() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("apiAccessToken");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.partialUpdate(apiAccessTokenPermissionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Api access token cannot be null");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectDifferentApiAccessToken() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.set("apiAccessToken", objectMapper.createObjectNode().put("id", 11L));
        apiAccessTokenPermissionDTO.setApiAccessToken(anotherApiAccessTokenDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessTokenPermission)
        );

        assertThatThrownBy(() -> apiAccessTokenPermissionService.partialUpdate(apiAccessTokenPermissionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Api access token cannot be changed");

        verify(apiAccessTokenPermissionRepository, never()).save(any());
    }

    @Test
    void deleteShouldReturnFalseWhenPermissionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(apiAccessTokenPermissionService.delete(100L)).isFalse();
        verify(apiAccessTokenPermissionRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedQueryForRegularUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenPermissionRepository.findAllWithEagerRelationshipsByTokenUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            java.util.List.of(apiAccessTokenPermission)
        );
        when(apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission)).thenReturn(apiAccessTokenPermissionDTO);

        assertThat(apiAccessTokenPermissionService.findAll()).hasSize(1);
        verify(apiAccessTokenPermissionRepository).findAllWithEagerRelationshipsByTokenUserLogin(CURRENT_USER_LOGIN);
        verify(apiAccessTokenPermissionRepository, never()).findAllWithEagerRelationships();
    }
}
