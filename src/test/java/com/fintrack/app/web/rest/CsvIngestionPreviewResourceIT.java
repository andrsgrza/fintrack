package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class CsvIngestionPreviewResourceIT {

    private static final String FILE_PREVIEW_URL = "/api/transaction-ingestions/file-preview";

    private static final String VALID_CSV =
        """
        transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
        2026-01-15,,NOMINA QUALTRICS,33698.34,MXN,,
        2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
        2026-01-17,2026-01-18,"Uber, Trip",-158.33,MXN,abc-123,"quoted, note"
        """;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private TransactionIngestionRepository transactionIngestionRepository;

    @Autowired
    private FileIngestionRepository fileIngestionRepository;

    @Autowired
    private IngestionRecordRepository ingestionRecordRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Test
    @Transactional
    void validCsvUploadCreatesPersistedPreview() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.recordsCreated").value(0))
            .andExpect(jsonPath("$.counts.recordsSkipped").value(0))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0))
            .andExpect(jsonPath("$.counts.validRows").value(3))
            .andExpect(jsonPath("$.rows[2].description").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].amount").value("158.33"))
            .andExpect(jsonPath("$.rows[2].flow").value("OUT"))
            .andExpect(jsonPath("$.rows[2].notes").value("quoted, note"));

        List<TransactionIngestion> ingestions = transactionIngestionRepository.findAll();
        assertThat(ingestions).hasSize(1);
        TransactionIngestion ingestion = ingestions.get(0);
        assertThat(ingestion.getAccount().getId()).isEqualTo(account.getId());
        assertThat(ingestion.getIngestionType()).isEqualTo(IngestionType.FILE);
        assertThat(ingestion.getStatus()).isEqualTo(IngestionStatus.READY);
        assertThat(ingestion.getRecordsReceived()).isEqualTo(3);
        assertThat(ingestion.getRecordsCreated()).isZero();
        assertThat(ingestion.getRecordsSkipped()).isZero();
        assertThat(ingestion.getRecordsRejected()).isZero();
        assertThat(ingestion.getErrorMessage()).isNull();
        assertThat(ingestion.getCompletedAt()).isNotNull();

        List<FileIngestion> fileIngestions = fileIngestionRepository.findAll();
        assertThat(fileIngestions).hasSize(1);
        FileIngestion fileIngestion = fileIngestions.get(0);
        assertThat(fileIngestion.getTransactionIngestion().getId()).isEqualTo(ingestion.getId());
        assertThat(fileIngestion.getFileType()).isEqualTo(ImportFileType.CSV);
        assertThat(fileIngestion.getChecksum()).isEqualTo(sha256Hex(VALID_CSV));
        assertThat(fileIngestion.getStorageKey()).isNull();
        assertThat(fileIngestion.getParserName()).isEqualTo("fintrack-canonical-csv");
        assertThat(fileIngestion.getParserVersion()).isEqualTo("1.0");
        assertThat(fileIngestion.getStatementStartDate()).isEqualTo(LocalDate.parse("2026-01-15"));
        assertThat(fileIngestion.getStatementEndDate()).isEqualTo(LocalDate.parse("2026-01-17"));

        List<IngestionRecord> records = recordsFor(ingestion);
        assertThat(records).hasSize(3);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.getStatus()).isEqualTo(IngestionRecordStatus.VALID);
            assertThat(record.getFinancialTransaction()).isNull();
            assertThat(record.getErrorCode()).isNull();
            assertThat(record.getErrorMessage()).isNull();
            assertThat(record.getRawData()).contains("\"raw\"", "\"normalized\"", "\"errors\"", "\"warnings\"");
        });
        assertThat(records.get(2).getExternalRecordId()).isEqualTo("abc-123");
        assertThat(objectMapper.readTree(records.get(2).getRawData()).path("normalized").path("description").asText()).isEqualTo(
            "Uber, Trip"
        );
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void invalidRowsPersistAsRejectedRecords() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            nope,,,-0.001,USD,,
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;

        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("mixed.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(2))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1))
            .andExpect(jsonPath("$.rows[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.rows[0].errorCode").value("INVALID_TRANSACTION_DATE"))
            .andExpect(jsonPath("$.rows[1].status").value("VALID"));

        TransactionIngestion ingestion = transactionIngestionRepository.findAll().get(0);
        List<IngestionRecord> records = recordsFor(ingestion);
        assertThat(records.get(0).getStatus()).isEqualTo(IngestionRecordStatus.REJECTED);
        assertThat(records.get(0).getFinancialTransaction()).isNull();
        assertThat(records.get(0).getErrorCode()).isEqualTo("INVALID_TRANSACTION_DATE");
        assertThat(records.get(0).getErrorMessage()).isNotBlank();
        assertThat(objectMapper.readTree(records.get(0).getRawData()).path("errors")).isNotEmpty();
    }

    @Test
    @Transactional
    void invalidHeaderCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(
                multipart(FILE_PREVIEW_URL)
                    .file(csvFile("invalid.csv", "transactionDate,description,signedAmount,currency\n2026-01-15,Coffee,-10.00,MXN"))
                    .param("accountId", account.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void missingFileCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc.perform(multipart(FILE_PREVIEW_URL).param("accountId", account.getId().toString())).andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void emptyFileCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("empty.csv", "")).param("accountId", account.getId().toString()))
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void headerOnlyFileCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(
                multipart(FILE_PREVIEW_URL)
                    .file(csvFile("header.csv", "transactionDate,postingDate,description,signedAmount,currency,externalReference,notes\n"))
                    .param("accountId", account.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void inaccessibleAccountRejectedAndCreatesNothing() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());

        mockMvc
            .perform(
                multipart(FILE_PREVIEW_URL)
                    .file(csvFile("canonical.csv", VALID_CSV))
                    .param("accountId", otherUsersAccount.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminForeignAccountRejectedAndCreatesNothing() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());

        mockMvc
            .perform(
                multipart(FILE_PREVIEW_URL)
                    .file(csvFile("canonical.csv", VALID_CSV))
                    .param("accountId", otherUsersAccount.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void duplicateChecksumSameAccountReturnsWarningOnly() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("first.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warnings").isEmpty());

        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("second.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warnings[0].code").value("DUPLICATE_FILE_CHECKSUM"));

        assertThat(transactionIngestionRepository.findAll()).hasSize(2);
        assertThat(fileIngestionRepository.findAll()).hasSize(2);
    }

    @Test
    @Transactional
    void getPersistedFilePreviewReturnsMetadataCountsAndRows() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();

        mockMvc
            .perform(get("/api/transaction-ingestions/" + ingestion.getId() + "/file-preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.fileIngestionId").exists())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.fileMetadata.originalFilename").value("canonical.csv"))
            .andExpect(jsonPath("$.fileMetadata.fileType").value("CSV"))
            .andExpect(jsonPath("$.fileMetadata.parserName").value("fintrack-canonical-csv"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.validRows").value(3))
            .andExpect(jsonPath("$.rows[0].status").value("VALID"))
            .andExpect(jsonPath("$.rows[0].financialTransaction").doesNotExist());

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void disableValidRowMarksDisabledAndRecalculatesCounters() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc
            .perform(post(reviewUrl(ingestion, record, "disable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.row.status").value("DISABLED"))
            .andExpect(jsonPath("$.row.errorCode").doesNotExist())
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.recordsSkipped").value(1))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0))
            .andExpect(jsonPath("$.counts.validRows").value(2));

        IngestionRecord disabled = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(disabled.getStatus()).isEqualTo(IngestionRecordStatus.DISABLED);
        assertThat(disabled.getFinancialTransaction()).isNull();
        assertThat(disabled.getErrorCode()).isNull();
        assertThat(disabled.getErrorMessage()).isNull();
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void disablingLastValidRowMakesBatchPartiallyReady() throws Exception {
        TransactionIngestion ingestion = createPreviewWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc
            .perform(post(reviewUrl(ingestion, record, "disable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.row.status").value("DISABLED"))
            .andExpect(jsonPath("$.counts.recordsSkipped").value(1))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0))
            .andExpect(jsonPath("$.counts.validRows").value(0));

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(
            IngestionStatus.PARTIALLY_READY
        );
    }

    @Test
    @Transactional
    void disableRejectedRowStopsBlockingBatch() throws Exception {
        TransactionIngestion ingestion = createPreviewWithInvalidRow();
        IngestionRecord rejected = recordsFor(ingestion).get(0);

        mockMvc
            .perform(post(reviewUrl(ingestion, rejected, "disable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.row.status").value("DISABLED"))
            .andExpect(jsonPath("$.counts.recordsSkipped").value(1))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0));

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void disabledRowsDoNotMakeBatchPartiallyReadyWhenValidRowsRemain() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isOk());

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void enableDisabledRejectedRowRevalidatesCurrentNormalizedValues() throws Exception {
        TransactionIngestion ingestion = createPreviewWithInvalidRow();
        IngestionRecord rejected = recordsFor(ingestion).get(0);

        mockMvc.perform(post(reviewUrl(ingestion, rejected, "disable"))).andExpect(status().isOk());

        mockMvc
            .perform(post(reviewUrl(ingestion, rejected, "enable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.row.status").value("REJECTED"))
            .andExpect(jsonPath("$.row.errorCode").value("INVALID_TRANSACTION_DATE"))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1));
    }

    @Test
    @Transactional
    void enableDisabledValidRowReturnsToValid() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isOk());

        mockMvc
            .perform(post(reviewUrl(ingestion, record, "enable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.row.status").value("VALID"))
            .andExpect(jsonPath("$.row.errorCode").doesNotExist())
            .andExpect(jsonPath("$.counts.recordsSkipped").value(0))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0));

        IngestionRecord enabled = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(enabled.getFinancialTransaction()).isNull();
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void editValidRowWithValidDataKeepsRawDataRawAndDerivesAmountAndFlow() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        String originalRaw = objectMapper.readTree(record.getRawData()).path("raw").toString();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-20", null, "Corrected description", "-274.00", "MXN", null, null)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.row.status").value("VALID"))
            .andExpect(jsonPath("$.row.transactionDate").value("2026-01-20"))
            .andExpect(jsonPath("$.row.description").value("Corrected description"))
            .andExpect(jsonPath("$.row.signedAmount").value("-274.00"))
            .andExpect(jsonPath("$.row.amount").value("274.00"))
            .andExpect(jsonPath("$.row.flow").value("OUT"))
            .andExpect(jsonPath("$.row.errorCode").doesNotExist())
            .andExpect(jsonPath("$.counts.recordsRejected").value(0));

        IngestionRecord edited = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        JsonNode rawData = objectMapper.readTree(edited.getRawData());
        assertThat(rawData.path("raw").toString()).isEqualTo(originalRaw);
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Corrected description");
        assertThat(rawData.path("normalized").path("amount").asText()).isEqualTo("274.00");
        assertThat(rawData.path("normalized").path("flow").asText()).isEqualTo("OUT");
        assertThat(rawData.path("errors")).isEmpty();
        assertThat(rawData.path("review").path("edited").asBoolean()).isTrue();
        assertThat(rawData.path("review").path("editedBy").asText()).isEqualTo("user");
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void editValidRowWithInvalidDataMarksRejectedAndUpdatesCounters() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Corrected", "0", "MXN", null, null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.row.status").value("REJECTED"))
            .andExpect(jsonPath("$.row.errorCode").value("ZERO_SIGNED_AMOUNT"))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1))
            .andExpect(jsonPath("$.counts.validRows").value(2));

        IngestionRecord edited = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(edited.getStatus()).isEqualTo(IngestionRecordStatus.REJECTED);
        assertThat(edited.getErrorCode()).isEqualTo("ZERO_SIGNED_AMOUNT");
        assertThat(objectMapper.readTree(edited.getRawData()).path("errors")).isNotEmpty();
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(
            IngestionStatus.PARTIALLY_READY
        );
    }

    @Test
    @Transactional
    void editRejectedRowWithValidOrInvalidDataRevalidates() throws Exception {
        TransactionIngestion ingestion = createPreviewWithInvalidRow();
        IngestionRecord rejected = recordsFor(ingestion).get(0);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, rejected, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-20", null, "Corrected rejected row", "25.00", "MXN", "fixed-ref", null)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.row.status").value("VALID"))
            .andExpect(jsonPath("$.row.externalReference").value("fixed-ref"))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0));

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, rejected, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("bad-date", null, "Still bad", "25.00", "MXN", null, null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.row.status").value("REJECTED"))
            .andExpect(jsonPath("$.row.errorCode").value("INVALID_TRANSACTION_DATE"))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1));
    }

    @Test
    @Transactional
    void editDisabledRowIsRejected() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isOk());

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Enabled by edit", "10.00", "MXN", null, null))
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Disabled rows must be enabled before editing."))
            .andExpect(jsonPath("$.message").value("error.invalid"));

        IngestionRecord unchanged = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(IngestionRecordStatus.DISABLED);
    }

    @Test
    @Transactional
    void editRejectsImportedSkippedFailedForeignAndMismatchedRows() throws Exception {
        TransactionIngestion firstIngestion = createPreviewWithValidRows();
        IngestionRecord importedRecord = recordsFor(firstIngestion).get(0);
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(firstIngestion.getAccount());
        financialTransaction.setTransactionIngestion(firstIngestion);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        importedRecord.setStatus(IngestionRecordStatus.IMPORTED);
        importedRecord.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(importedRecord);

        mockMvc
            .perform(
                patch(reviewUrl(firstIngestion, importedRecord, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());

        TransactionIngestion secondIngestion = createPreviewWithValidRows();
        IngestionRecord skippedRecord = recordsFor(secondIngestion).get(0);
        skippedRecord.setStatus(IngestionRecordStatus.SKIPPED_DUPLICATE);
        ingestionRecordRepository.saveAndFlush(skippedRecord);
        mockMvc
            .perform(
                patch(reviewUrl(secondIngestion, skippedRecord, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());

        IngestionRecord failedRecord = recordsFor(secondIngestion).get(1);
        failedRecord.setStatus(IngestionRecordStatus.FAILED);
        failedRecord.setErrorCode("FAILED");
        failedRecord.setErrorMessage("failed");
        ingestionRecordRepository.saveAndFlush(failedRecord);
        mockMvc
            .perform(
                patch(reviewUrl(secondIngestion, failedRecord, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());

        IngestionRecord mismatchedRecord = recordsFor(secondIngestion).get(2);
        mockMvc
            .perform(
                patch(reviewUrl(firstIngestion, mismatchedRecord, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void editForeignAccountRecordIsRejected() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());
        TransactionIngestion ingestion = TransactionIngestionResourceIT.createEntity(em);
        ingestion.setAccount(otherUsersAccount);
        ingestion.setIngestionType(IngestionType.FILE);
        ingestion = transactionIngestionRepository.saveAndFlush(ingestion);

        IngestionRecord record = validRecordFor(ingestion, 1);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void importedRowsAndMismatchedRecordsCannotBeReviewed() throws Exception {
        TransactionIngestion firstIngestion = createPreviewWithValidRows();
        IngestionRecord importedRecord = recordsFor(firstIngestion).get(0);
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(firstIngestion.getAccount());
        financialTransaction.setTransactionIngestion(firstIngestion);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        importedRecord.setStatus(IngestionRecordStatus.IMPORTED);
        importedRecord.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(importedRecord);

        mockMvc.perform(post(reviewUrl(firstIngestion, importedRecord, "disable"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(reviewUrl(firstIngestion, importedRecord, "enable"))).andExpect(status().isBadRequest());

        TransactionIngestion secondIngestion = createPreviewWithValidRows();
        IngestionRecord secondRecord = recordsFor(secondIngestion).get(0);

        mockMvc.perform(post(reviewUrl(firstIngestion, secondRecord, "disable"))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void confirmReadyIngestionCreatesFinancialTransactionsFromNormalizedRows() throws Exception {
        TransactionIngestion ingestion = createPreviewWithValidRows();
        IngestionRecord disabledRecord = recordsFor(ingestion).get(0);
        JsonNode originalRaw = objectMapper.readTree(disabledRecord.getRawData()).path("raw");
        mockMvc.perform(post(reviewUrl(ingestion, disabledRecord, "disable"))).andExpect(status().isOk());

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(2))
            .andExpect(jsonPath("$.alreadyImported").value(0))
            .andExpect(jsonPath("$.skipped").value(1))
            .andExpect(jsonPath("$.rejected").value(0))
            .andExpect(jsonPath("$.failed").value(0))
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.recordsCreated").value(2))
            .andExpect(jsonPath("$.counts.recordsSkipped").value(1))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0))
            .andExpect(jsonPath("$.counts.validRows").value(0))
            .andExpect(jsonPath("$.rows[0].status").value("DISABLED"))
            .andExpect(jsonPath("$.rows[1].status").value("IMPORTED"))
            .andExpect(jsonPath("$.rows[1].financialTransactionId").exists())
            .andExpect(jsonPath("$.rows[2].status").value("IMPORTED"))
            .andExpect(jsonPath("$.rows[2].financialTransactionId").exists());

        TransactionIngestion completed = transactionIngestionRepository.findById(ingestion.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(completed.getRecordsCreated()).isEqualTo(2);
        assertThat(completed.getRecordsSkipped()).isEqualTo(1);
        assertThat(completed.getRecordsRejected()).isZero();
        assertThat(completed.getCompletedAt()).isNotNull();

        List<IngestionRecord> records = recordsFor(completed);
        assertThat(records.get(0).getStatus()).isEqualTo(IngestionRecordStatus.DISABLED);
        assertThat(objectMapper.readTree(records.get(0).getRawData()).path("raw")).isEqualTo(originalRaw);
        assertThat(records.get(1).getStatus()).isEqualTo(IngestionRecordStatus.IMPORTED);
        assertThat(records.get(1).getFinancialTransaction()).isNotNull();
        assertThat(records.get(1).getErrorCode()).isNull();
        assertThat(records.get(1).getErrorMessage()).isNull();

        List<FinancialTransaction> financialTransactions = financialTransactionRepository.findAll();
        assertThat(financialTransactions).hasSize(2);
        FinancialTransaction oxxo = financialTransactions
            .stream()
            .filter(transaction -> transaction.getDescription().equals("OXXO AGUILAS"))
            .findFirst()
            .orElseThrow();
        assertThat(oxxo.getTransactionDate()).isEqualTo(LocalDate.parse("2026-01-16"));
        assertThat(oxxo.getPostingDate()).isNull();
        assertThat(oxxo.getAmount()).isEqualByComparingTo("274.00");
        assertThat(oxxo.getFlow().name()).isEqualTo("OUT");
        assertThat(oxxo.getOrigin().name()).isEqualTo("FILE_IMPORT");
        assertThat(oxxo.getTransactionIngestion().getId()).isEqualTo(ingestion.getId());
        assertThat(oxxo.getAccount().getId()).isEqualTo(ingestion.getAccount().getId());
        assertThat(oxxo.getCategory()).isNull();
        assertThat(oxxo.getFinancialSubscription()).isNull();
        assertThat(oxxo.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void confirmCompletedIngestionIsIdempotent() throws Exception {
        TransactionIngestion ingestion = createPreviewWithSingleValidRow();

        mockMvc.perform(post(confirmUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.createdNow").value(1));
        long financialTransactionCountAfterFirstConfirm = financialTransactionRepository.count();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(0))
            .andExpect(jsonPath("$.alreadyImported").value(1));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountAfterFirstConfirm);
    }

    @Test
    @Transactional
    void confirmRecalculatesReadinessAndRejectsStaleReadyWithRejectedRows() throws Exception {
        TransactionIngestion ingestion = createPreviewWithInvalidRow();
        ingestion.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(ingestion);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.detail").value(
                    "Cannot confirm import because ingestion is not ready. Fix or disable rejected rows and ensure at least one valid row exists."
                )
            );

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(
            IngestionStatus.PARTIALLY_READY
        );
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void confirmRecalculatesStalePartiallyReadyToReadyAndImports() throws Exception {
        TransactionIngestion ingestion = createPreviewWithSingleValidRow();
        ingestion.setStatus(IngestionStatus.PARTIALLY_READY);
        transactionIngestionRepository.saveAndFlush(ingestion);

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        assertThat(financialTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void confirmRejectsNotReadyAndDisallowedStatusesWithoutImporting() throws Exception {
        TransactionIngestion partiallyReady = createPreviewWithInvalidRow();
        mockMvc.perform(post(confirmUrl(partiallyReady))).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();

        TransactionIngestion noValidRows = createPreviewWithSingleValidRow();
        mockMvc.perform(post(reviewUrl(noValidRows, recordsFor(noValidRows).get(0), "disable"))).andExpect(status().isOk());
        mockMvc.perform(post(confirmUrl(noValidRows))).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();

        for (IngestionStatus status : List.of(
            IngestionStatus.PENDING,
            IngestionStatus.PROCESSING,
            IngestionStatus.FAILED,
            IngestionStatus.PARTIALLY_COMPLETED
        )) {
            TransactionIngestion ingestion = createPreviewWithSingleValidRow();
            ingestion.setStatus(status);
            transactionIngestionRepository.saveAndFlush(ingestion);
            mockMvc.perform(post(confirmUrl(ingestion))).andExpect(status().isBadRequest());
        }

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void confirmRejectsCorruptFinancialTransactionLinks() throws Exception {
        TransactionIngestion importedWithoutTransaction = createPreviewWithSingleValidRow();
        IngestionRecord importedRecord = recordsFor(importedWithoutTransaction).get(0);
        importedRecord.setStatus(IngestionRecordStatus.IMPORTED);
        importedRecord.setFinancialTransaction(null);
        ingestionRecordRepository.saveAndFlush(importedRecord);
        importedWithoutTransaction.setStatus(IngestionStatus.COMPLETED);
        transactionIngestionRepository.saveAndFlush(importedWithoutTransaction);

        mockMvc.perform(post(confirmUrl(importedWithoutTransaction))).andExpect(status().isBadRequest());

        TransactionIngestion validWithTransaction = createPreviewWithSingleValidRow();
        IngestionRecord validRecord = recordsFor(validWithTransaction).get(0);
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(validWithTransaction.getAccount());
        financialTransaction.setTransactionIngestion(validWithTransaction);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        validRecord.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(validRecord);

        mockMvc.perform(post(confirmUrl(validWithTransaction))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void completedIngestionRowsCannotBeReviewed() throws Exception {
        TransactionIngestion ingestion = createPreviewWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(confirmUrl(ingestion))).andExpect(status().isOk());

        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(reviewUrl(ingestion, record, "enable"))).andExpect(status().isBadRequest());
        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Edit", "10.00", "MXN", null, null)))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void confirmForeignIngestionIsRejected() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());
        TransactionIngestion ingestion = TransactionIngestionResourceIT.createEntity(em);
        ingestion.setAccount(otherUsersAccount);
        ingestion.setIngestionType(IngestionType.FILE);
        ingestion.setStatus(IngestionStatus.READY);
        ingestion = transactionIngestionRepository.saveAndFlush(ingestion);

        mockMvc.perform(post(confirmUrl(ingestion))).andExpect(status().isBadRequest());
    }

    private FinancialAccount createCurrentUserAccount() {
        return createAccountForUser(currentMockUser());
    }

    private TransactionIngestion createPreviewWithValidRows() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk());
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private TransactionIngestion createPreviewWithSingleValidRow() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;
        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("single-valid.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private TransactionIngestion createPreviewWithInvalidRow() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            nope,,,-0.001,USD,,
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;
        mockMvc
            .perform(multipart(FILE_PREVIEW_URL).file(csvFile("mixed.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk());
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private String reviewUrl(TransactionIngestion ingestion, IngestionRecord record, String action) {
        String url = "/api/transaction-ingestions/" + ingestion.getId() + "/records/" + record.getId();
        return action == null ? url : url + "/" + action;
    }

    private String confirmUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/confirm";
    }

    private Map<String, Object> reviewPayload(
        String transactionDate,
        String postingDate,
        String description,
        String signedAmount,
        String currency,
        String externalReference,
        String notes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionDate", transactionDate);
        payload.put("postingDate", postingDate);
        payload.put("description", description);
        payload.put("signedAmount", signedAmount);
        payload.put("currency", currency);
        payload.put("externalReference", externalReference);
        payload.put("notes", notes);
        payload.put("amount", "999999.99");
        payload.put("flow", "IN");
        payload.put("status", "IMPORTED");
        return payload;
    }

    private IngestionRecord validRecordFor(TransactionIngestion ingestion, int recordIndex) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("transactionDate", "2026-01-16");
        row.put("postingDate", null);
        row.put("description", "OXXO AGUILAS");
        row.put("signedAmount", "-274.00");
        row.put("amount", "274.00");
        row.put("flow", "OUT");
        row.put("currency", "MXN");
        row.put("externalReference", null);
        row.put("notes", null);

        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("raw", row);
        rawData.put("normalized", row);
        rawData.put("errors", List.of());
        rawData.put("warnings", List.of());

        IngestionRecord record = new IngestionRecord()
            .recordIndex(recordIndex)
            .status(IngestionRecordStatus.VALID)
            .rawData(objectMapper.writeValueAsString(rawData))
            .createdAt(java.time.Instant.now())
            .transactionIngestion(ingestion);
        return ingestionRecordRepository.saveAndFlush(record);
    }

    private FinancialAccount createAccountForUser(User user) {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account.setUser(user);
        return financialAccountRepository.saveAndFlush(account);
    }

    private User currentMockUser() {
        return em
            .createQuery("select user from User user where user.login = :login", User.class)
            .setParameter("login", "user")
            .getSingleResult();
    }

    private User createOtherUser() {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private MockMultipartFile csvFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private List<IngestionRecord> recordsFor(TransactionIngestion ingestion) {
        return ingestionRecordRepository
            .findAll()
            .stream()
            .filter(record -> record.getTransactionIngestion().getId().equals(ingestion.getId()))
            .sorted(Comparator.comparing(IngestionRecord::getRecordIndex))
            .toList();
    }

    private void assertNothingCreated() {
        assertThat(transactionIngestionRepository.findAll()).isEmpty();
        assertThat(fileIngestionRepository.findAll()).isEmpty();
        assertThat(ingestionRecordRepository.findAll()).isEmpty();
    }

    private String sha256Hex(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
