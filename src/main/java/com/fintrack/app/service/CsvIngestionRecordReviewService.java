package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvRawRow;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvRowResult;
import com.fintrack.app.service.csv.CsvIngestionValidationMessage;
import com.fintrack.app.service.dto.CsvIngestionPreviewCountsDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewRowDTO;
import com.fintrack.app.service.dto.CsvIngestionRecordReviewRequestDTO;
import com.fintrack.app.service.dto.CsvIngestionRecordReviewResponseDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CsvIngestionRecordReviewService {

    private static final TypeReference<List<CsvIngestionValidationMessage>> VALIDATION_MESSAGE_LIST = new TypeReference<>() {};

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final CurrentUserService currentUserService;
    private final CanonicalCsvIngestionParser parser;
    private final ObjectMapper objectMapper;

    public CsvIngestionRecordReviewService(
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        CurrentUserService currentUserService,
        CanonicalCsvIngestionParser parser,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.currentUserService = currentUserService;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public CsvIngestionRecordReviewResponseDTO disable(Long ingestionId, Long recordId) {
        IngestionRecord record = resolveAccessibleRecord(ingestionId, recordId);
        if (record.getStatus() != IngestionRecordStatus.VALID && record.getStatus() != IngestionRecordStatus.REJECTED) {
            throw new IllegalArgumentException("Only valid or rejected preview rows can be disabled");
        }
        rejectLinkedFinancialTransaction(record);

        record.setStatus(IngestionRecordStatus.DISABLED);
        record.setErrorCode(null);
        record.setErrorMessage(null);
        record.setFinancialTransaction(null);
        record.setRawData(updateRawDataStatus(record, null, false));
        ingestionRecordRepository.save(record);

        return response(record);
    }

    public CsvIngestionRecordReviewResponseDTO enable(Long ingestionId, Long recordId) {
        IngestionRecord record = resolveAccessibleRecord(ingestionId, recordId);
        if (record.getStatus() != IngestionRecordStatus.DISABLED) {
            throw new IllegalArgumentException("Only disabled preview rows can be enabled");
        }
        rejectLinkedFinancialTransaction(record);

        CsvRawRow rawRow = rawRowFromNormalized(record);
        CsvRowResult result = parser.validateReviewRow(
            record.getRecordIndex(),
            rawRow,
            record.getTransactionIngestion().getAccount().getCurrency()
        );
        applyValidationResult(record, result);
        ingestionRecordRepository.save(record);

        return response(record);
    }

    public CsvIngestionRecordReviewResponseDTO edit(Long ingestionId, Long recordId, CsvIngestionRecordReviewRequestDTO request) {
        IngestionRecord record = resolveAccessibleRecord(ingestionId, recordId);
        if (record.getStatus() == IngestionRecordStatus.DISABLED) {
            throw new IllegalArgumentException("Disabled rows must be enabled before editing.");
        }
        if (!isEditableStatus(record.getStatus())) {
            throw new IllegalArgumentException("Only valid or rejected preview rows can be edited");
        }
        rejectLinkedFinancialTransaction(record);

        CsvRawRow rawRow = new CsvRawRow(
            request == null ? null : request.getTransactionDate(),
            request == null ? null : request.getPostingDate(),
            request == null ? null : request.getDescription(),
            request == null ? null : request.getSignedAmount(),
            request == null ? null : request.getCurrency(),
            request == null ? null : request.getExternalReference(),
            request == null ? null : request.getNotes()
        );
        CsvRowResult result = parser.validateReviewRow(
            record.getRecordIndex(),
            rawRow,
            record.getTransactionIngestion().getAccount().getCurrency()
        );
        applyValidationResult(record, result, true);
        ingestionRecordRepository.save(record);

        return response(record);
    }

    private IngestionRecord resolveAccessibleRecord(Long ingestionId, Long recordId) {
        if (ingestionId == null || recordId == null) {
            throw new IllegalArgumentException("Transaction ingestion and record are required");
        }
        IngestionRecord record = ingestionRecordRepository
            .findOneWithToOneRelationshipsByIdAndTransactionIngestionAccountUserLogin(recordId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Ingestion record is not accessible"));
        if (record.getTransactionIngestion() == null || !ingestionId.equals(record.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Ingestion record does not belong to the transaction ingestion");
        }
        if (record.getTransactionIngestion().getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only file ingestion records can be reviewed");
        }
        return record;
    }

    private void rejectLinkedFinancialTransaction(IngestionRecord record) {
        if (record.getFinancialTransaction() != null || record.getStatus() == IngestionRecordStatus.IMPORTED) {
            throw new IllegalArgumentException("Imported ingestion records cannot be reviewed");
        }
    }

    private void applyValidationResult(IngestionRecord record, CsvRowResult result) {
        applyValidationResult(record, result, false);
    }

    private void applyValidationResult(IngestionRecord record, CsvRowResult result, boolean edited) {
        CsvIngestionValidationMessage firstError = result.getErrors().isEmpty() ? null : result.getErrors().get(0);
        record.setStatus(result.isValid() ? IngestionRecordStatus.VALID : IngestionRecordStatus.REJECTED);
        record.setErrorCode(firstError == null ? null : firstError.getCode());
        record.setErrorMessage(firstError == null ? null : firstError.getMessage());
        record.setExternalRecordId(result.getNormalized().getExternalReference());
        record.setFinancialTransaction(null);
        record.setRawData(updateRawDataStatus(record, result, edited));
    }

    private CsvRawRow rawRowFromNormalized(IngestionRecord record) {
        JsonNode normalized = rawData(record).path("normalized");
        return new CsvRawRow(
            textOrNull(normalized, "transactionDate"),
            textOrNull(normalized, "postingDate"),
            textOrNull(normalized, "description"),
            textOrNull(normalized, "signedAmount"),
            textOrNull(normalized, "currency"),
            textOrNull(normalized, "externalReference"),
            textOrNull(normalized, "notes")
        );
    }

    private boolean isEditableStatus(IngestionRecordStatus status) {
        return status == IngestionRecordStatus.VALID || status == IngestionRecordStatus.REJECTED;
    }

    private String updateRawDataStatus(IngestionRecord record, CsvRowResult result, boolean edited) {
        ObjectNode root = rawData(record);
        if (result != null) {
            root.set("normalized", objectMapper.valueToTree(normalizedMap(result)));
            root.set("errors", objectMapper.valueToTree(result.getErrors()));
            root.set("warnings", objectMapper.valueToTree(result.getWarnings()));
        }
        if (edited) {
            ObjectNode review = root.path("review").isObject() ? (ObjectNode) root.path("review") : objectMapper.createObjectNode();
            review.put("edited", true);
            review.put("editedAt", Instant.now().toString());
            review.put("editedBy", currentUserService.getCurrentUserLogin());
            root.set("review", review);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize ingestion record review data", e);
        }
    }

    private ObjectNode rawData(IngestionRecord record) {
        try {
            JsonNode parsed = record.getRawData() == null ? objectMapper.createObjectNode() : objectMapper.readTree(record.getRawData());
            if (parsed instanceof ObjectNode objectNode) {
                ensureObjectField(objectNode, "raw");
                ensureObjectField(objectNode, "normalized");
                ensureArrayField(objectNode, "errors");
                ensureArrayField(objectNode, "warnings");
                return objectNode;
            }
            return baseRawData();
        } catch (JsonProcessingException e) {
            return baseRawData();
        }
    }

    private ObjectNode baseRawData() {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("raw", objectMapper.createObjectNode());
        root.set("normalized", objectMapper.createObjectNode());
        root.set("errors", objectMapper.createArrayNode());
        root.set("warnings", objectMapper.createArrayNode());
        return root;
    }

    private void ensureObjectField(ObjectNode root, String fieldName) {
        if (!root.path(fieldName).isObject()) {
            root.set(fieldName, objectMapper.createObjectNode());
        }
    }

    private void ensureArrayField(ObjectNode root, String fieldName) {
        if (!root.path(fieldName).isArray()) {
            root.set(fieldName, objectMapper.createArrayNode());
        }
    }

    private ObjectNode normalizedMap(CsvRowResult result) {
        ObjectNode values = objectMapper.createObjectNode();
        values.put(
            "transactionDate",
            result.getNormalized().getTransactionDate() == null ? null : result.getNormalized().getTransactionDate().toString()
        );
        values.put(
            "postingDate",
            result.getNormalized().getPostingDate() == null ? null : result.getNormalized().getPostingDate().toString()
        );
        values.put("description", result.getNormalized().getDescription());
        values.put("signedAmount", result.getNormalized().getSignedAmount());
        values.put("amount", result.getNormalized().getAmount());
        values.put("flow", result.getNormalized().getFlow() == null ? null : result.getNormalized().getFlow().name());
        values.put("currency", result.getNormalized().getCurrency() == null ? null : result.getNormalized().getCurrency().name());
        values.put("externalReference", result.getNormalized().getExternalReference());
        values.put("notes", result.getNormalized().getNotes());
        return values;
    }

    private CsvIngestionRecordReviewResponseDTO response(IngestionRecord record) {
        TransactionIngestion ingestion = record.getTransactionIngestion();
        CsvIngestionPreviewCountsDTO counts = recalculateCounts(ingestion);
        CsvIngestionRecordReviewResponseDTO response = new CsvIngestionRecordReviewResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setCounts(counts);
        response.setRow(toRowDto(record));
        return response;
    }

    private CsvIngestionPreviewCountsDTO recalculateCounts(TransactionIngestion ingestion) {
        List<IngestionRecord> records = ingestionRecordRepository.findAllByTransactionIngestionIdOrderByRecordIndexAsc(ingestion.getId());
        int imported = 0;
        int skipped = 0;
        int rejected = 0;
        int valid = 0;
        for (IngestionRecord record : records) {
            if (record.getStatus() == IngestionRecordStatus.IMPORTED) {
                imported++;
            } else if (
                record.getStatus() == IngestionRecordStatus.DISABLED || record.getStatus() == IngestionRecordStatus.SKIPPED_DUPLICATE
            ) {
                skipped++;
            } else if (record.getStatus() == IngestionRecordStatus.REJECTED || record.getStatus() == IngestionRecordStatus.FAILED) {
                rejected++;
            } else if (record.getStatus() == IngestionRecordStatus.VALID) {
                valid++;
            }
        }

        ingestion.setRecordsReceived(records.size());
        ingestion.setRecordsCreated(imported);
        ingestion.setRecordsSkipped(skipped);
        ingestion.setRecordsRejected(rejected);
        ingestion.setStatus(rejected == 0 ? IngestionStatus.COMPLETED : IngestionStatus.PARTIALLY_COMPLETED);
        transactionIngestionRepository.save(ingestion);

        CsvIngestionPreviewCountsDTO counts = new CsvIngestionPreviewCountsDTO();
        counts.setRecordsReceived(records.size());
        counts.setRecordsCreated(imported);
        counts.setRecordsSkipped(skipped);
        counts.setRecordsRejected(rejected);
        counts.setValidRows(valid);
        counts.setInvalidRows(rejected);
        return counts;
    }

    private CsvIngestionPreviewRowDTO toRowDto(IngestionRecord record) {
        ObjectNode rawData = rawData(record);
        JsonNode normalized = rawData.path("normalized");
        CsvIngestionPreviewRowDTO dto = new CsvIngestionPreviewRowDTO();
        dto.setIngestionRecordId(record.getId());
        dto.setRecordIndex(record.getRecordIndex());
        dto.setStatus(record.getStatus());
        dto.setTransactionDate(parseLocalDate(normalized, "transactionDate"));
        dto.setPostingDate(parseLocalDate(normalized, "postingDate"));
        dto.setDescription(textOrNull(normalized, "description"));
        dto.setSignedAmount(textOrNull(normalized, "signedAmount"));
        dto.setAmount(textOrNull(normalized, "amount"));
        dto.setFlow(parseFlow(normalized));
        dto.setCurrency(parseCurrency(normalized));
        dto.setExternalReference(textOrNull(normalized, "externalReference"));
        dto.setNotes(textOrNull(normalized, "notes"));
        dto.setErrorCode(record.getErrorCode());
        dto.setErrorMessage(record.getErrorMessage());
        dto.setWarnings(messages(rawData.path("warnings")));
        return dto;
    }

    private java.time.LocalDate parseLocalDate(JsonNode node, String fieldName) {
        String value = textOrNull(node, fieldName);
        return value == null ? null : java.time.LocalDate.parse(value);
    }

    private com.fintrack.app.domain.enumeration.TransactionFlow parseFlow(JsonNode normalized) {
        String value = textOrNull(normalized, "flow");
        return value == null ? null : com.fintrack.app.domain.enumeration.TransactionFlow.valueOf(value);
    }

    private com.fintrack.app.domain.enumeration.CurrencyCode parseCurrency(JsonNode normalized) {
        String value = textOrNull(normalized, "currency");
        return value == null ? null : com.fintrack.app.domain.enumeration.CurrencyCode.valueOf(value);
    }

    private List<CsvIngestionValidationMessage> messages(JsonNode node) {
        if (!(node instanceof ArrayNode)) {
            return new ArrayList<>();
        }
        return objectMapper.convertValue(node, VALIDATION_MESSAGE_LIST);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
