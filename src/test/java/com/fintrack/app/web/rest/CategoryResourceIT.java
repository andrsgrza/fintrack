package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.CategoryAsserts.*;
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
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.CategoryService;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.mapper.CategoryMapper;
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
 * Integration tests for the {@link CategoryResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class CategoryResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final CategoryType DEFAULT_CATEGORY_TYPE = CategoryType.EXPENSE;
    private static final CategoryType UPDATED_CATEGORY_TYPE = CategoryType.INCOME;

    private static final String DEFAULT_COLOR = "#8d86DB";
    private static final String UPDATED_COLOR = "#f24DaF";

    private static final String DEFAULT_ICON = "AAAAAAAAAA";
    private static final String UPDATED_ICON = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/categories";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepositoryMock;

    @Autowired
    private CategoryMapper categoryMapper;

    @Mock
    private CategoryService categoryServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restCategoryMockMvc;

    private Category category;

    private Category insertedCategory;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Category createEntity(EntityManager em) {
        Category category = new Category()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .categoryType(DEFAULT_CATEGORY_TYPE)
            .color(DEFAULT_COLOR)
            .icon(DEFAULT_ICON)
            .active(DEFAULT_ACTIVE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        category.setUser(getCurrentMockUser(em));
        return category;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Category createUpdatedEntity(EntityManager em) {
        Category updatedCategory = new Category()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .categoryType(UPDATED_CATEGORY_TYPE)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        updatedCategory.setUser(getCurrentMockUser(em));
        return updatedCategory;
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
        category = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedCategory != null) {
            categoryRepository.delete(insertedCategory);
            insertedCategory = null;
        }
    }

    @Test
    @Transactional
    void createCategory() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        var returnedCategoryDTO = om.readValue(
            restCategoryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CategoryDTO.class
        );

        // Validate the Category in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedCategory = categoryMapper.toEntity(returnedCategoryDTO);
        assertCategoryUpdatableFieldsEquals(returnedCategory, getPersistedCategory(returnedCategory));

        insertedCategory = returnedCategory;
    }

    @Test
    @Transactional
    void createCategoryWithExistingId() throws Exception {
        // Create the Category with an existing ID
        category.setId(1L);
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        category.setName(null);

        // Create the Category, which fails.
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCategoryTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        category.setCategoryType(null);

        // Create the Category, which fails.
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        category.setActive(null);

        // Create the Category, which fails.
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        category.setCreatedAt(null);

        // Create the Category, which fails.
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        category.setUpdatedAt(null);

        // Create the Category, which fails.
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllCategories() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList
        restCategoryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(category.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].categoryType").value(hasItem(DEFAULT_CATEGORY_TYPE.toString())))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].icon").value(hasItem(DEFAULT_ICON)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllCategoriesWithEagerRelationshipsIsEnabled() throws Exception {
        when(categoryServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restCategoryMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(categoryServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllCategoriesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(categoryServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restCategoryMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(categoryRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getCategory() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get the category
        restCategoryMockMvc
            .perform(get(ENTITY_API_URL_ID, category.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(category.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.categoryType").value(DEFAULT_CATEGORY_TYPE.toString()))
            .andExpect(jsonPath("$.color").value(DEFAULT_COLOR))
            .andExpect(jsonPath("$.icon").value(DEFAULT_ICON))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getCategoriesByIdFiltering() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        Long id = category.getId();

        defaultCategoryFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultCategoryFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultCategoryFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllCategoriesByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where name equals to
        defaultCategoryFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllCategoriesByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where name in
        defaultCategoryFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllCategoriesByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where name is not null
        defaultCategoryFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where name contains
        defaultCategoryFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllCategoriesByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where name does not contain
        defaultCategoryFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllCategoriesByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where description equals to
        defaultCategoryFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllCategoriesByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where description in
        defaultCategoryFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllCategoriesByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where description is not null
        defaultCategoryFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where description contains
        defaultCategoryFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllCategoriesByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where description does not contain
        defaultCategoryFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllCategoriesByCategoryTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where categoryType equals to
        defaultCategoryFiltering("categoryType.equals=" + DEFAULT_CATEGORY_TYPE, "categoryType.equals=" + UPDATED_CATEGORY_TYPE);
    }

    @Test
    @Transactional
    void getAllCategoriesByCategoryTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where categoryType in
        defaultCategoryFiltering(
            "categoryType.in=" + DEFAULT_CATEGORY_TYPE + "," + UPDATED_CATEGORY_TYPE,
            "categoryType.in=" + UPDATED_CATEGORY_TYPE
        );
    }

    @Test
    @Transactional
    void getAllCategoriesByCategoryTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where categoryType is not null
        defaultCategoryFiltering("categoryType.specified=true", "categoryType.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByColorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where color equals to
        defaultCategoryFiltering("color.equals=" + DEFAULT_COLOR, "color.equals=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllCategoriesByColorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where color in
        defaultCategoryFiltering("color.in=" + DEFAULT_COLOR + "," + UPDATED_COLOR, "color.in=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllCategoriesByColorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where color is not null
        defaultCategoryFiltering("color.specified=true", "color.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByColorContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where color contains
        defaultCategoryFiltering("color.contains=" + DEFAULT_COLOR, "color.contains=" + UPDATED_COLOR);
    }

    @Test
    @Transactional
    void getAllCategoriesByColorNotContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where color does not contain
        defaultCategoryFiltering("color.doesNotContain=" + UPDATED_COLOR, "color.doesNotContain=" + DEFAULT_COLOR);
    }

    @Test
    @Transactional
    void getAllCategoriesByIconIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where icon equals to
        defaultCategoryFiltering("icon.equals=" + DEFAULT_ICON, "icon.equals=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllCategoriesByIconIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where icon in
        defaultCategoryFiltering("icon.in=" + DEFAULT_ICON + "," + UPDATED_ICON, "icon.in=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllCategoriesByIconIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where icon is not null
        defaultCategoryFiltering("icon.specified=true", "icon.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByIconContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where icon contains
        defaultCategoryFiltering("icon.contains=" + DEFAULT_ICON, "icon.contains=" + UPDATED_ICON);
    }

    @Test
    @Transactional
    void getAllCategoriesByIconNotContainsSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where icon does not contain
        defaultCategoryFiltering("icon.doesNotContain=" + UPDATED_ICON, "icon.doesNotContain=" + DEFAULT_ICON);
    }

    @Test
    @Transactional
    void getAllCategoriesByActiveIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where active equals to
        defaultCategoryFiltering("active.equals=" + DEFAULT_ACTIVE, "active.equals=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllCategoriesByActiveIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where active in
        defaultCategoryFiltering("active.in=" + DEFAULT_ACTIVE + "," + UPDATED_ACTIVE, "active.in=" + UPDATED_ACTIVE);
    }

    @Test
    @Transactional
    void getAllCategoriesByActiveIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where active is not null
        defaultCategoryFiltering("active.specified=true", "active.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where createdAt equals to
        defaultCategoryFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllCategoriesByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where createdAt in
        defaultCategoryFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllCategoriesByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where createdAt is not null
        defaultCategoryFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where updatedAt equals to
        defaultCategoryFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllCategoriesByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where updatedAt in
        defaultCategoryFiltering("updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT, "updatedAt.in=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllCategoriesByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        // Get all the categoryList where updatedAt is not null
        defaultCategoryFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllCategoriesByUserIsEqualToSomething() throws Exception {
        User user = getCurrentMockUser(em);
        category.setUser(user);
        categoryRepository.saveAndFlush(category);
        Long userId = user.getId();
        // Get all the categoryList where user equals to userId
        defaultCategoryShouldBeFound("userId.equals=" + userId);

        // Get all the categoryList where user equals to (userId + 1)
        defaultCategoryShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    @Test
    @Transactional
    void getAllCategoriesByParentCategoryIsEqualToSomething() throws Exception {
        Category parentCategory;
        if (TestUtil.findAll(em, Category.class).isEmpty()) {
            categoryRepository.saveAndFlush(category);
            parentCategory = CategoryResourceIT.createEntity(em);
        } else {
            parentCategory = TestUtil.findAll(em, Category.class).get(0);
        }
        em.persist(parentCategory);
        em.flush();
        category.setParentCategory(parentCategory);
        categoryRepository.saveAndFlush(category);
        Long parentCategoryId = parentCategory.getId();
        // Get all the categoryList where parentCategory equals to parentCategoryId
        defaultCategoryShouldBeFound("parentCategoryId.equals=" + parentCategoryId);

        // Get all the categoryList where parentCategory equals to (parentCategoryId + 1)
        defaultCategoryShouldNotBeFound("parentCategoryId.equals=" + (parentCategoryId + 1));
    }

    @Test
    @Transactional
    void getAllCategoriesByBudgetsIsEqualToSomething() throws Exception {
        Budget budgets;
        if (TestUtil.findAll(em, Budget.class).isEmpty()) {
            categoryRepository.saveAndFlush(category);
            budgets = BudgetResourceIT.createEntity(em);
        } else {
            budgets = TestUtil.findAll(em, Budget.class).get(0);
        }
        em.persist(budgets);
        em.flush();
        category.addBudgets(budgets);
        categoryRepository.saveAndFlush(category);
        Long budgetsId = budgets.getId();
        // Get all the categoryList where budgets equals to budgetsId
        defaultCategoryShouldBeFound("budgetsId.equals=" + budgetsId);

        // Get all the categoryList where budgets equals to (budgetsId + 1)
        defaultCategoryShouldNotBeFound("budgetsId.equals=" + (budgetsId + 1));
    }

    private void defaultCategoryFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultCategoryShouldBeFound(shouldBeFound);
        defaultCategoryShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultCategoryShouldBeFound(String filter) throws Exception {
        restCategoryMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(category.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].categoryType").value(hasItem(DEFAULT_CATEGORY_TYPE.toString())))
            .andExpect(jsonPath("$.[*].color").value(hasItem(DEFAULT_COLOR)))
            .andExpect(jsonPath("$.[*].icon").value(hasItem(DEFAULT_ICON)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restCategoryMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL + "/count", filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultCategoryShouldNotBeFound(String filter) throws Exception {
        restCategoryMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL, filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restCategoryMockMvc
            .perform(buildFilterRequest(ENTITY_API_URL + "/count", filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    private MockHttpServletRequestBuilder buildFilterRequest(String url, String filter) {
        MockHttpServletRequestBuilder requestBuilder = get(url).param("sort", "id,desc");
        for (String param : filter.split("&")) {
            String[] keyValue = param.split("=", 2);
            requestBuilder.param(keyValue[0], keyValue[1]);
        }
        return requestBuilder;
    }

    @Test
    @Transactional
    void getNonExistingCategory() throws Exception {
        // Get the category
        restCategoryMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingCategory() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the category
        Category updatedCategory = categoryRepository.findById(category.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedCategory are not directly saved in db
        em.detach(updatedCategory);
        updatedCategory
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .categoryType(UPDATED_CATEGORY_TYPE)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        CategoryDTO categoryDTO = categoryMapper.toDto(updatedCategory);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isOk());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedCategoryToMatchAllProperties(updatedCategory);
    }

    @Test
    @Transactional
    void putNonExistingCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateCategoryWithPatch() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the category using partial update
        Category partialUpdatedCategory = new Category();
        partialUpdatedCategory.setId(category.getId());

        partialUpdatedCategory
            .name(UPDATED_NAME)
            .categoryType(UPDATED_CATEGORY_TYPE)
            .color(UPDATED_COLOR)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCategory.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCategory))
            )
            .andExpect(status().isOk());

        // Validate the Category in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCategoryUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedCategory, category), getPersistedCategory(category));
    }

    @Test
    @Transactional
    void fullUpdateCategoryWithPatch() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the category using partial update
        Category partialUpdatedCategory = new Category();
        partialUpdatedCategory.setId(category.getId());

        partialUpdatedCategory
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .categoryType(UPDATED_CATEGORY_TYPE)
            .color(UPDATED_COLOR)
            .icon(UPDATED_ICON)
            .active(UPDATED_ACTIVE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCategory.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCategory))
            )
            .andExpect(status().isOk());

        // Validate the Category in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCategoryUpdatableFieldsEquals(partialUpdatedCategory, getPersistedCategory(partialUpdatedCategory));
    }

    @Test
    @Transactional
    void patchNonExistingCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamCategory() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        category.setId(longCount.incrementAndGet());

        // Create the Category
        CategoryDTO categoryDTO = categoryMapper.toDto(category);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCategoryMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Category in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteCategory() throws Exception {
        // Initialize the database
        insertedCategory = categoryRepository.saveAndFlush(category);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the category
        restCategoryMockMvc
            .perform(delete(ENTITY_API_URL_ID, category.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createCategoryAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        categoryDTO.setUser(otherUserDTO);

        CategoryDTO returnedCategoryDTO = om.readValue(
            restCategoryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CategoryDTO.class
        );

        assertThat(returnedCategoryDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedCategory = categoryMapper.toEntity(returnedCategoryDTO);
    }

    @Test
    @Transactional
    void getCategoryOwnedByAnotherUserIsNotFound() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        restCategoryMockMvc.perform(get(ENTITY_API_URL_ID, category.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllCategoriesDoesNotIncludeAnotherUsersCategories() throws Exception {
        category.setUser(createOtherUser(em));
        categoryRepository.saveAndFlush(category);

        restCategoryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(category.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetCategoryOwnedByAnotherUser() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        restCategoryMockMvc
            .perform(get(ENTITY_API_URL_ID, category.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(category.getId().intValue()));
    }

    @Test
    @Transactional
    void createCategoryWithoutUserInPayloadSucceeds() throws Exception {
        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setId(null);
        categoryDTO.setUser(null);

        CategoryDTO returnedCategoryDTO = om.readValue(
            restCategoryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CategoryDTO.class
        );

        assertThat(returnedCategoryDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedCategory = categoryMapper.toEntity(returnedCategoryDTO);
    }

    @Test
    @Transactional
    void putCategoryOwnedByAnotherUserIsNotFound() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setName(UPDATED_NAME);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchCategoryOwnedByAnotherUserIsNotFound() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(category.getId());
        categoryDTO.setName(UPDATED_NAME);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteCategoryOwnedByAnotherUserIsNotFound() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        restCategoryMockMvc
            .perform(delete(ENTITY_API_URL_ID, category.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(categoryRepository.existsById(category.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateCategoryCannotChangeOwner() throws Exception {
        insertedCategory = categoryRepository.saveAndFlush(category);
        User otherUser = createOtherUser(em);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        categoryDTO.setUser(otherUserDTO);
        categoryDTO.setName(UPDATED_NAME);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isOk());

        Category persistedCategory = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(persistedCategory.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedCategory.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void patchCategoryCannotChangeOwner() throws Exception {
        insertedCategory = categoryRepository.saveAndFlush(category);
        User otherUser = createOtherUser(em);

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(category.getId());
        categoryDTO.setName(UPDATED_NAME);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        categoryDTO.setUser(otherUserDTO);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isOk());

        Category persistedCategory = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(persistedCategory.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persistedCategory.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    @Transactional
    void getCategoryCountDoesNotIncludeAnotherUsersCategories() throws Exception {
        category.setUser(createOtherUser(em));
        categoryRepository.saveAndFlush(category);

        restCategoryMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("0"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllCategoriesIncludingOtherUsers() throws Exception {
        insertedCategory = categoryRepository.saveAndFlush(category);
        Category otherUsersCategory = createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        otherUsersCategory.setName("OTHER_USER_CATEGORY");
        otherUsersCategory = categoryRepository.saveAndFlush(otherUsersCategory);

        restCategoryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(category.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherUsersCategory.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCountAllCategoriesIncludingOtherUsers() throws Exception {
        insertedCategory = categoryRepository.saveAndFlush(category);
        Category otherUsersCategory = createEntity(em);
        otherUsersCategory.setUser(createOtherUser(em));
        categoryRepository.saveAndFlush(otherUsersCategory);

        restCategoryMockMvc.perform(get(ENTITY_API_URL + "/count")).andExpect(status().isOk()).andExpect(content().string("2"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateCategoryOwnedByAnotherUser() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setName(UPDATED_NAME);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(UPDATED_NAME));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteCategoryOwnedByAnotherUser() throws Exception {
        category.setUser(createOtherUser(em));
        insertedCategory = categoryRepository.saveAndFlush(category);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restCategoryMockMvc
            .perform(delete(ENTITY_API_URL_ID, category.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedCategory = null;
    }

    @Test
    @Transactional
    void createCategoryWithParentOwnedByAnotherUserFails() throws Exception {
        Category otherUsersParent = createEntity(em);
        otherUsersParent.setUser(createOtherUser(em));
        otherUsersParent = categoryRepository.saveAndFlush(otherUsersParent);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setId(null);
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(otherUsersParent.getId());
        categoryDTO.setParentCategory(parentDTO);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateCategoryWithSelfAsParentFails() throws Exception {
        insertedCategory = categoryRepository.saveAndFlush(category);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(category.getId());
        categoryDTO.setParentCategory(parentDTO);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateCategoryCreatingCycleFails() throws Exception {
        Category parentCategory = createEntity(em);
        parentCategory.setName("PARENT_CATEGORY");
        parentCategory = categoryRepository.saveAndFlush(parentCategory);

        category.setParentCategory(parentCategory);
        insertedCategory = categoryRepository.saveAndFlush(category);

        CategoryDTO categoryDTO = categoryMapper.toDto(parentCategory);
        CategoryDTO childDTO = new CategoryDTO();
        childDTO.setId(category.getId());
        categoryDTO.setParentCategory(childDTO);

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createCategoryWithAccessibleParentSucceeds() throws Exception {
        Category parentCategory = createEntity(em);
        parentCategory.setName("PARENT_CATEGORY");
        parentCategory = categoryRepository.saveAndFlush(parentCategory);

        CategoryDTO categoryDTO = categoryMapper.toDto(category);
        categoryDTO.setId(null);
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(parentCategory.getId());
        categoryDTO.setParentCategory(parentDTO);

        CategoryDTO returnedCategoryDTO = om.readValue(
            restCategoryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CategoryDTO.class
        );

        assertThat(returnedCategoryDTO.getParentCategory().getId()).isEqualTo(parentCategory.getId());
        insertedCategory = categoryMapper.toEntity(returnedCategoryDTO);
    }

    @Test
    @Transactional
    void createDuplicateRootCategorySameUserSameTypeDifferentCaseAndSpacingReturnsBadRequest() throws Exception {
        Category existingCategory = createEntity(em);
        existingCategory.name("Comida");
        categoryRepository.saveAndFlush(existingCategory);

        CategoryDTO categoryDTO = categoryMapper.toDto(createEntity(em));
        categoryDTO.setId(null);
        categoryDTO.setName(" COMIDA ");

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("category"));
    }

    @Test
    @Transactional
    void createSameRootCategoryNameForDifferentUserSucceeds() throws Exception {
        Category existingCategory = createEntity(em);
        existingCategory.name("Comida");
        categoryRepository.saveAndFlush(existingCategory);

        User otherUser = createOtherUser(em);
        CategoryDTO categoryDTO = categoryMapper.toDto(createEntity(em));
        categoryDTO.setId(null);
        categoryDTO.setName("Comida");

        restCategoryMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(user(otherUser.getLogin()).roles("USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Comida"));
    }

    @Test
    @Transactional
    void createSameRootCategoryNameSameUserDifferentCategoryTypeSucceeds() throws Exception {
        Category existingCategory = createEntity(em);
        existingCategory.name("Comida");
        existingCategory.categoryType(CategoryType.EXPENSE);
        categoryRepository.saveAndFlush(existingCategory);

        CategoryDTO categoryDTO = categoryMapper.toDto(createEntity(em));
        categoryDTO.setId(null);
        categoryDTO.setName("Comida");
        categoryDTO.setCategoryType(CategoryType.INCOME);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Comida"))
            .andExpect(jsonPath("$.categoryType").value(CategoryType.INCOME.toString()));
    }

    @Test
    @Transactional
    void createDuplicateChildCategoryUnderSameParentReturnsBadRequest() throws Exception {
        Category parentCategory = createEntity(em);
        parentCategory.setName("PARENT");
        parentCategory = categoryRepository.saveAndFlush(parentCategory);

        Category existingChild = createEntity(em);
        existingChild.setName("Comida");
        existingChild.setParentCategory(parentCategory);
        categoryRepository.saveAndFlush(existingChild);

        CategoryDTO categoryDTO = categoryMapper.toDto(createEntity(em));
        categoryDTO.setId(null);
        categoryDTO.setName(" comida ");
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(parentCategory.getId());
        categoryDTO.setParentCategory(parentDTO);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("category"));
    }

    @Test
    @Transactional
    void createSameChildCategoryNameUnderDifferentParentSucceeds() throws Exception {
        Category parentOne = createEntity(em);
        parentOne.setName("PARENT_ONE");
        parentOne = categoryRepository.saveAndFlush(parentOne);

        Category parentTwo = createEntity(em);
        parentTwo.setName("PARENT_TWO");
        parentTwo = categoryRepository.saveAndFlush(parentTwo);

        Category existingChild = createEntity(em);
        existingChild.setName("Comida");
        existingChild.setParentCategory(parentOne);
        categoryRepository.saveAndFlush(existingChild);

        CategoryDTO categoryDTO = categoryMapper.toDto(createEntity(em));
        categoryDTO.setId(null);
        categoryDTO.setName("Comida");
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(parentTwo.getId());
        categoryDTO.setParentCategory(parentDTO);

        restCategoryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(categoryDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Comida"))
            .andExpect(jsonPath("$.parentCategory.id").value(parentTwo.getId().intValue()));
    }

    @Test
    @Transactional
    void patchCategoryIntoParentWhereSiblingNameExistsReturnsBadRequest() throws Exception {
        Category targetParent = createEntity(em);
        targetParent.setName("TARGET_PARENT");
        targetParent = categoryRepository.saveAndFlush(targetParent);

        Category sibling = createEntity(em);
        sibling.setName("Comida");
        sibling.setParentCategory(targetParent);
        categoryRepository.saveAndFlush(sibling);

        Category otherParent = createEntity(em);
        otherParent.setName("OTHER_PARENT");
        otherParent = categoryRepository.saveAndFlush(otherParent);

        Category movingCategory = createEntity(em);
        movingCategory.setName("Comida");
        movingCategory.setParentCategory(otherParent);
        insertedCategory = categoryRepository.saveAndFlush(movingCategory);

        CategoryDTO patchPayload = new CategoryDTO();
        patchPayload.setId(movingCategory.getId());
        CategoryDTO parentDTO = new CategoryDTO();
        parentDTO.setId(targetParent.getId());
        patchPayload.setParentCategory(parentDTO);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, movingCategory.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchPayload))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("category"));
    }

    @Test
    @Transactional
    void patchCategoryTypeIntoScopeWhereSiblingNameExistsReturnsBadRequest() throws Exception {
        Category expenseCategory = createEntity(em);
        expenseCategory.setName("Comida");
        expenseCategory.setCategoryType(CategoryType.EXPENSE);
        categoryRepository.saveAndFlush(expenseCategory);

        Category incomeCategory = createEntity(em);
        incomeCategory.setName("Comida");
        incomeCategory.setCategoryType(CategoryType.INCOME);
        insertedCategory = categoryRepository.saveAndFlush(incomeCategory);

        CategoryDTO patchPayload = new CategoryDTO();
        patchPayload.setId(incomeCategory.getId());
        patchPayload.setCategoryType(CategoryType.EXPENSE);

        restCategoryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, incomeCategory.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patchPayload))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("category"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminEditingForeignCategoryCannotDuplicateWithinForeignOwnerSiblingScope() throws Exception {
        User foreignOwner = createOtherUser(em);

        Category parentCategory = createEntity(em);
        parentCategory.setUser(foreignOwner);
        parentCategory.setName("FOREIGN_PARENT");
        parentCategory = categoryRepository.saveAndFlush(parentCategory);

        Category firstChild = createEntity(em);
        firstChild.setUser(foreignOwner);
        firstChild.setName("Comida");
        firstChild.setParentCategory(parentCategory);
        categoryRepository.saveAndFlush(firstChild);

        Category secondChild = createEntity(em);
        secondChild.setUser(foreignOwner);
        secondChild.setName("Viajes");
        secondChild.setParentCategory(parentCategory);
        insertedCategory = categoryRepository.saveAndFlush(secondChild);

        CategoryDTO categoryDTO = categoryMapper.toDto(secondChild);
        categoryDTO.setName("comida");

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, categoryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(categoryDTO))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("category"));
    }

    @Test
    @Transactional
    void createAndUpdateCategoryNameTrimPersists() throws Exception {
        CategoryDTO createDTO = categoryMapper.toDto(createEntity(em));
        createDTO.setId(null);
        createDTO.setName(" Comida ");

        CategoryDTO createdCategory = om.readValue(
            restCategoryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Comida"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CategoryDTO.class
        );

        insertedCategory = categoryMapper.toEntity(createdCategory);

        CategoryDTO updateDTO = categoryMapper.toDto(getPersistedCategory(insertedCategory));
        updateDTO.setName(" Viajes ");

        restCategoryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updateDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(updateDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Viajes"));

        assertThat(getPersistedCategory(insertedCategory).getName()).isEqualTo("Viajes");
    }

    protected long getRepositoryCount() {
        return categoryRepository.count();
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

    protected Category getPersistedCategory(Category category) {
        return categoryRepository.findById(category.getId()).orElseThrow();
    }

    protected void assertPersistedCategoryToMatchAllProperties(Category expectedCategory) {
        assertCategoryAllPropertiesEquals(expectedCategory, getPersistedCategory(expectedCategory));
    }

    protected void assertPersistedCategoryToMatchUpdatableProperties(Category expectedCategory) {
        assertCategoryAllUpdatablePropertiesEquals(expectedCategory, getPersistedCategory(expectedCategory));
    }
}
