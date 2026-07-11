package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.BudgetAsserts.*;
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
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetPeriod;
import com.fintrack.app.domain.enumeration.BudgetStatus;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TagMatchMode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.BudgetService;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Integration tests for the {@link BudgetResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class BudgetResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal(100);
    private static final BigDecimal UPDATED_AMOUNT = new BigDecimal(200);
    private static final BigDecimal SMALLER_AMOUNT = new BigDecimal(0 - 1);

    private static final CurrencyCode DEFAULT_CURRENCY = CurrencyCode.MXN;
    private static final CurrencyCode UPDATED_CURRENCY = CurrencyCode.USD;

    private static final BudgetPeriod DEFAULT_PERIOD = BudgetPeriod.WEEKLY;
    private static final BudgetPeriod UPDATED_PERIOD = BudgetPeriod.MONTHLY;

    private static final LocalDate DEFAULT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_START_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_START_DATE = LocalDate.ofEpochDay(-1L);

    private static final LocalDate DEFAULT_END_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_END_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_END_DATE = LocalDate.ofEpochDay(-1L);

    private static final BudgetStatus DEFAULT_STATUS = BudgetStatus.ACTIVE;
    private static final BudgetStatus UPDATED_STATUS = BudgetStatus.PAUSED;

    private static final TagMatchMode DEFAULT_TAG_MATCH_MODE = TagMatchMode.ANY;
    private static final TagMatchMode UPDATED_TAG_MATCH_MODE = TagMatchMode.ALL;

    private static final BigDecimal DEFAULT_WARNING_PERCENTAGE = new BigDecimal(0);
    private static final BigDecimal UPDATED_WARNING_PERCENTAGE = new BigDecimal(1);
    private static final BigDecimal SMALLER_WARNING_PERCENTAGE = new BigDecimal(0 - 1);

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/budgets";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private BudgetRepository budgetRepositoryMock;

    @Autowired
    private BudgetMapper budgetMapper;

    @Mock
    private BudgetService budgetServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBudgetMockMvc;

    private Budget budget;

    private Budget insertedBudget;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Budget createEntity(EntityManager em) {
        Budget budget = new Budget()
            .name(DEFAULT_NAME)
            .amount(DEFAULT_AMOUNT)
            .currency(DEFAULT_CURRENCY)
            .period(DEFAULT_PERIOD)
            .startDate(DEFAULT_START_DATE)
            .endDate(DEFAULT_END_DATE)
            .status(DEFAULT_STATUS)
            .tagMatchMode(DEFAULT_TAG_MATCH_MODE)
            .warningPercentage(DEFAULT_WARNING_PERCENTAGE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        budget.setUser(getCurrentMockUser(em));
        return budget;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Budget createUpdatedEntity(EntityManager em) {
        Budget updatedBudget = new Budget()
            .name(UPDATED_NAME)
            .amount(UPDATED_AMOUNT)
            .currency(UPDATED_CURRENCY)
            .period(UPDATED_PERIOD)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .status(UPDATED_STATUS)
            .tagMatchMode(UPDATED_TAG_MATCH_MODE)
            .warningPercentage(UPDATED_WARNING_PERCENTAGE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        updatedBudget.setUser(getCurrentMockUser(em));
        return updatedBudget;
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

    @BeforeEach
    void initTest() {
        budget = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedBudget != null) {
            budgetRepository.delete(insertedBudget);
            insertedBudget = null;
        }
    }

    @Test
    @Transactional
    void createBudget() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        var returnedBudgetDTO = om.readValue(
            restBudgetMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BudgetDTO.class
        );

        // Validate the Budget in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBudget = budgetMapper.toEntity(returnedBudgetDTO);
        assertBudgetUpdatableFieldsEquals(returnedBudget, getPersistedBudget(returnedBudget));

        insertedBudget = returnedBudget;
    }

    @Test
    @Transactional
    void createBudgetWithExistingId() throws Exception {
        // Create the Budget with an existing ID
        budget.setId(1L);
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setName(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setAmount(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setCurrency(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPeriodIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setPeriod(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStartDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setStartDate(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setStatus(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTagMatchModeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setTagMatchMode(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setCreatedAt(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        budget.setUpdatedAt(null);

        // Create the Budget, which fails.
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllBudgets() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(budget.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].period").value(hasItem(DEFAULT_PERIOD.toString())))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].tagMatchMode").value(hasItem(DEFAULT_TAG_MATCH_MODE.toString())))
            .andExpect(jsonPath("$.[*].warningPercentage").value(hasItem(sameNumber(DEFAULT_WARNING_PERCENTAGE))))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBudgetsWithEagerRelationshipsIsEnabled() throws Exception {
        when(budgetServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBudgetMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(budgetServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBudgetsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(budgetServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBudgetMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(budgetRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getBudget() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get the budget
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL_ID, budget.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(budget.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.amount").value(sameNumber(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.period").value(DEFAULT_PERIOD.toString()))
            .andExpect(jsonPath("$.startDate").value(DEFAULT_START_DATE.toString()))
            .andExpect(jsonPath("$.endDate").value(DEFAULT_END_DATE.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.tagMatchMode").value(DEFAULT_TAG_MATCH_MODE.toString()))
            .andExpect(jsonPath("$.warningPercentage").value(sameNumber(DEFAULT_WARNING_PERCENTAGE)))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getBudgetsByIdFiltering() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        Long id = budget.getId();

        defaultBudgetFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultBudgetFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultBudgetFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllBudgetsByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where name equals to
        defaultBudgetFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllBudgetsByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where name in
        defaultBudgetFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllBudgetsByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where name is not null
        defaultBudgetFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where name contains
        defaultBudgetFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllBudgetsByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where name does not contain
        defaultBudgetFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount equals to
        defaultBudgetFiltering("amount.equals=" + DEFAULT_AMOUNT, "amount.equals=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount in
        defaultBudgetFiltering("amount.in=" + DEFAULT_AMOUNT + "," + UPDATED_AMOUNT, "amount.in=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount is not null
        defaultBudgetFiltering("amount.specified=true", "amount.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount is greater than or equal to
        defaultBudgetFiltering("amount.greaterThanOrEqual=" + DEFAULT_AMOUNT, "amount.greaterThanOrEqual=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount is less than or equal to
        defaultBudgetFiltering("amount.lessThanOrEqual=" + DEFAULT_AMOUNT, "amount.lessThanOrEqual=" + SMALLER_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount is less than
        defaultBudgetFiltering("amount.lessThan=" + UPDATED_AMOUNT, "amount.lessThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where amount is greater than
        defaultBudgetFiltering("amount.greaterThan=" + SMALLER_AMOUNT, "amount.greaterThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllBudgetsByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where currency equals to
        defaultBudgetFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBudgetsByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where currency in
        defaultBudgetFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBudgetsByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where currency is not null
        defaultBudgetFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByPeriodIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where period equals to
        defaultBudgetFiltering("period.equals=" + DEFAULT_PERIOD, "period.equals=" + UPDATED_PERIOD);
    }

    @Test
    @Transactional
    void getAllBudgetsByPeriodIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where period in
        defaultBudgetFiltering("period.in=" + DEFAULT_PERIOD + "," + UPDATED_PERIOD, "period.in=" + UPDATED_PERIOD);
    }

    @Test
    @Transactional
    void getAllBudgetsByPeriodIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where period is not null
        defaultBudgetFiltering("period.specified=true", "period.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate equals to
        defaultBudgetFiltering("startDate.equals=" + DEFAULT_START_DATE, "startDate.equals=" + UPDATED_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate in
        defaultBudgetFiltering("startDate.in=" + DEFAULT_START_DATE + "," + UPDATED_START_DATE, "startDate.in=" + UPDATED_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate is not null
        defaultBudgetFiltering("startDate.specified=true", "startDate.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate is greater than or equal to
        defaultBudgetFiltering("startDate.greaterThanOrEqual=" + DEFAULT_START_DATE, "startDate.greaterThanOrEqual=" + UPDATED_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate is less than or equal to
        defaultBudgetFiltering("startDate.lessThanOrEqual=" + DEFAULT_START_DATE, "startDate.lessThanOrEqual=" + SMALLER_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate is less than
        defaultBudgetFiltering("startDate.lessThan=" + UPDATED_START_DATE, "startDate.lessThan=" + DEFAULT_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStartDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where startDate is greater than
        defaultBudgetFiltering("startDate.greaterThan=" + SMALLER_START_DATE, "startDate.greaterThan=" + DEFAULT_START_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate equals to
        defaultBudgetFiltering("endDate.equals=" + DEFAULT_END_DATE, "endDate.equals=" + UPDATED_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate in
        defaultBudgetFiltering("endDate.in=" + DEFAULT_END_DATE + "," + UPDATED_END_DATE, "endDate.in=" + UPDATED_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate is not null
        defaultBudgetFiltering("endDate.specified=true", "endDate.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate is greater than or equal to
        defaultBudgetFiltering("endDate.greaterThanOrEqual=" + DEFAULT_END_DATE, "endDate.greaterThanOrEqual=" + UPDATED_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate is less than or equal to
        defaultBudgetFiltering("endDate.lessThanOrEqual=" + DEFAULT_END_DATE, "endDate.lessThanOrEqual=" + SMALLER_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate is less than
        defaultBudgetFiltering("endDate.lessThan=" + UPDATED_END_DATE, "endDate.lessThan=" + DEFAULT_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByEndDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where endDate is greater than
        defaultBudgetFiltering("endDate.greaterThan=" + SMALLER_END_DATE, "endDate.greaterThan=" + DEFAULT_END_DATE);
    }

    @Test
    @Transactional
    void getAllBudgetsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where status equals to
        defaultBudgetFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBudgetsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where status in
        defaultBudgetFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBudgetsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where status is not null
        defaultBudgetFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByTagMatchModeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where tagMatchMode equals to
        defaultBudgetFiltering("tagMatchMode.equals=" + DEFAULT_TAG_MATCH_MODE, "tagMatchMode.equals=" + UPDATED_TAG_MATCH_MODE);
    }

    @Test
    @Transactional
    void getAllBudgetsByTagMatchModeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where tagMatchMode in
        defaultBudgetFiltering(
            "tagMatchMode.in=" + DEFAULT_TAG_MATCH_MODE + "," + UPDATED_TAG_MATCH_MODE,
            "tagMatchMode.in=" + UPDATED_TAG_MATCH_MODE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByTagMatchModeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where tagMatchMode is not null
        defaultBudgetFiltering("tagMatchMode.specified=true", "tagMatchMode.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage equals to
        defaultBudgetFiltering(
            "warningPercentage.equals=" + DEFAULT_WARNING_PERCENTAGE,
            "warningPercentage.equals=" + UPDATED_WARNING_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage in
        defaultBudgetFiltering(
            "warningPercentage.in=" + DEFAULT_WARNING_PERCENTAGE + "," + UPDATED_WARNING_PERCENTAGE,
            "warningPercentage.in=" + UPDATED_WARNING_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage is not null
        defaultBudgetFiltering("warningPercentage.specified=true", "warningPercentage.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage is greater than or equal to
        defaultBudgetFiltering(
            "warningPercentage.greaterThanOrEqual=" + DEFAULT_WARNING_PERCENTAGE,
            "warningPercentage.greaterThanOrEqual=" + (DEFAULT_WARNING_PERCENTAGE.add(BigDecimal.ONE))
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage is less than or equal to
        defaultBudgetFiltering(
            "warningPercentage.lessThanOrEqual=" + DEFAULT_WARNING_PERCENTAGE,
            "warningPercentage.lessThanOrEqual=" + SMALLER_WARNING_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage is less than
        defaultBudgetFiltering(
            "warningPercentage.lessThan=" + (DEFAULT_WARNING_PERCENTAGE.add(BigDecimal.ONE)),
            "warningPercentage.lessThan=" + DEFAULT_WARNING_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByWarningPercentageIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where warningPercentage is greater than
        defaultBudgetFiltering(
            "warningPercentage.greaterThan=" + SMALLER_WARNING_PERCENTAGE,
            "warningPercentage.greaterThan=" + DEFAULT_WARNING_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllBudgetsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where createdAt equals to
        defaultBudgetFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllBudgetsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where createdAt in
        defaultBudgetFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllBudgetsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where createdAt is not null
        defaultBudgetFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where updatedAt equals to
        defaultBudgetFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllBudgetsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where updatedAt in
        defaultBudgetFiltering("updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT, "updatedAt.in=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllBudgetsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        // Get all the budgetList where updatedAt is not null
        defaultBudgetFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBudgetsByUserIsEqualToSomething() throws Exception {
        User user = getCurrentMockUser(em);
        budget.setUser(user);
        budgetRepository.saveAndFlush(budget);
        Long userId = user.getId();
        // Get all the budgetList where user equals to userId
        defaultBudgetShouldBeFound("userId.equals=" + userId);

        // Get all the budgetList where user equals to (userId + 1)
        defaultBudgetShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllBudgetsByAccountsIsEqualToSomething() throws Exception {
        FinancialAccount accounts;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            budgetRepository.saveAndFlush(budget);
            accounts = FinancialAccountResourceIT.createEntity(em);
        } else {
            accounts = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        em.persist(accounts);
        em.flush();
        budget.addAccounts(accounts);
        budgetRepository.saveAndFlush(budget);
        Long accountsId = accounts.getId();
        // Get all the budgetList where accounts equals to accountsId
        defaultBudgetShouldBeFound("accountsId.equals=" + accountsId);

        // Get all the budgetList where accounts equals to (accountsId + 1)
        defaultBudgetShouldNotBeFound("accountsId.equals=" + (accountsId + 1));
    }

    @Test
    @Transactional
    void getAllBudgetsByCategoriesIsEqualToSomething() throws Exception {
        Category categories;
        if (TestUtil.findAll(em, Category.class).isEmpty()) {
            budgetRepository.saveAndFlush(budget);
            categories = CategoryResourceIT.createEntity(em);
        } else {
            categories = TestUtil.findAll(em, Category.class).get(0);
        }
        em.persist(categories);
        em.flush();
        budget.addCategories(categories);
        budgetRepository.saveAndFlush(budget);
        Long categoriesId = categories.getId();
        // Get all the budgetList where categories equals to categoriesId
        defaultBudgetShouldBeFound("categoriesId.equals=" + categoriesId);

        // Get all the budgetList where categories equals to (categoriesId + 1)
        defaultBudgetShouldNotBeFound("categoriesId.equals=" + (categoriesId + 1));
    }

    @Test
    @Transactional
    void getAllBudgetsByTagsIsEqualToSomething() throws Exception {
        Tag tags;
        if (TestUtil.findAll(em, Tag.class).isEmpty()) {
            budgetRepository.saveAndFlush(budget);
            tags = TagResourceIT.createEntity(em);
        } else {
            tags = TestUtil.findAll(em, Tag.class).get(0);
        }
        em.persist(tags);
        em.flush();
        budget.addTags(tags);
        budgetRepository.saveAndFlush(budget);
        Long tagsId = tags.getId();
        // Get all the budgetList where tags equals to tagsId
        defaultBudgetShouldBeFound("tagsId.equals=" + tagsId);

        // Get all the budgetList where tags equals to (tagsId + 1)
        defaultBudgetShouldNotBeFound("tagsId.equals=" + (tagsId + 1));
    }

    private void defaultBudgetFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultBudgetShouldBeFound(shouldBeFound);
        defaultBudgetShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultBudgetShouldBeFound(String filter) throws Exception {
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(budget.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].period").value(hasItem(DEFAULT_PERIOD.toString())))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].tagMatchMode").value(hasItem(DEFAULT_TAG_MATCH_MODE.toString())))
            .andExpect(jsonPath("$.[*].warningPercentage").value(hasItem(sameNumber(DEFAULT_WARNING_PERCENTAGE))))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultBudgetShouldNotBeFound(String filter) throws Exception {
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingBudget() throws Exception {
        // Get the budget
        restBudgetMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBudget() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the budget
        Budget updatedBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBudget are not directly saved in db
        em.detach(updatedBudget);
        updatedBudget
            .name(UPDATED_NAME)
            .amount(UPDATED_AMOUNT)
            .currency(UPDATED_CURRENCY)
            .period(UPDATED_PERIOD)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .status(UPDATED_STATUS)
            .tagMatchMode(UPDATED_TAG_MATCH_MODE)
            .warningPercentage(UPDATED_WARNING_PERCENTAGE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        BudgetDTO budgetDTO = budgetMapper.toDto(updatedBudget);

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isOk());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBudgetToMatchAllProperties(updatedBudget);
    }

    @Test
    @Transactional
    void putNonExistingBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateBudgetWithPatch() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("amount", UPDATED_AMOUNT);
        patchJson.put("period", UPDATED_PERIOD.toString());
        patchJson.put("startDate", UPDATED_START_DATE.toString());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        // Validate the Budget in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        Budget partialUpdatedBudget = new Budget();
        partialUpdatedBudget.setId(budget.getId());
        partialUpdatedBudget.amount(UPDATED_AMOUNT).period(UPDATED_PERIOD).startDate(UPDATED_START_DATE);
        assertBudgetUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedBudget, budget), getPersistedBudget(budget));
    }

    @Test
    @Transactional
    void fullUpdateBudgetWithPatch() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);
        patchJson.put("amount", UPDATED_AMOUNT);
        patchJson.put("currency", UPDATED_CURRENCY.toString());
        patchJson.put("period", UPDATED_PERIOD.toString());
        patchJson.put("startDate", UPDATED_START_DATE.toString());
        patchJson.put("endDate", UPDATED_END_DATE.toString());
        patchJson.put("status", UPDATED_STATUS.toString());
        patchJson.put("tagMatchMode", UPDATED_TAG_MATCH_MODE.toString());
        patchJson.put("warningPercentage", UPDATED_WARNING_PERCENTAGE);
        patchJson.put("createdAt", UPDATED_CREATED_AT.toString());
        patchJson.put("updatedAt", UPDATED_UPDATED_AT.toString());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        // Validate the Budget in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        Budget partialUpdatedBudget = new Budget();
        partialUpdatedBudget.setId(budget.getId());
        partialUpdatedBudget
            .name(UPDATED_NAME)
            .amount(UPDATED_AMOUNT)
            .currency(UPDATED_CURRENCY)
            .period(UPDATED_PERIOD)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .status(UPDATED_STATUS)
            .tagMatchMode(UPDATED_TAG_MATCH_MODE)
            .warningPercentage(UPDATED_WARNING_PERCENTAGE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        assertBudgetUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedBudget, budget), getPersistedBudget(partialUpdatedBudget));
    }

    @Test
    @Transactional
    void patchNonExistingBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budgetDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBudget() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        budget.setId(longCount.incrementAndGet());

        // Create the Budget
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBudgetMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Budget in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteBudget() throws Exception {
        // Initialize the database
        insertedBudget = budgetRepository.saveAndFlush(budget);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the budget
        restBudgetMockMvc
            .perform(delete(ENTITY_API_URL_ID, budget.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createBudgetAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        budgetDTO.setUser(otherUserDTO);

        BudgetDTO returnedBudgetDTO = om.readValue(
            restBudgetMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BudgetDTO.class
        );

        assertThat(returnedBudgetDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedBudget = budgetMapper.toEntity(returnedBudgetDTO);
    }

    @Test
    @Transactional
    void getBudgetOwnedByAnotherUserIsNotFound() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        restBudgetMockMvc.perform(get(ENTITY_API_URL_ID, budget.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllBudgetsDoesNotIncludeAnotherUsersBudgets() throws Exception {
        budget.setUser(createOtherUser(em));
        budgetRepository.saveAndFlush(budget);

        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(budget.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetBudgetOwnedByAnotherUser() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        restBudgetMockMvc
            .perform(get(ENTITY_API_URL_ID, budget.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(budget.getId().intValue()));
    }

    @Test
    @Transactional
    void createBudgetWithoutUserInPayloadSucceeds() throws Exception {
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        budgetDTO.setUser(null);

        BudgetDTO returnedBudgetDTO = om.readValue(
            restBudgetMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BudgetDTO.class
        );

        assertThat(returnedBudgetDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedBudget = budgetMapper.toEntity(returnedBudgetDTO);
    }

    @Test
    @Transactional
    void putBudgetOwnedByAnotherUserIsNotFound() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setName(UPDATED_NAME);

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchBudgetOwnedByAnotherUserIsNotFound() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        BudgetDTO budgetDTO = new BudgetDTO();
        budgetDTO.setId(budget.getId());
        budgetDTO.setName(UPDATED_NAME);

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budgetDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteBudgetOwnedByAnotherUserIsNotFound() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        restBudgetMockMvc
            .perform(delete(ENTITY_API_URL_ID, budget.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(budgetRepository.existsById(budget.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateBudgetCannotChangeOwner() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);
        User otherUser = createOtherUser(em);

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        budgetDTO.setUser(otherUserDTO);
        budgetDTO.setName(UPDATED_NAME);

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isOk());

        Budget persistedBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(persistedBudget.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedBudget.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchBudgetCannotChangeOwner() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);
        User otherUser = createOtherUser(em);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);
        ObjectNode userNode = patchJson.putObject("user");
        userNode.put("id", otherUser.getId());
        userNode.put("login", otherUser.getLogin());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        Budget persistedBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(persistedBudget.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedBudget.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void getBudgetCountDoesNotIncludeAnotherUsersBudgets() throws Exception {
        budget.setUser(createOtherUser(em));
        budgetRepository.saveAndFlush(budget);

        restBudgetMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllBudgetsIncludingOtherUsers() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);
        Budget otherUsersBudget = createEntity(em);
        otherUsersBudget.setUser(createOtherUser(em));
        otherUsersBudget.setName("OTHER_USER_BUDGET");
        otherUsersBudget = budgetRepository.saveAndFlush(otherUsersBudget);

        restBudgetMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(budget.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersBudget.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllBudgetsIncludingOtherUsers() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);
        Budget otherUsersBudget = createEntity(em);
        otherUsersBudget.setUser(createOtherUser(em));
        budgetRepository.saveAndFlush(otherUsersBudget);

        restBudgetMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateBudgetOwnedByAnotherUser() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setName(UPDATED_NAME);

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteBudgetOwnedByAnotherUser() throws Exception {
        budget.setUser(createOtherUser(em));
        insertedBudget = budgetRepository.saveAndFlush(budget);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restBudgetMockMvc
            .perform(delete(ENTITY_API_URL_ID, budget.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedBudget = null;
    }

    @Test
    @Transactional
    void createBudgetWithAccountOwnedByAnotherUserFails() throws Exception {
        FinancialAccount otherUsersAccount = FinancialAccountResourceIT.createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        otherUsersAccount = em.merge(otherUsersAccount);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        Set<FinancialAccountDTO> accounts = new HashSet<>();
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        accounts.add(accountDTO);
        budgetDTO.setAccounts(accounts);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithCategoryOwnedByAnotherUserFails() throws Exception {
        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        Set<CategoryDTO> categories = new HashSet<>();
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(otherUsersCategory.getId());
        categories.add(categoryDTO);
        budgetDTO.setCategories(categories);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithTagOwnedByAnotherUserFails() throws Exception {
        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        Set<TagDTO> tags = new HashSet<>();
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(otherUsersTag.getId());
        tags.add(tagDTO);
        budgetDTO.setTags(tags);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithAccessibleAccountSucceeds() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        Set<FinancialAccountDTO> accounts = new HashSet<>();
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(ownAccount.getId());
        accounts.add(accountDTO);
        budgetDTO.setAccounts(accounts);

        BudgetDTO returnedBudgetDTO = om.readValue(
            restBudgetMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BudgetDTO.class
        );

        assertThat(returnedBudgetDTO.getAccounts()).extracting(FinancialAccountDTO::getId).contains(ownAccount.getId());
        insertedBudget = budgetMapper.toEntity(returnedBudgetDTO);
    }

    @Test
    @Transactional
    void patchBudgetScalarPreservesM2mLinks() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        budget.addAccounts(ownAccount);
        budget.addTags(ownTag);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("name", UPDATED_NAME);

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME))
            .andExpect(jsonPath("$.accounts[*].id").value(hasItem(ownAccount.getId().intValue())))
            .andExpect(jsonPath("$.tags[*].id").value(hasItem(ownTag.getId().intValue())));

        Budget persistedBudget = getPersistedBudget(budget);
        assertThat(persistedBudget.getAccounts()).extracting(FinancialAccount::getId).contains(ownAccount.getId());
        assertThat(persistedBudget.getTags()).extracting(Tag::getId).contains(ownTag.getId());
    }

    @Test
    @Transactional
    void patchBudgetNullTagsClearsTags() throws Exception {
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        budget.addTags(ownTag);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putNull("tags");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isEmpty());

        assertThat(getPersistedBudget(budget).getTags()).isEmpty();
    }

    @Test
    @Transactional
    void patchBudgetEmptyTagsClearsTags() throws Exception {
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        budget.addTags(ownTag);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("tags");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isEmpty());

        assertThat(getPersistedBudget(budget).getTags()).isEmpty();
    }

    @Test
    @Transactional
    void patchBudgetEmptyAccountsClearsAccounts() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        em.flush();

        budget.addAccounts(ownAccount);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("accounts");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts").isEmpty());

        assertThat(getPersistedBudget(budget).getAccounts()).isEmpty();
    }

    @Test
    @Transactional
    void patchBudgetEmptyCategoriesClearsCategories() throws Exception {
        Category ownCategory = CategoryResourceIT.createEntity(em);
        ownCategory = em.merge(ownCategory);
        em.flush();

        budget.addCategories(ownCategory);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("categories");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories").isEmpty());

        assertThat(getPersistedBudget(budget).getCategories()).isEmpty();
    }

    @Test
    @Transactional
    void patchBudgetReplacesTags() throws Exception {
        Tag firstTag = TagResourceIT.createEntity(em);
        firstTag = em.merge(firstTag);
        Tag secondTag = TagResourceIT.createEntity(em);
        secondTag.name("Second tag");
        secondTag = em.merge(secondTag);
        em.flush();

        budget.addTags(firstTag);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("tags").addObject().put("id", secondTag.getId());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[*].id").value(hasItem(secondTag.getId().intValue())))
            .andExpect(jsonPath("$.tags[*].id").value(not(hasItem(firstTag.getId().intValue()))));

        assertThat(getPersistedBudget(budget).getTags()).extracting(Tag::getId).containsExactly(secondTag.getId());
    }

    @Test
    @Transactional
    void patchBudgetForeignAccountFails() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);

        FinancialAccount otherUsersAccount = FinancialAccountResourceIT.createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        otherUsersAccount = em.merge(otherUsersAccount);
        em.flush();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("accounts").addObject().put("id", otherUsersAccount.getId());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("budget"));
    }

    @Test
    @Transactional
    void patchBudgetForeignCategoryFails() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);

        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("categories").addObject().put("id", otherUsersCategory.getId());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("budget"));
    }

    @Test
    @Transactional
    void patchBudgetForeignTagFails() throws Exception {
        insertedBudget = budgetRepository.saveAndFlush(budget);

        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putArray("tags").addObject().put("id", otherUsersTag.getId());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("budget"));
    }

    private Budget persistBudget() {
        insertedBudget = budgetRepository.saveAndFlush(budget);
        em.clear();
        return budgetRepository.findById(insertedBudget.getId()).orElseThrow();
    }

    private long countJoinRows(String table, Long budgetId) {
        return (
            (Number) em
                .createNativeQuery("select count(*) from " + table + " where budget_id = :budgetId")
                .setParameter("budgetId", budgetId)
                .getSingleResult()
        ).longValue();
    }

    @Test
    @Transactional
    void deleteBudgetWithM2mLinksCleansJoinTablesAndPreservesRelatedEntities() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account = em.merge(account);
        Category category = CategoryResourceIT.createEntity(em);
        category = em.merge(category);
        Tag tag = TagResourceIT.createEntity(em);
        tag = em.merge(tag);
        em.flush();

        budget.addAccounts(account);
        budget.addCategories(category);
        budget.addTags(tag);
        Budget persistedBudget = persistBudget();

        restBudgetMockMvc
            .perform(delete(ENTITY_API_URL_ID, persistedBudget.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(budgetRepository.existsById(persistedBudget.getId())).isFalse();
        assertThat(countJoinRows("rel_budget__accounts", persistedBudget.getId())).isZero();
        assertThat(countJoinRows("rel_budget__categories", persistedBudget.getId())).isZero();
        assertThat(countJoinRows("rel_budget__tags", persistedBudget.getId())).isZero();
        assertThat(financialAccountRepository.existsById(account.getId())).isTrue();
        assertThat(categoryRepository.existsById(category.getId())).isTrue();
        assertThat(tagRepository.existsById(tag.getId())).isTrue();
        insertedBudget = null;
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminDeleteForeignBudgetWithM2mLinksSucceeds() throws Exception {
        User foreignOwner = createOtherUser(em);
        budget.setUser(foreignOwner);
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account.setUser(foreignOwner);
        account = em.merge(account);
        budget.addAccounts(account);
        Budget persistedBudget = persistBudget();

        restBudgetMockMvc
            .perform(delete(ENTITY_API_URL_ID, persistedBudget.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(budgetRepository.existsById(persistedBudget.getId())).isFalse();
        assertThat(countJoinRows("rel_budget__accounts", persistedBudget.getId())).isZero();
        insertedBudget = null;
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminUpdateForeignBudgetWithOwnerValidLinksSucceeds() throws Exception {
        User budgetOwner = createOtherUser(em);
        budget.setUser(budgetOwner);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        Category ownerCategory = CategoryResourceIT.createEntity(em);
        ownerCategory.setUser(budgetOwner);
        ownerCategory = em.merge(ownerCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(ownerCategory.getId());
        budgetDTO.setCategories(new HashSet<>(Set.of(categoryDTO)));

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isOk());

        assertThat(getPersistedBudget(budget).getCategories()).extracting(Category::getId).contains(ownerCategory.getId());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminUpdateForeignBudgetWithAdminOwnedCategoryFails() throws Exception {
        User budgetOwner = createOtherUser(em);
        budget.setUser(budgetOwner);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        Category adminCategory = CategoryResourceIT.createEntity(em);
        adminCategory = em.merge(adminCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(adminCategory.getId());
        budgetDTO.setCategories(new HashSet<>(Set.of(categoryDTO)));

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithZeroAmountFails() throws Exception {
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        budgetDTO.setAmount(BigDecimal.ZERO);

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithNegativeAmountFails() throws Exception {
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        budgetDTO.setAmount(new BigDecimal("-1"));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithEndDateBeforeStartDateFails() throws Exception {
        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        budgetDTO.setStartDate(LocalDate.of(2026, 6, 1));
        budgetDTO.setEndDate(LocalDate.of(2026, 1, 1));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createBudgetWithAccountCurrencyMismatchFails() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account.setCurrency(CurrencyCode.USD);
        em.persist(account);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(account.getId());
        budgetDTO.setAccounts(new HashSet<>(Set.of(accountDTO)));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateBudgetCurrencyWithLinkedAccountMismatchFails() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account = em.merge(account);
        em.flush();

        budget.addAccounts(account);
        Budget persistedBudget = persistBudget();

        BudgetDTO budgetDTO = budgetMapper.toDto(persistedBudget);
        budgetDTO.setCurrency(CurrencyCode.USD);

        restBudgetMockMvc
            .perform(
                put(ENTITY_API_URL_ID, budgetDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchBudgetCurrencyWithoutLinkedAccountsSucceeds() throws Exception {
        Budget persistedBudget = persistBudget();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("currency", CurrencyCode.USD.toString());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, persistedBudget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value(CurrencyCode.USD.toString()));
    }

    @Test
    @Transactional
    void createBudgetWithExpenseCategorySucceeds() throws Exception {
        Category expenseCategory = CategoryResourceIT.createEntity(em);
        expenseCategory.setCategoryType(CategoryType.EXPENSE);
        expenseCategory = em.merge(expenseCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(expenseCategory.getId());
        budgetDTO.setCategories(new HashSet<>(Set.of(categoryDTO)));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isCreated());
    }

    @Test
    @Transactional
    void createBudgetWithBothCategorySucceeds() throws Exception {
        Category bothCategory = CategoryResourceIT.createEntity(em);
        bothCategory.setCategoryType(CategoryType.BOTH);
        bothCategory = em.merge(bothCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(bothCategory.getId());
        budgetDTO.setCategories(new HashSet<>(Set.of(categoryDTO)));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isCreated());
    }

    @Test
    @Transactional
    void createBudgetWithIncomeCategoryFails() throws Exception {
        Category incomeCategory = CategoryResourceIT.createEntity(em);
        incomeCategory.setCategoryType(CategoryType.INCOME);
        incomeCategory = em.merge(incomeCategory);
        em.flush();

        BudgetDTO budgetDTO = budgetMapper.toDto(budget);
        budgetDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(incomeCategory.getId());
        budgetDTO.setCategories(new HashSet<>(Set.of(categoryDTO)));

        restBudgetMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(budgetDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchBudgetStatusPausedPreservesM2mLinks() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account = em.merge(account);
        Tag tag = TagResourceIT.createEntity(em);
        tag = em.merge(tag);
        em.flush();

        budget.addAccounts(account);
        budget.addTags(tag);
        Budget persistedBudget = persistBudget();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("status", BudgetStatus.PAUSED.toString());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, persistedBudget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(BudgetStatus.PAUSED.toString()));

        Budget persisted = getPersistedBudget(persistedBudget);
        assertThat(persisted.getStatus()).isEqualTo(BudgetStatus.PAUSED);
        assertThat(persisted.getAccounts()).extracting(FinancialAccount::getId).contains(account.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).contains(tag.getId());
    }

    @Test
    @Transactional
    void patchBudgetStatusCompletedPreservesM2mLinks() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account = em.merge(account);
        budget.addAccounts(account);
        Budget persistedBudget = persistBudget();

        ObjectNode patchJson = om.createObjectNode();
        patchJson.put("status", BudgetStatus.COMPLETED.toString());

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, persistedBudget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        assertThat(getPersistedBudget(persistedBudget).getAccounts()).extracting(FinancialAccount::getId).contains(account.getId());
    }

    @Test
    @Transactional
    void patchBudgetNullAccountsClearsAccounts() throws Exception {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account = em.merge(account);
        em.flush();

        budget.addAccounts(account);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putNull("accounts");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        assertThat(getPersistedBudget(budget).getAccounts()).isEmpty();
    }

    @Test
    @Transactional
    void patchBudgetNullCategoriesClearsCategories() throws Exception {
        Category category = CategoryResourceIT.createEntity(em);
        category = em.merge(category);
        em.flush();

        budget.addCategories(category);
        insertedBudget = budgetRepository.saveAndFlush(budget);

        ObjectNode patchJson = om.createObjectNode();
        patchJson.putNull("categories");

        restBudgetMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, budget.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchJson))
            )
            .andExpect(status().isOk());

        assertThat(getPersistedBudget(budget).getCategories()).isEmpty();
    }

    protected long getRepositoryCount() {
        return budgetRepository.count();
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

    protected Budget getPersistedBudget(Budget budget) {
        return budgetRepository.findById(budget.getId()).orElseThrow();
    }

    protected void assertPersistedBudgetToMatchAllProperties(Budget expectedBudget) {
        assertBudgetAllPropertiesEquals(expectedBudget, getPersistedBudget(expectedBudget));
    }

    protected void assertPersistedBudgetToMatchUpdatableProperties(Budget expectedBudget) {
        assertBudgetAllUpdatablePropertiesEquals(expectedBudget, getPersistedBudget(expectedBudget));
    }
}
