package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.InternalTransferAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import com.fintrack.app.service.transfer.InternalTransferErrorCode;
import com.fintrack.app.service.transfer.InternalTransferProductQueryService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InternalTransferCommandService internalTransferCommandService;

    @Autowired
    private InternalTransferProductQueryService internalTransferProductQueryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        // The independent-command acceptance test deliberately runs without the
        // class fixture transaction so its commands use separate database transactions.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            internalTransfer = createEntity(em);
        }
    }

    @AfterEach
    void cleanup() {
        if (insertedInternalTransfer != null) {
            internalTransferRepository.deleteById(insertedInternalTransfer.getId());
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
    void createInternalTransferIgnoresClientProvidedCreatedAt() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        internalTransferDTO.setCreatedAt(Instant.parse("2000-01-01T00:00:00Z"));

        var returnedInternalTransferDTO = om.readValue(
            restInternalTransferMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            InternalTransferDTO.class
        );

        assertThat(returnedInternalTransferDTO.getCreatedAt()).isNotEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
        insertedInternalTransfer = internalTransferRepository.findById(returnedInternalTransferDTO.getId()).orElseThrow();
    }

    @Test
    @Transactional
    void createInternalTransferTrimsNotesAndConvertsBlankToNull() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        internalTransferDTO.setNotes("  trimmed notes  ");

        var returnedInternalTransferDTO = om.readValue(
            restInternalTransferMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value("trimmed notes"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            InternalTransferDTO.class
        );
        insertedInternalTransfer = internalTransferRepository.findById(returnedInternalTransferDTO.getId()).orElseThrow();

        TransferLegPair blankLegs = createTransferLegPair(em);
        InternalTransferDTO blankNotesDTO = toCreateDto(blankLegs);
        blankNotesDTO.setNotes("   ");

        var blankReturnedDTO = om.readValue(
            restInternalTransferMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blankNotesDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            InternalTransferDTO.class
        );
        internalTransferRepository.deleteById(blankReturnedDTO.getId());
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
    void getProductInternalTransfersReturnsHumanReadableLegSummaries() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "/product").param("sort", "outgoingTransaction.transactionDate,DESC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].transferId").value(internalTransfer.getId().intValue()))
            .andExpect(jsonPath("$.content[0].source.accountName").value(internalTransfer.getOutgoingTransaction().getAccount().getName()))
            .andExpect(
                jsonPath("$.content[0].destination.accountName").value(internalTransfer.getIncomingTransaction().getAccount().getName())
            )
            .andExpect(jsonPath("$.content[0].source.transactionId").value(internalTransfer.getOutgoingTransaction().getId().intValue()))
            .andExpect(
                jsonPath("$.content[0].destination.transactionId").value(internalTransfer.getIncomingTransaction().getId().intValue())
            );
    }

    @Test
    @Transactional
    void getProductInternalTransferReturnsBothLegsForDetail() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        restInternalTransferMockMvc
            .perform(get(ENTITY_API_URL + "/product/{id}", internalTransfer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(internalTransfer.getId().intValue()))
            .andExpect(jsonPath("$.source.flow").value(TransactionFlow.OUT.name()))
            .andExpect(jsonPath("$.destination.flow").value(TransactionFlow.IN.name()))
            .andExpect(jsonPath("$.source.origin").value(TransactionOrigin.MANUAL.name()))
            .andExpect(jsonPath("$.destination.origin").value(TransactionOrigin.MANUAL.name()));
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
    void putInternalTransferWithAbsentLinksPreservesLinks() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String updateJson = "{\"id\":" + internalTransfer.getId() + ",\"notes\":\"" + UPDATED_NOTES + "\"}";

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL_ID, internalTransfer.getId()).contentType(MediaType.APPLICATION_JSON).content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value(UPDATED_NOTES))
            .andExpect(jsonPath("$.outgoingTransaction.id").value(internalTransfer.getOutgoingTransaction().getId().intValue()))
            .andExpect(jsonPath("$.incomingTransaction.id").value(internalTransfer.getIncomingTransaction().getId().intValue()));
    }

    @Test
    @Transactional
    void putInternalTransferWithNullOutgoingTransactionFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String updateJson = "{\"id\":" + internalTransfer.getId() + ",\"notes\":\"" + UPDATED_NOTES + "\",\"outgoingTransaction\":null}";

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL_ID, internalTransfer.getId()).contentType(MediaType.APPLICATION_JSON).content(updateJson))
            .andExpect(status().isBadRequest());
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
    void patchInternalTransferWithNullNotesClearsNotes() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"notes\":null}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value(nullValue()));

        assertThat(getPersistedInternalTransfer(internalTransfer).getNotes()).isNull();
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
    void deleteInternalTransferLeavesTransactionsIntact() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        Long outgoingTransactionId = internalTransfer.getOutgoingTransaction().getId();
        Long incomingTransactionId = internalTransfer.getIncomingTransaction().getId();

        restInternalTransferMockMvc
            .perform(delete(ENTITY_API_URL_ID, internalTransfer.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(financialTransactionRepository.findById(outgoingTransactionId)).isPresent();
        assertThat(financialTransactionRepository.findById(incomingTransactionId)).isPresent();
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
    void createInternalTransferWithIncomingTransactionOwnedByAnotherUserFails() throws Exception {
        User otherUser = createOtherUser(em);
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(otherUser);
        em.persist(otherAccount);
        em.flush();
        FinancialTransaction otherIncoming = createManualTransaction(em, otherAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(otherIncoming);
        em.flush();

        TransferLegPair legs = createTransferLegPair(em);
        InternalTransferDTO internalTransferDTO = toCreateDto(legs);
        FinancialTransactionDTO otherIncomingDTO = new FinancialTransactionDTO();
        otherIncomingDTO.setId(otherIncoming.getId());
        internalTransferDTO.setIncomingTransaction(otherIncomingDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCannotCreateInternalTransferWithCrossOwnerLegs() throws Exception {
        User otherUser = createOtherUser(em);
        User currentUser = getCurrentMockUser(em);

        FinancialAccount outgoingAccount = FinancialAccountResourceIT.createEntity(em);
        outgoingAccount.setUser(currentUser);
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

        InternalTransferDTO internalTransferDTO = new InternalTransferDTO();
        internalTransferDTO.setNotes(DEFAULT_NOTES);
        FinancialTransactionDTO outgoingTransactionDTO = new FinancialTransactionDTO();
        outgoingTransactionDTO.setId(outgoing.getId());
        FinancialTransactionDTO incomingTransactionDTO = new FinancialTransactionDTO();
        incomingTransactionDTO.setId(incoming.getId());
        internalTransferDTO.setOutgoingTransaction(outgoingTransactionDTO);
        internalTransferDTO.setIncomingTransaction(incomingTransactionDTO);

        restInternalTransferMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(internalTransferDTO)))
            .andExpect(status().isBadRequest());
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
    void createInternalTransferWithNonManualOriginSucceeds() throws Exception {
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
            .andExpect(status().isCreated());

        insertedInternalTransfer = internalTransferRepository.findAll().get(internalTransferRepository.findAll().size() - 1);
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
    void createInternalTransferWithTransactionAlreadyLinkedInOppositeRoleFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);

        FinancialAccount newIncomingAccount = FinancialAccountResourceIT.createEntity(em);
        newIncomingAccount.setUser(internalTransfer.getIncomingTransaction().getAccount().getUser());
        newIncomingAccount.setCurrency(internalTransfer.getIncomingTransaction().getAccount().getCurrency());
        em.persist(newIncomingAccount);
        em.flush();

        FinancialTransaction newIncoming = createManualTransaction(em, newIncomingAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(newIncoming);
        em.flush();

        InternalTransferDTO internalTransferDTO = new InternalTransferDTO();
        internalTransferDTO.setNotes(DEFAULT_NOTES);
        FinancialTransactionDTO outgoingDTO = new FinancialTransactionDTO();
        outgoingDTO.setId(internalTransfer.getIncomingTransaction().getId());
        FinancialTransactionDTO incomingDTO = new FinancialTransactionDTO();
        incomingDTO.setId(newIncoming.getId());
        internalTransferDTO.setOutgoingTransaction(outgoingDTO);
        internalTransferDTO.setIncomingTransaction(incomingDTO);

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
    void patchInternalTransferWithMissingOutgoingTransactionIdFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"outgoingTransaction\":{}}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchInternalTransferWithNullCreatedAtFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"createdAt\":null}";

        restInternalTransferMockMvc
            .perform(patch(ENTITY_API_URL_ID, internalTransfer.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchInternalTransferWithChangedCreatedAtFails() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        String patchJson = "{\"id\":" + internalTransfer.getId() + ",\"createdAt\":\"2026-02-01T00:00:00Z\"}";

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

    @Test
    @Transactional
    void aggregateCommandAcceptsInitialVersionZeroAndUpdatesBothLegsAtomically() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        Long transferId = insertedInternalTransfer.getId();
        LocalDate updatedDate = LocalDate.of(2026, 11, 1);

        String command =
            "{\"expectedVersion\":0,\"amount\":\"125.00\",\"transactionDate\":\"" +
            updatedDate +
            "\",\"sourcePostingDate\":\"2026-11-02\",\"destinationPostingDate\":\"2026-11-03\",\"sourceDescription\":\"Updated source\",\"destinationDescription\":\"Updated destination\",\"notes\":\"updated atomically\"}";

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL + "/" + transferId + "/aggregate").contentType(MediaType.APPLICATION_JSON).content(command))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.amount").value(125.00))
            .andExpect(jsonPath("$.transactionDate").value(updatedDate.toString()));

        InternalTransfer persisted = internalTransferRepository.findById(transferId).orElseThrow();
        assertThat(persisted.getOutgoingTransaction().getAmount()).isEqualByComparingTo("125.00");
        assertThat(persisted.getIncomingTransaction().getAmount()).isEqualByComparingTo("125.00");
        assertThat(persisted.getOutgoingTransaction().getTransactionDate()).isEqualTo(updatedDate);
        assertThat(persisted.getIncomingTransaction().getTransactionDate()).isEqualTo(updatedDate);
    }

    @Test
    @Transactional
    void commandUnlinkWithInitialVersionZeroPreservesBothLegs() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        Long transferId = insertedInternalTransfer.getId();
        Long outgoingId = insertedInternalTransfer.getOutgoingTransaction().getId();
        Long incomingId = insertedInternalTransfer.getIncomingTransaction().getId();

        restInternalTransferMockMvc
            .perform(
                post(ENTITY_API_URL + "/" + transferId + "/unlink")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(transferId));

        assertThat(internalTransferRepository.findById(transferId)).isEmpty();
        assertThat(financialTransactionRepository.findById(outgoingId)).isPresent();
        assertThat(financialTransactionRepository.findById(incomingId)).isPresent();
        insertedInternalTransfer = null;
    }

    @Test
    @Transactional
    void aggregateUpdateThenUnlinkPreservesBothLegsAndAdvancesTheAggregateVersion() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        Long transferId = insertedInternalTransfer.getId();
        Long outgoingId = insertedInternalTransfer.getOutgoingTransaction().getId();
        Long incomingId = insertedInternalTransfer.getIncomingTransaction().getId();

        String updateCommand =
            "{\"expectedVersion\":0,\"amount\":\"125.00\",\"transactionDate\":\"2026-11-01\",\"sourcePostingDate\":\"2026-11-02\",\"destinationPostingDate\":\"2026-11-03\",\"sourceDescription\":\"Updated source\",\"destinationDescription\":\"Updated destination\",\"notes\":\"" +
            DEFAULT_NOTES +
            "\"}";

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL + "/" + transferId + "/aggregate").contentType(MediaType.APPLICATION_JSON).content(updateCommand))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        restInternalTransferMockMvc
            .perform(
                post(ENTITY_API_URL + "/" + transferId + "/unlink")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":1}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(transferId));

        assertThat(internalTransferRepository.findById(transferId)).isEmpty();
        assertThat(financialTransactionRepository.findById(outgoingId)).isPresent();
        assertThat(financialTransactionRepository.findById(incomingId)).isPresent();
        insertedInternalTransfer = null;
    }

    @Test
    @Transactional
    void aggregateCommandsRejectTheStaleVersionWithConflictAndKeepBothLegsLinked() throws Exception {
        insertedInternalTransfer = internalTransferRepository.saveAndFlush(internalTransfer);
        Long transferId = insertedInternalTransfer.getId();

        String firstUpdate =
            "{\"expectedVersion\":0,\"amount\":\"125.00\",\"transactionDate\":\"2026-11-01\",\"sourcePostingDate\":\"2026-11-02\",\"destinationPostingDate\":\"2026-11-03\",\"sourceDescription\":\"First source\",\"destinationDescription\":\"First destination\",\"notes\":\"" +
            DEFAULT_NOTES +
            "\"}";
        String staleUpdate = firstUpdate.replace("First source", "Stale source").replace("First destination", "Stale destination");

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL + "/" + transferId + "/aggregate").contentType(MediaType.APPLICATION_JSON).content(firstUpdate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        restInternalTransferMockMvc
            .perform(put(ENTITY_API_URL + "/" + transferId + "/aggregate").contentType(MediaType.APPLICATION_JSON).content(staleUpdate))
            .andExpect(status().isConflict());
        restInternalTransferMockMvc
            .perform(
                post(ENTITY_API_URL + "/" + transferId + "/unlink")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0}")
            )
            .andExpect(status().isConflict());

        InternalTransfer persisted = internalTransferRepository.findById(transferId).orElseThrow();
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getOutgoingTransaction().getDescription()).isEqualTo("First source");
        assertThat(persisted.getIncomingTransaction().getDescription()).isEqualTo("First destination");
    }

    /** Each command/read commits independently, representing two clients against PostgreSQL. */
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void aggregateVersionProtectsIndependentEditAndUnlinkCommands() {
        CommandTransferFixture fixture = createCommandTransferFixture();
        try {
            InternalTransferReadDTO firstRead = inNewTransaction(() -> internalTransferProductQueryService.findOne(fixture.transferId()));
            InternalTransferReadDTO secondRead = inNewTransaction(() -> internalTransferProductQueryService.findOne(fixture.transferId()));
            assertThat(firstRead.getVersion()).isZero();
            assertThat(secondRead.getVersion()).isZero();

            InternalTransferReadDTO updated = inNewTransaction(() ->
                internalTransferCommandService.updateTransfer(
                    fixture.transferId(),
                    commandUpdate(0L, "First client source", "First client destination")
                )
            );
            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(updated.getNotes()).isEqualTo("Initial transfer note");

            assertThatThrownBy(() ->
                inNewTransaction(() -> internalTransferCommandService.unlinkTransfer(fixture.transferId(), commandUnlink(0L)))
            )
                .isInstanceOf(InternalTransferCommandException.class)
                .extracting(exception -> ((InternalTransferCommandException) exception).getCode())
                .isEqualTo(InternalTransferErrorCode.TRANSFER_VERSION_CONFLICT);
            assertThatThrownBy(() ->
                inNewTransaction(() ->
                    internalTransferCommandService.updateTransfer(
                        fixture.transferId(),
                        commandUpdate(0L, "Stale source", "Stale destination")
                    )
                )
            )
                .isInstanceOf(InternalTransferCommandException.class)
                .extracting(exception -> ((InternalTransferCommandException) exception).getCode())
                .isEqualTo(InternalTransferErrorCode.TRANSFER_VERSION_CONFLICT);

            InternalTransfer persisted = inNewTransaction(() -> internalTransferRepository.findById(fixture.transferId()).orElseThrow());
            assertThat(persisted.getVersion()).isEqualTo(updated.getVersion());
            assertThat(
                inNewTransaction(() -> financialTransactionRepository.findById(fixture.sourceTransactionId()).orElseThrow()
                ).getDescription()
            ).isEqualTo("First client source");
            assertThat(
                inNewTransaction(() -> financialTransactionRepository.findById(fixture.destinationTransactionId()).orElseThrow()
                ).getDescription()
            ).isEqualTo("First client destination");

            InternalTransferReadDTO unlinked = inNewTransaction(() ->
                internalTransferCommandService.unlinkTransfer(fixture.transferId(), commandUnlink(updated.getVersion()))
            );
            assertThat(unlinked.getTransferId()).isEqualTo(fixture.transferId());
            assertThat(inNewTransaction(() -> internalTransferRepository.findById(fixture.transferId()))).isEmpty();
            assertThat(inNewTransaction(() -> financialTransactionRepository.findById(fixture.sourceTransactionId()))).isPresent();
            assertThat(inNewTransaction(() -> financialTransactionRepository.findById(fixture.destinationTransactionId()))).isPresent();
        } finally {
            cleanupCommandTransferFixture(fixture);
        }
    }

    private CommandTransferFixture createCommandTransferFixture() {
        return inNewTransaction(() -> {
            User owner = userRepository.findOneByLogin(CURRENT_MOCK_USER_LOGIN).orElseThrow();
            String suffix = String.valueOf(longCount.incrementAndGet());
            Instant now = Instant.now();
            FinancialAccount source = financialAccountRepository.saveAndFlush(commandAccount("Version source " + suffix, owner, now));
            FinancialAccount destination = financialAccountRepository.saveAndFlush(
                commandAccount("Version destination " + suffix, owner, now)
            );

            InternalTransferCreateCommand command = new InternalTransferCreateCommand();
            command.setSourceAccountId(source.getId());
            command.setDestinationAccountId(destination.getId());
            command.setAmount(new BigDecimal("100.00"));
            command.setTransactionDate(LocalDate.of(2026, 10, 18));
            command.setSourcePostingDate(LocalDate.of(2026, 10, 18));
            command.setDestinationPostingDate(LocalDate.of(2026, 10, 19));
            command.setSourceDescription("Initial source");
            command.setDestinationDescription("Initial destination");
            command.setTransferNotes("Initial transfer note");
            InternalTransferReadDTO transfer = internalTransferCommandService.createTransfer(command);
            return new CommandTransferFixture(
                transfer.getTransferId(),
                source.getId(),
                destination.getId(),
                transfer.getSource().getTransactionId(),
                transfer.getDestination().getTransactionId()
            );
        });
    }

    private FinancialAccount commandAccount(String name, User owner, Instant now) {
        FinancialAccount account = new FinancialAccount();
        account.setName(name);
        account.setAccountType(AccountType.DEBIT);
        account.setCurrency(CurrencyCode.MXN);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setInitialBalanceDate(LocalDate.of(2026, 10, 18));
        account.setActive(true);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setUser(owner);
        return account;
    }

    private InternalTransferUpdateCommand commandUpdate(Long version, String sourceDescription, String destinationDescription) {
        InternalTransferUpdateCommand command = new InternalTransferUpdateCommand();
        command.setExpectedVersion(version);
        command.setAmount(new BigDecimal("125.00"));
        command.setTransactionDate(LocalDate.of(2026, 10, 20));
        command.setSourcePostingDate(LocalDate.of(2026, 10, 21));
        command.setDestinationPostingDate(LocalDate.of(2026, 10, 22));
        command.setSourceDescription(sourceDescription);
        command.setDestinationDescription(destinationDescription);
        command.setNotes("Initial transfer note");
        return command;
    }

    private InternalTransferUnlinkCommand commandUnlink(Long version) {
        InternalTransferUnlinkCommand command = new InternalTransferUnlinkCommand();
        command.setExpectedVersion(version);
        return command;
    }

    private void cleanupCommandTransferFixture(CommandTransferFixture fixture) {
        inNewTransaction(() -> {
            internalTransferRepository
                .findById(fixture.transferId())
                .ifPresent(transfer ->
                    internalTransferCommandService.unlinkTransfer(fixture.transferId(), commandUnlink(transfer.getVersion()))
                );
            financialTransactionRepository.deleteById(fixture.sourceTransactionId());
            financialTransactionRepository.deleteById(fixture.destinationTransactionId());
            financialAccountRepository.deleteById(fixture.sourceAccountId());
            financialAccountRepository.deleteById(fixture.destinationAccountId());
            return null;
        });
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }

    private record CommandTransferFixture(
        Long transferId,
        Long sourceAccountId,
        Long destinationAccountId,
        Long sourceTransactionId,
        Long destinationTransactionId
    ) {}

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
