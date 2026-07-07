package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.ApiIngestionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.service.ApiIngestionService;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.mapper.ApiIngestionMapper;
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
 * Integration tests for the {@link ApiIngestionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class ApiIngestionResourceIT {

    private static final String DEFAULT_REQUEST_ID = "AAAAAAAAAA";
    private static final String UPDATED_REQUEST_ID = "BBBBBBBBBB";

    private static final String DEFAULT_IDEMPOTENCY_KEY = "AAAAAAAAAA";
    private static final String UPDATED_IDEMPOTENCY_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_SYSTEM = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_SYSTEM = "BBBBBBBBBB";

    private static final String DEFAULT_API_VERSION = "AAAAAAAAAA";
    private static final String UPDATED_API_VERSION = "BBBBBBBBBB";

    private static final String DEFAULT_ENDPOINT = "AAAAAAAAAA";
    private static final String UPDATED_ENDPOINT = "BBBBBBBBBB";

    private static final String DEFAULT_CLIENT_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_CLIENT_REFERENCE = "BBBBBBBBBB";

    private static final Instant DEFAULT_RECEIVED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RECEIVED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/api-ingestions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ApiIngestionRepository apiIngestionRepository;

    @Mock
    private ApiIngestionRepository apiIngestionRepositoryMock;

    @Autowired
    private ApiIngestionMapper apiIngestionMapper;

    @Mock
    private ApiIngestionService apiIngestionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restApiIngestionMockMvc;

    private ApiIngestion apiIngestion;

    private ApiIngestion insertedApiIngestion;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiIngestion createEntity(EntityManager em) {
        ApiIngestion apiIngestion = new ApiIngestion()
            .requestId(DEFAULT_REQUEST_ID)
            .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
            .sourceSystem(DEFAULT_SOURCE_SYSTEM)
            .apiVersion(DEFAULT_API_VERSION)
            .endpoint(DEFAULT_ENDPOINT)
            .clientReference(DEFAULT_CLIENT_REFERENCE)
            .receivedAt(DEFAULT_RECEIVED_AT)
            .createdAt(DEFAULT_CREATED_AT);
        // Add required entity
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            transactionIngestion = TransactionIngestionResourceIT.createEntity(em);
            em.persist(transactionIngestion);
            em.flush();
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        apiIngestion.setTransactionIngestion(transactionIngestion);
        // Add required entity
        ApiAccessToken apiAccessToken;
        if (TestUtil.findAll(em, ApiAccessToken.class).isEmpty()) {
            apiAccessToken = ApiAccessTokenResourceIT.createEntity(em);
            em.persist(apiAccessToken);
            em.flush();
        } else {
            apiAccessToken = TestUtil.findAll(em, ApiAccessToken.class).get(0);
        }
        apiIngestion.setApiAccessToken(apiAccessToken);
        return apiIngestion;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiIngestion createUpdatedEntity(EntityManager em) {
        ApiIngestion updatedApiIngestion = new ApiIngestion()
            .requestId(UPDATED_REQUEST_ID)
            .idempotencyKey(UPDATED_IDEMPOTENCY_KEY)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .clientReference(UPDATED_CLIENT_REFERENCE)
            .receivedAt(UPDATED_RECEIVED_AT)
            .createdAt(UPDATED_CREATED_AT);
        // Add required entity
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            transactionIngestion = TransactionIngestionResourceIT.createUpdatedEntity(em);
            em.persist(transactionIngestion);
            em.flush();
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        updatedApiIngestion.setTransactionIngestion(transactionIngestion);
        // Add required entity
        ApiAccessToken apiAccessToken;
        if (TestUtil.findAll(em, ApiAccessToken.class).isEmpty()) {
            apiAccessToken = ApiAccessTokenResourceIT.createUpdatedEntity(em);
            em.persist(apiAccessToken);
            em.flush();
        } else {
            apiAccessToken = TestUtil.findAll(em, ApiAccessToken.class).get(0);
        }
        updatedApiIngestion.setApiAccessToken(apiAccessToken);
        return updatedApiIngestion;
    }

    @BeforeEach
    void initTest() {
        apiIngestion = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedApiIngestion != null) {
            apiIngestionRepository.delete(insertedApiIngestion);
            insertedApiIngestion = null;
        }
    }

    @Test
    @Transactional
    void createApiIngestion() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);
        var returnedApiIngestionDTO = om.readValue(
            restApiIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiIngestionDTO.class
        );

        // Validate the ApiIngestion in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedApiIngestion = apiIngestionMapper.toEntity(returnedApiIngestionDTO);
        assertApiIngestionUpdatableFieldsEquals(returnedApiIngestion, getPersistedApiIngestion(returnedApiIngestion));

        insertedApiIngestion = returnedApiIngestion;
    }

    @Test
    @Transactional
    void createApiIngestionWithExistingId() throws Exception {
        // Create the ApiIngestion with an existing ID
        apiIngestion.setId(1L);
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkRequestIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiIngestion.setRequestId(null);

        // Create the ApiIngestion, which fails.
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkApiVersionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiIngestion.setApiVersion(null);

        // Create the ApiIngestion, which fails.
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEndpointIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiIngestion.setEndpoint(null);

        // Create the ApiIngestion, which fails.
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkReceivedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiIngestion.setReceivedAt(null);

        // Create the ApiIngestion, which fails.
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        apiIngestion.setCreatedAt(null);

        // Create the ApiIngestion, which fails.
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllApiIngestions() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        // Get all the apiIngestionList
        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].requestId").value(hasItem(DEFAULT_REQUEST_ID)))
            .andExpect(jsonPath("$.[*].idempotencyKey").value(hasItem(DEFAULT_IDEMPOTENCY_KEY)))
            .andExpect(jsonPath("$.[*].sourceSystem").value(hasItem(DEFAULT_SOURCE_SYSTEM)))
            .andExpect(jsonPath("$.[*].apiVersion").value(hasItem(DEFAULT_API_VERSION)))
            .andExpect(jsonPath("$.[*].endpoint").value(hasItem(DEFAULT_ENDPOINT)))
            .andExpect(jsonPath("$.[*].clientReference").value(hasItem(DEFAULT_CLIENT_REFERENCE)))
            .andExpect(jsonPath("$.[*].receivedAt").value(hasItem(DEFAULT_RECEIVED_AT.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiIngestionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(apiIngestionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiIngestionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(apiIngestionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllApiIngestionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(apiIngestionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restApiIngestionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(apiIngestionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getApiIngestion() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        // Get the apiIngestion
        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, apiIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(apiIngestion.getId().intValue()))
            .andExpect(jsonPath("$.requestId").value(DEFAULT_REQUEST_ID))
            .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
            .andExpect(jsonPath("$.sourceSystem").value(DEFAULT_SOURCE_SYSTEM))
            .andExpect(jsonPath("$.apiVersion").value(DEFAULT_API_VERSION))
            .andExpect(jsonPath("$.endpoint").value(DEFAULT_ENDPOINT))
            .andExpect(jsonPath("$.clientReference").value(DEFAULT_CLIENT_REFERENCE))
            .andExpect(jsonPath("$.receivedAt").value(DEFAULT_RECEIVED_AT.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingApiIngestion() throws Exception {
        // Get the apiIngestion
        restApiIngestionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingApiIngestion() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiIngestion
        ApiIngestion updatedApiIngestion = apiIngestionRepository.findById(apiIngestion.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedApiIngestion are not directly saved in db
        em.detach(updatedApiIngestion);
        updatedApiIngestion
            .requestId(UPDATED_REQUEST_ID)
            .idempotencyKey(UPDATED_IDEMPOTENCY_KEY)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .clientReference(UPDATED_CLIENT_REFERENCE)
            .receivedAt(UPDATED_RECEIVED_AT)
            .createdAt(UPDATED_CREATED_AT);
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(updatedApiIngestion);

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isOk());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedApiIngestionToMatchAllProperties(updatedApiIngestion);
    }

    @Test
    @Transactional
    void putNonExistingApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateApiIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiIngestion using partial update
        ApiIngestion partialUpdatedApiIngestion = new ApiIngestion();
        partialUpdatedApiIngestion.setId(apiIngestion.getId());

        partialUpdatedApiIngestion
            .requestId(UPDATED_REQUEST_ID)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .receivedAt(UPDATED_RECEIVED_AT)
            .createdAt(UPDATED_CREATED_AT);

        restApiIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedApiIngestion.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedApiIngestion))
            )
            .andExpect(status().isOk());

        // Validate the ApiIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertApiIngestionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedApiIngestion, apiIngestion),
            getPersistedApiIngestion(apiIngestion)
        );
    }

    @Test
    @Transactional
    void fullUpdateApiIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the apiIngestion using partial update
        ApiIngestion partialUpdatedApiIngestion = new ApiIngestion();
        partialUpdatedApiIngestion.setId(apiIngestion.getId());

        partialUpdatedApiIngestion
            .requestId(UPDATED_REQUEST_ID)
            .idempotencyKey(UPDATED_IDEMPOTENCY_KEY)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .clientReference(UPDATED_CLIENT_REFERENCE)
            .receivedAt(UPDATED_RECEIVED_AT)
            .createdAt(UPDATED_CREATED_AT);

        restApiIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedApiIngestion.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedApiIngestion))
            )
            .andExpect(status().isOk());

        // Validate the ApiIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertApiIngestionUpdatableFieldsEquals(partialUpdatedApiIngestion, getPersistedApiIngestion(partialUpdatedApiIngestion));
    }

    @Test
    @Transactional
    void patchNonExistingApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamApiIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        apiIngestion.setId(longCount.incrementAndGet());

        // Create the ApiIngestion
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(apiIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ApiIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteApiIngestion() throws Exception {
        // Initialize the database
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the apiIngestion
        restApiIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, apiIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return apiIngestionRepository.count();
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

    protected ApiIngestion getPersistedApiIngestion(ApiIngestion apiIngestion) {
        return apiIngestionRepository.findById(apiIngestion.getId()).orElseThrow();
    }

    protected void assertPersistedApiIngestionToMatchAllProperties(ApiIngestion expectedApiIngestion) {
        assertApiIngestionAllPropertiesEquals(expectedApiIngestion, getPersistedApiIngestion(expectedApiIngestion));
    }

    protected void assertPersistedApiIngestionToMatchUpdatableProperties(ApiIngestion expectedApiIngestion) {
        assertApiIngestionAllUpdatablePropertiesEquals(expectedApiIngestion, getPersistedApiIngestion(expectedApiIngestion));
    }
}
