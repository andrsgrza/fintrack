package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.CreditAccountDetailsAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.CreditAccountDetailsService;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.CreditAccountDetailsMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * Integration tests for the {@link CreditAccountDetailsResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class CreditAccountDetailsResourceIT {

    private static final BigDecimal DEFAULT_CREDIT_LIMIT = new BigDecimal(0);
    private static final BigDecimal UPDATED_CREDIT_LIMIT = new BigDecimal(1);

    private static final Integer DEFAULT_STATEMENT_DAY = 1;
    private static final Integer UPDATED_STATEMENT_DAY = 2;

    private static final Integer DEFAULT_PAYMENT_DUE_DAY = 1;
    private static final Integer UPDATED_PAYMENT_DUE_DAY = 2;

    private static final BigDecimal DEFAULT_ANNUAL_INTEREST_RATE = new BigDecimal(0);
    private static final BigDecimal UPDATED_ANNUAL_INTEREST_RATE = new BigDecimal(1);

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/credit-account-details";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepositoryMock;

    @Autowired
    private CreditAccountDetailsMapper creditAccountDetailsMapper;

    @Mock
    private CreditAccountDetailsService creditAccountDetailsServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restCreditAccountDetailsMockMvc;

    private CreditAccountDetails creditAccountDetails;

    private CreditAccountDetails insertedCreditAccountDetails;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CreditAccountDetails createEntity(EntityManager em) {
        CreditAccountDetails creditAccountDetails = new CreditAccountDetails()
            .creditLimit(DEFAULT_CREDIT_LIMIT)
            .statementDay(DEFAULT_STATEMENT_DAY)
            .paymentDueDay(DEFAULT_PAYMENT_DUE_DAY)
            .annualInterestRate(DEFAULT_ANNUAL_INTEREST_RATE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        // Add required entity
        FinancialAccount financialAccount = createCreditCardAccount(em);
        creditAccountDetails.setAccount(financialAccount);
        return creditAccountDetails;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CreditAccountDetails createUpdatedEntity(EntityManager em) {
        CreditAccountDetails updatedCreditAccountDetails = new CreditAccountDetails()
            .creditLimit(UPDATED_CREDIT_LIMIT)
            .statementDay(UPDATED_STATEMENT_DAY)
            .paymentDueDay(UPDATED_PAYMENT_DUE_DAY)
            .annualInterestRate(UPDATED_ANNUAL_INTEREST_RATE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        FinancialAccount financialAccount = FinancialAccountResourceIT.createUpdatedEntity(em);
        financialAccount.setAccountType(AccountType.CREDIT_CARD);
        em.persist(financialAccount);
        em.flush();
        updatedCreditAccountDetails.setAccount(financialAccount);
        return updatedCreditAccountDetails;
    }

    private static FinancialAccount createCreditCardAccount(EntityManager em) {
        FinancialAccount financialAccount = FinancialAccountResourceIT.createEntity(em);
        financialAccount.setAccountType(AccountType.CREDIT_CARD);
        em.persist(financialAccount);
        em.flush();
        return financialAccount;
    }

    private static FinancialAccount createCreditCardAccountForUser(EntityManager em, User user) {
        FinancialAccount financialAccount = FinancialAccountResourceIT.createEntity(em);
        financialAccount.setAccountType(AccountType.CREDIT_CARD);
        financialAccount.setUser(user);
        em.persist(financialAccount);
        em.flush();
        return financialAccount;
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    @BeforeEach
    void initTest() {
        creditAccountDetails = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedCreditAccountDetails != null) {
            creditAccountDetailsRepository.delete(insertedCreditAccountDetails);
            insertedCreditAccountDetails = null;
        }
    }

    @Test
    @Transactional
    void createCreditAccountDetails() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setCreatedAt(null);
        creditAccountDetailsDTO.setUpdatedAt(null);
        Instant beforeCreate = Instant.now();
        var returnedCreditAccountDetailsDTO = om.readValue(
            restCreditAccountDetailsMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CreditAccountDetailsDTO.class
        );
        Instant afterCreate = Instant.now();

        // Validate the CreditAccountDetails in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedCreditAccountDetails = creditAccountDetailsMapper.toEntity(returnedCreditAccountDetailsDTO);
        assertCreditAccountDetailsUpdatableFieldsEquals(
            returnedCreditAccountDetails,
            getPersistedCreditAccountDetails(returnedCreditAccountDetails)
        );
        CreditAccountDetails persistedCreditAccountDetails = getPersistedCreditAccountDetails(returnedCreditAccountDetails);
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isBetween(beforeCreate, afterCreate);

        insertedCreditAccountDetails = returnedCreditAccountDetails;
    }

    @Test
    @Transactional
    void createCreditAccountDetailsIgnoresClientProvidedTimestamps() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setCreatedAt(DEFAULT_CREATED_AT);
        creditAccountDetailsDTO.setUpdatedAt(DEFAULT_UPDATED_AT);

        Instant beforeCreate = Instant.now();
        var returnedCreditAccountDetailsDTO = om.readValue(
            restCreditAccountDetailsMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CreditAccountDetailsDTO.class
        );
        Instant afterCreate = Instant.now();

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        CreditAccountDetails persistedCreditAccountDetails = getPersistedCreditAccountDetails(
            creditAccountDetailsMapper.toEntity(returnedCreditAccountDetailsDTO)
        );
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isNotEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isNotEqualTo(DEFAULT_UPDATED_AT);

        insertedCreditAccountDetails = persistedCreditAccountDetails;
    }

    @Test
    @Transactional
    void createCreditAccountDetailsWithExistingId() throws Exception {
        // Create the CreditAccountDetails with an existing ID
        creditAccountDetails.setId(1L);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkCreditLimitIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        creditAccountDetails.setCreditLimit(null);

        // Create the CreditAccountDetails, which fails.
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatementDayIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        creditAccountDetails.setStatementDay(null);

        // Create the CreditAccountDetails, which fails.
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPaymentDueDayIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        creditAccountDetails.setPaymentDueDay(null);

        // Create the CreditAccountDetails, which fails.
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createCreditAccountDetailsWithoutCreatedAtSucceeds() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        creditAccountDetails.setCreatedAt(null);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        var returnedCreditAccountDetailsDTO = om.readValue(
            restCreditAccountDetailsMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CreditAccountDetailsDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        insertedCreditAccountDetails = creditAccountDetailsMapper.toEntity(returnedCreditAccountDetailsDTO);
    }

    @Test
    @Transactional
    void createCreditAccountDetailsWithoutUpdatedAtSucceeds() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        creditAccountDetails.setUpdatedAt(null);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        var returnedCreditAccountDetailsDTO = om.readValue(
            restCreditAccountDetailsMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CreditAccountDetailsDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        insertedCreditAccountDetails = creditAccountDetailsMapper.toEntity(returnedCreditAccountDetailsDTO);
    }

    @Test
    @Transactional
    void putCreditAccountDetailsWithNullCreatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setCreatedAt(null);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void putCreditAccountDetailsWithNullUpdatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setUpdatedAt(null);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void getAllCreditAccountDetails() throws Exception {
        // Initialize the database
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        // Get all the creditAccountDetailsList
        restCreditAccountDetailsMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(creditAccountDetails.getId().intValue())))
            .andExpect(jsonPath("$.[*].creditLimit").value(hasItem(sameNumber(DEFAULT_CREDIT_LIMIT))))
            .andExpect(jsonPath("$.[*].statementDay").value(hasItem(DEFAULT_STATEMENT_DAY)))
            .andExpect(jsonPath("$.[*].paymentDueDay").value(hasItem(DEFAULT_PAYMENT_DUE_DAY)))
            .andExpect(jsonPath("$.[*].annualInterestRate").value(hasItem(sameNumber(DEFAULT_ANNUAL_INTEREST_RATE))))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllCreditAccountDetailsWithEagerRelationshipsIsEnabled() throws Exception {
        when(creditAccountDetailsServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restCreditAccountDetailsMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(creditAccountDetailsServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllCreditAccountDetailsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(creditAccountDetailsServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restCreditAccountDetailsMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(creditAccountDetailsRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getCreditAccountDetails() throws Exception {
        // Initialize the database
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        // Get the creditAccountDetails
        restCreditAccountDetailsMockMvc
            .perform(get(ENTITY_API_URL_ID, creditAccountDetails.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(creditAccountDetails.getId().intValue()))
            .andExpect(jsonPath("$.creditLimit").value(sameNumber(DEFAULT_CREDIT_LIMIT)))
            .andExpect(jsonPath("$.statementDay").value(DEFAULT_STATEMENT_DAY))
            .andExpect(jsonPath("$.paymentDueDay").value(DEFAULT_PAYMENT_DUE_DAY))
            .andExpect(jsonPath("$.annualInterestRate").value(sameNumber(DEFAULT_ANNUAL_INTEREST_RATE)))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingCreditAccountDetails() throws Exception {
        // Get the creditAccountDetails
        restCreditAccountDetailsMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingCreditAccountDetails() throws Exception {
        // Initialize the database
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the creditAccountDetails
        CreditAccountDetails updatedCreditAccountDetails = creditAccountDetailsRepository
            .findById(creditAccountDetails.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedCreditAccountDetails are not directly saved in db
        em.detach(updatedCreditAccountDetails);
        updatedCreditAccountDetails
            .creditLimit(UPDATED_CREDIT_LIMIT)
            .statementDay(UPDATED_STATEMENT_DAY)
            .paymentDueDay(UPDATED_PAYMENT_DUE_DAY)
            .annualInterestRate(UPDATED_ANNUAL_INTEREST_RATE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(updatedCreditAccountDetails);

        Instant beforeUpdate = Instant.now();
        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isOk());
        Instant afterUpdate = Instant.now();

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        CreditAccountDetails persistedCreditAccountDetails = getPersistedCreditAccountDetails(updatedCreditAccountDetails);
        assertThat(persistedCreditAccountDetails.getCreditLimit()).isEqualByComparingTo(UPDATED_CREDIT_LIMIT);
        assertThat(persistedCreditAccountDetails.getStatementDay()).isEqualTo(UPDATED_STATEMENT_DAY);
        assertThat(persistedCreditAccountDetails.getPaymentDueDay()).isEqualTo(UPDATED_PAYMENT_DUE_DAY);
        assertThat(persistedCreditAccountDetails.getAnnualInterestRate()).isEqualByComparingTo(UPDATED_ANNUAL_INTEREST_RATE);
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isBetween(beforeUpdate, afterUpdate);
    }

    @Test
    @Transactional
    void putCreditAccountDetailsWithChangedCreatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setCreatedAt(UPDATED_CREATED_AT);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void putCreditAccountDetailsWithChangedUpdatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setUpdatedAt(UPDATED_UPDATED_AT);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void putNonExistingCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateCreditAccountDetailsWithPatch() throws Exception {
        // Initialize the database
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the creditAccountDetails using partial update
        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"annualInterestRate\":" + UPDATED_ANNUAL_INTEREST_RATE + "}";

        Instant beforeUpdate = Instant.now();
        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());
        Instant afterUpdate = Instant.now();

        // Validate the CreditAccountDetails in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        CreditAccountDetails persistedCreditAccountDetails = getPersistedCreditAccountDetails(creditAccountDetails);
        assertThat(persistedCreditAccountDetails.getAnnualInterestRate()).isEqualByComparingTo(UPDATED_ANNUAL_INTEREST_RATE);
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isBetween(beforeUpdate, afterUpdate);
    }

    @Test
    @Transactional
    void fullUpdateCreditAccountDetailsWithPatch() throws Exception {
        // Initialize the database
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the creditAccountDetails using partial update
        String patchJson =
            "{\"id\":" +
            creditAccountDetails.getId() +
            ",\"creditLimit\":" +
            UPDATED_CREDIT_LIMIT +
            ",\"statementDay\":" +
            UPDATED_STATEMENT_DAY +
            ",\"paymentDueDay\":" +
            UPDATED_PAYMENT_DUE_DAY +
            ",\"annualInterestRate\":" +
            UPDATED_ANNUAL_INTEREST_RATE +
            ",\"createdAt\":\"" +
            DEFAULT_CREATED_AT +
            "\",\"updatedAt\":\"" +
            DEFAULT_UPDATED_AT +
            "\"}";

        Instant beforeUpdate = Instant.now();
        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());
        Instant afterUpdate = Instant.now();

        // Validate the CreditAccountDetails in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        CreditAccountDetails persistedCreditAccountDetails = getPersistedCreditAccountDetails(creditAccountDetails);
        assertThat(persistedCreditAccountDetails.getCreditLimit()).isEqualByComparingTo(UPDATED_CREDIT_LIMIT);
        assertThat(persistedCreditAccountDetails.getStatementDay()).isEqualTo(UPDATED_STATEMENT_DAY);
        assertThat(persistedCreditAccountDetails.getPaymentDueDay()).isEqualTo(UPDATED_PAYMENT_DUE_DAY);
        assertThat(persistedCreditAccountDetails.getAnnualInterestRate()).isEqualByComparingTo(UPDATED_ANNUAL_INTEREST_RATE);
        assertThat(persistedCreditAccountDetails.getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
        assertThat(persistedCreditAccountDetails.getUpdatedAt()).isBetween(beforeUpdate, afterUpdate);
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithChangedCreatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"createdAt\":\"" + UPDATED_CREATED_AT + "\"}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedCreditAccountDetails(creditAccountDetails).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithChangedUpdatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"updatedAt\":\"" + UPDATED_UPDATED_AT + "\"}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedCreditAccountDetails(creditAccountDetails).getUpdatedAt()).isEqualTo(DEFAULT_UPDATED_AT);
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithNullCreatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"createdAt\":null}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedCreditAccountDetails(creditAccountDetails).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithNullUpdatedAtFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"updatedAt\":null}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());

        assertThat(getPersistedCreditAccountDetails(creditAccountDetails).getUpdatedAt()).isEqualTo(DEFAULT_UPDATED_AT);
    }

    @Test
    @Transactional
    void patchNonExistingCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamCreditAccountDetails() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        creditAccountDetails.setId(longCount.incrementAndGet());

        // Create the CreditAccountDetails
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCreditAccountDetailsMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the CreditAccountDetails in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteCreditAccountDetailsIsNotAllowed() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restCreditAccountDetailsMockMvc
            .perform(delete(ENTITY_API_URL_ID, creditAccountDetails.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("creditAccountDetails"));

        assertSameRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getCreditAccountDetailsOwnedByAnotherUserIsNotFound() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();

        restCreditAccountDetailsMockMvc.perform(get(ENTITY_API_URL_ID, otherDetails.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllCreditAccountDetailsDoesNotIncludeAnotherUsersDetails() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();

        restCreditAccountDetailsMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherDetails.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetCreditAccountDetailsOwnedByAnotherUser() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();

        restCreditAccountDetailsMockMvc
            .perform(get(ENTITY_API_URL_ID, otherDetails.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherDetails.getId().intValue()));
    }

    @Test
    @Transactional
    void putCreditAccountDetailsOwnedByAnotherUserIsNotFound() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(otherDetails);
        creditAccountDetailsDTO.setCreditLimit(UPDATED_CREDIT_LIMIT);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsOwnedByAnotherUserIsNotFound() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();
        String patchJson = "{\"id\":" + otherDetails.getId() + ",\"creditLimit\":" + UPDATED_CREDIT_LIMIT + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteCreditAccountDetailsOwnedByAnotherUserIsNotFound() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();

        restCreditAccountDetailsMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherDetails.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllCreditAccountDetailsIncludingOtherUsers() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();

        restCreditAccountDetailsMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(creditAccountDetails.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherDetails.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateCreditAccountDetailsOwnedByAnotherUser() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(otherDetails);
        creditAccountDetailsDTO.setCreditLimit(UPDATED_CREDIT_LIMIT);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creditLimit").value(UPDATED_CREDIT_LIMIT.intValue()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminDeleteCreditAccountDetailsOwnedByAnotherUserIsNotAllowed() throws Exception {
        CreditAccountDetails otherDetails = saveDetailsOnOtherUsersAccount();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restCreditAccountDetailsMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherDetails.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"))
            .andExpect(jsonPath("$.params").value("creditAccountDetails"));

        assertSameRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createCreditAccountDetailsWithAccountOwnedByAnotherUserFails() throws Exception {
        FinancialAccount otherUsersAccount = createCreditCardAccountForUser(em, createOtherUser(em));
        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(otherUsersAccount.getId());
        creditAccountDetailsDTO.setAccount(accountDTO);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createCreditAccountDetailsWithNonCreditCardAccountFails() throws Exception {
        FinancialAccount debitAccount = FinancialAccountResourceIT.createEntity(em);
        em.persist(debitAccount);
        em.flush();

        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        creditAccountDetailsDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(debitAccount.getId());
        creditAccountDetailsDTO.setAccount(accountDTO);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createCreditAccountDetailsForAccountThatAlreadyHasDetailsFails() throws Exception {
        FinancialAccount creditCardAccount = creditAccountDetails.getAccount();
        creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(createEntity(em));
        creditAccountDetailsDTO.setId(null);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(creditCardAccount.getId());
        creditAccountDetailsDTO.setAccount(accountDTO);

        restCreditAccountDetailsMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(creditAccountDetailsDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateCreditAccountDetailsWithDifferentAccountFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        FinancialAccount anotherCreditCardAccount = createCreditCardAccount(em);

        CreditAccountDetailsDTO creditAccountDetailsDTO = creditAccountDetailsMapper.toDto(creditAccountDetails);
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(anotherCreditCardAccount.getId());
        creditAccountDetailsDTO.setAccount(accountDTO);

        restCreditAccountDetailsMockMvc
            .perform(
                put(ENTITY_API_URL_ID, creditAccountDetailsDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(creditAccountDetailsDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithDifferentAccountFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        FinancialAccount anotherCreditCardAccount = createCreditCardAccount(em);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"account\":{\"id\":" + anotherCreditCardAccount.getId() + "}}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithoutAccountFieldPreservesParent() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);
        Long originalAccountId = creditAccountDetails.getAccount().getId();

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"creditLimit\":" + UPDATED_CREDIT_LIMIT + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id").value(originalAccountId.intValue()));

        assertThat(getPersistedCreditAccountDetails(creditAccountDetails).getAccount().getId()).isEqualTo(originalAccountId);
    }

    @Test
    @Transactional
    void patchCreditAccountDetailsWithNullAccountFails() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"account\":null}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchCreditLimitIsAllowed() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"creditLimit\":" + UPDATED_CREDIT_LIMIT + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creditLimit").value(UPDATED_CREDIT_LIMIT.intValue()));
    }

    @Test
    @Transactional
    void patchStatementDayIsAllowed() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"statementDay\":" + UPDATED_STATEMENT_DAY + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statementDay").value(UPDATED_STATEMENT_DAY));
    }

    @Test
    @Transactional
    void patchPaymentDueDayIsAllowed() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"paymentDueDay\":" + UPDATED_PAYMENT_DUE_DAY + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentDueDay").value(UPDATED_PAYMENT_DUE_DAY));
    }

    @Test
    @Transactional
    void patchAnnualInterestRateIsAllowed() throws Exception {
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"annualInterestRate\":" + UPDATED_ANNUAL_INTEREST_RATE + "}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.annualInterestRate").value(UPDATED_ANNUAL_INTEREST_RATE.intValue()));
    }

    @Test
    @Transactional
    void loweringCreditLimitBelowOutstandingBalanceIsAllowed() throws Exception {
        creditAccountDetails.creditLimit(new BigDecimal("10000"));
        insertedCreditAccountDetails = creditAccountDetailsRepository.saveAndFlush(creditAccountDetails);

        String patchJson = "{\"id\":" + creditAccountDetails.getId() + ",\"creditLimit\":1}";

        restCreditAccountDetailsMockMvc
            .perform(patch(ENTITY_API_URL_ID, creditAccountDetails.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creditLimit").value(1));
    }

    private CreditAccountDetails saveDetailsOnOtherUsersAccount() {
        FinancialAccount otherUsersAccount = createCreditCardAccountForUser(em, createOtherUser(em));
        return saveDetailsOnAccount(otherUsersAccount);
    }

    private CreditAccountDetails saveDetailsOnAccount(FinancialAccount account) {
        CreditAccountDetails details = new CreditAccountDetails()
            .creditLimit(DEFAULT_CREDIT_LIMIT)
            .statementDay(DEFAULT_STATEMENT_DAY)
            .paymentDueDay(DEFAULT_PAYMENT_DUE_DAY)
            .annualInterestRate(DEFAULT_ANNUAL_INTEREST_RATE)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        details.setAccount(account);
        return creditAccountDetailsRepository.saveAndFlush(details);
    }

    protected long getRepositoryCount() {
        return creditAccountDetailsRepository.count();
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

    protected CreditAccountDetails getPersistedCreditAccountDetails(CreditAccountDetails creditAccountDetails) {
        return creditAccountDetailsRepository.findById(creditAccountDetails.getId()).orElseThrow();
    }

    protected void assertPersistedCreditAccountDetailsToMatchAllProperties(CreditAccountDetails expectedCreditAccountDetails) {
        assertCreditAccountDetailsAllPropertiesEquals(
            expectedCreditAccountDetails,
            getPersistedCreditAccountDetails(expectedCreditAccountDetails)
        );
    }

    protected void assertPersistedCreditAccountDetailsToMatchUpdatableProperties(CreditAccountDetails expectedCreditAccountDetails) {
        assertCreditAccountDetailsAllUpdatablePropertiesEquals(
            expectedCreditAccountDetails,
            getPersistedCreditAccountDetails(expectedCreditAccountDetails)
        );
    }
}
