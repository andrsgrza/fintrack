package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CsvIngestionDescriptionReevaluationRequestDTO;
import com.fintrack.app.service.dto.CsvIngestionDescriptionReevaluationResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionDescriptionReevaluationRowDTO;
import com.fintrack.app.service.dto.CsvIngestionDescriptionReviewDTO;
import com.fintrack.app.service.rules.DescriptionNormalizationRuleEvaluationResult;
import com.fintrack.app.service.rules.DescriptionNormalizationRuleEvaluationService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-evaluates description-normalization rules for persisted CSV review rows.
 *
 * Description rules always evaluate {@code rawData.raw.description}. The request can ask for a preview only, or for
 * persisted application. A {@code USER_EDIT} description remains protected by default; an explicit apply request with
 * manual-change protection disabled may replace it with the current rule result.
 */
@Service
@Transactional
public class CsvIngestionDescriptionReevaluationService {

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final CurrentUserService currentUserService;
    private final DescriptionNormalizationRuleEvaluationService descriptionNormalizationRuleEvaluationService;
    private final FileImportTransactionCandidatePreparationService fileImportTransactionCandidatePreparationService;
    private final ObjectMapper objectMapper;

    public CsvIngestionDescriptionReevaluationService(
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        CurrentUserService currentUserService,
        DescriptionNormalizationRuleEvaluationService descriptionNormalizationRuleEvaluationService,
        FileImportTransactionCandidatePreparationService fileImportTransactionCandidatePreparationService,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.currentUserService = currentUserService;
        this.descriptionNormalizationRuleEvaluationService = descriptionNormalizationRuleEvaluationService;
        this.fileImportTransactionCandidatePreparationService = fileImportTransactionCandidatePreparationService;
        this.objectMapper = objectMapper;
    }

    public CsvIngestionDescriptionReevaluationResponseDTO reevaluate(
        Long transactionIngestionId,
        CsvIngestionDescriptionReevaluationRequestDTO request
    ) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, userLogin);
        List<IngestionRecord> records = selectRecords(ingestion, request);
        boolean apply = request == null || !Boolean.FALSE.equals(request.getApply());
        boolean protectManualChanges = request == null || !Boolean.FALSE.equals(request.getProtectManualChanges());

        CsvIngestionDescriptionReevaluationResponseDTO response = new CsvIngestionDescriptionReevaluationResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setRows(records.stream().map(record -> reevaluateRecord(record, userLogin, apply, protectManualChanges)).toList());
        return response;
    }

    private CsvIngestionDescriptionReevaluationRowDTO reevaluateRecord(
        IngestionRecord record,
        String userLogin,
        boolean apply,
        boolean protectManualChanges
    ) {
        CsvIngestionDescriptionReevaluationRowDTO response = baseRow(record);
        if (record.getStatus() != IngestionRecordStatus.VALID) {
            response.setAction("SKIPPED");
            response.setError("Only VALID ingestion records can be reevaluated");
            response.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(rawData(record)));
            return response;
        }

        ObjectNode root = rawData(record);
        String originalDescription = textOrNull(root.path("raw"), "description");
        if (originalDescription == null) {
            response.setAction("SKIPPED");
            response.setError("Original description is required for reevaluation");
            response.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(root));
            return response;
        }

        DescriptionNormalizationRuleEvaluationResult result = descriptionNormalizationRuleEvaluationService.evaluate(
            userLogin,
            originalDescription
        );
        if (isUserEditedDescription(root) && (!apply || protectManualChanges)) {
            response.setAction("MANUAL_PRESERVED");
            response.setSuggestedDescription(result.matched() ? result.resultingDescription() : null);
            response.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(root));
            return response;
        }

        if (!apply) {
            response.setAction(result.matched() ? "PREVIEWED" : "NO_MATCH");
            response.setSuggestedDescription(result.matched() ? result.resultingDescription() : null);
            response.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(root));
            return response;
        }

        ObjectNode normalized = ensureObject(root, "normalized");
        if (result.matched()) {
            ObjectNode review = ensureObject(root, "review");
            ObjectNode descriptionReview = ensureObject(review, "description");
            normalized.put("description", result.resultingDescription());
            descriptionReview.put("source", "DESCRIPTION_RULE");
            descriptionReview.put("ruleId", result.ruleId());
            descriptionReview.put("ruleName", result.ruleName());
            descriptionReview.put("resultingDescription", result.resultingDescription());
            descriptionReview.putNull("editedAt");
            descriptionReview.putNull("editedBy");
            response.setAction("APPLIED");
        } else {
            normalized.put("description", originalDescription);
            removeDescriptionReview(root);
            response.setAction("NO_MATCH");
        }

        record.setRawData(writeRawData(root));
        ingestionRecordRepository.save(record);
        fileImportTransactionCandidatePreparationService.syncExistingFileImportCandidateForValidRecord(record);
        response.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(root));
        return response;
    }

    private TransactionIngestion resolveAccessibleFileIngestion(Long transactionIngestionId, String userLogin) {
        if (transactionIngestionId == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion ingestion = transactionIngestionRepository
            .findOneWithToOneRelationshipsByIdAndAccountUserLogin(transactionIngestionId, userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        if (ingestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only FILE ingestions can reevaluate descriptions");
        }
        if (ingestion.getStatus() != IngestionStatus.READY && ingestion.getStatus() != IngestionStatus.PARTIALLY_READY) {
            throw new IllegalArgumentException("Only READY or PARTIALLY_READY file ingestions can reevaluate descriptions");
        }
        return ingestion;
    }

    private List<IngestionRecord> selectRecords(TransactionIngestion ingestion, CsvIngestionDescriptionReevaluationRequestDTO request) {
        List<IngestionRecord> records = ingestionRecordRepository.findAllByTransactionIngestionIdOrderByRecordIndexAsc(ingestion.getId());
        List<Long> requestedRecordIds = request == null ? null : request.getRecordIds();
        if (requestedRecordIds == null) {
            return records;
        }

        Set<Long> requestedIds = new LinkedHashSet<>();
        for (Long recordId : requestedRecordIds) {
            if (recordId == null) {
                throw new IllegalArgumentException("Ingestion record id is required");
            }
            if (!requestedIds.add(recordId)) {
                throw new IllegalArgumentException("Ingestion record ids must be unique");
            }
        }
        var recordsById = records.stream().collect(Collectors.toMap(IngestionRecord::getId, record -> record));
        return requestedIds
            .stream()
            .map(recordId -> {
                IngestionRecord record = recordsById.get(recordId);
                if (record == null) {
                    throw new IllegalArgumentException("Ingestion record does not belong to this ingestion");
                }
                return record;
            })
            .toList();
    }

    private CsvIngestionDescriptionReevaluationRowDTO baseRow(IngestionRecord record) {
        CsvIngestionDescriptionReevaluationRowDTO response = new CsvIngestionDescriptionReevaluationRowDTO();
        response.setIngestionRecordId(record.getId());
        response.setRecordIndex(record.getRecordIndex());
        return response;
    }

    private boolean isUserEditedDescription(ObjectNode root) {
        return "USER_EDIT".equals(textOrNull(root.path("review").path("description"), "source"));
    }

    private ObjectNode rawData(IngestionRecord record) {
        try {
            JsonNode parsed = record.getRawData() == null ? objectMapper.createObjectNode() : objectMapper.readTree(record.getRawData());
            if (parsed instanceof ObjectNode objectNode) {
                ensureObject(objectNode, "raw");
                ensureObject(objectNode, "normalized");
                return objectNode;
            }
        } catch (JsonProcessingException ignored) {
            // The row remains reviewable only if its persisted rawData is structurally usable.
        }
        throw new IllegalArgumentException("Ingestion record rawData could not be parsed");
    }

    private ObjectNode ensureObject(ObjectNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        if (value instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode objectNode = objectMapper.createObjectNode();
        parent.set(fieldName, objectNode);
        return objectNode;
    }

    private void removeDescriptionReview(ObjectNode root) {
        JsonNode reviewValue = root.path("review");
        if (!(reviewValue instanceof ObjectNode review)) {
            return;
        }
        review.remove("description");
        if (review.isEmpty()) {
            root.remove("review");
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String writeRawData(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize ingestion record rawData", e);
        }
    }
}
