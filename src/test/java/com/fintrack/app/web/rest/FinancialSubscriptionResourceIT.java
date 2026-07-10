package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.FinancialSubscriptionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.RecurrenceUnit;
import com.fintrack.app.domain.enumeration.SubscriptionStatus;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.FinancialSubscriptionService;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
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
 * Integration tests for the {@link FinancialSubscriptionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class FinancialSubscriptionResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final SubscriptionStatus DEFAULT_STATUS = SubscriptionStatus.ACTIVE;
    private static final SubscriptionStatus UPDATED_STATUS = SubscriptionStatus.PAUSED;

    private static final BigDecimal DEFAULT_EXPECTED_AMOUNT = new BigDecimal(0);
    private static final BigDecimal UPDATED_EXPECTED_AMOUNT = new BigDecimal(1);
    private static final BigDecimal SMALLER_EXPECTED_AMOUNT = new BigDecimal(0 - 1);

    private static final BigDecimal DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE = new BigDecimal(0);
    private static final BigDecimal UPDATED_AMOUNT_TOLERANCE_PERCENTAGE = new BigDecimal(1);
    private static final BigDecimal SMALLER_AMOUNT_TOLERANCE_PERCENTAGE = new BigDecimal(0 - 1);

    private static final CurrencyCode DEFAULT_CURRENCY = CurrencyCode.MXN;
    private static final CurrencyCode UPDATED_CURRENCY = CurrencyCode.USD;

    private static final RecurrenceUnit DEFAULT_RECURRENCE_UNIT = RecurrenceUnit.DAY;
    private static final RecurrenceUnit UPDATED_RECURRENCE_UNIT = RecurrenceUnit.WEEK;

    private static final Integer DEFAULT_INTERVAL_COUNT = 1;
    private static final Integer UPDATED_INTERVAL_COUNT = 2;
    private static final Integer SMALLER_INTERVAL_COUNT = 1 - 1;

    private static final LocalDate DEFAULT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_START_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_START_DATE = LocalDate.ofEpochDay(-1L);

    private static final LocalDate DEFAULT_NEXT_EXPECTED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_NEXT_EXPECTED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_NEXT_EXPECTED_DATE = LocalDate.ofEpochDay(-1L);

    private static final LocalDate DEFAULT_END_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_END_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate SMALLER_END_DATE = LocalDate.ofEpochDay(-1L);

    private static final Boolean DEFAULT_AUTOMATIC_PAYMENT = false;
    private static final Boolean UPDATED_AUTOMATIC_PAYMENT = true;

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/financial-subscriptions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepositoryMock;

    @Autowired
    private FinancialSubscriptionMapper financialSubscriptionMapper;

    @Mock
    private FinancialSubscriptionService financialSubscriptionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restFinancialSubscriptionMockMvc;

    private FinancialSubscription financialSubscription;

    private FinancialSubscription insertedFinancialSubscription;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialSubscription createEntity(EntityManager em) {
        FinancialSubscription financialSubscription = new FinancialSubscription()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .status(DEFAULT_STATUS)
            .expectedAmount(DEFAULT_EXPECTED_AMOUNT)
            .amountTolerancePercentage(DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE)
            .currency(DEFAULT_CURRENCY)
            .recurrenceUnit(DEFAULT_RECURRENCE_UNIT)
            .intervalCount(DEFAULT_INTERVAL_COUNT)
            .startDate(DEFAULT_START_DATE)
            .nextExpectedDate(DEFAULT_NEXT_EXPECTED_DATE)
            .endDate(DEFAULT_END_DATE)
            .automaticPayment(DEFAULT_AUTOMATIC_PAYMENT)
            .notes(DEFAULT_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        financialSubscription.setUser(getCurrentMockUser(em));
        return financialSubscription;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FinancialSubscription createUpdatedEntity(EntityManager em) {
        FinancialSubscription updatedFinancialSubscription = new FinancialSubscription()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .expectedAmount(UPDATED_EXPECTED_AMOUNT)
            .amountTolerancePercentage(UPDATED_AMOUNT_TOLERANCE_PERCENTAGE)
            .currency(UPDATED_CURRENCY)
            .recurrenceUnit(UPDATED_RECURRENCE_UNIT)
            .intervalCount(UPDATED_INTERVAL_COUNT)
            .startDate(UPDATED_START_DATE)
            .nextExpectedDate(UPDATED_NEXT_EXPECTED_DATE)
            .endDate(UPDATED_END_DATE)
            .automaticPayment(UPDATED_AUTOMATIC_PAYMENT)
            .notes(UPDATED_NOTES)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        updatedFinancialSubscription.setUser(getCurrentMockUser(em));
        return updatedFinancialSubscription;
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
        financialSubscription = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedFinancialSubscription != null) {
            financialSubscriptionRepository.delete(insertedFinancialSubscription);
            insertedFinancialSubscription = null;
        }
    }

    @Test
    @Transactional
    void createFinancialSubscription() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        var returnedFinancialSubscriptionDTO = om.readValue(
            restFinancialSubscriptionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialSubscriptionDTO.class
        );

        // Validate the FinancialSubscription in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedFinancialSubscription = financialSubscriptionMapper.toEntity(returnedFinancialSubscriptionDTO);
        assertFinancialSubscriptionUpdatableFieldsEquals(
            returnedFinancialSubscription,
            getPersistedFinancialSubscription(returnedFinancialSubscription)
        );

        insertedFinancialSubscription = returnedFinancialSubscription;
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithExistingId() throws Exception {
        // Create the FinancialSubscription with an existing ID
        financialSubscription.setId(1L);
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setName(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setStatus(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setCurrency(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkRecurrenceUnitIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setRecurrenceUnit(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkIntervalCountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setIntervalCount(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStartDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setStartDate(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAutomaticPaymentIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setAutomaticPayment(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setCreatedAt(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        financialSubscription.setUpdatedAt(null);

        // Create the FinancialSubscription, which fails.
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptions() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialSubscription.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].expectedAmount").value(hasItem(sameNumber(DEFAULT_EXPECTED_AMOUNT))))
            .andExpect(jsonPath("$.[*].amountTolerancePercentage").value(hasItem(sameNumber(DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].recurrenceUnit").value(hasItem(DEFAULT_RECURRENCE_UNIT.toString())))
            .andExpect(jsonPath("$.[*].intervalCount").value(hasItem(DEFAULT_INTERVAL_COUNT)))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].nextExpectedDate").value(hasItem(DEFAULT_NEXT_EXPECTED_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].automaticPayment").value(hasItem(DEFAULT_AUTOMATIC_PAYMENT)))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialSubscriptionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(financialSubscriptionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialSubscriptionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(financialSubscriptionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllFinancialSubscriptionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(financialSubscriptionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restFinancialSubscriptionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(financialSubscriptionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getFinancialSubscription() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get the financialSubscription
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL_ID, financialSubscription.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(financialSubscription.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.expectedAmount").value(sameNumber(DEFAULT_EXPECTED_AMOUNT)))
            .andExpect(jsonPath("$.amountTolerancePercentage").value(sameNumber(DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.recurrenceUnit").value(DEFAULT_RECURRENCE_UNIT.toString()))
            .andExpect(jsonPath("$.intervalCount").value(DEFAULT_INTERVAL_COUNT))
            .andExpect(jsonPath("$.startDate").value(DEFAULT_START_DATE.toString()))
            .andExpect(jsonPath("$.nextExpectedDate").value(DEFAULT_NEXT_EXPECTED_DATE.toString()))
            .andExpect(jsonPath("$.endDate").value(DEFAULT_END_DATE.toString()))
            .andExpect(jsonPath("$.automaticPayment").value(DEFAULT_AUTOMATIC_PAYMENT))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getFinancialSubscriptionsByIdFiltering() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        Long id = financialSubscription.getId();

        defaultFinancialSubscriptionFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultFinancialSubscriptionFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultFinancialSubscriptionFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where name equals to
        defaultFinancialSubscriptionFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where name in
        defaultFinancialSubscriptionFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where name is not null
        defaultFinancialSubscriptionFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where name contains
        defaultFinancialSubscriptionFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where name does not contain
        defaultFinancialSubscriptionFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where description equals to
        defaultFinancialSubscriptionFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where description in
        defaultFinancialSubscriptionFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where description is not null
        defaultFinancialSubscriptionFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where description contains
        defaultFinancialSubscriptionFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where description does not contain
        defaultFinancialSubscriptionFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where status equals to
        defaultFinancialSubscriptionFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where status in
        defaultFinancialSubscriptionFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where status is not null
        defaultFinancialSubscriptionFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount equals to
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.equals=" + DEFAULT_EXPECTED_AMOUNT,
            "expectedAmount.equals=" + UPDATED_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount in
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.in=" + DEFAULT_EXPECTED_AMOUNT + "," + UPDATED_EXPECTED_AMOUNT,
            "expectedAmount.in=" + UPDATED_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount is not null
        defaultFinancialSubscriptionFiltering("expectedAmount.specified=true", "expectedAmount.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.greaterThanOrEqual=" + DEFAULT_EXPECTED_AMOUNT,
            "expectedAmount.greaterThanOrEqual=" + UPDATED_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount is less than or equal to
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.lessThanOrEqual=" + DEFAULT_EXPECTED_AMOUNT,
            "expectedAmount.lessThanOrEqual=" + SMALLER_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount is less than
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.lessThan=" + UPDATED_EXPECTED_AMOUNT,
            "expectedAmount.lessThan=" + DEFAULT_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByExpectedAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where expectedAmount is greater than
        defaultFinancialSubscriptionFiltering(
            "expectedAmount.greaterThan=" + SMALLER_EXPECTED_AMOUNT,
            "expectedAmount.greaterThan=" + DEFAULT_EXPECTED_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage equals to
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.equals=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE,
            "amountTolerancePercentage.equals=" + UPDATED_AMOUNT_TOLERANCE_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage in
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.in=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE + "," + UPDATED_AMOUNT_TOLERANCE_PERCENTAGE,
            "amountTolerancePercentage.in=" + UPDATED_AMOUNT_TOLERANCE_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage is not null
        defaultFinancialSubscriptionFiltering("amountTolerancePercentage.specified=true", "amountTolerancePercentage.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.greaterThanOrEqual=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE,
            "amountTolerancePercentage.greaterThanOrEqual=" + (DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE.add(BigDecimal.ONE))
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage is less than or equal to
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.lessThanOrEqual=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE,
            "amountTolerancePercentage.lessThanOrEqual=" + SMALLER_AMOUNT_TOLERANCE_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage is less than
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.lessThan=" + (DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE.add(BigDecimal.ONE)),
            "amountTolerancePercentage.lessThan=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAmountTolerancePercentageIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where amountTolerancePercentage is greater than
        defaultFinancialSubscriptionFiltering(
            "amountTolerancePercentage.greaterThan=" + SMALLER_AMOUNT_TOLERANCE_PERCENTAGE,
            "amountTolerancePercentage.greaterThan=" + DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where currency equals to
        defaultFinancialSubscriptionFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where currency in
        defaultFinancialSubscriptionFiltering(
            "currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY,
            "currency.in=" + UPDATED_CURRENCY
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where currency is not null
        defaultFinancialSubscriptionFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByRecurrenceUnitIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where recurrenceUnit equals to
        defaultFinancialSubscriptionFiltering(
            "recurrenceUnit.equals=" + DEFAULT_RECURRENCE_UNIT,
            "recurrenceUnit.equals=" + UPDATED_RECURRENCE_UNIT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByRecurrenceUnitIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where recurrenceUnit in
        defaultFinancialSubscriptionFiltering(
            "recurrenceUnit.in=" + DEFAULT_RECURRENCE_UNIT + "," + UPDATED_RECURRENCE_UNIT,
            "recurrenceUnit.in=" + UPDATED_RECURRENCE_UNIT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByRecurrenceUnitIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where recurrenceUnit is not null
        defaultFinancialSubscriptionFiltering("recurrenceUnit.specified=true", "recurrenceUnit.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount equals to
        defaultFinancialSubscriptionFiltering(
            "intervalCount.equals=" + DEFAULT_INTERVAL_COUNT,
            "intervalCount.equals=" + UPDATED_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount in
        defaultFinancialSubscriptionFiltering(
            "intervalCount.in=" + DEFAULT_INTERVAL_COUNT + "," + UPDATED_INTERVAL_COUNT,
            "intervalCount.in=" + UPDATED_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount is not null
        defaultFinancialSubscriptionFiltering("intervalCount.specified=true", "intervalCount.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "intervalCount.greaterThanOrEqual=" + DEFAULT_INTERVAL_COUNT,
            "intervalCount.greaterThanOrEqual=" + UPDATED_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount is less than or equal to
        defaultFinancialSubscriptionFiltering(
            "intervalCount.lessThanOrEqual=" + DEFAULT_INTERVAL_COUNT,
            "intervalCount.lessThanOrEqual=" + SMALLER_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount is less than
        defaultFinancialSubscriptionFiltering(
            "intervalCount.lessThan=" + UPDATED_INTERVAL_COUNT,
            "intervalCount.lessThan=" + DEFAULT_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByIntervalCountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where intervalCount is greater than
        defaultFinancialSubscriptionFiltering(
            "intervalCount.greaterThan=" + SMALLER_INTERVAL_COUNT,
            "intervalCount.greaterThan=" + DEFAULT_INTERVAL_COUNT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate equals to
        defaultFinancialSubscriptionFiltering("startDate.equals=" + DEFAULT_START_DATE, "startDate.equals=" + UPDATED_START_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate in
        defaultFinancialSubscriptionFiltering(
            "startDate.in=" + DEFAULT_START_DATE + "," + UPDATED_START_DATE,
            "startDate.in=" + UPDATED_START_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate is not null
        defaultFinancialSubscriptionFiltering("startDate.specified=true", "startDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "startDate.greaterThanOrEqual=" + DEFAULT_START_DATE,
            "startDate.greaterThanOrEqual=" + UPDATED_START_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate is less than or equal to
        defaultFinancialSubscriptionFiltering(
            "startDate.lessThanOrEqual=" + DEFAULT_START_DATE,
            "startDate.lessThanOrEqual=" + SMALLER_START_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate is less than
        defaultFinancialSubscriptionFiltering("startDate.lessThan=" + UPDATED_START_DATE, "startDate.lessThan=" + DEFAULT_START_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByStartDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where startDate is greater than
        defaultFinancialSubscriptionFiltering("startDate.greaterThan=" + SMALLER_START_DATE, "startDate.greaterThan=" + DEFAULT_START_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate equals to
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.equals=" + DEFAULT_NEXT_EXPECTED_DATE,
            "nextExpectedDate.equals=" + UPDATED_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate in
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.in=" + DEFAULT_NEXT_EXPECTED_DATE + "," + UPDATED_NEXT_EXPECTED_DATE,
            "nextExpectedDate.in=" + UPDATED_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate is not null
        defaultFinancialSubscriptionFiltering("nextExpectedDate.specified=true", "nextExpectedDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.greaterThanOrEqual=" + DEFAULT_NEXT_EXPECTED_DATE,
            "nextExpectedDate.greaterThanOrEqual=" + UPDATED_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate is less than or equal to
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.lessThanOrEqual=" + DEFAULT_NEXT_EXPECTED_DATE,
            "nextExpectedDate.lessThanOrEqual=" + SMALLER_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate is less than
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.lessThan=" + UPDATED_NEXT_EXPECTED_DATE,
            "nextExpectedDate.lessThan=" + DEFAULT_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNextExpectedDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where nextExpectedDate is greater than
        defaultFinancialSubscriptionFiltering(
            "nextExpectedDate.greaterThan=" + SMALLER_NEXT_EXPECTED_DATE,
            "nextExpectedDate.greaterThan=" + DEFAULT_NEXT_EXPECTED_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate equals to
        defaultFinancialSubscriptionFiltering("endDate.equals=" + DEFAULT_END_DATE, "endDate.equals=" + UPDATED_END_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate in
        defaultFinancialSubscriptionFiltering("endDate.in=" + DEFAULT_END_DATE + "," + UPDATED_END_DATE, "endDate.in=" + UPDATED_END_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate is not null
        defaultFinancialSubscriptionFiltering("endDate.specified=true", "endDate.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate is greater than or equal to
        defaultFinancialSubscriptionFiltering(
            "endDate.greaterThanOrEqual=" + DEFAULT_END_DATE,
            "endDate.greaterThanOrEqual=" + UPDATED_END_DATE
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate is less than or equal to
        defaultFinancialSubscriptionFiltering("endDate.lessThanOrEqual=" + DEFAULT_END_DATE, "endDate.lessThanOrEqual=" + SMALLER_END_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate is less than
        defaultFinancialSubscriptionFiltering("endDate.lessThan=" + UPDATED_END_DATE, "endDate.lessThan=" + DEFAULT_END_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByEndDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where endDate is greater than
        defaultFinancialSubscriptionFiltering("endDate.greaterThan=" + SMALLER_END_DATE, "endDate.greaterThan=" + DEFAULT_END_DATE);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAutomaticPaymentIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where automaticPayment equals to
        defaultFinancialSubscriptionFiltering(
            "automaticPayment.equals=" + DEFAULT_AUTOMATIC_PAYMENT,
            "automaticPayment.equals=" + UPDATED_AUTOMATIC_PAYMENT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAutomaticPaymentIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where automaticPayment in
        defaultFinancialSubscriptionFiltering(
            "automaticPayment.in=" + DEFAULT_AUTOMATIC_PAYMENT + "," + UPDATED_AUTOMATIC_PAYMENT,
            "automaticPayment.in=" + UPDATED_AUTOMATIC_PAYMENT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAutomaticPaymentIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where automaticPayment is not null
        defaultFinancialSubscriptionFiltering("automaticPayment.specified=true", "automaticPayment.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNotesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where notes equals to
        defaultFinancialSubscriptionFiltering("notes.equals=" + DEFAULT_NOTES, "notes.equals=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNotesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where notes in
        defaultFinancialSubscriptionFiltering("notes.in=" + DEFAULT_NOTES + "," + UPDATED_NOTES, "notes.in=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNotesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where notes is not null
        defaultFinancialSubscriptionFiltering("notes.specified=true", "notes.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNotesContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where notes contains
        defaultFinancialSubscriptionFiltering("notes.contains=" + DEFAULT_NOTES, "notes.contains=" + UPDATED_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByNotesNotContainsSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where notes does not contain
        defaultFinancialSubscriptionFiltering("notes.doesNotContain=" + UPDATED_NOTES, "notes.doesNotContain=" + DEFAULT_NOTES);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where createdAt equals to
        defaultFinancialSubscriptionFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where createdAt in
        defaultFinancialSubscriptionFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where createdAt is not null
        defaultFinancialSubscriptionFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where updatedAt equals to
        defaultFinancialSubscriptionFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where updatedAt in
        defaultFinancialSubscriptionFiltering(
            "updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT,
            "updatedAt.in=" + UPDATED_UPDATED_AT
        );
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        // Get all the financialSubscriptionList where updatedAt is not null
        defaultFinancialSubscriptionFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByUserIsEqualToSomething() throws Exception {
        User user = getCurrentMockUser(em);
        financialSubscription.setUser(user);
        financialSubscriptionRepository.saveAndFlush(financialSubscription);
        Long userId = user.getId();
        // Get all the financialSubscriptionList where user equals to userId
        defaultFinancialSubscriptionShouldBeFound("userId.equals=" + userId);

        // Get all the financialSubscriptionList where user equals to (userId + 1)
        defaultFinancialSubscriptionShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByAccountIsEqualToSomething() throws Exception {
        FinancialAccount account;
        if (TestUtil.findAll(em, FinancialAccount.class).isEmpty()) {
            financialSubscriptionRepository.saveAndFlush(financialSubscription);
            account = FinancialAccountResourceIT.createEntity(em);
        } else {
            account = TestUtil.findAll(em, FinancialAccount.class).get(0);
        }
        em.persist(account);
        em.flush();
        financialSubscription.setAccount(account);
        financialSubscriptionRepository.saveAndFlush(financialSubscription);
        Long accountId = account.getId();
        // Get all the financialSubscriptionList where account equals to accountId
        defaultFinancialSubscriptionShouldBeFound("accountId.equals=" + accountId);

        // Get all the financialSubscriptionList where account equals to (accountId + 1)
        defaultFinancialSubscriptionShouldNotBeFound("accountId.equals=" + (accountId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByCategoryIsEqualToSomething() throws Exception {
        Category category;
        if (TestUtil.findAll(em, Category.class).isEmpty()) {
            financialSubscriptionRepository.saveAndFlush(financialSubscription);
            category = CategoryResourceIT.createEntity(em);
        } else {
            category = TestUtil.findAll(em, Category.class).get(0);
        }
        em.persist(category);
        em.flush();
        financialSubscription.setCategory(category);
        financialSubscriptionRepository.saveAndFlush(financialSubscription);
        Long categoryId = category.getId();
        // Get all the financialSubscriptionList where category equals to categoryId
        defaultFinancialSubscriptionShouldBeFound("categoryId.equals=" + categoryId);

        // Get all the financialSubscriptionList where category equals to (categoryId + 1)
        defaultFinancialSubscriptionShouldNotBeFound("categoryId.equals=" + (categoryId + 1));
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsByTagsIsEqualToSomething() throws Exception {
        Tag tags;
        if (TestUtil.findAll(em, Tag.class).isEmpty()) {
            financialSubscriptionRepository.saveAndFlush(financialSubscription);
            tags = TagResourceIT.createEntity(em);
        } else {
            tags = TestUtil.findAll(em, Tag.class).get(0);
        }
        em.persist(tags);
        em.flush();
        financialSubscription.addTags(tags);
        financialSubscriptionRepository.saveAndFlush(financialSubscription);
        Long tagsId = tags.getId();
        // Get all the financialSubscriptionList where tags equals to tagsId
        defaultFinancialSubscriptionShouldBeFound("tagsId.equals=" + tagsId);

        // Get all the financialSubscriptionList where tags equals to (tagsId + 1)
        defaultFinancialSubscriptionShouldNotBeFound("tagsId.equals=" + (tagsId + 1));
    }

    private void defaultFinancialSubscriptionFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultFinancialSubscriptionShouldBeFound(shouldBeFound);
        defaultFinancialSubscriptionShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultFinancialSubscriptionShouldBeFound(String filter) throws Exception {
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialSubscription.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].expectedAmount").value(hasItem(sameNumber(DEFAULT_EXPECTED_AMOUNT))))
            .andExpect(jsonPath("$.[*].amountTolerancePercentage").value(hasItem(sameNumber(DEFAULT_AMOUNT_TOLERANCE_PERCENTAGE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].recurrenceUnit").value(hasItem(DEFAULT_RECURRENCE_UNIT.toString())))
            .andExpect(jsonPath("$.[*].intervalCount").value(hasItem(DEFAULT_INTERVAL_COUNT)))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].nextExpectedDate").value(hasItem(DEFAULT_NEXT_EXPECTED_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].automaticPayment").value(hasItem(DEFAULT_AUTOMATIC_PAYMENT)))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultFinancialSubscriptionShouldNotBeFound(String filter) throws Exception {
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingFinancialSubscription() throws Exception {
        // Get the financialSubscription
        restFinancialSubscriptionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingFinancialSubscription() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialSubscription
        FinancialSubscription updatedFinancialSubscription = financialSubscriptionRepository
            .findById(financialSubscription.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedFinancialSubscription are not directly saved in db
        em.detach(updatedFinancialSubscription);
        updatedFinancialSubscription
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .expectedAmount(UPDATED_EXPECTED_AMOUNT)
            .amountTolerancePercentage(UPDATED_AMOUNT_TOLERANCE_PERCENTAGE)
            .currency(UPDATED_CURRENCY)
            .recurrenceUnit(UPDATED_RECURRENCE_UNIT)
            .intervalCount(UPDATED_INTERVAL_COUNT)
            .startDate(UPDATED_START_DATE)
            .nextExpectedDate(UPDATED_NEXT_EXPECTED_DATE)
            .endDate(UPDATED_END_DATE)
            .automaticPayment(UPDATED_AUTOMATIC_PAYMENT)
            .notes(UPDATED_NOTES)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(updatedFinancialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isOk());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedFinancialSubscriptionToMatchAllProperties(updatedFinancialSubscription);
    }

    @Test
    @Transactional
    void putNonExistingFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateFinancialSubscriptionWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialSubscription using partial update
        FinancialSubscription partialUpdatedFinancialSubscription = new FinancialSubscription();
        partialUpdatedFinancialSubscription.setId(financialSubscription.getId());

        partialUpdatedFinancialSubscription
            .description(UPDATED_DESCRIPTION)
            .amountTolerancePercentage(UPDATED_AMOUNT_TOLERANCE_PERCENTAGE)
            .currency(UPDATED_CURRENCY)
            .startDate(UPDATED_START_DATE)
            .nextExpectedDate(UPDATED_NEXT_EXPECTED_DATE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFinancialSubscription.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFinancialSubscription))
            )
            .andExpect(status().isOk());

        // Validate the FinancialSubscription in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFinancialSubscriptionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFinancialSubscription, financialSubscription),
            getPersistedFinancialSubscription(financialSubscription)
        );
    }

    @Test
    @Transactional
    void fullUpdateFinancialSubscriptionWithPatch() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the financialSubscription using partial update
        FinancialSubscription partialUpdatedFinancialSubscription = new FinancialSubscription();
        partialUpdatedFinancialSubscription.setId(financialSubscription.getId());

        partialUpdatedFinancialSubscription
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .expectedAmount(UPDATED_EXPECTED_AMOUNT)
            .amountTolerancePercentage(UPDATED_AMOUNT_TOLERANCE_PERCENTAGE)
            .currency(UPDATED_CURRENCY)
            .recurrenceUnit(UPDATED_RECURRENCE_UNIT)
            .intervalCount(UPDATED_INTERVAL_COUNT)
            .startDate(UPDATED_START_DATE)
            .nextExpectedDate(UPDATED_NEXT_EXPECTED_DATE)
            .endDate(UPDATED_END_DATE)
            .automaticPayment(UPDATED_AUTOMATIC_PAYMENT)
            .notes(UPDATED_NOTES)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFinancialSubscription.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFinancialSubscription))
            )
            .andExpect(status().isOk());

        // Validate the FinancialSubscription in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFinancialSubscriptionUpdatableFieldsEquals(
            partialUpdatedFinancialSubscription,
            getPersistedFinancialSubscription(partialUpdatedFinancialSubscription)
        );
    }

    @Test
    @Transactional
    void patchNonExistingFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamFinancialSubscription() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        financialSubscription.setId(longCount.incrementAndGet());

        // Create the FinancialSubscription
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the FinancialSubscription in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteFinancialSubscription() throws Exception {
        // Initialize the database
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the financialSubscription
        restFinancialSubscriptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialSubscription.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createFinancialSubscriptionAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        financialSubscriptionDTO.setUser(otherUserDTO);

        FinancialSubscriptionDTO returnedFinancialSubscriptionDTO = om.readValue(
            restFinancialSubscriptionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialSubscriptionDTO.class
        );

        assertThat(returnedFinancialSubscriptionDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedFinancialSubscription = financialSubscriptionMapper.toEntity(returnedFinancialSubscriptionDTO);
    }

    @Test
    @Transactional
    void getFinancialSubscriptionOwnedByAnotherUserIsNotFound() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        restFinancialSubscriptionMockMvc.perform(get(ENTITY_API_URL_ID, financialSubscription.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllFinancialSubscriptionsDoesNotIncludeAnotherUsersSubscriptions() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        financialSubscriptionRepository.saveAndFlush(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(financialSubscription.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetFinancialSubscriptionOwnedByAnotherUser() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL_ID, financialSubscription.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(financialSubscription.getId().intValue()));
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithoutUserInPayloadSucceeds() throws Exception {
        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        financialSubscriptionDTO.setUser(null);

        FinancialSubscriptionDTO returnedFinancialSubscriptionDTO = om.readValue(
            restFinancialSubscriptionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialSubscriptionDTO.class
        );

        assertThat(returnedFinancialSubscriptionDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedFinancialSubscription = financialSubscriptionMapper.toEntity(returnedFinancialSubscriptionDTO);
    }

    @Test
    @Transactional
    void putFinancialSubscriptionOwnedByAnotherUserIsNotFound() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setName(UPDATED_NAME);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionOwnedByAnotherUserIsNotFound() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        FinancialSubscriptionDTO financialSubscriptionDTO = new FinancialSubscriptionDTO();
        financialSubscriptionDTO.setId(financialSubscription.getId());
        financialSubscriptionDTO.setName(UPDATED_NAME);

        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFinancialSubscriptionOwnedByAnotherUserIsNotFound() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialSubscription.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(financialSubscriptionRepository.existsById(financialSubscription.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateFinancialSubscriptionCannotChangeOwner() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);
        User otherUser = createOtherUser(em);

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        financialSubscriptionDTO.setUser(otherUserDTO);
        financialSubscriptionDTO.setName(UPDATED_NAME);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isOk());

        FinancialSubscription persistedFinancialSubscription = financialSubscriptionRepository
            .findById(financialSubscription.getId())
            .orElseThrow();
        assertThat(persistedFinancialSubscription.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedFinancialSubscription.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionCannotChangeOwner() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);
        User otherUser = createOtherUser(em);

        FinancialSubscriptionDTO financialSubscriptionDTO = new FinancialSubscriptionDTO();
        financialSubscriptionDTO.setId(financialSubscription.getId());
        financialSubscriptionDTO.setName(UPDATED_NAME);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        financialSubscriptionDTO.setUser(otherUserDTO);

        restFinancialSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isOk());

        FinancialSubscription persistedFinancialSubscription = financialSubscriptionRepository
            .findById(financialSubscription.getId())
            .orElseThrow();
        assertThat(persistedFinancialSubscription.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedFinancialSubscription.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void getFinancialSubscriptionCountDoesNotIncludeAnotherUsersSubscriptions() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        financialSubscriptionRepository.saveAndFlush(financialSubscription);

        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "/count"))
            .andExpect(status().isOk())
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllFinancialSubscriptionsIncludingOtherUsers() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);
        FinancialSubscription otherUsersSubscription = createEntity(em);
        otherUsersSubscription.setUser(createOtherUser(em));
        otherUsersSubscription.setName("OTHER_USER_SUBSCRIPTION");
        otherUsersSubscription = financialSubscriptionRepository.saveAndFlush(otherUsersSubscription);

        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(financialSubscription.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersSubscription.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllFinancialSubscriptionsIncludingOtherUsers() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);
        FinancialSubscription otherUsersSubscription = createEntity(em);
        otherUsersSubscription.setUser(createOtherUser(em));
        financialSubscriptionRepository.saveAndFlush(otherUsersSubscription);

        restFinancialSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "/count"))
            .andExpect(status().isOk())
            .andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateFinancialSubscriptionOwnedByAnotherUser() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setName(UPDATED_NAME);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteFinancialSubscriptionOwnedByAnotherUser() throws Exception {
        financialSubscription.setUser(createOtherUser(em));
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restFinancialSubscriptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, financialSubscription.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedFinancialSubscription = null;
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithAccountOwnedByAnotherUserFails() throws Exception {
        FinancialAccount otherUsersAccount = FinancialAccountResourceIT.createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        otherUsersAccount = em.merge(otherUsersAccount);
        em.flush();

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        financialSubscriptionDTO.setAccount(accountDTO);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithCategoryOwnedByAnotherUserFails() throws Exception {
        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(otherUsersCategory.getId());
        financialSubscriptionDTO.setCategory(categoryDTO);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithTagOwnedByAnotherUserFails() throws Exception {
        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        Set<TagDTO> tags = new HashSet<>();
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(otherUsersTag.getId());
        tags.add(tagDTO);
        financialSubscriptionDTO.setTags(tags);

        restFinancialSubscriptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFinancialSubscriptionWithAccessibleAccountSucceeds() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        em.flush();

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        financialSubscriptionDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(ownAccount.getId());
        financialSubscriptionDTO.setAccount(accountDTO);

        FinancialSubscriptionDTO returnedFinancialSubscriptionDTO = om.readValue(
            restFinancialSubscriptionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(financialSubscriptionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FinancialSubscriptionDTO.class
        );

        assertThat(returnedFinancialSubscriptionDTO.getAccount().getId()).isEqualTo(ownAccount.getId());
        insertedFinancialSubscription = financialSubscriptionMapper.toEntity(returnedFinancialSubscriptionDTO);
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionWithoutAccountFieldPreservesExistingAccount() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        em.flush();

        financialSubscription.setAccount(ownAccount);
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        String patchJson = "{\"id\":" + financialSubscription.getId() + ",\"name\":\"" + UPDATED_NAME + "\"}";

        restFinancialSubscriptionMockMvc
            .perform(patch(ENTITY_API_URL_ID, financialSubscription.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id").value(ownAccount.getId().intValue()));

        FinancialSubscription persistedFinancialSubscription = getPersistedFinancialSubscription(financialSubscription);
        assertThat(persistedFinancialSubscription.getAccount().getId()).isEqualTo(ownAccount.getId());
        assertThat(persistedFinancialSubscription.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionWithNullAccountClearsAccount() throws Exception {
        FinancialAccount ownAccount = FinancialAccountResourceIT.createEntity(em);
        ownAccount = em.merge(ownAccount);
        em.flush();

        financialSubscription.setAccount(ownAccount);
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        String patchJson = "{\"id\":" + financialSubscription.getId() + ",\"account\":null}";

        restFinancialSubscriptionMockMvc
            .perform(patch(ENTITY_API_URL_ID, financialSubscription.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account").doesNotExist());

        assertThat(getPersistedFinancialSubscription(financialSubscription).getAccount()).isNull();
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionWithEmptyTagsClearsTags() throws Exception {
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        Set<Tag> tags = new HashSet<>();
        tags.add(ownTag);
        financialSubscription.setTags(tags);
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        String patchJson = "{\"id\":" + financialSubscription.getId() + ",\"tags\":[]}";

        restFinancialSubscriptionMockMvc
            .perform(patch(ENTITY_API_URL_ID, financialSubscription.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isEmpty());

        assertThat(getPersistedFinancialSubscription(financialSubscription).getTags()).isEmpty();
    }

    @Test
    @Transactional
    void updateFinancialSubscriptionWithForeignCategoryOrAccountFails() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        FinancialAccount otherUsersAccount = FinancialAccountResourceIT.createEntity(em);
        otherUsersAccount.setUser(createOtherUser(em));
        otherUsersAccount = em.merge(otherUsersAccount);
        em.flush();

        FinancialSubscriptionDTO financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        financialSubscriptionDTO.setAccount(accountDTO);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());

        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        financialSubscriptionDTO = financialSubscriptionMapper.toDto(financialSubscription);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(otherUsersCategory.getId());
        financialSubscriptionDTO.setCategory(categoryDTO);

        restFinancialSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, financialSubscriptionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(financialSubscriptionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFinancialSubscriptionWithForeignTagFails() throws Exception {
        insertedFinancialSubscription = financialSubscriptionRepository.saveAndFlush(financialSubscription);

        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        String patchJson = "{\"id\":" + financialSubscription.getId() + ",\"tags\":[{\"id\":" + otherUsersTag.getId() + "}]}";

        restFinancialSubscriptionMockMvc
            .perform(patch(ENTITY_API_URL_ID, financialSubscription.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    protected long getRepositoryCount() {
        return financialSubscriptionRepository.count();
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

    protected FinancialSubscription getPersistedFinancialSubscription(FinancialSubscription financialSubscription) {
        return financialSubscriptionRepository.findById(financialSubscription.getId()).orElseThrow();
    }

    protected void assertPersistedFinancialSubscriptionToMatchAllProperties(FinancialSubscription expectedFinancialSubscription) {
        assertFinancialSubscriptionAllPropertiesEquals(
            expectedFinancialSubscription,
            getPersistedFinancialSubscription(expectedFinancialSubscription)
        );
    }

    protected void assertPersistedFinancialSubscriptionToMatchUpdatableProperties(FinancialSubscription expectedFinancialSubscription) {
        assertFinancialSubscriptionAllUpdatablePropertiesEquals(
            expectedFinancialSubscription,
            getPersistedFinancialSubscription(expectedFinancialSubscription)
        );
    }
}
