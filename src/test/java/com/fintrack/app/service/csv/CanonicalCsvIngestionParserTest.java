package com.fintrack.app.service.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.service.csv.CanonicalCsvIngestionParser.CsvIngestionFileException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CanonicalCsvIngestionParserTest {

    private static final String HEADER = "transactionDate,postingDate,description,signedAmount,currency,externalReference,notes";

    private final CanonicalCsvIngestionParser parser = new CanonicalCsvIngestionParser();

    @Test
    void exactHeaderAccepted() {
        var result = parse(HEADER + "\n2026-01-15,,NOMINA QUALTRICS,33698.34,MXN,,");

        assertThat(result.getRecordsReceived()).isEqualTo(1);
        assertThat(result.getRecordsRejected()).isZero();
        assertThat(result.getRows().get(0).isValid()).isTrue();
    }

    @Test
    void missingColumnRejected() {
        assertWholeFileRejected("transactionDate,postingDate,description,signedAmount,currency,externalReference\n", "INVALID_HEADER");
    }

    @Test
    void extraColumnRejected() {
        assertWholeFileRejected(HEADER + ",extra\n", "INVALID_HEADER");
    }

    @Test
    void reorderedHeaderRejected() {
        assertWholeFileRejected(
            "postingDate,transactionDate,description,signedAmount,currency,externalReference,notes\n",
            "INVALID_HEADER"
        );
    }

    @Test
    void caseChangedHeaderRejected() {
        assertWholeFileRejected(
            "transactiondate,postingDate,description,signedAmount,currency,externalReference,notes\n",
            "INVALID_HEADER"
        );
    }

    @Test
    void invalidTransactionDateRejectedAsRow() {
        var row = parseOne("not-a-date,,Coffee,-10.00,MXN,,");

        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("INVALID_TRANSACTION_DATE");
    }

    @Test
    void invalidPostingDateRejectedAsRow() {
        var row = parseOne("2026-01-15,nope,Coffee,-10.00,MXN,,");

        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("INVALID_POSTING_DATE");
    }

    @Test
    void blankDescriptionRejected() {
        var row = parseOne("2026-01-15,,   ,-10.00,MXN,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("DESCRIPTION_REQUIRED");
    }

    @Test
    void descriptionMaxLengthRejected() {
        var row = parseOne("2026-01-15,," + "a".repeat(501) + ",-10.00,MXN,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("DESCRIPTION_TOO_LONG");
    }

    @Test
    void zeroSignedAmountRejected() {
        var row = parseOne("2026-01-15,,Coffee,0,MXN,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("ZERO_SIGNED_AMOUNT");
    }

    @Test
    void malformedSignedAmountRejected() {
        var row = parseOne("2026-01-15,,Coffee,abc,MXN,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("INVALID_SIGNED_AMOUNT");
    }

    @Test
    void signedAmountScaleGreaterThanTwoRejected() {
        var row = parseOne("2026-01-15,,Coffee,-10.001,MXN,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("AMOUNT_SCALE_EXCEEDED");
    }

    @Test
    void positiveSignedAmountNormalizesToInAndAbsAmount() {
        var row = parseOne("2026-01-15,,Payroll,33698.34,MXN,,");

        assertThat(row.isValid()).isTrue();
        assertThat(row.getNormalized().getFlow()).isEqualTo(TransactionFlow.IN);
        assertThat(row.getNormalized().getAmount()).isEqualTo("33698.34");
        assertThat(row.getNormalized().getSignedAmount()).isEqualTo("33698.34");
    }

    @Test
    void negativeSignedAmountNormalizesToOutAndAbsAmount() {
        var row = parseOne("2026-01-15,,OXXO,-274.00,MXN,,");

        assertThat(row.isValid()).isTrue();
        assertThat(row.getNormalized().getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(row.getNormalized().getAmount()).isEqualTo("274.00");
        assertThat(row.getNormalized().getSignedAmount()).isEqualTo("-274.00");
    }

    @Test
    void unsupportedCurrencyRejected() {
        var row = parseOne("2026-01-15,,Coffee,-10.00,GBP,,");

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("UNSUPPORTED_CURRENCY");
    }

    @Test
    void accountCurrencyMismatchRejected() {
        var row = parse(HEADER + "\n2026-01-15,,Coffee,-10.00,USD,,", CurrencyCode.MXN).getRows().get(0);

        assertThat(row.getErrors()).extracting(CsvIngestionValidationMessage::getCode).contains("CURRENCY_MISMATCH");
    }

    @Test
    void externalReferenceTrimAndBlankToNull() {
        var row = parseOne("2026-01-15,,Coffee,-10.00,MXN,   ,");

        assertThat(row.isValid()).isTrue();
        assertThat(row.getNormalized().getExternalReference()).isNull();

        row = parseOne("2026-01-15,,Coffee,-10.00,MXN, abc-123 ,");
        assertThat(row.getNormalized().getExternalReference()).isEqualTo("abc-123");
    }

    @Test
    void notesTrimAndBlankToNull() {
        var row = parseOne("2026-01-15,,Coffee,-10.00,MXN,,   ");

        assertThat(row.isValid()).isTrue();
        assertThat(row.getNormalized().getNotes()).isNull();

        row = parseOne("2026-01-15,,Coffee,-10.00,MXN,, hello ");
        assertThat(row.getNormalized().getNotes()).isEqualTo("hello");
    }

    @Test
    void quotedCsvFieldWithCommaAccepted() {
        var row = parseOne("2026-01-17,2026-01-18,\"Uber, Trip\",-158.33,MXN,abc-123,\"quoted, note\"");

        assertThat(row.isValid()).isTrue();
        assertThat(row.getNormalized().getDescription()).isEqualTo("Uber, Trip");
        assertThat(row.getNormalized().getNotes()).isEqualTo("quoted, note");
    }

    @Test
    void emptyFileRejected() {
        assertWholeFileRejected("", "EMPTY_FILE");
    }

    @Test
    void headerOnlyFileRejected() {
        assertWholeFileRejected(HEADER + "\n", "HEADER_ONLY_FILE");
    }

    @Test
    void rowCountLimitRejected() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < CanonicalCsvIngestionParser.MAX_DATA_ROWS + 1; i++) {
            csv.append("\n2026-01-15,,Coffee,-10.00,MXN,,");
        }

        assertWholeFileRejected(csv.toString(), "ROW_LIMIT_EXCEEDED");
    }

    @Test
    void fileSizeLimitRejected() {
        byte[] bytes = new byte[(int) CanonicalCsvIngestionParser.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> parser.parse(bytes, CurrencyCode.MXN))
            .isInstanceOf(CsvIngestionFileException.class)
            .extracting("code")
            .isEqualTo("FILE_TOO_LARGE");
    }

    private CanonicalCsvIngestionParser.CsvRowResult parseOne(String row) {
        return parse(HEADER + "\n" + row).getRows().get(0);
    }

    private CanonicalCsvIngestionParser.CsvParseResult parse(String csv) {
        return parse(csv, CurrencyCode.MXN);
    }

    private CanonicalCsvIngestionParser.CsvParseResult parse(String csv, CurrencyCode accountCurrency) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), accountCurrency);
    }

    private void assertWholeFileRejected(String csv, String code) {
        assertThatThrownBy(() -> parse(csv)).isInstanceOf(CsvIngestionFileException.class).extracting("code").isEqualTo(code);
    }
}
