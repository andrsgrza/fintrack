package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.InternalTransferAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100.00");

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

    public record TransferLegPair(FinancialTransaction outgoing, FinancialTransaction incoming) {}

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static InternalTransfer createEntity(EntityManager em) {
        TransferLegPair legs = createTransferLegPair(em);
        return new InternalTransfer()
            .notes(DEFAULT_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .outgoingTransaction(legs.outgoing())
            .incomingTransaction(legs.incoming());
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static InternalTransfer createUpdatedEntity(EntityManager em) {
        TransferLegPair legs = createTransferLegPair(em);
        return new InternalTransfer()
            .notes(UPDATED_NOTES)
            .createdAt(UPDATED_CREATED_AT)
            .outgoingTransaction(legs.outgoing())
            .incomingTransaction(legs.incoming());
    }

    public static TransferLegPair createTransferLegPair(EntityManager em) {
        FinancialAccount outgoingAccount = FinancialAccountResourceIT.createEntity(em);
        em.persist(outgoingAccount);
        FinancialAccount incomingAccount = FinancialAccountResourceIT.createEntity(em);
        incomingAccount.setCurrency(outgoingAccount.getCurrency());
        incomingAccount.setUser(outgoingAccount.getUser());
        em.persist(incomingAccount);
        em.flush();

        FinancialTransaction outgoing = createManualTransaction(em, outgoingAccount, TransactionFlow.OUT, TRANSFER_AMOUNT);
        FinancialTransaction incoming = createManualTransaction(em, incomingAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(outgoing);
        em.persist(incoming);
        em.flush();
        return new TransferLegPair(outgoing, incoming);
    }

    public static FinancialTransaction createManualTransaction(
        EntityManager em,
        FinancialAccount account,
        TransactionFlow flow,
        BigDecimal amount
    ) {
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(account);
        financialTransaction.setFlow(flow);
        financialTransaction.setOrigin(TransactionOrigin.MANUAL);
        financialTransaction.setAmount(amount);
        return financialTransaction;
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

    private InternalTransferDTO toCreateDto(TransferLegPair legs) {
        InternalTransferDTO internalTransferDTO = new InternalTransferDTO();
        internalTransferDTO.setNotes(DEFAULT_NOTES);
        FinancialTransactionDTO outgoingTransactionDTO = new FinancialTransactionDTO();
        outgoingTransactionDTO.setId(legs.outgoing().getId());
        FinancialTransactionDTO incomingTransactionDTO = new FinancialTransactionDTO();
        incomingTransactionDTO.setId(legs.incoming().getId());
        internalTransferDTO.setOutgoingTransaction(outgoingTransactionDTO);
        internalTransferDTO.setIncomingTransaction(incomingTransactionDTO);
        return internalTransferDTO;
    }

    private InternalTransfer saveTransferOnOtherUsersTransactions() {
        User otherUser = createOtherUser(em);
        FinancialAccount outgoingAccount = FinancialAccountResourceIT.createEntity(em);
        outgoingAccount.setUser(otherUser);
        em.persist(outgoingAccount);
        FinancialAccount incomingAccount = FinancialAccountResourceIT.createEntity(em);
        incomingAccount.setUser(otherUser);
        incomingAccount.setCurrency(outgoingAccount.getCurrency());
        em.persist(incomingAccount);
        em.flush();

        FinancialTransaction outgoing = createManualTransaction(em, outgoingAccount, TransactionFlow.OUT, TRANSFER_AMOUNT);
        FinancialTransaction incoming = createManualTransaction(em, incomingAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(outgoing);
        em.persist(incoming);
        em.flush();

        InternalTransfer transfer = new InternalTransfer()
            .notes(DEFAULT_NOTES)
            .createdAt(DEFAULT_CREATED_AT)
            .outgoingTransaction(outgoing)
            .incomingTransaction(incoming);
        return internalTransferRepository.saveAndFlush(transfer);
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
        TransferLegPair legs = createTransferLegPair(em);
        long databaseSizeBeforeCreate = getRepositoryCount();
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);

        var returnedInternalTransferDTO = om.readValue(
            restInternalTransferMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            InternalTransferDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedInternalTransferDTO.getCreatedAt()).isNotNull();
        assertThat(returnedInternalTransferDTO.getNotes()).isEqualTo(DEFAULT_NOTES);
        assertThat(returnedInternalTransferDTO.getOutgoingTransaction().getId()).isEqualTo(legs.outgoing().getId());
        assertThat(returnedInternalTransferDTO.getIncomingTransaction().getId()).isEqualTo(legs.incoming().getId());

        insertedInternalTransfer = internalTransferRepository.findById(returnedInternalTransferDTO.getId()).orElseThrow();
    }

    @Test
    @Transactional
    void createInternalTransferWithoutCreatedAtSucceeds() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        insertedInternalTransfer = internalTransferRepository.findAll().get(internalTransferRepository.findAll().size() - 1);
    }

    @Test
    @Transactional
    void createInternalTransferWithExistingId() throws Exception {
        internalTransfer.setId(1L);
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        long databaseSizeBeforeCreate = getRepositoryCount();

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllInternalTransfers() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(internalTransfer.getId().intValue())))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)));
    }

    @Test
    @Transactional
    void getInternalTransfer() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL_ID, internalTransfer.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(internalTransfer.getId().intValue()))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES));
    }

    @Test
    @Transactional
    void getNonExistingInternalTransfer() throws Exception {
        restInternalTransferMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingInternalTransfer() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        InternalTransfer updatedInternalTransfer = internalTransferRepository.findById(internalTransfer.getId()).orElseThrow();
        em.detach(updatedInternalTransfer);
        updatedInternalTransfer.notes(UPDATED_NOTES);
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(updatedInternalTransfer);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value(UPDATED_NOTES))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertThat(getPersistedInternalTransfer(internalTransfer).getNotes()).isEqualTo(UPDATED_NOTES);
        assertThat(getPersistedInternalTransfer(internalTransfer).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void putNonExistingInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isMethodNotAllowed());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateInternalTransferWithPatch() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeUpdate = getRepositoryCount();
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"notes\":\"" + UPDATED_NOTES + "\"}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value(UPDATED_NOTES));

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertThat(getPersistedInternalTransfer(internalTransfer).getNotes()).isEqualTo(UPDATED_NOTES);
        assertThat(getPersistedInternalTransfer(internalTransfer).getCreatedAt()).isEqualTo(DEFAULT_CREATED_AT);
    }

    @Test
    @Transactional
    void patchNonExistingInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamInternalTransfer() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        internalTransfer.setId(longCount.incrementAndGet());

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isMethodNotAllowed());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteInternalTransfer() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        long databaseSizeBeforeDelete = getRepositoryCount();

        restInternalTransferMockMvc
            .perform(delete(ENTITY_API_URL_ID, internalTransfer.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedInternalTransfer = null;
    }

    @Test
    @Transactional
    void getInternalTransferOwnedByAnotherUserIsNotFound() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();

        restInternalTransferMockMvc.perform(get(ENTITY_API_URL_ID, otherTransfer.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllInternalTransfersDoesNotIncludeAnotherUsersTransfers() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherTransfer.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetInternalTransferOwnedByAnotherUser() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL_ID, otherTransfer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherTransfer.getId().intValue()));
    }

    @Test
    @Transactional
    void putInternalTransferOwnedByAnotherUserIsNotFound() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(otherTransfer);
        internalTransferDTO.setNotes(UPDATED_NOTES);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchInternalTransferOwnedByAnotherUserIsNotFound() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();
        String patchJson = "{\"id\":" + otherTransfer.getId() + ",\"notes\":\"" + UPDATED_NOTES + "\"}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteInternalTransferOwnedByAnotherUserIsNotFound() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();

        restInternalTransferMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherTransfer.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllInternalTransfersIncludingOtherUsers() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(internalTransfer.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherTransfer.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateInternalTransferOwnedByAnotherUser() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();
        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(otherTransfer);
        internalTransferDTO.setNotes(UPDATED_NOTES);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value(UPDATED_NOTES));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteInternalTransferOwnedByAnotherUser() throws Exception {
        InternalTransfer otherTransfer = saveTransferOnOtherUsersTransactions();
        long databaseSizeBeforeDelete = getRepositoryCount();

        restInternalTransferMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherTransfer.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void createInternalTransferWithOutgoingTransactionOwnedByAnotherUserFails() throws Exception {
        User otherUser = createOtherUser(em);
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(otherUser);
        em.persist(otherAccount);
        em.flush();
        FinancialTransaction otherOutgoing = createManualTransaction(em, otherAccount, TransactionFlow.OUT, TRANSFER_AMOUNT);
        em.persist(otherOutgoing);
        em.flush();

        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO otherOutgoingDTO = new FinancialTransactionDTO();
        otherOutgoingDTO.setId(otherOutgoing.getId());
        internalTransferDTO.setOutgoingTransaction(otherOutgoingDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithSameTransactionFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO sameTransactionDTO = new FinancialTransactionDTO();
        sameTransactionDTO.setId(legs.outgoing().getId());
        internalTransferDTO.setIncomingTransaction(sameTransactionDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithDifferentCurrencyFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        FinancialAccount usdAccount = FinancialAccountResourceIT.createEntity(em);
        usdAccount.setCurrency(CurrencyCode.USD);
        usdAccount.setUser(getCurrentMockUser(em));
        em.persist(usdAccount);
        em.flush();
        FinancialTransaction incomingUsd = createManualTransaction(em, usdAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(incomingUsd);
        em.flush();

        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO incomingUsdDTO = new FinancialTransactionDTO();
        incomingUsdDTO.setId(incomingUsd.getId());
        internalTransferDTO.setIncomingTransaction(incomingUsdDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithDifferentAmountFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        FinancialTransaction mismatchedIncoming = createManualTransaction(
            em,
            legs.incoming().getAccount(),
            TransactionFlow.IN,
            new BigDecimal("200.00")
        );
        em.persist(mismatchedIncoming);
        em.flush();

        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO mismatchedIncomingDTO = new FinancialTransactionDTO();
        mismatchedIncomingDTO.setId(mismatchedIncoming.getId());
        internalTransferDTO.setIncomingTransaction(mismatchedIncomingDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithWrongFlowsFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        FinancialTransaction wrongFlow = createManualTransaction(em, legs.incoming().getAccount(), TransactionFlow.OUT, TRANSFER_AMOUNT);
        em.persist(wrongFlow);
        em.flush();

        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO wrongFlowDTO = new FinancialTransactionDTO();
        wrongFlowDTO.setId(wrongFlow.getId());
        internalTransferDTO.setIncomingTransaction(wrongFlowDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithSameAccountFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        FinancialTransaction incomingSameAccount = createManualTransaction(
            em,
            legs.outgoing().getAccount(),
            TransactionFlow.IN,
            TRANSFER_AMOUNT
        );
        em.persist(incomingSameAccount);
        em.flush();

        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO incomingSameAccountDTO = new FinancialTransactionDTO();
        incomingSameAccountDTO.setId(incomingSameAccount.getId());
        internalTransferDTO.setIncomingTransaction(incomingSameAccountDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithNonManualOriginFails() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        FinancialTransaction importedIncoming = createManualTransaction(
            em,
            legs.incoming().getAccount(),
            TransactionFlow.IN,
            TRANSFER_AMOUNT
        );
        importedIncoming.setOrigin(TransactionOrigin.FILE_IMPORT);
        em.persist(importedIncoming);
        em.flush();

        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO importedIncomingDTO = new FinancialTransactionDTO();
        importedIncomingDTO.setId(importedIncoming.getId());
        internalTransferDTO.setIncomingTransaction(importedIncomingDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createInternalTransferWithAlreadyLinkedTransactionFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO linkedOutgoingDTO = new FinancialTransactionDTO();
        linkedOutgoingDTO.setId(internalTransfer.getOutgoingTransaction().getId());
        internalTransferDTO.setOutgoingTransaction(linkedOutgoingDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateInternalTransferWithDifferentOutgoingTransactionFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        TransferLegPair anotherLegs = createTransferLegPair(em);

        InternalTransferDTO internalTransferDTO = internalTransferMapper.toDto(internalTransfer);
        FinancialTransactionDTO anotherOutgoingDTO = new FinancialTransactionDTO();
        anotherOutgoingDTO.setId(anotherLegs.outgoing().getId());
        internalTransferDTO.setOutgoingTransaction(anotherOutgoingDTO);

        restInternalTransferMockMvc
            .perform(
                put(ENTITY_API_URL_ID, internalTransferDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(internalTransferDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchInternalTransferWithNullOutgoingTransactionFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"outgoingTransaction\":null}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchInternalTransferWithDifferentIncomingTransactionFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        TransferLegPair anotherLegs = createTransferLegPair(em);
        String patchJson =
            "{\"id\":" + internalTransfer.getId() + ",\"incomingTransaction\":{\"id\":" + anotherLegs.incoming().getId() + "}}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
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
