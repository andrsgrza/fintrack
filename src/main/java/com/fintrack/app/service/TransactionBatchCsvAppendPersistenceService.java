package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.TransactionBatchAppendOperation;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionCandidateInputIssue;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.TransactionBatchAppendOperationRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvParseResult;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvRowResult;
import com.fintrack.app.service.csv.CsvIngestionValidationMessage;
import com.fintrack.app.service.dto.TransactionBatchAppendCsvCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of a CSV append. It owns no parser result after this method returns and never evaluates
 * rules. Keeping that boundary separate makes a successful append durable even when later evaluation is unavailable.
 */
@Service
public class TransactionBatchCsvAppendPersistenceService {

    private final TransactionBatchRepository batchRepository;
    private final TransactionBatchAppendOperationRepository operationRepository;
    private final TransactionCandidateRepository candidateRepository;
    private final CurrentUserService currentUserService;

    public TransactionBatchCsvAppendPersistenceService(
        TransactionBatchRepository batchRepository,
        TransactionBatchAppendOperationRepository operationRepository,
        TransactionCandidateRepository candidateRepository,
        CurrentUserService currentUserService
    ) {
        this.batchRepository = batchRepository;
        this.operationRepository = operationRepository;
        this.candidateRepository = candidateRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public TransactionBatchAppendOperation findAcceptedOperation(Long batchId, String operationKey) {
        String login = currentUserService.getCurrentUserLogin();
        requireOwnedBatch(batchId, login);
        return operationRepository.findOneByBatchIdAndOwnerLoginAndOperationKey(batchId, login, operationKey).orElse(null);
    }

    /** Lightweight owned read used only to supply the canonical account currency to the canonical parser. */
    @Transactional(readOnly = true)
    public CurrencyCode readAccountCurrency(Long batchId) {
        TransactionBatch batch = requireOwnedBatch(batchId, currentUserService.getCurrentUserLogin());
        if (batch.getStatus() != TransactionBatchStatus.OPEN || batch.getAccount() == null || batch.getAccount().getCurrency() == null) {
            throw new IllegalArgumentException("Open transaction batch account currency is required");
        }
        return batch.getAccount().getCurrency();
    }

    @Transactional
    public PersistedAppend appendNew(Long batchId, TransactionBatchAppendCsvCommand command, String digest, CsvParseResult parsed) {
        String login = currentUserService.getCurrentUserLogin();
        TransactionBatch batch = batchRepository
            .findOneForOwnedStatusMutation(batchId, login, TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Open transaction batch is not accessible"));

        TransactionBatchAppendOperation existing = operationRepository
            .findOneByBatchIdAndOwnerLoginAndOperationKey(batch.getId(), login, command.getOperationKey())
            .orElse(null);
        if (existing != null) {
            return replayOrConflict(batch, existing, digest);
        }
        if (command.getExpectedBatchVersion() == null || !Objects.equals(batch.getVersion(), command.getExpectedBatchVersion())) {
            throw new IllegalStateException("Transaction batch version is stale");
        }

        List<TransactionCandidate> candidates = new ArrayList<>();
        for (CsvRowResult row : parsed.getRows()) {
            candidates.add(toCandidate(batch, row));
        }
        candidates = candidateRepository.saveAllAndFlush(candidates);

        Instant now = Instant.now();
        TransactionBatchAppendOperation operation = new TransactionBatchAppendOperation();
        operation.setTransactionBatch(batch);
        operation.setOperationKey(command.getOperationKey());
        operation.setRequestDigest(digest);
        operation.setAcceptedCandidateCount(candidates.size());
        operation.setDisplayFilename(command.getFileName());
        operation.setAcceptedAt(now);
        operation = operationRepository.saveAndFlush(operation);

        // Reserving stable positions is a material workspace change. One root write covers the complete append.
        batch.setUpdatedAt(now);
        batch = batchRepository.saveAndFlush(batch);
        return new PersistedAppend(batch, operation, candidates.stream().map(TransactionCandidate::getId).toList(), false);
    }

    private PersistedAppend replayOrConflict(TransactionBatch batch, TransactionBatchAppendOperation existing, String digest) {
        if (!Objects.equals(existing.getRequestDigest(), digest)) {
            throw new IllegalStateException("OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT");
        }
        return new PersistedAppend(batch, existing, List.of(), true);
    }

    private TransactionBatch requireOwnedBatch(Long batchId, String login) {
        if (batchId == null) throw new IllegalArgumentException("Transaction batch id is required");
        return batchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, login)
            .orElseThrow(() -> new IllegalArgumentException("Transaction batch is not accessible"));
    }

    private TransactionCandidate toCandidate(TransactionBatch batch, CsvRowResult row) {
        boolean currencyIssue = row
            .getErrors()
            .stream()
            .anyMatch(message -> fieldFor(message) == TransactionCandidateInputIssueField.CURRENCY);
        Instant now = Instant.now();
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setTransactionBatch(batch);
        candidate.setRowPosition(reserveRowPosition(batch));
        candidate.setUser(batch.getUser());
        candidate.setAccount(batch.getAccount());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        candidate.setStatus(TransactionCandidateStatus.DRAFT);
        candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        candidate.setCategoryManualClear(false);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);

        var normalized = row.getNormalized();
        candidate.setTransactionDate(normalized.getTransactionDate());
        candidate.setPostingDate(normalized.getPostingDate());
        candidate.setDescription(normalized.getDescription());
        candidate.setSourceDescription(normalized.getDescription());
        candidate.setDescriptionSource(normalized.getDescription() == null ? null : TransactionCandidateDescriptionSource.SOURCE);
        candidate.setExternalReference(normalized.getExternalReference());
        candidate.setNotes(normalized.getNotes());

        if (!currencyIssue) {
            candidate.setCurrencySnapshot(batch.getAccount().getCurrency());
            candidate.setSignedAmount(decimalOrNull(normalized.getSignedAmount()));
            candidate.setAmount(decimalOrNull(normalized.getAmount()));
            candidate.setFlow(normalized.getFlow());
        }
        addRepairIssues(candidate, row);
        candidate.setValidationStatus(validationStatus(candidate));
        return candidate;
    }

    private long reserveRowPosition(TransactionBatch batch) {
        long next = batch.getNextRowPosition();
        batch.setNextRowPosition(Math.addExact(next, 1));
        return next;
    }

    private void addRepairIssues(TransactionCandidate candidate, CsvRowResult row) {
        for (CsvIngestionValidationMessage message : row.getErrors()) {
            TransactionCandidateInputIssueField field = fieldFor(message);
            if (field == null) continue;
            TransactionCandidateInputIssue issue = new TransactionCandidateInputIssue();
            issue.setField(field);
            issue.setReasonCode(reasonFor(message));
            issue.setSubmittedValue(issueValue(row, field));
            candidate.addOrReplaceInputIssue(issue);
        }
    }

    private TransactionCandidateInputIssueField fieldFor(CsvIngestionValidationMessage message) {
        if (message == null || message.getCode() == null) return null;
        return switch (message.getCode()) {
            case "INVALID_TRANSACTION_DATE" -> TransactionCandidateInputIssueField.TRANSACTION_DATE;
            case "INVALID_POSTING_DATE" -> TransactionCandidateInputIssueField.POSTING_DATE;
            case "SIGNED_AMOUNT_REQUIRED",
                "ZERO_SIGNED_AMOUNT",
                "AMOUNT_SCALE_EXCEEDED",
                "INVALID_SIGNED_AMOUNT" -> TransactionCandidateInputIssueField.AMOUNT;
            case "CURRENCY_REQUIRED", "UNSUPPORTED_CURRENCY", "CURRENCY_MISMATCH" -> TransactionCandidateInputIssueField.CURRENCY;
            default -> null;
        };
    }

    private TransactionCandidateInputIssueReason reasonFor(CsvIngestionValidationMessage message) {
        return switch (message.getCode()) {
            case "UNSUPPORTED_CURRENCY" -> TransactionCandidateInputIssueReason.UNSUPPORTED_CURRENCY;
            case "CURRENCY_MISMATCH" -> TransactionCandidateInputIssueReason.CURRENCY_MISMATCH;
            default -> TransactionCandidateInputIssueReason.MALFORMED_VALUE;
        };
    }

    private String issueValue(CsvRowResult row, TransactionCandidateInputIssueField field) {
        String raw =
            switch (field) {
                case TRANSACTION_DATE -> row.getRaw().getTransactionDate();
                case POSTING_DATE -> row.getRaw().getPostingDate();
                case AMOUNT -> row.getRaw().getSignedAmount();
                case CURRENCY -> row.getRaw().getCurrency();
            };
        if (raw == null || raw.isBlank()) return "<missing>";
        String trimmed = raw.trim();
        // The schema deliberately bounds repair evidence. Do not pretend a truncation is the submitted token.
        return trimmed.length() <= 100 ? trimmed : "<value exceeds 100 characters>";
    }

    private BigDecimal decimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private TransactionCandidateValidationStatus validationStatus(TransactionCandidate candidate) {
        if (!candidate.getInputIssues().isEmpty()) return TransactionCandidateValidationStatus.INVALID;
        boolean complete =
            candidate.getTransactionDate() != null &&
            candidate.getDescription() != null &&
            candidate.getAmount() != null &&
            candidate.getAmount().signum() > 0 &&
            candidate.getFlow() != null &&
            candidate.getCurrencySnapshot() == candidate.getAccount().getCurrency();
        return complete ? TransactionCandidateValidationStatus.VALID : TransactionCandidateValidationStatus.UNKNOWN;
    }

    public record PersistedAppend(
        TransactionBatch batch,
        TransactionBatchAppendOperation operation,
        List<Long> appendedCandidateIds,
        boolean replayed
    ) {}
}
