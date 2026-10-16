package com.fintrack.app.service;

import com.fintrack.app.config.ApplicationProperties;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.repository.TransactionBatchAppendOperationRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes only expired, structurally minimal PUBLISHED receipts. OPEN workspaces are never a cleanup target. */
@Service
public class TransactionBatchReceiptCleanupService {

    private final TransactionBatchRepository batchRepository;
    private final TransactionCandidateRepository candidateRepository;
    private final TransactionBatchAppendOperationRepository appendOperationRepository;
    private final ApplicationProperties applicationProperties;
    private final Clock clock;

    public TransactionBatchReceiptCleanupService(
        TransactionBatchRepository batchRepository,
        TransactionCandidateRepository candidateRepository,
        TransactionBatchAppendOperationRepository appendOperationRepository,
        ApplicationProperties applicationProperties,
        Clock clock
    ) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.appendOperationRepository = appendOperationRepository;
        this.applicationProperties = applicationProperties;
        this.clock = clock;
    }

    /** Invocable deterministic cleanup boundary for tests and operational maintenance. */
    @Transactional
    public int cleanupExpiredReceipts() {
        int limit = cleanupBatchSize();
        Instant now = clock.instant();
        List<Long> ids = batchRepository.findExpiredReceiptIds(TransactionBatchStatus.PUBLISHED, now, PageRequest.of(0, limit));
        int deleted = 0;
        for (Long id : ids) {
            TransactionBatch batch = batchRepository.findOneForReceiptCleanup(id).orElse(null);
            if (batch == null || batch.getStatus() != TransactionBatchStatus.PUBLISHED || !isExpired(batch, now)) {
                continue;
            }
            assertMinimalReceipt(batch);
            batchRepository.delete(batch);
            deleted++;
        }
        batchRepository.flush();
        return deleted;
    }

    /** The test profiles disable scheduling; production runs remain bounded by both time and row count. */
    @Scheduled(fixedDelayString = "${application.transaction-batch-receipt.cleanup-fixed-delay:PT1H}")
    public void scheduledCleanupExpiredReceipts() {
        cleanupExpiredReceipts();
    }

    private boolean isExpired(TransactionBatch batch, Instant now) {
        return batch.getReceiptExpiresAt() != null && !batch.getReceiptExpiresAt().isAfter(now);
    }

    private void assertMinimalReceipt(TransactionBatch batch) {
        if (
            batch.getAccount() != null ||
            batch.getNextRowPosition() != null ||
            batch.getAutoApplyDescriptions() != null ||
            batch.getAutoApplyCategories() != null ||
            batch.getAutoApplyTags() != null ||
            batch.getProtectManualChanges() != null ||
            batch.getPublishedAt() == null ||
            batch.getPublicationOperationKey() == null ||
            batch.getPublicationRequestDigest() == null ||
            batch.getPublishedTransactionCount() == null ||
            batch.getReceiptExpiresAt() == null ||
            candidateRepository.existsByTransactionBatchId(batch.getId()) ||
            appendOperationRepository.existsByTransactionBatchId(batch.getId())
        ) {
            throw new IllegalStateException("Expired transaction batch receipt contains unexpected workspace state");
        }
    }

    private int cleanupBatchSize() {
        int size = applicationProperties.getTransactionBatchReceipt().getCleanupBatchSize();
        if (size <= 0) throw new IllegalStateException("Transaction batch receipt cleanup batch size must be positive");
        return size;
    }
}
