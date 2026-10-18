package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTransferServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private InternalTransferMapper internalTransferMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private InternalTransferCommandService internalTransferCommandService;

    @InjectMocks
    private InternalTransferService internalTransferService;

    private InternalTransfer internalTransfer;
    private InternalTransferDTO internalTransferDTO;

    @BeforeEach
    void setUp() {
        FinancialTransaction outgoing = new FinancialTransaction();
        outgoing.setId(20L);
        FinancialTransaction incoming = new FinancialTransaction();
        incoming.setId(21L);

        internalTransfer = new InternalTransfer();
        internalTransfer.setId(100L);
        internalTransfer.setVersion(0L);
        internalTransfer.setNotes("notes");
        internalTransfer.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        internalTransfer.setOutgoingTransaction(outgoing);
        internalTransfer.setIncomingTransaction(incoming);

        internalTransferDTO = new InternalTransferDTO();
        internalTransferDTO.setId(100L);
        internalTransferDTO.setNotes("notes");
        FinancialTransactionDTO outgoingDTO = new FinancialTransactionDTO();
        outgoingDTO.setId(20L);
        FinancialTransactionDTO incomingDTO = new FinancialTransactionDTO();
        incomingDTO.setId(21L);
        internalTransferDTO.setOutgoingTransaction(outgoingDTO);
        internalTransferDTO.setIncomingTransaction(incomingDTO);
    }

    @Test
    void saveDelegatesLegacyLinkingToTheAtomicCommand() {
        InternalTransferReadDTO created = new InternalTransferReadDTO();
        created.setTransferId(100L);
        when(internalTransferCommandService.linkExistingTransactions(any())).thenReturn(created);
        accessibleTransfer(100L);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        assertThat(internalTransferService.save(internalTransferDTO)).isSameAs(internalTransferDTO);

        ArgumentCaptor<InternalTransferLinkCommand> captor = ArgumentCaptor.forClass(InternalTransferLinkCommand.class);
        verify(internalTransferCommandService).linkExistingTransactions(captor.capture());
        assertThat(captor.getValue().getOutgoingTransactionId()).isEqualTo(20L);
        assertThat(captor.getValue().getIncomingTransactionId()).isEqualTo(21L);
        assertThat(captor.getValue().getNotes()).isEqualTo("notes");
        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void saveRejectsMissingLegBeforeDispatchingTheCommand() {
        internalTransferDTO.setIncomingTransaction(null);

        assertThatThrownBy(() -> internalTransferService.save(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Incoming transaction is required");

        verify(internalTransferCommandService, never()).linkExistingTransactions(any());
    }

    @Test
    void updateRejectsOutgoingTransactionChange() {
        FinancialTransactionDTO changed = new FinancialTransactionDTO();
        changed.setId(99L);
        internalTransferDTO.setOutgoingTransaction(changed);
        accessibleTransfer(100L);

        assertThatThrownBy(() -> internalTransferService.update(internalTransferDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outgoing transaction cannot be changed");

        verify(internalTransferRepository, never()).save(any());
    }

    @Test
    void updateOnlyChangesNotesAndPreservesTheLegs() {
        internalTransferDTO.setNotes("  updated notes  ");
        accessibleTransfer(100L);
        when(internalTransferRepository.save(internalTransfer)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        internalTransferService.update(internalTransferDTO);

        assertThat(internalTransfer.getNotes()).isEqualTo("updated notes");
        assertThat(internalTransfer.getOutgoingTransaction().getId()).isEqualTo(20L);
        assertThat(internalTransfer.getIncomingTransaction().getId()).isEqualTo(21L);
    }

    @Test
    void partialUpdateRejectsNullOutgoingTransaction() {
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("outgoingTransaction");
        accessibleTransfer(100L);

        assertThatThrownBy(() -> internalTransferService.partialUpdate(internalTransferDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outgoing transaction cannot be null");
    }

    @Test
    void partialUpdateLeavesLegsUntouchedWhenTheyAreAbsent() {
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.put("notes", "patched notes");
        internalTransferDTO.setNotes("patched notes");
        accessibleTransfer(100L);
        when(internalTransferRepository.save(internalTransfer)).thenReturn(internalTransfer);
        when(internalTransferMapper.toDto(internalTransfer)).thenReturn(internalTransferDTO);

        assertThat(internalTransferService.partialUpdate(internalTransferDTO, patchNode)).isPresent();
        verify(internalTransferMapper).partialUpdate(internalTransfer, internalTransferDTO);
        assertThat(internalTransfer.getOutgoingTransaction().getId()).isEqualTo(20L);
        assertThat(internalTransfer.getIncomingTransaction().getId()).isEqualTo(21L);
    }

    @Test
    void deleteDelegatesToVersionedUnlinkInsteadOfDeletingEitherLeg() {
        accessibleTransfer(100L);

        assertThat(internalTransferService.delete(100L)).isTrue();

        ArgumentCaptor<InternalTransferUnlinkCommand> captor = ArgumentCaptor.forClass(InternalTransferUnlinkCommand.class);
        verify(internalTransferCommandService).unlinkTransfer(org.mockito.ArgumentMatchers.eq(100L), captor.capture());
        assertThat(captor.getValue().getExpectedVersion()).isZero();
        verify(internalTransferRepository, never()).deleteById(any());
    }

    private void accessibleTransfer(Long id) {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(id, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(internalTransfer)
        );
    }
}
