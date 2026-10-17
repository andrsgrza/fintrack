package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.service.dto.TransactionBatchSettingsCommand;
import com.fintrack.app.service.dto.TransactionBatchSettingsResultDTO;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists OPEN-batch automation settings without conflating that durable success with a later evaluation pass. */
@Service
@Transactional
public class TransactionBatchSettingsService {

    private final TransactionBatchRepository batchRepository;
    private final CurrentUserService currentUserService;

    public TransactionBatchSettingsService(TransactionBatchRepository batchRepository, CurrentUserService currentUserService) {
        this.batchRepository = batchRepository;
        this.currentUserService = currentUserService;
    }

    public TransactionBatchSettingsResultDTO save(Long batchId, TransactionBatchSettingsCommand command) {
        if (command == null) throw new IllegalArgumentException("Transaction batch settings are required");
        if (
            command.getAutoApplyDescriptions() == null ||
            command.getAutoApplyCategories() == null ||
            command.getAutoApplyTags() == null ||
            command.getProtectManualChanges() == null
        ) throw new IllegalArgumentException("All transaction batch settings are required");

        TransactionBatch batch = batchRepository
            .findOneForOwnedStatusMutation(batchId, currentUserService.getCurrentUserLogin(), TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Open transaction batch is not accessible"));
        if (command.getExpectedBatchVersion() == null || !Objects.equals(batch.getVersion(), command.getExpectedBatchVersion())) {
            throw new IllegalStateException("Transaction batch version is stale");
        }

        boolean changed =
            !Objects.equals(batch.getAutoApplyDescriptions(), command.getAutoApplyDescriptions()) ||
            !Objects.equals(batch.getAutoApplyCategories(), command.getAutoApplyCategories()) ||
            !Objects.equals(batch.getAutoApplyTags(), command.getAutoApplyTags()) ||
            !Objects.equals(batch.getProtectManualChanges(), command.getProtectManualChanges());
        if (changed) {
            batch.setAutoApplyDescriptions(command.getAutoApplyDescriptions());
            batch.setAutoApplyCategories(command.getAutoApplyCategories());
            batch.setAutoApplyTags(command.getAutoApplyTags());
            batch.setProtectManualChanges(command.getProtectManualChanges());
            batch.setUpdatedAt(Instant.now());
            batch = batchRepository.saveAndFlush(batch);
        }
        TransactionBatchSettingsResultDTO result = new TransactionBatchSettingsResultDTO();
        result.setBatchId(batch.getId());
        result.setBatchVersion(batch.getVersion());
        result.setChanged(changed);
        return result;
    }
}
