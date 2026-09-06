package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleConditionRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class TransactionIngestionWorkflowResourceIT {

    private static final String FILE_WORKFLOW_URL = "/api/transaction-ingestions/file";
    private static final String PARENT_FILE_INGESTION_URL = "/api/transaction-ingestions/{id}/file-ingestion";

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

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TransactionRuleRepository transactionRuleRepository;

    @Autowired
    private TransactionRuleConditionRepository transactionRuleConditionRepository;

    @Autowired
    private TransactionCandidateRepository transactionCandidateRepository;

    @Autowired
    private DescriptionNormalizationRuleRepository descriptionNormalizationRuleRepository;

    @Autowired
    private DescriptionNormalizationRuleConditionRepository descriptionNormalizationRuleConditionRepository;

    @Test
    @Transactional
    void validCsvUploadCreatesPersistedWorkflow() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
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
    void uploadCsvWithMatchingOriginalDescriptionAppliesDescriptionNormalizationRule() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(rule, "Uber");
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[2].description").value("Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.originalDescription").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.normalizedDescription").value("Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.ruleId").value(rule.getId()))
            .andExpect(jsonPath("$.rows[2].descriptionReview.ruleName").value("Normalize Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.resultingDescription").value("Uber"));

        TransactionIngestion ingestion = transactionIngestionRepository
            .findAll()
            .stream()
            .max(Comparator.comparing(TransactionIngestion::getId))
            .orElseThrow();
        IngestionRecord record = recordsFor(ingestion).get(2);
        JsonNode rawData = objectMapper.readTree(record.getRawData());

        assertThat(rawData.path("raw").path("description").asText()).isEqualTo("Uber, Trip");
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Uber");
        assertThat(rawData.path("review").path("description").path("source").asText()).isEqualTo("DESCRIPTION_RULE");
        assertThat(rawData.path("review").path("description").path("ruleId").asLong()).isEqualTo(rule.getId());
        assertThat(rawData.path("review").path("description").path("ruleName").asText()).isEqualTo("Normalize Uber");
        assertThat(rawData.path("review").path("description").path("resultingDescription").asText()).isEqualTo("Uber");
        assertThat(rawData.path("review").path("description").has("originalDescription")).isFalse();
        assertThat(rawData.has("suggestions")).isFalse();
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void uploadCsvWithoutMatchingDescriptionRuleKeepsParserNormalizedDescription() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Lyft", "Lyft");
        persistDescriptionNormalizationCondition(rule, "Lyft");

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[2].description").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.source").doesNotExist())
            .andExpect(jsonPath("$.rows[2].descriptionReview.originalDescription").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.normalizedDescription").value("Uber, Trip"));

        TransactionIngestion ingestion = transactionIngestionRepository
            .findAll()
            .stream()
            .max(Comparator.comparing(TransactionIngestion::getId))
            .orElseThrow();
        JsonNode rawData = objectMapper.readTree(recordsFor(ingestion).get(2).getRawData());
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Uber, Trip");
        assertThat(rawData.path("review").path("description").isMissingNode()).isTrue();
        assertThat(rawData.has("suggestions")).isFalse();
    }

    @Test
    @Transactional
    void rowEditSetsDescriptionReviewSourceUserEdit() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-20", null, "Manual description", "-274.00", "MXN", null, null)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.description").value("Manual description"))
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"))
            .andExpect(jsonPath("$.row.descriptionReview.originalDescription").value("NOMINA QUALTRICS"))
            .andExpect(jsonPath("$.row.descriptionReview.normalizedDescription").value("Manual description"))
            .andExpect(jsonPath("$.row.descriptionReview.resultingDescription").value("Manual description"))
            .andExpect(jsonPath("$.row.descriptionReview.editedBy").value("user"));

        JsonNode rawData = objectMapper.readTree(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData());
        assertThat(rawData.path("review").path("description").path("source").asText()).isEqualTo("USER_EDIT");
        assertThat(rawData.path("review").path("description").path("resultingDescription").asText()).isEqualTo("Manual description");
        assertThat(rawData.path("review").path("description").path("editedBy").asText()).isEqualTo("user");
    }

    @Test
    @Transactional
    void workflowRowDescriptionReviewIgnoresInvalidEditedAt() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(2);
        ObjectNode rawData = (ObjectNode) objectMapper.readTree(record.getRawData());
        ObjectNode review = rawData.has("review") && rawData.get("review").isObject()
            ? (ObjectNode) rawData.get("review")
            : rawData.putObject("review");
        ObjectNode descriptionReview = review.putObject("description");
        descriptionReview.put("source", "DESCRIPTION_RULE");
        descriptionReview.put("ruleName", "Normalize Uber");
        descriptionReview.put("resultingDescription", "Uber");
        descriptionReview.put("editedAt", "not-an-iso-instant");

        record.setRawData(objectMapper.writeValueAsString(rawData));
        ingestionRecordRepository.saveAndFlush(record);

        mockMvc
            .perform(get("/api/transaction-ingestions/" + ingestion.getId() + "/workflow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[2].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.ruleName").value("Normalize Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.originalDescription").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.normalizedDescription").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.editedAt").doesNotExist());
    }

    @Test
    @Transactional
    void secondaryAttachFileEndpointAppliesDescriptionNormalizationRule() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(rule, "Uber");
        TransactionIngestion pending = createPendingFileTransactionIngestion(account);

        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, pending.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[2].description").value("Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.originalDescription").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.normalizedDescription").value("Uber"))
            .andExpect(jsonPath("$.rows[2].descriptionReview.ruleName").value("Normalize Uber"));

        JsonNode rawData = objectMapper.readTree(recordsFor(pending).get(2).getRawData());
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Uber");
        assertThat(rawData.path("review").path("description").path("source").asText()).isEqualTo("DESCRIPTION_RULE");
    }

    @Test
    @Transactional
    void canonicalFileWorkflowCreatesParentFileMetadataAndRecords() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").exists())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.fileMetadata.originalFilename").value("canonical.csv"))
            .andExpect(jsonPath("$.fileMetadata.storageKey").doesNotExist())
            .andExpect(jsonPath("$.fileMetadata.createdAt").exists())
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.recordsCreated").value(0))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0));

        TransactionIngestion ingestion = transactionIngestionRepository
            .findAll()
            .stream()
            .max(Comparator.comparing(TransactionIngestion::getId))
            .orElseThrow();
        assertThat(ingestion.getIngestionType()).isEqualTo(IngestionType.FILE);
        assertThat(ingestion.getStatus()).isEqualTo(IngestionStatus.READY);
        assertThat(ingestion.getAccount().getId()).isEqualTo(account.getId());
        assertThat(fileIngestionRepository.findOneByTransactionIngestionId(ingestion.getId())).isPresent();
        assertThat(recordsFor(ingestion)).hasSize(3);
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void canonicalFileWorkflowWithInvalidRowsReturnsPartiallyReady() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            nope,,,-0.001,USD,,
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("mixed.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(2))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1));

        TransactionIngestion ingestion = transactionIngestionRepository
            .findAll()
            .stream()
            .max(Comparator.comparing(TransactionIngestion::getId))
            .orElseThrow();
        assertThat(ingestion.getStatus()).isEqualTo(IngestionStatus.PARTIALLY_READY);
        assertThat(recordsFor(ingestion))
            .extracting(IngestionRecord::getStatus)
            .containsExactly(IngestionRecordStatus.REJECTED, IngestionRecordStatus.VALID);
    }

    @Test
    @Transactional
    void canonicalFileWorkflowRejectsMissingAccountAndMissingFileWithoutPersisting() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc.perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV))).andExpect(status().isBadRequest());
        assertNothingCreated();

        mockMvc.perform(multipart(FILE_WORKFLOW_URL).param("accountId", account.getId().toString())).andExpect(status().isBadRequest());
        assertNothingCreated();
    }

    @Test
    @Transactional
    void canonicalFileWorkflowRejectsInvalidHeaderWithoutPersisting() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(
                multipart(FILE_WORKFLOW_URL)
                    .file(csvFile("invalid.csv", "transactionDate,description,signedAmount,currency\n2026-01-15,Coffee,-10.00,MXN"))
                    .param("accountId", account.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void canonicalFileWorkflowRejectsInvalidHeaderVariantsWithoutPersisting() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        List<String> invalidHeaderCsvs = List.of(
            "transactionDate,postingDate,description,signedAmount,currency,externalReference\n",
            "transactionDate,postingDate,description,signedAmount,currency,externalReference,notes,extra\n",
            "postingDate,transactionDate,description,signedAmount,currency,externalReference,notes\n",
            "transactiondate,postingDate,description,signedAmount,currency,externalReference,notes\n"
        );

        for (String invalidHeaderCsv : invalidHeaderCsvs) {
            mockMvc
                .perform(
                    multipart(FILE_WORKFLOW_URL)
                        .file(csvFile("invalid-header.csv", invalidHeaderCsv + "2026-01-16,,Coffee,-10.00,MXN,,"))
                        .param("accountId", account.getId().toString())
                )
                .andExpect(status().isBadRequest());

            assertNothingCreated();
        }
    }

    @Test
    @Transactional
    void canonicalFileWorkflowRejectsInaccessibleAccountWithoutPersisting() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());

        mockMvc
            .perform(
                multipart(FILE_WORKFLOW_URL)
                    .file(csvFile("canonical.csv", VALID_CSV))
                    .param("accountId", otherUsersAccount.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void legacyFileWorkflowCreateEndpointIsNotMapped() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(
                multipart(legacyFileWorkflowUrl()).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString())
            )
            .andExpect(status().isMethodNotAllowed());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void legacyFileWorkflowReadEndpointIsNotMapped() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();

        mockMvc.perform(get(legacyFileWorkflowUrl(ingestion.getId()))).andExpect(status().isNotFound());
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
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("mixed.csv", csv)).param("accountId", account.getId().toString()))
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
                multipart(FILE_WORKFLOW_URL)
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

        mockMvc.perform(multipart(FILE_WORKFLOW_URL).param("accountId", account.getId().toString())).andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void emptyFileCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("empty.csv", "")).param("accountId", account.getId().toString()))
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void headerOnlyFileCreatesNothing() throws Exception {
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(
                multipart(FILE_WORKFLOW_URL)
                    .file(csvFile("header.csv", "transactionDate,postingDate,description,signedAmount,currency,externalReference,notes\n"))
                    .param("accountId", account.getId().toString())
            )
            .andExpect(status().isBadRequest());

        assertNothingCreated();
    }

    @Test
    @Transactional
    void uploadFileToPendingFileTransactionIngestionCreatesMetadataRecordsAndReadyParent() throws Exception {
        TransactionIngestion parent = createPendingFileTransactionIngestion(createCurrentUserAccount());
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, parent.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(parent.getId()))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.fileMetadata.originalFilename").value("canonical.csv"))
            .andExpect(jsonPath("$.fileMetadata.fileType").value("CSV"))
            .andExpect(jsonPath("$.fileMetadata.storageKey").doesNotExist())
            .andExpect(jsonPath("$.fileMetadata.createdAt").exists())
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.recordsCreated").value(0))
            .andExpect(jsonPath("$.counts.recordsRejected").value(0))
            .andExpect(jsonPath("$.counts.validRows").value(3));

        TransactionIngestion updatedParent = transactionIngestionRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(IngestionStatus.READY);
        assertThat(updatedParent.getSourceLabel()).isEqualTo("Canonical CSV: canonical.csv");
        assertThat(updatedParent.getRecordsReceived()).isEqualTo(3);
        assertThat(updatedParent.getRecordsCreated()).isZero();
        assertThat(updatedParent.getRecordsSkipped()).isZero();
        assertThat(updatedParent.getRecordsRejected()).isZero();
        assertThat(updatedParent.getStartedAt()).isNotNull();
        assertThat(updatedParent.getCompletedAt()).isNotNull();

        FileIngestion fileIngestion = fileIngestionRepository.findAll().get(0);
        assertThat(fileIngestion.getTransactionIngestion().getId()).isEqualTo(parent.getId());
        assertThat(fileIngestion.getChecksum()).isEqualTo(sha256Hex(VALID_CSV));
        assertThat(fileIngestion.getStorageKey()).isNull();
        assertThat(fileIngestion.getParserName()).isEqualTo("fintrack-canonical-csv");
        assertThat(fileIngestion.getParserVersion()).isEqualTo("1.0");
        assertThat(fileIngestion.getStatementStartDate()).isEqualTo(LocalDate.parse("2026-01-15"));
        assertThat(recordsFor(parent)).hasSize(3);
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void uploadFileToPendingParentWithInvalidRowsMakesParentPartiallyReady() throws Exception {
        TransactionIngestion parent = createPendingFileTransactionIngestion(createCurrentUserAccount());
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            nope,,,-0.001,USD,,
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;

        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, parent.getId()).file(csvFile("mixed.csv", csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_READY"))
            .andExpect(jsonPath("$.counts.recordsReceived").value(2))
            .andExpect(jsonPath("$.counts.recordsRejected").value(1))
            .andExpect(jsonPath("$.rows[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.rows[1].status").value("VALID"));

        TransactionIngestion updatedParent = transactionIngestionRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(IngestionStatus.PARTIALLY_READY);
        assertThat(updatedParent.getRecordsRejected()).isEqualTo(1);
        assertThat(recordsFor(parent))
            .extracting(IngestionRecord::getStatus)
            .containsExactly(IngestionRecordStatus.REJECTED, IngestionRecordStatus.VALID);
    }

    @Test
    @Transactional
    void uploadFileToParentRejectsNonFileForeignNonPendingAndMissingFile() throws Exception {
        TransactionIngestion nonFileParent = createPendingFileTransactionIngestion(createCurrentUserAccount());
        nonFileParent.setIngestionType(IngestionType.API);
        transactionIngestionRepository.saveAndFlush(nonFileParent);
        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, nonFileParent.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        TransactionIngestion nonPendingParent = createPendingFileTransactionIngestion(createCurrentUserAccount());
        nonPendingParent.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(nonPendingParent);
        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, nonPendingParent.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        TransactionIngestion foreignParent = createPendingFileTransactionIngestion(createAccountForUser(createOtherUser()));
        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, foreignParent.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        TransactionIngestion missingFileParent = createPendingFileTransactionIngestion(createCurrentUserAccount());
        mockMvc.perform(multipart(PARENT_FILE_INGESTION_URL, missingFileParent.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void adminCannotUploadFileToForeignPendingTransactionIngestion() throws Exception {
        TransactionIngestion foreignParent = createPendingFileTransactionIngestion(createAccountForUser(createOtherUser()));

        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, foreignParent.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        assertThat(fileIngestionRepository.findAll()).isEmpty();
        assertThat(ingestionRecordRepository.findAll()).isEmpty();
    }

    @Test
    @Transactional
    void uploadFileToParentRejectsExistingChildrenAndCreatedFinancialTransactions() throws Exception {
        TransactionIngestion parentWithFile = createPendingFileTransactionIngestion(createCurrentUserAccount());
        fileIngestionRepository.saveAndFlush(
            new FileIngestion()
                .originalFilename("existing.csv")
                .fileType(ImportFileType.CSV)
                .createdAt(java.time.Instant.now())
                .transactionIngestion(parentWithFile)
        );
        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, parentWithFile.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        TransactionIngestion parentWithRecords = createPendingFileTransactionIngestion(createCurrentUserAccount());
        validRecordFor(parentWithRecords, 1);
        mockMvc
            .perform(multipart(PARENT_FILE_INGESTION_URL, parentWithRecords.getId()).file(csvFile("canonical.csv", VALID_CSV)))
            .andExpect(status().isBadRequest());

        TransactionIngestion parentWithFinancialTransactions = createPendingFileTransactionIngestion(createCurrentUserAccount());
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(parentWithFinancialTransactions.getAccount());
        financialTransaction.setTransactionIngestion(parentWithFinancialTransactions);
        financialTransactionRepository.saveAndFlush(financialTransaction);
        mockMvc
            .perform(
                multipart(PARENT_FILE_INGESTION_URL, parentWithFinancialTransactions.getId()).file(csvFile("canonical.csv", VALID_CSV))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void inaccessibleAccountRejectedAndCreatesNothing() throws Exception {
        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());

        mockMvc
            .perform(
                multipart(FILE_WORKFLOW_URL)
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
                multipart(FILE_WORKFLOW_URL)
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
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("first.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warnings").isEmpty());

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("second.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warnings[0].code").value("DUPLICATE_FILE_CHECKSUM"));

        assertThat(transactionIngestionRepository.findAll()).hasSize(2);
        assertThat(fileIngestionRepository.findAll()).hasSize(2);
    }

    @Test
    @Transactional
    void getPersistedWorkflowReturnsMetadataCountsAndRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();

        mockMvc
            .perform(get("/api/transaction-ingestions/" + ingestion.getId() + "/workflow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.fileIngestionId").exists())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.fileMetadata.originalFilename").value("canonical.csv"))
            .andExpect(jsonPath("$.fileMetadata.fileType").value("CSV"))
            .andExpect(jsonPath("$.fileMetadata.parserName").value("fintrack-canonical-csv"))
            .andExpect(jsonPath("$.fileMetadata.storageKey").doesNotExist())
            .andExpect(jsonPath("$.fileMetadata.createdAt").exists())
            .andExpect(jsonPath("$.counts.recordsReceived").value(3))
            .andExpect(jsonPath("$.counts.validRows").value(3))
            .andExpect(jsonPath("$.rows[0].status").value("VALID"))
            .andExpect(jsonPath("$.rows[0].financialTransaction").doesNotExist());

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void disableValidRowMarksDisabledAndRecalculatesCounters() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
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
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
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
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
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
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isOk());

        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void enableDisabledRejectedRowRevalidatesCurrentNormalizedValues() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
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
        TransactionIngestion ingestion = createWorkflowWithValidRows();
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
        TransactionIngestion ingestion = createWorkflowWithValidRows();
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
        TransactionIngestion ingestion = createWorkflowWithValidRows();
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
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
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
        TransactionIngestion ingestion = createWorkflowWithValidRows();
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
        TransactionIngestion firstIngestion = createWorkflowWithValidRows();
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

        TransactionIngestion secondIngestion = createWorkflowWithValidRows();
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
        TransactionIngestion firstIngestion = createWorkflowWithValidRows();
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

        TransactionIngestion secondIngestion = createWorkflowWithValidRows();
        IngestionRecord secondRecord = recordsFor(secondIngestion).get(0);

        mockMvc.perform(post(reviewUrl(firstIngestion, secondRecord, "disable"))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void prepareCandidatesCreatesFileImportCandidatesForValidRecordsOnly() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(rule, "Uber");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        List<IngestionRecord> records = recordsFor(ingestion);
        List<String> rawDataBefore = records.stream().map(IngestionRecord::getRawData).toList();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.createdCount").value(3))
            .andExpect(jsonPath("$.updatedCount").value(0))
            .andExpect(jsonPath("$.unchangedCount").value(0))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.rows[0].action").value("CREATED"))
            .andExpect(jsonPath("$.rows[2].action").value("CREATED"));

        List<TransactionCandidate> candidates = transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
            ingestion.getId(),
            "user"
        );
        assertThat(candidates).hasSize(3);
        TransactionCandidate uberCandidate = candidateForRecord(records.get(2));
        assertThat(uberCandidate.getSource()).isEqualTo(TransactionCandidateSource.FILE_IMPORT);
        assertThat(uberCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
        assertThat(uberCandidate.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.VALID);
        assertThat(uberCandidate.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        assertThat(uberCandidate.getDescriptionReviewStatus()).isEqualTo(TransactionCandidateDescriptionReviewStatus.AUTO_APPLIED);
        assertThat(uberCandidate.getUser().getLogin()).isEqualTo("user");
        assertThat(uberCandidate.getAccount().getId()).isEqualTo(ingestion.getAccount().getId());
        assertThat(uberCandidate.getTransactionIngestion().getId()).isEqualTo(ingestion.getId());
        assertThat(uberCandidate.getIngestionRecord().getId()).isEqualTo(records.get(2).getId());
        assertThat(uberCandidate.getFinancialTransaction()).isNull();
        assertThat(uberCandidate.getTransactionDate()).isEqualTo(LocalDate.parse("2026-01-17"));
        assertThat(uberCandidate.getPostingDate()).isEqualTo(LocalDate.parse("2026-01-18"));
        assertThat(uberCandidate.getDescription()).isEqualTo("Uber");
        assertThat(uberCandidate.getSignedAmount()).isEqualByComparingTo("-158.33");
        assertThat(uberCandidate.getAmount()).isEqualByComparingTo("158.33");
        assertThat(uberCandidate.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(uberCandidate.getCurrencySnapshot()).isEqualTo(ingestion.getAccount().getCurrency());
        assertThat(uberCandidate.getExternalReference()).isEqualTo("abc-123");
        assertThat(uberCandidate.getNotes()).isEqualTo("quoted, note");
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(recordsFor(ingestion).stream().map(IngestionRecord::getRawData).toList()).isEqualTo(rawDataBefore);
    }

    @Test
    @Transactional
    void prepareCandidatesSkipsEveryNonValidRecordStatus() throws Exception {
        TransactionIngestion ingestion = createPendingFileTransactionIngestion(createCurrentUserAccount());
        ingestion.setStatus(IngestionStatus.PARTIALLY_READY);
        transactionIngestionRepository.saveAndFlush(ingestion);
        IngestionRecord valid = validRecordFor(ingestion, 1);
        IngestionRecord rejected = validRecordFor(ingestion, 2);
        rejected.setStatus(IngestionRecordStatus.REJECTED);
        IngestionRecord disabled = validRecordFor(ingestion, 3);
        disabled.setStatus(IngestionRecordStatus.DISABLED);
        IngestionRecord imported = validRecordFor(ingestion, 4);
        imported.setStatus(IngestionRecordStatus.IMPORTED);
        IngestionRecord skipped = validRecordFor(ingestion, 5);
        skipped.setStatus(IngestionRecordStatus.SKIPPED_DUPLICATE);
        IngestionRecord failed = validRecordFor(ingestion, 6);
        failed.setStatus(IngestionRecordStatus.FAILED);
        ingestionRecordRepository.saveAllAndFlush(List.of(rejected, disabled, imported, skipped, failed));

        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(1))
            .andExpect(jsonPath("$.skippedCount").value(5))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.rows[0].ingestionRecordId").value(valid.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("CREATED"))
            .andExpect(jsonPath("$.rows[1].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[2].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[3].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[4].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[5].action").value("SKIPPED"));

        assertThat(transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user"))
            .hasSize(1)
            .first()
            .extracting(candidate -> candidate.getIngestionRecord().getId())
            .isEqualTo(valid.getId());
    }

    @Test
    @Transactional
    void prepareCandidatesIsIdempotentAndReportsUnchangedRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();

        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(3));
        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.updatedCount").value(0))
            .andExpect(jsonPath("$.unchangedCount").value(3))
            .andExpect(jsonPath("$.skippedCount").value(0))
            .andExpect(jsonPath("$.errorCount").value(0));

        assertThat(
            transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
        ).hasSize(3);
    }

    @Test
    @Transactional
    void prepareCandidatesSyncsChangedNormalizedFieldsMarksClassificationStaleAndPreservesOutputs() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Reviewed", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(category);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        candidate = transactionCandidateRepository.saveAndFlush(candidate);
        Instant updatedAtBefore = candidate.getUpdatedAt();
        setNormalizedFields(record, "2026-02-01", null, "Edited Uber ride", "-42.50", "MXN", "new-ref", "new notes");

        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.updatedCount").value(1))
            .andExpect(jsonPath("$.unchangedCount").value(2));

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getTransactionDate()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(synced.getDescription()).isEqualTo("Edited Uber ride");
        assertThat(synced.getSignedAmount()).isEqualByComparingTo("-42.50");
        assertThat(synced.getAmount()).isEqualByComparingTo("42.50");
        assertThat(synced.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(synced.getExternalReference()).isEqualTo("new-ref");
        assertThat(synced.getNotes()).isEqualTo("new notes");
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.STALE);
        assertThat(synced.getCategory().getId()).isEqualTo(category.getId());
        assertThat(synced.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        assertThat(synced.getUpdatedAt()).isAfter(updatedAtBefore);
    }

    @Test
    @Transactional
    void prepareCandidatesDoesNotMarkStaleForNotesOnlyChange() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);
        setNormalizedFields(record, "2026-01-15", null, "NOMINA QUALTRICS", "33698.34", "MXN", null, "only notes changed");

        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(1));

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getNotes()).isEqualTo("only notes changed");
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    @Transactional
    void prepareCandidatesDoesNotModifyPostedCandidateAndCreatesNoFinancialTransactions() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(ingestion.getAccount());
        financialTransaction.setTransactionIngestion(ingestion);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        candidate.setStatus(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(financialTransaction);
        candidate.setPostedAt(Instant.now());
        candidate.setDescription("Already posted");
        transactionCandidateRepository.saveAndFlush(candidate);
        long financialTransactionCountBefore = financialTransactionRepository.count();
        setNormalizedFields(record, "2026-02-01", null, "Should not sync", "-42.50", "MXN", null, null);

        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.updatedCount").value(0))
            .andExpect(jsonPath("$.skippedCount").value(1))
            .andExpect(jsonPath("$.rows[0].reason").value("Posted candidate not modified"));

        TransactionCandidate unchanged = candidateForRecord(record);
        assertThat(unchanged.getDescription()).isEqualTo("Already posted");
        assertThat(unchanged.getFinancialTransaction().getId()).isEqualTo(financialTransaction.getId());
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void prepareCandidatesRejectsForeignNonFileCompletedAndNoValidRows() throws Exception {
        TransactionIngestion foreign = createPendingFileTransactionIngestion(createAccountForUser(createOtherUser()));
        foreign.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(foreign);
        validRecordFor(foreign, 1);
        mockMvc.perform(post(prepareCandidatesUrl(foreign))).andExpect(status().isBadRequest());

        TransactionIngestion nonFile = createPendingFileTransactionIngestion(createCurrentUserAccount());
        nonFile.setIngestionType(IngestionType.API);
        nonFile.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(nonFile);
        validRecordFor(nonFile, 1);
        mockMvc.perform(post(prepareCandidatesUrl(nonFile))).andExpect(status().isBadRequest());

        TransactionIngestion completed = createWorkflowWithSingleValidRow();
        completed.setStatus(IngestionStatus.COMPLETED);
        transactionIngestionRepository.saveAndFlush(completed);
        mockMvc.perform(post(prepareCandidatesUrl(completed))).andExpect(status().isBadRequest());

        TransactionIngestion noValidRows = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(noValidRows).get(0);
        record.setStatus(IngestionRecordStatus.DISABLED);
        ingestionRecordRepository.saveAndFlush(record);
        noValidRows.setStatus(IngestionStatus.PARTIALLY_READY);
        transactionIngestionRepository.saveAndFlush(noValidRows);
        mockMvc.perform(post(prepareCandidatesUrl(noValidRows))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void prepareCandidatesReportsErrorForUnsafeNormalizedDataWithoutMutatingRawData() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        String rawDataBefore = record.getRawData();
        setNormalizedFields(record, "2026-01-15", null, "Broken amount", "0.00", "MXN", null, null);
        String rawDataAfterManualCorruption = ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(prepareCandidatesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.errorCount").value(1))
            .andExpect(jsonPath("$.rows[0].action").value("ERROR"))
            .andExpect(jsonPath("$.rows[0].reason").value("signedAmount must not be zero"));

        assertThat(
            transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
        ).isEmpty();
        assertThat(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData()).isEqualTo(rawDataAfterManualCorruption);
        assertThat(rawDataAfterManualCorruption).isNotEqualTo(rawDataBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
    }

    @Test
    @Transactional
    void classificationPreviewReturnsSuggestionsForValidReadyRowsWithoutMutatingWorkflow() throws Exception {
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Ride share", currentMockUser());
        TransactionRule rule = persistTransactionRule("Uber rule", category, List.of(tag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "Uber");
        persistTransactionRuleCondition(rule, TransactionRuleField.FLOW, RuleOperator.EQUALS, "OUT");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord disabledRecord = recordsFor(ingestion).get(0);
        mockMvc.perform(post(reviewUrl(ingestion, disabledRecord, "disable"))).andExpect(status().isOk());
        List<String> rawDataBefore = recordsFor(ingestion).stream().map(IngestionRecord::getRawData).toList();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(classificationPreviewUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[1].recordId").value(recordsFor(ingestion).get(2).getId()))
            .andExpect(jsonPath("$.rows[1].description").value("Uber, Trip"))
            .andExpect(jsonPath("$.rows[1].suggestedCategory.id").value(category.getId()))
            .andExpect(jsonPath("$.rows[1].suggestedCategory.name").value("Transport"))
            .andExpect(jsonPath("$.rows[1].suggestedCategory.categoryType").value("EXPENSE"))
            .andExpect(jsonPath("$.rows[1].suggestedTags[0].id").value(tag.getId()))
            .andExpect(jsonPath("$.rows[1].suggestedTags[0].name").value("Ride share"))
            .andExpect(jsonPath("$.rows[1].matchedRules[0].ruleName").value("Uber rule"));

        assertThat(recordsFor(ingestion).stream().map(IngestionRecord::getRawData).toList()).isEqualTo(rawDataBefore);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void classificationPreviewOnlySuggestsExpenseCategoryForMatchingOutRows() throws Exception {
        Category expenseCategory = persistCategory("Transporte", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Ride share", currentMockUser());
        TransactionRule rule = persistTransactionRule("Uber gastos", expenseCategory, List.of(tag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "Uber");
        persistTransactionRuleCondition(rule, TransactionRuleField.FLOW, RuleOperator.EQUALS, "OUT");
        TransactionIngestion ingestion = createWorkflowWithUberOutAndInRows();
        List<IngestionRecord> records = recordsFor(ingestion);

        JsonNode preview = objectMapper.readTree(
            mockMvc
                .perform(post(classificationPreviewUrl(ingestion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].recordId").value(records.get(0).getId()))
                .andExpect(jsonPath("$.rows[0].description").value("Uber trip"))
                .andExpect(jsonPath("$.rows[0].flow").value("OUT"))
                .andExpect(jsonPath("$.rows[0].suggestedCategory.id").value(expenseCategory.getId()))
                .andExpect(jsonPath("$.rows[0].suggestedCategory.name").value("Transporte"))
                .andExpect(jsonPath("$.rows[0].suggestedTags[0].id").value(tag.getId()))
                .andExpect(jsonPath("$.rows[1].recordId").value(records.get(1).getId()))
                .andExpect(jsonPath("$.rows[1].description").value("Uber refund"))
                .andExpect(jsonPath("$.rows[1].flow").value("IN"))
                .andReturn()
                .getResponse()
                .getContentAsString()
        );
        JsonNode inRowSuggestedCategory = preview.path("rows").get(1).path("suggestedCategory");
        assertThat(inRowSuggestedCategory.isMissingNode() || inRowSuggestedCategory.isNull()).isTrue();
        assertThat(preview.path("rows").get(1).path("suggestedTags").size()).isZero();
        assertThat(preview.path("rows").get(1).path("matchedRules").size()).isZero();

        confirmImport(
            ingestion,
            List.of(
                confirmSelection(records.get(0).getId(), expenseCategory.getId(), List.of(tag.getId())),
                confirmSelection(records.get(1).getId(), expenseCategory.getId(), List.of())
            )
        ).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();

        confirmImport(
            ingestion,
            List.of(
                confirmSelection(records.get(0).getId(), expenseCategory.getId(), List.of(tag.getId())),
                confirmSelection(records.get(1).getId(), null, List.of())
            )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(2));

        List<FinancialTransaction> transactions = financialTransactionRepository
            .findAll()
            .stream()
            .sorted(Comparator.comparing(FinancialTransaction::getTransactionDate))
            .toList();
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(transactions.get(0).getCategory().getId()).isEqualTo(expenseCategory.getId());
        assertThat(transactions.get(0).getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        assertThat(transactions.get(1).getFlow()).isEqualTo(TransactionFlow.IN);
        assertThat(transactions.get(1).getCategory()).isNull();
        assertThat(transactions.get(1).getTags()).isEmpty();
    }

    @Test
    @Transactional
    void classificationPreviewUsesNormalizedDescriptionAndRejectsForeignOrNotReadyIngestions() throws Exception {
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        TransactionRule rule = persistTransactionRule("Normalized Uber rule", category, List.of());
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.EQUALS, "Uber");
        persistTransactionRuleCondition(rule, TransactionRuleField.FLOW, RuleOperator.EQUALS, "OUT");
        DescriptionNormalizationRule descriptionRule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(descriptionRule, "Uber");
        FinancialAccount account = createCurrentUserAccount();

        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk());
        TransactionIngestion ingestion = transactionIngestionRepository
            .findAll()
            .stream()
            .max(Comparator.comparing(TransactionIngestion::getId))
            .orElseThrow();

        mockMvc
            .perform(post(classificationPreviewUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[2].description").value("Uber"))
            .andExpect(jsonPath("$.rows[2].suggestedCategory.name").value("Transport"));

        ingestion.setStatus(IngestionStatus.PARTIALLY_READY);
        transactionIngestionRepository.saveAndFlush(ingestion);
        mockMvc.perform(post(classificationPreviewUrl(ingestion))).andExpect(status().isBadRequest());

        TransactionIngestion foreign = createPendingFileTransactionIngestion(createAccountForUser(createOtherUser()));
        foreign.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(foreign);
        mockMvc.perform(post(classificationPreviewUrl(foreign))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void confirmAppliesSelectedCategoryAndTagsWithoutWritingSelectionToRawData() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Cash", currentMockUser());

        confirmImport(ingestion, List.of(confirmSelection(record.getId(), category.getId(), List.of(tag.getId()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(transaction.getCategory().getId()).isEqualTo(category.getId());
        assertThat(transaction.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        JsonNode rawData = objectMapper.readTree(recordsFor(ingestion).get(0).getRawData());
        assertThat(rawData.path("normalized").has("categoryId")).isFalse();
        assertThat(rawData.path("normalized").has("tagIds")).isFalse();
        assertThat(rawData.path("review").has("categoryId")).isFalse();
        assertThat(rawData.path("review").has("tagIds")).isFalse();
    }

    @Test
    @Transactional
    void confirmValidatesExplicitRecordCategoryAndTagPayload() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category incompatibleCategory = persistCategory("Income", CategoryType.INCOME, currentMockUser());
        Category foreignCategory = persistCategory("Foreign", CategoryType.EXPENSE, createOtherUser());
        Tag foreignTag = persistTag("Foreign tag", createOtherUser());

        confirmImport(ingestion, List.of()).andExpect(status().isBadRequest());
        confirmImport(ingestion, List.of(confirmSelection(record.getId() + 9999, null, List.of()))).andExpect(status().isBadRequest());
        confirmImport(
            ingestion,
            List.of(confirmSelection(record.getId(), null, List.of()), confirmSelection(record.getId(), null, List.of()))
        ).andExpect(status().isBadRequest());
        confirmImport(ingestion, List.of(confirmSelection(record.getId(), foreignCategory.getId(), List.of()))).andExpect(
            status().isBadRequest()
        );
        confirmImport(ingestion, List.of(confirmSelection(record.getId(), incompatibleCategory.getId(), List.of()))).andExpect(
            status().isBadRequest()
        );
        confirmImport(ingestion, List.of(confirmSelection(record.getId(), null, List.of(foreignTag.getId())))).andExpect(
            status().isBadRequest()
        );
        confirmImport(ingestion, List.of(confirmSelection(record.getId(), null, List.of(1L, 1L)))).andExpect(status().isBadRequest());

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void confirmRequiresPayloadForReadyIngestionButAcceptsNullCategoryAndEmptyTags() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Confirm import requires category/tag selections for all valid records"));

        confirmImport(ingestion).andExpect(status().isOk()).andExpect(jsonPath("$.createdNow").value(1));
        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void confirmReadyIngestionCreatesFinancialTransactionsFromNormalizedRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord disabledRecord = recordsFor(ingestion).get(0);
        JsonNode originalRaw = objectMapper.readTree(disabledRecord.getRawData()).path("raw");
        mockMvc.perform(post(reviewUrl(ingestion, disabledRecord, "disable"))).andExpect(status().isOk());

        confirmImport(ingestion)
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
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();

        confirmImport(ingestion).andExpect(status().isOk()).andExpect(jsonPath("$.createdNow").value(1));
        long financialTransactionCountAfterFirstConfirm = financialTransactionRepository.count();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(0))
            .andExpect(jsonPath("$.alreadyImported").value(1));

        IngestionRecord importedRecord = recordsFor(ingestion).get(0);
        confirmImport(ingestion, List.of(confirmSelection(importedRecord.getId(), null, List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(0))
            .andExpect(jsonPath("$.alreadyImported").value(1));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountAfterFirstConfirm);
    }

    @Test
    @Transactional
    void deleteCompletedImportedWorkflowCleansChildrenAndTransactionsButKeepsCategoryAndTags() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Long fileIngestionId = fileIngestionRepository.findAll().get(0).getId();
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Ride share", currentMockUser());

        confirmImport(ingestion, List.of(confirmSelection(record.getId(), category.getId(), List.of(tag.getId()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        Long financialTransactionId = financialTransactionRepository.findAll().get(0).getId();
        Number tagJoinRowsBeforeDelete = (Number) em
            .createNativeQuery("select count(*) from rel_financial_transaction__tags where financial_transaction_id = :transactionId")
            .setParameter("transactionId", financialTransactionId)
            .getSingleResult();
        assertThat(tagJoinRowsBeforeDelete.longValue()).isEqualTo(1L);

        mockMvc
            .perform(delete("/api/transaction-ingestions/{id}", ingestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        Number tagJoinRowsAfterDelete = (Number) em
            .createNativeQuery("select count(*) from rel_financial_transaction__tags where financial_transaction_id = :transactionId")
            .setParameter("transactionId", financialTransactionId)
            .getSingleResult();
        assertThat(tagJoinRowsAfterDelete.longValue()).isZero();
        assertThat(fileIngestionRepository.findById(fileIngestionId)).isEmpty();
        assertThat(ingestionRecordRepository.findById(record.getId())).isEmpty();
        assertThat(financialTransactionRepository.findById(financialTransactionId)).isEmpty();
        assertThat(transactionIngestionRepository.findById(ingestion.getId())).isEmpty();
        assertThat(categoryRepository.findById(category.getId())).isPresent();
        assertThat(tagRepository.findById(tag.getId())).isPresent();
    }

    @Test
    @Transactional
    void confirmRecalculatesReadinessAndRejectsStaleReadyWithRejectedRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
        ingestion.setStatus(IngestionStatus.READY);
        transactionIngestionRepository.saveAndFlush(ingestion);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
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
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        ingestion.setStatus(IngestionStatus.PARTIALLY_READY);
        transactionIngestionRepository.saveAndFlush(ingestion);

        confirmImport(ingestion)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        assertThat(financialTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void confirmRejectsNotReadyAndDisallowedStatusesWithoutImporting() throws Exception {
        TransactionIngestion partiallyReady = createWorkflowWithInvalidRow();
        confirmImport(partiallyReady).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();

        TransactionIngestion noValidRows = createWorkflowWithSingleValidRow();
        mockMvc.perform(post(reviewUrl(noValidRows, recordsFor(noValidRows).get(0), "disable"))).andExpect(status().isOk());
        confirmImport(noValidRows).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();

        for (IngestionStatus status : List.of(
            IngestionStatus.PENDING,
            IngestionStatus.PROCESSING,
            IngestionStatus.FAILED,
            IngestionStatus.PARTIALLY_COMPLETED
        )) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            ingestion.setStatus(status);
            transactionIngestionRepository.saveAndFlush(ingestion);
            confirmImport(ingestion).andExpect(status().isBadRequest());
        }

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void confirmRejectsCorruptFinancialTransactionLinks() throws Exception {
        TransactionIngestion importedWithoutTransaction = createWorkflowWithSingleValidRow();
        IngestionRecord importedRecord = recordsFor(importedWithoutTransaction).get(0);
        importedRecord.setStatus(IngestionRecordStatus.IMPORTED);
        importedRecord.setFinancialTransaction(null);
        ingestionRecordRepository.saveAndFlush(importedRecord);
        importedWithoutTransaction.setStatus(IngestionStatus.COMPLETED);
        transactionIngestionRepository.saveAndFlush(importedWithoutTransaction);

        mockMvc.perform(post(confirmUrl(importedWithoutTransaction))).andExpect(status().isBadRequest());

        TransactionIngestion validWithTransaction = createWorkflowWithSingleValidRow();
        IngestionRecord validRecord = recordsFor(validWithTransaction).get(0);
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(validWithTransaction.getAccount());
        financialTransaction.setTransactionIngestion(validWithTransaction);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        validRecord.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(validRecord);

        confirmImport(validWithTransaction).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void completedIngestionRowsCannotBeReviewed() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        confirmImport(ingestion).andExpect(status().isOk());

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

    private TransactionIngestion createPendingFileTransactionIngestion(FinancialAccount account) {
        TransactionIngestion ingestion = TransactionIngestionResourceIT.createEntity(em);
        ingestion.setAccount(account);
        ingestion.setIngestionType(IngestionType.FILE);
        ingestion.setStatus(IngestionStatus.PENDING);
        ingestion.setSourceLabel(null);
        ingestion.setStartedAt(java.time.Instant.now());
        ingestion.setCompletedAt(null);
        ingestion.setRecordsReceived(0);
        ingestion.setRecordsCreated(0);
        ingestion.setRecordsSkipped(0);
        ingestion.setRecordsRejected(0);
        ingestion.setErrorMessage(null);
        ingestion.setCreatedAt(java.time.Instant.now());
        return transactionIngestionRepository.saveAndFlush(ingestion);
    }

    private TransactionIngestion createWorkflowWithValidRows() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("canonical.csv", VALID_CSV)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk());
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private TransactionIngestion createWorkflowWithSingleValidRow() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;
        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("single-valid.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private TransactionIngestion createWorkflowWithUberOutAndInRows() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            2026-01-16,,Uber trip,-100.00,MXN,,
            2026-01-17,,Uber refund,100.00,MXN,,
            """;
        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("uber-out-in.csv", csv)).param("accountId", account.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));
        return transactionIngestionRepository.findAll().stream().max(Comparator.comparing(TransactionIngestion::getId)).orElseThrow();
    }

    private TransactionIngestion createWorkflowWithInvalidRow() throws Exception {
        FinancialAccount account = createCurrentUserAccount();
        String csv =
            """
            transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
            nope,,,-0.001,USD,,
            2026-01-16,,OXXO AGUILAS,-274.00,MXN,,
            """;
        mockMvc
            .perform(multipart(FILE_WORKFLOW_URL).file(csvFile("mixed.csv", csv)).param("accountId", account.getId().toString()))
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

    private String classificationPreviewUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/classification-preview";
    }

    private String prepareCandidatesUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/prepare";
    }

    private ResultActions confirmImport(TransactionIngestion ingestion) throws Exception {
        return confirmImport(
            ingestion,
            recordsFor(ingestion)
                .stream()
                .filter(record -> record.getStatus() == IngestionRecordStatus.VALID)
                .map(record -> confirmSelection(record.getId(), null, List.of()))
                .toList()
        );
    }

    private ResultActions confirmImport(TransactionIngestion ingestion, List<Map<String, Object>> recordSelections) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", recordSelections);
        return mockMvc.perform(
            post(confirmUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload))
        );
    }

    private Map<String, Object> confirmSelection(Long recordId, Long categoryId, List<Long> tagIds) {
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("recordId", recordId);
        selection.put("categoryId", categoryId);
        selection.put("tagIds", tagIds);
        return selection;
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

    private Category persistCategory(String name, CategoryType categoryType, User user) {
        Category category = CategoryResourceIT.createEntity(em);
        category.setName(name);
        category.setCategoryType(categoryType);
        category.setParentCategory(null);
        category.setUser(user);
        return categoryRepository.saveAndFlush(category);
    }

    private Tag persistTag(String name, User user) {
        Tag tag = new Tag()
            .name(name)
            .description(null)
            .color(null)
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .user(user);
        return tagRepository.saveAndFlush(tag);
    }

    private TransactionRule persistTransactionRule(String name, Category resultingCategory, List<Tag> resultingTags) {
        TransactionRule rule = new TransactionRule()
            .name(name)
            .description(null)
            .priority(0)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .user(currentMockUser())
            .resultingCategory(resultingCategory);
        resultingTags.forEach(rule::addResultingTags);
        return transactionRuleRepository.saveAndFlush(rule);
    }

    private TransactionRuleCondition persistTransactionRuleCondition(
        TransactionRule rule,
        TransactionRuleField field,
        RuleOperator operator,
        String value
    ) {
        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(field)
            .operator(operator)
            .value(value)
            .secondValue(null)
            .caseSensitive(false)
            .position(rule.getConditions().size())
            .transactionRule(rule);
        TransactionRuleCondition persisted = transactionRuleConditionRepository.saveAndFlush(condition);
        rule.getConditions().add(persisted);
        return persisted;
    }

    private DescriptionNormalizationRule persistDescriptionNormalizationRule(String name, String resultingDescription) {
        DescriptionNormalizationRule rule = new DescriptionNormalizationRule()
            .name(name)
            .description(null)
            .active(true)
            .priority(0)
            .conditionOperator(RuleConditionLogic.ANY)
            .resultingDescription(resultingDescription)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .user(currentMockUser());
        return descriptionNormalizationRuleRepository.saveAndFlush(rule);
    }

    private DescriptionNormalizationRuleCondition persistDescriptionNormalizationCondition(
        DescriptionNormalizationRule rule,
        String value
    ) {
        DescriptionNormalizationRuleCondition condition = new DescriptionNormalizationRuleCondition()
            .operator(DescriptionNormalizationRuleOperator.CONTAINS)
            .value(value)
            .caseSensitive(false)
            .position(0)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .descriptionNormalizationRule(rule);
        DescriptionNormalizationRuleCondition persisted = descriptionNormalizationRuleConditionRepository.saveAndFlush(condition);
        rule.getConditions().add(persisted);
        return persisted;
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

    private TransactionCandidate candidateForRecord(IngestionRecord record) {
        return transactionCandidateRepository.findOneWithRelationshipsByIngestionRecordIdAndUserLogin(record.getId(), "user").orElseThrow();
    }

    private void setNormalizedFields(
        IngestionRecord record,
        String transactionDate,
        String postingDate,
        String description,
        String signedAmount,
        String currency,
        String externalReference,
        String notes
    ) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(record.getRawData());
        ObjectNode normalized = root.path("normalized").isObject() ? (ObjectNode) root.path("normalized") : root.putObject("normalized");
        putNullable(normalized, "transactionDate", transactionDate);
        putNullable(normalized, "postingDate", postingDate);
        putNullable(normalized, "description", description);
        putNullable(normalized, "signedAmount", signedAmount);
        putNullable(normalized, "currency", currency);
        putNullable(normalized, "externalReference", externalReference);
        putNullable(normalized, "notes", notes);
        if (signedAmount != null && !signedAmount.isBlank()) {
            java.math.BigDecimal amount = new java.math.BigDecimal(signedAmount).abs();
            normalized.put("amount", amount.setScale(2).toPlainString());
            normalized.put("flow", new java.math.BigDecimal(signedAmount).signum() > 0 ? "IN" : "OUT");
        }
        record.setRawData(objectMapper.writeValueAsString(root));
        ingestionRecordRepository.saveAndFlush(record);
    }

    private void putNullable(ObjectNode node, String fieldName, String value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    private void assertNothingCreated() {
        assertThat(transactionIngestionRepository.findAll()).isEmpty();
        assertThat(fileIngestionRepository.findAll()).isEmpty();
        assertThat(ingestionRecordRepository.findAll()).isEmpty();
    }

    private String legacyFileWorkflowUrl() {
        return "/api/transaction-ingestions/file-" + "pre" + "view";
    }

    private String legacyFileWorkflowUrl(Long transactionIngestionId) {
        return "/api/transaction-ingestions/" + transactionIngestionId + "/file-" + "pre" + "view";
    }

    private String sha256Hex(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
