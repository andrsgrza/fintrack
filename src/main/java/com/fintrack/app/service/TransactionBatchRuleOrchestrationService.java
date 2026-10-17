package com.fintrack.app.service;

import com.fintrack.app.service.dto.*;
import java.util.List;
import org.springframework.stereotype.Service;

/** Coordinates durable setting changes and fresh batch evaluation without turning preview into a write. */
@Service
public class TransactionBatchRuleOrchestrationService {

    private final TransactionBatchSettingsService settingsService;
    private final TransactionBatchRuleEvaluationService evaluationService;
    private final TransactionBatchCommandService commandService;

    public TransactionBatchRuleOrchestrationService(
        TransactionBatchSettingsService settingsService,
        TransactionBatchRuleEvaluationService evaluationService,
        TransactionBatchCommandService commandService
    ) {
        this.settingsService = settingsService;
        this.evaluationService = evaluationService;
        this.commandService = commandService;
    }

    /** Settings are saved first. A caller can distinguish a later evaluation failure from a failed settings write. */
    public TransactionBatchEvaluationRunDTO saveSettingsAndReevaluate(Long batchId, TransactionBatchSettingsCommand command) {
        settingsService.save(batchId, command);
        return reevaluate(batchId);
    }

    /** Performs a fresh preview and lets only enabled conservative automation apply. */
    public TransactionBatchEvaluationRunDTO reevaluate(Long batchId) {
        List<TransactionBatchRulePreviewDTO> previews = evaluationService.previewBatch(batchId);
        List<TransactionBatchRuleApplyResultDTO> applications = evaluationService.reevaluateAndAutoApply(
            batchId,
            previews.stream().map(TransactionBatchRulePreviewDTO::getCandidateId).toList()
        );
        TransactionBatchEvaluationRunDTO result = new TransactionBatchEvaluationRunDTO();
        result.setBatchId(batchId);
        result.setPreviews(previews);
        result.setApplications(applications);
        result.setBatchVersion(commandService.findOpenBatch(batchId).orElseThrow().getVersion());
        return result;
    }

    /** Used by append after its persistence transaction commits; no older workspace rows are included. */
    public TransactionBatchEvaluationRunDTO reevaluateCandidates(Long batchId, List<Long> candidateIds) {
        List<TransactionBatchRuleApplyResultDTO> applications = evaluationService.reevaluateAndAutoApply(batchId, candidateIds);
        TransactionBatchEvaluationRunDTO result = new TransactionBatchEvaluationRunDTO();
        result.setBatchId(batchId);
        result.setApplications(applications);
        result.setPreviews(
            candidateIds == null ? List.of() : candidateIds.stream().map(id -> evaluationService.previewCandidate(batchId, id)).toList()
        );
        result.setBatchVersion(commandService.findOpenBatch(batchId).orElseThrow().getVersion());
        return result;
    }
}
