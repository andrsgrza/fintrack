package com.fintrack.app.service.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.dto.InternalTransferCounterpartCommand;
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTransferCommandServiceTest {

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private EntityManager entityManager;

    private InternalTransferCommandService commandService;
    private FinancialAccount sourceAccount;
    private FinancialAccount destinationAccount;

    @BeforeEach
    void setUp() {
        commandService = new InternalTransferCommandService(
            financialAccountRepository,
            financialTransactionRepository,
            internalTransferRepository,
            categoryRepository,
            tagRepository,
            currentUserService,
            new InternalTransferInvariantService(internalTransferRepository),
            entityManager
        );

        User owner = new User();
        owner.setId(1L);
        owner.setLogin("user");
        sourceAccount = account(10L, "Source", owner);
        destinationAccount = account(11L, "Destination", owner);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn("user");
    }

    @Test
    void createsBothManualLegsAndTheCanonicalRootWithoutRuleServices() {
        InternalTransferCreateCommand command = new InternalTransferCreateCommand();
        command.setSourceAccountId(sourceAccount.getId());
        command.setDestinationAccountId(destinationAccount.getId());
        command.setAmount(new BigDecimal("100.00"));
        command.setTransactionDate(LocalDate.of(2026, 10, 17));
        command.setSourceDescription("Transfer to savings");
        command.setDestinationDescription("Transfer from checking");
        command.setTransferNotes("move funds");

        when(financialAccountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(List.of(10L, 11L), "user")).thenReturn(
            List.of(sourceAccount, destinationAccount)
        );
        when(financialTransactionRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<FinancialTransaction> legs = invocation.getArgument(0);
            legs.get(0).setId(101L);
            legs.get(1).setId(102L);
            return legs;
        });
        when(internalTransferRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalTransfer transfer = invocation.getArgument(0);
            transfer.setId(200L);
            transfer.setVersion(0L);
            return transfer;
        });

        InternalTransferReadDTO result = commandService.createTransfer(command);

        ArgumentCaptor<List<FinancialTransaction>> legsCaptor = ArgumentCaptor.forClass(List.class);
        verify(financialTransactionRepository).saveAllAndFlush(legsCaptor.capture());
        List<FinancialTransaction> legs = legsCaptor.getValue();
        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).getAccount()).isEqualTo(sourceAccount);
        assertThat(legs.get(0).getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(legs.get(1).getAccount()).isEqualTo(destinationAccount);
        assertThat(legs.get(1).getFlow()).isEqualTo(TransactionFlow.IN);
        assertThat(legs).allSatisfy(leg -> {
            assertThat(leg.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
            assertThat(leg.getAmount()).isEqualByComparingTo("100.00");
            assertThat(leg.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 17));
        });
        assertThat(result.getTransferId()).isEqualTo(200L);
        assertThat(result.getSource().getTransactionId()).isEqualTo(101L);
        assertThat(result.getDestination().getTransactionId()).isEqualTo(102L);
    }

    @Test
    void linksExistingLegsWithoutChangingTheirClassificationOrProvenance() {
        FinancialTransaction outgoing = transaction(101L, sourceAccount, TransactionFlow.OUT);
        FinancialTransaction incoming = transaction(102L, destinationAccount, TransactionFlow.IN);
        Category category = new Category();
        category.setId(33L);
        outgoing.setCategory(category);
        outgoing.setOrigin(TransactionOrigin.FILE_IMPORT);
        incoming.setOrigin(TransactionOrigin.API);

        InternalTransferLinkCommand command = new InternalTransferLinkCommand();
        command.setOutgoingTransactionId(outgoing.getId());
        command.setIncomingTransactionId(incoming.getId());
        command.setNotes("reconciled");

        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(101L, "user")).thenReturn(Optional.of(outgoing));
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(102L, "user")).thenReturn(Optional.of(incoming));
        when(financialAccountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(List.of(10L, 11L), "user")).thenReturn(
            List.of(sourceAccount, destinationAccount)
        );
        when(
            financialTransactionRepository.findAllForInternalTransferMutationByIdInAndAccountUserLogin(List.of(101L, 102L), "user")
        ).thenReturn(List.of(outgoing, incoming));
        when(internalTransferRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalTransfer transfer = invocation.getArgument(0);
            transfer.setId(200L);
            transfer.setVersion(0L);
            return transfer;
        });

        commandService.linkExistingTransactions(command);

        assertThat(outgoing.getCategory()).isSameAs(category);
        assertThat(outgoing.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
        assertThat(incoming.getOrigin()).isEqualTo(TransactionOrigin.API);
        verify(financialTransactionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void createsAnOppositeCounterpartWithoutMutatingTheOriginalLeg() {
        FinancialTransaction original = transaction(101L, sourceAccount, TransactionFlow.OUT);
        original.setOrigin(TransactionOrigin.FILE_IMPORT);
        InternalTransferCounterpartCommand command = new InternalTransferCounterpartCommand();
        command.setTargetAccountId(destinationAccount.getId());
        command.setDescription("Incoming counterpart");

        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(original.getId(), "user")).thenReturn(
            Optional.of(original)
        );
        when(financialAccountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(List.of(10L, 11L), "user")).thenReturn(
            List.of(sourceAccount, destinationAccount)
        );
        when(
            financialTransactionRepository.findAllForInternalTransferMutationByIdInAndAccountUserLogin(List.of(original.getId()), "user")
        ).thenReturn(List.of(original));
        when(financialTransactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FinancialTransaction counterpart = invocation.getArgument(0);
            counterpart.setId(102L);
            return counterpart;
        });
        when(internalTransferRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InternalTransfer transfer = invocation.getArgument(0);
            transfer.setId(200L);
            transfer.setVersion(0L);
            return transfer;
        });

        InternalTransferReadDTO result = commandService.createCounterpartForTransaction(original.getId(), command);

        assertThat(original.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
        assertThat(result.getSource().getTransactionId()).isEqualTo(original.getId());
        assertThat(result.getDestination().getTransactionId()).isEqualTo(102L);
        assertThat(result.getDestination().getFlow()).isEqualTo(TransactionFlow.IN);
        assertThat(result.getDestination().getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
    }

    @Test
    void rejectsStaleAggregateCommandsBeforeMutatingAnyLeg() {
        FinancialTransaction outgoing = transaction(101L, sourceAccount, TransactionFlow.OUT);
        FinancialTransaction incoming = transaction(102L, destinationAccount, TransactionFlow.IN);
        InternalTransfer transfer = transfer(200L, 1L, outgoing, incoming);
        InternalTransferUpdateCommand command = new InternalTransferUpdateCommand();
        command.setExpectedVersion(0L);
        command.setAmount(new BigDecimal("125.00"));
        command.setTransactionDate(LocalDate.of(2026, 10, 18));
        command.setSourceDescription("source");
        command.setDestinationDescription("destination");

        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(200L, "user")).thenReturn(
            Optional.of(transfer)
        );
        when(financialAccountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(List.of(10L, 11L), "user")).thenReturn(
            List.of(sourceAccount, destinationAccount)
        );
        when(
            financialTransactionRepository.findAllForInternalTransferMutationByIdInAndAccountUserLogin(List.of(101L, 102L), "user")
        ).thenReturn(List.of(outgoing, incoming));
        when(internalTransferRepository.findOneForInternalTransferMutationByIdAndAccountUserLogin(200L, "user")).thenReturn(
            Optional.of(transfer)
        );

        assertThatThrownBy(() -> commandService.updateTransfer(200L, command))
            .isInstanceOf(InternalTransferCommandException.class)
            .extracting("code")
            .isEqualTo(InternalTransferErrorCode.TRANSFER_VERSION_CONFLICT);
        verify(financialTransactionRepository, never()).saveAllAndFlush(any());
    }

    private FinancialAccount account(Long id, String name, User owner) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setName(name);
        account.setUser(owner);
        account.setCurrency(CurrencyCode.MXN);
        account.setActive(true);
        return account;
    }

    private FinancialTransaction transaction(Long id, FinancialAccount account, TransactionFlow flow) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(id);
        transaction.setAccount(account);
        transaction.setFlow(flow);
        transaction.setOrigin(TransactionOrigin.MANUAL);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionDate(LocalDate.of(2026, 10, 17));
        transaction.setDescription("Transfer leg");
        transaction.setTags(Set.of());
        return transaction;
    }

    private InternalTransfer transfer(Long id, Long version, FinancialTransaction outgoing, FinancialTransaction incoming) {
        InternalTransfer transfer = new InternalTransfer();
        transfer.setId(id);
        transfer.setVersion(version);
        transfer.setCreatedAt(java.time.Instant.now());
        transfer.setOutgoingTransaction(outgoing);
        transfer.setIncomingTransaction(incoming);
        return transfer;
    }
}
