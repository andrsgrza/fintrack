package com.fintrack.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvNormalizedRow;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvParseResult;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvRawRow;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvRowResult;
import com.fintrack.app.service.csv.CsvIngestionValidationMessage;
import com.fintrack.app.service.dto.CsvIngestionPreviewCountsDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewRowDTO;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CsvIngestionPreviewService {

    private static final String PARSER_NAME = "fintrack-canonical-csv";
    private static final String PARSER_VERSION = "1.0";

    private final FinancialAccountRepository financialAccountRepository;
    private final TransactionIngestionRepository transactionIngestionRepository;
    private final FileIngestionRepository fileIngestionRepository;
    private final IngestionRecordRepository ingestionRecordRepository;
    private final CurrentUserService currentUserService;
    private final CanonicalCsvIngestionParser parser;
    private final ObjectMapper objectMapper;

    public CsvIngestionPreviewService(
        FinancialAccountRepository financialAccountRepository,
        TransactionIngestionRepository transactionIngestionRepository,
        FileIngestionRepository fileIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        CurrentUserService currentUserService,
        CanonicalCsvIngestionParser parser,
        ObjectMapper objectMapper
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.fileIngestionRepository = fileIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.currentUserService = currentUserService;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public CsvIngestionPreviewResponseDTO createPreview(Long accountId, MultipartFile file) {
        FinancialAccount account = resolveCurrentUserAccount(accountId);
        byte[] bytes = readFileBytes(file);
        String checksum = sha256Hex(bytes);
        CsvParseResult parseResult = parser.parse(bytes, account.getCurrency());
        List<CsvIngestionValidationMessage> warnings = duplicateChecksumWarnings(account.getId(), checksum);

        Instant startedAt = Instant.now();
        IngestionStatus status = parseResult.getRecordsRejected() == 0 ? IngestionStatus.COMPLETED : IngestionStatus.PARTIALLY_COMPLETED;

        TransactionIngestion transactionIngestion = new TransactionIngestion()
            .ingestionType(IngestionType.FILE)
            .status(status)
            .sourceLabel(sourceLabel(file))
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .recordsReceived(parseResult.getRecordsReceived())
            .recordsCreated(0)
            .recordsSkipped(0)
            .recordsRejected(parseResult.getRecordsRejected())
            .errorMessage(null)
            .createdAt(startedAt)
            .account(account);
        transactionIngestion = transactionIngestionRepository.save(transactionIngestion);

        FileIngestion fileIngestion = new FileIngestion()
            .originalFilename(originalFilename(file))
            .fileType(ImportFileType.CSV)
            .contentType(truncate(trimToNull(file.getContentType()), 100))
            .fileSizeBytes((long) bytes.length)
            .checksum(checksum)
            .storageKey(null)
            .parserName(PARSER_NAME)
            .parserVersion(PARSER_VERSION)
            .statementStartDate(parseResult.getStatementStartDate())
            .statementEndDate(parseResult.getStatementEndDate())
            .createdAt(Instant.now())
            .transactionIngestion(transactionIngestion);
        fileIngestion = fileIngestionRepository.save(fileIngestion);

        List<IngestionRecord> records = new ArrayList<>();
        for (CsvRowResult row : parseResult.getRows()) {
            records.add(toIngestionRecord(row, transactionIngestion));
        }
        records = ingestionRecordRepository.saveAll(records);

        return toResponse(transactionIngestion, fileIngestion, records, parseResult, warnings);
    }

    private FinancialAccount resolveCurrentUserAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account is required");
        }
        return financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Account is not accessible"));
    }

    private byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }
        if (file.getSize() > CanonicalCsvIngestionParser.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("CSV file must be 2 MB or smaller");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV file could not be read", e);
        }
    }

    private List<CsvIngestionValidationMessage> duplicateChecksumWarnings(Long accountId, String checksum) {
        if (fileIngestionRepository.existsByChecksumAndTransactionIngestionAccountId(checksum, accountId)) {
            return List.of(
                new CsvIngestionValidationMessage(
                    "DUPLICATE_FILE_CHECKSUM",
                    "A file with the same checksum was already previewed for this account"
                )
            );
        }
        return List.of();
    }

    private IngestionRecord toIngestionRecord(CsvRowResult row, TransactionIngestion transactionIngestion) {
        CsvIngestionValidationMessage firstError = row.getErrors().isEmpty() ? null : row.getErrors().get(0);
        return new IngestionRecord()
            .recordIndex(row.getRecordIndex())
            .externalRecordId(row.getNormalized().getExternalReference())
            .status(row.isValid() ? IngestionRecordStatus.CREATED : IngestionRecordStatus.REJECTED)
            .rawData(rawData(row))
            .errorCode(firstError == null ? null : firstError.getCode())
            .errorMessage(firstError == null ? null : firstError.getMessage())
            .createdAt(Instant.now())
            .financialTransaction(null)
            .transactionIngestion(transactionIngestion);
    }

    private CsvIngestionPreviewResponseDTO toResponse(
        TransactionIngestion transactionIngestion,
        FileIngestion fileIngestion,
        List<IngestionRecord> records,
        CsvParseResult parseResult,
        List<CsvIngestionValidationMessage> warnings
    ) {
        CsvIngestionPreviewResponseDTO response = new CsvIngestionPreviewResponseDTO();
        response.setTransactionIngestionId(transactionIngestion.getId());
        response.setFileIngestionId(fileIngestion.getId());
        response.setStatus(transactionIngestion.getStatus());
        response.setSourceLabel(transactionIngestion.getSourceLabel());
        response.setCounts(counts(parseResult));
        response.setWarnings(warnings);

        List<CsvIngestionPreviewRowDTO> rows = new ArrayList<>();
        for (int i = 0; i < parseResult.getRows().size(); i++) {
            rows.add(toRowDto(records.get(i), parseResult.getRows().get(i)));
        }
        response.setRows(rows);
        return response;
    }

    private CsvIngestionPreviewCountsDTO counts(CsvParseResult parseResult) {
        CsvIngestionPreviewCountsDTO counts = new CsvIngestionPreviewCountsDTO();
        counts.setRecordsReceived(parseResult.getRecordsReceived());
        counts.setRecordsCreated(0);
        counts.setRecordsSkipped(0);
        counts.setRecordsRejected(parseResult.getRecordsRejected());
        counts.setValidRows(parseResult.getValidRows());
        counts.setInvalidRows(parseResult.getRecordsRejected());
        return counts;
    }

    private CsvIngestionPreviewRowDTO toRowDto(IngestionRecord record, CsvRowResult row) {
        CsvNormalizedRow normalized = row.getNormalized();
        CsvIngestionPreviewRowDTO dto = new CsvIngestionPreviewRowDTO();
        dto.setIngestionRecordId(record.getId());
        dto.setRecordIndex(record.getRecordIndex());
        dto.setStatus(record.getStatus());
        dto.setTransactionDate(normalized.getTransactionDate());
        dto.setPostingDate(normalized.getPostingDate());
        dto.setDescription(normalized.getDescription());
        dto.setSignedAmount(normalized.getSignedAmount());
        dto.setAmount(normalized.getAmount());
        dto.setFlow(normalized.getFlow());
        dto.setCurrency(normalized.getCurrency());
        dto.setExternalReference(normalized.getExternalReference());
        dto.setNotes(normalized.getNotes());
        dto.setErrorCode(record.getErrorCode());
        dto.setErrorMessage(record.getErrorMessage());
        dto.setWarnings(row.getWarnings());
        return dto;
    }

    private String rawData(CsvRowResult row) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("raw", rawMap(row.getRaw()));
        root.put("normalized", normalizedMap(row.getNormalized()));
        root.put("errors", row.getErrors());
        root.put("warnings", row.getWarnings());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize CSV row preview", e);
        }
    }

    private Map<String, Object> rawMap(CsvRawRow raw) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("transactionDate", raw.getTransactionDate());
        values.put("postingDate", raw.getPostingDate());
        values.put("description", raw.getDescription());
        values.put("signedAmount", raw.getSignedAmount());
        values.put("currency", raw.getCurrency());
        values.put("externalReference", raw.getExternalReference());
        values.put("notes", raw.getNotes());
        return values;
    }

    private Map<String, Object> normalizedMap(CsvNormalizedRow normalized) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("transactionDate", normalized.getTransactionDate() == null ? null : normalized.getTransactionDate().toString());
        values.put("postingDate", normalized.getPostingDate() == null ? null : normalized.getPostingDate().toString());
        values.put("description", normalized.getDescription());
        values.put("signedAmount", normalized.getSignedAmount());
        values.put("amount", normalized.getAmount());
        values.put("flow", normalized.getFlow() == null ? null : normalized.getFlow().name());
        values.put("currency", normalized.getCurrency() == null ? null : normalized.getCurrency().name());
        values.put("externalReference", normalized.getExternalReference());
        values.put("notes", normalized.getNotes());
        return values;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String sourceLabel(MultipartFile file) {
        return truncate("Canonical CSV: " + originalFilename(file), 100);
    }

    private String originalFilename(MultipartFile file) {
        String filename = trimToNull(file.getOriginalFilename());
        return truncate(filename == null ? "upload.csv" : filename, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
