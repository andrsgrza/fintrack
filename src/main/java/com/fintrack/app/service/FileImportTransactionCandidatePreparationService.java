package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.PrepareTransactionCandidateRowAction;
import com.fintrack.app.service.dto.PrepareTransactionCandidateRowResultDTO;
import com.fintrack.app.service.dto.PrepareTransactionCandidatesResponseDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FileImportTransactionCandidatePreparationService {

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final TransactionCandidateRepository transactionCandidateRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public FileImportTransactionCandidatePreparationService(
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        TransactionCandidateRepository transactionCandidateRepository,
        CurrentUserService currentUserService,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    public PrepareTransactionCandidatesResponseDTO prepare(Long transactionIngestionId) {
        String ownerLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, ownerLogin);
        List<IngestionRecord> records = ingestionRecordRepository.findAllByTransactionIngestionIdOrderByRecordIndexAsc(ingestion.getId());
        if (ingestion.getStatus() == IngestionStatus.PARTIALLY_READY && records.stream().noneMatch(this::isValidRecord)) {
            throw new IllegalArgumentException("At least one valid ingestion record is required to prepare candidates");
        }

        PrepareTransactionCandidatesResponseDTO response = new PrepareTransactionCandidatesResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        for (IngestionRecord record : records) {
            PrepareTransactionCandidateRowResultDTO row = prepareRecord(ingestion, record, ownerLogin);
            response.getRows().add(row);
            increment(response, row.getAction());
        }
        return response;
    }

    private TransactionIngestion resolveAccessibleFileIngestion(Long transactionIngestionId, String ownerLogin) {
        if (transactionIngestionId == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion ingestion = transactionIngestionRepository
            .findOneWithToOneRelationshipsByIdAndAccountUserLogin(transactionIngestionId, ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        if (ingestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only FILE ingestions can prepare transaction candidates");
        }
        if (ingestion.getAccount() == null || ingestion.getAccount().getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion account is required");
        }
        if (ingestion.getStatus() != IngestionStatus.READY && ingestion.getStatus() != IngestionStatus.PARTIALLY_READY) {
            throw new IllegalArgumentException("Only READY or PARTIALLY_READY file ingestions can prepare transaction candidates");
        }
        return ingestion;
    }

    private PrepareTransactionCandidateRowResultDTO prepareRecord(
        TransactionIngestion ingestion,
        IngestionRecord record,
        String ownerLogin
    ) {
        if (!isValidRecord(record)) {
            return rowResult(record, null, PrepareTransactionCandidateRowAction.SKIPPED, "Only VALID records create candidates");
        }

        try {
            CandidateFields fields = candidateFields(ingestion, record);
            return transactionCandidateRepository
                .findOneWithRelationshipsByIngestionRecordIdAndUserLogin(record.getId(), ownerLogin)
                .map(existing -> syncExistingCandidate(record, existing, fields))
                .orElseGet(() -> createCandidate(ingestion, record, fields));
        } catch (IllegalArgumentException e) {
            return rowResult(record, null, PrepareTransactionCandidateRowAction.ERROR, e.getMessage());
        }
    }

    private PrepareTransactionCandidateRowResultDTO createCandidate(
        TransactionIngestion ingestion,
        IngestionRecord record,
        CandidateFields fields
    ) {
        Instant now = Instant.now();
        TransactionCandidate candidate = new TransactionCandidate()
            .source(TransactionCandidateSource.FILE_IMPORT)
            .status(TransactionCandidateStatus.READY_TO_POST)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(fields.descriptionReviewStatus())
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .transactionDate(fields.transactionDate())
            .postingDate(fields.postingDate())
            .description(fields.description())
            .signedAmount(fields.signedAmount())
            .amount(fields.amount())
            .flow(fields.flow())
            .currencySnapshot(fields.currency())
            .externalReference(fields.externalReference())
            .notes(fields.notes())
            .createdAt(now)
            .updatedAt(now)
            .user(ingestion.getAccount().getUser())
            .account(ingestion.getAccount())
            .transactionIngestion(ingestion)
            .ingestionRecord(record);
        candidate = transactionCandidateRepository.save(candidate);
        return rowResult(record, candidate.getId(), PrepareTransactionCandidateRowAction.CREATED, "Candidate created");
    }

    private PrepareTransactionCandidateRowResultDTO syncExistingCandidate(
        IngestionRecord record,
        TransactionCandidate candidate,
        CandidateFields fields
    ) {
        if (candidate.getSource() != TransactionCandidateSource.FILE_IMPORT) {
            return rowResult(
                record,
                candidate.getId(),
                PrepareTransactionCandidateRowAction.ERROR,
                "Existing candidate source is not FILE_IMPORT"
            );
        }
        if (candidate.getStatus() == TransactionCandidateStatus.POSTED) {
            return rowResult(record, candidate.getId(), PrepareTransactionCandidateRowAction.SKIPPED, "Posted candidate not modified");
        }
        if (candidate.getStatus() == TransactionCandidateStatus.CANCELLED || candidate.getStatus() == TransactionCandidateStatus.FAILED) {
            return rowResult(record, candidate.getId(), PrepareTransactionCandidateRowAction.ERROR, "Final candidate cannot be synced");
        }
        if (candidate.getFinancialTransaction() != null) {
            return rowResult(
                record,
                candidate.getId(),
                PrepareTransactionCandidateRowAction.ERROR,
                "Candidate with financial transaction link cannot be synced"
            );
        }

        boolean ruleInputChanged = ruleInputChanged(candidate, fields);
        boolean changed = applyFields(candidate, fields);
        if (candidate.getStatus() != TransactionCandidateStatus.READY_TO_POST) {
            candidate.setStatus(TransactionCandidateStatus.READY_TO_POST);
            changed = true;
        }
        if (candidate.getValidationStatus() != TransactionCandidateValidationStatus.VALID) {
            candidate.setValidationStatus(TransactionCandidateValidationStatus.VALID);
            changed = true;
        }
        if (candidate.getDescriptionReviewStatus() != fields.descriptionReviewStatus()) {
            candidate.setDescriptionReviewStatus(fields.descriptionReviewStatus());
            changed = true;
        }
        if (ruleInputChanged && isFreshClassification(candidate.getClassificationReviewStatus())) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.STALE);
            changed = true;
        }

        if (!changed) {
            return rowResult(record, candidate.getId(), PrepareTransactionCandidateRowAction.UNCHANGED, "Candidate already up to date");
        }

        candidate.setUpdatedAt(Instant.now());
        transactionCandidateRepository.save(candidate);
        return rowResult(record, candidate.getId(), PrepareTransactionCandidateRowAction.UPDATED, "Candidate synced from normalized row");
    }

    private boolean applyFields(TransactionCandidate candidate, CandidateFields fields) {
        boolean changed = false;
        if (!Objects.equals(accountId(candidate), fields.account().getId())) {
            candidate.setAccount(fields.account());
            changed = true;
        }
        if (!Objects.equals(candidate.getTransactionDate(), fields.transactionDate())) {
            candidate.setTransactionDate(fields.transactionDate());
            changed = true;
        }
        if (!Objects.equals(candidate.getPostingDate(), fields.postingDate())) {
            candidate.setPostingDate(fields.postingDate());
            changed = true;
        }
        if (!Objects.equals(candidate.getDescription(), fields.description())) {
            candidate.setDescription(fields.description());
            changed = true;
        }
        if (!sameAmount(candidate.getSignedAmount(), fields.signedAmount())) {
            candidate.setSignedAmount(fields.signedAmount());
            changed = true;
        }
        if (!sameAmount(candidate.getAmount(), fields.amount())) {
            candidate.setAmount(fields.amount());
            changed = true;
        }
        if (candidate.getFlow() != fields.flow()) {
            candidate.setFlow(fields.flow());
            changed = true;
        }
        if (candidate.getCurrencySnapshot() != fields.currency()) {
            candidate.setCurrencySnapshot(fields.currency());
            changed = true;
        }
        if (!Objects.equals(candidate.getExternalReference(), fields.externalReference())) {
            candidate.setExternalReference(fields.externalReference());
            changed = true;
        }
        if (!Objects.equals(candidate.getNotes(), fields.notes())) {
            candidate.setNotes(fields.notes());
            changed = true;
        }
        return changed;
    }

    private CandidateFields candidateFields(TransactionIngestion ingestion, IngestionRecord record) {
        JsonNode rawData = rawData(record);
        JsonNode normalized = rawData.path("normalized");
        FinancialAccount account = ingestion.getAccount();
        LocalDate transactionDate = parseRequiredDate(normalized, "transactionDate");
        LocalDate postingDate = parseOptionalDate(normalized, "postingDate");
        String description = requiredText(normalized, "description", "Description is required");
        BigDecimal signedAmount = parseSignedAmount(normalized);
        BigDecimal amount = signedAmount.abs();
        TransactionFlow flow = signedAmount.signum() > 0 ? TransactionFlow.IN : TransactionFlow.OUT;
        CurrencyCode currency = parseRequiredCurrency(normalized);
        if (currency != account.getCurrency()) {
            throw new IllegalArgumentException("Currency must match the transaction ingestion account currency");
        }
        return new CandidateFields(
            account,
            transactionDate,
            postingDate,
            description,
            signedAmount,
            amount,
            flow,
            currency,
            textOrNull(normalized, "externalReference"),
            textOrNull(normalized, "notes"),
            descriptionReviewStatus(rawData)
        );
    }

    private JsonNode rawData(IngestionRecord record) {
        try {
            return record.getRawData() == null ? objectMapper.createObjectNode() : objectMapper.readTree(record.getRawData());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ingestion record rawData could not be parsed");
        }
    }

    private LocalDate parseRequiredDate(JsonNode node, String fieldName) {
        String value = requiredText(node, fieldName, fieldName + " is required");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be an ISO date");
        }
    }

    private LocalDate parseOptionalDate(JsonNode node, String fieldName) {
        String value = textOrNull(node, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be an ISO date");
        }
    }

    private BigDecimal parseSignedAmount(JsonNode normalized) {
        String value = requiredText(normalized, "signedAmount", "signedAmount is required");
        try {
            BigDecimal signedAmount = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
            if (signedAmount.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("signedAmount must not be zero");
            }
            return signedAmount;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("signedAmount must have at most 2 decimal places");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("signedAmount must be a decimal number");
        }
    }

    private CurrencyCode parseRequiredCurrency(JsonNode normalized) {
        String value = requiredText(normalized, "currency", "currency is required");
        try {
            return CurrencyCode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("currency is unsupported");
        }
    }

    private String requiredText(JsonNode node, String fieldName, String message) {
        String value = textOrNull(node, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private TransactionCandidateDescriptionReviewStatus descriptionReviewStatus(JsonNode rawData) {
        String source = textOrNull(rawData.path("review").path("description"), "source");
        if ("DESCRIPTION_RULE".equals(source)) {
            return TransactionCandidateDescriptionReviewStatus.AUTO_APPLIED;
        }
        if ("USER_EDIT".equals(source)) {
            return TransactionCandidateDescriptionReviewStatus.USER_EDITED;
        }
        return TransactionCandidateDescriptionReviewStatus.NOT_APPLICABLE;
    }

    private boolean ruleInputChanged(TransactionCandidate candidate, CandidateFields fields) {
        return (
            !Objects.equals(accountId(candidate), fields.account().getId()) ||
            !Objects.equals(candidate.getTransactionDate(), fields.transactionDate()) ||
            !Objects.equals(candidate.getPostingDate(), fields.postingDate()) ||
            !Objects.equals(candidate.getDescription(), fields.description()) ||
            !sameAmount(candidate.getAmount(), fields.amount()) ||
            candidate.getFlow() != fields.flow() ||
            !Objects.equals(candidate.getExternalReference(), fields.externalReference())
        );
    }

    private boolean isFreshClassification(TransactionCandidateClassificationReviewStatus status) {
        return (
            status == TransactionCandidateClassificationReviewStatus.SUGGESTED ||
            status == TransactionCandidateClassificationReviewStatus.USER_SELECTED ||
            status == TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE
        );
    }

    private Long accountId(TransactionCandidate candidate) {
        return candidate.getAccount() == null ? null : candidate.getAccount().getId();
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private boolean isValidRecord(IngestionRecord record) {
        return record.getStatus() == IngestionRecordStatus.VALID;
    }

    private PrepareTransactionCandidateRowResultDTO rowResult(
        IngestionRecord record,
        Long candidateId,
        PrepareTransactionCandidateRowAction action,
        String reason
    ) {
        PrepareTransactionCandidateRowResultDTO result = new PrepareTransactionCandidateRowResultDTO();
        result.setIngestionRecordId(record.getId());
        result.setRecordIndex(record.getRecordIndex());
        result.setCandidateId(candidateId);
        result.setAction(action);
        result.setReason(reason);
        return result;
    }

    private void increment(PrepareTransactionCandidatesResponseDTO response, PrepareTransactionCandidateRowAction action) {
        if (action == PrepareTransactionCandidateRowAction.CREATED) {
            response.incrementCreatedCount();
        } else if (action == PrepareTransactionCandidateRowAction.UPDATED) {
            response.incrementUpdatedCount();
        } else if (action == PrepareTransactionCandidateRowAction.UNCHANGED) {
            response.incrementUnchangedCount();
        } else if (action == PrepareTransactionCandidateRowAction.SKIPPED) {
            response.incrementSkippedCount();
        } else if (action == PrepareTransactionCandidateRowAction.ERROR) {
            response.incrementErrorCount();
        }
    }

    private record CandidateFields(
        FinancialAccount account,
        LocalDate transactionDate,
        LocalDate postingDate,
        String description,
        BigDecimal signedAmount,
        BigDecimal amount,
        TransactionFlow flow,
        CurrencyCode currency,
        String externalReference,
        String notes,
        TransactionCandidateDescriptionReviewStatus descriptionReviewStatus
    ) {}
}
