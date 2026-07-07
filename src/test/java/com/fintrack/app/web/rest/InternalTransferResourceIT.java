package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.InternalTransferAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link InternalTransferResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class InternalTransferResourceIT {

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/internal-transfers";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private InternalTransferRepository internalTransferRepository;

    @Autowired
    private InternalTransferMapper internalTransferMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restInternalTransferMockMvc;

    private InternalTransfer internalTransfer;

    private InternalTransfer insertedInternalTransfer;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static InternalTransfer createEntity(EntityManager em) {
        InternalTransfer internalTransfer = new InternalTransfer().notes(DEFAULT_NOTES).createdAt(DEFAULT_CREATED_AT);
        // Add required entity
        FinancialTransaction financialTransaction;
        if (TestUtil.findAll(em, FinancialTransaction.class).isEmpty()) {
            financialTransaction = FinancialTransactionResourceIT.createEntity(em);
            em.persist(financialTransaction);
            em.flush();
        } else {
            financialTransaction = TestUtil.findAll(em, FinancialTransaction.class).get(0);
        }
        internalTransfer.setOutgoingTransaction(financialTransaction);
        // Add required entity
        internalTransfer.setIncomingTransaction(financialTransaction);
        return internalTransfer;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static InternalTransfer createUpdatedEntity(EntityManager em) {
        InternalTransfer updatedInternalTransfer = new InternalTransfer().notes(UPDATED_NOTES).createdAt(UPDATED_CREATED_AT);
        // Add required entity
        FinancialTransaction financialTransaction;
        if (TestUtil.findAll(em, FinancialTransaction.class).isEmpty()) {
            financialTransaction = FinancialTransactionResourceIT.createUpdatedEntity(em);
            em.persist(financialTransaction);
            em.flush();
        } else {
            financialTransaction = TestUtil.findAll(em, FinancialTransaction.class).get(0);
        }
        updatedInternalTransfer.setOutgoingTransaction(financialTransaction);
        // Add required entity
        updatedInternalTransfer.setIncomingTransaction(financialTransaction);
        return updatedInternalTransfer;
    }

    @BeforeEach
    void initTest() {
        internalTransfer = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedInternalTransfer != null) {
            internalTransferRepository.delete(insertedInternalTransfer);
            insertedInternalTransfer = null;
        }
    }

    @Test
    @Transactional
    void createInternalTransfer() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);
        var returnedInternalTransferDTO = om.readValue(
            restInternalTransferMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            InternalTransferDTO.class
        );

        // Validate the InternalTransfer in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedInternalTransfer = internalTransferMapper.toEntity(returnedInternalTransferDTO);
        assertInternalTransferUpdatableFieldsEquals(returnedInternalTransfer, getPersistedInternalTransfer(returnedInternalTransfer));

        insertedInternalTransfer = returnedInternalTransfer;
    }

    @Test
    @Transactional
    void createInternalTransferWithExistingId() throws Exception {
        // Create the InternalTransfer with an existing ID
        internalTransfer.setId(1L);
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        internalTransfer.setCreatedAt(null);

        // Create the InternalTransfer, which fails.
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllInternalTransfers() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        // Get all the internalTransferList
        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(internalTransfer.getId().intValue())))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @Test
    @Transactional
    void getInternalTransfer() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        // Get the internalTransfer
        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL_ID, internalTransfer.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(internalTransfer.getId().intValue()))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingInternalTransfer() throws Exception {
        // Get the internalTransfer
        restInternalTransferMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingInternalTransfer() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the internalTransfer
        InternalTransfer updatedInternalTransfer = internalTransferRepository.findById(internalTransfer.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedInternalTransfer are not directly saved in db
        em.detach(updatedInternalTransfer);
        updatedInternalTransfer.notes(UPDATED_NOTES).createdAt(UPDATED_CREATED_AT);
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(updatedInternalTransfer);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isOk());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedInternalTransferToMatchAllProperties(updatedInternalTransfer);
    }

    @Test
    @Transactional
    void putNonExistingInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateInternalTransferWithPatch() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the internalTransfer using partial update
        InternalTransfer partialUpdatedInternalTransfer = new InternalTransfer();
        partialUpdatedInternalTransfer.setId(internalTransfer.getId());

        partialUpdatedInternalTransfer.notes(UPDATED_NOTES).createdAt(UPDATED_CREATED_AT);

        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedInternalTransfer.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedInternalTransfer))
            )
            .andExpect(status().isOk());

        // Validate the InternalTransfer in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertInternalTransferUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedInternalTransfer, internalTransfer),
            getPersistedInternalTransfer(internalTransfer)
        );
    }

    @Test
    @Transactional
    void fullUpdateInternalTransferWithPatch() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the internalTransfer using partial update
        InternalTransfer partialUpdatedInternalTransfer = new InternalTransfer();
        partialUpdatedInternalTransfer.setId(internalTransfer.getId());

        partialUpdatedInternalTransfer.notes(UPDATED_NOTES).createdAt(UPDATED_CREATED_AT);

        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedInternalTransfer.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedInternalTransfer))
            )
            .andExpect(status().isOk());

        // Validate the InternalTransfer in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertInternalTransferUpdatableFieldsEquals(
            partialUpdatedInternalTransfer,
            getPersistedInternalTransfer(partialUpdatedInternalTransfer)
        );
    }

    @Test
    @Transactional
    void patchNonExistingInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        // Create the InternalTransfer
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the InternalTransfer in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteInternalTransfer() throws Exception {
        // Initialize the database
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the internalTransfer
        restInternalTransferMockMvc
            .perform(delete(ENTITY_API_URL_ID, internalTransfer.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return internalTransferRepository.count();
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

    protected InternalTransfer getPersistedInternalTransfer(InternalTransfer internalTransfer) {
        return internalTransferRepository.findById(internalTransfer.getId()).orElseThrow();
    }

    protected void assertPersistedInternalTransferToMatchAllProperties(InternalTransfer expectedInternalTransfer) {
        assertInternalTransferAllPropertiesEquals(expectedInternalTransfer, getPersistedInternalTransfer(expectedInternalTransfer));
    }

    protected void assertPersistedInternalTransferToMatchUpdatableProperties(InternalTransfer expectedInternalTransfer) {
        assertInternalTransferAllUpdatablePropertiesEquals(
            expectedInternalTransfer,
            getPersistedInternalTransfer(expectedInternalTransfer)
        );
    }
}
