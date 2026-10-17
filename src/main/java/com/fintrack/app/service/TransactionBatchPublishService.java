package com.fintrack.app.service;

import com.fintrack.app.config.ApplicationProperties;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.TransactionBatchAppendOperationRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.TransactionBatchPublicationReceiptDTO;
import com.fintrack.app.service.dto.TransactionBatchPublishCommand;
import com.fintrack.app.service.mapper.TransactionCandidateFinancialTransactionMapper;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
    private final TransactionCandidateFinancialTransactionMapper candidateFinancialTransactionMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final ApplicationProperties applicationProperties;

    public TransactionBatchPublishService(
        TransactionBatchRepository batchRepository,
        TransactionCandidateRepository candidateRepository,
        TransactionBatchAppendOperationRepository appendOperationRepository,
        FinancialAccountRepository accountRepository,
        FinancialTransactionRepository financialTransactionRepository,
        TransactionCandidateFinancialTransactionMapper candidateFinancialTransactionMapper,
        CurrentUserService currentUserService,
        Clock clock,
        ApplicationProperties applicationProperties
    ) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.appendOperationRepository = appendOperationRepository;
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.candidateFinancialTransactionMapper = candidateFinancialTransactionMapper;
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

        // Account -> root is the same lock order used by account hard-delete and all OPEN-batch mutators lock root.
        FinancialAccount account = accountRepository
            .findOneForBatchPublicationByIdAndUserLogin(observed.getAccount().getId(), login)
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
        FinancialAccountReferenceValidator.validateActiveForNewReference(account);

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
        if (!Objects.equals(batch.getAccount().getId(), account.getId())) {
            throw new IllegalStateException("Transaction batch account changed during publication");
        }

        List<TransactionCandidate> candidates = candidateRepository.findAllBatchCandidatesForPublication(batch.getId(), login);
        validatePublicationAggregate(batch, account, candidates);

        List<FinancialTransaction> finalTransactions = candidates
            .stream()
            .map(candidate -> candidateFinancialTransactionMapper.toFinancialTransaction(candidate, originFor(candidate), now))
            .toList();
        // One batched persistence boundary: any later FK/constraint or cleanup failure rolls every new final back.
        financialTransactionRepository.saveAllAndFlush(finalTransactions);

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
        transitionToPublishedReceipt(batch, command.getOperationKey(), digest, finalTransactions.size(), now);
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

    private void transitionToPublishedReceipt(TransactionBatch batch, String operationKey, String digest, int count, Instant publishedAt) {
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
        batch.setPublishedTransactionCount(count);
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
        result.setReceiptExpiresAt(batch.getReceiptExpiresAt());
        result.setReplayed(replayed);
        return result;
    }
}
