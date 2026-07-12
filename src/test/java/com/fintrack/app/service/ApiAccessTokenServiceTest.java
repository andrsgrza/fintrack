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
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiAccessTokenServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private ApiAccessTokenRepository apiAccessTokenRepository;

    @Mock
    private ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    @Mock
    private ApiAccessTokenMapper apiAccessTokenMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ApiAccessTokenService apiAccessTokenService;

    private User currentUser;
    private ApiAccessToken apiAccessToken;
    private ApiAccessTokenDTO apiAccessTokenDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        apiAccessToken = new ApiAccessToken();
        apiAccessToken.setId(10L);
        apiAccessToken.setName("Import token");
        apiAccessToken.setTokenPrefix("ftk_");
        apiAccessToken.setTokenHash("hash-value");
        apiAccessToken.setStatus(ApiTokenStatus.ACTIVE);
        apiAccessToken.setCreatedAt(Instant.now());
        apiAccessToken.setUpdatedAt(Instant.now());
        apiAccessToken.setUser(currentUser);

        apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(10L);
        apiAccessTokenDTO.setName("Import token");
        apiAccessTokenDTO.setTokenPrefix("ftk_");
        apiAccessTokenDTO.setTokenHash("hash-value");
        apiAccessTokenDTO.setStatus(ApiTokenStatus.ACTIVE);
        apiAccessTokenDTO.setCreatedAt(Instant.now());
        apiAccessTokenDTO.setUpdatedAt(Instant.now());
    }

    @Test
    void saveShouldGenerateTokenSecretsWhenHashOmitted() {
        ApiAccessTokenDTO dto = new ApiAccessTokenDTO();
        dto.setName("Generated token");

        ApiAccessToken mappedEntity = new ApiAccessToken();
        when(apiAccessTokenMapper.toEntity(dto)).thenReturn(mappedEntity);
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(apiAccessTokenRepository.save(mappedEntity)).thenAnswer(invocation -> {
            ApiAccessToken saved = invocation.getArgument(0);
            saved.setId(10L);
            saved.setTokenHash(dto.getTokenHash());
            saved.setTokenPrefix(dto.getTokenPrefix());
            saved.setStatus(dto.getStatus());
            return saved;
        });
        when(apiAccessTokenMapper.toDto(mappedEntity)).thenReturn(dto);

        ApiAccessTokenDTO result = apiAccessTokenService.save(dto);

        assertThat(dto.getTokenHash()).isNotBlank();
        assertThat(dto.getTokenPrefix()).startsWith("ftk_");
        assertThat(dto.getStatus()).isEqualTo(ApiTokenStatus.ACTIVE);
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getUpdatedAt()).isNotNull();
        assertThat(result.getRawToken()).isNotBlank();
        verify(apiAccessTokenRepository).save(mappedEntity);
    }

    @Test
    void saveShouldAssignCurrentUserAndPersistTokenHash() {
        ApiAccessTokenDTO dto = new ApiAccessTokenDTO();
        dto.setName("Import token");
        ApiAccessToken mappedEntity = new ApiAccessToken();

        when(apiAccessTokenMapper.toEntity(dto)).thenReturn(mappedEntity);
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(apiAccessTokenRepository.save(mappedEntity)).thenReturn(apiAccessToken);
        when(apiAccessTokenMapper.toDto(apiAccessToken)).thenReturn(dto);

        apiAccessTokenService.save(dto);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(apiAccessTokenRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectClientProvidedTokenHash() {
        assertThatThrownBy(() -> apiAccessTokenService.save(apiAccessTokenDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token hash is server-generated");

        verify(apiAccessTokenRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectTokenHashChange() {
        apiAccessTokenDTO.setTokenHash("different-hash");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessToken)
        );

        assertThatThrownBy(() -> apiAccessTokenService.update(apiAccessTokenDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token hash cannot be changed");

        verify(apiAccessTokenRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullUser() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.putNull("user");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessToken)
        );

        assertThatThrownBy(() -> apiAccessTokenService.partialUpdate(apiAccessTokenDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User cannot be null");

        verify(apiAccessTokenRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectTokenPrefixChange() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.put("tokenPrefix", "new_prefix");
        apiAccessTokenDTO.setTokenPrefix("new_prefix");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessToken)
        );

        assertThatThrownBy(() -> apiAccessTokenService.partialUpdate(apiAccessTokenDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token prefix cannot be changed");

        verify(apiAccessTokenRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullTokenPrefix() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 10L);
        patchNode.putNull("tokenPrefix");
        apiAccessTokenDTO.setTokenPrefix(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessToken)
        );

        assertThatThrownBy(() -> apiAccessTokenService.partialUpdate(apiAccessTokenDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Token prefix cannot be changed");

        verify(apiAccessTokenRepository, never()).save(any());
    }

    @Test
    void deleteShouldCascadePermissionChildren() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiAccessToken)
        );

        assertThat(apiAccessTokenService.delete(10L)).isTrue();
        verify(apiAccessTokenPermissionRepository).deleteByApiAccessTokenId(10L);
        verify(apiAccessTokenRepository).deleteById(10L);
    }

    @Test
    void deleteShouldReturnFalseWhenTokenIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(apiAccessTokenService.delete(10L)).isFalse();
        verify(apiAccessTokenRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedQueryForRegularUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiAccessTokenRepository.findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            java.util.List.of(apiAccessToken)
        );
        when(apiAccessTokenMapper.toDto(apiAccessToken)).thenReturn(apiAccessTokenDTO);

        assertThat(apiAccessTokenService.findAll()).hasSize(1);
        verify(apiAccessTokenRepository).findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN);
        verify(apiAccessTokenRepository, never()).findAllWithEagerRelationships();
    }
}
