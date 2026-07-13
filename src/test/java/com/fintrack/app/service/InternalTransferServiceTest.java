package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTransferServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100.00");

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private InternalTransferMapper internalTransferMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialTransactionService financialTransactionService;

    @InjectMocks
    private InternalTransferService internalTransferService;

    private User currentUser;
    private FinancialAccount outgoingAccount;
    private FinancialAccount incomingAccount;
    private FinancialTransaction outgoingTransaction;
    private FinancialTransaction incomingTransaction;
    private InternalTransfer internalTransfer;
    private InternalTransferDTO internalTransferDTO;
    private FinancialTransactionDTO outgoingTransactionDTO;
    private FinancialTransactionDTO incomingTransactionDTO;
    private FinancialTransactionDTO anotherOutgoingTransactionDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        outgoingAccount = new FinancialAccount();
        outgoingAccount.setId(10L);
        outgoingAccount.setCurrency(CurrencyCode.MXN);
        outgoingAccount.setUser(currentUser);

        incomingAccount = new FinancialAccount();
        incomingAccount.setId(11L);
        incomingAccount.setCurrency(CurrencyCode.MXN);
        incomingAccount.setUser(currentUser);

        outgoingTransaction = new FinancialTransaction();
        outgoingTransaction.setId(20L);
        outgoingTransaction.setAccount(outgoingAccount);
        outgoingTransaction.setFlow(TransactionFlow.OUT);
        outgoingTransaction.setOrigin(TransactionOrigin.MANUAL);
        outgoingTransaction.setAmount(TRANSFER_AMOUNT);

        incomingTransaction = new FinancialTransaction();
        incomingTransaction.setId(21L);
        incomingTransaction.setAccount(incomingAccount);
        incomingTransaction.setFlow(TransactionFlow.IN);
        incomingTransaction.setOrigin(TransactionOrigin.MANUAL);
        incomingTransaction.setAmount(TRANSFER_AMOUNT);

        internalTransfer = new InternalTransfer();
        internalTransfer.setId(100L);
        internalTransfer.setNotes("notes");
        internalTransfer.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        internalTransfer.setOutgoingTransaction(outgoingTransaction);
        internalTransfer.setIncomingTransaction(incomingTransaction);

        internalTransferDTO = new InternalTransferDTO();
        internalTransferDTO.setId(100L);
        internalTransferDTO.setNotes("notes");

        outgoingTransactionDTO = new FinancialTransactionDTO();
        outgoingTransactionDTO.setId(20L);
        incomingTransactionDTO = new FinancialTransactionDTO();
        incomingTransactionDTO.setId(21L);
        anotherOutgoingTransactionDTO = new FinancialTransactionDTO();
        anotherOutgoingTransactionDTO.setId(22L);
        internalTransferDTO.setOutgoingTransaction(outgoingTransactionDTO);
        internalTransferDTO.setIncomingTransaction(incomingTransactionDTO);
    }

    @Test
    void saveShouldResolveAccessibleTransactionsAssignCreatedAtAndPersist() {
        InternalTransfer mappedEntity = new InternalTransfer();

        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(financialTransactionService.findAccessibleTransactionEntity(20L)).thenReturn(Optional.of(outgoingTransaction));
        when(financialTransactionService.findAccessibleTransactionEntity(21L)).thenReturn(Optional.of(incomingTransaction));
        when(internalTransferRepository.existsByTransactionIdInEitherRole(20L)).thenReturn(false);
        when(internalTransferRepository.existsByTransactionIdInEitherRole(21L)).thenReturn(false);
        when(internalTransferRepository.save(mappedEntity)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        internalTransferService.save(internalTransferDTO);

        assertThat(mappedEntity.getOutgoingTransaction()).isEqualTo(outgoingTransaction);
        assertThat(mappedEntity.getIncomingTransaction()).isEqualTo(incomingTransaction);
        assertThat(mappedEntity.getCreatedAt()).isNotNull();
        verify(internalTransferRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectDifferentAccountsWithDifferentCurrency() {
        incomingAccount.setCurrency(CurrencyCode.USD);
        InternalTransfer mappedEntity = new InternalTransfer();

        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(financialTransactionService.findAccessibleTransactionEntity(20L)).thenReturn(Optional.of(outgoingTransaction));
        when(financialTransactionService.findAccessibleTransactionEntity(21L)).thenReturn(Optional.of(incomingTransaction));

        assertThatThrownBy(() -> internalTransferService.save(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transactions must use the same currency");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectAlreadyLinkedOutgoingTransaction() {
        InternalTransfer mappedEntity = new InternalTransfer();

        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(financialTransactionService.findAccessibleTransactionEntity(20L)).thenReturn(Optional.of(outgoingTransaction));
        when(financialTransactionService.findAccessibleTransactionEntity(21L)).thenReturn(Optional.of(incomingTransaction));
        when(internalTransferRepository.existsByTransactionIdInEitherRole(20L)).thenReturn(true);

        assertThatThrownBy(() -> internalTransferService.save(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outgoing transaction is already linked to an internal transfer");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectOutgoingTransactionChange() {
        internalTransferDTO.setOutgoingTransaction(anotherOutgoingTransactionDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );

        assertThatThrownBy(() -> internalTransferService.update(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outgoing transaction cannot be changed");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void updateShouldPreserveImmutableFields() {
        internalTransferDTO.setNotes("updated notes");
        InternalTransfer mappedEntity = new InternalTransfer();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );
        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(internalTransferRepository.save(mappedEntity)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        internalTransferService.update(internalTransferDTO);

        assertThat(mappedEntity.getOutgoingTransaction()).isEqualTo(outgoingTransaction);
        assertThat(mappedEntity.getIncomingTransaction()).isEqualTo(incomingTransaction);
        assertThat(mappedEntity.getCreatedAt()).isEqualTo(internalTransfer.getCreatedAt());
    }

    @Test
    void partialUpdateShouldRejectNullOutgoingTransaction() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("outgoingTransaction");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );

        assertThatThrownBy(() -> internalTransferService.partialUpdate(internalTransferDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outgoing transaction cannot be null");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectIncomingTransactionChange() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putObject("incomingTransaction").put("id", 99L);
        FinancialTransactionDTO changedIncoming = new FinancialTransactionDTO();
        changedIncoming.setId(99L);
        internalTransferDTO.setIncomingTransaction(changedIncoming);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );

        assertThatThrownBy(() -> internalTransferService.partialUpdate(internalTransferDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Incoming transaction cannot be changed");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveLegsWhenAbsent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("notes", "patched notes");
        internalTransferDTO.setNotes("patched notes");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );
        when(internalTransferRepository.save(internalTransfer)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        Optional<InternalTransferDTO> result = internalTransferService.partialUpdate(internalTransferDTO, patchNode);

        assertThat(result).isPresent();
        verify(internalTransferMapper).partialUpdate(internalTransfer, internalTransferDTO);
        verify(internalTransferRepository).save(internalTransfer);
    }

    @Test
    void saveShouldAllowNonManualOrigins() {
        outgoingTransaction.setOrigin(TransactionOrigin.FILE_IMPORT);
        incomingTransaction.setOrigin(TransactionOrigin.API);
        InternalTransfer mappedEntity = new InternalTransfer();

        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(financialTransactionService.findAccessibleTransactionEntity(20L)).thenReturn(Optional.of(outgoingTransaction));
        when(financialTransactionService.findAccessibleTransactionEntity(21L)).thenReturn(Optional.of(incomingTransaction));
        when(internalTransferRepository.existsByTransactionIdInEitherRole(20L)).thenReturn(false);
        when(internalTransferRepository.existsByTransactionIdInEitherRole(21L)).thenReturn(false);
        when(internalTransferRepository.save(mappedEntity)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        internalTransferService.save(internalTransferDTO);

        verify(internalTransferRepository).save(mappedEntity);
    }

    @Test
    void saveShouldNormalizeNotesBeforePersisting() {
        internalTransferDTO.setNotes("  notes  ");
        InternalTransfer mappedEntity = new InternalTransfer();
        mappedEntity.setNotes("  notes  ");

        when(internalTransferMapper.toEntity(internalTransferDTO)).thenReturn(mappedEntity);
        when(financialTransactionService.findAccessibleTransactionEntity(20L)).thenReturn(Optional.of(outgoingTransaction));
        when(financialTransactionService.findAccessibleTransactionEntity(21L)).thenReturn(Optional.of(incomingTransaction));
        when(internalTransferRepository.existsByTransactionIdInEitherRole(20L)).thenReturn(false);
        when(internalTransferRepository.existsByTransactionIdInEitherRole(21L)).thenReturn(false);
        when(internalTransferRepository.save(mappedEntity)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        internalTransferService.save(internalTransferDTO);

        assertThat(mappedEntity.getNotes()).isEqualTo("notes");
    }

    @Test
    void updateShouldRejectCreatedAtChange() {
        internalTransferDTO.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );

        assertThatThrownBy(() -> internalTransferService.update(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void deleteShouldReturnFalseWhenTransferIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(internalTransferService.delete(100L)).isFalse();
        verify(internalTransferRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleTransfer() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );

        assertThat(internalTransferService.delete(100L)).isTrue();
        verify(internalTransferRepository).deleteById(100L);
    }
}
