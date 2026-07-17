package com.fintrack.app.service.csv;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CanonicalCsvIngestionParser {

    public static final long MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L;
    public static final int MAX_DATA_ROWS = 5_000;

    private static final List<String> EXPECTED_HEADER = List.of(
        "transactionDate",
        "postingDate",
        "description",
        "signedAmount",
        "currency",
        "externalReference",
        "notes"
    );

    public CsvParseResult parse(byte[] bytes, CurrencyCode accountCurrency) {
        if (bytes == null || bytes.length == 0) {
            throw new CsvIngestionFileException("EMPTY_FILE", "CSV file is empty");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new CsvIngestionFileException("FILE_TOO_LARGE", "CSV file must be 2 MB or smaller");
        }
        if (accountCurrency == null) {
            throw new CsvIngestionFileException("ACCOUNT_CURRENCY_REQUIRED", "Account currency is required");
        }

        String content = decodeUtf8(bytes);
        List<List<String>> csvRows = parseCsv(content);
        if (csvRows.isEmpty() || isSingleEmptyRow(csvRows)) {
            throw new CsvIngestionFileException("EMPTY_FILE", "CSV file is empty");
        }
        if (!EXPECTED_HEADER.equals(csvRows.get(0))) {
            throw new CsvIngestionFileException("INVALID_HEADER", "CSV header must match the canonical FINTRACK header");
        }
        if (csvRows.size() == 1) {
            throw new CsvIngestionFileException("HEADER_ONLY_FILE", "CSV file must contain at least one data row");
        }

        int dataRowCount = csvRows.size() - 1;
        if (dataRowCount > MAX_DATA_ROWS) {
            throw new CsvIngestionFileException("ROW_LIMIT_EXCEEDED", "CSV file must contain 5,000 data rows or fewer");
        }

        List<CsvRowResult> rows = new ArrayList<>();
        LocalDate statementStartDate = null;
        LocalDate statementEndDate = null;
        int rejectedCount = 0;

        for (int i = 1; i < csvRows.size(); i++) {
            CsvRowResult row = validateRow(i, csvRows.get(i), accountCurrency);
            rows.add(row);
            if (row.isValid()) {
                LocalDate transactionDate = row.getNormalized().getTransactionDate();
                if (statementStartDate == null || transactionDate.isBefore(statementStartDate)) {
                    statementStartDate = transactionDate;
                }
                if (statementEndDate == null || transactionDate.isAfter(statementEndDate)) {
                    statementEndDate = transactionDate;
                }
            } else {
                rejectedCount++;
            }
        }

        return new CsvParseResult(rows, dataRowCount, rejectedCount, statementStartDate, statementEndDate);
    }

    private CsvRowResult validateRow(int recordIndex, List<String> columns, CurrencyCode accountCurrency) {
        CsvRawRow raw = rawRow(columns);
        CsvNormalizedRow normalized = new CsvNormalizedRow();
        List<CsvIngestionValidationMessage> errors = new ArrayList<>();
        List<CsvIngestionValidationMessage> warnings = new ArrayList<>();

        if (columns.size() != EXPECTED_HEADER.size()) {
            errors.add(new CsvIngestionValidationMessage("INVALID_COLUMN_COUNT", "CSV row must contain exactly 7 columns"));
        }

        LocalDate transactionDate = parseRequiredDate(raw.getTransactionDate(), "transactionDate", "INVALID_TRANSACTION_DATE", errors);
        normalized.setTransactionDate(transactionDate);
        normalized.setPostingDate(parseOptionalDate(raw.getPostingDate(), "INVALID_POSTING_DATE", errors));

        String description = normalizeRequiredString(
            raw.getDescription(),
            "description",
            "DESCRIPTION_REQUIRED",
            500,
            "DESCRIPTION_TOO_LONG",
            errors
        );
        normalized.setDescription(description);

        BigDecimal signedAmount = parseSignedAmount(raw.getSignedAmount(), errors);
        if (signedAmount != null) {
            normalized.setSignedAmount(signedAmount.toPlainString());
            normalized.setAmount(signedAmount.abs().toPlainString());
            normalized.setFlow(signedAmount.signum() > 0 ? TransactionFlow.IN : TransactionFlow.OUT);
        }

        CurrencyCode currency = parseCurrency(raw.getCurrency(), errors);
        normalized.setCurrency(currency);
        if (currency != null && currency != accountCurrency) {
            errors.add(new CsvIngestionValidationMessage("CURRENCY_MISMATCH", "currency must match the selected account currency"));
        }

        normalized.setExternalReference(normalizeOptionalString(raw.getExternalReference(), 150, "EXTERNAL_REFERENCE_TOO_LONG", errors));
        normalized.setNotes(normalizeOptionalString(raw.getNotes(), 1000, "NOTES_TOO_LONG", errors));

        return new CsvRowResult(recordIndex, raw, normalized, errors, warnings);
    }

    private CsvRawRow rawRow(List<String> columns) {
        return new CsvRawRow(
            valueAt(columns, 0),
            valueAt(columns, 1),
            valueAt(columns, 2),
            valueAt(columns, 3),
            valueAt(columns, 4),
            valueAt(columns, 5),
            valueAt(columns, 6)
        );
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new CsvIngestionFileException("INVALID_UTF8", "CSV file must be UTF-8");
        }
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldQuoted = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                if (field.length() == 0 && !fieldQuoted) {
                    inQuotes = true;
                    fieldQuoted = true;
                } else {
                    throw new CsvIngestionFileException("MALFORMED_CSV", "CSV file is malformed");
                }
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldQuoted = false;
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                fieldQuoted = false;
            } else if (c == '\r') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                fieldQuoted = false;
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(c);
            }
        }

        if (inQuotes) {
            throw new CsvIngestionFileException("MALFORMED_CSV", "CSV file is malformed");
        }
        if (field.length() > 0 || fieldQuoted || !row.isEmpty() || content.endsWith(",")) {
            row.add(field.toString());
            rows.add(row);
        }

        return rows;
    }

    private boolean isSingleEmptyRow(List<List<String>> rows) {
        return rows.size() == 1 && rows.get(0).size() == 1 && rows.get(0).get(0).isEmpty();
    }

    private LocalDate parseRequiredDate(String value, String fieldName, String errorCode, List<CsvIngestionValidationMessage> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            errors.add(new CsvIngestionValidationMessage(errorCode, fieldName + " must be ISO date YYYY-MM-DD"));
            return null;
        }
        return parseDate(normalized, fieldName, errorCode, errors);
    }

    private LocalDate parseOptionalDate(String value, String errorCode, List<CsvIngestionValidationMessage> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return parseDate(normalized, "postingDate", errorCode, errors);
    }

    private LocalDate parseDate(String value, String fieldName, String errorCode, List<CsvIngestionValidationMessage> errors) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.add(new CsvIngestionValidationMessage(errorCode, fieldName + " must be ISO date YYYY-MM-DD"));
            return null;
        }
    }

    private String normalizeRequiredString(
        String value,
        String fieldName,
        String requiredCode,
        int maxLength,
        String maxLengthCode,
        List<CsvIngestionValidationMessage> errors
    ) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            errors.add(new CsvIngestionValidationMessage(requiredCode, fieldName + " is required"));
            return null;
        }
        if (normalized.length() > maxLength) {
            errors.add(new CsvIngestionValidationMessage(maxLengthCode, fieldName + " must be " + maxLength + " characters or fewer"));
            return null;
        }
        return normalized;
    }

    private String normalizeOptionalString(String value, int maxLength, String maxLengthCode, List<CsvIngestionValidationMessage> errors) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            errors.add(new CsvIngestionValidationMessage(maxLengthCode, "value must be " + maxLength + " characters or fewer"));
            return null;
        }
        return normalized;
    }

    private BigDecimal parseSignedAmount(String value, List<CsvIngestionValidationMessage> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            errors.add(new CsvIngestionValidationMessage("SIGNED_AMOUNT_REQUIRED", "signedAmount is required"));
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(normalized);
            if (amount.signum() == 0) {
                errors.add(new CsvIngestionValidationMessage("ZERO_SIGNED_AMOUNT", "signedAmount must be nonzero"));
                return amount;
            }
            if (amount.scale() > 2) {
                errors.add(new CsvIngestionValidationMessage("AMOUNT_SCALE_EXCEEDED", "signedAmount must have at most 2 decimal places"));
            }
            return amount;
        } catch (NumberFormatException e) {
            errors.add(new CsvIngestionValidationMessage("INVALID_SIGNED_AMOUNT", "signedAmount must be a decimal number"));
            return null;
        }
    }

    private CurrencyCode parseCurrency(String value, List<CsvIngestionValidationMessage> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            errors.add(new CsvIngestionValidationMessage("CURRENCY_REQUIRED", "currency is required"));
            return null;
        }
        try {
            return CurrencyCode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            errors.add(new CsvIngestionValidationMessage("UNSUPPORTED_CURRENCY", "currency is not supported"));
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueAt(List<String> values, int index) {
        if (index >= values.size()) {
            return "";
        }
        return values.get(index);
    }

    public static class CsvIngestionFileException extends IllegalArgumentException {

        private final String code;

        public CsvIngestionFileException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class CsvParseResult {

        private final List<CsvRowResult> rows;
        private final int recordsReceived;
        private final int recordsRejected;
        private final LocalDate statementStartDate;
        private final LocalDate statementEndDate;

        public CsvParseResult(
            List<CsvRowResult> rows,
            int recordsReceived,
            int recordsRejected,
            LocalDate statementStartDate,
            LocalDate statementEndDate
        ) {
            this.rows = List.copyOf(rows);
            this.recordsReceived = recordsReceived;
            this.recordsRejected = recordsRejected;
            this.statementStartDate = statementStartDate;
            this.statementEndDate = statementEndDate;
        }

        public List<CsvRowResult> getRows() {
            return rows;
        }

        public int getRecordsReceived() {
            return recordsReceived;
        }

        public int getRecordsRejected() {
            return recordsRejected;
        }

        public int getRecordsCreated() {
            return 0;
        }

        public int getRecordsSkipped() {
            return 0;
        }

        public int getValidRows() {
            return recordsReceived - recordsRejected;
        }

        public LocalDate getStatementStartDate() {
            return statementStartDate;
        }

        public LocalDate getStatementEndDate() {
            return statementEndDate;
        }
    }

    public static class CsvRowResult {

        private final int recordIndex;
        private final CsvRawRow raw;
        private final CsvNormalizedRow normalized;
        private final List<CsvIngestionValidationMessage> errors;
        private final List<CsvIngestionValidationMessage> warnings;

        public CsvRowResult(
            int recordIndex,
            CsvRawRow raw,
            CsvNormalizedRow normalized,
            List<CsvIngestionValidationMessage> errors,
            List<CsvIngestionValidationMessage> warnings
        ) {
            this.recordIndex = recordIndex;
            this.raw = raw;
            this.normalized = normalized;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public int getRecordIndex() {
            return recordIndex;
        }

        public CsvRawRow getRaw() {
            return raw;
        }

        public CsvNormalizedRow getNormalized() {
            return normalized;
        }

        public List<CsvIngestionValidationMessage> getErrors() {
            return errors;
        }

        public List<CsvIngestionValidationMessage> getWarnings() {
            return warnings;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public static class CsvRawRow {

        private final String transactionDate;
        private final String postingDate;
        private final String description;
        private final String signedAmount;
        private final String currency;
        private final String externalReference;
        private final String notes;

        public CsvRawRow(
            String transactionDate,
            String postingDate,
            String description,
            String signedAmount,
            String currency,
            String externalReference,
            String notes
        ) {
            this.transactionDate = transactionDate;
            this.postingDate = postingDate;
            this.description = description;
            this.signedAmount = signedAmount;
            this.currency = currency;
            this.externalReference = externalReference;
            this.notes = notes;
        }

        public String getTransactionDate() {
            return transactionDate;
        }

        public String getPostingDate() {
            return postingDate;
        }

        public String getDescription() {
            return description;
        }

        public String getSignedAmount() {
            return signedAmount;
        }

        public String getCurrency() {
            return currency;
        }

        public String getExternalReference() {
            return externalReference;
        }

        public String getNotes() {
            return notes;
        }
    }

    public static class CsvNormalizedRow {

        private LocalDate transactionDate;
        private LocalDate postingDate;
        private String description;
        private String signedAmount;
        private String amount;
        private TransactionFlow flow;
        private CurrencyCode currency;
        private String externalReference;
        private String notes;

        public LocalDate getTransactionDate() {
            return transactionDate;
        }

        public void setTransactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
        }

        public LocalDate getPostingDate() {
            return postingDate;
        }

        public void setPostingDate(LocalDate postingDate) {
            this.postingDate = postingDate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSignedAmount() {
            return signedAmount;
        }

        public void setSignedAmount(String signedAmount) {
            this.signedAmount = signedAmount;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public TransactionFlow getFlow() {
            return flow;
        }

        public void setFlow(TransactionFlow flow) {
            this.flow = flow;
        }

        public CurrencyCode getCurrency() {
            return currency;
        }

        public void setCurrency(CurrencyCode currency) {
            this.currency = currency;
        }

        public String getExternalReference() {
            return externalReference;
        }

        public void setExternalReference(String externalReference) {
            this.externalReference = externalReference;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
