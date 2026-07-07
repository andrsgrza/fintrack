package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.ApiAccessTokenPermissionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.domain.enumeration.ApiPermission;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.service.ApiAccessTokenPermissionService;
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
        // Add required entity
        ApiAccessToken apiAccessToken;
        if (TestUtil.findAll(em, ApiAccessToken.class).isEmpty()) {
            apiAccessToken = ApiAccessTokenResourceIT.createEntity(em);
            em.persist(apiAccessToken);
            em.flush();
        } else {
            apiAccessToken = TestUtil.findAll(em, ApiAccessToken.class).get(0);
        }
        apiAccessTokenPermission.setApiAccessToken(apiAccessToken);
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
        // Add required entity
        ApiAccessToken apiAccessToken;
        if (TestUtil.findAll(em, ApiAccessToken.class).isEmpty()) {
            apiAccessToken = ApiAccessTokenResourceIT.createUpdatedEntity(em);
            em.persist(apiAccessToken);
            em.flush();
        } else {
            apiAccessToken = TestUtil.findAll(em, ApiAccessToken.class).get(0);
        }
        updatedApiAccessTokenPermission.setApiAccessToken(apiAccessToken);
        return updatedApiAccessTokenPermission;
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
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);
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
        var returnedApiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(returnedApiAccessTokenPermissionDTO);
        assertApiAccessTokenPermissionUpdatableFieldsEquals(
            returnedApiAccessTokenPermission,
            getPersistedApiAccessTokenPermission(returnedApiAccessTokenPermission)
        );

        insertedApiAccessTokenPermission = returnedApiAccessTokenPermission;
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
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

        restApiAccessTokenPermissionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiAccessTokenPermission.setCreatedAt(null);

        // Create the ApiAccessTokenPermission, which fails.
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);

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

        // Update the apiAccessTokenPermission
        ApiAccessTokenPermission updatedApiAccessTokenPermission = apiAccessTokenPermissionRepository
            .findById(apiAccessTokenPermission.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedApiAccessTokenPermission are not directly saved in db
        em.detach(updatedApiAccessTokenPermission);
        updatedApiAccessTokenPermission.permission(UPDATED_PERMISSION).createdAt(UPDATED_CREATED_AT);
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = apiAccessTokenPermissionMapper.toDto(updatedApiAccessTokenPermission);

        restApiAccessTokenPermissionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiAccessTokenPermissionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiAccessTokenPermissionDTO))
            )
            .andExpect(status().isOk());

        // Validate the ApiAccessTokenPermission in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedApiAccessTokenPermissionToMatchAllProperties(updatedApiAccessTokenPermission);
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

        // Update the apiAccessTokenPermission using partial update
        ApiAccessTokenPermission partialUpdatedApiAccessTokenPermission = new ApiAccessTokenPermission();
        partialUpdatedApiAccessTokenPermission.setId(apiAccessTokenPermission.getId());

        partialUpdatedApiAccessTokenPermission.createdAt(UPDATED_CREATED_AT);

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedApiAccessTokenPermission.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedApiAccessTokenPermission))
            )
            .andExpect(status().isOk());

        // Validate the ApiAccessTokenPermission in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertApiAccessTokenPermissionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedApiAccessTokenPermission, apiAccessTokenPermission),
            getPersistedApiAccessTokenPermission(apiAccessTokenPermission)
        );
    }

    @Test
    @Transactional
    void fullUpdateApiAccessTokenPermissionWithPatch() throws Exception {
        // Initialize the database
        insertedApiAccessTokenPermission = apiAccessTokenPermissionRepository.saveAndFlush(apiAccessTokenPermission);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiAccessTokenPermission using partial update
        ApiAccessTokenPermission partialUpdatedApiAccessTokenPermission = new ApiAccessTokenPermission();
        partialUpdatedApiAccessTokenPermission.setId(apiAccessTokenPermission.getId());

        partialUpdatedApiAccessTokenPermission.permission(UPDATED_PERMISSION).createdAt(UPDATED_CREATED_AT);

        restApiAccessTokenPermissionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedApiAccessTokenPermission.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedApiAccessTokenPermission))
            )
            .andExpect(status().isOk());

        // Validate the ApiAccessTokenPermission in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertApiAccessTokenPermissionUpdatableFieldsEquals(
            partialUpdatedApiAccessTokenPermission,
            getPersistedApiAccessTokenPermission(partialUpdatedApiAccessTokenPermission)
        );
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
