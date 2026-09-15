package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.CsvIngestionReadinessService.CsvIngestionReadinessSnapshot;
import com.fintrack.app.service.dto.CsvIngestionConfirmImportResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionDescriptionReviewDTO;
import com.fintrack.app.service.dto.CsvIngestionWorkflowCountsDTO;
import com.fintrack.app.service.dto.CsvIngestionWorkflowRecordDTO;
import com.fintrack.app.service.mapper.TransactionCandidateWorkflowSummaryMapper;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CsvIngestionConfirmImportService {

    private static final String NOT_READY_MESSAGE =
        "Cannot confirm import because ingestion is not ready. Fix or disable rejected rows and ensure at least one valid row exists.";

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final FileIngestionRepository fileIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final TransactionCandidateRepository transactionCandidateRepository;
    private final CurrentUserService currentUserService;
    private final CsvIngestionReadinessService csvIngestionReadinessService;
    private final TransactionCandidateWorkflowSummaryMapper transactionCandidateWorkflowSummaryMapper;
    private final ObjectMapper objectMapper;

    public CsvIngestionConfirmImportService(
        TransactionIngestionRepository transactionIngestionRepository,
        FileIngestionRepository fileIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        FinancialTransactionRepository financialTransactionRepository,
        TransactionCandidateRepository transactionCandidateRepository,
        CurrentUserService currentUserService,
        CsvIngestionReadinessService csvIngestionReadinessService,
        TransactionCandidateWorkflowSummaryMapper transactionCandidateWorkflowSummaryMapper,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.fileIngestionRepository = fileIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.currentUserService = currentUserService;
        this.csvIngestionReadinessService = csvIngestionReadinessService;
        this.transactionCandidateWorkflowSummaryMapper = transactionCandidateWorkflowSummaryMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = IngestionNotReadyException.class)
    public CsvIngestionConfirmImportResponseDTO confirm(Long transactionIngestionId) {
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId);
        List<IngestionRecord> records = records(ingestion);
        validateNoCorruptFinancialTransactionLinks(records);

        if (ingestion.getStatus() == IngestionStatus.COMPLETED) {
            return response(ingestion, records, 0);
        }
        validateNoCorruptPreCompletedCandidates(ingestion, records);
        if (!isConfirmPrecheckStatusAllowed(ingestion.getStatus())) {
            throw new IllegalArgumentException("Only ready file ingestions can be confirmed");
        }

        CsvIngestionReadinessSnapshot readiness = csvIngestionReadinessService.applyReadiness(ingestion, records);
        transactionIngestionRepository.save(ingestion);
        if (readiness.status() != IngestionStatus.READY) {
            throw new IngestionNotReadyException(NOT_READY_MESSAGE);
        }

        Map<Long, TransactionCandidate> candidatesByRecordId = validateAndResolveCandidates(ingestion, records);

        int createdNow = 0;
        Instant now = Instant.now();
        for (IngestionRecord record : records) {
            if (record.getStatus() == IngestionRecordStatus.VALID) {
                TransactionCandidate candidate = candidatesByRecordId.get(record.getId());
                FinancialTransaction financialTransaction = financialTransactionRepository.save(
                    toFinancialTransaction(ingestion, candidate, now)
                );
                candidate.setFinancialTransaction(financialTransaction);
                candidate.setStatus(TransactionCandidateStatus.POSTED);
                candidate.setPostedAt(now);
                candidate.setUpdatedAt(now);
                transactionCandidateRepository.save(candidate);
                record.setStatus(IngestionRecordStatus.IMPORTED);
                record.setFinancialTransaction(financialTransaction);
                record.setErrorCode(null);
                record.setErrorMessage(null);
                ingestionRecordRepository.save(record);
                createdNow++;
            }
        }

        CsvIngestionWorkflowCountsDTO counts = csvIngestionReadinessService.snapshot(records).counts();
        csvIngestionReadinessService.applyCounts(ingestion, counts);
        ingestion.setStatus(IngestionStatus.COMPLETED);
        ingestion.setCompletedAt(now);
        transactionIngestionRepository.save(ingestion);

        return response(ingestion, records, createdNow);
    }

    private Map<Long, TransactionCandidate> validateAndResolveCandidates(TransactionIngestion ingestion, List<IngestionRecord> records) {
        String userLogin = currentUserService.getCurrentUserLogin();
        List<IngestionRecord> validRecords = records.stream().filter(record -> record.getStatus() == IngestionRecordStatus.VALID).toList();
        Map<Long, TransactionCandidate> candidatesByRecordId = new HashMap<>();
        for (TransactionCandidate candidate : transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
            ingestion.getId(),
            userLogin
        )) {
            Long recordId = candidate.getIngestionRecord() == null ? null : candidate.getIngestionRecord().getId();
            if (recordId != null && candidatesByRecordId.put(recordId, candidate) != null) {
                throw new IllegalArgumentException("Exactly one transaction candidate is required for each valid ingestion record");
            }
        }
        for (IngestionRecord record : validRecords) {
            TransactionCandidate candidate = candidatesByRecordId.get(record.getId());
            if (candidate == null) {
                throw new IllegalArgumentException("Transaction candidate is required for each valid ingestion record");
            }
            validateCandidateForConfirm(ingestion, record, candidate, userLogin);
        }
        return candidatesByRecordId;
    }

    private void validateCandidateForConfirm(
        TransactionIngestion ingestion,
        IngestionRecord record,
        TransactionCandidate candidate,
        String userLogin
    ) {
        if (candidate.getSource() != TransactionCandidateSource.FILE_IMPORT) {
            throw new IllegalArgumentException("Only FILE_IMPORT transaction candidates can be confirmed from file ingestion");
        }
        if (candidate.getStatus() == TransactionCandidateStatus.POSTED || candidate.getFinancialTransaction() != null) {
            throw new IllegalArgumentException("Transaction candidate is already posted before ingestion completed");
        }
        if (candidate.getStatus() != TransactionCandidateStatus.READY_TO_POST) {
            throw new IllegalArgumentException("Transaction candidate is not ready to post");
        }
        if (candidate.getValidationStatus() != TransactionCandidateValidationStatus.VALID) {
            throw new IllegalArgumentException("Transaction candidate validation must be valid before confirm");
        }
        if (!isConfirmedClassificationStatus(candidate.getClassificationReviewStatus())) {
            throw new IllegalArgumentException("Transaction candidate classification must be reviewed before confirm");
        }
        if (
            candidate.getTransactionIngestion() == null ||
            !Objects.equals(candidate.getTransactionIngestion().getId(), ingestion.getId()) ||
            candidate.getIngestionRecord() == null ||
            !Objects.equals(candidate.getIngestionRecord().getId(), record.getId())
        ) {
            throw new IllegalArgumentException("Transaction candidate does not belong to the valid ingestion record");
        }
        if (
            candidate.getUser() == null ||
            candidate.getUser().getLogin() == null ||
            !Objects.equals(candidate.getUser().getLogin(), userLogin)
        ) {
            throw new IllegalArgumentException("Transaction candidate is not accessible");
        }
        if (
            candidate.getAccount() == null ||
            ingestion.getAccount() == null ||
            !Objects.equals(candidate.getAccount().getId(), ingestion.getAccount().getId())
        ) {
            throw new IllegalArgumentException("Transaction candidate account must match the ingestion account");
        }
        validateCandidateFields(candidate);
        validateCandidateOutputs(candidate, userLogin);
    }

    private boolean isConfirmedClassificationStatus(TransactionCandidateClassificationReviewStatus status) {
        return (
            status == TransactionCandidateClassificationReviewStatus.SUGGESTED ||
            status == TransactionCandidateClassificationReviewStatus.USER_SELECTED ||
            status == TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE
        );
    }

    private void validateCandidateFields(TransactionCandidate candidate) {
        if (candidate.getAccount().getCurrency() == null || candidate.getCurrencySnapshot() == null) {
            throw new IllegalArgumentException("Transaction candidate currency is required");
        }
        if (candidate.getCurrencySnapshot() != candidate.getAccount().getCurrency()) {
            throw new IllegalArgumentException("Transaction candidate currency must match the selected account currency");
        }
        if (candidate.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction candidate transaction date is required");
        }
        if (candidate.getDescription() == null || candidate.getDescription().isBlank()) {
            throw new IllegalArgumentException("Transaction candidate description is required");
        }
        if (candidate.getAmount() == null || candidate.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Transaction candidate amount must be greater than zero");
        }
        if (candidate.getFlow() == null) {
            throw new IllegalArgumentException("Transaction candidate flow is required");
        }
        if (candidate.getSignedAmount() == null || candidate.getSignedAmount().signum() == 0) {
            throw new IllegalArgumentException("Transaction candidate signed amount is required");
        }
        if (candidate.getSignedAmount().abs().compareTo(candidate.getAmount()) != 0) {
            throw new IllegalArgumentException("Transaction candidate amount must match signed amount");
        }
        if (candidate.getSignedAmount().signum() > 0 && candidate.getFlow() != TransactionFlow.IN) {
            throw new IllegalArgumentException("Transaction candidate flow must match signed amount");
        }
        if (candidate.getSignedAmount().signum() < 0 && candidate.getFlow() != TransactionFlow.OUT) {
            throw new IllegalArgumentException("Transaction candidate flow must match signed amount");
        }
    }

    private void validateCandidateOutputs(TransactionCandidate candidate, String userLogin) {
        Category category = candidate.getCategory();
        if (category != null) {
            if (category.getUser() == null || !Objects.equals(category.getUser().getLogin(), userLogin)) {
                throw new IllegalArgumentException("Transaction candidate category is not accessible");
            }
            validateCategoryCompatibility(category, candidate.getFlow());
        }
        if (candidate.getTags() != null) {
            for (Tag tag : candidate.getTags()) {
                if (tag.getUser() == null || !Objects.equals(tag.getUser().getLogin(), userLogin)) {
                    throw new IllegalArgumentException("Transaction candidate tag is not accessible");
                }
            }
        }
    }

    private void validateCategoryCompatibility(Category category, TransactionFlow flow) {
        if (category == null || flow == null) {
            return;
        }
        if (!CategoryFlowCompatibilityValidator.isCompatible(category.getCategoryType(), flow)) {
            throw new IllegalArgumentException("Category type is not compatible with transaction flow");
        }
    }

    private TransactionIngestion resolveAccessibleFileIngestion(Long transactionIngestionId) {
        if (transactionIngestionId == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion ingestion = transactionIngestionRepository
            .findOneWithToOneRelationshipsByIdAndAccountUserLogin(transactionIngestionId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        if (ingestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only file ingestions can be confirmed");
        }
        if (!fileIngestionRepository.existsByTransactionIngestionId(transactionIngestionId)) {
            throw new IllegalArgumentException("File ingestion metadata was not found");
        }
        return ingestion;
    }

    private List<IngestionRecord> records(TransactionIngestion ingestion) {
        List<IngestionRecord> records = ingestionRecordRepository.findAllByTransactionIngestionIdOrderByRecordIndexAsc(ingestion.getId());
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Cannot confirm import because ingestion has no records");
        }
        return records;
    }

    private boolean isConfirmPrecheckStatusAllowed(IngestionStatus status) {
        return status == IngestionStatus.READY || status == IngestionStatus.PARTIALLY_READY;
    }

    private void validateNoCorruptFinancialTransactionLinks(List<IngestionRecord> records) {
        for (IngestionRecord record : records) {
            if (record.getStatus() == IngestionRecordStatus.IMPORTED && record.getFinancialTransaction() == null) {
                throw new IllegalArgumentException("Imported ingestion record is missing its financial transaction");
            }
            if (record.getStatus() == IngestionRecordStatus.VALID && record.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Valid ingestion record is already linked to a financial transaction");
            }
        }
    }

    private void validateNoCorruptPreCompletedCandidates(TransactionIngestion ingestion, List<IngestionRecord> records) {
        String userLogin = currentUserService.getCurrentUserLogin();
        Map<Long, IngestionRecord> recordsById = new HashMap<>();
        records.forEach(record -> recordsById.put(record.getId(), record));

        for (TransactionCandidate candidate : transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
            ingestion.getId(),
            userLogin
        )) {
            if (candidate.getSource() != TransactionCandidateSource.FILE_IMPORT) {
                throw new IllegalArgumentException("Only FILE_IMPORT transaction candidates can be confirmed from file ingestion");
            }
            IngestionRecord record = candidate.getIngestionRecord();
            if (record == null || record.getId() == null) {
                throw new IllegalArgumentException("Transaction candidate is missing its ingestion record");
            }
            if (
                record.getTransactionIngestion() == null ||
                !Objects.equals(record.getTransactionIngestion().getId(), ingestion.getId()) ||
                !recordsById.containsKey(record.getId())
            ) {
                throw new IllegalArgumentException("Transaction candidate does not belong to this ingestion");
            }

            IngestionRecord persistedRecord = recordsById.get(record.getId());
            if (candidate.getStatus() == TransactionCandidateStatus.POSTED) {
                validatePostedCandidateMatchesImportedRecord(candidate, persistedRecord);
            } else {
                validateUnpostedCandidateMatchesPreImportRecord(candidate, persistedRecord);
            }
        }
    }

    private void validatePostedCandidateMatchesImportedRecord(TransactionCandidate candidate, IngestionRecord record) {
        if (record.getStatus() != IngestionRecordStatus.IMPORTED) {
            throw new IllegalArgumentException("Posted transaction candidate requires an imported ingestion record");
        }
        if (candidate.getFinancialTransaction() == null) {
            throw new IllegalArgumentException("Posted transaction candidate is missing its financial transaction");
        }
        if (
            record.getFinancialTransaction() == null ||
            !Objects.equals(candidate.getFinancialTransaction().getId(), record.getFinancialTransaction().getId())
        ) {
            throw new IllegalArgumentException("Posted transaction candidate financial transaction must match ingestion record");
        }
    }

    private void validateUnpostedCandidateMatchesPreImportRecord(TransactionCandidate candidate, IngestionRecord record) {
        if (record.getStatus() == IngestionRecordStatus.IMPORTED) {
            throw new IllegalArgumentException("Imported ingestion record requires a posted transaction candidate");
        }
        if (record.getStatus() != IngestionRecordStatus.VALID) {
            throw new IllegalArgumentException("Transaction candidate is linked to a non-valid ingestion record");
        }
        if (candidate.getFinancialTransaction() != null) {
            throw new IllegalArgumentException("Unposted transaction candidate cannot be linked to a financial transaction");
        }
    }

    private FinancialTransaction toFinancialTransaction(TransactionIngestion ingestion, TransactionCandidate candidate, Instant now) {
        FinancialTransaction financialTransaction = new FinancialTransaction()
            .transactionDate(candidate.getTransactionDate())
            .postingDate(candidate.getPostingDate())
            .description(candidate.getDescription())
            .amount(candidate.getAmount())
            .flow(candidate.getFlow())
            .origin(TransactionOrigin.FILE_IMPORT)
            .externalReference(candidate.getExternalReference())
            .notes(candidate.getNotes())
            .createdAt(now)
            .updatedAt(now)
            .account(candidate.getAccount())
            .category(candidate.getCategory())
            .financialSubscription(null)
            .transactionIngestion(ingestion);

        if (candidate.getTags() != null) {
            candidate.getTags().forEach(financialTransaction::addTags);
        }

        return financialTransaction;
    }

    private CsvIngestionConfirmImportResponseDTO response(TransactionIngestion ingestion, List<IngestionRecord> records, int createdNow) {
        CsvIngestionWorkflowCountsDTO counts = csvIngestionReadinessService.snapshot(records).counts();
        CsvIngestionConfirmImportResponseDTO response = new CsvIngestionConfirmImportResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setStatus(ingestion.getStatus());
        response.setCreatedNow(createdNow);
        response.setAlreadyImported(count(records, IngestionRecordStatus.IMPORTED) - createdNow);
        response.setSkipped(count(records, IngestionRecordStatus.DISABLED) + count(records, IngestionRecordStatus.SKIPPED_DUPLICATE));
        response.setRejected(count(records, IngestionRecordStatus.REJECTED));
        response.setFailed(count(records, IngestionRecordStatus.FAILED));
        response.setCounts(counts);
        Map<Long, TransactionCandidate> candidatesByRecordId = currentUserCandidateByRecordId(ingestion);
        response.setRows(records.stream().map(record -> toRowDto(record, candidatesByRecordId.get(record.getId()))).toList());
        return response;
    }

    private Map<Long, TransactionCandidate> currentUserCandidateByRecordId(TransactionIngestion ingestion) {
        Map<Long, TransactionCandidate> candidatesByRecordId = new HashMap<>();
        for (TransactionCandidate candidate : transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
            ingestion.getId(),
            currentUserService.getCurrentUserLogin()
        )) {
            if (candidate.getIngestionRecord() != null && candidate.getIngestionRecord().getId() != null) {
                candidatesByRecordId.put(candidate.getIngestionRecord().getId(), candidate);
            }
        }
        return candidatesByRecordId;
    }

    private int count(List<IngestionRecord> records, IngestionRecordStatus status) {
        return (int) records.stream().filter(record -> record.getStatus() == status).count();
    }

    private CsvIngestionWorkflowRecordDTO toRowDto(IngestionRecord record, TransactionCandidate candidate) {
        JsonNode rawData = rawData(record);
        JsonNode normalized = rawData.path("normalized");
        CsvIngestionWorkflowRecordDTO dto = new CsvIngestionWorkflowRecordDTO();
        dto.setIngestionRecordId(record.getId());
        dto.setRecordIndex(record.getRecordIndex());
        dto.setStatus(record.getStatus());
        dto.setFinancialTransactionId(record.getFinancialTransaction() == null ? null : record.getFinancialTransaction().getId());
        dto.setTransactionDate(optionalLocalDate(normalized, "transactionDate"));
        dto.setPostingDate(optionalLocalDate(normalized, "postingDate"));
        dto.setDescription(optionalText(normalized, "description"));
        dto.setSignedAmount(optionalText(normalized, "signedAmount"));
        dto.setAmount(optionalText(normalized, "amount"));
        dto.setFlow(optionalFlow(normalized, "flow"));
        dto.setCurrency(optionalCurrency(normalized, "currency"));
        dto.setExternalReference(optionalText(normalized, "externalReference"));
        dto.setNotes(optionalText(normalized, "notes"));
        dto.setErrorCode(record.getErrorCode());
        dto.setErrorMessage(record.getErrorMessage());
        dto.setDescriptionReview(CsvIngestionDescriptionReviewDTO.fromRawData(rawData));
        dto.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(candidate));
        return dto;
    }

    private JsonNode rawData(IngestionRecord record) {
        try {
            return record.getRawData() == null ? objectMapper.createObjectNode() : objectMapper.readTree(record.getRawData());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not read ingestion record data", e);
        }
    }

    private LocalDate optionalLocalDate(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : LocalDate.parse(value);
    }

    private TransactionFlow optionalFlow(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : TransactionFlow.valueOf(value);
    }

    private CurrencyCode optionalCurrency(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : CurrencyCode.valueOf(value);
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static class IngestionNotReadyException extends IllegalArgumentException {

        private IngestionNotReadyException(String message) {
            super(message);
        }
    }
}
