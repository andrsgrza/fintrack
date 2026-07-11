package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.ApiAccessTokenPermissionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
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
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ApiPermission;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.ApiAccessTokenPermissionService;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenPermissionMapper;
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
 * Integration tests for the {@link ApiAccessTokenPermissionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class ApiAccessTokenPermissionResourceIT {

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static final ApiPermission DEFAULT_PERMISSION = ApiPermission.CREATE_TRANSACTIONS;
    private static final ApiPermission UPDATED_PERMISSION = ApiPermission.CREATE_TRANSACTIONS;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/api-access-token-permissions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    @Autowired
    private ApiAccessTokenRepository apiAccessTokenRepository;

    @Mock
    private ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepositoryMock;

    @Autowired
    private ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper;

    @Mock
    private ApiAccessTokenPermissionService apiAccessTokenPermissionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restApiAccessTokenPermissionMockMvc;

    private ApiAccessTokenPermission apiAccessTokenPermission;

    private ApiAccessTokenPermission insertedApiAccessTokenPermission;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiAccessTokenPermission createEntity(EntityManager em) {
        ApiAccessTokenPermission apiAccessTokenPermission = new ApiAccessTokenPermission()
            .permission(DEFAULT_PERMISSION)
            .createdAt(DEFAULT_CREATED_AT);
        apiAccessTokenPermission.setApiAccessToken(createPersistedApiAccessToken(em));
        return apiAccessTokenPermission;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiAccessTokenPermission createUpdatedEntity(EntityManager em) {
        ApiAccessTokenPermission updatedApiAccessTokenPermission = new ApiAccessTokenPermission()
            .permission(UPDATED_PERMISSION)
            .createdAt(UPDATED_CREATED_AT);
        updatedApiAccessTokenPermission.setApiAccessToken(createPersistedUpdatedApiAccessToken(em));
        return updatedApiAccessTokenPermission;
    }

    private static ApiAccessToken createPersistedApiAccessToken(EntityManager em) {
        ApiAccessToken apiAccessToken = ApiAccessTokenResourceIT.createEntity(em);
        apiAccessToken.setTokenHash("hash-default-" + longCount.incrementAndGet());
        em.persist(apiAccessToken);
        em.flush();
        return apiAccessToken;
    }

    private static ApiAccessToken createPersistedUpdatedApiAccessToken(EntityManager em) {
        ApiAccessToken apiAccessToken = ApiAccessTokenResourceIT.createUpdatedEntity(em);
        apiAccessToken.setTokenHash("hash-updated-" + longCount.incrementAndGet());
        em.persist(apiAccessToken);
        em.flush();
        return apiAccessToken;
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private ApiAccessTokenPermissionDTO toCreateDto(ApiAccessTokenPermission entity) {
        ApiAccessTokenPermissionDTO dto = new ApiAccessTokenPermissionDTO();
        dto.setPermission(entity.getPermission());
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(entity.getApiAccessToken().getId());
        dto.setApiAccessToken(apiAccessTokenDTO);
        return dto;
    }

    @BeforeEach
    void initTest() {
        apiAccessTokenPermission = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedApiAccessTokenPermission != null) {
            apiAccessTokenPermissionRepository.delete(insertedApiAccessTokenPermission);
            insertedApiAccessTokenPermission = null;
        }
    }

    @Test
    @Transactional
    void createApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = toCreateDto(apiAccessTokenPermission);
        var returnedApiAccessTokenPermissionDTO = om.readValue(
            restApiAccessTokenPermissionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiAccessTokenPermissionDTO.class
        );

        // Validate the ApiAccessTokenPermission in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedApiAccessTokenPermissionDTO.getCreatedAt()).isNotNull();
        assertThat(returnedApiAccessTokenPermissionDTO.getCreatedAt()).isNotEqualTo(DEFAULT_CREATED_AT);
        assertThat(returnedApiAccessTokenPermissionDTO.getPermission()).isEqualTo(DEFAULT_PERMISSION);
        assertThat(returnedApiAccessTokenPermissionDTO.getApiAccessToken().getId()).isEqualTo(
            apiAccessTokenPermission.getApiAccessToken().getId()
        );

        insertedApiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(returnedApiAccessTokenPermissionDTO);
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionWithExistingId() throws Exception {
        // Create the ApiAccessTokenPermission with an existing ID
        apiAccessTokenPermission.setId(1L);
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkPermissionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiAccessTokenPermission.setPermission(null);

        // Create the ApiAccessTokenPermission, which fails.
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = toCreateDto(apiAccessTokenPermission);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllApiAccessTokenPermissions() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        // Get all the apiAccessTokenPermissionList
        restApiAccessTokenPermissionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiAccessTokenPermission.getId().intValue())))
            .andExpect(jsonPath("$.[*].permission").value(hasItem(DEFAULT_PERMISSION.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiAccessTokenPermissionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(apiAccessTokenPermissionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiAccessTokenPermissionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(apiAccessTokenPermissionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiAccessTokenPermissionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(apiAccessTokenPermissionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiAccessTokenPermissionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(apiAccessTokenPermissionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getApiAccessTokenPermission() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        // Get the apiAccessTokenPermission
        restApiAccessTokenPermissionMockMvc
            .perform(get(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(apiAccessTokenPermission.getId().intValue()))
            .andExpect(jsonPath("$.permission").value(DEFAULT_PERMISSION.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingApiAccessTokenPermission() throws Exception {
        // Get the apiAccessTokenPermission
        restApiAccessTokenPermissionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingApiAccessTokenPermission() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiAccessTokenPermission without changing immutable fields
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(
            getPersistedApiAccessTokenPermission(apiAccessTokenPermission)
        );

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isOk());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiAccessTokenPermission persisted = getPersistedApiAccessTokenPermission(apiAccessTokenPermission);
        assertThat(persisted.getPermission()).isEqualTo(DEFAULT_PERMISSION);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getApiAccessToken().getId()).isEqualTo(apiAccessTokenPermission.getApiAccessToken().getId());
    }

    @Test
    @Transactional
    void putNonExistingApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateApiAccessTokenPermissionWithPatch() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        String patchJson = "{\"id\":" + apiAccessTokenPermission.getId() + "}";

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the ApiAccessTokenPermission in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiAccessTokenPermission persisted = getPersistedApiAccessTokenPermission(apiAccessTokenPermission);
        assertThat(persisted.getPermission()).isEqualTo(DEFAULT_PERMISSION);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void fullUpdateApiAccessTokenPermissionWithPatch() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        String patchJson = "{\"id\":" + apiAccessTokenPermission.getId() + ",\"createdAt\":\"" + UPDATED_CREATED_AT + "\"}";

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchNonExistingApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamApiAccessTokenPermission() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiAccessTokenPermission.setId(longCount.incrementAndGet());

        // Create the ApiAccessTokenPermission
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteApiAccessTokenPermission() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the apiAccessTokenPermission
        restApiAccessTokenPermissionMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getApiAccessTokenPermissionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();

        restApiAccessTokenPermissionMockMvc.perform(get(ENTITY_API_URL_ID, otherPermission.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllApiAccessTokenPermissionsDoesNotIncludeAnotherUsersPermissions() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();

        restApiAccessTokenPermissionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherPermission.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetApiAccessTokenPermissionOwnedByAnotherUser() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();

        restApiAccessTokenPermissionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherPermission.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherPermission.getId().intValue()));
    }

    @Test
    @Transactional
    void putApiAccessTokenPermissionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(otherPermission);

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenPermissionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();
        String patchJson = "{\"id\":" + otherPermission.getId() + "}";

        restApiAccessTokenPermissionMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherPermission.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteApiAccessTokenPermissionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();

        restApiAccessTokenPermissionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherPermission.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllApiAccessTokenPermissionsIncludingOtherUsers() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();

        restApiAccessTokenPermissionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiAccessTokenPermission.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherPermission.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateApiAccessTokenPermissionOwnedByAnotherUser() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(otherPermission);

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permission").value(DEFAULT_PERMISSION.toString()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteApiAccessTokenPermissionOwnedByAnotherUser() throws Exception {
        ApiAccessTokenPermission otherPermission = savePermissionOnOtherUsersToken();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restApiAccessTokenPermissionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherPermission.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionWithTokenOwnedByAnotherUserFails() throws Exception {
        ApiAccessToken otherUsersToken = createApiAccessTokenForUser(em, createOtherUser(em));
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = toCreateDto(apiAccessTokenPermission);
        apiAccessTokenPermissionDTO.setId(null);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(otherUsersToken.getId());
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionForTokenThatAlreadyHasPermissionFails() throws Exception {
        ApiAccessToken apiAccessTokenEntity = apiAccessTokenPermission.getApiAccessToken();
        apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = toCreateDto(createEntity(em));
        apiAccessTokenPermissionDTO.setId(null);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(apiAccessTokenEntity.getId());
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiAccessTokenPermissionWithDifferentApiAccessTokenFails() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);
        ApiAccessToken anotherApiAccessToken = createPersistedUpdatedApiAccessToken(em);

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(anotherApiAccessToken.getId());
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);
        apiAccessTokenPermissionDTO.setCreatedAt(DEFAULT_CREATED_AT);

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenPermissionWithDifferentApiAccessTokenFails() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);
        ApiAccessToken anotherApiAccessToken = createPersistedUpdatedApiAccessToken(em);

        String patchJson =
            "{\"id\":" + apiAccessTokenPermission.getId() + ",\"apiAccessToken\":{\"id\":" + anotherApiAccessToken.getId() + "}}";

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiAccessTokenPermissionWithoutApiAccessTokenFieldPreservesParent() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);
        Long originalTokenId = apiAccessTokenPermission.getApiAccessToken().getId();

        String patchJson = "{\"id\":" + apiAccessTokenPermission.getId() + "}";

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiAccessToken.id").value(originalTokenId.intValue()));

        assertThat(getPersistedApiAccessTokenPermission(apiAccessTokenPermission).getApiAccessToken().getId()).isEqualTo(originalTokenId);
    }

    @Test
    @Transactional
    void patchApiAccessTokenPermissionWithNullApiAccessTokenFails() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        String patchJson = "{\"id\":" + apiAccessTokenPermission.getId() + ",\"apiAccessToken\":null}";

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiAccessTokenPermissionWithDifferentCreatedAtFails() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);
        apiAccessTokenPermissionDTO.setCreatedAt(UPDATED_CREATED_AT);

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    private ApiAccessToken createApiAccessTokenForUser(EntityManager em, User user) {
        ApiAccessToken apiAccessTokenEntity = ApiAccessTokenResourceIT.createEntity(em);
        apiAccessTokenEntity.setUser(user);
        apiAccessTokenEntity.setTokenHash("hash-other-" + longCount.incrementAndGet());
        em.persist(apiAccessTokenEntity);
        em.flush();
        return apiAccessTokenEntity;
    }

    private ApiAccessTokenPermission savePermissionOnOtherUsersToken() {
        ApiAccessToken otherUsersToken = createApiAccessTokenForUser(em, createOtherUser(em));
        return savePermissionOnToken(otherUsersToken);
    }

    private ApiAccessTokenPermission savePermissionOnToken(ApiAccessToken apiAccessTokenEntity) {
        return savePermissionOnToken(apiAccessTokenEntity, DEFAULT_PERMISSION);
    }

    private ApiAccessTokenPermission savePermissionOnToken(ApiAccessToken apiAccessTokenEntity, ApiPermission permission) {
        ApiAccessTokenPermission permissionEntity = new ApiAccessTokenPermission().permission(permission).createdAt(DEFAULT_CREATED_AT);
        permissionEntity.setApiAccessToken(apiAccessTokenEntity);
        return apiAccessTokenPermissionRepository.saveAndFlush(permissionEntity);
    }

    @Test
    @Transactional
    void deleteApiAccessTokenPermissionLeavesParentTokenIntact() throws Exception {
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);
        Long tokenId = apiAccessTokenPermission.getApiAccessToken().getId();
        long permissionCountBeforeDelete = getRepositoryCount();

        restApiAccessTokenPermissionMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiAccessTokenPermission.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(permissionCountBeforeDelete);
        assertThat(apiAccessTokenRepository.existsById(tokenId)).isTrue();
        insertedApiAccessTokenPermission = null;
    }

    @Test
    @Transactional
    void deleteApiAccessTokenPermissionLeavesSiblingPermissionOnSameToken() throws Exception {
        ApiAccessToken token = apiAccessTokenPermission.getApiAccessToken();
        ApiAccessTokenPermission permissionToDelete = savePermissionOnToken(token, DEFAULT_PERMISSION);
        ApiAccessTokenPermission siblingPermission = savePermissionOnToken(token, ApiPermission.READ_TRANSACTIONS);
        long permissionCountBeforeDelete = getRepositoryCount();

        restApiAccessTokenPermissionMockMvc
            .perform(delete(ENTITY_API_URL_ID, permissionToDelete.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(getRepositoryCount()).isEqualTo(permissionCountBeforeDelete - 1);
        assertThat(apiAccessTokenPermissionRepository.existsById(siblingPermission.getId())).isTrue();
        assertThat(apiAccessTokenPermissionRepository.existsById(permissionToDelete.getId())).isFalse();
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionOnRevokedTokenSucceeds() throws Exception {
        ApiAccessToken revokedToken = apiAccessTokenPermission.getApiAccessToken();
        revokedToken.setStatus(ApiTokenStatus.REVOKED);
        revokedToken.setRevokedAt(Instant.now());
        em.persist(revokedToken);
        em.flush();

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = toCreateDto(apiAccessTokenPermission);
        apiAccessTokenPermissionDTO.setId(null);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permission").value(DEFAULT_PERMISSION.toString()));

        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository
            .findAll()
            .stream()
            .filter(permission -> permission.getApiAccessToken().getId().equals(revokedToken.getId()))
            .findFirst()
            .orElseThrow();
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionOnExpiredTokenSucceeds() throws Exception {
        ApiAccessToken expiredToken = createPersistedUpdatedApiAccessToken(em);
        expiredToken.setStatus(ApiTokenStatus.EXPIRED);
        expiredToken.setExpiresAt(Instant.now().minusSeconds(60));
        em.persist(expiredToken);
        em.flush();

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = new ApiAccessTokenPermissionDTO();
        apiAccessTokenPermissionDTO.setPermission(DEFAULT_PERMISSION);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(expiredToken.getId());
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permission").value(DEFAULT_PERMISSION.toString()));

        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository
            .findAll()
            .stream()
            .filter(permission -> permission.getApiAccessToken().getId().equals(expiredToken.getId()))
            .findFirst()
            .orElseThrow();
    }

    @Test
    @Transactional
    void createApiAccessTokenPermissionOnDifferentTokenWithSamePermissionSucceeds() throws Exception {
        ApiAccessToken firstToken = apiAccessTokenPermission.getApiAccessToken();
        apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        ApiAccessToken secondToken = createPersistedUpdatedApiAccessToken(em);
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = new ApiAccessTokenPermissionDTO();
        apiAccessTokenPermissionDTO.setPermission(DEFAULT_PERMISSION);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(secondToken.getId());
        apiAccessTokenPermissionDTO.setApiAccessToken(apiAccessTokenDTO);

        var returnedDto = om.readValue(
            restApiAccessTokenPermissionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value(DEFAULT_PERMISSION.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiAccessTokenPermissionDTO.class
        );

        assertThat(returnedDto.getApiAccessToken().getId()).isEqualTo(secondToken.getId());
        assertThat(
            apiAccessTokenPermissionRepository.existsByApiAccessTokenIdAndPermission(firstToken.getId(), DEFAULT_PERMISSION)
        ).isTrue();
        assertThat(
            apiAccessTokenPermissionRepository.existsByApiAccessTokenIdAndPermission(secondToken.getId(), DEFAULT_PERMISSION)
        ).isTrue();
        insertedApiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(returnedDto);
    }

    protected long getRepositoryCount() {
        return apiAccessTokenPermissionRepository.count();
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

    protected ApiAccessTokenPermission getPersistedApiAccessTokenPermission(ApiAccessTokenPermission apiAccessTokenPermission) {
        return apiAccessTokenPermissionRepository.findById(apiAccessTokenPermission.getId()).orElseThrow();
    }

    protected void assertPersistedApiAccessTokenPermissionToMatchAllProperties(ApiAccessTokenPermission expectedApiAccessTokenPermission) {
        assertApiAccessTokenPermissionAllPropertiesEquals(
            expectedApiAccessTokenPermission,
            getPersistedApiAccessTokenPermission(expectedApiAccessTokenPermission)
        );
    }

    protected void assertPersistedApiAccessTokenPermissionToMatchUpdatableProperties(
        ApiAccessTokenPermission expectedApiAccessTokenPermission
    ) {
        assertApiAccessTokenPermissionAllUpdatablePropertiesEquals(
            expectedApiAccessTokenPermission,
            getPersistedApiAccessTokenPermission(expectedApiAccessTokenPermission)
        );
    }
}
