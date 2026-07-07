package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TransactionRuleAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.TransactionRuleService;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
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
 * Integration tests for the {@link TransactionRuleResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionRuleResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final Integer DEFAULT_PRIORITY = 0;
    private static final Integer UPDATED_PRIORITY = 1;
    private static final Integer SMALLER_PRIORITY = 0 - 1;

    private static final RuleConditionLogic DEFAULT_CONDITION_LOGIC = RuleConditionLogic.ALL;
    private static final RuleConditionLogic UPDATED_CONDITION_LOGIC = RuleConditionLogic.ANY;

    private static final String DEFAULT_RESULTING_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_RESULTING_DESCRIPTION = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/transaction-rules";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionRuleRepository transactionRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private TransactionRuleRepository transactionRuleRepositoryMock;

    @Autowired
    private TransactionRuleMapper transactionRuleMapper;

    @Mock
    private TransactionRuleService transactionRuleServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionRuleMockMvc;

    private TransactionRule transactionRule;

    private TransactionRule insertedTransactionRule;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRule createEntity(EntityManager em) {
        TransactionRule transactionRule = new TransactionRule()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .priority(DEFAULT_PRIORITY)
            .conditionLogic(DEFAULT_CONDITION_LOGIC)
            .resultingDescription(DEFAULT_RESULTING_DESCRIPTION)
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        // Add required entity
        User user = UserResourceIT.createEntity();
        em.persist(user);
        em.flush();
        transactionRule.setUser(user);
        return transactionRule;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRule createUpdatedEntity(EntityManager em) {
        TransactionRule updatedTransactionRule = new TransactionRule()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .priority(UPDATED_PRIORITY)
            .conditionLogic(UPDATED_CONDITION_LOGIC)
            .resultingDescription(UPDATED_RESULTING_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        // Add required entity
        User user = UserResourceIT.createEntity();
        em.persist(user);
        em.flush();
        updatedTransactionRule.setUser(user);
        return updatedTransactionRule;
    }

    @BeforeEach
    void initTest() {
        transactionRule = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionRule != null) {
            transactionRuleRepository.delete(insertedTransactionRule);
            insertedTransactionRule = null;
        }
    }

    @Test
    @Transactional
    void createTransactionRule() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        var returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        // Validate the TransactionRule in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
        assertTransactionRuleUpdatableFieldsEquals(returnedTransactionRule, getPersistedTransactionRule(returnedTransactionRule));

        insertedTransactionRule = returnedTransactionRule;
    }

    @Test
    @Transactional
    void createTransactionRuleWithExistingId() throws Exception {
        // Create the TransactionRule with an existing ID
        transactionRule.setId(1L);
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setName(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPriorityIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setPriority(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkConditionLogicIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setConditionLogic(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setActive(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setCreatedAt(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRule.setUpdatedAt(null);

        // Create the TransactionRule, which fails.
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllTransactionRules() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRule.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].priority").value(hasItem(DEFAULT_PRIORITY)))
            .andExpect(jsonPath("$.[*].conditionLogic").value(hasItem(DEFAULT_CONDITION_LOGIC.toString())))
            .andExpect(jsonPath("$.[*].resultingDescription").value(hasItem(DEFAULT_RESULTING_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRulesWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionRuleServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionRuleServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRulesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionRuleServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionRuleRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionRule() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get the transactionRule
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionRule.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.priority").value(DEFAULT_PRIORITY))
            .andExpect(jsonPath("$.conditionLogic").value(DEFAULT_CONDITION_LOGIC.toString()))
            .andExpect(jsonPath("$.resultingDescription").value(DEFAULT_RESULTING_DESCRIPTION))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getTransactionRulesByIdFiltering() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        Long id = transactionRule.getId();

        defaultTransactionRuleFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTransactionRuleFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTransactionRuleFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where name equals to
        defaultTransactionRuleFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where name in
        defaultTransactionRuleFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where name is not null
        defaultTransactionRuleFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where name contains
        defaultTransactionRuleFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where name does not contain
        defaultTransactionRuleFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where description equals to
        defaultTransactionRuleFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where description in
        defaultTransactionRuleFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where description is not null
        defaultTransactionRuleFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where description contains
        defaultTransactionRuleFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where description does not contain
        defaultTransactionRuleFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority equals to
        defaultTransactionRuleFiltering("priority.equals=" + DEFAULT_PRIORITY, "priority.equals=" + UPDATED_PRIORITY);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority in
        defaultTransactionRuleFiltering("priority.in=" + DEFAULT_PRIORITY + "," + UPDATED_PRIORITY, "priority.in=" + UPDATED_PRIORITY);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority is not null
        defaultTransactionRuleFiltering("priority.specified=true", "priority.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority is greater than or equal to
        defaultTransactionRuleFiltering(
            "priority.greaterThanOrEqual=" + DEFAULT_PRIORITY,
            "priority.greaterThanOrEqual=" + UPDATED_PRIORITY
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority is less than or equal to
        defaultTransactionRuleFiltering("priority.lessThanOrEqual=" + DEFAULT_PRIORITY, "priority.lessThanOrEqual=" + SMALLER_PRIORITY);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority is less than
        defaultTransactionRuleFiltering("priority.lessThan=" + UPDATED_PRIORITY, "priority.lessThan=" + DEFAULT_PRIORITY);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByPriorityIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where priority is greater than
        defaultTransactionRuleFiltering("priority.greaterThan=" + SMALLER_PRIORITY, "priority.greaterThan=" + DEFAULT_PRIORITY);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByConditionLogicIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where conditionLogic equals to
        defaultTransactionRuleFiltering(
            "conditionLogic.equals=" + DEFAULT_CONDITION_LOGIC,
            "conditionLogic.equals=" + UPDATED_CONDITION_LOGIC
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByConditionLogicIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where conditionLogic in
        defaultTransactionRuleFiltering(
            "conditionLogic.in=" + DEFAULT_CONDITION_LOGIC + "," + UPDATED_CONDITION_LOGIC,
            "conditionLogic.in=" + UPDATED_CONDITION_LOGIC
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByConditionLogicIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where conditionLogic is not null
        defaultTransactionRuleFiltering("conditionLogic.specified=true", "conditionLogic.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where resultingDescription equals to
        defaultTransactionRuleFiltering(
            "resultingDescription.equals=" + DEFAULT_RESULTING_DESCRIPTION,
            "resultingDescription.equals=" + UPDATED_RESULTING_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where resultingDescription in
        defaultTransactionRuleFiltering(
            "resultingDescription.in=" + DEFAULT_RESULTING_DESCRIPTION + "," + UPDATED_RESULTING_DESCRIPTION,
            "resultingDescription.in=" + UPDATED_RESULTING_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where resultingDescription is not null
        defaultTransactionRuleFiltering("resultingDescription.specified=true", "resultingDescription.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where resultingDescription contains
        defaultTransactionRuleFiltering(
            "resultingDescription.contains=" + DEFAULT_RESULTING_DESCRIPTION,
            "resultingDescription.contains=" + UPDATED_RESULTING_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where resultingDescription does not contain
        defaultTransactionRuleFiltering(
            "resultingDescription.doesNotContain=" + UPDATED_RESULTING_DESCRIPTION,
            "resultingDescription.doesNotContain=" + DEFAULT_RESULTING_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByActiveIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where active equals to
        defaultTransactionRuleFiltering("active.equals=" + DEFAULT_ACTIVE, "active.equals=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByActiveIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where active in
        defaultTransactionRuleFiltering("active.in=" + DEFAULT_ACTIVE + "," + UPDATED_ACTIVE, "active.in=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByActiveIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where active is not null
        defaultTransactionRuleFiltering("active.specified=true", "active.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where createdAt equals to
        defaultTransactionRuleFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where createdAt in
        defaultTransactionRuleFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where createdAt is not null
        defaultTransactionRuleFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where updatedAt equals to
        defaultTransactionRuleFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionRulesByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where updatedAt in
        defaultTransactionRuleFiltering(
            "updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT,
            "updatedAt.in=" + UPDATED_UPDATED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRulesByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        // Get all the transactionRuleList where updatedAt is not null
        defaultTransactionRuleFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRulesByUserIsEqualToSomething() throws Exception {
        User user;
        if (TestUtil.findAll(em, User.class).isEmpty()) {
            transactionRuleRepository.saveAndFlush(transactionRule);
            user = UserResourceIT.createEntity();
        } else {
            user = TestUtil.findAll(em, User.class).get(0);
        }
        em.persist(user);
        em.flush();
        transactionRule.setUser(user);
        transactionRuleRepository.saveAndFlush(transactionRule);
        Long userId = user.getId();
        // Get all the transactionRuleList where user equals to userId
        defaultTransactionRuleShouldBeFound("userId.equals=" + userId);

        // Get all the transactionRuleList where user equals to (userId + 1)
        defaultTransactionRuleShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingCategoryIsEqualToSomething() throws Exception {
        Category resultingCategory;
        if (TestUtil.findAll(em, Category.class).isEmpty()) {
            transactionRuleRepository.saveAndFlush(transactionRule);
            resultingCategory = CategoryResourceIT.createEntity(em);
        } else {
            resultingCategory = TestUtil.findAll(em, Category.class).get(0);
        }
        em.persist(resultingCategory);
        em.flush();
        transactionRule.setResultingCategory(resultingCategory);
        transactionRuleRepository.saveAndFlush(transactionRule);
        Long resultingCategoryId = resultingCategory.getId();
        // Get all the transactionRuleList where resultingCategory equals to resultingCategoryId
        defaultTransactionRuleShouldBeFound("resultingCategoryId.equals=" + resultingCategoryId);

        // Get all the transactionRuleList where resultingCategory equals to (resultingCategoryId + 1)
        defaultTransactionRuleShouldNotBeFound("resultingCategoryId.equals=" + (resultingCategoryId + 1));
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingFinancialSubscriptionIsEqualToSomething() throws Exception {
        FinancialSubscription resultingFinancialSubscription;
        if (TestUtil.findAll(em, FinancialSubscription.class).isEmpty()) {
            transactionRuleRepository.saveAndFlush(transactionRule);
            resultingFinancialSubscription = FinancialSubscriptionResourceIT.createEntity(em);
        } else {
            resultingFinancialSubscription = TestUtil.findAll(em, FinancialSubscription.class).get(0);
        }
        em.persist(resultingFinancialSubscription);
        em.flush();
        transactionRule.setResultingFinancialSubscription(resultingFinancialSubscription);
        transactionRuleRepository.saveAndFlush(transactionRule);
        Long resultingFinancialSubscriptionId = resultingFinancialSubscription.getId();
        // Get all the transactionRuleList where resultingFinancialSubscription equals to resultingFinancialSubscriptionId
        defaultTransactionRuleShouldBeFound("resultingFinancialSubscriptionId.equals=" + resultingFinancialSubscriptionId);

        // Get all the transactionRuleList where resultingFinancialSubscription equals to (resultingFinancialSubscriptionId + 1)
        defaultTransactionRuleShouldNotBeFound("resultingFinancialSubscriptionId.equals=" + (resultingFinancialSubscriptionId + 1));
    }

    @Test
    @Transactional
    void getAllTransactionRulesByResultingTagsIsEqualToSomething() throws Exception {
        Tag resultingTags;
        if (TestUtil.findAll(em, Tag.class).isEmpty()) {
            transactionRuleRepository.saveAndFlush(transactionRule);
            resultingTags = TagResourceIT.createEntity(em);
        } else {
            resultingTags = TestUtil.findAll(em, Tag.class).get(0);
        }
        em.persist(resultingTags);
        em.flush();
        transactionRule.addResultingTags(resultingTags);
        transactionRuleRepository.saveAndFlush(transactionRule);
        Long resultingTagsId = resultingTags.getId();
        // Get all the transactionRuleList where resultingTags equals to resultingTagsId
        defaultTransactionRuleShouldBeFound("resultingTagsId.equals=" + resultingTagsId);

        // Get all the transactionRuleList where resultingTags equals to (resultingTagsId + 1)
        defaultTransactionRuleShouldNotBeFound("resultingTagsId.equals=" + (resultingTagsId + 1));
    }

    private void defaultTransactionRuleFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTransactionRuleShouldBeFound(shouldBeFound);
        defaultTransactionRuleShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTransactionRuleShouldBeFound(String filter) throws Exception {
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRule.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].priority").value(hasItem(DEFAULT_PRIORITY)))
            .andExpect(jsonPath("$.[*].conditionLogic").value(hasItem(DEFAULT_CONDITION_LOGIC.toString())))
            .andExpect(jsonPath("$.[*].resultingDescription").value(hasItem(DEFAULT_RESULTING_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTransactionRuleShouldNotBeFound(String filter) throws Exception {
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingTransactionRule() throws Exception {
        // Get the transactionRule
        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionRule() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRule
        TransactionRule updatedTransactionRule = transactionRuleRepository.findById(transactionRule.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionRule are not directly saved in db
        em.detach(updatedTransactionRule);
        updatedTransactionRule
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .priority(UPDATED_PRIORITY)
            .conditionLogic(UPDATED_CONDITION_LOGIC)
            .resultingDescription(UPDATED_RESULTING_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(updatedTransactionRule);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTransactionRuleToMatchAllProperties(updatedTransactionRule);
    }

    @Test
    @Transactional
    void putNonExistingTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionRuleWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRule using partial update
        TransactionRule partialUpdatedTransactionRule = new TransactionRule();
        partialUpdatedTransactionRule.setId(transactionRule.getId());

        partialUpdatedTransactionRule.name(UPDATED_NAME).resultingDescription(UPDATED_RESULTING_DESCRIPTION).updatedAt(UPDATED_UPDATED_AT);

        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRule.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRule))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRule in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionRuleUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionRule, transactionRule),
            getPersistedTransactionRule(transactionRule)
        );
    }

    @Test
    @Transactional
    void fullUpdateTransactionRuleWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRule using partial update
        TransactionRule partialUpdatedTransactionRule = new TransactionRule();
        partialUpdatedTransactionRule.setId(transactionRule.getId());

        partialUpdatedTransactionRule
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .priority(UPDATED_PRIORITY)
            .conditionLogic(UPDATED_CONDITION_LOGIC)
            .resultingDescription(UPDATED_RESULTING_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRule.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRule))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRule in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionRuleUpdatableFieldsEquals(
            partialUpdatedTransactionRule,
            getPersistedTransactionRule(partialUpdatedTransactionRule)
        );
    }

    @Test
    @Transactional
    void patchNonExistingTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionRule() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRule.setId(longCount.incrementAndGet());

        // Create the TransactionRule
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRule in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionRule() throws Exception {
        // Initialize the database
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionRule
        restTransactionRuleMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRule.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return transactionRuleRepository.count();
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

    protected TransactionRule getPersistedTransactionRule(TransactionRule transactionRule) {
        return transactionRuleRepository.findById(transactionRule.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionRuleToMatchAllProperties(TransactionRule expectedTransactionRule) {
        assertTransactionRuleAllPropertiesEquals(expectedTransactionRule, getPersistedTransactionRule(expectedTransactionRule));
    }

    protected void assertPersistedTransactionRuleToMatchUpdatableProperties(TransactionRule expectedTransactionRule) {
        assertTransactionRuleAllUpdatablePropertiesEquals(expectedTransactionRule, getPersistedTransactionRule(expectedTransactionRule));
    }
}
