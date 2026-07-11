package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.ApiIngestionAsserts.*;
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
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.ApiIngestionService;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
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
    private ApiAccessTokenRepository apiAccessTokenRepository;

    @Autowired
    private ApiIngestionRepository apiIngestionRepository;

    @Autowired
    private TransactionIngestionRepository transactionIngestionRepository;

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
            .requestId(DEFAULT_REQUEST_ID + longCount.incrementAndGet())
            .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
            .sourceSystem(DEFAULT_SOURCE_SYSTEM)
            .apiVersion(DEFAULT_API_VERSION)
            .endpoint(DEFAULT_ENDPOINT)
            .clientReference(DEFAULT_CLIENT_REFERENCE)
            .receivedAt(DEFAULT_RECEIVED_AT)
            .createdAt(DEFAULT_CREATED_AT);

        TransactionIngestion transactionIngestion = createApiTransactionIngestionEntity(em);
        em.persist(transactionIngestion);
        em.flush();
        apiIngestion.setTransactionIngestion(transactionIngestion);

        ApiAccessToken apiAccessToken = ApiAccessTokenResourceIT.createEntity(em);
        apiAccessToken.setTokenHash("HASH" + longCount.incrementAndGet());
        apiAccessToken.setTokenPrefix("PREFIX" + longCount.incrementAndGet());
        em.persist(apiAccessToken);
        em.flush();
        applyTokenSnapshots(apiIngestion, apiAccessToken);
        return apiIngestion;
    }

    public static void applyTokenSnapshots(ApiIngestion apiIngestion, ApiAccessToken apiAccessToken) {
        apiIngestion.setApiTokenIdSnapshot(apiAccessToken.getId());
        apiIngestion.setApiTokenPrefixSnapshot(apiAccessToken.getTokenPrefix());
        apiIngestion.setApiTokenNameSnapshot(apiAccessToken.getName());
    }

    public static TransactionIngestion createApiTransactionIngestionEntity(EntityManager em) {
        TransactionIngestion transactionIngestion = TransactionIngestionResourceIT.createEntity(em);
        transactionIngestion.setIngestionType(IngestionType.API);
        return transactionIngestion;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ApiIngestion createUpdatedEntity(EntityManager em) {
        ApiIngestion updatedApiIngestion = new ApiIngestion()
            .requestId(UPDATED_REQUEST_ID + longCount.incrementAndGet())
            .idempotencyKey(UPDATED_IDEMPOTENCY_KEY)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .clientReference(UPDATED_CLIENT_REFERENCE)
            .receivedAt(UPDATED_RECEIVED_AT)
            .createdAt(UPDATED_CREATED_AT);
        TransactionIngestion transactionIngestion = createApiTransactionIngestionEntity(em);
        em.persist(transactionIngestion);
        em.flush();
        updatedApiIngestion.setTransactionIngestion(transactionIngestion);
        ApiAccessToken apiAccessToken = ApiAccessTokenResourceIT.createUpdatedEntity(em);
        apiAccessToken.setTokenHash("HASH" + longCount.incrementAndGet());
        apiAccessToken.setTokenPrefix("PREFIX" + longCount.incrementAndGet());
        em.persist(apiAccessToken);
        em.flush();
        applyTokenSnapshots(updatedApiIngestion, apiAccessToken);
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
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            apiIngestion.getRequestId()
        );
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
        assertThat(returnedApiIngestionDTO.getId()).isNotNull();
        assertThat(returnedApiIngestionDTO.getCreatedAt()).isNotNull();
        assertThat(returnedApiIngestionDTO.getReceivedAt()).isNotNull();
        ApiIngestion persisted = apiIngestionRepository.findById(returnedApiIngestionDTO.getId()).orElseThrow();
        assertThat(persisted.getRequestId()).isEqualTo(returnedApiIngestionDTO.getRequestId());
        assertThat(persisted.getApiTokenIdSnapshot()).isEqualTo(apiIngestion.getApiTokenIdSnapshot());
        assertThat(persisted.getApiTokenPrefixSnapshot()).isEqualTo(apiIngestion.getApiTokenPrefixSnapshot());
        assertThat(persisted.getApiTokenNameSnapshot()).isEqualTo(apiIngestion.getApiTokenNameSnapshot());

        insertedApiIngestion = persisted;
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
    void createApiIngestionWithoutTimestampsPersistsServerTimestamps() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-NO-TS-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        ApiIngestion persisted = apiIngestionRepository.findAll().get(apiIngestionRepository.findAll().size() - 1);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getReceivedAt()).isNotNull();
        apiIngestionRepository.delete(persisted);
    }

    @Test
    @Transactional
    void createApiIngestionIgnoresClientTimestamps() throws Exception {
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-FAKE-TS-" + longCount.incrementAndGet()
        );
        apiIngestionDTO.setCreatedAt(DEFAULT_CREATED_AT);
        apiIngestionDTO.setReceivedAt(DEFAULT_RECEIVED_AT);

        var returnedApiIngestionDTO = om.readValue(
            restApiIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ApiIngestionDTO.class
        );

        assertThat(returnedApiIngestionDTO.getCreatedAt()).isNotEqualTo(DEFAULT_CREATED_AT);
        assertThat(returnedApiIngestionDTO.getReceivedAt()).isNotEqualTo(DEFAULT_RECEIVED_AT);
        apiIngestionRepository.deleteById(returnedApiIngestionDTO.getId());
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
            .andExpect(jsonPath("$.[*].requestId").value(hasItem(apiIngestion.getRequestId())))
            .andExpect(jsonPath("$.[*].idempotencyKey").value(hasItem(DEFAULT_IDEMPOTENCY_KEY)))
            .andExpect(jsonPath("$.[*].sourceSystem").value(hasItem(DEFAULT_SOURCE_SYSTEM)))
            .andExpect(jsonPath("$.[*].apiVersion").value(hasItem(DEFAULT_API_VERSION)))
            .andExpect(jsonPath("$.[*].endpoint").value(hasItem(DEFAULT_ENDPOINT)))
            .andExpect(jsonPath("$.[*].clientReference").value(hasItem(DEFAULT_CLIENT_REFERENCE)))
            .andExpect(jsonPath("$.[*].receivedAt").value(hasItem(DEFAULT_RECEIVED_AT.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
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
            .andExpect(jsonPath("$.requestId").value(apiIngestion.getRequestId()))
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
            .idempotencyKey(UPDATED_IDEMPOTENCY_KEY)
            .sourceSystem(UPDATED_SOURCE_SYSTEM)
            .apiVersion(UPDATED_API_VERSION)
            .endpoint(UPDATED_ENDPOINT)
            .clientReference(UPDATED_CLIENT_REFERENCE);
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

        String patchJson =
            "{\"id\":" +
            apiIngestion.getId() +
            ",\"sourceSystem\":\"" +
            UPDATED_SOURCE_SYSTEM +
            "\",\"apiVersion\":\"" +
            UPDATED_API_VERSION +
            "\",\"endpoint\":\"" +
            UPDATED_ENDPOINT +
            "\"}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the ApiIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertThat(getPersistedApiIngestion(apiIngestion).getSourceSystem()).isEqualTo(UPDATED_SOURCE_SYSTEM);
        assertThat(getPersistedApiIngestion(apiIngestion).getApiVersion()).isEqualTo(UPDATED_API_VERSION);
        assertThat(getPersistedApiIngestion(apiIngestion).getEndpoint()).isEqualTo(UPDATED_ENDPOINT);
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

        partialUpdatedApiIngestion.setId(apiIngestion.getId());

        String patchJson =
            "{\"id\":" +
            apiIngestion.getId() +
            ",\"idempotencyKey\":\"" +
            UPDATED_IDEMPOTENCY_KEY +
            "\",\"sourceSystem\":\"" +
            UPDATED_SOURCE_SYSTEM +
            "\",\"apiVersion\":\"" +
            UPDATED_API_VERSION +
            "\",\"endpoint\":\"" +
            UPDATED_ENDPOINT +
            "\",\"clientReference\":\"" +
            UPDATED_CLIENT_REFERENCE +
            "\"}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the ApiIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        ApiIngestion persisted = getPersistedApiIngestion(apiIngestion);
        assertThat(persisted.getIdempotencyKey()).isEqualTo(UPDATED_IDEMPOTENCY_KEY);
        assertThat(persisted.getSourceSystem()).isEqualTo(UPDATED_SOURCE_SYSTEM);
        assertThat(persisted.getApiVersion()).isEqualTo(UPDATED_API_VERSION);
        assertThat(persisted.getEndpoint()).isEqualTo(UPDATED_ENDPOINT);
        assertThat(persisted.getClientReference()).isEqualTo(UPDATED_CLIENT_REFERENCE);
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

    @Test
    @Transactional
    void getApiIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc.perform(get(ENTITY_API_URL_ID, otherApiIngestion.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllApiIngestionsDoesNotIncludeAnotherUsersApiIngestions() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherApiIngestion.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetApiIngestionOwnedByAnotherUser() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherApiIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherApiIngestion.getId().intValue()));
    }

    @Test
    @Transactional
    void putApiIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(otherApiIngestion);
        apiIngestionDTO.setEndpoint(UPDATED_ENDPOINT);

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();
        String patchJson = "{\"id\":" + otherApiIngestion.getId() + ",\"endpoint\":\"" + UPDATED_ENDPOINT + "\"}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherApiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteApiIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherApiIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllApiIngestionsIncludingOtherUsers() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(apiIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherApiIngestion.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateApiIngestionOwnedByAnotherUser() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(otherApiIngestion);
        apiIngestionDTO.setEndpoint(UPDATED_ENDPOINT);

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endpoint").value(UPDATED_ENDPOINT));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteApiIngestionOwnedByAnotherUser() throws Exception {
        ApiIngestion otherApiIngestion = saveApiIngestionOnOtherUsersIngestion();

        restApiIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherApiIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCreateApiIngestionWithForeignParentsFromSameOwner() throws Exception {
        User otherUser = createOtherUser(em);
        TransactionIngestion otherIngestion = saveApiTransactionIngestionForUser(otherUser);
        ApiAccessToken otherToken = saveApiAccessTokenForUser(otherUser);
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            otherIngestion.getId(),
            otherToken.getId(),
            "REQ-ADMIN-FOREIGN-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isCreated());

        apiIngestionRepository
            .findAll()
            .stream()
            .filter(ai -> ai.getTransactionIngestion().getId().equals(otherIngestion.getId()))
            .findFirst()
            .ifPresent(ai -> apiIngestionRepository.delete(ai));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCreateApiIngestionWithCrossOwnerParentsFails() throws Exception {
        User userA = createOtherUser(em);
        User userB = createOtherUser(em);
        TransactionIngestion ingestionUserA = saveApiTransactionIngestionForUser(userA);
        ApiAccessToken tokenUserB = saveApiAccessTokenForUser(userB);
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            ingestionUserA.getId(),
            tokenUserB.getId(),
            "REQ-CROSS-OWNER-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionWithTransactionIngestionOwnedByAnotherUserFails() throws Exception {
        TransactionIngestion otherIngestion = saveApiTransactionIngestionForUser(createOtherUser(em));
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            otherIngestion.getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-FOREIGN-TI-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionWithApiAccessTokenOwnedByAnotherUserFails() throws Exception {
        ApiAccessToken otherToken = saveApiAccessTokenForUser(createOtherUser(em));
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            otherToken.getId(),
            "REQ-FOREIGN-TOKEN-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionWithFileTransactionIngestionFails() throws Exception {
        TransactionIngestion fileIngestion = TransactionIngestionResourceIT.createEntity(em);
        fileIngestion.setIngestionType(IngestionType.FILE);
        em.persist(fileIngestion);
        em.flush();

        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            fileIngestion.getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-FILE-PARENT-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionWithParentThatAlreadyHasApiIngestionFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-DUP-PARENT-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionWithDuplicateRequestIdFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        TransactionIngestion freshIngestion = createApiTransactionIngestionEntity(em);
        em.persist(freshIngestion);
        em.flush();
        ApiAccessToken freshToken = ApiAccessTokenResourceIT.createEntity(em);
        freshToken.setTokenHash("HASH" + longCount.incrementAndGet());
        freshToken.setTokenPrefix("PREFIX" + longCount.incrementAndGet());
        em.persist(freshToken);
        em.flush();
        ApiIngestionDTO apiIngestionDTO = buildCreateApiIngestionDTO(
            freshIngestion.getId(),
            freshToken.getId(),
            apiIngestion.getRequestId()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(apiIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiIngestionWithDifferentTransactionIngestionFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        TransactionIngestion otherIngestion = createApiTransactionIngestionEntity(em);
        em.persist(otherIngestion);
        em.flush();

        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(insertedApiIngestion);
        TransactionIngestionDTO otherParentDTO = new TransactionIngestionDTO();
        otherParentDTO.setId(otherIngestion.getId());
        apiIngestionDTO.setTransactionIngestion(otherParentDTO);

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiIngestionWithDifferentApiTokenNameSnapshotFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(insertedApiIngestion);
        apiIngestionDTO.setApiTokenNameSnapshot("renamed-token");

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateApiIngestionWithDifferentRequestIdFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        ApiIngestionDTO apiIngestionDTO = apiIngestionMapper.toDto(insertedApiIngestion);
        apiIngestionDTO.setRequestId(UPDATED_REQUEST_ID);

        restApiIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, apiIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(apiIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionWithNullTransactionIngestionFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        String patchJson = "{\"id\":" + apiIngestion.getId() + ",\"transactionIngestion\":null}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionWithNullApiTokenNameSnapshotFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        String patchJson = "{\"id\":" + apiIngestion.getId() + ",\"apiTokenNameSnapshot\":null}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionWithDifferentTransactionIngestionFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        TransactionIngestion otherIngestion = createApiTransactionIngestionEntity(em);
        em.persist(otherIngestion);
        em.flush();
        String patchJson = "{\"id\":" + apiIngestion.getId() + ",\"transactionIngestion\":{\"id\":" + otherIngestion.getId() + "}}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionWithDifferentApiTokenNameSnapshotFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        String patchJson = "{\"id\":" + apiIngestion.getId() + ",\"apiTokenNameSnapshot\":\"renamed-token\"}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchApiIngestionWithDifferentRequestIdFails() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        String patchJson = "{\"id\":" + apiIngestion.getId() + ",\"requestId\":\"" + UPDATED_REQUEST_ID + "\"}";

        restApiIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, apiIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createApiIngestionCopiesTokenSnapshots() throws Exception {
        ApiIngestionDTO dto = buildCreateApiIngestionDTO(
            apiIngestion.getTransactionIngestion().getId(),
            apiIngestion.getApiTokenIdSnapshot(),
            "REQ-SNAPSHOT-" + longCount.incrementAndGet()
        );

        restApiIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.apiTokenIdSnapshot").value(apiIngestion.getApiTokenIdSnapshot().intValue()))
            .andExpect(jsonPath("$.apiTokenPrefixSnapshot").value(apiIngestion.getApiTokenPrefixSnapshot()))
            .andExpect(jsonPath("$.apiTokenNameSnapshot").value(apiIngestion.getApiTokenNameSnapshot()))
            .andExpect(jsonPath("$.apiAccessToken").doesNotExist());

        ApiIngestion persisted = apiIngestionRepository.findAll().get(apiIngestionRepository.findAll().size() - 1);
        apiIngestionRepository.delete(persisted);
    }

    @Test
    @Transactional
    void getApiIngestionRetainsSnapshotsAfterTokenDeleted() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        Long tokenId = insertedApiIngestion.getApiTokenIdSnapshot();
        String prefix = insertedApiIngestion.getApiTokenPrefixSnapshot();
        String name = insertedApiIngestion.getApiTokenNameSnapshot();

        apiAccessTokenRepository.deleteById(tokenId);
        em.flush();

        restApiIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, insertedApiIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiTokenIdSnapshot").value(tokenId.intValue()))
            .andExpect(jsonPath("$.apiTokenPrefixSnapshot").value(prefix))
            .andExpect(jsonPath("$.apiTokenNameSnapshot").value(name));
    }

    @Test
    @Transactional
    void renameTokenDoesNotMutateOldIngestionSnapshots() throws Exception {
        insertedApiIngestion = apiIngestionRepository.saveAndFlush(apiIngestion);
        String originalName = insertedApiIngestion.getApiTokenNameSnapshot();
        ApiAccessToken token = apiAccessTokenRepository.findById(insertedApiIngestion.getApiTokenIdSnapshot()).orElseThrow();
        token.setName("Renamed Token");
        apiAccessTokenRepository.saveAndFlush(token);

        ApiIngestion reloaded = apiIngestionRepository.findById(insertedApiIngestion.getId()).orElseThrow();
        assertThat(reloaded.getApiTokenNameSnapshot()).isEqualTo(originalName);
    }

    private ApiIngestion saveApiIngestionOnOtherUsersIngestion() {
        User otherUser = createOtherUser(em);
        TransactionIngestion otherIngestion = saveApiTransactionIngestionForUser(otherUser);
        ApiAccessToken otherToken = saveApiAccessTokenForUser(otherUser);
        ApiIngestion otherApiIngestion = new ApiIngestion()
            .requestId("REQ-OTHER-" + longCount.incrementAndGet())
            .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
            .sourceSystem(DEFAULT_SOURCE_SYSTEM)
            .apiVersion(DEFAULT_API_VERSION)
            .endpoint(DEFAULT_ENDPOINT)
            .clientReference(DEFAULT_CLIENT_REFERENCE)
            .receivedAt(DEFAULT_RECEIVED_AT)
            .createdAt(DEFAULT_CREATED_AT);
        otherApiIngestion.setTransactionIngestion(otherIngestion);
        applyTokenSnapshots(otherApiIngestion, otherToken);
        return apiIngestionRepository.saveAndFlush(otherApiIngestion);
    }

    private TransactionIngestion saveApiTransactionIngestionForUser(User user) {
        FinancialAccount account = createAccountForUser(em, user);
        TransactionIngestion ingestion = createApiTransactionIngestionEntity(em);
        ingestion.setAccount(account);
        return transactionIngestionRepository.saveAndFlush(ingestion);
    }

    private ApiAccessToken saveApiAccessTokenForUser(User user) {
        ApiAccessToken token = ApiAccessTokenResourceIT.createEntity(em);
        token.setUser(user);
        token.setTokenHash("HASH" + longCount.incrementAndGet());
        token.setTokenPrefix("PREFIX" + longCount.incrementAndGet());
        em.persist(token);
        em.flush();
        return token;
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private static FinancialAccount createAccountForUser(EntityManager em, User user) {
        FinancialAccount financialAccount = FinancialAccountResourceIT.createEntity(em);
        financialAccount.setUser(user);
        em.persist(financialAccount);
        em.flush();
        return financialAccount;
    }

    private ApiIngestionDTO buildCreateApiIngestionDTO(Long transactionIngestionId, Long apiAccessTokenId, String requestId) {
        ApiIngestionDTO apiIngestionDTO = new ApiIngestionDTO();
        apiIngestionDTO.setRequestId(requestId);
        apiIngestionDTO.setIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        apiIngestionDTO.setSourceSystem(DEFAULT_SOURCE_SYSTEM);
        apiIngestionDTO.setApiVersion(DEFAULT_API_VERSION);
        apiIngestionDTO.setEndpoint(DEFAULT_ENDPOINT);
        apiIngestionDTO.setClientReference(DEFAULT_CLIENT_REFERENCE);
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(transactionIngestionId);
        apiIngestionDTO.setTransactionIngestion(transactionIngestionDTO);
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(apiAccessTokenId);
        apiIngestionDTO.setApiAccessToken(apiAccessTokenDTO);
        return apiIngestionDTO;
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
