package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.FinancialAccountAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.FinancialAccountService;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link FinancialAccountResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class FinancialAccountResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_INSTITUTION_NAME = "AAAAAAAAAA";
    private static final String UPDATED_INSTITUTION_NAME = "BBBBBBBBBB";

    private static final AccountType DEFAULT_ACCOUNT_TYPE = AccountType.DEBIT;
    private static final AccountType UPDATED_ACCOUNT_TYPE = AccountType.CASH;

    private static final CurrencyCode DEFAULT_CURRENCY = CurrencyCode.MXN;
    private static final CurrencyCode UPDATED_CURRENCY = CurrencyCode.USD;

    private static final BigDecimal DEFAULT_INITIAL_BALANCE = new BigDecimal(1);
    private static final BigDecimal UPDATED_INITIAL_BALANCE = new BigDecimal(2);
    private static final BigDecimal SMALLER_INITIAL_BALANCE = new BigDecimal(1 - 1);

    private static final LocalDate DEFAULT_INITIAL_BALANCE_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_INITIAL_BALANCE_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_INITIAL_BALANCE_DATE = LocalDate.ofEpochDay(-1L);

    private static final String DEFAULT_LAST_FOUR_DIGITS = "1189";
    private static final String UPDATED_LAST_FOUR_DIGITS = "9361";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_COLOR = "#50484F";
    private static final String UPDATED_COLOR = "#FfaE2b";

    private static final String DEFAULT_ICON = "AAAAAAAAAA";
    private static final String UPDATED_ICON = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/financial-accounts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepositoryMock;

    @Autowired
    private FinancialAccountMapper financialAccountMapper;

    @Mock
    private FinancialAccountService financialAccountServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restFinancialAccountMockMvc;

    private FinancialAccount financialAccount;

    private FinancialAccount insertedFinancialAccount;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialAccount createEntity(EntityManager em) {
        FinancialAccount financialAccount = new FinancialAccount()
            .name(DEFAULT_NAME)
            .institutionName(DEFAULT_INSTITUTION_NAME)
            .accountType(DEFAULT_ACCOUNT_TYPE)
            .currency(DEFAULT_CURRENCY)
            .initialBalance(DEFAULT_INITIAL_BALANCE)
            .initialBalanceDate(DEFAULT_INITIAL_BALANCE_DATE)
            .lastFourDigits(DEFAULT_LAST_FOUR_DIGITS)
            .description(DEFAULT_DESCRIPTION)
            .color(DEFAULT_COLOR)
            .icon(DEFAULT_ICON)
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        financialAccount.setUser(getCurrentMockUser(em));
        return financialAccount;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialAccount createUpdatedEntity(EntityManager em) {
        FinancialAccount updatedFinancialAccount = new FinancialAccount()
            .name(UPDATED_NAME)
            .institutionName(UPDATED_INSTITUTION_NAME)
            .accountType(UPDATED_ACCOUNT_TYPE)
            .currency(UPDATED_CURRENCY)
            .initialBalance(UPDATED_INITIAL_BALANCE)
            .initialBalanceDate(UPDATED_INITIAL_BALANCE_DATE)
            .lastFourDigits(UPDATED_LAST_FOUR_DIGITS)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        updatedFinancialAccount.setUser(getCurrentMockUser(em));
        return updatedFinancialAccount;
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

    private FinancialAccount createAccount(String name) {
        FinancialAccount account = createEntity(em);
        account.setName(name);
        account = financialAccountRepository.saveAndFlush(account);
        return account;
    }

    private FinancialTransaction createTransaction(FinancialAccount account, LocalDate transactionDate) {
        FinancialTransaction transaction = new FinancialTransaction()
            .transactionDate(transactionDate)
            .description("Test transaction")
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT)
            .account(account);
        em.persist(transaction);
        em.flush();
        return transaction;
    }

    private TransactionIngestion createTransactionIngestion(FinancialAccount account, IngestionType ingestionType) {
        TransactionIngestion ingestion = new TransactionIngestion()
            .ingestionType(ingestionType)
            .status(IngestionStatus.PENDING)
            .sourceLabel("source")
            .startedAt(DEFAULT_CREATED_AT)
            .recordsReceived(1)
            .recordsCreated(1)
            .recordsSkipped(0)
            .recordsRejected(0)
            .createdAt(DEFAULT_CREATED_AT)
            .account(account);
        em.persist(ingestion);
        em.flush();
        return ingestion;
    }

    private FileIngestion createFileIngestion(TransactionIngestion ingestion) {
        FileIngestion fileIngestion = new FileIngestion()
            .originalFilename("statement.csv")
            .fileType(ImportFileType.CSV)
            .createdAt(DEFAULT_CREATED_AT)
            .transactionIngestion(ingestion);
        em.persist(fileIngestion);
        em.flush();
        return fileIngestion;
    }

    private ApiIngestion createApiIngestion(TransactionIngestion ingestion) {
        ApiIngestion apiIngestion = new ApiIngestion()
            .requestId("request-" + ingestion.getId())
            .apiVersion("v1")
            .endpoint("/transactions")
            .receivedAt(DEFAULT_CREATED_AT)
            .createdAt(DEFAULT_CREATED_AT)
            .apiTokenIdSnapshot(1000L + ingestion.getId())
            .apiTokenPrefixSnapshot("ftk_test")
            .apiTokenNameSnapshot("Test token")
            .transactionIngestion(ingestion);
        em.persist(apiIngestion);
        em.flush();
        return apiIngestion;
    }

    private IngestionRecord createIngestionRecord(TransactionIngestion ingestion, FinancialTransaction transaction) {
        IngestionRecord record = new IngestionRecord()
            .recordIndex(0)
            .externalRecordId("record-" + ingestion.getId())
            .status(IngestionRecordStatus.CREATED)
            .rawData("{}")
            .createdAt(DEFAULT_CREATED_AT)
            .transactionIngestion(ingestion)
            .financialTransaction(transaction);
        em.persist(record);
        em.flush();
        return record;
    }

    private InternalTransfer createInternalTransfer(FinancialTransaction outgoingTransaction, FinancialTransaction incomingTransaction) {
        InternalTransfer transfer = new InternalTransfer()
            .notes("transfer")
            .createdAt(DEFAULT_CREATED_AT)
            .outgoingTransaction(outgoingTransaction)
            .incomingTransaction(incomingTransaction);
        em.persist(transfer);
        em.flush();
        return transfer;
    }

    @BeforeEach
    void initTest() {
        financialAccount = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedFinancialAccount != null) {
            financialAccountRepository.delete(insertedFinancialAccount);
            insertedFinancialAccount = null;
        }
    }

    @Test
    @Transactional
    void createFinancialAccount() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        var returnedFinancialAccountDTO = om.readValue(
            restFinancialAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialAccountDTO.class
        );

        // Validate the FinancialAccount in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedFinancialAccount = financialAccountMapper.toEntity(returnedFinancialAccountDTO);
        assertFinancialAccountUpdatableFieldsEquals(returnedFinancialAccount, getPersistedFinancialAccount(returnedFinancialAccount));

        insertedFinancialAccount = returnedFinancialAccount;
    }

    @Test
    @Transactional
    void createFinancialAccountWithExistingId() throws Exception {
        // Create the FinancialAccount with an existing ID
        financialAccount.setId(1L);
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setName(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAccountTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setAccountType(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setCurrency(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkInitialBalanceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setInitialBalance(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkInitialBalanceDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setInitialBalanceDate(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setActive(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setCreatedAt(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialAccount.setUpdatedAt(null);

        // Create the FinancialAccount, which fails.
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        restFinancialAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllFinancialAccounts() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList
        restFinancialAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].institutionName").value(hasItem(DEFAULT_INSTITUTION_NAME)))
            .andExpect(jsonPath("$.[*].accountType").value(hasItem(DEFAULT_ACCOUNT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].initialBalance").value(hasItem(sameNumber(DEFAULT_INITIAL_BALANCE))))
            .andExpect(jsonPath("$.[*].initialBalanceDate").value(hasItem(DEFAULT_INITIAL_BALANCE_DATE.toString())))
            .andExpect(jsonPath("$.[*].lastFourDigits").value(hasItem(DEFAULT_LAST_FOUR_DIGITS)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].icon").value(hasItem(DEFAULT_ICON)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialAccountsWithEagerRelationshipsIsEnabled() throws Exception {
        when(financialAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(financialAccountServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialAccountsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(financialAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(financialAccountRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getFinancialAccount() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get the financialAccount
        restFinancialAccountMockMvc
            .perform(get(ENTITY_API_URL_ID, financialAccount.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(financialAccount.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.institutionName").value(DEFAULT_INSTITUTION_NAME))
            .andExpect(jsonPath("$.accountType").value(DEFAULT_ACCOUNT_TYPE.toString()))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.initialBalance").value(sameNumber(DEFAULT_INITIAL_BALANCE)))
            .andExpect(jsonPath("$.initialBalanceDate").value(DEFAULT_INITIAL_BALANCE_DATE.toString()))
            .andExpect(jsonPath("$.lastFourDigits").value(DEFAULT_LAST_FOUR_DIGITS))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.color").value(DEFAULT_COLOR))
            .andExpect(jsonPath("$.icon").value(DEFAULT_ICON))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getFinancialAccountsByIdFiltering() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        Long id = financialAccount.getId();

        defaultFinancialAccountFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultFinancialAccountFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultFinancialAccountFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where name equals to
        defaultFinancialAccountFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where name in
        defaultFinancialAccountFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where name is not null
        defaultFinancialAccountFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where name contains
        defaultFinancialAccountFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where name does not contain
        defaultFinancialAccountFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInstitutionNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where institutionName equals to
        defaultFinancialAccountFiltering(
            "institutionName.equals=" + DEFAULT_INSTITUTION_NAME,
            "institutionName.equals=" + UPDATED_INSTITUTION_NAME
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInstitutionNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where institutionName in
        defaultFinancialAccountFiltering(
            "institutionName.in=" + DEFAULT_INSTITUTION_NAME + "," + UPDATED_INSTITUTION_NAME,
            "institutionName.in=" + UPDATED_INSTITUTION_NAME
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInstitutionNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where institutionName is not null
        defaultFinancialAccountFiltering("institutionName.specified=true", "institutionName.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInstitutionNameContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where institutionName contains
        defaultFinancialAccountFiltering(
            "institutionName.contains=" + DEFAULT_INSTITUTION_NAME,
            "institutionName.contains=" + UPDATED_INSTITUTION_NAME
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInstitutionNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where institutionName does not contain
        defaultFinancialAccountFiltering(
            "institutionName.doesNotContain=" + UPDATED_INSTITUTION_NAME,
            "institutionName.doesNotContain=" + DEFAULT_INSTITUTION_NAME
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByAccountTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where accountType equals to
        defaultFinancialAccountFiltering("accountType.equals=" + DEFAULT_ACCOUNT_TYPE, "accountType.equals=" + UPDATED_ACCOUNT_TYPE);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByAccountTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where accountType in
        defaultFinancialAccountFiltering(
            "accountType.in=" + DEFAULT_ACCOUNT_TYPE + "," + UPDATED_ACCOUNT_TYPE,
            "accountType.in=" + UPDATED_ACCOUNT_TYPE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByAccountTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where accountType is not null
        defaultFinancialAccountFiltering("accountType.specified=true", "accountType.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where currency equals to
        defaultFinancialAccountFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where currency in
        defaultFinancialAccountFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where currency is not null
        defaultFinancialAccountFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance equals to
        defaultFinancialAccountFiltering(
            "initialBalance.equals=" + DEFAULT_INITIAL_BALANCE,
            "initialBalance.equals=" + UPDATED_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance in
        defaultFinancialAccountFiltering(
            "initialBalance.in=" + DEFAULT_INITIAL_BALANCE + "," + UPDATED_INITIAL_BALANCE,
            "initialBalance.in=" + UPDATED_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance is not null
        defaultFinancialAccountFiltering("initialBalance.specified=true", "initialBalance.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance is greater than or equal to
        defaultFinancialAccountFiltering(
            "initialBalance.greaterThanOrEqual=" + DEFAULT_INITIAL_BALANCE,
            "initialBalance.greaterThanOrEqual=" + UPDATED_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance is less than or equal to
        defaultFinancialAccountFiltering(
            "initialBalance.lessThanOrEqual=" + DEFAULT_INITIAL_BALANCE,
            "initialBalance.lessThanOrEqual=" + SMALLER_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance is less than
        defaultFinancialAccountFiltering(
            "initialBalance.lessThan=" + UPDATED_INITIAL_BALANCE,
            "initialBalance.lessThan=" + DEFAULT_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalance is greater than
        defaultFinancialAccountFiltering(
            "initialBalance.greaterThan=" + SMALLER_INITIAL_BALANCE,
            "initialBalance.greaterThan=" + DEFAULT_INITIAL_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate equals to
        defaultFinancialAccountFiltering(
            "initialBalanceDate.equals=" + DEFAULT_INITIAL_BALANCE_DATE,
            "initialBalanceDate.equals=" + UPDATED_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate in
        defaultFinancialAccountFiltering(
            "initialBalanceDate.in=" + DEFAULT_INITIAL_BALANCE_DATE + "," + UPDATED_INITIAL_BALANCE_DATE,
            "initialBalanceDate.in=" + UPDATED_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate is not null
        defaultFinancialAccountFiltering("initialBalanceDate.specified=true", "initialBalanceDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate is greater than or equal to
        defaultFinancialAccountFiltering(
            "initialBalanceDate.greaterThanOrEqual=" + DEFAULT_INITIAL_BALANCE_DATE,
            "initialBalanceDate.greaterThanOrEqual=" + UPDATED_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate is less than or equal to
        defaultFinancialAccountFiltering(
            "initialBalanceDate.lessThanOrEqual=" + DEFAULT_INITIAL_BALANCE_DATE,
            "initialBalanceDate.lessThanOrEqual=" + SMALLER_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate is less than
        defaultFinancialAccountFiltering(
            "initialBalanceDate.lessThan=" + UPDATED_INITIAL_BALANCE_DATE,
            "initialBalanceDate.lessThan=" + DEFAULT_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByInitialBalanceDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where initialBalanceDate is greater than
        defaultFinancialAccountFiltering(
            "initialBalanceDate.greaterThan=" + SMALLER_INITIAL_BALANCE_DATE,
            "initialBalanceDate.greaterThan=" + DEFAULT_INITIAL_BALANCE_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByLastFourDigitsIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where lastFourDigits equals to
        defaultFinancialAccountFiltering(
            "lastFourDigits.equals=" + DEFAULT_LAST_FOUR_DIGITS,
            "lastFourDigits.equals=" + UPDATED_LAST_FOUR_DIGITS
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByLastFourDigitsIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where lastFourDigits in
        defaultFinancialAccountFiltering(
            "lastFourDigits.in=" + DEFAULT_LAST_FOUR_DIGITS + "," + UPDATED_LAST_FOUR_DIGITS,
            "lastFourDigits.in=" + UPDATED_LAST_FOUR_DIGITS
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByLastFourDigitsIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where lastFourDigits is not null
        defaultFinancialAccountFiltering("lastFourDigits.specified=true", "lastFourDigits.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByLastFourDigitsContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where lastFourDigits contains
        defaultFinancialAccountFiltering(
            "lastFourDigits.contains=" + DEFAULT_LAST_FOUR_DIGITS,
            "lastFourDigits.contains=" + UPDATED_LAST_FOUR_DIGITS
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByLastFourDigitsNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where lastFourDigits does not contain
        defaultFinancialAccountFiltering(
            "lastFourDigits.doesNotContain=" + UPDATED_LAST_FOUR_DIGITS,
            "lastFourDigits.doesNotContain=" + DEFAULT_LAST_FOUR_DIGITS
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where description equals to
        defaultFinancialAccountFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where description in
        defaultFinancialAccountFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where description is not null
        defaultFinancialAccountFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where description contains
        defaultFinancialAccountFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where description does not contain
        defaultFinancialAccountFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByColorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where color equals to
        defaultFinancialAccountFiltering("color.equals=" + DEFAULT_COLOR, "color.equals=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByColorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where color in
        defaultFinancialAccountFiltering("color.in=" + DEFAULT_COLOR + "," + UPDATED_COLOR, "color.in=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByColorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where color is not null
        defaultFinancialAccountFiltering("color.specified=true", "color.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByColorContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where color contains
        defaultFinancialAccountFiltering("color.contains=" + DEFAULT_COLOR, "color.contains=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByColorNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where color does not contain
        defaultFinancialAccountFiltering("color.doesNotContain=" + UPDATED_COLOR, "color.doesNotContain=" + DEFAULT_COLOR);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByIconIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where icon equals to
        defaultFinancialAccountFiltering("icon.equals=" + DEFAULT_ICON, "icon.equals=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByIconIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where icon in
        defaultFinancialAccountFiltering("icon.in=" + DEFAULT_ICON + "," + UPDATED_ICON, "icon.in=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByIconIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where icon is not null
        defaultFinancialAccountFiltering("icon.specified=true", "icon.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByIconContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where icon contains
        defaultFinancialAccountFiltering("icon.contains=" + DEFAULT_ICON, "icon.contains=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByIconNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where icon does not contain
        defaultFinancialAccountFiltering("icon.doesNotContain=" + UPDATED_ICON, "icon.doesNotContain=" + DEFAULT_ICON);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByActiveIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where active equals to
        defaultFinancialAccountFiltering("active.equals=" + DEFAULT_ACTIVE, "active.equals=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByActiveIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where active in
        defaultFinancialAccountFiltering("active.in=" + DEFAULT_ACTIVE + "," + UPDATED_ACTIVE, "active.in=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByActiveIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where active is not null
        defaultFinancialAccountFiltering("active.specified=true", "active.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where createdAt equals to
        defaultFinancialAccountFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where createdAt in
        defaultFinancialAccountFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where createdAt is not null
        defaultFinancialAccountFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where updatedAt equals to
        defaultFinancialAccountFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where updatedAt in
        defaultFinancialAccountFiltering(
            "updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT,
            "updatedAt.in=" + UPDATED_UPDATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        // Get all the financialAccountList where updatedAt is not null
        defaultFinancialAccountFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByUserIsEqualToSomething() throws Exception {
        User user = getCurrentMockUser(em);
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        Long userId = user.getId();
        // Get all the financialAccountList where user equals to userId
        defaultFinancialAccountShouldBeFound("userId.equals=" + userId);

        // Get all the financialAccountList where user equals to (userId + 1)
        defaultFinancialAccountShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByBudgetsIsEqualToSomething() throws Exception {
        Budget budgets;
        if (TestUtil.findAll(em, Budget.class).isEmpty()) {
            financialAccountRepository.saveAndFlush(financialAccount);
            budgets = BudgetResourceIT.createEntity(em);
        } else {
            budgets = TestUtil.findAll(em, Budget.class).get(0);
        }
        em.persist(budgets);
        em.flush();
        financialAccount.addBudgets(budgets);
        financialAccountRepository.saveAndFlush(financialAccount);
        Long budgetsId = budgets.getId();
        // Get all the financialAccountList where budgets equals to budgetsId
        defaultFinancialAccountShouldBeFound("budgetsId.equals=" + budgetsId);

        // Get all the financialAccountList where budgets equals to (budgetsId + 1)
        defaultFinancialAccountShouldNotBeFound("budgetsId.equals=" + (budgetsId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialAccountsByTransactionIngestionsIsEqualToSomething() throws Exception {
        TransactionIngestion transactionIngestions;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            financialAccountRepository.saveAndFlush(financialAccount);
            transactionIngestions = TransactionIngestionResourceIT.createEntity(em);
        } else {
            transactionIngestions = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        em.persist(transactionIngestions);
        em.flush();
        financialAccount.addTransactionIngestions(transactionIngestions);
        financialAccountRepository.saveAndFlush(financialAccount);
        Long transactionIngestionsId = transactionIngestions.getId();
        // Get all the financialAccountList where transactionIngestions equals to transactionIngestionsId
        defaultFinancialAccountShouldBeFound("transactionIngestionsId.equals=" + transactionIngestionsId);

        // Get all the financialAccountList where transactionIngestions equals to (transactionIngestionsId + 1)
        defaultFinancialAccountShouldNotBeFound("transactionIngestionsId.equals=" + (transactionIngestionsId + 1));
    }

    private void defaultFinancialAccountFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultFinancialAccountShouldBeFound(shouldBeFound);
        defaultFinancialAccountShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultFinancialAccountShouldBeFound(String filter) throws Exception {
        restFinancialAccountMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].institutionName").value(hasItem(DEFAULT_INSTITUTION_NAME)))
            .andExpect(jsonPath("$.[*].accountType").value(hasItem(DEFAULT_ACCOUNT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].initialBalance").value(hasItem(sameNumber(DEFAULT_INITIAL_BALANCE))))
            .andExpect(jsonPath("$.[*].initialBalanceDate").value(hasItem(DEFAULT_INITIAL_BALANCE_DATE.toString())))
            .andExpect(jsonPath("$.[*].lastFourDigits").value(hasItem(DEFAULT_LAST_FOUR_DIGITS)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].icon").value(hasItem(DEFAULT_ICON)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restFinancialAccountMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL + "/count", filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultFinancialAccountShouldNotBeFound(String filter) throws Exception {
        restFinancialAccountMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restFinancialAccountMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL + "/count", filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    private MockHttpServletRequestBuilder buildFilterRequest(String url, String filter) {
        MockHttpServletRequestBuilder request = get(url).param("sort", "id,desc");
        int separatorIndex = filter.indexOf('=');
        if (separatorIndex > 0) {
            request.param(filter.substring(0, separatorIndex), filter.substring(separatorIndex + 1));
        }
        return request;
    }

    @Test
    @Transactional
    void getNonExistingFinancialAccount() throws Exception {
        // Get the financialAccount
        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingFinancialAccount() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialAccount
        FinancialAccount updatedFinancialAccount = financialAccountRepository.findById(financialAccount.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedFinancialAccount are not directly saved in db
        em.detach(updatedFinancialAccount);
        updatedFinancialAccount
            .name(UPDATED_NAME)
            .institutionName(UPDATED_INSTITUTION_NAME)
            .initialBalance(UPDATED_INITIAL_BALANCE)
            .initialBalanceDate(UPDATED_INITIAL_BALANCE_DATE)
            .lastFourDigits(UPDATED_LAST_FOUR_DIGITS)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(updatedFinancialAccount);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isOk());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedFinancialAccountToMatchAllProperties(updatedFinancialAccount);
    }

    @Test
    @Transactional
    void putNonExistingFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateFinancialAccountWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);
        patchJson.put("institutionName", UPDATED_INSTITUTION_NAME);
        patchJson.put("active", UPDATED_ACTIVE);

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        // Validate the FinancialAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FinancialAccount partialUpdatedFinancialAccount = new FinancialAccount();
        partialUpdatedFinancialAccount.setId(financialAccount.getId());
        partialUpdatedFinancialAccount.name(UPDATED_NAME).institutionName(UPDATED_INSTITUTION_NAME).active(UPDATED_ACTIVE);
        assertFinancialAccountUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFinancialAccount, financialAccount),
            getPersistedFinancialAccount(financialAccount)
        );
    }

    @Test
    @Transactional
    void fullUpdateFinancialAccountWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);
        patchJson.put("institutionName", UPDATED_INSTITUTION_NAME);
        patchJson.put("initialBalance", UPDATED_INITIAL_BALANCE);
        patchJson.put("initialBalanceDate", UPDATED_INITIAL_BALANCE_DATE.toString());
        patchJson.put("lastFourDigits", UPDATED_LAST_FOUR_DIGITS);
        patchJson.put("description", UPDATED_DESCRIPTION);
        patchJson.put("color", UPDATED_COLOR);
        patchJson.put("icon", UPDATED_ICON);
        patchJson.put("active", UPDATED_ACTIVE);
        patchJson.put("createdAt", UPDATED_CREATED_AT.toString());
        patchJson.put("updatedAt", UPDATED_UPDATED_AT.toString());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        // Validate the FinancialAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FinancialAccount partialUpdatedFinancialAccount = new FinancialAccount();
        partialUpdatedFinancialAccount.setId(financialAccount.getId());
        partialUpdatedFinancialAccount
            .name(UPDATED_NAME)
            .institutionName(UPDATED_INSTITUTION_NAME)
            .initialBalance(UPDATED_INITIAL_BALANCE)
            .initialBalanceDate(UPDATED_INITIAL_BALANCE_DATE)
            .lastFourDigits(UPDATED_LAST_FOUR_DIGITS)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        assertFinancialAccountUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFinancialAccount, financialAccount),
            getPersistedFinancialAccount(partialUpdatedFinancialAccount)
        );
    }

    @Test
    @Transactional
    void patchNonExistingFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamFinancialAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialAccount.setId(longCount.incrementAndGet());

        // Create the FinancialAccount
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialAccountMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(financialAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteFinancialAccount() throws Exception {
        // Initialize the database
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the financialAccount
        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createFinancialAccountAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        financialAccountDTO.setUser(otherUserDTO);

        FinancialAccountDTO returnedFinancialAccountDTO = om.readValue(
            restFinancialAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialAccountDTO.class
        );

        assertThat(returnedFinancialAccountDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedFinancialAccount = financialAccountMapper.toEntity(returnedFinancialAccountDTO);
    }

    @Test
    @Transactional
    void getFinancialAccountOwnedByAnotherUserIsNotFound() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL_ID, financialAccount.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllFinancialAccountsDoesNotIncludeAnotherUsersAccounts() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        financialAccountRepository.saveAndFlush(financialAccount);

        restFinancialAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(financialAccount.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetFinancialAccountOwnedByAnotherUser() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        restFinancialAccountMockMvc
            .perform(get(ENTITY_API_URL_ID, financialAccount.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(financialAccount.getId().intValue()));
    }

    @Test
    @Transactional
    void createFinancialAccountWithoutUserInPayloadSucceeds() throws Exception {
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setId(null);
        financialAccountDTO.setUser(null);

        FinancialAccountDTO returnedFinancialAccountDTO = om.readValue(
            restFinancialAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialAccountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialAccountDTO.class
        );

        assertThat(returnedFinancialAccountDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedFinancialAccount = financialAccountMapper.toEntity(returnedFinancialAccountDTO);
    }

    @Test
    @Transactional
    void putFinancialAccountOwnedByAnotherUserIsNotFound() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setName(UPDATED_NAME);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFinancialAccountOwnedByAnotherUserIsNotFound() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        FinancialAccountDTO financialAccountDTO = new FinancialAccountDTO();
        financialAccountDTO.setId(financialAccount.getId());
        financialAccountDTO.setName(UPDATED_NAME);

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFinancialAccountOwnedByAnotherUserIsNotFound() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(financialAccountRepository.existsById(financialAccount.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateFinancialAccountCannotChangeOwner() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        User otherUser = createOtherUser(em);

        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        financialAccountDTO.setUser(otherUserDTO);
        financialAccountDTO.setName(UPDATED_NAME);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isOk());

        FinancialAccount persistedFinancialAccount = financialAccountRepository.findById(financialAccount.getId()).orElseThrow();
        assertThat(persistedFinancialAccount.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedFinancialAccount.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchFinancialAccountCannotChangeOwner() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        User otherUser = createOtherUser(em);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);
        ObjectNode userNode = patchJson.putObject("user");
        userNode.put("id", otherUser.getId());
        userNode.put("login", otherUser.getLogin());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        FinancialAccount persistedFinancialAccount = financialAccountRepository.findById(financialAccount.getId()).orElseThrow();
        assertThat(persistedFinancialAccount.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedFinancialAccount.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void putFinancialAccountCannotChangeCurrency() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setCurrency(UPDATED_CURRENCY);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("financialAccount"));
    }

    @Test
    @Transactional
    void patchFinancialAccountCannotChangeCurrency() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("currency", UPDATED_CURRENCY.toString());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("financialAccount"));
    }

    @Test
    @Transactional
    void putFinancialAccountCannotChangeAccountType() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setAccountType(UPDATED_ACCOUNT_TYPE);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("financialAccount"));
    }

    @Test
    @Transactional
    void patchFinancialAccountCannotChangeAccountType() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("accountType", UPDATED_ACCOUNT_TYPE.toString());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("financialAccount"));
    }

    @Test
    @Transactional
    void patchFinancialAccountCanChangeInitialBalance() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("initialBalance", UPDATED_INITIAL_BALANCE);

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.initialBalance").value(UPDATED_INITIAL_BALANCE.doubleValue()));

        assertThat(getPersistedFinancialAccount(financialAccount).getInitialBalance()).isEqualByComparingTo(UPDATED_INITIAL_BALANCE);
    }

    @Test
    @Transactional
    void patchFinancialAccountCanChangeInitialBalanceDate() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("initialBalanceDate", UPDATED_INITIAL_BALANCE_DATE.toString());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.initialBalanceDate").value(UPDATED_INITIAL_BALANCE_DATE.toString()));

        assertThat(getPersistedFinancialAccount(financialAccount).getInitialBalanceDate()).isEqualTo(UPDATED_INITIAL_BALANCE_DATE);
    }

    @Test
    @Transactional
    void patchFinancialAccountCanChangeActive() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("active", UPDATED_ACTIVE);

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(UPDATED_ACTIVE));

        assertThat(getPersistedFinancialAccount(financialAccount).getActive()).isEqualTo(UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getFinancialAccountCountDoesNotIncludeAnotherUsersAccounts() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        financialAccountRepository.saveAndFlush(financialAccount);

        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllFinancialAccountsIncludingOtherUsers() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        FinancialAccount otherUsersAccount = createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        otherUsersAccount.setName("OTHER_USER_ACCOUNT");
        otherUsersAccount = financialAccountRepository.saveAndFlush(otherUsersAccount);

        restFinancialAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersAccount.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllFinancialAccountsIncludingOtherUsers() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        FinancialAccount otherUsersAccount = createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        financialAccountRepository.saveAndFlush(otherUsersAccount);

        restFinancialAccountMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateFinancialAccountOwnedByAnotherUser() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);

        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setName(UPDATED_NAME);

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteFinancialAccountOwnedByAnotherUser() throws Exception {
        financialAccount.setUser(createOtherUser(em));
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountWithCreditAccountDetailsDeletesDetails() throws Exception {
        financialAccount.setAccountType(AccountType.CREDIT_CARD);
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        CreditAccountDetails details = new CreditAccountDetails()
            .creditLimit(new BigDecimal("1000.00"))
            .statementDay(5)
            .paymentDueDay(20)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT)
            .account(financialAccount);
        em.persist(details);
        em.flush();
        Long detailsId = details.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(TestUtil.findAll(em, CreditAccountDetails.class).stream().noneMatch(cad -> detailsId.equals(cad.getId()))).isTrue();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountLinkedToBudgetRemovesLinkAndPreservesBudget() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        Budget budget = BudgetResourceIT.createEntity(em);
        budget.addAccounts(financialAccount);
        em.persist(budget);
        em.flush();
        Long budgetId = budget.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        em.clear();
        Budget persistedBudget = em.find(Budget.class, budgetId);
        assertThat(persistedBudget).isNotNull();
        assertThat(persistedBudget.getAccounts()).isEmpty();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountLinkedToSubscriptionClearsAccountAndPreservesSubscription() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        FinancialSubscription subscription = FinancialSubscriptionResourceIT.createEntity(em);
        subscription.setAccount(financialAccount);
        em.persist(subscription);
        em.flush();
        Long subscriptionId = subscription.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        em.clear();
        FinancialSubscription persistedSubscription = em.find(FinancialSubscription.class, subscriptionId);
        assertThat(persistedSubscription).isNotNull();
        assertThat(persistedSubscription.getAccount()).isNull();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountWithManualTransactionDeletesTransactionAndPreservesTags() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        FinancialTransaction transaction = createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        Tag tag = TagResourceIT.createEntity(em);
        transaction.addTags(tag);
        em.persist(tag);
        em.merge(transaction);
        em.flush();
        Long transactionId = transaction.getId();
        Long tagId = tag.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(TestUtil.findAll(em, FinancialTransaction.class).stream().noneMatch(tx -> transactionId.equals(tx.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, Tag.class).stream().anyMatch(persistedTag -> tagId.equals(persistedTag.getId()))).isTrue();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountWithInternalTransferPreservesOppositeTransaction() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        FinancialAccount otherAccount = createAccount("OTHER_ACCOUNT");
        FinancialTransaction outgoingTransaction = createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        FinancialTransaction incomingTransaction = createTransaction(otherAccount, LocalDate.parse("2026-01-10"));
        incomingTransaction.setFlow(TransactionFlow.IN);
        incomingTransaction = em.merge(incomingTransaction);
        InternalTransfer transfer = createInternalTransfer(outgoingTransaction, incomingTransaction);
        Long outgoingTransactionId = outgoingTransaction.getId();
        Long incomingTransactionId = incomingTransaction.getId();
        Long transferId = transfer.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(TestUtil.findAll(em, InternalTransfer.class).stream().noneMatch(it -> transferId.equals(it.getId()))).isTrue();
        assertThat(
            TestUtil.findAll(em, FinancialTransaction.class).stream().noneMatch(tx -> outgoingTransactionId.equals(tx.getId()))
        ).isTrue();
        assertThat(
            TestUtil.findAll(em, FinancialTransaction.class).stream().anyMatch(tx -> incomingTransactionId.equals(tx.getId()))
        ).isTrue();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void deleteFinancialAccountWithTransactionIngestionTreeDeletesTree() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        TransactionIngestion fileParent = createTransactionIngestion(financialAccount, IngestionType.FILE);
        FileIngestion fileIngestion = createFileIngestion(fileParent);
        FinancialTransaction fileTransaction = createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        fileTransaction.setOrigin(TransactionOrigin.FILE_IMPORT);
        fileTransaction.setTransactionIngestion(fileParent);
        fileTransaction = em.merge(fileTransaction);
        IngestionRecord record = createIngestionRecord(fileParent, fileTransaction);

        TransactionIngestion apiParent = createTransactionIngestion(financialAccount, IngestionType.API);
        ApiIngestion apiIngestion = createApiIngestion(apiParent);

        Long fileParentId = fileParent.getId();
        Long apiParentId = apiParent.getId();
        Long fileIngestionId = fileIngestion.getId();
        Long apiIngestionId = apiIngestion.getId();
        Long recordId = record.getId();
        Long fileTransactionId = fileTransaction.getId();

        restFinancialAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(TestUtil.findAll(em, TransactionIngestion.class).stream().noneMatch(ti -> fileParentId.equals(ti.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, TransactionIngestion.class).stream().noneMatch(ti -> apiParentId.equals(ti.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, FileIngestion.class).stream().noneMatch(fi -> fileIngestionId.equals(fi.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, ApiIngestion.class).stream().noneMatch(ai -> apiIngestionId.equals(ai.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, IngestionRecord.class).stream().noneMatch(ir -> recordId.equals(ir.getId()))).isTrue();
        assertThat(
            TestUtil.findAll(em, FinancialTransaction.class).stream().noneMatch(tx -> fileTransactionId.equals(tx.getId()))
        ).isTrue();
        insertedFinancialAccount = null;
    }

    @Test
    @Transactional
    void patchFinancialAccountActiveHasNoSideEffects() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        insertedFinancialAccount = null;
        Budget budget = BudgetResourceIT.createEntity(em);
        budget.addAccounts(financialAccount);
        em.persist(budget);
        FinancialSubscription subscription = FinancialSubscriptionResourceIT.createEntity(em);
        subscription.setAccount(financialAccount);
        em.persist(subscription);
        FinancialTransaction transaction = createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        em.flush();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("active", !financialAccount.getActive());

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        em.clear();
        assertThat(em.find(Budget.class, budget.getId()).getAccounts()).hasSize(1);
        assertThat(em.find(FinancialSubscription.class, subscription.getId()).getAccount()).isNotNull();
        assertThat(em.find(FinancialTransaction.class, transaction.getId())).isNotNull();
    }

    @Test
    @Transactional
    void putInitialBalanceDateAfterEarliestTransactionDateFails() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        insertedFinancialAccount = null;
        createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setInitialBalanceDate(LocalDate.parse("2026-01-11"));

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void putInitialBalanceDateEqualOrBeforeEarliestTransactionDateSucceeds() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        insertedFinancialAccount = null;
        createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        FinancialAccountDTO financialAccountDTO = financialAccountMapper.toDto(financialAccount);
        financialAccountDTO.setInitialBalanceDate(LocalDate.parse("2026-01-10"));

        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isOk());

        financialAccountDTO.setInitialBalanceDate(LocalDate.parse("2026-01-09"));
        restFinancialAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialAccountDTO))
            )
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void patchInitialBalanceDateAfterEarliestTransactionDateFails() throws Exception {
        insertedFinancialAccount = financialAccountRepository.saveAndFlush(financialAccount);
        insertedFinancialAccount = null;
        createTransaction(financialAccount, LocalDate.parse("2026-01-10"));
        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("initialBalanceDate", "2026-01-11");

        restFinancialAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest());
    }

    protected long getRepositoryCount() {
        return financialAccountRepository.count();
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

    protected FinancialAccount getPersistedFinancialAccount(FinancialAccount financialAccount) {
        return financialAccountRepository.findById(financialAccount.getId()).orElseThrow();
    }

    protected void assertPersistedFinancialAccountToMatchAllProperties(FinancialAccount expectedFinancialAccount) {
        assertFinancialAccountAllPropertiesEquals(expectedFinancialAccount, getPersistedFinancialAccount(expectedFinancialAccount));
    }

    protected void assertPersistedFinancialAccountToMatchUpdatableProperties(FinancialAccount expectedFinancialAccount) {
        assertFinancialAccountAllUpdatablePropertiesEquals(
            expectedFinancialAccount,
            getPersistedFinancialAccount(expectedFinancialAccount)
        );
    }
}
