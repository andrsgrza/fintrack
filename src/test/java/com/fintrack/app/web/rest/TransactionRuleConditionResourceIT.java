package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TransactionRuleConditionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.TransactionRuleConditionService;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleConditionMapper;
import jakarta.persistence.EntityManager;
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
 * Integration tests for the {@link TransactionRuleConditionResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionRuleConditionResourceIT {

    private static final TransactionRuleField DEFAULT_FIELD = TransactionRuleField.DESCRIPTION;
    private static final TransactionRuleField UPDATED_FIELD = TransactionRuleField.AMOUNT;

    private static final RuleOperator DEFAULT_OPERATOR = RuleOperator.EQUALS;
    private static final RuleOperator UPDATED_OPERATOR = RuleOperator.NOT_EQUALS;

    private static final String DEFAULT_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_VALUE = "BBBBBBBBBB";

    private static final String DEFAULT_SECOND_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_SECOND_VALUE = "BBBBBBBBBB";

    private static final Boolean DEFAULT_CASE_SENSITIVE = false;
    private static final Boolean UPDATED_CASE_SENSITIVE = true;

    private static final Integer DEFAULT_POSITION = 0;
    private static final Integer UPDATED_POSITION = 1;

    private static final String ENTITY_API_URL = "/api/transaction-rule-conditions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionRuleConditionRepository transactionRuleConditionRepository;

    @Mock
    private TransactionRuleConditionRepository transactionRuleConditionRepositoryMock;

    @Autowired
    private TransactionRuleConditionMapper transactionRuleConditionMapper;

    @Mock
    private TransactionRuleConditionService transactionRuleConditionServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionRuleConditionMockMvc;

    private TransactionRuleCondition transactionRuleCondition;

    private TransactionRuleCondition insertedTransactionRuleCondition;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRuleCondition createEntity(EntityManager em) {
        TransactionRuleCondition transactionRuleCondition = new TransactionRuleCondition()
            .field(DEFAULT_FIELD)
            .operator(DEFAULT_OPERATOR)
            .value(DEFAULT_VALUE)
            .secondValue(DEFAULT_SECOND_VALUE)
            .caseSensitive(DEFAULT_CASE_SENSITIVE)
            .position(DEFAULT_POSITION);
        TransactionRule transactionRule = TransactionRuleResourceIT.createEntity(em);
        em.persist(transactionRule);
        em.flush();
        transactionRuleCondition.setTransactionRule(transactionRule);
        return transactionRuleCondition;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRuleCondition createUpdatedEntity(EntityManager em) {
        TransactionRuleCondition updatedTransactionRuleCondition = new TransactionRuleCondition()
            .field(UPDATED_FIELD)
            .operator(UPDATED_OPERATOR)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .caseSensitive(UPDATED_CASE_SENSITIVE)
            .position(UPDATED_POSITION);
        TransactionRule transactionRule = TransactionRuleResourceIT.createUpdatedEntity(em);
        em.persist(transactionRule);
        em.flush();
        updatedTransactionRuleCondition.setTransactionRule(transactionRule);
        return updatedTransactionRuleCondition;
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    @BeforeEach
    void initTest() {
        transactionRuleCondition = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionRuleCondition != null) {
            transactionRuleConditionRepository.delete(insertedTransactionRuleCondition);
            insertedTransactionRuleCondition = null;
        }
    }

    @Test
    @Transactional
    void createTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);
        var returnedTransactionRuleConditionDTO = om.readValue(
            restTransactionRuleConditionMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRuleConditionDTO.class
        );

        // Validate the TransactionRuleCondition in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTransactionRuleCondition = transactionRuleConditionMapper.toEntity(returnedTransactionRuleConditionDTO);
        assertTransactionRuleConditionUpdatableFieldsEquals(
            returnedTransactionRuleCondition,
            getPersistedTransactionRuleCondition(returnedTransactionRuleCondition)
        );

        insertedTransactionRuleCondition = returnedTransactionRuleCondition;
    }

    @Test
    @Transactional
    void createTransactionRuleConditionWithExistingId() throws Exception {
        // Create the TransactionRuleCondition with an existing ID
        transactionRuleCondition.setId(1L);
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkFieldIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRuleCondition.setField(null);

        // Create the TransactionRuleCondition, which fails.
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkOperatorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRuleCondition.setOperator(null);

        // Create the TransactionRuleCondition, which fails.
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkValueIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRuleCondition.setValue(null);

        // Create the TransactionRuleCondition, which fails.
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCaseSensitiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRuleCondition.setCaseSensitive(null);

        // Create the TransactionRuleCondition, which fails.
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPositionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRuleCondition.setPosition(null);

        // Create the TransactionRuleCondition, which fails.
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllTransactionRuleConditions() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        // Get all the transactionRuleConditionList
        restTransactionRuleConditionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRuleCondition.getId().intValue())))
            .andExpect(jsonPath("$.[*].field").value(hasItem(DEFAULT_FIELD.toString())))
            .andExpect(jsonPath("$.[*].operator").value(hasItem(DEFAULT_OPERATOR.toString())))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.[*].secondValue").value(hasItem(DEFAULT_SECOND_VALUE)))
            .andExpect(jsonPath("$.[*].caseSensitive").value(hasItem(DEFAULT_CASE_SENSITIVE)))
            .andExpect(jsonPath("$.[*].position").value(hasItem(DEFAULT_POSITION)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRuleConditionsWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionRuleConditionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRuleConditionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionRuleConditionServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRuleConditionsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionRuleConditionServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRuleConditionMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionRuleConditionRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionRuleCondition() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        // Get the transactionRuleCondition
        restTransactionRuleConditionMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionRuleCondition.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionRuleCondition.getId().intValue()))
            .andExpect(jsonPath("$.field").value(DEFAULT_FIELD.toString()))
            .andExpect(jsonPath("$.operator").value(DEFAULT_OPERATOR.toString()))
            .andExpect(jsonPath("$.value").value(DEFAULT_VALUE))
            .andExpect(jsonPath("$.secondValue").value(DEFAULT_SECOND_VALUE))
            .andExpect(jsonPath("$.caseSensitive").value(DEFAULT_CASE_SENSITIVE))
            .andExpect(jsonPath("$.position").value(DEFAULT_POSITION));
    }

    @Test
    @Transactional
    void getNonExistingTransactionRuleCondition() throws Exception {
        // Get the transactionRuleCondition
        restTransactionRuleConditionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionRuleCondition() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRuleCondition
        TransactionRuleCondition updatedTransactionRuleCondition = transactionRuleConditionRepository
            .findById(transactionRuleCondition.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionRuleCondition are not directly saved in db
        em.detach(updatedTransactionRuleCondition);
        updatedTransactionRuleCondition
            .field(UPDATED_FIELD)
            .operator(UPDATED_OPERATOR)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .caseSensitive(UPDATED_CASE_SENSITIVE)
            .position(UPDATED_POSITION);
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(updatedTransactionRuleCondition);

        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTransactionRuleConditionToMatchAllProperties(updatedTransactionRuleCondition);
    }

    @Test
    @Transactional
    void putNonExistingTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionRuleConditionWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRuleCondition using partial update
        String patchJson =
            "{\"id\":" +
            transactionRuleCondition.getId() +
            ",\"field\":\"" +
            UPDATED_FIELD +
            "\",\"value\":\"" +
            UPDATED_VALUE +
            "\",\"secondValue\":\"" +
            UPDATED_SECOND_VALUE +
            "\",\"position\":" +
            UPDATED_POSITION +
            "}";

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleCondition.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the TransactionRuleCondition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionRuleCondition partialUpdatedTransactionRuleCondition = new TransactionRuleCondition()
            .field(UPDATED_FIELD)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .position(UPDATED_POSITION);
        partialUpdatedTransactionRuleCondition.setId(transactionRuleCondition.getId());
        assertTransactionRuleConditionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionRuleCondition, transactionRuleCondition),
            getPersistedTransactionRuleCondition(transactionRuleCondition)
        );
    }

    @Test
    @Transactional
    void fullUpdateTransactionRuleConditionWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRuleCondition using partial update
        String patchJson =
            "{\"id\":" +
            transactionRuleCondition.getId() +
            ",\"field\":\"" +
            UPDATED_FIELD +
            "\",\"operator\":\"" +
            UPDATED_OPERATOR +
            "\",\"value\":\"" +
            UPDATED_VALUE +
            "\",\"secondValue\":\"" +
            UPDATED_SECOND_VALUE +
            "\",\"caseSensitive\":" +
            UPDATED_CASE_SENSITIVE +
            ",\"position\":" +
            UPDATED_POSITION +
            "}";

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleCondition.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the TransactionRuleCondition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        TransactionRuleCondition partialUpdatedTransactionRuleCondition = new TransactionRuleCondition()
            .field(UPDATED_FIELD)
            .operator(UPDATED_OPERATOR)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .caseSensitive(UPDATED_CASE_SENSITIVE)
            .position(UPDATED_POSITION);
        partialUpdatedTransactionRuleCondition.setId(transactionRuleCondition.getId());
        assertTransactionRuleConditionUpdatableFieldsEquals(
            partialUpdatedTransactionRuleCondition,
            getPersistedTransactionRuleCondition(partialUpdatedTransactionRuleCondition)
        );
    }

    @Test
    @Transactional
    void patchNonExistingTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionRuleCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRuleCondition.setId(longCount.incrementAndGet());

        // Create the TransactionRuleCondition
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRuleCondition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionRuleCondition() throws Exception {
        // Initialize the database
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionRuleCondition
        restTransactionRuleConditionMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRuleCondition.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getTransactionRuleConditionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();

        restTransactionRuleConditionMockMvc.perform(get(ENTITY_API_URL_ID, otherCondition.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllTransactionRuleConditionsDoesNotIncludeAnotherUsersConditions() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();

        restTransactionRuleConditionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherCondition.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetTransactionRuleConditionOwnedByAnotherUser() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();

        restTransactionRuleConditionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherCondition.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherCondition.getId().intValue()));
    }

    @Test
    @Transactional
    void putTransactionRuleConditionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(otherCondition);
        transactionRuleConditionDTO.setValue(UPDATED_VALUE);

        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleConditionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();
        String patchJson = "{\"id\":" + otherCondition.getId() + ",\"value\":\"" + UPDATED_VALUE + "\"}";

        restTransactionRuleConditionMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherCondition.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteTransactionRuleConditionOwnedByAnotherUserIsNotFound() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();

        restTransactionRuleConditionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherCondition.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllTransactionRuleConditionsIncludingOtherUsers() throws Exception {
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();

        restTransactionRuleConditionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRuleCondition.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherCondition.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateTransactionRuleConditionOwnedByAnotherUser() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(otherCondition);
        transactionRuleConditionDTO.setValue(UPDATED_VALUE);

        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value").value(UPDATED_VALUE));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteTransactionRuleConditionOwnedByAnotherUser() throws Exception {
        TransactionRuleCondition otherCondition = saveConditionOnOtherUsersRule();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restTransactionRuleConditionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherCondition.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createTransactionRuleConditionWithRuleOwnedByAnotherUserFails() throws Exception {
        TransactionRule otherUsersRule = createRuleForOtherUser();
        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);
        transactionRuleConditionDTO.setId(null);
        TransactionRuleDTO transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(otherUsersRule.getId());
        transactionRuleConditionDTO.setTransactionRule(transactionRuleDTO);

        restTransactionRuleConditionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateTransactionRuleConditionWithRuleOwnedByAnotherUserFails() throws Exception {
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);
        TransactionRule otherUsersRule = createRuleForOtherUser();

        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(transactionRuleCondition);
        TransactionRuleDTO transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(otherUsersRule.getId());
        transactionRuleConditionDTO.setTransactionRule(transactionRuleDTO);

        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleConditionWithForeignRuleFails() throws Exception {
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);
        TransactionRule otherUsersRule = createRuleForOtherUser();

        String patchJson = "{\"id\":" + transactionRuleCondition.getId() + ",\"transactionRule\":{\"id\":" + otherUsersRule.getId() + "}}";

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleCondition.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanReparentTransactionRuleConditionWithinSameOwner() throws Exception {
        User otherUser = createOtherUser(em);
        TransactionRule rule1 = createRuleForUser(otherUser);
        TransactionRule rule2 = createRuleForUser(otherUser);

        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(DEFAULT_FIELD)
            .operator(DEFAULT_OPERATOR)
            .value(DEFAULT_VALUE)
            .secondValue(DEFAULT_SECOND_VALUE)
            .caseSensitive(DEFAULT_CASE_SENSITIVE)
            .position(DEFAULT_POSITION);
        condition.setTransactionRule(rule1);
        condition = transactionRuleConditionRepository.saveAndFlush(condition);

        String patchJson = "{\"id\":" + condition.getId() + ",\"transactionRule\":{\"id\":" + rule2.getId() + "}}";

        restTransactionRuleConditionMockMvc
            .perform(patch(ENTITY_API_URL_ID, condition.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionRule.id").value(rule2.getId().intValue()));

        assertThat(getPersistedTransactionRuleCondition(condition).getTransactionRule().getId()).isEqualTo(rule2.getId());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCannotReparentTransactionRuleConditionAcrossOwners() throws Exception {
        User otherUser = createOtherUser(em);
        TransactionRule otherUsersRule = createRuleForUser(otherUser);
        TransactionRule ownRule = transactionRuleCondition.getTransactionRule();

        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(DEFAULT_FIELD)
            .operator(DEFAULT_OPERATOR)
            .value(DEFAULT_VALUE)
            .secondValue(DEFAULT_SECOND_VALUE)
            .caseSensitive(DEFAULT_CASE_SENSITIVE)
            .position(DEFAULT_POSITION);
        condition.setTransactionRule(otherUsersRule);
        condition = transactionRuleConditionRepository.saveAndFlush(condition);

        TransactionRuleConditionDTO transactionRuleConditionDTO = transactionRuleConditionMapper.toDto(condition);
        TransactionRuleDTO transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(ownRule.getId());
        transactionRuleConditionDTO.setTransactionRule(transactionRuleDTO);

        restTransactionRuleConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRuleConditionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRuleConditionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchTransactionRuleConditionWithoutTransactionRuleFieldPreservesParent() throws Exception {
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);
        Long originalRuleId = transactionRuleCondition.getTransactionRule().getId();

        String patchJson = "{\"id\":" + transactionRuleCondition.getId() + ",\"value\":\"" + UPDATED_VALUE + "\"}";

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleCondition.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionRule.id").value(originalRuleId.intValue()));

        assertThat(getPersistedTransactionRuleCondition(transactionRuleCondition).getTransactionRule().getId()).isEqualTo(originalRuleId);
    }

    @Test
    @Transactional
    void patchTransactionRuleConditionWithNullTransactionRuleFails() throws Exception {
        insertedTransactionRuleCondition = transactionRuleConditionRepository.saveAndFlush(transactionRuleCondition);

        String patchJson = "{\"id\":" + transactionRuleCondition.getId() + ",\"transactionRule\":null}";

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRuleCondition.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    private TransactionRuleCondition saveConditionOnOtherUsersRule() {
        TransactionRule otherUsersRule = createRuleForOtherUser();
        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(DEFAULT_FIELD)
            .operator(DEFAULT_OPERATOR)
            .value(DEFAULT_VALUE)
            .secondValue(DEFAULT_SECOND_VALUE)
            .caseSensitive(DEFAULT_CASE_SENSITIVE)
            .position(DEFAULT_POSITION);
        condition.setTransactionRule(otherUsersRule);
        return transactionRuleConditionRepository.saveAndFlush(condition);
    }

    private TransactionRule createRuleForOtherUser() {
        return createRuleForUser(createOtherUser(em));
    }

    private TransactionRule createRuleForUser(User user) {
        TransactionRule rule = TransactionRuleResourceIT.createEntity(em);
        rule.setUser(user);
        em.persist(rule);
        em.flush();
        return rule;
    }

    protected long getRepositoryCount() {
        return transactionRuleConditionRepository.count();
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

    protected TransactionRuleCondition getPersistedTransactionRuleCondition(TransactionRuleCondition transactionRuleCondition) {
        return transactionRuleConditionRepository.findById(transactionRuleCondition.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionRuleConditionToMatchAllProperties(TransactionRuleCondition expectedTransactionRuleCondition) {
        assertTransactionRuleConditionAllPropertiesEquals(
            expectedTransactionRuleCondition,
            getPersistedTransactionRuleCondition(expectedTransactionRuleCondition)
        );
    }

    protected void assertPersistedTransactionRuleConditionToMatchUpdatableProperties(
        TransactionRuleCondition expectedTransactionRuleCondition
    ) {
        assertTransactionRuleConditionAllUpdatablePropertiesEquals(
            expectedTransactionRuleCondition,
            getPersistedTransactionRuleCondition(expectedTransactionRuleCondition)
        );
    }
}
