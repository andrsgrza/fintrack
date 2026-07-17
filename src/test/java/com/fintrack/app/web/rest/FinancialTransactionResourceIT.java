package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.FinancialTransactionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.FinancialTransactionService;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
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
 * Integration tests for the {@link FinancialTransactionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class FinancialTransactionResourceIT {

    private static final LocalDate DEFAULT_TRANSACTION_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_TRANSACTION_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_TRANSACTION_DATE = LocalDate.ofEpochDay(-1L);

    private static final LocalDate DEFAULT_POSTING_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_POSTING_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_POSTING_DATE = LocalDate.ofEpochDay(-1L);

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal(1);
    private static final BigDecimal UPDATED_AMOUNT = new BigDecimal(2);
    private static final BigDecimal SMALLER_AMOUNT = new BigDecimal(0);

    private static final TransactionFlow DEFAULT_FLOW = TransactionFlow.IN;
    private static final TransactionFlow UPDATED_FLOW = TransactionFlow.OUT;

    private static final TransactionOrigin DEFAULT_ORIGIN = TransactionOrigin.MANUAL;
    private static final TransactionOrigin UPDATED_ORIGIN = TransactionOrigin.FILE_IMPORT;

    private static final String DEFAULT_EXTERNAL_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_EXTERNAL_REFERENCE = "BBBBBBBBBB";

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/financial-transactions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String RULE_PREVIEW_API_URL = ENTITY_API_URL + "/rule-preview";
    private static final String OUTGOING_INTERNAL_TRANSFER_CANDIDATES_API_URL = ENTITY_API_URL + "/outgoing-internal-transfer-candidates";
    private static final String INCOMING_INTERNAL_TRANSFER_CANDIDATES_API_URL = ENTITY_API_URL + "/incoming-internal-transfer-candidates";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepositoryMock;

    @Autowired
    private FinancialTransactionMapper financialTransactionMapper;

    @Autowired
    private FinancialTransactionService financialTransactionService;

    @Mock
    private FinancialTransactionService financialTransactionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restFinancialTransactionMockMvc;

    private FinancialTransaction financialTransaction;

    private FinancialTransaction insertedFinancialTransaction;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialTransaction createEntity(EntityManager em) {
        FinancialTransaction financialTransaction = new FinancialTransaction()
            .transactionDate(DEFAULT_TRANSACTION_DATE)
            .postingDate(DEFAULT_POSTING_DATE)
            .description(DEFAULT_DESCRIPTION)
            .amount(DEFAULT_AMOUNT)
            .flow(DEFAULT_FLOW)
            .origin(DEFAULT_ORIGIN)
            .externalReference(DEFAULT_EXTERNAL_REFERENCE)
            .notes(DEFAULT_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        // Add required entity
        FinancialAccount financialAccount;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialAccount = FinancialAccountResourceIT.createEntity(em);
            em.persist(financialAccount);
            em.flush();
        } else {
            financialAccount = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        financialTransaction.setAccount(financialAccount);
        return financialTransaction;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialTransaction createUpdatedEntity(EntityManager em) {
        FinancialTransaction updatedFinancialTransaction = new FinancialTransaction()
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .postingDate(UPDATED_POSTING_DATE)
            .description(UPDATED_DESCRIPTION)
            .amount(UPDATED_AMOUNT)
            .flow(UPDATED_FLOW)
            .origin(UPDATED_ORIGIN)
            .externalReference(UPDATED_EXTERNAL_REFERENCE)
            .notes(UPDATED_NOTES)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        // Add required entity
        FinancialAccount financialAccount;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialAccount = FinancialAccountResourceIT.createUpdatedEntity(em);
            em.persist(financialAccount);
            em.flush();
        } else {
            financialAccount = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        updatedFinancialTransaction.setAccount(financialAccount);
        return updatedFinancialTransaction;
    }

    @BeforeEach
    void initTest() {
        financialTransaction = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedFinancialTransaction != null) {
            financialTransactionRepository.delete(insertedFinancialTransaction);
            insertedFinancialTransaction = null;
        }
    }

    @Test
    @Transactional
    void createFinancialTransaction() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        var returnedFinancialTransactionDTO = om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );

        // Validate the FinancialTransaction in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedFinancialTransaction = financialTransactionMapper.toEntity(returnedFinancialTransactionDTO);
        assertFinancialTransactionUpdatableFieldsEquals(
            returnedFinancialTransaction,
            getPersistedFinancialTransaction(returnedFinancialTransaction)
        );

        insertedFinancialTransaction = returnedFinancialTransaction;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithExistingId() throws Exception {
        // Create the FinancialTransaction with an existing ID
        financialTransaction.setId(1L);
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTransactionDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialTransaction.setTransactionDate(null);

        // Create the FinancialTransaction, which fails.
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialTransaction.setDescription(null);

        // Create the FinancialTransaction, which fails.
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialTransaction.setAmount(null);

        // Create the FinancialTransaction, which fails.
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFlowIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialTransaction.setFlow(null);

        // Create the FinancialTransaction, which fails.
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkOriginIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialTransaction.setOrigin(null);

        // Create the FinancialTransaction, which fails.
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createFinancialTransactionIgnoresMissingCreatedAtAndSetsServerTimestamp() throws Exception {
        financialTransaction.setCreatedAt(null);

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        FinancialTransactionDTO returnedFinancialTransactionDTO = om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );

        assertThat(returnedFinancialTransactionDTO.getCreatedAt()).isNotNull();
        insertedFinancialTransaction = financialTransactionMapper.toEntity(returnedFinancialTransactionDTO);
    }

    @Test
    @Transactional
    void createFinancialTransactionIgnoresMissingUpdatedAtAndSetsServerTimestamp() throws Exception {
        financialTransaction.setUpdatedAt(null);

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        FinancialTransactionDTO returnedFinancialTransactionDTO = om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );

        assertThat(returnedFinancialTransactionDTO.getUpdatedAt()).isNotNull();
        insertedFinancialTransaction = financialTransactionMapper.toEntity(returnedFinancialTransactionDTO);
    }

    @Test
    @Transactional
    void getAllFinancialTransactions() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialTransaction.getId().intValue())))
            .andExpect(jsonPath("$.[*].transactionDate").value(hasItem(DEFAULT_TRANSACTION_DATE.toString())))
            .andExpect(jsonPath("$.[*].postingDate").value(hasItem(DEFAULT_POSTING_DATE.toString())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].flow").value(hasItem(DEFAULT_FLOW.toString())))
            .andExpect(jsonPath("$.[*].origin").value(hasItem(DEFAULT_ORIGIN.toString())))
            .andExpect(jsonPath("$.[*].externalReference").value(hasItem(DEFAULT_EXTERNAL_REFERENCE)))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialTransactionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(financialTransactionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(financialTransactionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialTransactionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(financialTransactionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(financialTransactionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getFinancialTransaction() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get the financialTransaction
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL_ID, financialTransaction.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(financialTransaction.getId().intValue()))
            .andExpect(jsonPath("$.transactionDate").value(DEFAULT_TRANSACTION_DATE.toString()))
            .andExpect(jsonPath("$.postingDate").value(DEFAULT_POSTING_DATE.toString()))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.amount").value(sameNumber(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.flow").value(DEFAULT_FLOW.toString()))
            .andExpect(jsonPath("$.origin").value(DEFAULT_ORIGIN.toString()))
            .andExpect(jsonPath("$.externalReference").value(DEFAULT_EXTERNAL_REFERENCE))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getFinancialTransactionsByIdFiltering() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        Long id = financialTransaction.getId();

        defaultFinancialTransactionFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultFinancialTransactionFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultFinancialTransactionFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate equals to
        defaultFinancialTransactionFiltering(
            "transactionDate.equals=" + DEFAULT_TRANSACTION_DATE,
            "transactionDate.equals=" + UPDATED_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate in
        defaultFinancialTransactionFiltering(
            "transactionDate.in=" + DEFAULT_TRANSACTION_DATE + "," + UPDATED_TRANSACTION_DATE,
            "transactionDate.in=" + UPDATED_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate is not null
        defaultFinancialTransactionFiltering("transactionDate.specified=true", "transactionDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate is greater than or equal to
        defaultFinancialTransactionFiltering(
            "transactionDate.greaterThanOrEqual=" + DEFAULT_TRANSACTION_DATE,
            "transactionDate.greaterThanOrEqual=" + UPDATED_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate is less than or equal to
        defaultFinancialTransactionFiltering(
            "transactionDate.lessThanOrEqual=" + DEFAULT_TRANSACTION_DATE,
            "transactionDate.lessThanOrEqual=" + SMALLER_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate is less than
        defaultFinancialTransactionFiltering(
            "transactionDate.lessThan=" + UPDATED_TRANSACTION_DATE,
            "transactionDate.lessThan=" + DEFAULT_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where transactionDate is greater than
        defaultFinancialTransactionFiltering(
            "transactionDate.greaterThan=" + SMALLER_TRANSACTION_DATE,
            "transactionDate.greaterThan=" + DEFAULT_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate equals to
        defaultFinancialTransactionFiltering("postingDate.equals=" + DEFAULT_POSTING_DATE, "postingDate.equals=" + UPDATED_POSTING_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate in
        defaultFinancialTransactionFiltering(
            "postingDate.in=" + DEFAULT_POSTING_DATE + "," + UPDATED_POSTING_DATE,
            "postingDate.in=" + UPDATED_POSTING_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate is not null
        defaultFinancialTransactionFiltering("postingDate.specified=true", "postingDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate is greater than or equal to
        defaultFinancialTransactionFiltering(
            "postingDate.greaterThanOrEqual=" + DEFAULT_POSTING_DATE,
            "postingDate.greaterThanOrEqual=" + UPDATED_POSTING_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate is less than or equal to
        defaultFinancialTransactionFiltering(
            "postingDate.lessThanOrEqual=" + DEFAULT_POSTING_DATE,
            "postingDate.lessThanOrEqual=" + SMALLER_POSTING_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate is less than
        defaultFinancialTransactionFiltering(
            "postingDate.lessThan=" + UPDATED_POSTING_DATE,
            "postingDate.lessThan=" + DEFAULT_POSTING_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByPostingDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where postingDate is greater than
        defaultFinancialTransactionFiltering(
            "postingDate.greaterThan=" + SMALLER_POSTING_DATE,
            "postingDate.greaterThan=" + DEFAULT_POSTING_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where description equals to
        defaultFinancialTransactionFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where description in
        defaultFinancialTransactionFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where description is not null
        defaultFinancialTransactionFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where description contains
        defaultFinancialTransactionFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where description does not contain
        defaultFinancialTransactionFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount equals to
        defaultFinancialTransactionFiltering("amount.equals=" + DEFAULT_AMOUNT, "amount.equals=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount in
        defaultFinancialTransactionFiltering("amount.in=" + DEFAULT_AMOUNT + "," + UPDATED_AMOUNT, "amount.in=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount is not null
        defaultFinancialTransactionFiltering("amount.specified=true", "amount.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount is greater than or equal to
        defaultFinancialTransactionFiltering("amount.greaterThanOrEqual=" + DEFAULT_AMOUNT, "amount.greaterThanOrEqual=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount is less than or equal to
        defaultFinancialTransactionFiltering("amount.lessThanOrEqual=" + DEFAULT_AMOUNT, "amount.lessThanOrEqual=" + SMALLER_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount is less than
        defaultFinancialTransactionFiltering("amount.lessThan=" + UPDATED_AMOUNT, "amount.lessThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where amount is greater than
        defaultFinancialTransactionFiltering("amount.greaterThan=" + SMALLER_AMOUNT, "amount.greaterThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByFlowIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where flow equals to
        defaultFinancialTransactionFiltering("flow.equals=" + DEFAULT_FLOW, "flow.equals=" + UPDATED_FLOW);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByFlowIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where flow in
        defaultFinancialTransactionFiltering("flow.in=" + DEFAULT_FLOW + "," + UPDATED_FLOW, "flow.in=" + UPDATED_FLOW);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByFlowIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where flow is not null
        defaultFinancialTransactionFiltering("flow.specified=true", "flow.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByOriginIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where origin equals to
        defaultFinancialTransactionFiltering("origin.equals=" + DEFAULT_ORIGIN, "origin.equals=" + UPDATED_ORIGIN);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByOriginIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where origin in
        defaultFinancialTransactionFiltering("origin.in=" + DEFAULT_ORIGIN + "," + UPDATED_ORIGIN, "origin.in=" + UPDATED_ORIGIN);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByOriginIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where origin is not null
        defaultFinancialTransactionFiltering("origin.specified=true", "origin.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByExternalReferenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where externalReference equals to
        defaultFinancialTransactionFiltering(
            "externalReference.equals=" + DEFAULT_EXTERNAL_REFERENCE,
            "externalReference.equals=" + UPDATED_EXTERNAL_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByExternalReferenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where externalReference in
        defaultFinancialTransactionFiltering(
            "externalReference.in=" + DEFAULT_EXTERNAL_REFERENCE + "," + UPDATED_EXTERNAL_REFERENCE,
            "externalReference.in=" + UPDATED_EXTERNAL_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByExternalReferenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where externalReference is not null
        defaultFinancialTransactionFiltering("externalReference.specified=true", "externalReference.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByExternalReferenceContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where externalReference contains
        defaultFinancialTransactionFiltering(
            "externalReference.contains=" + DEFAULT_EXTERNAL_REFERENCE,
            "externalReference.contains=" + UPDATED_EXTERNAL_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByExternalReferenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where externalReference does not contain
        defaultFinancialTransactionFiltering(
            "externalReference.doesNotContain=" + UPDATED_EXTERNAL_REFERENCE,
            "externalReference.doesNotContain=" + DEFAULT_EXTERNAL_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByNotesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where notes equals to
        defaultFinancialTransactionFiltering("notes.equals=" + DEFAULT_NOTES, "notes.equals=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByNotesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where notes in
        defaultFinancialTransactionFiltering("notes.in=" + DEFAULT_NOTES + "," + UPDATED_NOTES, "notes.in=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByNotesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where notes is not null
        defaultFinancialTransactionFiltering("notes.specified=true", "notes.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByNotesContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where notes contains
        defaultFinancialTransactionFiltering("notes.contains=" + DEFAULT_NOTES, "notes.contains=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByNotesNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where notes does not contain
        defaultFinancialTransactionFiltering("notes.doesNotContain=" + UPDATED_NOTES, "notes.doesNotContain=" + DEFAULT_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where createdAt equals to
        defaultFinancialTransactionFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where createdAt in
        defaultFinancialTransactionFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where createdAt is not null
        defaultFinancialTransactionFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where updatedAt equals to
        defaultFinancialTransactionFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where updatedAt in
        defaultFinancialTransactionFiltering(
            "updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT,
            "updatedAt.in=" + UPDATED_UPDATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        // Get all the financialTransactionList where updatedAt is not null
        defaultFinancialTransactionFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByAccountIsEqualToSomething() throws Exception {
        FinancialAccount account;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialTransactionRepository.saveAndFlush(financialTransaction);
            account = FinancialAccountResourceIT.createEntity(em);
        } else {
            account = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        em.persist(account);
        em.flush();
        financialTransaction.setAccount(account);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        Long accountId = account.getId();
        // Get all the financialTransactionList where account equals to accountId
        defaultFinancialTransactionShouldBeFound("accountId.equals=" + accountId);

        // Get all the financialTransactionList where account equals to (accountId + 1)
        defaultFinancialTransactionShouldNotBeFound("accountId.equals=" + (accountId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByCategoryIsEqualToSomething() throws Exception {
        Category category;
        if (TestUtil.findAll(em, Category.class).isEmpty()) {
            financialTransactionRepository.saveAndFlush(financialTransaction);
            category = CategoryResourceIT.createEntity(em);
        } else {
            category = TestUtil.findAll(em, Category.class).get(0);
        }
        em.persist(category);
        em.flush();
        financialTransaction.setCategory(category);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        Long categoryId = category.getId();
        // Get all the financialTransactionList where category equals to categoryId
        defaultFinancialTransactionShouldBeFound("categoryId.equals=" + categoryId);

        // Get all the financialTransactionList where category equals to (categoryId + 1)
        defaultFinancialTransactionShouldNotBeFound("categoryId.equals=" + (categoryId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByFinancialSubscriptionIsEqualToSomething() throws Exception {
        FinancialSubscription financialSubscription;
        if (TestUtil.findAll(em, FinancialSubscription.class).isEmpty()) {
            financialTransactionRepository.saveAndFlush(financialTransaction);
            financialSubscription = FinancialSubscriptionResourceIT.createEntity(em);
        } else {
            financialSubscription = TestUtil.findAll(em, FinancialSubscription.class).get(0);
        }
        em.persist(financialSubscription);
        em.flush();
        financialTransaction.setFinancialSubscription(financialSubscription);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        Long financialSubscriptionId = financialSubscription.getId();
        // Get all the financialTransactionList where financialSubscription equals to financialSubscriptionId
        defaultFinancialTransactionShouldBeFound("financialSubscriptionId.equals=" + financialSubscriptionId);

        // Get all the financialTransactionList where financialSubscription equals to (financialSubscriptionId + 1)
        defaultFinancialTransactionShouldNotBeFound("financialSubscriptionId.equals=" + (financialSubscriptionId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTransactionIngestionIsEqualToSomething() throws Exception {
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            financialTransactionRepository.saveAndFlush(financialTransaction);
            transactionIngestion = TransactionIngestionResourceIT.createEntity(em);
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        em.persist(transactionIngestion);
        em.flush();
        financialTransaction.setTransactionIngestion(transactionIngestion);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        Long transactionIngestionId = transactionIngestion.getId();
        // Get all the financialTransactionList where transactionIngestion equals to transactionIngestionId
        defaultFinancialTransactionShouldBeFound("transactionIngestionId.equals=" + transactionIngestionId);

        // Get all the financialTransactionList where transactionIngestion equals to (transactionIngestionId + 1)
        defaultFinancialTransactionShouldNotBeFound("transactionIngestionId.equals=" + (transactionIngestionId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsByTagsIsEqualToSomething() throws Exception {
        Tag tags;
        if (TestUtil.findAll(em, Tag.class).isEmpty()) {
            financialTransactionRepository.saveAndFlush(financialTransaction);
            tags = TagResourceIT.createEntity(em);
        } else {
            tags = TestUtil.findAll(em, Tag.class).get(0);
        }
        em.persist(tags);
        em.flush();
        financialTransaction.addTags(tags);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        Long tagsId = tags.getId();
        // Get all the financialTransactionList where tags equals to tagsId
        defaultFinancialTransactionShouldBeFound("tagsId.equals=" + tagsId);

        // Get all the financialTransactionList where tags equals to (tagsId + 1)
        defaultFinancialTransactionShouldNotBeFound("tagsId.equals=" + (tagsId + 1));
    }

    private void defaultFinancialTransactionFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultFinancialTransactionShouldBeFound(shouldBeFound);
        defaultFinancialTransactionShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultFinancialTransactionShouldBeFound(String filter) throws Exception {
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialTransaction.getId().intValue())))
            .andExpect(jsonPath("$.[*].transactionDate").value(hasItem(DEFAULT_TRANSACTION_DATE.toString())))
            .andExpect(jsonPath("$.[*].postingDate").value(hasItem(DEFAULT_POSTING_DATE.toString())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].flow").value(hasItem(DEFAULT_FLOW.toString())))
            .andExpect(jsonPath("$.[*].origin").value(hasItem(DEFAULT_ORIGIN.toString())))
            .andExpect(jsonPath("$.[*].externalReference").value(hasItem(DEFAULT_EXTERNAL_REFERENCE)))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultFinancialTransactionShouldNotBeFound(String filter) throws Exception {
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingFinancialTransaction() throws Exception {
        // Get the financialTransaction
        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingFinancialTransaction() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialTransaction
        FinancialTransaction updatedFinancialTransaction = financialTransactionRepository
            .findById(financialTransaction.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedFinancialTransaction are not directly saved in db
        em.detach(updatedFinancialTransaction);
        updatedFinancialTransaction
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .postingDate(UPDATED_POSTING_DATE)
            .description(UPDATED_DESCRIPTION)
            .amount(UPDATED_AMOUNT)
            .flow(UPDATED_FLOW)
            .origin(DEFAULT_ORIGIN)
            .externalReference(UPDATED_EXTERNAL_REFERENCE)
            .notes(UPDATED_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(updatedFinancialTransaction);

        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isOk());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FinancialTransaction persistedFinancialTransaction = getPersistedFinancialTransaction(updatedFinancialTransaction);
        updatedFinancialTransaction.setUpdatedAt(persistedFinancialTransaction.getUpdatedAt());
        assertPersistedFinancialTransactionToMatchAllProperties(updatedFinancialTransaction);
    }

    @Test
    @Transactional
    void putNonExistingFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateFinancialTransactionWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialTransaction using partial update
        FinancialTransaction partialUpdatedFinancialTransaction = new FinancialTransaction();
        partialUpdatedFinancialTransaction.setId(financialTransaction.getId());

        partialUpdatedFinancialTransaction
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .postingDate(UPDATED_POSTING_DATE)
            .amount(UPDATED_AMOUNT);
        ObjectNode patchNode = om.createObjectNode();
        patchNode.put("id", partialUpdatedFinancialTransaction.getId());
        patchNode.put("transactionDate", UPDATED_TRANSACTION_DATE.toString());
        patchNode.put("postingDate", UPDATED_POSTING_DATE.toString());
        patchNode.put("amount", UPDATED_AMOUNT);

        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFinancialTransaction.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchNode))
            )
            .andExpect(status().isOk());

        // Validate the FinancialTransaction in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFinancialTransactionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFinancialTransaction, financialTransaction),
            getPersistedFinancialTransaction(financialTransaction)
        );
    }

    @Test
    @Transactional
    void fullUpdateFinancialTransactionWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialTransaction using partial update
        FinancialTransaction partialUpdatedFinancialTransaction = new FinancialTransaction();
        partialUpdatedFinancialTransaction.setId(financialTransaction.getId());

        partialUpdatedFinancialTransaction
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .postingDate(UPDATED_POSTING_DATE)
            .description(UPDATED_DESCRIPTION)
            .amount(UPDATED_AMOUNT)
            .flow(UPDATED_FLOW)
            .externalReference(UPDATED_EXTERNAL_REFERENCE)
            .notes(UPDATED_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        ObjectNode patchNode = om.createObjectNode();
        patchNode.put("id", partialUpdatedFinancialTransaction.getId());
        patchNode.put("transactionDate", UPDATED_TRANSACTION_DATE.toString());
        patchNode.put("postingDate", UPDATED_POSTING_DATE.toString());
        patchNode.put("description", UPDATED_DESCRIPTION);
        patchNode.put("amount", UPDATED_AMOUNT);
        patchNode.put("flow", UPDATED_FLOW.toString());
        patchNode.put("externalReference", UPDATED_EXTERNAL_REFERENCE);
        patchNode.put("notes", UPDATED_NOTES);

        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFinancialTransaction.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchNode))
            )
            .andExpect(status().isOk());

        // Validate the FinancialTransaction in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FinancialTransaction persistedFinancialTransaction = getPersistedFinancialTransaction(partialUpdatedFinancialTransaction);
        partialUpdatedFinancialTransaction.setOrigin(DEFAULT_ORIGIN);
        partialUpdatedFinancialTransaction.setUpdatedAt(persistedFinancialTransaction.getUpdatedAt());
        assertFinancialTransactionUpdatableFieldsEquals(partialUpdatedFinancialTransaction, persistedFinancialTransaction);
    }

    @Test
    @Transactional
    void patchNonExistingFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamFinancialTransaction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialTransaction.setId(longCount.incrementAndGet());

        // Create the FinancialTransaction
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialTransaction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteFinancialTransaction() throws Exception {
        // Initialize the database
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the financialTransaction
        restFinancialTransactionMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialTransaction.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private FinancialTransaction saveTransactionOnOtherUsersAccount() {
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(createOtherUser(em));
        em.persist(otherAccount);
        em.flush();
        FinancialTransaction otherTransaction = createEntity(em);
        otherTransaction.setAccount(otherAccount);
        return financialTransactionRepository.saveAndFlush(otherTransaction);
    }

    @Test
    @Transactional
    void createFinancialTransactionOnInaccessibleAccountFails() throws Exception {
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(createOtherUser(em));
        em.persist(otherAccount);
        em.flush();

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.getAccount().setId(otherAccount.getId());

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialTransactionPreservesPayloadOriginWhenNoIngestionIsSet() throws Exception {
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setOrigin(TransactionOrigin.FILE_IMPORT);

        FinancialTransactionDTO returnedFinancialTransactionDTO = om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );

        assertThat(returnedFinancialTransactionDTO.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
        assertThat(returnedFinancialTransactionDTO.getTransactionIngestion()).isNull();
        insertedFinancialTransaction = financialTransactionMapper.toEntity(returnedFinancialTransactionDTO);
    }

    @Test
    @Transactional
    void createFinancialTransactionWithZeroAmountFails() throws Exception {
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setAmount(BigDecimal.ZERO);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialTransactionTrimsTextFieldsAndConvertsBlankOptionalTextToNull() throws Exception {
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setDescription("  Coffee  ");
        financialTransactionDTO.setExternalReference("   ");
        financialTransactionDTO.setNotes("  Morning cup  ");

        FinancialTransactionDTO returnedFinancialTransactionDTO = om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Coffee"))
                .andExpect(jsonPath("$.externalReference").value(nullValue()))
                .andExpect(jsonPath("$.notes").value("Morning cup"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );

        insertedFinancialTransaction = financialTransactionMapper.toEntity(returnedFinancialTransactionDTO);
    }

    @Test
    @Transactional
    void createFinancialTransactionWithIncompatibleCategoryTypeFails() throws Exception {
        Category incomeCategory = CategoryResourceIT.createEntity(em);
        incomeCategory.setUser(financialTransaction.getAccount().getUser());
        incomeCategory.setCategoryType(CategoryType.INCOME);
        em.persist(incomeCategory);
        em.flush();

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setFlow(TransactionFlow.OUT);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(incomeCategory.getId());
        financialTransactionDTO.setCategory(categoryDTO);

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialTransactionWithoutCategoryAppliesSuggestedCategory() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNotNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(transport.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithExplicitCategoryDoesNotOverride() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Category restaurants = persistCategory("Restaurants", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", restaurants, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNotNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(restaurants.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithoutTagsAddsSuggestedTags() throws Exception {
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Ride share rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactly(rideShare.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithExplicitTagsPreservesThemAndAddsSuggestedTags() throws Exception {
        Tag business = persistTag("Business", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Ride share rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of(business));
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(business.getId(), rideShare.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithAlreadySuggestedExplicitTagDoesNotDuplicate() throws Exception {
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Ride share rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of(rideShare));
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactly(rideShare.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithExplicitDifferentCategoryStillAppliesSuggestedNewTags() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Category restaurants = persistCategory("Restaurants", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule(
            "Transport ride share rule",
            financialTransaction.getAccount().getUser(),
            transport,
            Set.of(rideShare),
            true,
            "Uber"
        );

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", restaurants, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNotNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(restaurants.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactly(rideShare.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithNoMatchingRulesLeavesCategoryAndTagsUnchanged() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Non matching rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Lyft");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionDoesNotApplyInactiveRule() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Inactive rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), false, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionDoesNotApplyRuleFromAnotherUser() throws Exception {
        User otherUser = createOtherUser(em);
        Category foreignCategory = persistCategory("Foreign transport", otherUser);
        Tag foreignTag = persistTag("Foreign ride share", otherUser);
        persistMatchingRule("Foreign rule", otherUser, foreignCategory, Set.of(foreignTag), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCreateOnAnotherUsersAccountIsRejectedAndDoesNotApplyRules() throws Exception {
        User accountOwner = financialTransaction.getAccount().getUser();
        Category transport = persistCategory("Transport", accountOwner);
        persistMatchingRule("Owner rule", accountOwner, transport, Set.of(), true, "Uber");

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setDescription("Uber ride");

        restFinancialTransactionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateFinancialTransactionDoesNotApplyRules() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Uber");
        financialTransaction.setDescription("Initial unmatched");
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(insertedFinancialTransaction);
        financialTransactionDTO.setDescription("Uber ride");

        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isOk());

        FinancialTransaction persisted = financialTransactionRepository
            .findOneWithEagerRelationships(insertedFinancialTransaction.getId())
            .orElseThrow();
        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void patchFinancialTransactionDoesNotApplyRules() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Uber");
        financialTransaction.setDescription("Initial unmatched");
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        ObjectNode patchNode = om.createObjectNode();
        patchNode.put("id", insertedFinancialTransaction.getId());
        patchNode.put("description", "Uber ride");

        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, insertedFinancialTransaction.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchNode))
            )
            .andExpect(status().isOk());

        FinancialTransaction persisted = financialTransactionRepository
            .findOneWithEagerRelationships(insertedFinancialTransaction.getId())
            .orElseThrow();
        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void createFinancialTransactionAppliesCategoryAndMultipleTagsOnlyOnce() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        Tag business = persistTag("Business", financialTransaction.getAccount().getUser());
        persistMatchingRule(
            "Transport tags rule",
            financialTransaction.getAccount().getUser(),
            transport,
            Set.of(rideShare, business),
            true,
            "Uber"
        );

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", null, Set.of(rideShare));
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNotNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(transport.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(rideShare.getId(), business.getId());
        assertThat(persisted.getTags()).hasSize(2);
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void createFinancialTransactionWithSameExplicitCategoryKeepsCategoryAndAppliesTags() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Uber");

        FinancialTransactionDTO result = createTransactionFromDraft("Uber ride", transport, Set.of());
        FinancialTransaction persisted = financialTransactionRepository.findOneWithEagerRelationships(result.getId()).orElseThrow();

        assertThat(persisted.getCategory()).isNotNull();
        assertThat(persisted.getCategory().getId()).isEqualTo(transport.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactly(rideShare.getId());
        insertedFinancialTransaction = persisted;
    }

    @Test
    @Transactional
    void previewRulesWithoutCategoryReturnsSuggestedCategoryAndDoesNotSaveTransaction() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(), true, "Uber");
        long databaseSizeBeforePreview = getRepositoryCount();

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory.categoryId").value(transport.getId().intValue()))
            .andExpect(jsonPath("$.suggestedCategory.categoryName").value("Transport"))
            .andExpect(jsonPath("$.suggestedCategory.sourceRuleName").value("Transport rule"))
            .andExpect(jsonPath("$.suggestedCategory.conflictsWithCurrentValue").value(false))
            .andExpect(jsonPath("$.matchedRules.[0].ruleName").value("Transport rule"))
            .andExpect(jsonPath("$.hasSuggestions").value(true))
            .andExpect(jsonPath("$.hasConflicts").value(false))
            .andExpect(jsonPath("$.suggestedCategory.user").doesNotExist())
            .andExpect(jsonPath("$.matchedRules.[0].user").doesNotExist());

        assertSameRepositoryCount(databaseSizeBeforePreview);
    }

    @Test
    @Transactional
    void previewRulesWithExplicitDifferentCategoryReturnsConflictAndDoesNotApply() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Category restaurants = persistCategory("Restaurants", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(), true, "Uber");

        ObjectNode request = previewRequest("Uber ride");
        request.put("categoryId", restaurants.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory.categoryId").value(transport.getId().intValue()))
            .andExpect(jsonPath("$.suggestedCategory.conflictsWithCurrentValue").value(true))
            .andExpect(jsonPath("$.suggestedCategory.currentCategoryId").value(restaurants.getId().intValue()))
            .andExpect(jsonPath("$.suggestedCategory.currentCategoryName").value("Restaurants"))
            .andExpect(jsonPath("$.conflicts.[0].field").value("CATEGORY"))
            .andExpect(jsonPath("$.conflicts.[0].currentValueId").value(restaurants.getId().intValue()))
            .andExpect(jsonPath("$.conflicts.[0].suggestedValueId").value(transport.getId().intValue()))
            .andExpect(jsonPath("$.hasConflicts").value(true));
    }

    @Test
    @Transactional
    void previewRulesWithSameExplicitCategoryDoesNotProduceConflict() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(), true, "Uber");

        ObjectNode request = previewRequest("Uber ride");
        request.put("categoryId", transport.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory.categoryId").value(transport.getId().intValue()))
            .andExpect(jsonPath("$.suggestedCategory.conflictsWithCurrentValue").value(false))
            .andExpect(jsonPath("$.conflicts.length()").value(0))
            .andExpect(jsonPath("$.hasConflicts").value(false));
    }

    @Test
    @Transactional
    void previewRulesWithoutTagsReturnsSuggestedTags() throws Exception {
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Ride share rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedTags.length()").value(1))
            .andExpect(jsonPath("$.suggestedTags.[0].tagId").value(rideShare.getId().intValue()))
            .andExpect(jsonPath("$.suggestedTags.[0].tagName").value("Ride share"))
            .andExpect(jsonPath("$.suggestedTags.[0].alreadyPresent").value(false))
            .andExpect(jsonPath("$.suggestedTags.[0].duplicateOfEarlierSuggestion").value(false))
            .andExpect(jsonPath("$.hasSuggestions").value(true));
    }

    @Test
    @Transactional
    void previewRulesWithExplicitTagMarksAlreadyPresent() throws Exception {
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Ride share rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        ObjectNode request = previewRequest("Uber ride");
        request.putArray("tagIds").add(rideShare.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedTags.[0].tagId").value(rideShare.getId().intValue()))
            .andExpect(jsonPath("$.suggestedTags.[0].alreadyPresent").value(true))
            .andExpect(jsonPath("$.skippedOutputs.[0].reason").value("TAG_ALREADY_PRESENT"));
    }

    @Test
    @Transactional
    void previewRulesWithExplicitTagPlusNewSuggestedTagReturnsBothStates() throws Exception {
        Tag business = persistTag("Business", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule(
            "Business ride rule",
            financialTransaction.getAccount().getUser(),
            null,
            Set.of(business, rideShare),
            true,
            "Uber"
        );

        ObjectNode request = previewRequest("Uber ride");
        request.putArray("tagIds").add(business.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedTags.length()").value(2))
            .andExpect(jsonPath("$.suggestedTags.[?(@.tagName == 'Business')].alreadyPresent").value(hasItem(true)))
            .andExpect(jsonPath("$.suggestedTags.[?(@.tagName == 'Ride share')].alreadyPresent").value(hasItem(false)));
    }

    @Test
    @Transactional
    void previewRulesWithDuplicateTagSuggestionsReportsSkip() throws Exception {
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("First ride rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");
        persistMatchingRule("Second ride rule", financialTransaction.getAccount().getUser(), null, Set.of(rideShare), true, "Uber");

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedTags.length()").value(1))
            .andExpect(
                jsonPath("$.skippedOutputs.[?(@.reason == 'TAG_ALREADY_SUGGESTED')].valueId").value(hasItem(rideShare.getId().intValue()))
            );
    }

    @Test
    @Transactional
    void previewRulesWithNoMatchingRulesReturnsEmptyResult() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Non matching rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Lyft");

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory").value(nullValue()))
            .andExpect(jsonPath("$.suggestedTags.length()").value(0))
            .andExpect(jsonPath("$.conflicts.length()").value(0))
            .andExpect(jsonPath("$.skippedOutputs.length()").value(0))
            .andExpect(jsonPath("$.matchedRules.length()").value(0))
            .andExpect(jsonPath("$.hasSuggestions").value(false))
            .andExpect(jsonPath("$.hasConflicts").value(false));
    }

    @Test
    @Transactional
    void previewRulesIgnoresInactiveRules() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        persistMatchingRule("Inactive rule", financialTransaction.getAccount().getUser(), transport, Set.of(), false, "Uber");

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory").value(nullValue()))
            .andExpect(jsonPath("$.hasSuggestions").value(false));
    }

    @Test
    @Transactional
    void previewRulesIgnoresRulesFromAnotherUser() throws Exception {
        User otherUser = createOtherUser(em);
        Category foreignCategory = persistCategory("Foreign transport", otherUser);
        Tag foreignTag = persistTag("Foreign ride share", otherUser);
        persistMatchingRule("Foreign rule", otherUser, foreignCategory, Set.of(foreignTag), true, "Uber");

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory").value(nullValue()))
            .andExpect(jsonPath("$.suggestedTags.length()").value(0));
    }

    @Test
    @Transactional
    void previewRulesRejectsForeignAccount() throws Exception {
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(createOtherUser(em));
        em.persist(otherAccount);
        em.flush();

        ObjectNode request = previewRequest("Uber ride");
        request.put("accountId", otherAccount.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminPreviewForAnotherUsersAccountIsRejected() throws Exception {
        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void previewRulesRejectsForeignCategory() throws Exception {
        Category foreignCategory = persistCategory("Foreign category", createOtherUser(em));
        ObjectNode request = previewRequest("Uber ride");
        request.put("categoryId", foreignCategory.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void previewRulesRejectsForeignTag() throws Exception {
        Tag foreignTag = persistTag("Foreign tag", createOtherUser(em));
        ObjectNode request = previewRequest("Uber ride");
        request.putArray("tagIds").add(foreignTag.getId());

        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void previewRulesValidatesRequiredFields() throws Exception {
        ObjectNode request = previewRequest("Uber ride");
        request.remove("accountId");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());

        request = previewRequest("   ");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());

        request = previewRequest("Uber ride");
        request.put("amount", "0.00");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());

        request = previewRequest("Uber ride");
        request.remove("flow");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());

        request = previewRequest("Uber ride");
        request.remove("origin");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());

        request = previewRequest("Uber ride");
        request.remove("transactionDate");
        restFinancialTransactionMockMvc
            .perform(post(RULE_PREVIEW_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void previewRulesDoesNotApplyFillEmptyOnlyOrPersistTransaction() throws Exception {
        Category transport = persistCategory("Transport", financialTransaction.getAccount().getUser());
        Tag rideShare = persistTag("Ride share", financialTransaction.getAccount().getUser());
        persistMatchingRule("Transport rule", financialTransaction.getAccount().getUser(), transport, Set.of(rideShare), true, "Uber");
        long databaseSizeBeforePreview = getRepositoryCount();

        restFinancialTransactionMockMvc
            .perform(
                post(RULE_PREVIEW_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory.categoryId").value(transport.getId().intValue()))
            .andExpect(jsonPath("$.suggestedTags.[0].tagId").value(rideShare.getId().intValue()));

        assertSameRepositoryCount(databaseSizeBeforePreview);
        assertThat(financialTransactionRepository.findAll()).noneMatch(transaction -> "Uber ride".equals(transaction.getDescription()));
    }

    @Test
    @Transactional
    void existingTransactionRulePreviewEndpointIsNotImplemented() throws Exception {
        restFinancialTransactionMockMvc
            .perform(
                post(ENTITY_API_URL + "/1/rule-preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(previewRequest("Uber ride")))
            )
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    void patchFinancialTransactionAmountFailsWhenLinkedToInternalTransfer() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        FinancialTransaction oppositeTransaction = createEntity(em);
        oppositeTransaction.setAccount(financialTransaction.getAccount());
        oppositeTransaction.setFlow(TransactionFlow.OUT);
        em.persist(oppositeTransaction);
        em.persist(
            new InternalTransfer()
                .createdAt(DEFAULT_CREATED_AT)
                .outgoingTransaction(financialTransaction)
                .incomingTransaction(oppositeTransaction)
        );
        em.flush();

        ObjectNode patchNode = om.createObjectNode();
        patchNode.put("id", financialTransaction.getId());
        patchNode.put("amount", UPDATED_AMOUNT);

        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialTransaction.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchNode))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFinancialTransactionLinkedToIngestionRecordRejectsAndUnlinksRecord() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        IngestionRecord ingestionRecord = IngestionRecordResourceIT.createEntity(em);
        ingestionRecord.setStatus(IngestionRecordStatus.IMPORTED);
        ingestionRecord.setFinancialTransaction(financialTransaction);
        em.persist(ingestionRecord);
        em.flush();
        Long ingestionRecordId = ingestionRecord.getId();

        restFinancialTransactionMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialTransaction.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        em.flush();
        em.clear();
        IngestionRecord persistedRecord = em.find(IngestionRecord.class, ingestionRecordId);
        assertThat(financialTransactionRepository.findById(financialTransaction.getId())).isEmpty();
        assertThat(persistedRecord.getFinancialTransaction()).isNull();
        assertThat(persistedRecord.getStatus()).isEqualTo(IngestionRecordStatus.REJECTED);
        assertThat(persistedRecord.getErrorCode()).isEqualTo("FINANCIAL_TRANSACTION_DELETED");
        assertThat(persistedRecord.getErrorMessage()).isEqualTo("Financial transaction was deleted manually.");
        insertedFinancialTransaction = null;
    }

    @Test
    @Transactional
    void getFinancialTransactionOnAnotherUsersAccountIsNotFound() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL_ID, otherTransaction.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllFinancialTransactionsDoesNotIncludeAnotherUsersTransactions() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherTransaction.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetFinancialTransactionOnAnotherUsersAccount() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherTransaction.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherTransaction.getId().intValue()));
    }

    @Test
    @Transactional
    void putFinancialTransactionOnAnotherUsersAccountIsNotFound() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(otherTransaction);
        financialTransactionDTO.setDescription(UPDATED_DESCRIPTION);

        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFinancialTransactionOnAnotherUsersAccountIsNotFound() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        FinancialTransactionDTO financialTransactionDTO = new FinancialTransactionDTO();
        financialTransactionDTO.setId(otherTransaction.getId());
        financialTransactionDTO.setDescription(UPDATED_DESCRIPTION);

        restFinancialTransactionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFinancialTransactionOnAnotherUsersAccountIsNotFound() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherTransaction.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(financialTransactionRepository.existsById(otherTransaction.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateFinancialTransactionCannotChangeOrigin() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setOrigin(TransactionOrigin.FILE_IMPORT);
        financialTransactionDTO.setDescription(UPDATED_DESCRIPTION);

        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isBadRequest());

        FinancialTransaction persistedFinancialTransaction = financialTransactionRepository
            .findById(financialTransaction.getId())
            .orElseThrow();
        assertThat(persistedFinancialTransaction.getOrigin()).isEqualTo(DEFAULT_ORIGIN);
        assertThat(persistedFinancialTransaction.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
    }

    @Test
    @Transactional
    void getFinancialTransactionCountDoesNotIncludeAnotherUsersTransactions() throws Exception {
        saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllFinancialTransactionsIncludingOtherUsers() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialTransaction.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherTransaction.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllFinancialTransactionsIncludingOtherUsers() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        saveTransactionOnOtherUsersAccount();

        restFinancialTransactionMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateFinancialTransactionOwnedByAnotherUser() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(otherTransaction);
        financialTransactionDTO.setDescription(UPDATED_DESCRIPTION);

        restFinancialTransactionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialTransactionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialTransactionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(UPDATED_DESCRIPTION));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteFinancialTransactionOwnedByAnotherUser() throws Exception {
        FinancialTransaction otherTransaction = saveTransactionOnOtherUsersAccount();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restFinancialTransactionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherTransaction.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void findAllWhereIngestionRecordIsNullIsScopedAndUnlinkedOnly() throws Exception {
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);

        FinancialTransaction otherUnlinked = saveTransactionOnOtherUsersAccount();

        FinancialTransaction linkedTransaction = createEntity(em);
        linkedTransaction.setAccount(financialTransaction.getAccount());
        linkedTransaction = financialTransactionRepository.saveAndFlush(linkedTransaction);

        IngestionRecord ingestionRecord = IngestionRecordResourceIT.createEntity(em);
        ingestionRecord.setFinancialTransaction(linkedTransaction);
        ingestionRecord.setRecordIndex((int) (Math.floorMod(longCount.incrementAndGet(), 100000)));
        em.persist(ingestionRecord);
        em.flush();

        assertThat(financialTransactionService.findAllWhereIngestionRecordIsNull())
            .extracting(FinancialTransactionDTO::getId)
            .contains(financialTransaction.getId())
            .doesNotContain(otherUnlinked.getId(), linkedTransaction.getId());

        restFinancialTransactionMockMvc
            .perform(get(ENTITY_API_URL + "/ingestion-record-is-null"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(hasItem(financialTransaction.getId().intValue())))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(otherUnlinked.getId().intValue()))))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(linkedTransaction.getId().intValue()))));
    }

    @Test
    @Transactional
    void getOutgoingInternalTransferCandidatesDoesNotIncludeAnotherUsersTransactions() throws Exception {
        FinancialTransaction otherOutgoing = saveTransactionOnOtherUsersAccount();
        otherOutgoing.setFlow(TransactionFlow.OUT);
        otherOutgoing.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(otherOutgoing);

        FinancialTransaction eligibleOutgoing = createEntity(em);
        eligibleOutgoing.setFlow(TransactionFlow.OUT);
        eligibleOutgoing.setOrigin(TransactionOrigin.MANUAL);
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(eligibleOutgoing);

        restFinancialTransactionMockMvc
            .perform(get(OUTGOING_INTERNAL_TRANSFER_CANDIDATES_API_URL))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eligibleOutgoing.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherOutgoing.getId().intValue()))));
    }

    @Test
    @Transactional
    void getOutgoingInternalTransferCandidatesIncludeOnlyOutUnlinked() throws Exception {
        FinancialAccount account = financialTransaction.getAccount();

        FinancialTransaction eligibleOutgoing = createEntity(em);
        eligibleOutgoing.setAccount(account);
        eligibleOutgoing.setFlow(TransactionFlow.OUT);
        eligibleOutgoing.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(eligibleOutgoing);

        FinancialTransaction inFlowTransaction = createEntity(em);
        inFlowTransaction.setAccount(account);
        inFlowTransaction.setFlow(TransactionFlow.IN);
        inFlowTransaction.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(inFlowTransaction);

        FinancialTransaction importedOutgoing = createEntity(em);
        importedOutgoing.setAccount(account);
        importedOutgoing.setFlow(TransactionFlow.OUT);
        importedOutgoing.setOrigin(TransactionOrigin.FILE_IMPORT);
        financialTransactionRepository.saveAndFlush(importedOutgoing);

        InternalTransfer linkedTransfer = InternalTransferResourceIT.createEntity(em);
        Long linkedOutgoingId = linkedTransfer.getOutgoingTransaction().getId();
        em.persist(linkedTransfer);
        em.flush();
        em.clear();

        restFinancialTransactionMockMvc
            .perform(get(OUTGOING_INTERNAL_TRANSFER_CANDIDATES_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(eligibleOutgoing.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(importedOutgoing.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(inFlowTransaction.getId().intValue()))))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(linkedOutgoingId.intValue()))));
    }

    @Test
    @Transactional
    void getIncomingInternalTransferCandidatesDoesNotIncludeAnotherUsersTransactions() throws Exception {
        FinancialTransaction otherIncoming = saveTransactionOnOtherUsersAccount();
        otherIncoming.setFlow(TransactionFlow.IN);
        otherIncoming.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(otherIncoming);

        FinancialTransaction eligibleIncoming = createEntity(em);
        eligibleIncoming.setFlow(TransactionFlow.IN);
        eligibleIncoming.setOrigin(TransactionOrigin.MANUAL);
        insertedFinancialTransaction = financialTransactionRepository.saveAndFlush(eligibleIncoming);

        restFinancialTransactionMockMvc
            .perform(get(INCOMING_INTERNAL_TRANSFER_CANDIDATES_API_URL))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eligibleIncoming.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherIncoming.getId().intValue()))));
    }

    @Test
    @Transactional
    void getIncomingInternalTransferCandidatesIncludeOnlyInUnlinked() throws Exception {
        FinancialAccount account = financialTransaction.getAccount();

        FinancialTransaction eligibleIncoming = createEntity(em);
        eligibleIncoming.setAccount(account);
        eligibleIncoming.setFlow(TransactionFlow.IN);
        eligibleIncoming.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(eligibleIncoming);

        FinancialTransaction outFlowTransaction = createEntity(em);
        outFlowTransaction.setAccount(account);
        outFlowTransaction.setFlow(TransactionFlow.OUT);
        outFlowTransaction.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(outFlowTransaction);

        FinancialTransaction importedIncoming = createEntity(em);
        importedIncoming.setAccount(account);
        importedIncoming.setFlow(TransactionFlow.IN);
        importedIncoming.setOrigin(TransactionOrigin.FILE_IMPORT);
        financialTransactionRepository.saveAndFlush(importedIncoming);

        InternalTransfer linkedTransfer = InternalTransferResourceIT.createEntity(em);
        Long linkedIncomingId = linkedTransfer.getIncomingTransaction().getId();
        em.persist(linkedTransfer);
        em.flush();
        em.clear();

        restFinancialTransactionMockMvc
            .perform(get(INCOMING_INTERNAL_TRANSFER_CANDIDATES_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(eligibleIncoming.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(importedIncoming.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(outFlowTransaction.getId().intValue()))))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(linkedIncomingId.intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminOutgoingInternalTransferCandidatesCanIncludeAnotherUsersTransactions() throws Exception {
        FinancialTransaction otherOutgoing = saveTransactionOnOtherUsersAccount();
        otherOutgoing.setFlow(TransactionFlow.OUT);
        otherOutgoing.setOrigin(TransactionOrigin.MANUAL);
        financialTransactionRepository.saveAndFlush(otherOutgoing);

        restFinancialTransactionMockMvc
            .perform(get(OUTGOING_INTERNAL_TRANSFER_CANDIDATES_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherOutgoing.getId().intValue())));
    }

    private FinancialTransactionDTO createTransactionFromDraft(String description, Category category, Set<Tag> tags) throws Exception {
        FinancialTransactionDTO financialTransactionDTO = financialTransactionMapper.toDto(financialTransaction);
        financialTransactionDTO.setId(null);
        financialTransactionDTO.setDescription(description);
        financialTransactionDTO.setCategory(category == null ? null : categoryDTO(category));
        financialTransactionDTO.setTags(tags.stream().map(this::tagDTO).collect(java.util.stream.Collectors.toSet()));

        return om.readValue(
            restFinancialTransactionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialTransactionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialTransactionDTO.class
        );
    }

    private ObjectNode previewRequest(String description) {
        ObjectNode request = om.createObjectNode();
        request.put("accountId", financialTransaction.getAccount().getId());
        request.put("description", description);
        request.put("amount", "1.00");
        request.put("flow", TransactionFlow.OUT.name());
        request.put("origin", TransactionOrigin.MANUAL.name());
        request.put("transactionDate", DEFAULT_TRANSACTION_DATE.toString());
        request.put("postingDate", DEFAULT_POSTING_DATE.toString());
        request.put("externalReference", "preview-ref");
        request.putArray("tagIds");
        return request;
    }

    private Category persistCategory(String name, User user) {
        Category category = CategoryResourceIT.createEntity(em);
        category.setName(name);
        category.setCategoryType(CategoryType.BOTH);
        category.setUser(user);
        em.persist(category);
        em.flush();
        return category;
    }

    private Tag persistTag(String name, User user) {
        Tag tag = TagResourceIT.createEntity(em);
        tag.setName(name);
        tag.setUser(user);
        em.persist(tag);
        em.flush();
        return tag;
    }

    private TransactionRule persistMatchingRule(
        String name,
        User user,
        Category resultingCategory,
        Set<Tag> resultingTags,
        boolean active,
        String descriptionValue
    ) {
        TransactionRule rule = new TransactionRule()
            .name(name)
            .priority(0)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(active)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT)
            .user(user)
            .resultingCategory(resultingCategory);
        resultingTags.forEach(rule::addResultingTags);
        em.persist(rule);
        em.flush();

        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(TransactionRuleField.DESCRIPTION)
            .operator(RuleOperator.CONTAINS)
            .value(descriptionValue)
            .caseSensitive(false)
            .position(0)
            .transactionRule(rule);
        rule.addConditions(condition);
        em.persist(condition);
        em.flush();
        return rule;
    }

    private CategoryDTO categoryDTO(Category category) {
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(category.getId());
        return categoryDTO;
    }

    private TagDTO tagDTO(Tag tag) {
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(tag.getId());
        return tagDTO;
    }

    protected long getRepositoryCount() {
        return financialTransactionRepository.count();
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

    protected FinancialTransaction getPersistedFinancialTransaction(FinancialTransaction financialTransaction) {
        return financialTransactionRepository.findById(financialTransaction.getId()).orElseThrow();
    }

    protected void assertPersistedFinancialTransactionToMatchAllProperties(FinancialTransaction expectedFinancialTransaction) {
        assertFinancialTransactionAllPropertiesEquals(
            expectedFinancialTransaction,
            getPersistedFinancialTransaction(expectedFinancialTransaction)
        );
    }

    protected void assertPersistedFinancialTransactionToMatchUpdatableProperties(FinancialTransaction expectedFinancialTransaction) {
        assertFinancialTransactionAllUpdatablePropertiesEquals(
            expectedFinancialTransaction,
            getPersistedFinancialTransaction(expectedFinancialTransaction)
        );
    }
}
