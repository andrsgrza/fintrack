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
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.IngestionRecordMapper;
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
class IngestionRecordServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private IngestionRecordRepository ingestionRecordRepository;

    @Mock
    private IngestionRecordMapper ingestionRecordMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private FinancialTransactionService financialTransactionService;

    @InjectMocks
    private IngestionRecordService ingestionRecordService;

    private User currentUser;
    private User otherUser;
    private FinancialAccount account;
    private FinancialAccount otherAccount;
    private TransactionIngestion transactionIngestion;
    private FinancialTransaction financialTransaction;
    private IngestionRecord ingestionRecord;
    private IngestionRecordDTO ingestionRecordDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setLogin("other");

        account = new FinancialAccount();
        account.setId(10L);
        account.setUser(currentUser);

        otherAccount = new FinancialAccount();
        otherAccount.setId(11L);
        otherAccount.setUser(otherUser);

        transactionIngestion = new TransactionIngestion();
        transactionIngestion.setId(50L);
        transactionIngestion.setStatus(IngestionStatus.PENDING);
        transactionIngestion.setAccount(account);

        financialTransaction = new FinancialTransaction();
        financialTransaction.setId(70L);
        financialTransaction.setAccount(account);
        financialTransaction.setTransactionIngestion(transactionIngestion);

        ingestionRecord = new IngestionRecord();
        ingestionRecord.setId(100L);
        ingestionRecord.setRecordIndex(0);
        ingestionRecord.setStatus(IngestionRecordStatus.CREATED);
        ingestionRecord.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        ingestionRecord.setTransactionIngestion(transactionIngestion);
        ingestionRecord.setFinancialTransaction(financialTransaction);

        ingestionRecordDTO = new IngestionRecordDTO();
        ingestionRecordDTO.setRecordIndex(0);
        ingestionRecordDTO.setStatus(IngestionRecordStatus.SKIPPED_DUPLICATE);

        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(50L);
        ingestionRecordDTO.setTransactionIngestion(transactionIngestionDTO);
    }

    @Test
    void saveShouldResolveParentsApplyCreatedAtAndValidateRecordIndex() {
        IngestionRecord mappedEntity = new IngestionRecord();
        mappedEntity.setRecordIndex(0);
        mappedEntity.setStatus(IngestionRecordStatus.SKIPPED_DUPLICATE);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(ingestionRecordMapper.toEntity(ingestionRecordDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(50L, 0)).thenReturn(false);
        when(ingestionRecordRepository.save(mappedEntity)).thenReturn(ingestionRecord);
        when(ingestionRecordMapper.toDto(ingestionRecord)).thenReturn(ingestionRecordDTO);

        ingestionRecordService.save(ingestionRecordDTO);

        ArgumentCaptor<IngestionRecord> captor = ArgumentCaptor.forClass(IngestionRecord.class);
        verify(ingestionRecordRepository).save(captor.capture());
        IngestionRecord saved = captor.getValue();
        assertThat(saved.getTransactionIngestion()).isEqualTo(transactionIngestion);
        assertThat(saved.getCreatedAt()).isNotNull();

        when(ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(50L, 0)).thenReturn(true);
        assertThatThrownBy(() -> ingestionRecordService.save(ingestionRecordDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveShouldRejectCrossOwnerFinancialTransactionEvenForAdmin() {
        IngestionRecord mappedEntity = new IngestionRecord();
        mappedEntity.setRecordIndex(1);
        mappedEntity.setStatus(IngestionRecordStatus.CREATED);

        FinancialTransaction otherTransaction = new FinancialTransaction();
        otherTransaction.setId(71L);
        otherTransaction.setAccount(otherAccount);

        FinancialTransactionDTO financialTransactionDTO = new FinancialTransactionDTO();
        financialTransactionDTO.setId(71L);
        ingestionRecordDTO.setFinancialTransaction(financialTransactionDTO);
        ingestionRecordDTO.setRecordIndex(1);
        ingestionRecordDTO.setStatus(IngestionRecordStatus.CREATED);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(ingestionRecordMapper.toEntity(ingestionRecordDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationships(50L)).thenReturn(Optional.of(transactionIngestion));
        when(ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(50L, 1)).thenReturn(false);
        when(financialTransactionService.findAccessibleTransactionEntity(71L)).thenReturn(Optional.of(otherTransaction));

        assertThatThrownBy(() -> ingestionRecordService.save(ingestionRecordDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveShouldRejectFinancialTransactionAlreadyLinked() {
        IngestionRecord mappedEntity = new IngestionRecord();
        mappedEntity.setRecordIndex(2);
        mappedEntity.setStatus(IngestionRecordStatus.CREATED);

        FinancialTransactionDTO financialTransactionDTO = new FinancialTransactionDTO();
        financialTransactionDTO.setId(70L);
        ingestionRecordDTO.setFinancialTransaction(financialTransactionDTO);
        ingestionRecordDTO.setRecordIndex(2);
        ingestionRecordDTO.setStatus(IngestionRecordStatus.CREATED);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(ingestionRecordMapper.toEntity(ingestionRecordDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(50L, 2)).thenReturn(false);
        when(financialTransactionService.findAccessibleTransactionEntity(70L)).thenReturn(Optional.of(financialTransaction));
        when(ingestionRecordRepository.existsByFinancialTransactionId(70L)).thenReturn(true);

        assertThatThrownBy(() -> ingestionRecordService.save(ingestionRecordDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShouldRejectImmutableFieldChanges() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            ingestionRecordRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(ingestionRecord));

        ingestionRecordDTO.setId(100L);
        ingestionRecordDTO.setCreatedAt(ingestionRecord.getCreatedAt());

        TransactionIngestionDTO otherParentDTO = new TransactionIngestionDTO();
        otherParentDTO.setId(99L);
        ingestionRecordDTO.setTransactionIngestion(otherParentDTO);
        assertThatThrownBy(() -> ingestionRecordService.update(ingestionRecordDTO)).isInstanceOf(IllegalArgumentException.class);

        ingestionRecordDTO.setTransactionIngestion(new TransactionIngestionDTO());
        ingestionRecordDTO.getTransactionIngestion().setId(50L);
        ingestionRecordDTO.setRecordIndex(99);
        assertThatThrownBy(() -> ingestionRecordService.update(ingestionRecordDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialUpdateShouldRejectNullTransactionIngestion() throws Exception {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            ingestionRecordRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(ingestionRecord));

        ingestionRecordDTO.setId(100L);
        ingestionRecordDTO.setTransactionIngestion(null);
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("transactionIngestion");
        assertThatThrownBy(() -> ingestionRecordService.partialUpdate(ingestionRecordDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void partialUpdateShouldRejectFinancialTransactionChange() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            ingestionRecordRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(ingestionRecord));

        ingestionRecordDTO.setId(100L);
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("financialTransaction");
        assertThatThrownBy(() -> ingestionRecordService.partialUpdate(ingestionRecordDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void deleteShouldReturnFalseWhenNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            ingestionRecordRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.empty());

        assertThat(ingestionRecordService.delete(100L)).isFalse();
        verify(ingestionRecordRepository, never()).deleteById(any());
    }
}
