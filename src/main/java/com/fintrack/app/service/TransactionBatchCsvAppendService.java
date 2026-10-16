package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionBatchAppendOperation;
import com.fintrack.app.service.TransactionBatchCsvAppendPersistenceService.PersistedAppend;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser;
import com.fintrack.app.service.dto.TransactionBatchAppendCsvCommand;
import com.fintrack.app.service.dto.TransactionBatchAppendResultDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Internal append entry point: authorize/replay-check, parse once, persist atomically, then evaluate after commit. */
@Service
public class TransactionBatchCsvAppendService {

    private final TransactionBatchCsvAppendPersistenceService persistenceService;
    private final TransactionBatchRuleOrchestrationService ruleOrchestrationService;
    private final CanonicalCsvIngestionParser canonicalCsvIngestionParser;

    public TransactionBatchCsvAppendService(
        TransactionBatchCsvAppendPersistenceService persistenceService,
        TransactionBatchRuleOrchestrationService ruleOrchestrationService,
        CanonicalCsvIngestionParser canonicalCsvIngestionParser
    ) {
        this.persistenceService = persistenceService;
        this.ruleOrchestrationService = ruleOrchestrationService;
        this.canonicalCsvIngestionParser = canonicalCsvIngestionParser;
    }

    public TransactionBatchAppendResultDTO append(Long batchId, TransactionBatchAppendCsvCommand command) {
        validate(command);
        String digest = digest(command);
        TransactionBatchAppendOperation existing = persistenceService.findAcceptedOperation(batchId, command.getOperationKey());
        if (existing != null) {
            if (!Objects.equals(existing.getRequestDigest(), digest)) {
                throw new IllegalStateException("OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT");
            }
            return receipt(existing, existing.getTransactionBatch().getVersion(), true, "NOT_REEVALUATED_REPLAY", java.util.List.of());
        }

        // Parsing happens after authorization and outside the root mutation transaction. No parser artifact is retained.
        var parsed = canonicalCsvIngestionParser.parse(command.getBytes(), persistenceService.readAccountCurrency(batchId));
        PersistedAppend persisted = persistenceService.appendNew(batchId, command, digest, parsed);
        if (persisted.replayed()) {
            return receipt(persisted.operation(), persisted.batch().getVersion(), true, "NOT_REEVALUATED_REPLAY", java.util.List.of());
        }

        String evaluationStatus = "EVALUATED";
        Long authoritativeBatchVersion = persisted.batch().getVersion();
        try {
            authoritativeBatchVersion = ruleOrchestrationService
                .reevaluateCandidates(batchId, persisted.appendedCandidateIds())
                .getBatchVersion();
        } catch (RuntimeException evaluationFailure) {
            // The persistence transaction is already complete. A caller can retry reevaluation without ever retrying
            // append under a different key.
            evaluationStatus = "EVALUATION_PENDING";
        }
        return receipt(persisted.operation(), authoritativeBatchVersion, false, evaluationStatus, persisted.appendedCandidateIds());
    }

    private TransactionBatchAppendResultDTO receipt(
        TransactionBatchAppendOperation operation,
        Long batchVersion,
        boolean replayed,
        String evaluationStatus,
        java.util.List<Long> candidateIds
    ) {
        TransactionBatchAppendResultDTO result = new TransactionBatchAppendResultDTO();
        result.setBatchId(operation.getTransactionBatch().getId());
        result.setBatchVersion(batchVersion);
        result.setOperationKey(operation.getOperationKey());
        result.setAcceptedCandidateCount(operation.getAcceptedCandidateCount());
        result.setAcceptedAt(operation.getAcceptedAt());
        result.setReplayed(replayed);
        result.setEvaluationStatus(evaluationStatus);
        result.setAppendedCandidateIds(candidateIds);
        return result;
    }

    private void validate(TransactionBatchAppendCsvCommand command) {
        if (command == null) throw new IllegalArgumentException("CSV append command is required");
        command.setOperationKey(requiredBounded(command.getOperationKey(), 100, "Operation key"));
        command.setFileName(optionalBounded(command.getFileName(), 255, "Filename"));
        command.setContentType(requiredBounded(command.getContentType(), 100, "Content type"));
        command.setParserVersion(requiredBounded(command.getParserVersion(), 100, "Parser version"));
        if (!"canonical-csv-v1".equals(command.getParserVersion())) {
            throw new IllegalArgumentException("Unsupported CSV parser version");
        }
        if (!"text/csv".equalsIgnoreCase(command.getContentType())) {
            throw new IllegalArgumentException("Unsupported CSV content type");
        }
        if (command.getBytes() == null || command.getBytes().length == 0) throw new IllegalArgumentException("CSV file is required");
    }

    private String requiredBounded(String value, int maximum, String label) {
        String normalized = optionalBounded(value, maximum, label);
        if (normalized == null) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }

    private String optionalBounded(String value, int maximum, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) throw new IllegalArgumentException(label + " cannot exceed " + maximum + " characters");
        return normalized;
    }

    private String digest(TransactionBatchAppendCsvCommand command) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            digestPart(sha256, command.getBytes());
            digestPart(sha256, command.getFileName() == null ? "" : command.getFileName());
            digestPart(sha256, command.getContentType());
            digestPart(sha256, command.getParserVersion());
            return java.util.HexFormat.of().formatHex(sha256.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void digestPart(MessageDigest digest, byte[] value) {
        digest.update((byte) 0);
        digest.update(value);
    }

    private void digestPart(MessageDigest digest, String value) {
        digestPart(digest, value.getBytes(StandardCharsets.UTF_8));
    }
}
