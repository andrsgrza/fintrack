package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import com.fintrack.app.service.transfer.InternalTransferErrorCode;
import com.fintrack.app.service.transfer.InternalTransferProductQueryService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Integration tests for the intent-oriented InternalTransfer HTTP API. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class InternalTransferResourceIT {

    private static final String CURRENT_MOCK_USER_LOGIN = "user";
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100.00");
    private static final String API_URL = "/api/internal-transfers";
    private static final AtomicLong sequence = new AtomicLong();

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MockMvc restInternalTransferMockMvc;

    @Autowired
    private EntityManager em;

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

    record TransferLegPair(FinancialTransaction outgoing, FinancialTransaction incoming) {}

    public static InternalTransfer createEntity(EntityManager em) {
        TransferLegPair legs = createTransferLegPair(em);
        return new InternalTransfer()
            .notes("Initial transfer note")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
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
        FinancialTransaction transaction = FinancialTransactionResourceIT.createEntity(em);
        transaction.setAccount(account);
        transaction.setFlow(flow);
        transaction.setOrigin(TransactionOrigin.MANUAL);
        transaction.setAmount(amount);
        return transaction;
    }

    @Test
    @Transactional
    void productReadModelsRemainAvailableWhileLegacyCrudWritesAreNotMapped() throws Exception {
        InternalTransfer transfer = internalTransferRepository.saveAndFlush(createEntity(em));
        Long transferId = transfer.getId();
        long transfersBefore = internalTransferRepository.count();

        restInternalTransferMockMvc
            .perform(get(API_URL + "/product/{id}", transferId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(transferId));

        restInternalTransferMockMvc
            .perform(post(API_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        restInternalTransferMockMvc
            .perform(put(API_URL + "/{id}", transferId).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        restInternalTransferMockMvc
            .perform(patch(API_URL + "/{id}", transferId).contentType("application/merge-patch+json").content("{}"))
            .andExpect(status().isNotFound());
        restInternalTransferMockMvc.perform(delete(API_URL + "/{id}", transferId)).andExpect(status().isNotFound());

        assertThat(internalTransferRepository.count()).isEqualTo(transfersBefore);
        assertThat(financialTransactionRepository.findById(transfer.getOutgoingTransaction().getId())).isPresent();
        assertThat(financialTransactionRepository.findById(transfer.getIncomingTransaction().getId())).isPresent();
    }

    @Test
    @Transactional
    void linkExistingCommandRejectsInvalidPairsAndLeavesNoTransfer() throws Exception {
        long transfersBefore = internalTransferRepository.count();
        TransferLegPair legs = createTransferLegPair(em);
        FinancialTransaction sameAccountIncoming = createManualTransaction(
            em,
            legs.outgoing().getAccount(),
            TransactionFlow.IN,
            TRANSFER_AMOUNT
        );
        FinancialTransaction wrongAmountIncoming = createManualTransaction(
            em,
            legs.incoming().getAccount(),
            TransactionFlow.IN,
            new BigDecimal("101.00")
        );
        FinancialTransaction wrongDateIncoming = createManualTransaction(
            em,
            legs.incoming().getAccount(),
            TransactionFlow.IN,
            TRANSFER_AMOUNT
        );
        wrongDateIncoming.setTransactionDate(legs.outgoing().getTransactionDate().plusDays(1));
        FinancialTransaction wrongFlowIncoming = createManualTransaction(
            em,
            legs.incoming().getAccount(),
            TransactionFlow.OUT,
            TRANSFER_AMOUNT
        );
        em.persist(sameAccountIncoming);
        em.persist(wrongAmountIncoming);
        em.persist(wrongDateIncoming);
        em.persist(wrongFlowIncoming);
        em.flush();

        for (FinancialTransaction invalidIncoming : new FinancialTransaction[] {
            sameAccountIncoming,
            wrongAmountIncoming,
            wrongDateIncoming,
            wrongFlowIncoming,
        }) {
            InternalTransferLinkCommand command = linkCommand(legs.outgoing().getId(), invalidIncoming.getId());
            restInternalTransferMockMvc
                .perform(
                    post(API_URL + "/commands/link-existing").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(command))
                )
                .andExpect(status().isBadRequest());
        }

        assertThat(internalTransferRepository.count()).isEqualTo(transfersBefore);
    }

    @Test
    @Transactional
    void linkExistingCommandRejectsCrossOwnerAndAlreadyLinkedLegs() throws Exception {
        TransferLegPair legs = createTransferLegPair(em);
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        FinancialAccount otherAccount = FinancialAccountResourceIT.createEntity(em);
        otherAccount.setUser(otherUser);
        otherAccount.setCurrency(legs.outgoing().getAccount().getCurrency());
        em.persist(otherAccount);
        FinancialTransaction crossOwnerIncoming = createManualTransaction(em, otherAccount, TransactionFlow.IN, TRANSFER_AMOUNT);
        em.persist(crossOwnerIncoming);
        em.flush();

        restInternalTransferMockMvc
            .perform(
                post(API_URL + "/commands/link-existing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(linkCommand(legs.outgoing().getId(), crossOwnerIncoming.getId())))
            )
            .andExpect(status().isNotFound());

        InternalTransfer linked = internalTransferRepository.saveAndFlush(
            new InternalTransfer().createdAt(Instant.now()).outgoingTransaction(legs.outgoing()).incomingTransaction(legs.incoming())
        );
        TransferLegPair anotherPair = createTransferLegPair(em);

        restInternalTransferMockMvc
            .perform(
                post(API_URL + "/commands/link-existing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(linkCommand(legs.outgoing().getId(), anotherPair.incoming().getId())))
            )
            .andExpect(status().isConflict());

        assertThat(internalTransferRepository.findById(linked.getId())).isPresent();
        assertThat(internalTransferRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void aggregateCommandsRequireVersionAndPreserveBothLegsOnUnlink() throws Exception {
        InternalTransfer transfer = internalTransferRepository.saveAndFlush(createEntity(em));
        Long transferId = transfer.getId();
        Long outgoingId = transfer.getOutgoingTransaction().getId();
        Long incomingId = transfer.getIncomingTransaction().getId();

        InternalTransferUpdateCommand firstUpdate = updateCommand(0L, "First source", "First destination");
        restInternalTransferMockMvc
            .perform(
                put(API_URL + "/{id}/aggregate", transferId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(firstUpdate))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        restInternalTransferMockMvc
            .perform(
                put(API_URL + "/{id}/aggregate", transferId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updateCommand(0L, "Stale source", "Stale destination")))
            )
            .andExpect(status().isConflict());
        restInternalTransferMockMvc
            .perform(post(API_URL + "/{id}/unlink", transferId).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
            .andExpect(status().isConflict());

        restInternalTransferMockMvc
            .perform(post(API_URL + "/{id}/unlink", transferId).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(transferId));

        assertThat(internalTransferRepository.findById(transferId)).isEmpty();
        assertThat(financialTransactionRepository.findById(outgoingId)).isPresent();
        assertThat(financialTransactionRepository.findById(incomingId)).isPresent();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void independentClientsCannotOverwriteOrUnlinkWithAStaleVersion() {
        CommandTransferFixture fixture = createCommandTransferFixture();
        try {
            InternalTransferReadDTO firstRead = inNewTransaction(() -> internalTransferProductQueryService.findOne(fixture.transferId()));
            InternalTransferReadDTO secondRead = inNewTransaction(() -> internalTransferProductQueryService.findOne(fixture.transferId()));

            InternalTransferReadDTO updated = inNewTransaction(() ->
                internalTransferCommandService.updateTransfer(
                    fixture.transferId(),
                    updateCommand(firstRead.getVersion(), "First client source", "First client destination")
                )
            );
            assertThat(updated.getVersion()).isEqualTo(1L);

            assertThatThrownBy(() ->
                inNewTransaction(() ->
                    internalTransferCommandService.unlinkTransfer(fixture.transferId(), unlinkCommand(secondRead.getVersion()))
                )
            )
                .isInstanceOf(InternalTransferCommandException.class)
                .extracting(error -> ((InternalTransferCommandException) error).getCode())
                .isEqualTo(InternalTransferErrorCode.TRANSFER_VERSION_CONFLICT);

            String sourceDescription = inNewTransaction(() ->
                financialTransactionRepository.findById(fixture.sourceTransactionId()).orElseThrow().getDescription()
            );
            String destinationDescription = inNewTransaction(() ->
                financialTransactionRepository.findById(fixture.destinationTransactionId()).orElseThrow().getDescription()
            );
            assertThat(sourceDescription).isEqualTo("First client source");
            assertThat(destinationDescription).isEqualTo("First client destination");
        } finally {
            cleanupCommandTransferFixture(fixture);
        }
    }

    private InternalTransferLinkCommand linkCommand(Long outgoingId, Long incomingId) {
        InternalTransferLinkCommand command = new InternalTransferLinkCommand();
        command.setOutgoingTransactionId(outgoingId);
        command.setIncomingTransactionId(incomingId);
        command.setNotes("Linked deliberately");
        return command;
    }

    private CommandTransferFixture createCommandTransferFixture() {
        return inNewTransaction(() -> {
            User owner = userRepository.findOneByLogin(CURRENT_MOCK_USER_LOGIN).orElseThrow();
            String suffix = String.valueOf(sequence.incrementAndGet());
            Instant now = Instant.now();
            FinancialAccount source = financialAccountRepository.saveAndFlush(commandAccount("Version source " + suffix, owner, now));
            FinancialAccount destination = financialAccountRepository.saveAndFlush(
                commandAccount("Version destination " + suffix, owner, now)
            );
            InternalTransferCreateCommand command = new InternalTransferCreateCommand();
            command.setSourceAccountId(source.getId());
            command.setDestinationAccountId(destination.getId());
            command.setAmount(TRANSFER_AMOUNT);
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

    private InternalTransferUpdateCommand updateCommand(Long version, String sourceDescription, String destinationDescription) {
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

    private InternalTransferUnlinkCommand unlinkCommand(Long version) {
        InternalTransferUnlinkCommand command = new InternalTransferUnlinkCommand();
        command.setExpectedVersion(version);
        return command;
    }

    private void cleanupCommandTransferFixture(CommandTransferFixture fixture) {
        inNewTransaction(() -> {
            internalTransferRepository
                .findById(fixture.transferId())
                .ifPresent(transfer ->
                    internalTransferCommandService.unlinkTransfer(fixture.transferId(), unlinkCommand(transfer.getVersion()))
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
}
