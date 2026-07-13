package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionIngestionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private TransactionIngestionMapper transactionIngestionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private FileIngestionRepository fileIngestionRepository;

    @Mock
    private ApiIngestionRepository apiIngestionRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private IngestionRecordRepository ingestionRecordRepository;

    @InjectMocks
    private TransactionIngestionService transactionIngestionService;

    private User currentUser;
    private FinancialAccount account;
    private FinancialAccount foreignAccount;
    private TransactionIngestion transactionIngestion;
    private TransactionIngestionDTO transactionIngestionDTO;
    private FinancialAccountDTO accountDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        account = new FinancialAccount();
        account.setId(10L);
        account.setUser(currentUser);

        foreignAccount = new FinancialAccount();
        foreignAccount.setId(99L);

        transactionIngestion = new TransactionIngestion();
        transactionIngestion.setId(100L);
        transactionIngestion.setIngestionType(IngestionType.FILE);
        transactionIngestion.setStatus(IngestionStatus.PENDING);
        transactionIngestion.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        transactionIngestion.setStartedAt(Instant.parse("2026-01-01T00:00:00Z"));
        transactionIngestion.setRecordsReceived(0);
        transactionIngestion.setRecordsCreated(0);
        transactionIngestion.setRecordsSkipped(0);
        transactionIngestion.setRecordsRejected(0);
        transactionIngestion.setAccount(account);

        transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(100L);
        transactionIngestionDTO.setIngestionType(IngestionType.FILE);
        transactionIngestionDTO.setSourceLabel("import.csv");

        accountDTO = new FinancialAccountDTO();
        accountDTO.setId(10L);
        transactionIngestionDTO.setAccount(accountDTO);
    }

    @Test
    void saveShouldIgnoreClientStatusAndForcePending() {
        transactionIngestionDTO.setStatus(IngestionStatus.COMPLETED);
        TransactionIngestion mappedEntity = new TransactionIngestion();
        mappedEntity.setIngestionType(IngestionType.FILE);

        when(transactionIngestionMapper.toEntity(transactionIngestionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(10L)).thenReturn(Optional.of(account));
        when(transactionIngestionRepository.save(mappedEntity)).thenReturn(transactionIngestion);
        when(transactionIngestionMapper.toDto(transactionIngestion)).thenReturn(transactionIngestionDTO);

        transactionIngestionService.save(transactionIngestionDTO);

        assertThat(mappedEntity.getStatus()).isEqualTo(IngestionStatus.PENDING);
    }

    @Test
    void saveShouldResolveAccessibleAccountAndApplyServerDefaults() {
        TransactionIngestion mappedEntity = new TransactionIngestion();
        mappedEntity.setIngestionType(IngestionType.FILE);

        when(transactionIngestionMapper.toEntity(transactionIngestionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(10L)).thenReturn(Optional.of(account));
        when(transactionIngestionRepository.save(mappedEntity)).thenReturn(transactionIngestion);
        when(transactionIngestionMapper.toDto(transactionIngestion)).thenReturn(transactionIngestionDTO);

        transactionIngestionService.save(transactionIngestionDTO);

        assertThat(mappedEntity.getAccount()).isEqualTo(account);
        assertThat(mappedEntity.getStatus()).isEqualTo(IngestionStatus.PENDING);
        assertThat(mappedEntity.getRecordsReceived()).isZero();
        assertThat(mappedEntity.getRecordsCreated()).isZero();
        assertThat(mappedEntity.getRecordsSkipped()).isZero();
        assertThat(mappedEntity.getRecordsRejected()).isZero();
        assertThat(mappedEntity.getCreatedAt()).isNotNull();
        assertThat(mappedEntity.getStartedAt()).isNotNull();
        assertThat(mappedEntity.getCompletedAt()).isNull();
        assertThat(mappedEntity.getErrorMessage()).isNull();
        verify(transactionIngestionRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectForeignAccount() {
        FinancialAccountDTO foreignAccountDTO = new FinancialAccountDTO();
        foreignAccountDTO.setId(99L);
        transactionIngestionDTO.setAccount(foreignAccountDTO);

        when(transactionIngestionMapper.toEntity(transactionIngestionDTO)).thenReturn(new TransactionIngestion());
        when(financialAccountService.findAccessibleAccountEntity(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionIngestionService.save(transactionIngestionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account is not accessible");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectAccountChange() {
        FinancialAccountDTO otherAccountDTO = new FinancialAccountDTO();
        otherAccountDTO.setId(11L);
        transactionIngestionDTO.setAccount(otherAccountDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );

        assertThatThrownBy(() -> transactionIngestionService.update(transactionIngestionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account cannot be changed");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectIngestionTypeChange() {
        transactionIngestionDTO.setIngestionType(IngestionType.API);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );

        assertThatThrownBy(() -> transactionIngestionService.update(transactionIngestionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ingestion type cannot be changed");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void updateShouldSetCompletedAtWhenTransitioningToCompletedWithFileMetadata() {
        TransactionIngestionDTO updateDTO = validUpdateDTO();
        updateDTO.setStatus(IngestionStatus.COMPLETED);
        updateDTO.setRecordsReceived(1);
        updateDTO.setRecordsCreated(1);
        updateDTO.setRecordsSkipped(0);
        updateDTO.setRecordsRejected(0);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(fileIngestionRepository.existsByTransactionIngestionId(100L)).thenReturn(true);
        when(transactionIngestionRepository.save(transactionIngestion)).thenReturn(transactionIngestion);
        when(transactionIngestionMapper.toDto(transactionIngestion)).thenReturn(updateDTO);

        transactionIngestionService.update(updateDTO);

        assertThat(transactionIngestion.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(transactionIngestion.getCompletedAt()).isNotNull();
        verify(transactionIngestionRepository).save(transactionIngestion);
    }

    @Test
    void updateShouldRejectFinalFileIngestionWithoutFileMetadata() {
        TransactionIngestionDTO updateDTO = validUpdateDTO();
        updateDTO.setStatus(IngestionStatus.COMPLETED);
        updateDTO.setRecordsReceived(1);
        updateDTO.setRecordsCreated(1);
        updateDTO.setRecordsSkipped(0);
        updateDTO.setRecordsRejected(0);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(fileIngestionRepository.existsByTransactionIngestionId(100L)).thenReturn(false);

        assertThatThrownBy(() -> transactionIngestionService.update(updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Final FILE ingestion requires file metadata");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectTerminalStatusChange() {
        transactionIngestion.setStatus(IngestionStatus.FAILED);
        transactionIngestion.setCompletedAt(Instant.parse("2026-01-01T00:01:00Z"));
        transactionIngestion.setErrorMessage("boom");
        TransactionIngestionDTO updateDTO = validUpdateDTO();
        updateDTO.setStatus(IngestionStatus.PROCESSING);
        updateDTO.setCompletedAt(transactionIngestion.getCompletedAt());
        updateDTO.setErrorMessage("boom");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );

        assertThatThrownBy(() -> transactionIngestionService.update(updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Final ingestion status cannot be changed");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectNullAccount() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("account");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );

        assertThatThrownBy(() -> transactionIngestionService.partialUpdate(transactionIngestionDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account cannot be null");

        verify(transactionIngestionRepository, never()).save(any());
    }

    @Test
    void findAllWhereFileIngestionIsNullShouldUseScopedEntities() {
        TransactionIngestion fileIngestion = new TransactionIngestion();
        fileIngestion.setId(1L);
        fileIngestion.setIngestionType(IngestionType.FILE);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findAllWithEagerRelationshipsByAccountUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            List.of(fileIngestion)
        );
        when(transactionIngestionMapper.toDto(fileIngestion)).thenReturn(transactionIngestionDTO);

        assertThat(transactionIngestionService.findAllWhereFileIngestionIsNull()).hasSize(1);
    }

    @Test
    void findAllWhereApiIngestionIsNullShouldFilterApiTypeOnly() {
        TransactionIngestion apiIngestion = new TransactionIngestion();
        apiIngestion.setId(2L);
        apiIngestion.setIngestionType(IngestionType.API);
        TransactionIngestionDTO apiDto = new TransactionIngestionDTO();
        apiDto.setId(2L);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionIngestionRepository.findAll()).thenReturn(List.of(apiIngestion));
        when(transactionIngestionMapper.toDto(apiIngestion)).thenReturn(apiDto);

        assertThat(transactionIngestionService.findAllWhereApiIngestionIsNull())
            .extracting(TransactionIngestionDTO::getId)
            .containsExactly(2L);
    }

    @Test
    void deleteShouldReturnFalseWhenIngestionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(transactionIngestionService.delete(100L)).isFalse();
        verify(transactionIngestionRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldCleanupChildIngestionsBeforeDeletingParent() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );

        assertThat(transactionIngestionService.delete(100L)).isTrue();

        InOrder inOrder = inOrder(
            fileIngestionRepository,
            apiIngestionRepository,
            internalTransferRepository,
            ingestionRecordRepository,
            financialTransactionRepository,
            transactionIngestionRepository
        );
        inOrder.verify(fileIngestionRepository).deleteByTransactionIngestionId(100L);
        inOrder.verify(apiIngestionRepository).deleteByTransactionIngestionId(100L);
        inOrder.verify(internalTransferRepository).deleteByTransactionIngestionIdInEitherRole(100L);
        inOrder.verify(ingestionRecordRepository).deleteByTransactionIngestionId(100L);
        inOrder.verify(financialTransactionRepository).deleteTagLinksByTransactionIngestionId(100L);
        inOrder.verify(financialTransactionRepository).deleteByTransactionIngestionId(100L);
        inOrder.verify(transactionIngestionRepository).deleteById(100L);
        verify(ingestionRecordRepository, never()).clearFinancialTransactionByTransactionIngestionId(any());
    }

    private TransactionIngestionDTO validUpdateDTO() {
        TransactionIngestionDTO updateDTO = new TransactionIngestionDTO();
        updateDTO.setId(100L);
        updateDTO.setIngestionType(IngestionType.FILE);
        updateDTO.setStatus(IngestionStatus.PROCESSING);
        updateDTO.setSourceLabel(" import.csv ");
        updateDTO.setCreatedAt(transactionIngestion.getCreatedAt());
        updateDTO.setStartedAt(transactionIngestion.getStartedAt());
        updateDTO.setRecordsReceived(0);
        updateDTO.setRecordsCreated(0);
        updateDTO.setRecordsSkipped(0);
        updateDTO.setRecordsRejected(0);
        updateDTO.setCompletedAt(transactionIngestion.getCompletedAt());
        updateDTO.setAccount(accountDTO);
        return updateDTO;
    }
}
