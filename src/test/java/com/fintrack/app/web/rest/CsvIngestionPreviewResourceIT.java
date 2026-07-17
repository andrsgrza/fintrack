package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
            .andExpect(jsonPath("$.status").value("COMPLETED"))
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
        assertThat(ingestion.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
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
            assertThat(record.getStatus()).isEqualTo(IngestionRecordStatus.CREATED);
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
            .andExpect(jsonPath("$.status").value("PARTIALLY_COMPLETED"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(2))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1))
            .andExpect(jsonPath("$.rows[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.rows[0].errorCode").value("INVALID_TRANSACTION_DATE"))
            .andExpect(jsonPath("$.rows[1].status").value("CREATED"));

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

    private FinancialAccount createCurrentUserAccount() {
        return createAccountForUser(currentMockUser());
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
