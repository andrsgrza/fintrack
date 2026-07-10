package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TagAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.TagService;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.TagMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link TagResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TagResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_COLOR = "#baD81d";
    private static final String UPDATED_COLOR = "#baeA07";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/tags";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private TagRepository tagRepositoryMock;

    @Autowired
    private TagMapper tagMapper;

    @Mock
    private TagService tagServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTagMockMvc;

    private Tag tag;

    private Tag insertedTag;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createEntity(EntityManager em) {
        Tag tag = new Tag()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .color(DEFAULT_COLOR)
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        tag.setUser(getCurrentMockUser(em));
        return tag;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createUpdatedEntity(EntityManager em) {
        Tag updatedTag = new Tag()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        updatedTag.setUser(getCurrentMockUser(em));
        return updatedTag;
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
        tag = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedTag != null) {
            tagRepository.delete(insertedTag);
            insertedTag = null;
        }
    }

    @Test
    @Transactional
    void createTag() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);
        var returnedTagDTO = om.readValue(
            restTagMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TagDTO.class
        );

        // Validate the Tag in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTag = tagMapper.toEntity(returnedTagDTO);
        assertTagUpdatableFieldsEquals(returnedTag, getPersistedTag(returnedTag));

        insertedTag = returnedTag;
    }

    @Test
    @Transactional
    void createTagWithExistingId() throws Exception {
        // Create the Tag with an existing ID
        tag.setId(1L);
        TagDTO tagDTO = tagMapper.toDto(tag);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tag.setName(null);

        // Create the Tag, which fails.
        TagDTO tagDTO = tagMapper.toDto(tag);

        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tag.setActive(null);

        // Create the Tag, which fails.
        TagDTO tagDTO = tagMapper.toDto(tag);

        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tag.setCreatedAt(null);

        // Create the Tag, which fails.
        TagDTO tagDTO = tagMapper.toDto(tag);

        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tag.setUpdatedAt(null);

        // Create the Tag, which fails.
        TagDTO tagDTO = tagMapper.toDto(tag);

        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllTags() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList
        restTagMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(tag.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTagsWithEagerRelationshipsIsEnabled() throws Exception {
        when(tagServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTagMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(tagServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTagsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(tagServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTagMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(tagRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTag() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get the tag
        restTagMockMvc
            .perform(get(ENTITY_API_URL_ID, tag.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(tag.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.color").value(DEFAULT_COLOR))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getTagsByIdFiltering() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        Long id = tag.getId();

        defaultTagFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTagFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTagFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTagsByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where name equals to
        defaultTagFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTagsByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where name in
        defaultTagFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTagsByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where name is not null
        defaultTagFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where name contains
        defaultTagFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllTagsByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where name does not contain
        defaultTagFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllTagsByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where description equals to
        defaultTagFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTagsByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where description in
        defaultTagFiltering("description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION, "description.in=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTagsByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where description is not null
        defaultTagFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where description contains
        defaultTagFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTagsByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where description does not contain
        defaultTagFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTagsByColorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where color equals to
        defaultTagFiltering("color.equals=" + DEFAULT_COLOR, "color.equals=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllTagsByColorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where color in
        defaultTagFiltering("color.in=" + DEFAULT_COLOR + "," + UPDATED_COLOR, "color.in=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllTagsByColorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where color is not null
        defaultTagFiltering("color.specified=true", "color.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByColorContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where color contains
        defaultTagFiltering("color.contains=" + DEFAULT_COLOR, "color.contains=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllTagsByColorNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where color does not contain
        defaultTagFiltering("color.doesNotContain=" + UPDATED_COLOR, "color.doesNotContain=" + DEFAULT_COLOR);
    }

    @Test
    @Transactional
    void getAllTagsByActiveIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where active equals to
        defaultTagFiltering("active.equals=" + DEFAULT_ACTIVE, "active.equals=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllTagsByActiveIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where active in
        defaultTagFiltering("active.in=" + DEFAULT_ACTIVE + "," + UPDATED_ACTIVE, "active.in=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllTagsByActiveIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where active is not null
        defaultTagFiltering("active.specified=true", "active.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where createdAt equals to
        defaultTagFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllTagsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where createdAt in
        defaultTagFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllTagsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where createdAt is not null
        defaultTagFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where updatedAt equals to
        defaultTagFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllTagsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where updatedAt in
        defaultTagFiltering("updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT, "updatedAt.in=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllTagsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        // Get all the tagList where updatedAt is not null
        defaultTagFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTagsByUserIsEqualToSomething() throws Exception {
        User user = getCurrentMockUser(em);
        insertedTag = tagRepository.saveAndFlush(tag);
        Long userId = user.getId();
        // Get all the tagList where user equals to userId
        defaultTagShouldBeFound("userId.equals=" + userId);

        // Get all the tagList where user equals to (userId + 1)
        defaultTagShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllTagsByFinancialTransactionsIsEqualToSomething() throws Exception {
        FinancialTransaction financialTransactions;
        if (TestUtil.findAll(em, FinancialTransaction.class).isEmpty()) {
            tagRepository.saveAndFlush(tag);
            financialTransactions = FinancialTransactionResourceIT.createEntity(em);
        } else {
            financialTransactions = TestUtil.findAll(em, FinancialTransaction.class).get(0);
        }
        em.persist(financialTransactions);
        em.flush();
        tag.addFinancialTransactions(financialTransactions);
        tagRepository.saveAndFlush(tag);
        Long financialTransactionsId = financialTransactions.getId();
        // Get all the tagList where financialTransactions equals to financialTransactionsId
        defaultTagShouldBeFound("financialTransactionsId.equals=" + financialTransactionsId);

        // Get all the tagList where financialTransactions equals to (financialTransactionsId + 1)
        defaultTagShouldNotBeFound("financialTransactionsId.equals=" + (financialTransactionsId + 1));
    }

    @Test
    @Transactional
    void getAllTagsByTransactionRulesIsEqualToSomething() throws Exception {
        TransactionRule transactionRules;
        if (TestUtil.findAll(em, TransactionRule.class).isEmpty()) {
            tagRepository.saveAndFlush(tag);
            transactionRules = TransactionRuleResourceIT.createEntity(em);
        } else {
            transactionRules = TestUtil.findAll(em, TransactionRule.class).get(0);
        }
        em.persist(transactionRules);
        em.flush();
        tag.addTransactionRules(transactionRules);
        tagRepository.saveAndFlush(tag);
        Long transactionRulesId = transactionRules.getId();
        // Get all the tagList where transactionRules equals to transactionRulesId
        defaultTagShouldBeFound("transactionRulesId.equals=" + transactionRulesId);

        // Get all the tagList where transactionRules equals to (transactionRulesId + 1)
        defaultTagShouldNotBeFound("transactionRulesId.equals=" + (transactionRulesId + 1));
    }

    @Test
    @Transactional
    void getAllTagsBySubscriptionsIsEqualToSomething() throws Exception {
        FinancialSubscription subscriptions;
        if (TestUtil.findAll(em, FinancialSubscription.class).isEmpty()) {
            tagRepository.saveAndFlush(tag);
            subscriptions = FinancialSubscriptionResourceIT.createEntity(em);
        } else {
            subscriptions = TestUtil.findAll(em, FinancialSubscription.class).get(0);
        }
        em.persist(subscriptions);
        em.flush();
        tag.addSubscriptions(subscriptions);
        tagRepository.saveAndFlush(tag);
        Long subscriptionsId = subscriptions.getId();
        // Get all the tagList where subscriptions equals to subscriptionsId
        defaultTagShouldBeFound("subscriptionsId.equals=" + subscriptionsId);

        // Get all the tagList where subscriptions equals to (subscriptionsId + 1)
        defaultTagShouldNotBeFound("subscriptionsId.equals=" + (subscriptionsId + 1));
    }

    @Test
    @Transactional
    void getAllTagsByBudgetsIsEqualToSomething() throws Exception {
        Budget budgets;
        if (TestUtil.findAll(em, Budget.class).isEmpty()) {
            tagRepository.saveAndFlush(tag);
            budgets = BudgetResourceIT.createEntity(em);
        } else {
            budgets = TestUtil.findAll(em, Budget.class).get(0);
        }
        em.persist(budgets);
        em.flush();
        tag.addBudgets(budgets);
        tagRepository.saveAndFlush(tag);
        Long budgetsId = budgets.getId();
        // Get all the tagList where budgets equals to budgetsId
        defaultTagShouldBeFound("budgetsId.equals=" + budgetsId);

        // Get all the tagList where budgets equals to (budgetsId + 1)
        defaultTagShouldNotBeFound("budgetsId.equals=" + (budgetsId + 1));
    }

    private void defaultTagFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTagShouldBeFound(shouldBeFound);
        defaultTagShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTagShouldBeFound(String filter) throws Exception {
        restTagMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(tag.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restTagMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL + "/count", filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTagShouldNotBeFound(String filter) throws Exception {
        restTagMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTagMockMvc
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
    void getNonExistingTag() throws Exception {
        // Get the tag
        restTagMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTag() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag
        Tag updatedTag = tagRepository.findById(tag.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedTag are not directly saved in db
        em.detach(updatedTag);
        updatedTag
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        TagDTO tagDTO = tagMapper.toDto(updatedTag);

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isOk());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTagToMatchAllProperties(updatedTag);
    }

    @Test
    @Transactional
    void putNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag.description(UPDATED_DESCRIPTION).updatedAt(UPDATED_UPDATED_AT);

        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTag.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTag))
            )
            .andExpect(status().isOk());

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedTag, tag), getPersistedTag(tag));
    }

    @Test
    @Transactional
    void fullUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .color(UPDATED_COLOR)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTag.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTag))
            )
            .andExpect(status().isOk());

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(partialUpdatedTag, getPersistedTag(partialUpdatedTag));
    }

    @Test
    @Transactional
    void patchNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, tagDTO.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTagMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTag() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.saveAndFlush(tag);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the tag
        restTagMockMvc.perform(delete(ENTITY_API_URL_ID, tag.getId()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createTagAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        TagDTO tagDTO = tagMapper.toDto(tag);
        tagDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        tagDTO.setUser(otherUserDTO);

        TagDTO returnedTagDTO = om.readValue(
            restTagMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TagDTO.class
        );

        assertThat(returnedTagDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedTag = tagMapper.toEntity(returnedTagDTO);
    }

    @Test
    @Transactional
    void getTagOwnedByAnotherUserIsNotFound() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        restTagMockMvc.perform(get(ENTITY_API_URL_ID, tag.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllTagsDoesNotIncludeAnotherUsersTags() throws Exception {
        tag.setUser(createOtherUser(em));
        tagRepository.saveAndFlush(tag);

        restTagMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(tag.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetTagOwnedByAnotherUser() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        restTagMockMvc
            .perform(get(ENTITY_API_URL_ID, tag.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(tag.getId().intValue()));
    }

    @Test
    @Transactional
    void createTagWithoutUserInPayloadSucceeds() throws Exception {
        TagDTO tagDTO = tagMapper.toDto(tag);
        tagDTO.setId(null);
        tagDTO.setUser(null);

        TagDTO returnedTagDTO = om.readValue(
            restTagMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TagDTO.class
        );

        assertThat(returnedTagDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedTag = tagMapper.toEntity(returnedTagDTO);
    }

    @Test
    @Transactional
    void putTagOwnedByAnotherUserIsNotFound() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        TagDTO tagDTO = tagMapper.toDto(tag);
        tagDTO.setName(UPDATED_NAME);

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTagOwnedByAnotherUserIsNotFound() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(tag.getId());
        tagDTO.setName(UPDATED_NAME);

        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, tagDTO.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteTagOwnedByAnotherUserIsNotFound() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        restTagMockMvc.perform(delete(ENTITY_API_URL_ID, tag.getId()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());

        assertThat(tagRepository.existsById(tag.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateTagCannotChangeOwner() throws Exception {
        insertedTag = tagRepository.saveAndFlush(tag);
        User otherUser = createOtherUser(em);

        TagDTO tagDTO = tagMapper.toDto(tag);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        tagDTO.setUser(otherUserDTO);
        tagDTO.setName(UPDATED_NAME);

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isOk());

        Tag persistedTag = tagRepository.findById(tag.getId()).orElseThrow();
        assertThat(persistedTag.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedTag.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchTagCannotChangeOwner() throws Exception {
        insertedTag = tagRepository.saveAndFlush(tag);
        User otherUser = createOtherUser(em);

        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(tag.getId());
        tagDTO.setName(UPDATED_NAME);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        tagDTO.setUser(otherUserDTO);

        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, tagDTO.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isOk());

        Tag persistedTag = tagRepository.findById(tag.getId()).orElseThrow();
        assertThat(persistedTag.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedTag.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void getTagCountDoesNotIncludeAnotherUsersTags() throws Exception {
        tag.setUser(createOtherUser(em));
        tagRepository.saveAndFlush(tag);

        restTagMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllTagsIncludingOtherUsers() throws Exception {
        insertedTag = tagRepository.saveAndFlush(tag);
        Tag otherUsersTag = createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        otherUsersTag.setName("OTHER_USER_TAG");
        otherUsersTag = tagRepository.saveAndFlush(otherUsersTag);

        restTagMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(tag.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersTag.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllTagsIncludingOtherUsers() throws Exception {
        insertedTag = tagRepository.saveAndFlush(tag);
        Tag otherUsersTag = createEntity(em);
        otherUsersTag.setUser(createOtherUser(em));
        tagRepository.saveAndFlush(otherUsersTag);

        restTagMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateTagOwnedByAnotherUser() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);

        TagDTO tagDTO = tagMapper.toDto(tag);
        tagDTO.setName(UPDATED_NAME);

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteTagOwnedByAnotherUser() throws Exception {
        tag.setUser(createOtherUser(em));
        insertedTag = tagRepository.saveAndFlush(tag);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restTagMockMvc.perform(delete(ENTITY_API_URL_ID, tag.getId()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedTag = null;
    }

    @Test
    @Transactional
    void createDuplicateTagSameUserDifferentCaseAndSpacingReturnsBadRequest() throws Exception {
        Tag existingTag = createEntity(em);
        existingTag.name("Comida");
        insertedTag = tagRepository.saveAndFlush(existingTag);

        Tag duplicateTag = createEntity(em);
        duplicateTag.name(" COMIDA ");
        TagDTO tagDTO = tagMapper.toDto(duplicateTag);

        restTagMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("tag"));
    }

    @Test
    @Transactional
    void createSameTagNameForDifferentUserSucceeds() throws Exception {
        Tag existingTag = createEntity(em);
        existingTag.name("Comida");
        insertedTag = tagRepository.saveAndFlush(existingTag);

        User otherUser = createOtherUser(em);
        TagDTO tagDTO = tagMapper.toDto(createEntity(em));
        tagDTO.setId(null);
        tagDTO.setName("Comida");

        restTagMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(user(otherUser.getLogin()).roles("USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(tagDTO))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Comida"));
    }

    @Test
    @Transactional
    void updateTagNameToDuplicateSameUserReturnsBadRequest() throws Exception {
        Tag firstTag = createEntity(em);
        firstTag.name("Comida");
        tagRepository.saveAndFlush(firstTag);

        Tag secondTag = createEntity(em);
        secondTag.name("Viajes");
        insertedTag = tagRepository.saveAndFlush(secondTag);

        TagDTO tagDTO = tagMapper.toDto(secondTag);
        tagDTO.setName("comida");

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("tag"));
    }

    @Test
    @Transactional
    void patchTagNameToDuplicateSameUserReturnsBadRequest() throws Exception {
        Tag firstTag = createEntity(em);
        firstTag.name("Comida");
        tagRepository.saveAndFlush(firstTag);

        Tag secondTag = createEntity(em);
        secondTag.name("Viajes");
        insertedTag = tagRepository.saveAndFlush(secondTag);

        Tag patchPayload = new Tag();
        patchPayload.setId(secondTag.getId());
        patchPayload.setName(" COMIDA ");

        restTagMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, secondTag.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchPayload))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("tag"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminEditingForeignTagCannotDuplicateWithinForeignOwner() throws Exception {
        User foreignOwner = createOtherUser(em);

        Tag firstTag = createEntity(em);
        firstTag.setUser(foreignOwner);
        firstTag.name("Comida");
        tagRepository.saveAndFlush(firstTag);

        Tag secondTag = createEntity(em);
        secondTag.setUser(foreignOwner);
        secondTag.name("Viajes");
        insertedTag = tagRepository.saveAndFlush(secondTag);

        TagDTO tagDTO = tagMapper.toDto(secondTag);
        tagDTO.setName("comida");

        restTagMockMvc
            .perform(put(ENTITY_API_URL_ID, tagDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(tagDTO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("tag"));
    }

    protected long getRepositoryCount() {
        return tagRepository.count();
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

    protected Tag getPersistedTag(Tag tag) {
        return tagRepository.findById(tag.getId()).orElseThrow();
    }

    protected void assertPersistedTagToMatchAllProperties(Tag expectedTag) {
        assertTagAllPropertiesEquals(expectedTag, getPersistedTag(expectedTag));
    }

    protected void assertPersistedTagToMatchUpdatableProperties(Tag expectedTag) {
        assertTagAllUpdatablePropertiesEquals(expectedTag, getPersistedTag(expectedTag));
    }
}
