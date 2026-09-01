package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CsvIngestionClassificationCategoryDTO;
import com.fintrack.app.service.dto.CsvIngestionClassificationPreviewResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionClassificationPreviewRowDTO;
import com.fintrack.app.service.dto.CsvIngestionClassificationTagDTO;
import com.fintrack.app.service.rules.TagSuggestion;
import com.fintrack.app.service.rules.TransactionRuleEvaluationInput;
import com.fintrack.app.service.rules.TransactionRuleEvaluationResult;
import com.fintrack.app.service.rules.TransactionRuleEvaluationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CsvIngestionClassificationPreviewService {

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final FileIngestionRepository fileIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final TransactionRuleEvaluationService transactionRuleEvaluationService;
    private final ObjectMapper objectMapper;

    public CsvIngestionClassificationPreviewService(
        TransactionIngestionRepository transactionIngestionRepository,
        FileIngestionRepository fileIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        TransactionRuleEvaluationService transactionRuleEvaluationService,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.fileIngestionRepository = fileIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.transactionRuleEvaluationService = transactionRuleEvaluationService;
        this.objectMapper = objectMapper;
    }

    public CsvIngestionClassificationPreviewResponseDTO preview(Long transactionIngestionId) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleReadyFileIngestion(transactionIngestionId, userLogin);

        CsvIngestionClassificationPreviewResponseDTO response = new CsvIngestionClassificationPreviewResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setRows(
            ingestionRecordRepository
                .findAllByTransactionIngestionIdOrderByRecordIndexAsc(ingestion.getId())
                .stream()
                .filter(record -> record.getStatus() == IngestionRecordStatus.VALID)
                .map(record -> toPreviewRow(ingestion, record, userLogin))
                .toList()
        );
        return response;
    }

    private TransactionIngestion resolveAccessibleReadyFileIngestion(Long transactionIngestionId, String userLogin) {
        if (transactionIngestionId == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion ingestion = transactionIngestionRepository
            .findOneWithToOneRelationshipsByIdAndAccountUserLogin(transactionIngestionId, userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        if (ingestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only file ingestions can be previewed");
        }
        if (!fileIngestionRepository.existsByTransactionIngestionId(transactionIngestionId)) {
            throw new IllegalArgumentException("File ingestion metadata was not found");
        }
        if (ingestion.getStatus() != IngestionStatus.READY) {
            throw new IllegalArgumentException("Only ready file ingestions can be classification-previewed");
        }
        return ingestion;
    }

    private CsvIngestionClassificationPreviewRowDTO toPreviewRow(TransactionIngestion ingestion, IngestionRecord record, String userLogin) {
        JsonNode normalized = rawData(record).path("normalized");
        TransactionRuleEvaluationResult evaluation = transactionRuleEvaluationService.evaluate(
            new TransactionRuleEvaluationInput(
                userLogin,
                requiredText(normalized, "description"),
                requiredAmount(normalized, "amount"),
                requiredFlow(normalized, "flow"),
                optionalText(normalized, "externalReference"),
                TransactionOrigin.FILE_IMPORT,
                requiredLocalDate(normalized, "transactionDate"),
                optionalLocalDate(normalized, "postingDate"),
                ingestion.getAccount().getId(),
                null,
                null,
                Set.of(),
                Map.of()
            )
        );

        CsvIngestionClassificationPreviewRowDTO row = new CsvIngestionClassificationPreviewRowDTO();
        row.setRecordId(record.getId());
        row.setRecordIndex(record.getRecordIndex());
        row.setDescription(requiredText(normalized, "description"));
        row.setTransactionDate(requiredLocalDate(normalized, "transactionDate"));
        row.setSignedAmount(optionalText(normalized, "signedAmount"));
        row.setAmount(optionalText(normalized, "amount"));
        row.setFlow(optionalFlow(normalized, "flow"));
        row.setSuggestedCategory(toCategoryDto(evaluation, userLogin));
        row.setSuggestedTags(
            evaluation
                .suggestedTags()
                .stream()
                .filter(tag -> !tag.alreadyPresent() && !tag.duplicateOfEarlierSuggestion())
                .map(tag -> toTagDto(tag, userLogin))
                .toList()
        );
        row.setMatchedRules(evaluation.matchedRules());
        row.setConflicts(evaluation.conflicts());
        row.setSkippedOutputs(evaluation.skippedOutputs());
        return row;
    }

    private CsvIngestionClassificationCategoryDTO toCategoryDto(TransactionRuleEvaluationResult evaluation, String userLogin) {
        if (evaluation.suggestedCategory() == null || evaluation.suggestedCategory().conflictsWithCurrentValue()) {
            return null;
        }
        Category category = categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(evaluation.suggestedCategory().categoryId(), userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Suggested category is not accessible"));
        return new CsvIngestionClassificationCategoryDTO(category.getId(), category.getName(), category.getCategoryType());
    }

    private CsvIngestionClassificationTagDTO toTagDto(TagSuggestion suggestion, String userLogin) {
        Tag tag = tagRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(suggestion.tagId(), userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Suggested tag is not accessible"));
        return new CsvIngestionClassificationTagDTO(tag.getId(), tag.getName());
    }

    private JsonNode rawData(IngestionRecord record) {
        try {
            return record.getRawData() == null ? objectMapper.createObjectNode() : objectMapper.readTree(record.getRawData());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not read ingestion record data", e);
        }
    }

    private LocalDate requiredLocalDate(JsonNode node, String fieldName) {
        String value = requiredText(node, fieldName);
        return LocalDate.parse(value);
    }

    private LocalDate optionalLocalDate(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : LocalDate.parse(value);
    }

    private BigDecimal requiredAmount(JsonNode node, String fieldName) {
        return new BigDecimal(requiredText(node, fieldName));
    }

    private TransactionFlow requiredFlow(JsonNode node, String fieldName) {
        return TransactionFlow.valueOf(requiredText(node, fieldName));
    }

    private TransactionFlow optionalFlow(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : TransactionFlow.valueOf(value);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null) {
            throw new IllegalArgumentException("CSV row normalized " + fieldName + " is required");
        }
        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
