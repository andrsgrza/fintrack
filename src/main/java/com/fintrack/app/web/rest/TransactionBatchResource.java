package com.fintrack.app.web.rest;

import com.fintrack.app.service.TransactionBatchCommandService;
import com.fintrack.app.service.TransactionBatchCsvAppendService;
import com.fintrack.app.service.TransactionBatchPublishService;
import com.fintrack.app.service.TransactionBatchReceiptService;
import com.fintrack.app.service.TransactionBatchRuleEvaluationService;
import com.fintrack.app.service.TransactionBatchRuleOrchestrationService;
import com.fintrack.app.service.dto.*;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated product boundary for the temporary TransactionBatch workspace. It delegates every domain decision
 * to the internal services; no generic candidate or receipt CRUD is exposed here.
 */
@RestController
@RequestMapping("/api/transaction-batches")
public class TransactionBatchResource {

    private static final String ENTITY_NAME = "transactionBatch";

    private final TransactionBatchCommandService commandService;
    private final TransactionBatchCsvAppendService csvAppendService;
    private final TransactionBatchRuleEvaluationService ruleEvaluationService;
    private final TransactionBatchRuleOrchestrationService ruleOrchestrationService;
    private final TransactionBatchPublishService publishService;
    private final TransactionBatchReceiptService receiptService;

    public TransactionBatchResource(
        TransactionBatchCommandService commandService,
        TransactionBatchCsvAppendService csvAppendService,
        TransactionBatchRuleEvaluationService ruleEvaluationService,
        TransactionBatchRuleOrchestrationService ruleOrchestrationService,
        TransactionBatchPublishService publishService,
        TransactionBatchReceiptService receiptService
    ) {
        this.commandService = commandService;
        this.csvAppendService = csvAppendService;
        this.ruleEvaluationService = ruleEvaluationService;
        this.ruleOrchestrationService = ruleOrchestrationService;
        this.publishService = publishService;
        this.receiptService = receiptService;
    }

    @PostMapping("")
    public ResponseEntity<TransactionBatchWorkspaceDTO> create(@Valid @RequestBody TransactionBatchCreateCommand command)
        throws URISyntaxException {
        TransactionBatchWorkspaceDTO result = invoke(() -> commandService.createOpenBatch(command.getAccountId()));
        return ResponseEntity.created(new URI("/api/transaction-batches/" + result.getId())).body(result);
    }

    @GetMapping("")
    public List<TransactionBatchWorkspaceDTO> listOpen() {
        return invoke(commandService::findOpenBatches);
    }

    @GetMapping("/{batchId}")
    public TransactionBatchWorkspaceDTO getOpen(@PathVariable Long batchId) {
        return commandService
            .findOpenBatch(batchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OPEN_BATCH_NOT_FOUND"));
    }

    @PostMapping("/{batchId}/candidates")
    public TransactionBatchRowMutationDTO addManualDraft(
        @PathVariable Long batchId,
        @Valid @RequestBody TransactionBatchDraftCommand command
    ) {
        return invoke(() -> commandService.addManualDraft(batchId, command));
    }

    @PatchMapping("/{batchId}/candidates/{candidateId}")
    public TransactionBatchRowMutationDTO patchCandidate(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @RequestBody TransactionBatchCandidatePatchCommand command
    ) {
        return invoke(() -> commandService.patchCandidate(batchId, candidateId, command));
    }

    @PutMapping("/{batchId}/candidates/{candidateId}/category")
    public TransactionBatchRowMutationDTO setCategory(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @Valid @RequestBody TransactionBatchCategoryCommand command
    ) {
        return invoke(() ->
            command.getCategoryId() == null
                ? commandService.clearCategory(batchId, candidateId, command.getExpectedCandidateVersion())
                : commandService.setCategory(batchId, candidateId, command.getExpectedCandidateVersion(), command.getCategoryId())
        );
    }

    @PutMapping("/{batchId}/candidates/{candidateId}/tags")
    public TransactionBatchRowMutationDTO replaceTags(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @Valid @RequestBody TransactionBatchTagCommand command
    ) {
        return invoke(() -> commandService.replaceTags(batchId, candidateId, command.getExpectedCandidateVersion(), command.getTagIds()));
    }

    /** Stores a pre-publication link intent only; no transfer exists until Batch publication succeeds. */
    @PutMapping("/{batchId}/candidates/{candidateId}/internal-transfer")
    public TransactionBatchRowMutationDTO setInternalTransfer(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @Valid @RequestBody TransactionBatchInternalTransferCommand command
    ) {
        return invoke(() -> commandService.setInternalTransfer(batchId, candidateId, command));
    }

    /** Removes only the temporary link intent, leaving every normal row field unchanged. */
    @DeleteMapping("/{batchId}/candidates/{candidateId}/internal-transfer")
    public TransactionBatchRowMutationDTO clearInternalTransfer(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @Valid @RequestBody TransactionBatchInternalTransferClearCommand command
    ) {
        return invoke(() -> commandService.clearInternalTransfer(batchId, candidateId, command.getExpectedCandidateVersion()));
    }

    /** Server-scoped counterpart search; browser clients never enumerate all transactions locally. */
    @GetMapping("/{batchId}/candidates/{candidateId}/transfer-counterparts")
    public Page<InternalTransferCounterpartDTO> findEligibleTransferCounterparts(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long accountId,
        Pageable pageable
    ) {
        return invoke(() -> commandService.findEligibleTransferCounterparts(batchId, candidateId, search, accountId, pageable));
    }

    @DeleteMapping("/{batchId}/candidates/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(@PathVariable Long batchId, @PathVariable Long candidateId, @RequestParam Long expectedCandidateVersion) {
        invoke(() -> commandService.deleteCandidate(batchId, candidateId, expectedCandidateVersion));
    }

    @PostMapping(value = "/{batchId}/append-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransactionBatchAppendResultDTO appendCsv(
        @PathVariable Long batchId,
        @RequestParam Long expectedBatchVersion,
        @RequestParam String operationKey,
        @RequestPart("file") MultipartFile file
    ) {
        TransactionBatchAppendCsvCommand command = new TransactionBatchAppendCsvCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setOperationKey(operationKey);
        command.setFileName(file.getOriginalFilename());
        command.setContentType(file.getContentType() == null ? "text/csv" : file.getContentType());
        try {
            command.setBytes(file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV_READ_FAILED", exception);
        }
        return invoke(() -> csvAppendService.append(batchId, command));
    }

    @GetMapping("/{batchId}/rule-preview")
    public List<TransactionBatchRulePreviewDTO> previewRules(@PathVariable Long batchId) {
        return invoke(() -> ruleEvaluationService.previewBatch(batchId));
    }

    @PostMapping("/{batchId}/reevaluate")
    public TransactionBatchEvaluationRunDTO reevaluate(@PathVariable Long batchId) {
        return invoke(() -> ruleOrchestrationService.reevaluate(batchId));
    }

    /** Reevaluate one current OPEN-batch row through the same batch orchestration used by CSV append. */
    @PostMapping("/{batchId}/candidates/{candidateId}/reevaluate")
    public TransactionBatchEvaluationRunDTO reevaluateCandidate(@PathVariable Long batchId, @PathVariable Long candidateId) {
        return invoke(() -> ruleOrchestrationService.reevaluateCandidates(batchId, List.of(candidateId)));
    }

    @PostMapping("/{batchId}/candidates/{candidateId}/apply-rules")
    public TransactionBatchRuleApplyResultDTO applyRules(
        @PathVariable Long batchId,
        @PathVariable Long candidateId,
        @RequestBody TransactionBatchRuleApplyCommand command
    ) {
        return invoke(() -> ruleEvaluationService.applyExplicit(batchId, candidateId, command));
    }

    @PutMapping("/{batchId}/settings")
    public TransactionBatchEvaluationRunDTO saveSettings(
        @PathVariable Long batchId,
        @Valid @RequestBody TransactionBatchSettingsCommand command
    ) {
        return invoke(() -> ruleOrchestrationService.saveSettingsAndReevaluate(batchId, command));
    }

    @PostMapping("/{batchId}/publish")
    public TransactionBatchPublicationReceiptDTO publish(
        @PathVariable Long batchId,
        @Valid @RequestBody TransactionBatchPublishCommand command
    ) {
        return invoke(() -> publishService.publish(batchId, command));
    }

    @GetMapping("/{batchId}/receipt")
    public TransactionBatchPublicationReceiptDTO receipt(@PathVariable Long batchId) {
        return invoke(() -> receiptService.findLiveReceipt(batchId));
    }

    @DeleteMapping("/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable Long batchId, @RequestParam Long expectedBatchVersion) {
        boolean discarded = invoke(() -> commandService.discardOpenBatch(batchId, expectedBatchVersion));
        if (!discarded) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OPEN_BATCH_NOT_FOUND");
    }

    private <T> T invoke(ThrowingSupplier<T> action) {
        try {
            return action.get();
        } catch (InternalTransferCommandException exception) {
            String code = exception.getCode().name();
            HttpStatus status = code.contains("STALE") || code.contains("LINKED") || code.contains("VERSION")
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, code, exception);
        } catch (IllegalArgumentException exception) {
            HttpStatus status = exception.getMessage() != null && exception.getMessage().contains("accessible")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            String code = exception.getMessage();
            if ("TRANSACTION_BATCH_RECEIPT_EXPIRED".equals(code)) {
                throw new ResponseStatusException(HttpStatus.GONE, code, exception);
            }
            if (code != null && (code.contains("stale") || code.contains("PUBLISHED") || code.contains("OPERATION_KEY"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, code, exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code, exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
