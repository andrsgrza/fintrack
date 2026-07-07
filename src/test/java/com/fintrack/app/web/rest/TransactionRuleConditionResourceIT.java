package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.TransactionRuleConditionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.service.TransactionRuleConditionService;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
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
        // Add required entity
        TransactionRule transactionRule;
        if (TestUtil.findAll(em, TransactionRule.class).isEmpty()) {
            transactionRule = TransactionRuleResourceIT.createEntity(em);
            em.persist(transactionRule);
            em.flush();
        } else {
            transactionRule = TestUtil.findAll(em, TransactionRule.class).get(0);
        }
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
        // Add required entity
        TransactionRule transactionRule;
        if (TestUtil.findAll(em, TransactionRule.class).isEmpty()) {
            transactionRule = TransactionRuleResourceIT.createUpdatedEntity(em);
            em.persist(transactionRule);
            em.flush();
        } else {
            transactionRule = TestUtil.findAll(em, TransactionRule.class).get(0);
        }
        updatedTransactionRuleCondition.setTransactionRule(transactionRule);
        return updatedTransactionRuleCondition;
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
        TransactionRuleCondition partialUpdatedTransactionRuleCondition = new TransactionRuleCondition();
        partialUpdatedTransactionRuleCondition.setId(transactionRuleCondition.getId());

        partialUpdatedTransactionRuleCondition
            .field(UPDATED_FIELD)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .position(UPDATED_POSITION);

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRuleCondition.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRuleCondition))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRuleCondition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
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
        TransactionRuleCondition partialUpdatedTransactionRuleCondition = new TransactionRuleCondition();
        partialUpdatedTransactionRuleCondition.setId(transactionRuleCondition.getId());

        partialUpdatedTransactionRuleCondition
            .field(UPDATED_FIELD)
            .operator(UPDATED_OPERATOR)
            .value(UPDATED_VALUE)
            .secondValue(UPDATED_SECOND_VALUE)
            .caseSensitive(UPDATED_CASE_SENSITIVE)
            .position(UPDATED_POSITION);

        restTransactionRuleConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRuleCondition.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRuleCondition))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRuleCondition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
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
