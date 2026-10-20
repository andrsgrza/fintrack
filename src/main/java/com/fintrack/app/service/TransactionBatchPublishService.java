package com.fintrack.app.service;

import com.fintrack.app.config.ApplicationProperties;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateInternalTransferMode;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TransactionBatchAppendOperationRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.InternalTransferCounterpartCommand;
import com.fintrack.app.service.dto.TransactionBatchPublicationReceiptDTO;
import com.fintrack.app.service.dto.TransactionBatchPublishCommand;
import com.fintrack.app.service.mapper.TransactionCandidateFinancialTransactionMapper;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import com.fintrack.app.service.transfer.InternalTransferErrorCode;
import com.fintrack.app.service.transfer.InternalTransferInvariantService;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal all-or-nothing conversion of one OPEN batch to final financial transactions.
 *
 * <p>The service deliberately writes finals directly through the validated candidate mapping instead of calling
 * {@link FinancialTransactionService#save}; that public create path evaluates rules, which would turn an explicit
 * candidate decision into a different classification during publication.
 */
@Service
public class TransactionBatchPublishService {

    private static final String PUBLICATION_CONTRACT_DIGEST_INPUT = "transaction-batch-publish-v1";

    private final TransactionBatchRepository batchRepository;
    private final TransactionCandidateRepository candidateRepository;
    private final TransactionBatchAppendOperationRepository appendOperationRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final TransactionCandidateFinancialTransactionMapper candidateFinancialTransactionMapper;
    private final InternalTransferInvariantService internalTransferInvariantService;
    private final InternalTransferCommandService internalTransferCommandService;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final ApplicationProperties applicationProperties;

    public TransactionBatchPublishService(
        TransactionBatchRepository batchRepository,
        TransactionCandidateRepository candidateRepository,
        TransactionBatchAppendOperationRepository appendOperationRepository,
        FinancialAccountRepository accountRepository,
        FinancialTransactionRepository financialTransactionRepository,
        InternalTransferRepository internalTransferRepository,
        TransactionCandidateFinancialTransactionMapper candidateFinancialTransactionMapper,
        InternalTransferInvariantService internalTransferInvariantService,
        InternalTransferCommandService internalTransferCommandService,
        CurrentUserService currentUserService,
        Clock clock,
        ApplicationProperties applicationProperties
    ) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.appendOperationRepository = appendOperationRepository;
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.candidateFinancialTransactionMapper = candidateFinancialTransactionMapper;
        this.internalTransferInvariantService = internalTransferInvariantService;
        this.internalTransferCommandService = internalTransferCommandService;
        this.currentUserService = currentUserService;
        this.clock = clock;
        this.applicationProperties = applicationProperties;
    }

    /** Publishes every persisted row in the named workspace or publishes none of them. */
    @Transactional
    public TransactionBatchPublicationReceiptDTO publish(Long batchId, TransactionBatchPublishCommand command) {
        validateCommand(batchId, command);
        String login = currentUserService.getCurrentUserLogin();
        String digest = publicationDigest();
        Instant now = clock.instant();

        TransactionBatch observed = batchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, login)
            .orElseThrow(() -> new IllegalArgumentException("Transaction batch is not accessible"));
        if (observed.getStatus() == TransactionBatchStatus.PUBLISHED) {
            return replayPublishedReceipt(observed, command.getOperationKey(), digest, now);
        }
        if (observed.getStatus() != TransactionBatchStatus.OPEN || observed.getAccount() == null) {
            throw new IllegalStateException("Transaction batch is not publishable");
        }

        // OPEN Batch commands own the root before their rows. Publication then
        // locks all affected accounts in ascending id order before any final
        // transaction or InternalTransfer row, matching the transfer engine.
        TransactionBatch batch = batchRepository
            .findOneForOwnedMutation(batchId, login)
            .orElseThrow(() -> new IllegalArgumentException("Transaction batch is not accessible"));
        if (batch.getStatus() == TransactionBatchStatus.PUBLISHED) {
            return replayPublishedReceipt(batch, command.getOperationKey(), digest, now);
        }
        if (batch.getStatus() != TransactionBatchStatus.OPEN || batch.getAccount() == null) {
            throw new IllegalStateException("Transaction batch is not publishable");
        }
        requireExpectedVersion(batch, command.getExpectedBatchVersion());
        List<TransactionCandidate> candidates = candidateRepository.findAllBatchCandidatesForPublication(batch.getId(), login);
        Map<Long, FinancialAccount> accountsById = lockPublicationAccounts(batch, candidates, login);
        FinancialAccount account = accountsById.get(batch.getAccount().getId());
        if (account == null) {
            throw new IllegalArgumentException("Financial account is not accessible");
        }
        FinancialAccountReferenceValidator.validateActiveForNewReference(account);
        validatePublicationAggregate(batch, account, candidates);

        // All account locks have been acquired in deterministic order. Transfer
        // rows now lock any existing counterpart leg by ascending id. No
        // counterpart is materialised or linked before complete preflight.
        Map<Long, FinancialTransaction> counterpartsByCandidateId = lockAndValidateTransferCounterparts(candidates, login, accountsById);

        List<FinancialTransaction> finalTransactions = candidates
            .stream()
            .map(candidate -> candidateFinancialTransactionMapper.toFinancialTransaction(candidate, originFor(candidate), now))
            .toList();
        // One batched persistence boundary: any later FK/constraint or cleanup failure rolls every new final back.
        finalTransactions = financialTransactionRepository.saveAllAndFlush(finalTransactions);
        int linkedTransferCount = persistTransferRelations(candidates, finalTransactions, counterpartsByCandidateId, now);
        int createdCounterpartCount = createConfiguredTransferCounterparts(candidates, finalTransactions);

        // The original association table predates candidate-owned tag entities, so remove it explicitly before the
        // bulk candidate delete. Candidate issues and suppressions are protected by their FK cascade.
        candidateRepository.deleteTagLinksByTransactionBatchId(batch.getId());
        candidateRepository.deleteByTransactionBatchId(batch.getId());
        appendOperationRepository.deleteByTransactionBatchId(batch.getId());

        // The bulk cleanup clears the persistence context. Reload the still-locked root before converting it.
        batch = batchRepository
            .findOneForOwnedMutation(batchId, login)
            .orElseThrow(() -> new IllegalStateException("Transaction batch disappeared during publication"));
        if (
            candidateRepository.existsByTransactionBatchId(batch.getId()) ||
            appendOperationRepository.existsByTransactionBatchId(batch.getId())
        ) {
            throw new IllegalStateException("Transaction batch cleanup did not remove temporary state");
        }
        transitionToPublishedReceipt(
            batch,
            command.getOperationKey(),
            digest,
            finalTransactions.size(),
            createdCounterpartCount,
            linkedTransferCount + createdCounterpartCount,
            now
        );
        batch = batchRepository.saveAndFlush(batch);
        return toReceipt(batch, false);
    }

    private TransactionBatchPublicationReceiptDTO replayPublishedReceipt(
        TransactionBatch batch,
        String operationKey,
        String digest,
        Instant now
    ) {
        ensureReceiptCurrent(batch, now);
        if (Objects.equals(batch.getPublicationOperationKey(), operationKey)) {
            if (!Objects.equals(batch.getPublicationRequestDigest(), digest)) {
                throw new IllegalStateException("OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT");
            }
            return toReceipt(batch, true);
        }
        throw new IllegalStateException("BATCH_ALREADY_PUBLISHED");
    }

    private void validatePublicationAggregate(TransactionBatch batch, FinancialAccount account, List<TransactionCandidate> candidates) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("Transaction batch cannot publish without candidates");
        if (batch.getUser() == null || account.getUser() == null || !Objects.equals(batch.getUser().getId(), account.getUser().getId())) {
            throw new IllegalStateException("Transaction batch account and owner are inconsistent");
        }
        for (TransactionCandidate candidate : candidates) {
            validateCandidate(batch, account, candidate);
        }
    }

    /**
     * Locks and rechecks every transfer intent before any Batch final
     * transaction is written. Existing-leg duplicate claims and incomplete
     * create-counterpart configurations fail before the first final write.
     */
    private Map<Long, FinancialTransaction> lockAndValidateTransferCounterparts(
        List<TransactionCandidate> candidates,
        String login,
        Map<Long, FinancialAccount> accountsById
    ) {
        List<TransactionCandidate> transferCandidates = candidates
            .stream()
            .filter(TransactionCandidate::getInternalTransferEnabled)
            .toList();
        if (transferCandidates.isEmpty()) return Map.of();

        Set<Long> counterpartIds = new HashSet<>();
        for (TransactionCandidate candidate : transferCandidates) {
            if (candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.LINK_EXISTING) {
                collectExistingCounterpartForPreflight(candidate, counterpartIds);
                continue;
            }
            if (candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.CREATE_COUNTERPART) {
                if (
                    candidate.getInternalTransferCounterpartAccount() == null ||
                    candidate.getInternalTransferCounterpartAccount().getId() == null
                ) {
                    throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
                }
                continue;
            }
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }

        List<FinancialTransaction> lockedCounterparts = counterpartIds.isEmpty()
            ? List.of()
            : financialTransactionRepository.findAllForInternalTransferMutationByIdInAndAccountUserLogin(
                counterpartIds.stream().sorted().toList(),
                login
            );
        if (lockedCounterparts.size() != counterpartIds.size()) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        Map<Long, FinancialTransaction> byId = new HashMap<>();
        lockedCounterparts.forEach(counterpart -> byId.put(counterpart.getId(), counterpart));
        Map<Long, FinancialTransaction> byCandidateId = new HashMap<>();
        for (TransactionCandidate candidate : transferCandidates) {
            if (candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.CREATE_COUNTERPART) {
                validateCreateCounterpartIntent(candidate, accountsById);
                continue;
            }
            FinancialTransaction counterpart = byId.get(candidate.getInternalTransferCounterpartTransaction().getId());
            if (counterpart == null) throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
            internalTransferInvariantService.validateCandidateAgainstExistingCounterpart(candidate, counterpart);
            internalTransferInvariantService.requireUnlinked(counterpart);
            byCandidateId.put(candidate.getId(), counterpart);
        }
        return byCandidateId;
    }

    private void collectExistingCounterpartForPreflight(TransactionCandidate candidate, Set<Long> counterpartIds) {
        if (
            candidate.getInternalTransferCounterpartTransaction() == null ||
            candidate.getInternalTransferCounterpartTransaction().getId() == null
        ) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        Long counterpartId = candidate.getInternalTransferCounterpartTransaction().getId();
        if (!counterpartIds.add(counterpartId)) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_LEG_ALREADY_LINKED);
        }
        if (
            candidate.getInternalTransferCounterpartTransaction().getAccount() == null ||
            candidate.getInternalTransferCounterpartTransaction().getAccount().getId() == null
        ) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
    }

    /**
     * The Batch root and its rows are already write-locked. Acquire every
     * source/target account once, sorted by id, before locking published
     * counterpart transactions. This prevents a CREATE row from taking the
     * source account in a different order from the neutral transfer engine.
     */
    private Map<Long, FinancialAccount> lockPublicationAccounts(
        TransactionBatch batch,
        List<TransactionCandidate> candidates,
        String login
    ) {
        Set<Long> accountIds = new HashSet<>();
        if (batch.getAccount() == null || batch.getAccount().getId() == null) {
            throw new IllegalStateException("Transaction batch is not publishable");
        }
        accountIds.add(batch.getAccount().getId());
        for (TransactionCandidate candidate : candidates) {
            if (!candidate.getInternalTransferEnabled()) continue;
            if (
                candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.CREATE_COUNTERPART &&
                candidate.getInternalTransferCounterpartAccount() != null &&
                candidate.getInternalTransferCounterpartAccount().getId() != null
            ) {
                accountIds.add(candidate.getInternalTransferCounterpartAccount().getId());
            }
            if (
                candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.LINK_EXISTING &&
                candidate.getInternalTransferCounterpartTransaction() != null &&
                candidate.getInternalTransferCounterpartTransaction().getAccount() != null &&
                candidate.getInternalTransferCounterpartTransaction().getAccount().getId() != null
            ) {
                accountIds.add(candidate.getInternalTransferCounterpartTransaction().getAccount().getId());
            }
        }
        List<FinancialAccount> lockedAccounts = accountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(
            accountIds.stream().sorted().toList(),
            login
        );
        if (lockedAccounts.size() != accountIds.size()) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        Map<Long, FinancialAccount> accountsById = new HashMap<>();
        lockedAccounts.forEach(lockedAccount -> accountsById.put(lockedAccount.getId(), lockedAccount));
        return accountsById;
    }

    private void validateCreateCounterpartIntent(TransactionCandidate candidate, Map<Long, FinancialAccount> accountsById) {
        FinancialAccount targetAccount = accountsById.get(candidate.getInternalTransferCounterpartAccount().getId());
        if (targetAccount == null || candidate.getInternalTransferCounterpartDescription() == null) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        if (candidate.getInternalTransferCounterpartDescription().trim().isBlank()) {
            throw transferFailure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        internalTransferInvariantService.validateExistingAccountAndNewCounterpart(candidate.getAccount(), targetAccount);
    }

    /**
     * Reuses the neutral transfer engine only after every Batch row has passed
     * locked preflight and its source leg has been persisted. Any failure still
     * rolls back source legs, new counterparts, links, cleanup and receipt.
     */
    private int createConfiguredTransferCounterparts(List<TransactionCandidate> candidates, List<FinancialTransaction> finalTransactions) {
        int createdCounterpartCount = 0;
        for (int index = 0; index < candidates.size(); index++) {
            TransactionCandidate candidate = candidates.get(index);
            if (
                !candidate.getInternalTransferEnabled() ||
                candidate.getInternalTransferMode() != TransactionCandidateInternalTransferMode.CREATE_COUNTERPART
            ) {
                continue;
            }
            InternalTransferCounterpartCommand counterpart = new InternalTransferCounterpartCommand();
            counterpart.setTargetAccountId(candidate.getInternalTransferCounterpartAccount().getId());
            counterpart.setPostingDate(candidate.getInternalTransferCounterpartPostingDate());
            counterpart.setDescription(candidate.getInternalTransferCounterpartDescription());
            counterpart.setExternalReference(candidate.getInternalTransferCounterpartReference());
            counterpart.setNotes(candidate.getInternalTransferCounterpartNotes());
            // Classification intentionally stays empty: this product action
            // does not implicitly rerun DNR or TransactionRules.
            internalTransferCommandService.createCounterpartForTransaction(finalTransactions.get(index).getId(), counterpart);
            createdCounterpartCount++;
        }
        return createdCounterpartCount;
    }

    /** Creates roots only after every candidate/counterpart pair has passed locked preflight. */
    private int persistTransferRelations(
        List<TransactionCandidate> candidates,
        List<FinancialTransaction> finalTransactions,
        Map<Long, FinancialTransaction> counterpartsByCandidateId,
        Instant now
    ) {
        if (counterpartsByCandidateId.isEmpty()) return 0;
        List<InternalTransfer> transfers = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            TransactionCandidate candidate = candidates.get(index);
            FinancialTransaction counterpart = counterpartsByCandidateId.get(candidate.getId());
            if (counterpart == null) continue;
            FinancialTransaction currentLeg = finalTransactions.get(index);
            FinancialTransaction outgoing = currentLeg.getFlow() == TransactionFlow.OUT ? currentLeg : counterpart;
            FinancialTransaction incoming = currentLeg.getFlow() == TransactionFlow.IN ? currentLeg : counterpart;
            internalTransferInvariantService.validateExistingPair(outgoing, incoming);
            internalTransferInvariantService.requireUnlinked(outgoing, incoming);
            InternalTransfer transfer = new InternalTransfer();
            transfer.setOutgoingTransaction(outgoing);
            transfer.setIncomingTransaction(incoming);
            transfer.setCreatedAt(now);
            transfer.setUpdatedAt(now);
            transfers.add(transfer);
        }
        internalTransferRepository.saveAllAndFlush(transfers);
        return transfers.size();
    }

    private InternalTransferCommandException transferFailure(InternalTransferErrorCode code) {
        return new InternalTransferCommandException(code);
    }

    private void validateCandidate(TransactionBatch batch, FinancialAccount account, TransactionCandidate candidate) {
        if (
            candidate.getTransactionBatch() == null ||
            !Objects.equals(candidate.getTransactionBatch().getId(), batch.getId()) ||
            candidate.getUser() == null ||
            !Objects.equals(candidate.getUser().getId(), batch.getUser().getId()) ||
            candidate.getAccount() == null ||
            !Objects.equals(candidate.getAccount().getId(), account.getId())
        ) {
            throw new IllegalStateException("Transaction candidate is outside the batch publication scope");
        }
        if (candidate.getSource() != TransactionCandidateSource.MANUAL && candidate.getSource() != TransactionCandidateSource.FILE_IMPORT) {
            throw new IllegalArgumentException("Transaction batch only publishes manual or CSV candidates");
        }
        if (candidate.getStatus() == TransactionCandidateStatus.CANCELLED || candidate.getStatus() == TransactionCandidateStatus.FAILED) {
            throw new IllegalArgumentException("Final transaction candidates cannot be published");
        }
        if (candidate.getValidationStatus() != TransactionCandidateValidationStatus.VALID || !candidate.getInputIssues().isEmpty()) {
            throw new IllegalArgumentException("Transaction batch has unresolved candidate input issues");
        }
        if (
            candidate.getCurrencySnapshot() != account.getCurrency() ||
            candidate.getTransactionDate() == null ||
            candidate.getDescription() == null ||
            candidate.getDescription().isBlank() ||
            candidate.getDescription().trim().length() > 500
        ) {
            throw new IllegalArgumentException("Transaction candidate is financially incomplete");
        }
        validateCanonicalAmount(candidate);
        if (candidate.getCategory() != null) {
            if (
                candidate.getCategory().getUser() == null ||
                !Objects.equals(candidate.getCategory().getUser().getId(), batch.getUser().getId()) ||
                !CategoryFlowCompatibilityValidator.isCompatible(candidate.getCategory().getCategoryType(), candidate.getFlow())
            ) {
                throw new IllegalArgumentException("Transaction candidate category is invalid");
            }
            // A selected inactive category remains a historical candidate reference; publication must preserve it
            // instead of silently dropping it or re-running rules to pick a replacement.
        }
        for (Tag tag : candidate.getTags()) {
            if (
                tag.getUser() == null || !Objects.equals(tag.getUser().getId(), batch.getUser().getId()) || candidate.isTagSuppressed(tag)
            ) {
                throw new IllegalArgumentException("Transaction candidate tag is invalid");
            }
            // As with categories, an already persisted inactive tag remains valid historical intent.
        }
    }

    private void validateCanonicalAmount(TransactionCandidate candidate) {
        BigDecimal signed = candidate.getSignedAmount();
        BigDecimal amount = candidate.getAmount();
        if (signed == null || amount == null || amount.signum() <= 0 || candidate.getFlow() == null) {
            throw new IllegalArgumentException("Transaction candidate amount and flow are required");
        }
        try {
            signed = signed.setScale(2, RoundingMode.UNNECESSARY);
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Transaction candidate amount must have at most 2 decimal places", exception);
        }
        TransactionFlow expectedFlow = signed.signum() > 0 ? TransactionFlow.IN : signed.signum() < 0 ? TransactionFlow.OUT : null;
        if (expectedFlow == null || expectedFlow != candidate.getFlow() || amount.compareTo(signed.abs()) != 0) {
            throw new IllegalArgumentException("Transaction candidate amount and flow are inconsistent");
        }
    }

    private void transitionToPublishedReceipt(
        TransactionBatch batch,
        String operationKey,
        String digest,
        int publishedCandidateCount,
        int createdCounterpartCount,
        int createdInternalTransferCount,
        Instant publishedAt
    ) {
        Duration ttl = receiptTtl();
        batch.setStatus(TransactionBatchStatus.PUBLISHED);
        batch.setAccount(null);
        batch.setNextRowPosition(null);
        batch.setAutoApplyDescriptions(null);
        batch.setAutoApplyCategories(null);
        batch.setAutoApplyTags(null);
        batch.setProtectManualChanges(null);
        batch.setPublishedAt(publishedAt);
        batch.setPublicationOperationKey(operationKey);
        batch.setPublicationRequestDigest(digest);
        // Keep the original receipt field's source-row meaning. New explicit
        // counters let new receipts describe all materialised financial legs
        // without rewriting the semantics of historical receipts.
        batch.setPublishedTransactionCount(publishedCandidateCount);
        batch.setCreatedCounterpartCount(createdCounterpartCount);
        batch.setCreatedInternalTransferCount(createdInternalTransferCount);
        batch.setReceiptExpiresAt(publishedAt.plus(ttl));
        batch.setUpdatedAt(publishedAt);
    }

    private void ensureReceiptCurrent(TransactionBatch batch, Instant now) {
        if (batch.getReceiptExpiresAt() == null || !batch.getReceiptExpiresAt().isAfter(now)) {
            throw new IllegalStateException("TRANSACTION_BATCH_RECEIPT_EXPIRED");
        }
    }

    private Duration receiptTtl() {
        Duration ttl = applicationProperties.getTransactionBatchReceipt().getTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("Transaction batch receipt TTL must be positive");
        }
        return ttl;
    }

    private void validateCommand(Long batchId, TransactionBatchPublishCommand command) {
        if (batchId == null) throw new IllegalArgumentException("Transaction batch id is required");
        if (command == null) throw new IllegalArgumentException("Transaction batch publish command is required");
        if (command.getOperationKey() == null || command.getOperationKey().isBlank()) {
            throw new IllegalArgumentException("Publication operation key is required");
        }
        String normalized = command.getOperationKey().trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("Publication operation key cannot exceed 100 characters");
        command.setOperationKey(normalized);
    }

    private void requireExpectedVersion(TransactionBatch batch, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(batch.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Transaction batch version is stale");
        }
    }

    private TransactionOrigin originFor(TransactionCandidate candidate) {
        return candidate.getSource() == TransactionCandidateSource.MANUAL ? TransactionOrigin.MANUAL : TransactionOrigin.FILE_IMPORT;
    }

    private String publicationDigest() {
        try {
            return java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(PUBLICATION_CONTRACT_DIGEST_INPUT.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private TransactionBatchPublicationReceiptDTO toReceipt(TransactionBatch batch, boolean replayed) {
        TransactionBatchPublicationReceiptDTO result = new TransactionBatchPublicationReceiptDTO();
        result.setBatchId(batch.getId());
        result.setStatus(batch.getStatus());
        result.setOperationKey(batch.getPublicationOperationKey());
        result.setPublishedAt(batch.getPublishedAt());
        result.setPublishedTransactionCount(batch.getPublishedTransactionCount());
        int publishedCandidateCount = batch.getPublishedTransactionCount() == null ? 0 : batch.getPublishedTransactionCount();
        int createdCounterpartCount = batch.getCreatedCounterpartCount() == null ? 0 : batch.getCreatedCounterpartCount();
        result.setCreatedCounterpartCount(createdCounterpartCount);
        result.setTotalCreatedFinancialTransactionCount(publishedCandidateCount + createdCounterpartCount);
        result.setCreatedInternalTransferCount(
            batch.getCreatedInternalTransferCount() == null ? 0 : batch.getCreatedInternalTransferCount()
        );
        result.setReceiptExpiresAt(batch.getReceiptExpiresAt());
        result.setReplayed(replayed);
        return result;
    }
}
