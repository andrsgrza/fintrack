package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.CsvIngestionReadinessService.CsvIngestionReadinessSnapshot;
import com.fintrack.app.service.dto.CsvIngestionConfirmImportResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewCountsDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewRowDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private final CurrentUserService currentUserService;
    private final CsvIngestionReadinessService csvIngestionReadinessService;
    private final ObjectMapper objectMapper;

    public CsvIngestionConfirmImportService(
        TransactionIngestionRepository transactionIngestionRepository,
        FileIngestionRepository fileIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        FinancialTransactionRepository financialTransactionRepository,
        CurrentUserService currentUserService,
        CsvIngestionReadinessService csvIngestionReadinessService,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.fileIngestionRepository = fileIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.currentUserService = currentUserService;
        this.csvIngestionReadinessService = csvIngestionReadinessService;
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
        if (!isConfirmPrecheckStatusAllowed(ingestion.getStatus())) {
            throw new IllegalArgumentException("Only ready file ingestions can be confirmed");
        }

        CsvIngestionReadinessSnapshot readiness = csvIngestionReadinessService.applyReadiness(ingestion, records);
        transactionIngestionRepository.save(ingestion);
        if (readiness.status() != IngestionStatus.READY) {
            throw new IngestionNotReadyException(NOT_READY_MESSAGE);
        }

        int createdNow = 0;
        Instant now = Instant.now();
        for (IngestionRecord record : records) {
            if (record.getStatus() == IngestionRecordStatus.VALID) {
                FinancialTransaction financialTransaction = financialTransactionRepository.save(
                    toFinancialTransaction(ingestion, record, now)
                );
                record.setStatus(IngestionRecordStatus.IMPORTED);
                record.setFinancialTransaction(financialTransaction);
                record.setErrorCode(null);
                record.setErrorMessage(null);
                ingestionRecordRepository.save(record);
                createdNow++;
            }
        }

        CsvIngestionPreviewCountsDTO counts = csvIngestionReadinessService.snapshot(records).counts();
        csvIngestionReadinessService.applyCounts(ingestion, counts);
        ingestion.setStatus(IngestionStatus.COMPLETED);
        ingestion.setCompletedAt(now);
        transactionIngestionRepository.save(ingestion);

        return response(ingestion, records, createdNow);
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

    private FinancialTransaction toFinancialTransaction(TransactionIngestion ingestion, IngestionRecord record, Instant now) {
        JsonNode normalized = rawData(record).path("normalized");
        CurrencyCode rowCurrency = requiredCurrency(normalized, "currency");
        if (
            ingestion.getAccount() == null ||
            ingestion.getAccount().getCurrency() == null ||
            rowCurrency != ingestion.getAccount().getCurrency()
        ) {
            throw new IllegalArgumentException("CSV row currency must match the selected account currency");
        }

        return new FinancialTransaction()
            .transactionDate(requiredLocalDate(normalized, "transactionDate"))
            .postingDate(optionalLocalDate(normalized, "postingDate"))
            .description(requiredText(normalized, "description"))
            .amount(requiredAmount(normalized, "amount"))
            .flow(requiredFlow(normalized, "flow"))
            .origin(TransactionOrigin.FILE_IMPORT)
            .externalReference(optionalText(normalized, "externalReference"))
            .notes(optionalText(normalized, "notes"))
            .createdAt(now)
            .updatedAt(now)
            .account(ingestion.getAccount())
            .category(null)
            .financialSubscription(null)
            .transactionIngestion(ingestion);
    }

    private CsvIngestionConfirmImportResponseDTO response(TransactionIngestion ingestion, List<IngestionRecord> records, int createdNow) {
        CsvIngestionPreviewCountsDTO counts = csvIngestionReadinessService.snapshot(records).counts();
        CsvIngestionConfirmImportResponseDTO response = new CsvIngestionConfirmImportResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setStatus(ingestion.getStatus());
        response.setCreatedNow(createdNow);
        response.setAlreadyImported(count(records, IngestionRecordStatus.IMPORTED) - createdNow);
        response.setSkipped(count(records, IngestionRecordStatus.DISABLED) + count(records, IngestionRecordStatus.SKIPPED_DUPLICATE));
        response.setRejected(count(records, IngestionRecordStatus.REJECTED));
        response.setFailed(count(records, IngestionRecordStatus.FAILED));
        response.setCounts(counts);
        response.setRows(records.stream().map(this::toRowDto).toList());
        return response;
    }

    private int count(List<IngestionRecord> records, IngestionRecordStatus status) {
        return (int) records.stream().filter(record -> record.getStatus() == status).count();
    }

    private CsvIngestionPreviewRowDTO toRowDto(IngestionRecord record) {
        JsonNode normalized = rawData(record).path("normalized");
        CsvIngestionPreviewRowDTO dto = new CsvIngestionPreviewRowDTO();
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
        return dto;
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

    private CurrencyCode requiredCurrency(JsonNode node, String fieldName) {
        return CurrencyCode.valueOf(requiredText(node, fieldName));
    }

    private CurrencyCode optionalCurrency(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null ? null : CurrencyCode.valueOf(value);
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

    private static class IngestionNotReadyException extends IllegalArgumentException {

        private IngestionNotReadyException(String message) {
            super(message);
        }
    }
}
