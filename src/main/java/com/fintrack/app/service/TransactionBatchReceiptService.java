package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.service.dto.TransactionBatchPublicationReceiptDTO;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the bounded technical receipt without exposing the deleted working aggregate. */
@Service
@Transactional(readOnly = true)
public class TransactionBatchReceiptService {

    private final TransactionBatchRepository batchRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public TransactionBatchReceiptService(TransactionBatchRepository batchRepository, CurrentUserService currentUserService, Clock clock) {
        this.batchRepository = batchRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public TransactionBatchPublicationReceiptDTO findLiveReceipt(Long batchId) {
        TransactionBatch batch = batchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Transaction batch is not accessible"));
        if (batch.getStatus() != TransactionBatchStatus.PUBLISHED) {
            throw new IllegalStateException("TRANSACTION_BATCH_RECEIPT_NOT_AVAILABLE");
        }
        Instant now = clock.instant();
        if (batch.getReceiptExpiresAt() == null || !batch.getReceiptExpiresAt().isAfter(now)) {
            throw new IllegalStateException("TRANSACTION_BATCH_RECEIPT_EXPIRED");
        }
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
        result.setReplayed(false);
        return result;
    }
}
