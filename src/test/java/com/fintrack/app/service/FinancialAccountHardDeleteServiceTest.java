package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.FinancialAccountDeletionBlockerCode;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.service.dto.FinancialAccountDeletionPreviewDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialAccountHardDeleteServiceTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String USER_LOGIN = "user";

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FileIngestionRepository fileIngestionRepository;

    @Mock
    private ApiIngestionRepository apiIngestionRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private TransactionCandidateRepository transactionCandidateRepository;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private IngestionRecordRepository ingestionRecordRepository;

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Mock
    private TransactionRuleConditionRepository transactionRuleConditionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FinancialAccountHardDeleteService financialAccountHardDeleteService;

    private FinancialAccount account;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(2L);
        user.setLogin(USER_LOGIN);
        account = account(ACCOUNT_ID, user);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(USER_LOGIN);
        // The shared preview defaults are deliberately lenient: hard-delete uses the locked lookup instead.
        lenient()
            .when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(ACCOUNT_ID, USER_LOGIN))
            .thenReturn(Optional.of(account));
        lenient().when(financialTransactionRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of());
        lenient().when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of());
        lenient().when(transactionIngestionRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of());
        lenient().when(ingestionRecordRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of());
        lenient().when(internalTransferRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of());
        lenient().when(budgetRepository.findAllWithAccountsByAccountId(ACCOUNT_ID)).thenReturn(List.of());
        lenient()
            .when(transactionRuleConditionRepository.findAllAccountConditionsByRuleUserId(TransactionRuleField.ACCOUNT, 2L))
            .thenReturn(List.of());
        lenient().when(creditAccountDetailsRepository.countByAccountId(ACCOUNT_ID)).thenReturn(0L);
        lenient().when(financialSubscriptionRepository.countByAccountId(ACCOUNT_ID)).thenReturn(0L);
    }

    @Test
    void previewEmptyAccountIsSafeAndPerformsNoMutation() {
        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getBlockers()).isEmpty();
        assertThat(preview.getCounts().getFinancialTransactions()).isZero();
        assertThat(preview.getCounts().getTransactionIngestions()).isZero();
        verify(financialAccountRepository, never()).deleteById(anyLong());
        verify(transactionCandidateRepository, never()).deleteAll();
        verify(financialTransactionRepository, never()).deleteAll();
    }

    @Test
    void hardDeleteReturnsFalseWhenTheAccountIsNotAccessible() {
        when(financialAccountRepository.findOneForHardDeleteByIdAndUserLogin(ACCOUNT_ID, USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(financialAccountHardDeleteService.hardDelete(ACCOUNT_ID)).isFalse();
        verify(financialAccountRepository, never()).deleteById(ACCOUNT_ID);
    }

    @Test
    void hardDeleteAbortsBeforeCleanupWhenPreflightFindsABlocker() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.POSTED);
        when(financialAccountRepository.findOneForHardDeleteByIdAndUserLogin(ACCOUNT_ID, USER_LOGIN)).thenReturn(Optional.of(account));
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThatThrownBy(() -> financialAccountHardDeleteService.hardDelete(ACCOUNT_ID))
            .isInstanceOf(FinancialAccountHardDeleteBlockedException.class)
            .satisfies(error ->
                assertThat(((FinancialAccountHardDeleteBlockedException) error).getBlockers())
                    .extracting(blocker -> blocker.getCode())
                    .containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE)
            );

        verify(fileIngestionRepository, never()).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        verify(apiIngestionRepository, never()).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        verify(internalTransferRepository, never()).deleteLocalByAccountId(ACCOUNT_ID);
        verify(transactionCandidateRepository, never()).deleteTagLinksByAccountId(ACCOUNT_ID);
        verify(transactionCandidateRepository, never()).deleteByAccountId(ACCOUNT_ID);
        verify(ingestionRecordRepository, never()).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        verify(financialTransactionRepository, never()).deleteTagLinksByAccountId(ACCOUNT_ID);
        verify(financialTransactionRepository, never()).deleteByAccountId(ACCOUNT_ID);
        verify(transactionIngestionRepository, never()).deleteByAccountId(ACCOUNT_ID);
        verify(budgetRepository, never()).deleteAccountLinksByAccountId(ACCOUNT_ID);
        verify(financialSubscriptionRepository, never()).clearAccountByAccountId(ACCOUNT_ID);
        verify(creditAccountDetailsRepository, never()).deleteByAccountId(ACCOUNT_ID);
        verify(financialAccountRepository, never()).deleteById(ACCOUNT_ID);
    }

    @Test
    void hardDeletePerformsTheApprovedAccountScopedCleanupOrderAfterSafePreflight() {
        when(financialAccountRepository.findOneForHardDeleteByIdAndUserLogin(ACCOUNT_ID, USER_LOGIN)).thenReturn(Optional.of(account));

        assertThat(financialAccountHardDeleteService.hardDelete(ACCOUNT_ID)).isTrue();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
            fileIngestionRepository,
            apiIngestionRepository,
            internalTransferRepository,
            transactionCandidateRepository,
            ingestionRecordRepository,
            financialTransactionRepository,
            transactionIngestionRepository,
            budgetRepository,
            financialSubscriptionRepository,
            creditAccountDetailsRepository,
            financialAccountRepository
        );
        order.verify(fileIngestionRepository).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        order.verify(apiIngestionRepository).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        order.verify(internalTransferRepository).deleteLocalByAccountId(ACCOUNT_ID);
        order.verify(transactionCandidateRepository).deleteTagLinksByAccountId(ACCOUNT_ID);
        order.verify(transactionCandidateRepository).deleteByAccountId(ACCOUNT_ID);
        order.verify(ingestionRecordRepository).deleteByTransactionIngestionAccountId(ACCOUNT_ID);
        order.verify(financialTransactionRepository).deleteTagLinksByAccountId(ACCOUNT_ID);
        order.verify(financialTransactionRepository).deleteByAccountId(ACCOUNT_ID);
        order.verify(transactionIngestionRepository).deleteByAccountId(ACCOUNT_ID);
        order.verify(budgetRepository).deleteAccountLinksByAccountId(ACCOUNT_ID);
        order.verify(financialSubscriptionRepository).clearAccountByAccountId(ACCOUNT_ID);
        order.verify(creditAccountDetailsRepository).deleteByAccountId(ACCOUNT_ID);
        order.verify(financialAccountRepository).deleteById(ACCOUNT_ID);
    }

    @Test
    void previewCountsDirectLegacyFinancialTransactionWithoutBlocking() {
        when(financialTransactionRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(transaction(account)));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getFinancialTransactions()).isEqualTo(1L);
        assertThat(preview.getBlockers()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCandidateStatus.class, names = { "DRAFT", "READY_TO_POST", "CANCELLED", "FAILED" })
    void previewAllowsValidUnpostedManualCandidates(TransactionCandidateStatus status) {
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(manualCandidate(status)));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getManualCandidates()).isEqualTo(1L);
        assertThat(preview.getBlockers()).isEmpty();
    }

    @Test
    void previewAllowsPostedManualCandidateWithFinancialTransactionFromSameAccount() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(transaction(account));
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getManualPostedCandidates()).isEqualTo(1L);
        assertThat(preview.getBlockers()).isEmpty();
    }

    @Test
    void previewBlocksPostedManualCandidateWithoutFinancialTransaction() {
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(
            List.of(manualCandidate(TransactionCandidateStatus.POSTED))
        );

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE);
    }

    @Test
    void previewBlocksManualCandidateWithUnexpectedFinancialTransaction() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.READY_TO_POST);
        candidate.setFinancialTransaction(transaction(account));
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE);
    }

    @Test
    void previewBlocksPostedManualCandidateWhoseFinancialTransactionBelongsToAnotherAccount() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(transaction(account(20L, account.getUser())));
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE);
    }

    @Test
    void previewBlocksManualCandidateAttachedToIngestion() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.DRAFT);
        candidate.setTransactionIngestion(ingestion(account));
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE);
    }

    @Test
    void previewAllowsPreparedFileImportCandidate() {
        TransactionIngestion ingestion = fileIngestion(account);
        IngestionRecord record = record(ingestion, IngestionRecordStatus.VALID, null);
        TransactionCandidate candidate = fileCandidate(ingestion, record, TransactionCandidateStatus.READY_TO_POST, null);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getFileImportCandidates()).isEqualTo(1L);
    }

    @Test
    void previewAllowsPostedFileImportCandidateWithConsistentGraph() {
        TransactionIngestion ingestion = fileIngestion(account);
        FinancialTransaction transaction = transaction(account);
        transaction.setTransactionIngestion(ingestion);
        IngestionRecord record = record(ingestion, IngestionRecordStatus.IMPORTED, transaction);
        TransactionCandidate candidate = fileCandidate(ingestion, record, TransactionCandidateStatus.POSTED, transaction);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(preview().getCanHardDelete()).isTrue();
    }

    @Test
    void previewBlocksFileImportCandidateWithoutIngestionOrRecord() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.READY_TO_POST);
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH);
    }

    @Test
    void previewBlocksFileImportCandidateWithDifferentIngestionAccount() {
        FinancialAccount otherAccount = account(20L, account.getUser());
        TransactionIngestion ingestion = fileIngestion(otherAccount);
        IngestionRecord record = record(ingestion, IngestionRecordStatus.VALID, null);
        TransactionCandidate candidate = fileCandidate(ingestion, record, TransactionCandidateStatus.READY_TO_POST, null);
        candidate.setAccount(account);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH);
    }

    @Test
    void previewBlocksPostedFileImportCandidateWithFinancialTransactionFromAnotherAccount() {
        FinancialAccount otherAccount = account(20L, account.getUser());
        TransactionIngestion ingestion = fileIngestion(account);
        FinancialTransaction otherAccountTransaction = transaction(otherAccount);
        otherAccountTransaction.setTransactionIngestion(ingestion);
        IngestionRecord record = record(ingestion, IngestionRecordStatus.IMPORTED, otherAccountTransaction);
        TransactionCandidate candidate = fileCandidate(ingestion, record, TransactionCandidateStatus.POSTED, otherAccountTransaction);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH);
    }

    @Test
    void previewBlocksApiImportCandidateWithoutDefiningApiImportDeletion() {
        TransactionCandidate candidate = manualCandidate(TransactionCandidateStatus.DRAFT);
        candidate.setSource(TransactionCandidateSource.API_IMPORT);
        when(transactionCandidateRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(candidate));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.UNSUPPORTED_API_IMPORT_CANDIDATE);
    }

    @Test
    void previewBlocksCrossAccountInternalTransfer() {
        FinancialAccount otherAccount = account(20L, account.getUser());
        InternalTransfer transfer = new InternalTransfer();
        transfer.setId(91L);
        transfer.setOutgoingTransaction(transaction(account));
        transfer.setIncomingTransaction(transaction(otherAccount));
        when(internalTransferRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(transfer));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isFalse();
        assertThat(preview.getCounts().getCrossAccountTransfers()).isEqualTo(1L);
        assertThat(blockerCodes(preview)).containsExactly(FinancialAccountDeletionBlockerCode.CROSS_ACCOUNT_TRANSFER);
    }

    @Test
    void previewAllowsTransferWhoseBothLegsBelongToDeletionAggregate() {
        InternalTransfer transfer = new InternalTransfer();
        transfer.setId(91L);
        transfer.setOutgoingTransaction(transaction(account));
        transfer.setIncomingTransaction(transaction(account));
        when(internalTransferRepository.findAllForAccountDeletionPreview(ACCOUNT_ID)).thenReturn(List.of(transfer));

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getCrossAccountTransfers()).isZero();
    }

    @Test
    void previewAllowsBudgetWhenAnotherExplicitAccountRemains() {
        FinancialAccount otherAccount = account(20L, account.getUser());
        Budget budget = new Budget();
        budget.setId(92L);
        budget.addAccounts(account);
        budget.addAccounts(otherAccount);
        when(budgetRepository.findAllWithAccountsByAccountId(ACCOUNT_ID)).thenReturn(List.of(budget));

        assertThat(preview().getCanHardDelete()).isTrue();
    }

    @Test
    void previewBlocksBudgetWhenTargetIsLastExplicitAccount() {
        Budget budget = new Budget();
        budget.setId(92L);
        budget.addAccounts(account);
        when(budgetRepository.findAllWithAccountsByAccountId(ACCOUNT_ID)).thenReturn(List.of(budget));

        assertThat(blockerCodes(preview())).containsExactly(FinancialAccountDeletionBlockerCode.BUDGET_SCOPE_WOULD_BROADEN);
    }

    @Test
    void previewBlocksAnyRuleConditionThatReferencesTargetAccount() {
        TransactionRule rule = new TransactionRule();
        rule.setUser(account.getUser());
        TransactionRuleCondition condition = new TransactionRuleCondition();
        condition.setId(93L);
        condition.setField(TransactionRuleField.ACCOUNT);
        condition.setValue("5, 10, 15");
        condition.setTransactionRule(rule);
        when(transactionRuleConditionRepository.findAllAccountConditionsByRuleUserId(TransactionRuleField.ACCOUNT, 2L)).thenReturn(
            List.of(condition)
        );

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCounts().getRuleAccountReferences()).isEqualTo(1L);
        assertThat(blockerCodes(preview)).containsExactly(FinancialAccountDeletionBlockerCode.RULE_ACCOUNT_CONDITION_REFERENCE);
    }

    @Test
    void previewCountsCreditDetailsAndSubscriptionsWithoutBlocking() {
        when(creditAccountDetailsRepository.countByAccountId(ACCOUNT_ID)).thenReturn(1L);
        when(financialSubscriptionRepository.countByAccountId(ACCOUNT_ID)).thenReturn(2L);

        FinancialAccountDeletionPreviewDTO preview = preview();

        assertThat(preview.getCanHardDelete()).isTrue();
        assertThat(preview.getCounts().getCreditAccountDetails()).isEqualTo(1L);
        assertThat(preview.getCounts().getSubscriptions()).isEqualTo(2L);
    }

    private FinancialAccountDeletionPreviewDTO preview() {
        return financialAccountHardDeleteService.preview(ACCOUNT_ID).orElseThrow();
    }

    private List<FinancialAccountDeletionBlockerCode> blockerCodes(FinancialAccountDeletionPreviewDTO preview) {
        return preview.getBlockers().stream().map(blocker -> blocker.getCode()).toList();
    }

    private FinancialAccount account(Long id, User user) {
        FinancialAccount result = new FinancialAccount();
        result.setId(id);
        result.setName("Account " + id);
        result.setUser(user);
        result.setAccountType(AccountType.DEBIT);
        return result;
    }

    private FinancialTransaction transaction(FinancialAccount owner) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(100L + owner.getId());
        transaction.setAccount(owner);
        return transaction;
    }

    private TransactionCandidate manualCandidate(TransactionCandidateStatus status) {
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setId(80L);
        candidate.setSource(TransactionCandidateSource.MANUAL);
        candidate.setStatus(status);
        candidate.setAccount(account);
        return candidate;
    }

    private TransactionIngestion ingestion(FinancialAccount owner) {
        TransactionIngestion ingestion = new TransactionIngestion();
        ingestion.setId(70L + owner.getId());
        ingestion.setAccount(owner);
        return ingestion;
    }

    private TransactionIngestion fileIngestion(FinancialAccount owner) {
        TransactionIngestion ingestion = ingestion(owner);
        ingestion.setIngestionType(IngestionType.FILE);
        ingestion.setFileIngestion(new FileIngestion());
        return ingestion;
    }

    private IngestionRecord record(TransactionIngestion ingestion, IngestionRecordStatus status, FinancialTransaction transaction) {
        IngestionRecord record = new IngestionRecord();
        record.setId(60L + ingestion.getId());
        record.setTransactionIngestion(ingestion);
        record.setStatus(status);
        record.setFinancialTransaction(transaction);
        return record;
    }

    private TransactionCandidate fileCandidate(
        TransactionIngestion ingestion,
        IngestionRecord record,
        TransactionCandidateStatus status,
        FinancialTransaction transaction
    ) {
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setId(50L + ingestion.getId());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        candidate.setStatus(status);
        candidate.setAccount(ingestion.getAccount());
        candidate.setTransactionIngestion(ingestion);
        candidate.setIngestionRecord(record);
        candidate.setFinancialTransaction(transaction);
        return candidate;
    }
}
