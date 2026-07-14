package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TransactionRuleAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.TransactionRuleService;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

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
        transactionRule.setUser(getCurrentMockUser(em));
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
        updatedTransactionRule.setUser(getCurrentMockUser(em));
        return updatedTransactionRule;
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

    private TransactionRuleCondition createCondition(TransactionRule rule) {
        return createCondition(rule, 0, "Amazon");
    }

    private TransactionRuleCondition createCondition(TransactionRule rule, int position, String value) {
        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(TransactionRuleField.DESCRIPTION)
            .operator(RuleOperator.CONTAINS)
            .value(value)
            .caseSensitive(false)
            .position(position)
            .transactionRule(rule);
        em.persist(condition);
        em.flush();
        return condition;
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
        TransactionRule persistedTransactionRule = getPersistedTransactionRule(returnedTransactionRule);
        assertThat(persistedTransactionRule.getCreatedAt()).isNotNull();
        assertThat(persistedTransactionRule.getUpdatedAt()).isNotNull();
        assertThat(persistedTransactionRule.getCreatedAt()).isNotEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedTransactionRule.getUpdatedAt()).isNotEqualTo(DEFAULT_UPDATED_AT);
        assertTransactionRuleUpdatableFieldsEquals(returnedTransactionRule, persistedTransactionRule);

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
    void createTransactionRuleWithoutCreatedAtUsesServerTimestamp() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        transactionRule.setCreatedAt(null);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeTest);
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void createTransactionRuleWithoutUpdatedAtUsesServerTimestamp() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        transactionRule.setUpdatedAt(null);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeTest);
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void createTransactionRuleNormalizesTextFields() throws Exception {
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setName("  Trimmed rule  ");
        transactionRuleDTO.setDescription("  Description  ");
        transactionRuleDTO.setResultingDescription("  Result  ");

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        TransactionRule persistedTransactionRule = transactionRuleRepository.findById(returnedTransactionRuleDTO.getId()).orElseThrow();
        assertThat(persistedTransactionRule.getName()).isEqualTo("Trimmed rule");
        assertThat(persistedTransactionRule.getDescription()).isEqualTo("Description");
        assertThat(persistedTransactionRule.getResultingDescription()).isEqualTo("Result");
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void createTransactionRuleWithBlankNameFails() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setName("   ");

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createTransactionRuleWithDuplicateNameForSameOwnerFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        long databaseSizeBeforeTest = getRepositoryCount();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(createEntity(em));
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setName("  " + DEFAULT_NAME.toLowerCase() + "  ");

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createTransactionRuleWithSameNameForDifferentOwnerSucceeds() throws Exception {
        TransactionRule otherUsersRule = createEntity(em);
        otherUsersRule.setUser(createOtherUser(em));
        otherUsersRule.setName(DEFAULT_NAME);
        transactionRuleRepository.saveAndFlush(otherUsersRule);
        long databaseSizeBeforeTest = getRepositoryCount();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setName("  " + DEFAULT_NAME.toLowerCase() + "  ");

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeTest);
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void createTransactionRuleWithNoOutputsFails() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setResultingDescription("   ");
        transactionRuleDTO.setResultingCategory(null);
        transactionRuleDTO.setResultingFinancialSubscription(null);
        transactionRuleDTO.setResultingTags(new HashSet<>());

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createActiveTransactionRuleWithZeroConditionsFails() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setActive(true);

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
        User user = getCurrentMockUser(em);
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
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
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
        TransactionRule persistedTransactionRule = getPersistedTransactionRule(updatedTransactionRule);
        assertThat(persistedTransactionRule.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedTransactionRule.getUpdatedAt()).isNotEqualTo(DEFAULT_UPDATED_AT);
        updatedTransactionRule.setUpdatedAt(persistedTransactionRule.getUpdatedAt());
        assertPersistedTransactionRuleToMatchAllProperties(updatedTransactionRule);
    }

    @Test
    @Transactional
    void putChangingCreatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setCreatedAt(UPDATED_CREATED_AT);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void putNullCreatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setCreatedAt(null);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void putChangingUpdatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setUpdatedAt(UPDATED_UPDATED_AT);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getUpdatedAt()).isEqualTo(DEFAULT_UPDATED_AT);
    }

    @Test
    @Transactional
    void putNullUpdatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setUpdatedAt(null);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getUpdatedAt()).isEqualTo(DEFAULT_UPDATED_AT);
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

        TransactionRule partialUpdatedTransactionRule = new TransactionRule();
        partialUpdatedTransactionRule.setId(transactionRule.getId());
        partialUpdatedTransactionRule.name(UPDATED_NAME).resultingDescription(UPDATED_RESULTING_DESCRIPTION);
        String patchJson =
            "{\"id\":" +
            transactionRule.getId() +
            ",\"name\":\"" +
            UPDATED_NAME +
            "\",\"resultingDescription\":\"" +
            UPDATED_RESULTING_DESCRIPTION +
            "\"}";

        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRule.getId())
                    .contentType("application/merge-patch+json")
                    .content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the TransactionRule in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionRule persistedTransactionRule = getPersistedTransactionRule(transactionRule);
        assertThat(persistedTransactionRule.getUpdatedAt()).isNotEqualTo(DEFAULT_UPDATED_AT);
        partialUpdatedTransactionRule.setUpdatedAt(persistedTransactionRule.getUpdatedAt());
        assertTransactionRuleUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionRule, transactionRule),
            persistedTransactionRule
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
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);

        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRule.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRule))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRule in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionRule persistedTransactionRule = getPersistedTransactionRule(partialUpdatedTransactionRule);
        assertThat(persistedTransactionRule.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedTransactionRule.getUpdatedAt()).isNotEqualTo(DEFAULT_UPDATED_AT);
        partialUpdatedTransactionRule.setUpdatedAt(persistedTransactionRule.getUpdatedAt());
        assertTransactionRuleUpdatableFieldsEquals(partialUpdatedTransactionRule, persistedTransactionRule);
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
    void patchRequiredFieldsWithNullFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String[] fields = { "name", "priority", "conditionLogic", "active", "createdAt", "updatedAt" };
        for (String field : fields) {
            String patchJson = "{\"id\":" + transactionRule.getId() + ",\"" + field + "\":null}";

            restTransactionRuleMockMvc
                .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @Transactional
    void patchChangingCreatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"createdAt\":\"" + UPDATED_CREATED_AT + "\"}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void patchChangingUpdatedAtFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"updatedAt\":\"" + UPDATED_UPDATED_AT + "\"}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedTransactionRule(transactionRule).getUpdatedAt()).isEqualTo(DEFAULT_UPDATED_AT);
    }

    @Test
    @Transactional
    void patchActiveTrueWithZeroConditionsFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"active\":true}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchActiveTrueWithConditionSucceeds() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRuleCondition condition = createCondition(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"active\":true}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        assertThat(getPersistedTransactionRule(transactionRule).getActive()).isTrue();
        em.remove(em.contains(condition) ? condition : em.merge(condition));
        em.flush();
    }

    @Test
    @Transactional
    void patchClearingAllOutputsFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson =
            "{\"id\":" +
            transactionRule.getId() +
            ",\"resultingDescription\":\"   \",\"resultingCategory\":null,\"resultingFinancialSubscription\":null,\"resultingTags\":[]}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleWithCategoryObjectMissingIdFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"resultingCategory\":{}}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleWithTagObjectMissingIdFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"resultingTags\":[{}]}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
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

    @Test
    @Transactional
    void deleteTransactionRuleDeletesConditionsAndTagJoinsOnly() throws Exception {
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        transactionRule.addResultingTags(ownTag);
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRuleCondition condition = createCondition(transactionRule);
        Long conditionId = condition.getId();
        Long tagId = ownTag.getId();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restTransactionRuleMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRule.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        assertThat(TestUtil.findAll(em, TransactionRuleCondition.class).stream().noneMatch(c -> conditionId.equals(c.getId()))).isTrue();
        assertThat(TestUtil.findAll(em, Tag.class).stream().anyMatch(tag -> tagId.equals(tag.getId()))).isTrue();
        insertedTransactionRule = null;
    }

    @Test
    @Transactional
    void createTransactionRuleAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        transactionRuleDTO.setUser(otherUserDTO);

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertThat(returnedTransactionRuleDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void getTransactionRuleOwnedByAnotherUserIsNotFound() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL_ID, transactionRule.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllTransactionRulesDoesNotIncludeAnotherUsersRules() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(transactionRule.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetTransactionRuleOwnedByAnotherUser() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(transactionRule.getId().intValue()));
    }

    @Test
    @Transactional
    void getTransactionRuleConditionsReturnsOwnConditionsSorted() throws Exception {
        transactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRuleCondition laterCondition = createCondition(transactionRule, 2, "later");
        TransactionRuleCondition earlierCondition = createCondition(transactionRule, 1, "earlier");

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID + "/conditions", transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[0].id").value(earlierCondition.getId().intValue()))
            .andExpect(jsonPath("$.[0].position").value(1))
            .andExpect(jsonPath("$.[1].id").value(laterCondition.getId().intValue()))
            .andExpect(jsonPath("$.[1].position").value(2));
    }

    @Test
    @Transactional
    void getTransactionRuleConditionsReturnsEmptyListWhenOwnRuleHasNoConditions() throws Exception {
        transactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID + "/conditions", transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    @Transactional
    void getTransactionRuleConditionsOwnedByAnotherUserIsNotFound() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        transactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        createCondition(transactionRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID + "/conditions", transactionRule.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetTransactionRuleConditionsOwnedByAnotherUser() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        transactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRuleCondition condition = createCondition(transactionRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID + "/conditions", transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(condition.getId().intValue())));
    }

    @Test
    @Transactional
    void getTransactionRuleConditionsOnlyReturnsRequestedRuleConditions() throws Exception {
        transactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRule otherRule = createEntity(em);
        otherRule.setName("OTHER_RULE");
        otherRule = transactionRuleRepository.saveAndFlush(otherRule);
        TransactionRuleCondition requestedCondition = createCondition(transactionRule, 0, "requested");
        TransactionRuleCondition otherCondition = createCondition(otherRule, 0, "other");

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL_ID + "/conditions", transactionRule.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(requestedCondition.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherCondition.getId().intValue()))));
    }

    @Test
    @Transactional
    void createTransactionRuleWithoutUserInPayloadSucceeds() throws Exception {
        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        transactionRuleDTO.setUser(null);

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertThat(returnedTransactionRuleDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void putTransactionRuleOwnedByAnotherUserIsNotFound() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setName(UPDATED_NAME);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleOwnedByAnotherUserIsNotFound() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(transactionRule.getId());
        transactionRuleDTO.setName(UPDATED_NAME);

        restTransactionRuleMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteTransactionRuleOwnedByAnotherUserIsNotFound() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRule.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(transactionRuleRepository.existsById(transactionRule.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateTransactionRuleCannotChangeOwner() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        User otherUser = createOtherUser(em);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        transactionRuleDTO.setUser(otherUserDTO);
        transactionRuleDTO.setName(UPDATED_NAME);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isOk());

        TransactionRule persistedTransactionRule = transactionRuleRepository.findById(transactionRule.getId()).orElseThrow();
        assertThat(persistedTransactionRule.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedTransactionRule.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchTransactionRuleCannotChangeOwner() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        User otherUser = createOtherUser(em);

        String patchJson =
            "{\"id\":" +
            transactionRule.getId() +
            ",\"name\":\"" +
            UPDATED_NAME +
            "\",\"user\":{\"id\":" +
            otherUser.getId() +
            ",\"login\":\"" +
            otherUser.getLogin() +
            "\"}}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        TransactionRule persistedTransactionRule = transactionRuleRepository.findById(transactionRule.getId()).orElseThrow();
        assertThat(persistedTransactionRule.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedTransactionRule.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void getTransactionRuleCountDoesNotIncludeAnotherUsersRules() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        transactionRuleRepository.saveAndFlush(transactionRule);

        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllTransactionRulesIncludingOtherUsers() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRule otherUsersRule = createEntity(em);
        otherUsersRule.setUser(createOtherUser(em));
        otherUsersRule.setName("OTHER_USER_RULE");
        otherUsersRule = transactionRuleRepository.saveAndFlush(otherUsersRule);

        restTransactionRuleMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRule.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersRule.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllTransactionRulesIncludingOtherUsers() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        TransactionRule otherUsersRule = createEntity(em);
        otherUsersRule.setUser(createOtherUser(em));
        transactionRuleRepository.saveAndFlush(otherUsersRule);

        restTransactionRuleMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateTransactionRuleOwnedByAnotherUser() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setName(UPDATED_NAME);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteTransactionRuleOwnedByAnotherUser() throws Exception {
        transactionRule.setUser(createOtherUser(em));
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restTransactionRuleMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRule.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedTransactionRule = null;
    }

    @Test
    @Transactional
    void createTransactionRuleWithCategoryOwnedByAnotherUserFails() throws Exception {
        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(otherUsersCategory.getId());
        transactionRuleDTO.setResultingCategory(categoryDTO);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createTransactionRuleWithSubscriptionOwnedByAnotherUserFails() throws Exception {
        FinancialSubscription otherUsersSubscription = FinancialSubscriptionResourceIT.createEntity(em);
        otherUsersSubscription.setUser(createOtherUser(em));
        otherUsersSubscription = em.merge(otherUsersSubscription);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        FinancialSubscriptionDTO subscriptionDTO = new FinancialSubscriptionDTO();
        subscriptionDTO.setId(otherUsersSubscription.getId());
        transactionRuleDTO.setResultingFinancialSubscription(subscriptionDTO);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createTransactionRuleWithTagOwnedByAnotherUserFails() throws Exception {
        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        Set<TagDTO> tags = new HashSet<>();
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(otherUsersTag.getId());
        tags.add(tagDTO);
        transactionRuleDTO.setResultingTags(tags);

        restTransactionRuleMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createTransactionRuleWithAccessibleCategorySucceeds() throws Exception {
        Category ownCategory = CategoryResourceIT.createEntity(em);
        ownCategory = em.merge(ownCategory);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        transactionRuleDTO.setId(null);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(ownCategory.getId());
        transactionRuleDTO.setResultingCategory(categoryDTO);

        TransactionRuleDTO returnedTransactionRuleDTO = om.readValue(
            restTransactionRuleMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleDTO.class
        );

        assertThat(returnedTransactionRuleDTO.getResultingCategory().getId()).isEqualTo(ownCategory.getId());
        insertedTransactionRule = transactionRuleMapper.toEntity(returnedTransactionRuleDTO);
    }

    @Test
    @Transactional
    void patchTransactionRuleWithoutCategoryFieldPreservesExistingCategory() throws Exception {
        Category ownCategory = CategoryResourceIT.createEntity(em);
        ownCategory = em.merge(ownCategory);
        em.flush();

        transactionRule.setResultingCategory(ownCategory);
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"name\":\"" + UPDATED_NAME + "\"}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultingCategory.id").value(ownCategory.getId().intValue()));

        assertThat(getPersistedTransactionRule(transactionRule).getResultingCategory().getId()).isEqualTo(ownCategory.getId());
    }

    @Test
    @Transactional
    void patchTransactionRuleWithNullCategoryClearsCategory() throws Exception {
        Category ownCategory = CategoryResourceIT.createEntity(em);
        ownCategory = em.merge(ownCategory);
        em.flush();

        transactionRule.setResultingCategory(ownCategory);
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"resultingCategory\":null}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultingCategory").doesNotExist());

        assertThat(getPersistedTransactionRule(transactionRule).getResultingCategory()).isNull();
    }

    @Test
    @Transactional
    void patchTransactionRuleWithEmptyTagsClearsTags() throws Exception {
        Tag ownTag = TagResourceIT.createEntity(em);
        ownTag = em.merge(ownTag);
        em.flush();

        Set<Tag> tags = new HashSet<>();
        tags.add(ownTag);
        transactionRule.setResultingTags(tags);
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"resultingTags\":[]}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultingTags").isEmpty());

        assertThat(getPersistedTransactionRule(transactionRule).getResultingTags()).isEmpty();
    }

    @Test
    @Transactional
    void updateTransactionRuleWithForeignCategoryOrSubscriptionFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        Category otherUsersCategory = CategoryResourceIT.createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory = em.merge(otherUsersCategory);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(otherUsersCategory.getId());
        transactionRuleDTO.setResultingCategory(categoryDTO);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());

        FinancialSubscription otherUsersSubscription = FinancialSubscriptionResourceIT.createEntity(em);
        otherUsersSubscription.setUser(createOtherUser(em));
        otherUsersSubscription = em.merge(otherUsersSubscription);
        em.flush();

        transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        FinancialSubscriptionDTO subscriptionDTO = new FinancialSubscriptionDTO();
        subscriptionDTO.setId(otherUsersSubscription.getId());
        transactionRuleDTO.setResultingFinancialSubscription(subscriptionDTO);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleWithForeignTagFails() throws Exception {
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        Tag otherUsersTag = TagResourceIT.createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag = em.merge(otherUsersTag);
        em.flush();

        String patchJson = "{\"id\":" + transactionRule.getId() + ",\"resultingTags\":[{\"id\":" + otherUsersTag.getId() + "}]}";

        restTransactionRuleMockMvc
            .perform(patch(ENTITY_API_URL_ID, transactionRule.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCannotAttachCurrentUsersCategoryToAnotherUsersRule() throws Exception {
        User ruleOwner = createOtherUser(em);
        transactionRule.setUser(ruleOwner);
        insertedTransactionRule = transactionRuleRepository.saveAndFlush(transactionRule);

        Category currentUsersCategory = CategoryResourceIT.createEntity(em);
        currentUsersCategory = em.merge(currentUsersCategory);
        em.flush();

        TransactionRuleDTO transactionRuleDTO = transactionRuleMapper.toDto(transactionRule);
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(currentUsersCategory.getId());
        transactionRuleDTO.setResultingCategory(categoryDTO);

        restTransactionRuleMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleDTO))
            )
            .andExpect(status().isBadRequest());
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
