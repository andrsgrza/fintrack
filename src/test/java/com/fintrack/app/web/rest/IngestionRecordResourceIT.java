package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.IngestionRecordAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.mapper.IngestionRecordMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link IngestionRecordResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class IngestionRecordResourceIT {

    private static final Integer DEFAULT_RECORD_INDEX = 0;
    private static final Integer UPDATED_RECORD_INDEX = 1;
    private static final Integer SMALLER_RECORD_INDEX = 0 - 1;

    private static final String DEFAULT_EXTERNAL_RECORD_ID = "AAAAAAAAAA";
    private static final String UPDATED_EXTERNAL_RECORD_ID = "BBBBBBBBBB";

    private static final IngestionRecordStatus DEFAULT_STATUS = IngestionRecordStatus.CREATED;
    private static final IngestionRecordStatus UPDATED_STATUS = IngestionRecordStatus.SKIPPED_DUPLICATE;

    private static final String DEFAULT_RAW_DATA = "AAAAAAAAAA";
    private static final String UPDATED_RAW_DATA = "BBBBBBBBBB";

    private static final String DEFAULT_ERROR_CODE = "AAAAAAAAAA";
    private static final String UPDATED_ERROR_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_ERROR_MESSAGE = "AAAAAAAAAA";
    private static final String UPDATED_ERROR_MESSAGE = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/ingestion-records";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private IngestionRecordRepository ingestionRecordRepository;

    @Autowired
    private IngestionRecordMapper ingestionRecordMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restIngestionRecordMockMvc;

    private IngestionRecord ingestionRecord;

    private IngestionRecord insertedIngestionRecord;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static IngestionRecord createEntity(EntityManager em) {
        IngestionRecord ingestionRecord = new IngestionRecord()
            .recordIndex(DEFAULT_RECORD_INDEX)
            .externalRecordId(DEFAULT_EXTERNAL_RECORD_ID)
            .status(DEFAULT_STATUS)
            .rawData(DEFAULT_RAW_DATA)
            .errorCode(DEFAULT_ERROR_CODE)
            .errorMessage(DEFAULT_ERROR_MESSAGE)
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
        ingestionRecord.setTransactionIngestion(transactionIngestion);
        return ingestionRecord;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static IngestionRecord createUpdatedEntity(EntityManager em) {
        IngestionRecord updatedIngestionRecord = new IngestionRecord()
            .recordIndex(UPDATED_RECORD_INDEX)
            .externalRecordId(UPDATED_EXTERNAL_RECORD_ID)
            .status(UPDATED_STATUS)
            .rawData(UPDATED_RAW_DATA)
            .errorCode(UPDATED_ERROR_CODE)
            .errorMessage(UPDATED_ERROR_MESSAGE)
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
        updatedIngestionRecord.setTransactionIngestion(transactionIngestion);
        return updatedIngestionRecord;
    }

    @BeforeEach
    void initTest() {
        ingestionRecord = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedIngestionRecord != null) {
            ingestionRecordRepository.delete(insertedIngestionRecord);
            insertedIngestionRecord = null;
        }
    }

    @Test
    @Transactional
    void createIngestionRecord() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);
        var returnedIngestionRecordDTO = om.readValue(
            restIngestionRecordMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            IngestionRecordDTO.class
        );

        // Validate the IngestionRecord in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedIngestionRecord = ingestionRecordMapper.toEntity(returnedIngestionRecordDTO);
        assertIngestionRecordUpdatableFieldsEquals(returnedIngestionRecord, getPersistedIngestionRecord(returnedIngestionRecord));

        insertedIngestionRecord = returnedIngestionRecord;
    }

    @Test
    @Transactional
    void createIngestionRecordWithExistingId() throws Exception {
        // Create the IngestionRecord with an existing ID
        ingestionRecord.setId(1L);
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restIngestionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isBadRequest());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkRecordIndexIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ingestionRecord.setRecordIndex(null);

        // Create the IngestionRecord, which fails.
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        restIngestionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ingestionRecord.setStatus(null);

        // Create the IngestionRecord, which fails.
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        restIngestionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ingestionRecord.setCreatedAt(null);

        // Create the IngestionRecord, which fails.
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        restIngestionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllIngestionRecords() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ingestionRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].recordIndex").value(hasItem(DEFAULT_RECORD_INDEX)))
            .andExpect(jsonPath("$.[*].externalRecordId").value(hasItem(DEFAULT_EXTERNAL_RECORD_ID)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].rawData").value(hasItem(DEFAULT_RAW_DATA)))
            .andExpect(jsonPath("$.[*].errorCode").value(hasItem(DEFAULT_ERROR_CODE)))
            .andExpect(jsonPath("$.[*].errorMessage").value(hasItem(DEFAULT_ERROR_MESSAGE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @Test
    @Transactional
    void getIngestionRecord() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get the ingestionRecord
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL_ID, ingestionRecord.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(ingestionRecord.getId().intValue()))
            .andExpect(jsonPath("$.recordIndex").value(DEFAULT_RECORD_INDEX))
            .andExpect(jsonPath("$.externalRecordId").value(DEFAULT_EXTERNAL_RECORD_ID))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.rawData").value(DEFAULT_RAW_DATA))
            .andExpect(jsonPath("$.errorCode").value(DEFAULT_ERROR_CODE))
            .andExpect(jsonPath("$.errorMessage").value(DEFAULT_ERROR_MESSAGE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getIngestionRecordsByIdFiltering() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        Long id = ingestionRecord.getId();

        defaultIngestionRecordFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultIngestionRecordFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultIngestionRecordFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex equals to
        defaultIngestionRecordFiltering("recordIndex.equals=" + DEFAULT_RECORD_INDEX, "recordIndex.equals=" + UPDATED_RECORD_INDEX);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex in
        defaultIngestionRecordFiltering(
            "recordIndex.in=" + DEFAULT_RECORD_INDEX + "," + UPDATED_RECORD_INDEX,
            "recordIndex.in=" + UPDATED_RECORD_INDEX
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex is not null
        defaultIngestionRecordFiltering("recordIndex.specified=true", "recordIndex.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex is greater than or equal to
        defaultIngestionRecordFiltering(
            "recordIndex.greaterThanOrEqual=" + DEFAULT_RECORD_INDEX,
            "recordIndex.greaterThanOrEqual=" + UPDATED_RECORD_INDEX
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex is less than or equal to
        defaultIngestionRecordFiltering(
            "recordIndex.lessThanOrEqual=" + DEFAULT_RECORD_INDEX,
            "recordIndex.lessThanOrEqual=" + SMALLER_RECORD_INDEX
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex is less than
        defaultIngestionRecordFiltering("recordIndex.lessThan=" + UPDATED_RECORD_INDEX, "recordIndex.lessThan=" + DEFAULT_RECORD_INDEX);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByRecordIndexIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where recordIndex is greater than
        defaultIngestionRecordFiltering(
            "recordIndex.greaterThan=" + SMALLER_RECORD_INDEX,
            "recordIndex.greaterThan=" + DEFAULT_RECORD_INDEX
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByExternalRecordIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where externalRecordId equals to
        defaultIngestionRecordFiltering(
            "externalRecordId.equals=" + DEFAULT_EXTERNAL_RECORD_ID,
            "externalRecordId.equals=" + UPDATED_EXTERNAL_RECORD_ID
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByExternalRecordIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where externalRecordId in
        defaultIngestionRecordFiltering(
            "externalRecordId.in=" + DEFAULT_EXTERNAL_RECORD_ID + "," + UPDATED_EXTERNAL_RECORD_ID,
            "externalRecordId.in=" + UPDATED_EXTERNAL_RECORD_ID
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByExternalRecordIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where externalRecordId is not null
        defaultIngestionRecordFiltering("externalRecordId.specified=true", "externalRecordId.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByExternalRecordIdContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where externalRecordId contains
        defaultIngestionRecordFiltering(
            "externalRecordId.contains=" + DEFAULT_EXTERNAL_RECORD_ID,
            "externalRecordId.contains=" + UPDATED_EXTERNAL_RECORD_ID
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByExternalRecordIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where externalRecordId does not contain
        defaultIngestionRecordFiltering(
            "externalRecordId.doesNotContain=" + UPDATED_EXTERNAL_RECORD_ID,
            "externalRecordId.doesNotContain=" + DEFAULT_EXTERNAL_RECORD_ID
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where status equals to
        defaultIngestionRecordFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where status in
        defaultIngestionRecordFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where status is not null
        defaultIngestionRecordFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorCodeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorCode equals to
        defaultIngestionRecordFiltering("errorCode.equals=" + DEFAULT_ERROR_CODE, "errorCode.equals=" + UPDATED_ERROR_CODE);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorCodeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorCode in
        defaultIngestionRecordFiltering(
            "errorCode.in=" + DEFAULT_ERROR_CODE + "," + UPDATED_ERROR_CODE,
            "errorCode.in=" + UPDATED_ERROR_CODE
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorCodeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorCode is not null
        defaultIngestionRecordFiltering("errorCode.specified=true", "errorCode.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorCodeContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorCode contains
        defaultIngestionRecordFiltering("errorCode.contains=" + DEFAULT_ERROR_CODE, "errorCode.contains=" + UPDATED_ERROR_CODE);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorCodeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorCode does not contain
        defaultIngestionRecordFiltering("errorCode.doesNotContain=" + UPDATED_ERROR_CODE, "errorCode.doesNotContain=" + DEFAULT_ERROR_CODE);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorMessageIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorMessage equals to
        defaultIngestionRecordFiltering("errorMessage.equals=" + DEFAULT_ERROR_MESSAGE, "errorMessage.equals=" + UPDATED_ERROR_MESSAGE);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorMessageIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorMessage in
        defaultIngestionRecordFiltering(
            "errorMessage.in=" + DEFAULT_ERROR_MESSAGE + "," + UPDATED_ERROR_MESSAGE,
            "errorMessage.in=" + UPDATED_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorMessageIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorMessage is not null
        defaultIngestionRecordFiltering("errorMessage.specified=true", "errorMessage.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorMessageContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorMessage contains
        defaultIngestionRecordFiltering("errorMessage.contains=" + DEFAULT_ERROR_MESSAGE, "errorMessage.contains=" + UPDATED_ERROR_MESSAGE);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByErrorMessageNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where errorMessage does not contain
        defaultIngestionRecordFiltering(
            "errorMessage.doesNotContain=" + UPDATED_ERROR_MESSAGE,
            "errorMessage.doesNotContain=" + DEFAULT_ERROR_MESSAGE
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where createdAt equals to
        defaultIngestionRecordFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where createdAt in
        defaultIngestionRecordFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        // Get all the ingestionRecordList where createdAt is not null
        defaultIngestionRecordFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByFinancialTransactionIsEqualToSomething() throws Exception {
        FinancialTransaction financialTransaction;
        if (TestUtil.findAll(em, FinancialTransaction.class).isEmpty()) {
            ingestionRecordRepository.saveAndFlush(ingestionRecord);
            financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        } else {
            financialTransaction = TestUtil.findAll(em, FinancialTransaction.class).get(0);
        }
        em.persist(financialTransaction);
        em.flush();
        ingestionRecord.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(ingestionRecord);
        Long financialTransactionId = financialTransaction.getId();
        // Get all the ingestionRecordList where financialTransaction equals to financialTransactionId
        defaultIngestionRecordShouldBeFound("financialTransactionId.equals=" + financialTransactionId);

        // Get all the ingestionRecordList where financialTransaction equals to (financialTransactionId + 1)
        defaultIngestionRecordShouldNotBeFound("financialTransactionId.equals=" + (financialTransactionId + 1));
    }

    @Test
    @Transactional
    void getAllIngestionRecordsByTransactionIngestionIsEqualToSomething() throws Exception {
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            ingestionRecordRepository.saveAndFlush(ingestionRecord);
            transactionIngestion = TransactionIngestionResourceIT.createEntity(em);
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        em.persist(transactionIngestion);
        em.flush();
        ingestionRecord.setTransactionIngestion(transactionIngestion);
        ingestionRecordRepository.saveAndFlush(ingestionRecord);
        Long transactionIngestionId = transactionIngestion.getId();
        // Get all the ingestionRecordList where transactionIngestion equals to transactionIngestionId
        defaultIngestionRecordShouldBeFound("transactionIngestionId.equals=" + transactionIngestionId);

        // Get all the ingestionRecordList where transactionIngestion equals to (transactionIngestionId + 1)
        defaultIngestionRecordShouldNotBeFound("transactionIngestionId.equals=" + (transactionIngestionId + 1));
    }

    private void defaultIngestionRecordFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultIngestionRecordShouldBeFound(shouldBeFound);
        defaultIngestionRecordShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultIngestionRecordShouldBeFound(String filter) throws Exception {
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ingestionRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].recordIndex").value(hasItem(DEFAULT_RECORD_INDEX)))
            .andExpect(jsonPath("$.[*].externalRecordId").value(hasItem(DEFAULT_EXTERNAL_RECORD_ID)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].rawData").value(hasItem(DEFAULT_RAW_DATA)))
            .andExpect(jsonPath("$.[*].errorCode").value(hasItem(DEFAULT_ERROR_CODE)))
            .andExpect(jsonPath("$.[*].errorMessage").value(hasItem(DEFAULT_ERROR_MESSAGE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));

        // Check, that the count call also returns 1
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultIngestionRecordShouldNotBeFound(String filter) throws Exception {
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restIngestionRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingIngestionRecord() throws Exception {
        // Get the ingestionRecord
        restIngestionRecordMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingIngestionRecord() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ingestionRecord
        IngestionRecord updatedIngestionRecord = ingestionRecordRepository.findById(ingestionRecord.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedIngestionRecord are not directly saved in db
        em.detach(updatedIngestionRecord);
        updatedIngestionRecord
            .recordIndex(UPDATED_RECORD_INDEX)
            .externalRecordId(UPDATED_EXTERNAL_RECORD_ID)
            .status(UPDATED_STATUS)
            .rawData(UPDATED_RAW_DATA)
            .errorCode(UPDATED_ERROR_CODE)
            .errorMessage(UPDATED_ERROR_MESSAGE)
            .createdAt(UPDATED_CREATED_AT);
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(updatedIngestionRecord);

        restIngestionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ingestionRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ingestionRecordDTO))
            )
            .andExpect(status().isOk());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedIngestionRecordToMatchAllProperties(updatedIngestionRecord);
    }

    @Test
    @Transactional
    void putNonExistingIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ingestionRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ingestionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ingestionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateIngestionRecordWithPatch() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ingestionRecord using partial update
        IngestionRecord partialUpdatedIngestionRecord = new IngestionRecord();
        partialUpdatedIngestionRecord.setId(ingestionRecord.getId());

        partialUpdatedIngestionRecord
            .recordIndex(UPDATED_RECORD_INDEX)
            .externalRecordId(UPDATED_EXTERNAL_RECORD_ID)
            .status(UPDATED_STATUS)
            .rawData(UPDATED_RAW_DATA)
            .errorCode(UPDATED_ERROR_CODE)
            .errorMessage(UPDATED_ERROR_MESSAGE)
            .createdAt(UPDATED_CREATED_AT);

        restIngestionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedIngestionRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedIngestionRecord))
            )
            .andExpect(status().isOk());

        // Validate the IngestionRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertIngestionRecordUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedIngestionRecord, ingestionRecord),
            getPersistedIngestionRecord(ingestionRecord)
        );
    }

    @Test
    @Transactional
    void fullUpdateIngestionRecordWithPatch() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ingestionRecord using partial update
        IngestionRecord partialUpdatedIngestionRecord = new IngestionRecord();
        partialUpdatedIngestionRecord.setId(ingestionRecord.getId());

        partialUpdatedIngestionRecord
            .recordIndex(UPDATED_RECORD_INDEX)
            .externalRecordId(UPDATED_EXTERNAL_RECORD_ID)
            .status(UPDATED_STATUS)
            .rawData(UPDATED_RAW_DATA)
            .errorCode(UPDATED_ERROR_CODE)
            .errorMessage(UPDATED_ERROR_MESSAGE)
            .createdAt(UPDATED_CREATED_AT);

        restIngestionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedIngestionRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedIngestionRecord))
            )
            .andExpect(status().isOk());

        // Validate the IngestionRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertIngestionRecordUpdatableFieldsEquals(
            partialUpdatedIngestionRecord,
            getPersistedIngestionRecord(partialUpdatedIngestionRecord)
        );
    }

    @Test
    @Transactional
    void patchNonExistingIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, ingestionRecordDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ingestionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ingestionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamIngestionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ingestionRecord.setId(longCount.incrementAndGet());

        // Create the IngestionRecord
        IngestionRecordDTO ingestionRecordDTO = ingestionRecordMapper.toDto(ingestionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIngestionRecordMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(ingestionRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the IngestionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteIngestionRecord() throws Exception {
        // Initialize the database
        insertedIngestionRecord = ingestionRecordRepository.saveAndFlush(ingestionRecord);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the ingestionRecord
        restIngestionRecordMockMvc
            .perform(delete(ENTITY_API_URL_ID, ingestionRecord.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return ingestionRecordRepository.count();
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

    protected IngestionRecord getPersistedIngestionRecord(IngestionRecord ingestionRecord) {
        return ingestionRecordRepository.findById(ingestionRecord.getId()).orElseThrow();
    }

    protected void assertPersistedIngestionRecordToMatchAllProperties(IngestionRecord expectedIngestionRecord) {
        assertIngestionRecordAllPropertiesEquals(expectedIngestionRecord, getPersistedIngestionRecord(expectedIngestionRecord));
    }

    protected void assertPersistedIngestionRecordToMatchUpdatableProperties(IngestionRecord expectedIngestionRecord) {
        assertIngestionRecordAllUpdatablePropertiesEquals(expectedIngestionRecord, getPersistedIngestionRecord(expectedIngestionRecord));
    }
}
