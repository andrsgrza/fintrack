package com.fintrack.app.service;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.TransactionRuleCondition;
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
import com.fintrack.app.service.dto.FinancialAccountDeletionBlockerDTO;
import com.fintrack.app.service.dto.FinancialAccountDeletionPreviewCountsDTO;
import com.fintrack.app.service.dto.FinancialAccountDeletionPreviewDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only preflight for the future aggregate FinancialAccount hard-delete command.
 *
 * <p>The preview deliberately uses a consistent read instead of acquiring a pessimistic write lock for a GET
 * request. The future mutating command must invoke the same validation again while it owns an account-level
 * write lock; this preview is informational and is never authorization to skip that validation.</p>
 */
@Service
@Transactional(readOnly = true)
public class FinancialAccountHardDeleteService {

    private final FinancialAccountRepository financialAccountRepository;
    private final FileIngestionRepository fileIngestionRepository;
    private final ApiIngestionRepository apiIngestionRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final TransactionCandidateRepository transactionCandidateRepository;
    private final TransactionIngestionRepository transactionIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final CreditAccountDetailsRepository creditAccountDetailsRepository;
    private final BudgetRepository budgetRepository;
    private final FinancialSubscriptionRepository financialSubscriptionRepository;
    private final TransactionRuleConditionRepository transactionRuleConditionRepository;
    private final CurrentUserService currentUserService;

    public FinancialAccountHardDeleteService(
        FinancialAccountRepository financialAccountRepository,
        FileIngestionRepository fileIngestionRepository,
        ApiIngestionRepository apiIngestionRepository,
        FinancialTransactionRepository financialTransactionRepository,
        TransactionCandidateRepository transactionCandidateRepository,
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        InternalTransferRepository internalTransferRepository,
        CreditAccountDetailsRepository creditAccountDetailsRepository,
        BudgetRepository budgetRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        CurrentUserService currentUserService
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.fileIngestionRepository = fileIngestionRepository;
        this.apiIngestionRepository = apiIngestionRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
        this.budgetRepository = budgetRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.currentUserService = currentUserService;
    }

    /** Returns an owner-safe, non-mutating deletion preview when the account is visible to the current actor. */
    @Transactional(readOnly = true)
    public Optional<FinancialAccountDeletionPreviewDTO> preview(Long accountId) {
        return findAccessibleAccount(accountId).map(this::buildPreview);
    }

    /**
     * Permanently removes one account aggregate after rerunning the complete preflight under its write lock.
     *
     * <p>A prior preview is only informational and is never used as authorization. A blocker raises the expected
     * domain exception before the first mutation; an inaccessible account returns {@code false}.</p>
     */
    @Transactional
    public boolean hardDelete(Long accountId) {
        Optional<FinancialAccount> lockedAccount = findAccessibleAccountForHardDelete(accountId);
        if (lockedAccount.isEmpty()) {
            return false;
        }

        FinancialAccount account = lockedAccount.get();
        FinancialAccountDeletionPreviewDTO preflight = buildPreview(account);
        if (!Boolean.TRUE.equals(preflight.getCanHardDelete())) {
            throw new FinancialAccountHardDeleteBlockedException(preflight.getBlockers());
        }

        deleteAggregate(account);
        return true;
    }

    private FinancialAccountDeletionPreviewDTO buildPreview(FinancialAccount account) {
        Long accountId = account.getId();
        List<FinancialTransaction> financialTransactions = financialTransactionRepository.findAllForAccountDeletionPreview(accountId);
        List<TransactionCandidate> candidates = transactionCandidateRepository.findAllForAccountDeletionPreview(accountId);
        List<TransactionIngestion> ingestions = transactionIngestionRepository.findAllForAccountDeletionPreview(accountId);
        List<IngestionRecord> records = ingestionRecordRepository.findAllForAccountDeletionPreview(accountId);
        List<InternalTransfer> transfers = internalTransferRepository.findAllForAccountDeletionPreview(accountId);
        List<Budget> budgets = budgetRepository.findAllWithAccountsByAccountId(accountId);
        List<TransactionRuleCondition> accountConditions = account.getUser() == null || account.getUser().getId() == null
            ? List.of()
            : transactionRuleConditionRepository.findAllAccountConditionsByRuleUserId(
                TransactionRuleField.ACCOUNT,
                account.getUser().getId()
            );

        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers = new LinkedHashMap<>();
        validateFinancialTransactions(account, financialTransactions, blockers);
        validateCandidates(account, candidates, blockers);
        validateRecords(account, records, blockers);
        validateTransfers(account, transfers, blockers);
        validateBudgetScope(budgets, blockers);
        List<TransactionRuleCondition> ruleReferences = accountConditions
            .stream()
            .filter(condition -> referencesAccount(condition, accountId))
            .toList();
        for (TransactionRuleCondition condition : ruleReferences) {
            addBlocker(blockers, FinancialAccountDeletionBlockerCode.RULE_ACCOUNT_CONDITION_REFERENCE, condition.getId());
        }

        FinancialAccountDeletionPreviewCountsDTO counts = new FinancialAccountDeletionPreviewCountsDTO();
        counts.setFinancialTransactions(
            financialTransactions.stream().filter(transaction -> belongsToAccount(transaction.getAccount(), account)).count()
        );
        counts.setManualCandidates(
            candidates.stream().filter(candidate -> candidate.getSource() == TransactionCandidateSource.MANUAL).count()
        );
        counts.setManualDraftCandidates(
            candidates
                .stream()
                .filter(candidate -> candidate.getSource() == TransactionCandidateSource.MANUAL)
                .filter(candidate -> candidate.getStatus() == TransactionCandidateStatus.DRAFT)
                .count()
        );
        counts.setManualPostedCandidates(
            candidates
                .stream()
                .filter(candidate -> candidate.getSource() == TransactionCandidateSource.MANUAL)
                .filter(candidate -> candidate.getStatus() == TransactionCandidateStatus.POSTED)
                .count()
        );
        counts.setTransactionIngestions((long) ingestions.size());
        counts.setIngestionRecords((long) records.size());
        counts.setFileImportCandidates(
            candidates.stream().filter(candidate -> candidate.getSource() == TransactionCandidateSource.FILE_IMPORT).count()
        );
        counts.setCreditAccountDetails(creditAccountDetailsRepository.countByAccountId(accountId));
        counts.setBudgetLinks((long) budgets.size());
        counts.setSubscriptions(financialSubscriptionRepository.countByAccountId(accountId));
        counts.setCrossAccountTransfers(transfers.stream().filter(transfer -> isCrossAccountTransfer(transfer, account)).count());
        counts.setRuleAccountReferences((long) ruleReferences.size());

        FinancialAccountDeletionPreviewDTO preview = new FinancialAccountDeletionPreviewDTO();
        preview.setAccountId(accountId);
        preview.setAccountName(account.getName());
        preview.setCounts(counts);
        preview.setBlockers(toBlockers(blockers));
        preview.setCanHardDelete(preview.getBlockers().isEmpty());
        return preview;
    }

    private void validateFinancialTransactions(
        FinancialAccount account,
        List<FinancialTransaction> financialTransactions,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        for (FinancialTransaction transaction : financialTransactions) {
            if (transaction.getTransactionIngestion() == null) {
                continue;
            }
            if (
                !belongsToAccount(transaction.getAccount(), account) ||
                !belongsToAccount(transaction.getTransactionIngestion().getAccount(), account)
            ) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, transaction.getId());
            }
        }
    }

    private void validateCandidates(
        FinancialAccount account,
        List<TransactionCandidate> candidates,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        for (TransactionCandidate candidate : candidates) {
            if (candidate.getSource() == TransactionCandidateSource.API_IMPORT) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.UNSUPPORTED_API_IMPORT_CANDIDATE, candidate.getId());
            } else if (candidate.getSource() == TransactionCandidateSource.MANUAL) {
                validateManualCandidate(account, candidate, blockers);
            } else if (candidate.getSource() == TransactionCandidateSource.FILE_IMPORT) {
                validateFileImportCandidate(account, candidate, blockers);
            } else {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE, candidate.getId());
            }
        }
    }

    private void validateManualCandidate(
        FinancialAccount account,
        TransactionCandidate candidate,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        boolean hasWorkflowLink = candidate.getTransactionIngestion() != null || candidate.getIngestionRecord() != null;
        boolean validUnposted =
            candidate.getStatus() == TransactionCandidateStatus.DRAFT ||
            candidate.getStatus() == TransactionCandidateStatus.READY_TO_POST ||
            candidate.getStatus() == TransactionCandidateStatus.CANCELLED ||
            candidate.getStatus() == TransactionCandidateStatus.FAILED;

        if (hasWorkflowLink || !belongsToAccount(candidate.getAccount(), account)) {
            addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE, candidate.getId());
            return;
        }
        if (validUnposted && candidate.getFinancialTransaction() == null) {
            return;
        }
        if (
            candidate.getStatus() != TransactionCandidateStatus.POSTED ||
            candidate.getFinancialTransaction() == null ||
            !belongsToAccount(candidate.getFinancialTransaction().getAccount(), account)
        ) {
            addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_CANDIDATE, candidate.getId());
        }
    }

    private void validateFileImportCandidate(
        FinancialAccount account,
        TransactionCandidate candidate,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        TransactionIngestion ingestion = candidate.getTransactionIngestion();
        IngestionRecord record = candidate.getIngestionRecord();
        if (
            ingestion == null ||
            record == null ||
            ingestion.getIngestionType() != IngestionType.FILE ||
            ingestion.getFileIngestion() == null ||
            !belongsToAccount(candidate.getAccount(), account) ||
            !belongsToAccount(ingestion.getAccount(), account) ||
            record.getTransactionIngestion() == null ||
            !Objects.equals(record.getTransactionIngestion().getId(), ingestion.getId())
        ) {
            addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, candidate.getId());
            return;
        }

        if (candidate.getStatus() == TransactionCandidateStatus.READY_TO_POST) {
            if (
                candidate.getFinancialTransaction() != null ||
                record.getFinancialTransaction() != null ||
                record.getStatus() != IngestionRecordStatus.VALID
            ) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, candidate.getId());
            }
            return;
        }

        if (
            candidate.getStatus() != TransactionCandidateStatus.POSTED ||
            candidate.getFinancialTransaction() == null ||
            record.getStatus() != IngestionRecordStatus.IMPORTED ||
            record.getFinancialTransaction() == null ||
            !Objects.equals(record.getFinancialTransaction().getId(), candidate.getFinancialTransaction().getId()) ||
            !belongsToAccount(candidate.getFinancialTransaction().getAccount(), account) ||
            candidate.getFinancialTransaction().getTransactionIngestion() == null ||
            !Objects.equals(candidate.getFinancialTransaction().getTransactionIngestion().getId(), ingestion.getId())
        ) {
            addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, candidate.getId());
        }
    }

    private void validateRecords(
        FinancialAccount account,
        List<IngestionRecord> records,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        for (IngestionRecord record : records) {
            TransactionIngestion ingestion = record.getTransactionIngestion();
            FinancialTransaction transaction = record.getFinancialTransaction();
            if (ingestion == null || !belongsToAccount(ingestion.getAccount(), account)) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, record.getId());
                continue;
            }
            if (record.getStatus() == IngestionRecordStatus.IMPORTED && transaction == null) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, record.getId());
                continue;
            }
            if (transaction != null) {
                if (
                    record.getStatus() != IngestionRecordStatus.IMPORTED ||
                    !belongsToAccount(transaction.getAccount(), account) ||
                    transaction.getTransactionIngestion() == null ||
                    !Objects.equals(transaction.getTransactionIngestion().getId(), ingestion.getId())
                ) {
                    addBlocker(blockers, FinancialAccountDeletionBlockerCode.CORRUPT_INGESTION_GRAPH, record.getId());
                }
            }
        }
    }

    private void validateTransfers(
        FinancialAccount account,
        List<InternalTransfer> transfers,
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers
    ) {
        for (InternalTransfer transfer : transfers) {
            if (isCrossAccountTransfer(transfer, account)) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.CROSS_ACCOUNT_TRANSFER, transfer.getId());
            }
        }
    }

    private void validateBudgetScope(List<Budget> budgets, Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers) {
        for (Budget budget : budgets) {
            if (budget.getAccounts().size() <= 1) {
                addBlocker(blockers, FinancialAccountDeletionBlockerCode.BUDGET_SCOPE_WOULD_BROADEN, budget.getId());
            }
        }
    }

    private boolean isCrossAccountTransfer(InternalTransfer transfer, FinancialAccount account) {
        if (transfer.getOutgoingTransaction() == null || transfer.getIncomingTransaction() == null) {
            return true;
        }
        boolean outgoingIsTarget = belongsToAccount(transfer.getOutgoingTransaction().getAccount(), account);
        boolean incomingIsTarget = belongsToAccount(transfer.getIncomingTransaction().getAccount(), account);
        return outgoingIsTarget != incomingIsTarget;
    }

    private boolean referencesAccount(TransactionRuleCondition condition, Long accountId) {
        if (condition.getValue() == null || accountId == null) {
            return false;
        }
        String expected = accountId.toString();
        for (String token : condition.getValue().split(",")) {
            if (expected.equals(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private Optional<FinancialAccount> findAccessibleAccount(Long id) {
        if (currentUserService.isAdmin()) {
            return financialAccountRepository.findOneWithEagerRelationships(id);
        }
        return financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private Optional<FinancialAccount> findAccessibleAccountForHardDelete(Long id) {
        if (currentUserService.isAdmin()) {
            return financialAccountRepository.findOneForHardDelete(id);
        }
        return financialAccountRepository.findOneForHardDeleteByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    /**
     * FK-safe deletion order. All methods are account scoped and run in the transaction started by
     * {@link #hardDelete(Long)}; none is an independently committed cleanup path.
     */
    private void deleteAggregate(FinancialAccount account) {
        Long accountId = account.getId();

        // Children of TransactionIngestion must disappear before the ingestion parent.
        fileIngestionRepository.deleteByTransactionIngestionAccountId(accountId);
        apiIngestionRepository.deleteByTransactionIngestionAccountId(accountId);

        // Preflight admits only transfers whose two legs belong to this same aggregate.
        internalTransferRepository.deleteLocalByAccountId(accountId);

        // Candidates own FKs to both records and posted financial transactions.
        transactionCandidateRepository.deleteTagLinksByAccountId(accountId);
        transactionCandidateRepository.deleteByAccountId(accountId);
        ingestionRecordRepository.deleteByTransactionIngestionAccountId(accountId);

        financialTransactionRepository.deleteTagLinksByAccountId(accountId);
        financialTransactionRepository.deleteByAccountId(accountId);
        transactionIngestionRepository.deleteByAccountId(accountId);

        // Shared entities survive; only their target-account relationship is removed.
        budgetRepository.deleteAccountLinksByAccountId(accountId);
        financialSubscriptionRepository.clearAccountByAccountId(accountId);
        creditAccountDetailsRepository.deleteByAccountId(accountId);
        financialAccountRepository.deleteById(accountId);
    }

    private boolean belongsToAccount(FinancialAccount candidate, FinancialAccount account) {
        return candidate != null && Objects.equals(candidate.getId(), account.getId());
    }

    private void addBlocker(
        Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers,
        FinancialAccountDeletionBlockerCode code,
        Long id
    ) {
        blockers.computeIfAbsent(code, ignored -> new LinkedHashSet<>()).add(id);
    }

    private List<FinancialAccountDeletionBlockerDTO> toBlockers(Map<FinancialAccountDeletionBlockerCode, Set<Long>> blockers) {
        List<FinancialAccountDeletionBlockerDTO> result = new ArrayList<>();
        blockers.forEach((code, ids) -> {
            FinancialAccountDeletionBlockerDTO blocker = new FinancialAccountDeletionBlockerDTO();
            blocker.setCode(code);
            blocker.setCount((long) ids.size());
            blocker.setRelatedIds(ids.stream().toList());
            result.add(blocker);
        });
        return result;
    }
}
