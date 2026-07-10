package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TransactionIngestionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.TransactionIngestionService;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
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
 * Integration tests for the {@link TransactionIngestionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionIngestionResourceIT {

    private static final IngestionType DEFAULT_INGESTION_TYPE = IngestionType.FILE;
    private static final IngestionType UPDATED_INGESTION_TYPE = IngestionType.API;

    private static final IngestionStatus DEFAULT_STATUS = IngestionStatus.PENDING;
    private static final IngestionStatus UPDATED_STATUS = IngestionStatus.PROCESSING;

    private static final String DEFAULT_SOURCE_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_LABEL = "BBBBBBBBBB";

    private static final Instant DEFAULT_STARTED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_STARTED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_COMPLETED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_COMPLETED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Integer DEFAULT_RECORDS_RECEIVED = 0;
    private static final Integer UPDATED_RECORDS_RECEIVED = 1;
    private static final Integer SMALLER_RECORDS_RECEIVED = 0 - 1;

    private static final Integer DEFAULT_RECORDS_CREATED = 0;
    private static final Integer UPDATED_RECORDS_CREATED = 1;
    private static final Integer SMALLER_RECORDS_CREATED = 0 - 1;

    private static final Integer DEFAULT_RECORDS_SKIPPED = 0;
    private static final Integer UPDATED_RECORDS_SKIPPED = 1;
    private static final Integer SMALLER_RECORDS_SKIPPED = 0 - 1;

    private static final Integer DEFAULT_RECORDS_REJECTED = 0;
    private static final Integer UPDATED_RECORDS_REJECTED = 1;
    private static final Integer SMALLER_RECORDS_REJECTED = 0 - 1;

    private static final String DEFAULT_ERROR_MESSAGE = "AAAAAAAAAA";
    private static final String UPDATED_ERROR_MESSAGE = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/transaction-ingestions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepositoryMock;

    @Autowired
    private TransactionIngestionMapper transactionIngestionMapper;

    @Mock
    private TransactionIngestionService transactionIngestionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionIngestionMockMvc;

    @Autowired
    private TransactionIngestionService transactionIngestionService;

    private TransactionIngestion transactionIngestion;

    private TransactionIngestion insertedTransactionIngestion;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionIngestion createEntity(EntityManager em) {
        TransactionIngestion transactionIngestion = new TransactionIngestion()
            .ingestionType(DEFAULT_INGESTION_TYPE)
            .status(DEFAULT_STATUS)
            .sourceLabel(DEFAULT_SOURCE_LABEL)
            .startedAt(DEFAULT_STARTED_AT)
            .completedAt(DEFAULT_COMPLETED_AT)
            .recordsReceived(DEFAULT_RECORDS_RECEIVED)
            .recordsCreated(DEFAULT_RECORDS_CREATED)
            .recordsSkipped(DEFAULT_RECORDS_SKIPPED)
            .recordsRejected(DEFAULT_RECORDS_REJECTED)
            .errorMessage(DEFAULT_ERROR_MESSAGE)
            .createdAt(DEFAULT_CREATED_AT);
        // Add required entity
        FinancialAccount financialAccount;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialAccount = FinancialAccountResourceIT.createEntity(em);
            em.persist(financialAccount);
            em.flush();
        } else {
            financialAccount = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        transactionIngestion.setAccount(financialAccount);
        return transactionIngestion;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionIngestion createUpdatedEntity(EntityManager em) {
        TransactionIngestion updatedTransactionIngestion = new TransactionIngestion()
            .ingestionType(UPDATED_INGESTION_TYPE)
            .status(UPDATED_STATUS)
            .sourceLabel(UPDATED_SOURCE_LABEL)
            .startedAt(UPDATED_STARTED_AT)
            .completedAt(UPDATED_COMPLETED_AT)
            .recordsReceived(UPDATED_RECORDS_RECEIVED)
            .recordsCreated(UPDATED_RECORDS_CREATED)
            .recordsSkipped(UPDATED_RECORDS_SKIPPED)
            .recordsRejected(UPDATED_RECORDS_REJECTED)
            .errorMessage(UPDATED_ERROR_MESSAGE)
            .createdAt(UPDATED_CREATED_AT);
        // Add required entity
        FinancialAccount financialAccount;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialAccount = FinancialAccountResourceIT.createUpdatedEntity(em);
            em.persist(financialAccount);
            em.flush();
        } else {
            financialAccount = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        updatedTransactionIngestion.setAccount(financialAccount);
        return updatedTransactionIngestion;
    }

    @BeforeEach
    void initTest() {
        transactionIngestion = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionIngestion != null) {
            transactionIngestionRepository.delete(insertedTransactionIngestion);
            insertedTransactionIngestion = null;
        }
    }

    @Test
    @Transactional
    void createTransactionIngestion() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);
        var returnedTransactionIngestionDTO = om.readValue(
            restTransactionIngestionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionIngestionDTO.class
        );

        // Validate the TransactionIngestion in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedTransactionIngestionDTO.getStatus()).isEqualTo(IngestionStatus.PENDING);
        assertThat(returnedTransactionIngestionDTO.getRecordsReceived()).isZero();
        TransactionIngestion persisted = transactionIngestionRepository.findById(returnedTransactionIngestionDTO.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(IngestionStatus.PENDING);

        insertedTransactionIngestion = persisted;
    }

    @Test
    @Transactional
    void createTransactionIngestionWithExistingId() throws Exception {
        // Create the TransactionIngestion with an existing ID
        transactionIngestion.setId(1L);
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkIngestionTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionIngestion.setIngestionType(null);

        // Create the TransactionIngestion, which fails.
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        restTransactionIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createTransactionIngestionWithoutStatusPersistsPending() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setIngestionType(DEFAULT_INGESTION_TYPE);
        transactionIngestionDTO.setSourceLabel(DEFAULT_SOURCE_LABEL);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(transactionIngestion.getAccount().getId());
        transactionIngestionDTO.setAccount(accountDTO);

        var returnedTransactionIngestionDTO = om.readValue(
            restTransactionIngestionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(IngestionStatus.PENDING.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionIngestionDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        TransactionIngestion persisted = transactionIngestionRepository.findById(returnedTransactionIngestionDTO.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(IngestionStatus.PENDING);
        insertedTransactionIngestion = persisted;
    }

    @Test
    @Transactional
    void createTransactionIngestionWithoutServerFieldsSucceeds() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setIngestionType(DEFAULT_INGESTION_TYPE);
        transactionIngestionDTO.setSourceLabel(DEFAULT_SOURCE_LABEL);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(transactionIngestion.getAccount().getId());
        transactionIngestionDTO.setAccount(accountDTO);

        var returnedTransactionIngestionDTO = om.readValue(
            restTransactionIngestionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(IngestionStatus.PENDING.toString()))
                .andExpect(jsonPath("$.recordsReceived").value(0))
                .andExpect(jsonPath("$.recordsCreated").value(0))
                .andExpect(jsonPath("$.recordsSkipped").value(0))
                .andExpect(jsonPath("$.recordsRejected").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionIngestionDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedTransactionIngestionDTO.getCreatedAt()).isNotNull();
        assertThat(returnedTransactionIngestionDTO.getStartedAt()).isNotNull();
        assertThat(returnedTransactionIngestionDTO.getCompletedAt()).isNull();
        assertThat(returnedTransactionIngestionDTO.getErrorMessage()).isNull();
        insertedTransactionIngestion = transactionIngestionMapper.toEntity(returnedTransactionIngestionDTO);
    }

    @Test
    @Transactional
    void createTransactionIngestionIgnoresClientStatusAndCompletedAt() throws Exception {
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setIngestionType(DEFAULT_INGESTION_TYPE);
        transactionIngestionDTO.setStatus(UPDATED_STATUS);
        transactionIngestionDTO.setCompletedAt(UPDATED_COMPLETED_AT);
        transactionIngestionDTO.setErrorMessage(UPDATED_ERROR_MESSAGE);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(transactionIngestion.getAccount().getId());
        transactionIngestionDTO.setAccount(accountDTO);

        restTransactionIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(IngestionStatus.PENDING.toString()))
            .andExpect(jsonPath("$.completedAt").doesNotExist())
            .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    @Transactional
    void getAllTransactionIngestions() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].ingestionType").value(hasItem(DEFAULT_INGESTION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].sourceLabel").value(hasItem(DEFAULT_SOURCE_LABEL)))
            .andExpect(jsonPath("$.[*].startedAt").value(hasItem(DEFAULT_STARTED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())))
            .andExpect(jsonPath("$.[*].recordsReceived").value(hasItem(DEFAULT_RECORDS_RECEIVED)))
            .andExpect(jsonPath("$.[*].recordsCreated").value(hasItem(DEFAULT_RECORDS_CREATED)))
            .andExpect(jsonPath("$.[*].recordsSkipped").value(hasItem(DEFAULT_RECORDS_SKIPPED)))
            .andExpect(jsonPath("$.[*].recordsRejected").value(hasItem(DEFAULT_RECORDS_REJECTED)))
            .andExpect(jsonPath("$.[*].errorMessage").value(hasItem(DEFAULT_ERROR_MESSAGE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionIngestionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionIngestionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionIngestionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionIngestionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionIngestionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionIngestionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionIngestionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionIngestionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionIngestion() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get the transactionIngestion
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionIngestion.getId().intValue()))
            .andExpect(jsonPath("$.ingestionType").value(DEFAULT_INGESTION_TYPE.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.sourceLabel").value(DEFAULT_SOURCE_LABEL))
            .andExpect(jsonPath("$.startedAt").value(DEFAULT_STARTED_AT.toString()))
            .andExpect(jsonPath("$.completedAt").value(DEFAULT_COMPLETED_AT.toString()))
            .andExpect(jsonPath("$.recordsReceived").value(DEFAULT_RECORDS_RECEIVED))
            .andExpect(jsonPath("$.recordsCreated").value(DEFAULT_RECORDS_CREATED))
            .andExpect(jsonPath("$.recordsSkipped").value(DEFAULT_RECORDS_SKIPPED))
            .andExpect(jsonPath("$.recordsRejected").value(DEFAULT_RECORDS_REJECTED))
            .andExpect(jsonPath("$.errorMessage").value(DEFAULT_ERROR_MESSAGE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getTransactionIngestionsByIdFiltering() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        Long id = transactionIngestion.getId();

        defaultTransactionIngestionFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTransactionIngestionFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTransactionIngestionFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByIngestionTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where ingestionType equals to
        defaultTransactionIngestionFiltering(
            "ingestionType.equals=" + DEFAULT_INGESTION_TYPE,
            "ingestionType.equals=" + UPDATED_INGESTION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByIngestionTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where ingestionType in
        defaultTransactionIngestionFiltering(
            "ingestionType.in=" + DEFAULT_INGESTION_TYPE + "," + UPDATED_INGESTION_TYPE,
            "ingestionType.in=" + UPDATED_INGESTION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByIngestionTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where ingestionType is not null
        defaultTransactionIngestionFiltering("ingestionType.specified=true", "ingestionType.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where status equals to
        defaultTransactionIngestionFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where status in
        defaultTransactionIngestionFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where status is not null
        defaultTransactionIngestionFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsBySourceLabelIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where sourceLabel equals to
        defaultTransactionIngestionFiltering("sourceLabel.equals=" + DEFAULT_SOURCE_LABEL, "sourceLabel.equals=" + UPDATED_SOURCE_LABEL);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsBySourceLabelIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where sourceLabel in
        defaultTransactionIngestionFiltering(
            "sourceLabel.in=" + DEFAULT_SOURCE_LABEL + "," + UPDATED_SOURCE_LABEL,
            "sourceLabel.in=" + UPDATED_SOURCE_LABEL
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsBySourceLabelIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where sourceLabel is not null
        defaultTransactionIngestionFiltering("sourceLabel.specified=true", "sourceLabel.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsBySourceLabelContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where sourceLabel contains
        defaultTransactionIngestionFiltering(
            "sourceLabel.contains=" + DEFAULT_SOURCE_LABEL,
            "sourceLabel.contains=" + UPDATED_SOURCE_LABEL
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsBySourceLabelNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where sourceLabel does not contain
        defaultTransactionIngestionFiltering(
            "sourceLabel.doesNotContain=" + UPDATED_SOURCE_LABEL,
            "sourceLabel.doesNotContain=" + DEFAULT_SOURCE_LABEL
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStartedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where startedAt equals to
        defaultTransactionIngestionFiltering("startedAt.equals=" + DEFAULT_STARTED_AT, "startedAt.equals=" + UPDATED_STARTED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStartedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where startedAt in
        defaultTransactionIngestionFiltering(
            "startedAt.in=" + DEFAULT_STARTED_AT + "," + UPDATED_STARTED_AT,
            "startedAt.in=" + UPDATED_STARTED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByStartedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where startedAt is not null
        defaultTransactionIngestionFiltering("startedAt.specified=true", "startedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCompletedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where completedAt equals to
        defaultTransactionIngestionFiltering("completedAt.equals=" + DEFAULT_COMPLETED_AT, "completedAt.equals=" + UPDATED_COMPLETED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCompletedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where completedAt in
        defaultTransactionIngestionFiltering(
            "completedAt.in=" + DEFAULT_COMPLETED_AT + "," + UPDATED_COMPLETED_AT,
            "completedAt.in=" + UPDATED_COMPLETED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCompletedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where completedAt is not null
        defaultTransactionIngestionFiltering("completedAt.specified=true", "completedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived equals to
        defaultTransactionIngestionFiltering(
            "recordsReceived.equals=" + DEFAULT_RECORDS_RECEIVED,
            "recordsReceived.equals=" + UPDATED_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived in
        defaultTransactionIngestionFiltering(
            "recordsReceived.in=" + DEFAULT_RECORDS_RECEIVED + "," + UPDATED_RECORDS_RECEIVED,
            "recordsReceived.in=" + UPDATED_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived is not null
        defaultTransactionIngestionFiltering("recordsReceived.specified=true", "recordsReceived.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived is greater than or equal to
        defaultTransactionIngestionFiltering(
            "recordsReceived.greaterThanOrEqual=" + DEFAULT_RECORDS_RECEIVED,
            "recordsReceived.greaterThanOrEqual=" + UPDATED_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived is less than or equal to
        defaultTransactionIngestionFiltering(
            "recordsReceived.lessThanOrEqual=" + DEFAULT_RECORDS_RECEIVED,
            "recordsReceived.lessThanOrEqual=" + SMALLER_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived is less than
        defaultTransactionIngestionFiltering(
            "recordsReceived.lessThan=" + UPDATED_RECORDS_RECEIVED,
            "recordsReceived.lessThan=" + DEFAULT_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsReceivedIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsReceived is greater than
        defaultTransactionIngestionFiltering(
            "recordsReceived.greaterThan=" + SMALLER_RECORDS_RECEIVED,
            "recordsReceived.greaterThan=" + DEFAULT_RECORDS_RECEIVED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated equals to
        defaultTransactionIngestionFiltering(
            "recordsCreated.equals=" + DEFAULT_RECORDS_CREATED,
            "recordsCreated.equals=" + UPDATED_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated in
        defaultTransactionIngestionFiltering(
            "recordsCreated.in=" + DEFAULT_RECORDS_CREATED + "," + UPDATED_RECORDS_CREATED,
            "recordsCreated.in=" + UPDATED_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated is not null
        defaultTransactionIngestionFiltering("recordsCreated.specified=true", "recordsCreated.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated is greater than or equal to
        defaultTransactionIngestionFiltering(
            "recordsCreated.greaterThanOrEqual=" + DEFAULT_RECORDS_CREATED,
            "recordsCreated.greaterThanOrEqual=" + UPDATED_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated is less than or equal to
        defaultTransactionIngestionFiltering(
            "recordsCreated.lessThanOrEqual=" + DEFAULT_RECORDS_CREATED,
            "recordsCreated.lessThanOrEqual=" + SMALLER_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated is less than
        defaultTransactionIngestionFiltering(
            "recordsCreated.lessThan=" + UPDATED_RECORDS_CREATED,
            "recordsCreated.lessThan=" + DEFAULT_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsCreatedIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsCreated is greater than
        defaultTransactionIngestionFiltering(
            "recordsCreated.greaterThan=" + SMALLER_RECORDS_CREATED,
            "recordsCreated.greaterThan=" + DEFAULT_RECORDS_CREATED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped equals to
        defaultTransactionIngestionFiltering(
            "recordsSkipped.equals=" + DEFAULT_RECORDS_SKIPPED,
            "recordsSkipped.equals=" + UPDATED_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped in
        defaultTransactionIngestionFiltering(
            "recordsSkipped.in=" + DEFAULT_RECORDS_SKIPPED + "," + UPDATED_RECORDS_SKIPPED,
            "recordsSkipped.in=" + UPDATED_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped is not null
        defaultTransactionIngestionFiltering("recordsSkipped.specified=true", "recordsSkipped.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped is greater than or equal to
        defaultTransactionIngestionFiltering(
            "recordsSkipped.greaterThanOrEqual=" + DEFAULT_RECORDS_SKIPPED,
            "recordsSkipped.greaterThanOrEqual=" + UPDATED_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped is less than or equal to
        defaultTransactionIngestionFiltering(
            "recordsSkipped.lessThanOrEqual=" + DEFAULT_RECORDS_SKIPPED,
            "recordsSkipped.lessThanOrEqual=" + SMALLER_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped is less than
        defaultTransactionIngestionFiltering(
            "recordsSkipped.lessThan=" + UPDATED_RECORDS_SKIPPED,
            "recordsSkipped.lessThan=" + DEFAULT_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsSkippedIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsSkipped is greater than
        defaultTransactionIngestionFiltering(
            "recordsSkipped.greaterThan=" + SMALLER_RECORDS_SKIPPED,
            "recordsSkipped.greaterThan=" + DEFAULT_RECORDS_SKIPPED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected equals to
        defaultTransactionIngestionFiltering(
            "recordsRejected.equals=" + DEFAULT_RECORDS_REJECTED,
            "recordsRejected.equals=" + UPDATED_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected in
        defaultTransactionIngestionFiltering(
            "recordsRejected.in=" + DEFAULT_RECORDS_REJECTED + "," + UPDATED_RECORDS_REJECTED,
            "recordsRejected.in=" + UPDATED_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected is not null
        defaultTransactionIngestionFiltering("recordsRejected.specified=true", "recordsRejected.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected is greater than or equal to
        defaultTransactionIngestionFiltering(
            "recordsRejected.greaterThanOrEqual=" + DEFAULT_RECORDS_REJECTED,
            "recordsRejected.greaterThanOrEqual=" + UPDATED_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected is less than or equal to
        defaultTransactionIngestionFiltering(
            "recordsRejected.lessThanOrEqual=" + DEFAULT_RECORDS_REJECTED,
            "recordsRejected.lessThanOrEqual=" + SMALLER_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected is less than
        defaultTransactionIngestionFiltering(
            "recordsRejected.lessThan=" + UPDATED_RECORDS_REJECTED,
            "recordsRejected.lessThan=" + DEFAULT_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByRecordsRejectedIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where recordsRejected is greater than
        defaultTransactionIngestionFiltering(
            "recordsRejected.greaterThan=" + SMALLER_RECORDS_REJECTED,
            "recordsRejected.greaterThan=" + DEFAULT_RECORDS_REJECTED
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByErrorMessageIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where errorMessage equals to
        defaultTransactionIngestionFiltering(
            "errorMessage.equals=" + DEFAULT_ERROR_MESSAGE,
            "errorMessage.equals=" + UPDATED_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByErrorMessageIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where errorMessage in
        defaultTransactionIngestionFiltering(
            "errorMessage.in=" + DEFAULT_ERROR_MESSAGE + "," + UPDATED_ERROR_MESSAGE,
            "errorMessage.in=" + UPDATED_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByErrorMessageIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where errorMessage is not null
        defaultTransactionIngestionFiltering("errorMessage.specified=true", "errorMessage.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByErrorMessageContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where errorMessage contains
        defaultTransactionIngestionFiltering(
            "errorMessage.contains=" + DEFAULT_ERROR_MESSAGE,
            "errorMessage.contains=" + UPDATED_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByErrorMessageNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where errorMessage does not contain
        defaultTransactionIngestionFiltering(
            "errorMessage.doesNotContain=" + UPDATED_ERROR_MESSAGE,
            "errorMessage.doesNotContain=" + DEFAULT_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where createdAt equals to
        defaultTransactionIngestionFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where createdAt in
        defaultTransactionIngestionFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        // Get all the transactionIngestionList where createdAt is not null
        defaultTransactionIngestionFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsByAccountIsEqualToSomething() throws Exception {
        FinancialAccount account;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            transactionIngestionRepository.saveAndFlush(transactionIngestion);
            account = FinancialAccountResourceIT.createEntity(em);
        } else {
            account = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        em.persist(account);
        em.flush();
        transactionIngestion.setAccount(account);
        transactionIngestionRepository.saveAndFlush(transactionIngestion);
        Long accountId = account.getId();
        // Get all the transactionIngestionList where account equals to accountId
        defaultTransactionIngestionShouldBeFound("accountId.equals=" + accountId);

        // Get all the transactionIngestionList where account equals to (accountId + 1)
        defaultTransactionIngestionShouldNotBeFound("accountId.equals=" + (accountId + 1));
    }

    private void defaultTransactionIngestionFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTransactionIngestionShouldBeFound(shouldBeFound);
        defaultTransactionIngestionShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTransactionIngestionShouldBeFound(String filter) throws Exception {
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].ingestionType").value(hasItem(DEFAULT_INGESTION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].sourceLabel").value(hasItem(DEFAULT_SOURCE_LABEL)))
            .andExpect(jsonPath("$.[*].startedAt").value(hasItem(DEFAULT_STARTED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())))
            .andExpect(jsonPath("$.[*].recordsReceived").value(hasItem(DEFAULT_RECORDS_RECEIVED)))
            .andExpect(jsonPath("$.[*].recordsCreated").value(hasItem(DEFAULT_RECORDS_CREATED)))
            .andExpect(jsonPath("$.[*].recordsSkipped").value(hasItem(DEFAULT_RECORDS_SKIPPED)))
            .andExpect(jsonPath("$.[*].recordsRejected").value(hasItem(DEFAULT_RECORDS_REJECTED)))
            .andExpect(jsonPath("$.[*].errorMessage").value(hasItem(DEFAULT_ERROR_MESSAGE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));

        // Check, that the count call also returns 1
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTransactionIngestionShouldNotBeFound(String filter) throws Exception {
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingTransactionIngestion() throws Exception {
        // Get the transactionIngestion
        restTransactionIngestionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionIngestion() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionIngestion
        TransactionIngestion updatedTransactionIngestion = transactionIngestionRepository
            .findById(transactionIngestion.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionIngestion are not directly saved in db
        em.detach(updatedTransactionIngestion);
        updatedTransactionIngestion
            .status(UPDATED_STATUS)
            .sourceLabel(UPDATED_SOURCE_LABEL)
            .completedAt(UPDATED_COMPLETED_AT)
            .recordsReceived(UPDATED_RECORDS_RECEIVED)
            .recordsCreated(UPDATED_RECORDS_CREATED)
            .recordsSkipped(UPDATED_RECORDS_SKIPPED)
            .recordsRejected(UPDATED_RECORDS_REJECTED)
            .errorMessage(UPDATED_ERROR_MESSAGE);
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(updatedTransactionIngestion);

        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionIngestion persisted = getPersistedTransactionIngestion(transactionIngestion);
        assertThat(persisted.getIngestionType()).isEqualTo(DEFAULT_INGESTION_TYPE);
        assertThat(persisted.getStartedAt()).isEqualTo(DEFAULT_STARTED_AT);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(persisted.getSourceLabel()).isEqualTo(UPDATED_SOURCE_LABEL);
        assertThat(persisted.getRecordsReceived()).isEqualTo(UPDATED_RECORDS_RECEIVED);
    }

    @Test
    @Transactional
    void putNonExistingTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionIngestion using partial update
        TransactionIngestion partialUpdatedTransactionIngestion = new TransactionIngestion();
        partialUpdatedTransactionIngestion.setId(transactionIngestion.getId());

        String patchJson =
            "{\"id\":" +
            transactionIngestion.getId() +
            ",\"status\":\"" +
            UPDATED_STATUS +
            "\",\"recordsCreated\":" +
            UPDATED_RECORDS_CREATED +
            ",\"errorMessage\":\"" +
            UPDATED_ERROR_MESSAGE +
            "\"}";

        restTransactionIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the TransactionIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionIngestion persisted = getPersistedTransactionIngestion(transactionIngestion);
        assertThat(persisted.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(persisted.getRecordsCreated()).isEqualTo(UPDATED_RECORDS_CREATED);
        assertThat(persisted.getErrorMessage()).isEqualTo(UPDATED_ERROR_MESSAGE);
        assertThat(persisted.getStartedAt()).isEqualTo(DEFAULT_STARTED_AT);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void fullUpdateTransactionIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionIngestion using partial update
        TransactionIngestion partialUpdatedTransactionIngestion = new TransactionIngestion();
        partialUpdatedTransactionIngestion.setId(transactionIngestion.getId());

        String patchJson =
            "{\"id\":" +
            transactionIngestion.getId() +
            ",\"status\":\"" +
            UPDATED_STATUS +
            "\",\"sourceLabel\":\"" +
            UPDATED_SOURCE_LABEL +
            "\",\"completedAt\":\"" +
            UPDATED_COMPLETED_AT +
            "\",\"recordsReceived\":" +
            UPDATED_RECORDS_RECEIVED +
            ",\"recordsCreated\":" +
            UPDATED_RECORDS_CREATED +
            ",\"recordsSkipped\":" +
            UPDATED_RECORDS_SKIPPED +
            ",\"recordsRejected\":" +
            UPDATED_RECORDS_REJECTED +
            ",\"errorMessage\":\"" +
            UPDATED_ERROR_MESSAGE +
            "\"}";

        restTransactionIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the TransactionIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionIngestion persisted = getPersistedTransactionIngestion(transactionIngestion);
        assertThat(persisted.getIngestionType()).isEqualTo(DEFAULT_INGESTION_TYPE);
        assertThat(persisted.getStartedAt()).isEqualTo(DEFAULT_STARTED_AT);
        assertThat(persisted.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persisted.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(persisted.getSourceLabel()).isEqualTo(UPDATED_SOURCE_LABEL);
    }

    @Test
    @Transactional
    void patchNonExistingTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionIngestion.setId(longCount.incrementAndGet());

        // Create the TransactionIngestion
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionIngestion() throws Exception {
        // Initialize the database
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionIngestion
        restTransactionIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getTransactionIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();

        restTransactionIngestionMockMvc.perform(get(ENTITY_API_URL_ID, otherIngestion.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllTransactionIngestionsDoesNotIncludeAnotherUsersIngestions() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();

        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherIngestion.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetTransactionIngestionOwnedByAnotherUser() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();

        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherIngestion.getId().intValue()));
    }

    @Test
    @Transactional
    void putTransactionIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(otherIngestion);
        transactionIngestionDTO.setStatus(UPDATED_STATUS);

        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        String patchJson = "{\"id\":" + otherIngestion.getId() + ",\"status\":\"" + UPDATED_STATUS + "\"}";

        restTransactionIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteTransactionIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();

        restTransactionIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllTransactionIngestionsIncludingOtherUsers() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();

        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherIngestion.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateTransactionIngestionOwnedByAnotherUser() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(otherIngestion);
        transactionIngestionDTO.setStatus(UPDATED_STATUS);

        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(UPDATED_STATUS.toString()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteTransactionIngestionOwnedByAnotherUser() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restTransactionIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createTransactionIngestionWithAccountOwnedByAnotherUserFails() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(em, createOtherUser(em));
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setIngestionType(DEFAULT_INGESTION_TYPE);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        transactionIngestionDTO.setAccount(accountDTO);

        restTransactionIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCreateTransactionIngestionWithForeignAccount() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(em, createOtherUser(em));
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setIngestionType(IngestionType.API);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        transactionIngestionDTO.setAccount(accountDTO);

        restTransactionIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionIngestionDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ingestionType").value(IngestionType.API.toString()));
    }

    @Test
    @Transactional
    void updateTransactionIngestionWithDifferentAccountFails() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        FinancialAccount otherAccount = createAccountForUser(em, createOtherUser(em));
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherAccount.getId());
        transactionIngestionDTO.setAccount(accountDTO);

        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionIngestionWithNullAccountFails() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        String patchJson = "{\"id\":" + transactionIngestion.getId() + ",\"account\":null}";

        restTransactionIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionIngestionWithDifferentAccountFails() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        FinancialAccount otherAccount = createAccountForUser(em, createOtherUser(em));
        String patchJson = "{\"id\":" + transactionIngestion.getId() + ",\"account\":{\"id\":" + otherAccount.getId() + "}}";

        restTransactionIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateTransactionIngestionWithDifferentIngestionTypeFails() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        TransactionIngestionDTO transactionIngestionDTO = transactionIngestionMapper.toDto(transactionIngestion);
        transactionIngestionDTO.setIngestionType(UPDATED_INGESTION_TYPE);

        restTransactionIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void findAllWhereFileIngestionIsNullIsScopedAndFileOnly() throws Exception {
        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);
        TransactionIngestion otherFileIngestion = saveIngestionOnOtherUsersAccount();
        otherFileIngestion.setIngestionType(IngestionType.FILE);
        transactionIngestionRepository.saveAndFlush(otherFileIngestion);

        TransactionIngestion apiIngestion = createEntity(em);
        apiIngestion.setIngestionType(IngestionType.API);
        apiIngestion = transactionIngestionRepository.saveAndFlush(apiIngestion);

        assertThat(transactionIngestionService.findAllWhereFileIngestionIsNull())
            .extracting(TransactionIngestionDTO::getId)
            .contains(transactionIngestion.getId())
            .doesNotContain(otherFileIngestion.getId(), apiIngestion.getId());

        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "/file-ingestion-is-null"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(hasItem(transactionIngestion.getId().intValue())))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(otherFileIngestion.getId().intValue()))))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(apiIngestion.getId().intValue()))));
    }

    @Test
    @Transactional
    void findAllWhereApiIngestionIsNullIsScopedAndApiOnly() throws Exception {
        TransactionIngestion apiIngestion = createEntity(em);
        apiIngestion.setIngestionType(IngestionType.API);
        apiIngestion = transactionIngestionRepository.saveAndFlush(apiIngestion);

        TransactionIngestion otherApiIngestion = saveIngestionOnOtherUsersAccount();
        otherApiIngestion.setIngestionType(IngestionType.API);
        transactionIngestionRepository.saveAndFlush(otherApiIngestion);

        insertedTransactionIngestion = transactionIngestionRepository.saveAndFlush(transactionIngestion);

        assertThat(transactionIngestionService.findAllWhereApiIngestionIsNull())
            .extracting(TransactionIngestionDTO::getId)
            .contains(apiIngestion.getId())
            .doesNotContain(otherApiIngestion.getId(), transactionIngestion.getId());

        restTransactionIngestionMockMvc
            .perform(get(ENTITY_API_URL + "/api-ingestion-is-null"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(hasItem(apiIngestion.getId().intValue())))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(otherApiIngestion.getId().intValue()))))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(transactionIngestion.getId().intValue()))));
    }

    private TransactionIngestion saveIngestionOnOtherUsersAccount() {
        FinancialAccount otherAccount = createAccountForUser(em, createOtherUser(em));
        TransactionIngestion otherIngestion = createEntity(em);
        otherIngestion.setAccount(otherAccount);
        return transactionIngestionRepository.saveAndFlush(otherIngestion);
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

    protected long getRepositoryCount() {
        return transactionIngestionRepository.count();
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

    protected TransactionIngestion getPersistedTransactionIngestion(TransactionIngestion transactionIngestion) {
        return transactionIngestionRepository.findById(transactionIngestion.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionIngestionToMatchAllProperties(TransactionIngestion expectedTransactionIngestion) {
        assertTransactionIngestionAllPropertiesEquals(
            expectedTransactionIngestion,
            getPersistedTransactionIngestion(expectedTransactionIngestion)
        );
    }

    protected void assertPersistedTransactionIngestionToMatchUpdatableProperties(TransactionIngestion expectedTransactionIngestion) {
        assertTransactionIngestionAllUpdatablePropertiesEquals(
            expectedTransactionIngestion,
            getPersistedTransactionIngestion(expectedTransactionIngestion)
        );
    }
}
