package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.ApiAccessTokenAsserts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ApiPermission;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.ApiAccessTokenService;
import com.fintrack.app.service.dto.ApiAccessTokenCreateRequestDTO;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiAccessTokenUpdateRequestDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link ApiAccessTokenResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class ApiAccessTokenResourceIT {

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_TOKEN_PREFIX = "AAAAAAAAAA";
    private static final String UPDATED_TOKEN_PREFIX = "BBBBBBBBBB";

    private static final String DEFAULT_TOKEN_HASH = "AAAAAAAAAA";
    private static final String UPDATED_TOKEN_HASH = "BBBBBBBBBB";

    private static final ApiTokenStatus DEFAULT_STATUS = ApiTokenStatus.ACTIVE;
    private static final ApiTokenStatus UPDATED_STATUS = ApiTokenStatus.REVOKED;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_LAST_USED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_LAST_USED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_EXPIRES_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_EXPIRES_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_REVOKED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_REVOKED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/api-access-tokens";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ApiAccessTokenRepository apiAccessTokenRepository;

    @Autowired
    private ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    @Autowired
    private ApiIngestionRepository apiIngestionRepository;

    @Mock
    private ApiAccessTokenRepository apiAccessTokenRepositoryMock;

    @Autowired
    private ApiAccessTokenMapper apiAccessTokenMapper;

    @Mock
    private ApiAccessTokenService apiAccessTokenServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restApiAccessTokenMockMvc;

    private ApiAccessToken apiAccessToken;

    private ApiAccessToken insertedApiAccessToken;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiAccessToken createEntity(EntityManager em) {
        ApiAccessToken apiAccessToken = new ApiAccessToken()
            .name(DEFAULT_NAME)
            .tokenPrefix(DEFAULT_TOKEN_PREFIX)
            .tokenHash(DEFAULT_TOKEN_HASH)
            .status(DEFAULT_STATUS)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT)
            .lastUsedAt(DEFAULT_LAST_USED_AT)
            .expiresAt(DEFAULT_EXPIRES_AT)
            .revokedAt(DEFAULT_REVOKED_AT);
        apiAccessToken.setUser(getCurrentMockUser(em));
        return apiAccessToken;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiAccessToken createUpdatedEntity(EntityManager em) {
        ApiAccessToken updatedApiAccessToken = new ApiAccessToken()
            .name(UPDATED_NAME)
            .tokenPrefix(UPDATED_TOKEN_PREFIX)
            .tokenHash(UPDATED_TOKEN_HASH)
            .status(UPDATED_STATUS)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT)
            .lastUsedAt(UPDATED_LAST_USED_AT)
            .expiresAt(UPDATED_EXPIRES_AT)
            .revokedAt(UPDATED_REVOKED_AT);
        updatedApiAccessToken.setUser(getCurrentMockUser(em));
        return updatedApiAccessToken;
    }

    private static User getCurrentMockUser(EntityManager em) {
        return TestUtil.findAll(em, User.class)
            .stream()
            .filter(user -> CURRENT_MOCK_USER_LOGIN.equals(user.getLogin()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Current mock user not found"));
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private ApiAccessTokenCreateRequestDTO toCreateRequest(ApiAccessToken apiAccessToken) {
        ApiAccessTokenCreateRequestDTO createRequest = new ApiAccessTokenCreateRequestDTO();
        createRequest.setName(apiAccessToken.getName());
        return createRequest;
    }

    private ApiAccessTokenUpdateRequestDTO toUpdateRequest(ApiAccessToken apiAccessToken) {
        ApiAccessTokenUpdateRequestDTO updateRequest = new ApiAccessTokenUpdateRequestDTO();
        updateRequest.setId(apiAccessToken.getId());
        updateRequest.setName(apiAccessToken.getName());
        updateRequest.setStatus(apiAccessToken.getStatus());
        updateRequest.setExpiresAt(apiAccessToken.getExpiresAt());
        return updateRequest;
    }

    @BeforeEach
    void initTest() {
        apiAccessToken = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedApiAccessToken != null) {
            apiAccessTokenRepository.delete(insertedApiAccessToken);
            insertedApiAccessToken = null;
        }
    }

    @Test
    @Transactional
    void createApiAccessToken() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ApiAccessToken
        ApiAccessTokenCreateRequestDTO createRequest = toCreateRequest(apiAccessToken);
        var returnedApiAccessTokenDTO = om.readValue(
            restApiAccessTokenMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiAccessTokenDTO.class
        );

        // Validate the ApiAccessToken in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedApiAccessTokenDTO.getId()).isNotNull();
        insertedApiAccessToken = apiAccessTokenRepository.findById(returnedApiAccessTokenDTO.getId()).orElseThrow();
        assertThat(insertedApiAccessToken.getTokenHash()).isNotBlank();
        assertThat(insertedApiAccessToken.getTokenPrefix()).startsWith("ftk_");
        assertThat(insertedApiAccessToken.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
    }

    @Test
    @Transactional
    void createApiAccessTokenWithExistingId() throws Exception {
        // Create the ApiAccessToken with an existing ID
        apiAccessToken.setId(1L);
        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restApiAccessTokenMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content("{\"id\":1,\"name\":\"" + DEFAULT_NAME + "\"}"))
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiAccessToken.setName(null);

        // Create the ApiAccessToken, which fails.
        ApiAccessTokenCreateRequestDTO createRequest = toCreateRequest(apiAccessToken);

        restApiAccessTokenMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createRequest)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createApiAccessTokenWithNameOnlyGeneratesSecrets() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        ApiAccessTokenCreateRequestDTO createRequest = new ApiAccessTokenCreateRequestDTO();
        createRequest.setName("Import token");

        var returnedApiAccessTokenDTO = om.readValue(
            restApiAccessTokenMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.tokenPrefix").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rawToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiAccessTokenDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        insertedApiAccessToken = apiAccessTokenRepository.findById(returnedApiAccessTokenDTO.getId()).orElseThrow();
        assertThat(insertedApiAccessToken.getTokenHash()).isNotBlank();
        assertThat(insertedApiAccessToken.getTokenPrefix()).startsWith("ftk_");
        assertThat(insertedApiAccessToken.getStatus()).isEqualTo(DEFAULT_STATUS);
        apiAccessTokenRepository.delete(insertedApiAccessToken);
        insertedApiAccessToken = null;
    }

    @Test
    @Transactional
    void getAllApiAccessTokens() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        // Get all the apiAccessTokenList
        restApiAccessTokenMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiAccessToken.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].tokenPrefix").value(hasItem(DEFAULT_TOKEN_PREFIX)))
            .andExpect(jsonPath("$.[*].tokenHash").doesNotExist())
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())))
            .andExpect(jsonPath("$.[*].lastUsedAt").value(hasItem(DEFAULT_LAST_USED_AT.toString())))
            .andExpect(jsonPath("$.[*].expiresAt").value(hasItem(DEFAULT_EXPIRES_AT.toString())))
            .andExpect(jsonPath("$.[*].revokedAt").value(hasItem(DEFAULT_REVOKED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiAccessTokensWithEagerRelationshipsIsEnabled() throws Exception {
        when(apiAccessTokenServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiAccessTokenMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(apiAccessTokenServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiAccessTokensWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(apiAccessTokenServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiAccessTokenMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(apiAccessTokenRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getApiAccessToken() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        // Get the apiAccessToken
        restApiAccessTokenMockMvc
            .perform(get(ENTITY_API_URL_ID, apiAccessToken.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(apiAccessToken.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.tokenPrefix").value(DEFAULT_TOKEN_PREFIX))
            .andExpect(jsonPath("$.tokenHash").doesNotExist())
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()))
            .andExpect(jsonPath("$.lastUsedAt").value(DEFAULT_LAST_USED_AT.toString()))
            .andExpect(jsonPath("$.expiresAt").value(DEFAULT_EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.revokedAt").value(DEFAULT_REVOKED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingApiAccessToken() throws Exception {
        // Get the apiAccessToken
        restApiAccessTokenMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingApiAccessToken() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiAccessToken
        ApiAccessToken updatedApiAccessToken = apiAccessTokenRepository.findById(apiAccessToken.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedApiAccessToken are not directly saved in db
        em.detach(updatedApiAccessToken);
        updatedApiAccessToken.name(UPDATED_NAME).status(UPDATED_STATUS).updatedAt(UPDATED_UPDATED_AT).expiresAt(UPDATED_EXPIRES_AT);
        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(updatedApiAccessToken);

        restApiAccessTokenMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updateRequest.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateRequest))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenHash").doesNotExist());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiAccessToken persisted = getPersistedApiAccessToken(apiAccessToken);
        assertThat(persisted.getName()).isEqualTo(UPDATED_NAME);
        assertThat(persisted.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(persisted.getTokenHash()).isEqualTo(DEFAULT_TOKEN_HASH);
        assertThat(persisted.getTokenPrefix()).isEqualTo(DEFAULT_TOKEN_PREFIX);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getUpdatedAt()).isAfter(DEFAULT_UPDATED_AT);
        assertThat(persisted.getLastUsedAt()).isEqualTo(DEFAULT_LAST_USED_AT);
        assertThat(persisted.getRevokedAt()).isEqualTo(DEFAULT_REVOKED_AT);
    }

    @Test
    @Transactional
    void putNonExistingApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(apiAccessToken);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updateRequest.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateRequest))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(apiAccessToken);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateRequest))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(apiAccessToken);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(updateRequest)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateApiAccessTokenWithPatch() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiAccessToken using partial update
        String patchJson =
            "{\"id\":" +
            apiAccessToken.getId() +
            ",\"name\":\"" +
            UPDATED_NAME +
            "\",\"updatedAt\":\"" +
            UPDATED_UPDATED_AT +
            "\",\"expiresAt\":\"" +
            UPDATED_EXPIRES_AT +
            "\"}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenHash").doesNotExist());

        // Validate the ApiAccessToken in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiAccessToken partialUpdatedApiAccessToken = new ApiAccessToken();
        partialUpdatedApiAccessToken.setId(apiAccessToken.getId());
        partialUpdatedApiAccessToken.name(UPDATED_NAME).expiresAt(UPDATED_EXPIRES_AT);
        ApiAccessToken persisted = getPersistedApiAccessToken(apiAccessToken);
        assertThat(persisted.getName()).isEqualTo(UPDATED_NAME);
        assertThat(persisted.getExpiresAt()).isEqualTo(UPDATED_EXPIRES_AT);
        assertThat(persisted.getUpdatedAt()).isAfter(DEFAULT_UPDATED_AT);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getLastUsedAt()).isEqualTo(DEFAULT_LAST_USED_AT);
        assertThat(persisted.getRevokedAt()).isEqualTo(DEFAULT_REVOKED_AT);
    }

    @Test
    @Transactional
    void fullUpdateApiAccessTokenWithPatch() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiAccessToken using partial update
        String patchJson =
            "{\"id\":" +
            apiAccessToken.getId() +
            ",\"name\":\"" +
            UPDATED_NAME +
            "\",\"status\":\"" +
            UPDATED_STATUS +
            "\",\"updatedAt\":\"" +
            UPDATED_UPDATED_AT +
            "\",\"expiresAt\":\"" +
            UPDATED_EXPIRES_AT +
            "\"}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenHash").doesNotExist());

        // Validate the ApiAccessToken in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiAccessToken partialUpdatedApiAccessToken = new ApiAccessToken()
            .name(UPDATED_NAME)
            .status(UPDATED_STATUS)
            .expiresAt(UPDATED_EXPIRES_AT);
        partialUpdatedApiAccessToken.setId(apiAccessToken.getId());
        ApiAccessToken persisted = getPersistedApiAccessToken(partialUpdatedApiAccessToken);
        assertThat(persisted.getName()).isEqualTo(UPDATED_NAME);
        assertThat(persisted.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(persisted.getExpiresAt()).isEqualTo(UPDATED_EXPIRES_AT);
        assertThat(persisted.getUpdatedAt()).isAfter(DEFAULT_UPDATED_AT);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getLastUsedAt()).isEqualTo(DEFAULT_LAST_USED_AT);
        assertThat(persisted.getRevokedAt()).isEqualTo(DEFAULT_REVOKED_AT);
    }

    @Test
    @Transactional
    void patchNonExistingApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenDTO apiAccessTokenDTO = apiAccessTokenMapper.toDto(apiAccessToken);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiAccessTokenDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenDTO apiAccessTokenDTO = apiAccessTokenMapper.toDto(apiAccessToken);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiAccessTokenDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamApiAccessToken() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessToken.setId(longCount.incrementAndGet());

        // Create the ApiAccessToken
        ApiAccessTokenDTO apiAccessTokenDTO = apiAccessTokenMapper.toDto(apiAccessToken);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(apiAccessTokenDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiAccessToken in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteApiAccessToken() throws Exception {
        // Initialize the database
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the apiAccessToken
        restApiAccessTokenMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessToken.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getApiAccessTokenOwnedByAnotherUserIsNotFound() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        restApiAccessTokenMockMvc.perform(get(ENTITY_API_URL_ID, apiAccessToken.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllApiAccessTokensDoesNotIncludeAnotherUsersTokens() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        restApiAccessTokenMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(apiAccessToken.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetApiAccessTokenOwnedByAnotherUser() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        restApiAccessTokenMockMvc
            .perform(get(ENTITY_API_URL_ID, apiAccessToken.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(apiAccessToken.getId().intValue()))
            .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    @Transactional
    void putApiAccessTokenOwnedByAnotherUserIsNotFound() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(apiAccessToken);
        updateRequest.setName(UPDATED_NAME);

        restApiAccessTokenMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updateRequest.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateRequest))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenOwnedByAnotherUserIsNotFound() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"name\":\"" + UPDATED_NAME + "\"}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteApiAccessTokenOwnedByAnotherUserIsNotFound() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        restApiAccessTokenMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessToken.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllApiAccessTokensIncludingOtherUsers() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);
        ApiAccessToken otherToken = createEntity(em);
        otherToken.setUser(createOtherUser(em));
        otherToken.setTokenHash("OTHER_TOKEN_HASH");
        otherToken = apiAccessTokenRepository.saveAndFlush(otherToken);

        restApiAccessTokenMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiAccessToken.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherToken.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateApiAccessTokenOwnedByAnotherUser() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        ApiAccessTokenUpdateRequestDTO updateRequest = toUpdateRequest(apiAccessToken);
        updateRequest.setName(UPDATED_NAME);

        restApiAccessTokenMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updateRequest.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateRequest))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteApiAccessTokenOwnedByAnotherUser() throws Exception {
        apiAccessToken.setUser(createOtherUser(em));
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restApiAccessTokenMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessToken.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedApiAccessToken = null;
    }

    @Test
    @Transactional
    void deleteApiAccessTokenDoesNotDeleteApiIngestions() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);
        ApiIngestion apiIngestion = ApiIngestionResourceIT.createEntity(em);
        apiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        Long ingestionId = apiIngestion.getId();

        restApiAccessTokenMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessToken.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(apiIngestionRepository.findById(ingestionId)).isPresent();
        insertedApiAccessToken = null;
        apiIngestionRepository.deleteById(ingestionId);
    }

    @Test
    @Transactional
    void deleteApiAccessTokenDeletesPermissionChildren() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);
        ApiAccessTokenPermission permission = new ApiAccessTokenPermission();
        permission.setPermission(ApiPermission.READ_TRANSACTIONS);
        permission.setCreatedAt(DEFAULT_CREATED_AT);
        permission.setApiAccessToken(insertedApiAccessToken);
        permission = apiAccessTokenPermissionRepository.saveAndFlush(permission);
        Long permissionId = permission.getId();

        restApiAccessTokenMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessToken.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(apiAccessTokenPermissionRepository.findById(permissionId)).isEmpty();
        insertedApiAccessToken = null;
    }

    @Test
    @Transactional
    void createApiAccessTokenWithoutUserInPayloadSucceeds() throws Exception {
        ApiAccessTokenCreateRequestDTO createRequest = toCreateRequest(apiAccessToken);

        ApiAccessTokenDTO returnedApiAccessTokenDTO = om.readValue(
            restApiAccessTokenMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.login").value(CURRENT_MOCK_USER_LOGIN))
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiAccessTokenDTO.class
        );

        assertThat(returnedApiAccessTokenDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedApiAccessToken = apiAccessTokenRepository.findById(returnedApiAccessTokenDTO.getId()).orElseThrow();
    }

    @Test
    @Transactional
    void createApiAccessTokenWithForeignUserFails() throws Exception {
        User otherUser = createOtherUser(em);
        String createJson =
            "{\"name\":\"" + DEFAULT_NAME + "\",\"user\":{\"id\":" + otherUser.getId() + ",\"login\":\"" + otherUser.getLogin() + "\"}}";

        restApiAccessTokenMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(createJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiAccessTokenWithTokenHashFails() throws Exception {
        String createJson = "{\"name\":\"" + DEFAULT_NAME + "\",\"tokenHash\":\"" + DEFAULT_TOKEN_HASH + "\"}";

        restApiAccessTokenMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(createJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiAccessTokenWithTokenPrefixFails() throws Exception {
        String createJson = "{\"name\":\"" + DEFAULT_NAME + "\",\"tokenPrefix\":\"" + DEFAULT_TOKEN_PREFIX + "\"}";

        restApiAccessTokenMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(createJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiAccessTokenWithDifferentTokenHashFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String updateJson =
            "{\"id\":" +
            apiAccessToken.getId() +
            ",\"name\":\"" +
            DEFAULT_NAME +
            "\",\"status\":\"" +
            DEFAULT_STATUS +
            "\",\"expiresAt\":\"" +
            DEFAULT_EXPIRES_AT +
            "\",\"tokenHash\":\"" +
            UPDATED_TOKEN_HASH +
            "\"}";

        restApiAccessTokenMockMvc
            .perform(put(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType(MediaType.APPLICATION_JSON).content(updateJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenWithDifferentTokenPrefixFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"tokenPrefix\":\"" + UPDATED_TOKEN_PREFIX + "\"}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenWithNullTokenPrefixFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"tokenPrefix\":null}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenWithNullUpdatedAtFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"updatedAt\":null}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenWithNullUserFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"user\":null}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenWithoutUserFieldPreservesOwner() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);

        String patchJson = "{\"id\":" + apiAccessToken.getId() + ",\"name\":\"" + UPDATED_NAME + "\"}";

        restApiAccessTokenMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.login").value(CURRENT_MOCK_USER_LOGIN));

        assertThat(getPersistedApiAccessToken(apiAccessToken).getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
    }

    @Test
    @Transactional
    void updateApiAccessTokenWithUserFails() throws Exception {
        insertedApiAccessToken = apiAccessTokenRepository.saveAndFlush(apiAccessToken);
        User otherUser = createOtherUser(em);
        String updateJson =
            "{\"id\":" +
            apiAccessToken.getId() +
            ",\"name\":\"" +
            UPDATED_NAME +
            "\",\"status\":\"" +
            DEFAULT_STATUS +
            "\",\"expiresAt\":\"" +
            DEFAULT_EXPIRES_AT +
            "\",\"user\":{\"id\":" +
            otherUser.getId() +
            ",\"login\":\"" +
            otherUser.getLogin() +
            "\"}}";

        restApiAccessTokenMockMvc
            .perform(put(ENTITY_API_URL_ID, apiAccessToken.getId()).contentType(MediaType.APPLICATION_JSON).content(updateJson))
            .andExpect(status().isBadRequest());

        ApiAccessToken persisted = getPersistedApiAccessToken(apiAccessToken);
        assertThat(persisted.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persisted.getName()).isEqualTo(DEFAULT_NAME);
    }

    protected long getRepositoryCount() {
        return apiAccessTokenRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected ApiAccessToken getPersistedApiAccessToken(ApiAccessToken apiAccessToken) {
        return apiAccessTokenRepository.findById(apiAccessToken.getId()).orElseThrow();
    }

    protected void assertPersistedApiAccessTokenToMatchAllProperties(ApiAccessToken expectedApiAccessToken) {
        assertApiAccessTokenAllPropertiesEquals(expectedApiAccessToken, getPersistedApiAccessToken(expectedApiAccessToken));
    }

    protected void assertPersistedApiAccessTokenToMatchUpdatableProperties(ApiAccessToken expectedApiAccessToken) {
        assertApiAccessTokenAllUpdatablePropertiesEquals(expectedApiAccessToken, getPersistedApiAccessToken(expectedApiAccessToken));
    }
}
