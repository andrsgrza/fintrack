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
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
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
import java.util.stream.Collectors;
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
    void rowEditChangingDescriptionReplacesRuleProvenanceAndSyncsPreparedCandidate() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Manual Uber", "-158.33", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.description").value("Manual Uber"))
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"));

        JsonNode rawData = rawDataFor(record);
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Manual Uber");
        assertThat(rawData.path("review").path("description").path("source").asText()).isEqualTo("USER_EDIT");

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getDescription()).isEqualTo("Manual Uber");
        assertThat(synced.getDescriptionReviewStatus()).isEqualTo(TransactionCandidateDescriptionReviewStatus.USER_EDITED);
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.STALE);
    }

    @Test
    @Transactional
    void reevaluateDescriptionsUsesPriorityOrderedRulesSyncsExistingCandidateAndDoesNotApplyClassification() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord uberRecord = recordsFor(ingestion).get(2);
        DescriptionNormalizationRule lowerPriorityRule = persistDescriptionNormalizationRule("Normalize Uber later", "Uber later");
        persistDescriptionNormalizationCondition(lowerPriorityRule, "Uber");
        lowerPriorityRule.setPriority(1);
        descriptionNormalizationRuleRepository.saveAndFlush(lowerPriorityRule);
        DescriptionNormalizationRule higherPriorityRule = persistDescriptionNormalizationRule("Normalize Uber first", "Uber first");
        persistDescriptionNormalizationCondition(higherPriorityRule, "Uber");
        higherPriorityRule.setPriority(0);
        descriptionNormalizationRuleRepository.saveAndFlush(higherPriorityRule);

        Category manualCategory = persistCategory("Manual transport", CategoryType.EXPENSE, currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(uberRecord);
        candidate.setCategory(manualCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.setTags(new HashSet<>(Set.of(manualTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);
        String rawDataBefore = uberRecord.getRawData();
        String unrelatedRawDataBefore = recordsFor(ingestion).get(0).getRawData();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(uberRecord.getId()))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].ingestionRecordId").value(uberRecord.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.ruleId").value(higherPriorityRule.getId()))
            .andExpect(jsonPath("$.rows[0].descriptionReview.ruleName").value("Normalize Uber first"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.normalizedDescription").value("Uber first"));

        JsonNode rawData = rawDataFor(uberRecord);
        assertThat(rawData.path("raw").path("description").asText()).isEqualTo("Uber, Trip");
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Uber first");
        assertThat(rawData.path("review").path("description").path("ruleId").asLong()).isEqualTo(higherPriorityRule.getId());
        assertThat(rawData.path("normalized").has("category")).isFalse();
        assertThat(rawData.path("normalized").has("tags")).isFalse();
        assertThat(ingestionRecordRepository.findById(recordsFor(ingestion).get(0).getId()).orElseThrow().getRawData()).isEqualTo(
            unrelatedRawDataBefore
        );

        TransactionCandidate updated = candidateForRecord(uberRecord);
        assertThat(updated.getDescription()).isEqualTo("Uber first");
        assertThat(updated.getCategory().getId()).isEqualTo(manualCategory.getId());
        assertThat(updated.getTags()).extracting(Tag::getId).containsExactly(manualTag.getId());
        assertThat(updated.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.STALE);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void reevaluateDescriptionsReturnsNoMatchForRequestedRowOnlyAndSkipsNonValidRows() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(rule, "Uber");
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
        List<IngestionRecord> records = recordsFor(ingestion);
        IngestionRecord rejected = records.get(0);
        IngestionRecord valid = records.get(1);
        String rejectedRawDataBefore = rejected.getRawData();
        String validRawDataBefore = valid.getRawData();

        mockMvc
            .perform(post(descriptionReevaluationUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].ingestionRecordId").value(rejected.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[1].ingestionRecordId").value(valid.getId()))
            .andExpect(jsonPath("$.rows[1].action").value("NO_MATCH"));

        assertThat(ingestionRecordRepository.findById(rejected.getId()).orElseThrow().getRawData()).isEqualTo(rejectedRawDataBefore);
        assertThat(ingestionRecordRepository.findById(valid.getId()).orElseThrow().getRawData()).isEqualTo(validRawDataBefore);

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(valid.getId()))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].ingestionRecordId").value(valid.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("NO_MATCH"));
    }

    @Test
    @Transactional
    void reevaluateDescriptionsPreservesUserEditedDescriptionAndReturnsOnlyTransientSuggestion() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize payroll", "Payroll");
        persistDescriptionNormalizationCondition(rule, "NOMINA");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        assertThat(candidate.getCategorySource()).isNull();
        assertThat(candidate.getTagAssociations()).isEmpty();

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(reviewPayload("2026-01-15", null, "Manual payroll", "33698.34", "MXN", null, null))
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"));
        String rawDataBefore = ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(record.getId()))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].action").value("MANUAL_PRESERVED"))
            .andExpect(jsonPath("$.rows[0].suggestedDescription").value("Payroll"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.source").value("USER_EDIT"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.normalizedDescription").value("Manual payroll"));

        assertThat(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData()).isEqualTo(rawDataBefore);
        TransactionCandidate unchanged = candidateForRecord(record);
        assertThat(unchanged.getId()).isEqualTo(candidate.getId());
        assertThat(unchanged.getDescription()).isEqualTo("Manual payroll");
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void descriptionReevaluationPreviewDoesNotOverwriteUntilApplyIsRequested() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize OXXO", "OXXO Store");
        persistDescriptionNormalizationCondition(rule, "OXXO");
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        String rawDataBefore = record.getRawData();
        TransactionCandidate candidateBefore = candidateForRecord(record);

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(record.getId()), false, true)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].action").value("PREVIEWED"))
            .andExpect(jsonPath("$.rows[0].suggestedDescription").value("OXXO Store"));

        assertThat(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData()).isEqualTo(rawDataBefore);
        assertThat(candidateForRecord(record).getDescription()).isEqualTo(candidateBefore.getDescription());
    }

    @Test
    @Transactional
    void descriptionReevaluationCanReplaceUserEditOnlyWhenManualProtectionIsOff() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize payroll", "Payroll");
        persistDescriptionNormalizationCondition(rule, "NOMINA");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(reviewPayload("2026-01-15", null, "Manual payroll", "33698.34", "MXN", null, null))
                    )
            )
            .andExpect(status().isOk());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(record.getId()), true, false)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[0].descriptionReview.normalizedDescription").value("Payroll"));

        assertThat(rawDataFor(record).path("normalized").path("description").asText()).isEqualTo("Payroll");
        assertThat(candidateForRecord(record).getDescription()).isEqualTo("Payroll");
    }

    @Test
    @Transactional
    void batchDescriptionApplyPersistsRuleResultsPreservesProtectedManualEditsAndSkipsDisabledRows() throws Exception {
        DescriptionNormalizationRule oxxoRule = persistDescriptionNormalizationRule("Normalize OXXO", "OXXO Store");
        persistDescriptionNormalizationCondition(oxxoRule, "OXXO");
        DescriptionNormalizationRule uberRule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(uberRule, "Uber");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        List<IngestionRecord> records = recordsFor(ingestion);
        IngestionRecord disabledRecord = records.get(0);
        IngestionRecord oxxoRecord = records.get(1);
        IngestionRecord uberRecord = records.get(2);
        String disabledRawDataBefore = disabledRecord.getRawData();
        disabledRecord.setStatus(IngestionRecordStatus.DISABLED);
        ingestionRecordRepository.saveAndFlush(disabledRecord);

        Category manualCategory = persistCategory("Manual transport", CategoryType.EXPENSE, currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate oxxoCandidate = candidateForRecord(oxxoRecord);
        oxxoCandidate.setCategory(manualCategory);
        oxxoCandidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        oxxoCandidate.setTags(new HashSet<>(Set.of(manualTag)));
        oxxoCandidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(oxxoCandidate);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, uberRecord, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Manual Uber", "-158.33", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"));
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            descriptionReevaluationPayload(
                                List.of(disabledRecord.getId(), oxxoRecord.getId(), uberRecord.getId()),
                                true,
                                true
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(3))
            .andExpect(jsonPath("$.rows[0].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[1].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[1].descriptionReview.source").value("DESCRIPTION_RULE"))
            .andExpect(jsonPath("$.rows[1].descriptionReview.normalizedDescription").value("OXXO Store"))
            .andExpect(jsonPath("$.rows[2].action").value("MANUAL_PRESERVED"))
            .andExpect(jsonPath("$.rows[2].suggestedDescription").value("Uber"));

        assertThat(ingestionRecordRepository.findById(disabledRecord.getId()).orElseThrow().getRawData()).isEqualTo(disabledRawDataBefore);
        JsonNode oxxoRawData = rawDataFor(oxxoRecord);
        assertThat(oxxoRawData.path("normalized").path("description").asText()).isEqualTo("OXXO Store");
        assertThat(oxxoRawData.path("normalized").has("category")).isFalse();
        assertThat(oxxoRawData.path("normalized").has("tags")).isFalse();
        assertThat(rawDataFor(uberRecord).path("normalized").path("description").asText()).isEqualTo("Manual Uber");

        TransactionCandidate updatedOxxoCandidate = candidateForRecord(oxxoRecord);
        assertThat(updatedOxxoCandidate.getDescription()).isEqualTo("OXXO Store");
        assertThat(updatedOxxoCandidate.getCategory().getId()).isEqualTo(manualCategory.getId());
        assertThat(updatedOxxoCandidate.getTags()).extracting(Tag::getId).containsExactly(manualTag.getId());
        assertThat(candidateForRecord(uberRecord).getDescription()).isEqualTo("Manual Uber");
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void reevaluateDescriptionsRejectsForeignIngestionAndRecordFromAnotherIngestion() throws Exception {
        TransactionIngestion accessibleIngestion = createWorkflowWithValidRows();
        TransactionIngestion otherIngestion = createWorkflowWithValidRows();
        IngestionRecord otherRecord = recordsFor(otherIngestion).get(0);

        mockMvc
            .perform(
                post(descriptionReevaluationUrl(accessibleIngestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(descriptionReevaluationPayload(List.of(otherRecord.getId()))))
            )
            .andExpect(status().isBadRequest());

        FinancialAccount otherUsersAccount = createAccountForUser(createOtherUser());
        TransactionIngestion foreignIngestion = createPendingFileTransactionIngestion(otherUsersAccount);
        foreignIngestion.setStatus(IngestionStatus.READY);
        foreignIngestion = transactionIngestionRepository.saveAndFlush(foreignIngestion);
        validRecordFor(foreignIngestion, 1);
        mockMvc.perform(post(descriptionReevaluationUrl(foreignIngestion))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void rowEditChangingAmountPreservesRuleDescriptionProvenanceAndCandidateDescriptionReview() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);
        JsonNode descriptionReviewBefore = descriptionReviewFor(record);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Uber", "-200.00", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("DESCRIPTION_RULE"));

        JsonNode rawData = rawDataFor(record);
        assertThat(rawData.path("normalized").path("description").asText()).isEqualTo("Uber");
        assertThat(rawData.path("normalized").path("signedAmount").asText()).isEqualTo("-200.00");
        assertThat(rawData.path("review").path("description")).isEqualTo(descriptionReviewBefore);

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getSignedAmount()).isEqualByComparingTo("-200.00");
        assertThat(synced.getDescriptionReviewStatus()).isEqualTo(TransactionCandidateDescriptionReviewStatus.AUTO_APPLIED);
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.STALE);
    }

    @Test
    @Transactional
    void rowEditChangingDatesPreservesRuleDescriptionProvenance() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        JsonNode descriptionReviewBefore = descriptionReviewFor(record);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-19", "2026-01-20", "Uber", "-158.33", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("DESCRIPTION_RULE"));

        JsonNode rawData = rawDataFor(record);
        assertThat(rawData.path("normalized").path("transactionDate").asText()).isEqualTo("2026-01-19");
        assertThat(rawData.path("normalized").path("postingDate").asText()).isEqualTo("2026-01-20");
        assertThat(rawData.path("review").path("description")).isEqualTo(descriptionReviewBefore);
    }

    @Test
    @Transactional
    void rowEditChangingNotesAndExternalReferencePreservesRuleDescriptionProvenance() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        JsonNode descriptionReviewBefore = descriptionReviewFor(record);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Uber", "-158.33", "MXN", "updated-ref", "updated notes")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("DESCRIPTION_RULE"));

        JsonNode rawData = rawDataFor(record);
        assertThat(rawData.path("normalized").path("externalReference").asText()).isEqualTo("updated-ref");
        assertThat(rawData.path("normalized").path("notes").asText()).isEqualTo("updated notes");
        assertThat(rawData.path("review").path("description")).isEqualTo(descriptionReviewBefore);
    }

    @Test
    @Transactional
    void rowEditWithSameEffectiveDescriptionPreservesRuleDescriptionProvenance() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        JsonNode descriptionReviewBefore = descriptionReviewFor(record);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "  Uber  ", "-158.33", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.description").value("Uber"))
            .andExpect(jsonPath("$.row.descriptionReview.source").value("DESCRIPTION_RULE"));

        assertThat(descriptionReviewFor(record)).isEqualTo(descriptionReviewBefore);
    }

    @Test
    @Transactional
    void rowEditOfUnrelatedFieldPreservesExistingUserEditDescriptionMetadata() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithRuleNormalizedUberDescription();
        IngestionRecord record = recordsFor(ingestion).get(2);
        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Manual Uber", "-158.33", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"));
        JsonNode descriptionReviewBefore = descriptionReviewFor(record);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-17", "2026-01-18", "Manual Uber", "-200.00", "MXN", "abc-123", "quoted, note")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.descriptionReview.source").value("USER_EDIT"));

        assertThat(descriptionReviewFor(record)).isEqualTo(descriptionReviewBefore);
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
    void disableValidRowWithPreparedCandidateDeletesCandidateAndCandidateTagJoins() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Tag tag = persistTag("Prepared disabled row tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        transactionCandidateRepository.saveAndFlush(candidate);
        Long candidateId = candidate.getId();

        assertThat(candidateTagJoinRows(candidateId).longValue()).isEqualTo(1L);

        mockMvc
            .perform(post(reviewUrl(ingestion, record, "disable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.status").value("DISABLED"));

        assertThat(candidateTagJoinRows(candidateId).longValue()).isZero();
        assertThat(transactionCandidateRepository.findById(candidateId)).isEmpty();
        mockMvc.perform(get(workflowUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].candidate").doesNotExist());
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
    void reEnableAfterDisableDoesNotRestoreOldCandidateAndPrepareCreatesFreshCandidate() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        Long deletedCandidateId = candidateForRecord(record).getId();

        mockMvc.perform(post(reviewUrl(ingestion, record, "disable"))).andExpect(status().isOk());
        mockMvc
            .perform(post(reviewUrl(ingestion, record, "enable")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.status").value("VALID"));

        assertThat(transactionCandidateRepository.findById(deletedCandidateId)).isEmpty();
        assertThat(
            transactionCandidateRepository.findOneWithRelationshipsByIngestionRecordIdAndUserLogin(record.getId(), "user")
        ).isEmpty();

        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(1));

        TransactionCandidate freshCandidate = candidateForRecord(record);
        assertThat(freshCandidate.getId()).isNotEqualTo(deletedCandidateId);
        assertThat(freshCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
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
    void editPreparedValidRowSyncsCandidatePreservesOutputsAndMarksClassificationStale() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Edited sync category", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Edited sync tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(category);
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        candidate = transactionCandidateRepository.saveAndFlush(candidate);
        Instant updatedAtBefore = candidate.getUpdatedAt();

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-02-01", "2026-02-02", "Edited prepared row", "-42.50", "MXN", "edited-ref", "edited notes")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.status").value("VALID"));

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getTransactionDate()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(synced.getPostingDate()).isEqualTo(LocalDate.parse("2026-02-02"));
        assertThat(synced.getDescription()).isEqualTo("Edited prepared row");
        assertThat(synced.getSignedAmount()).isEqualByComparingTo("-42.50");
        assertThat(synced.getAmount()).isEqualByComparingTo("42.50");
        assertThat(synced.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(synced.getExternalReference()).isEqualTo("edited-ref");
        assertThat(synced.getNotes()).isEqualTo("edited notes");
        assertThat(synced.getCategory().getId()).isEqualTo(category.getId());
        assertThat(synced.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        assertThat(synced.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(synced.getTagSource(tag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.STALE);
        assertThat(synced.getUpdatedAt()).isAfter(updatedAtBefore);
    }

    @Test
    @Transactional
    void editPreparedValidRowWithNotesOnlyDoesNotMarkClassificationStale() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            reviewPayload("2026-01-16", null, "OXXO AGUILAS", "-274.00", "MXN", null, "only notes changed")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.status").value("VALID"));

        TransactionCandidate synced = candidateForRecord(record);
        assertThat(synced.getNotes()).isEqualTo("only notes changed");
        assertThat(synced.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
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
    void editPreparedRowToRejectedDeletesUnpostedCandidateAndCandidateTagJoins() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Tag tag = persistTag("Rejected edited row tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        transactionCandidateRepository.saveAndFlush(candidate);
        Long candidateId = candidate.getId();

        mockMvc
            .perform(
                patch(reviewUrl(ingestion, record, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(reviewPayload("2026-01-20", null, "Corrected", "0", "MXN", null, null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.status").value("REJECTED"));

        assertThat(candidateTagJoinRows(candidateId).longValue()).isZero();
        assertThat(transactionCandidateRepository.findById(candidateId)).isEmpty();
        mockMvc.perform(get(workflowUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].candidate").doesNotExist());
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
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
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
        assertThat(synced.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(synced.getTagSource(tag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
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
    void workflowIncludesCandidateSummaryAfterPrepare() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);

        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        assertThat(candidate.getCategorySource()).isNull();
        assertThat(candidate.getTagAssociations()).isEmpty();

        mockMvc
            .perform(get(workflowUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].ingestionRecordId").value(record.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.id").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.source").value("FILE_IMPORT"))
            .andExpect(jsonPath("$.rows[0].candidate.status").value("READY_TO_POST"))
            .andExpect(jsonPath("$.rows[0].candidate.validationStatus").value("VALID"))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("NOT_EVALUATED"))
            .andExpect(jsonPath("$.rows[0].candidate.descriptionReviewStatus").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.rows[0].candidate.transactionDate").value("2026-01-16"))
            .andExpect(jsonPath("$.rows[0].candidate.postingDate").doesNotExist())
            .andExpect(jsonPath("$.rows[0].candidate.description").value("OXXO AGUILAS"))
            .andExpect(jsonPath("$.rows[0].candidate.signedAmount").value(-274.00))
            .andExpect(jsonPath("$.rows[0].candidate.amount").value(274.00))
            .andExpect(jsonPath("$.rows[0].candidate.flow").value("OUT"))
            .andExpect(jsonPath("$.rows[0].candidate.currencySnapshot").value("MXN"))
            .andExpect(jsonPath("$.rows[0].candidate.accountId").value(ingestion.getAccount().getId()))
            .andExpect(jsonPath("$.rows[0].candidate.accountName").value(ingestion.getAccount().getName()))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").doesNotExist())
            .andExpect(jsonPath("$.rows[0].candidate.categorySource").doesNotExist())
            .andExpect(jsonPath("$.rows[0].candidate.tagIds").isArray())
            .andExpect(jsonPath("$.rows[0].candidate.tagIds.length()").value(0))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags").isArray())
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags.length()").value(0))
            .andExpect(jsonPath("$.rows[0].candidate.financialTransactionId").doesNotExist())
            .andExpect(jsonPath("$.rows[0].candidate.createdAt").exists())
            .andExpect(jsonPath("$.rows[0].candidate.updatedAt").exists());
    }

    @Test
    @Transactional
    void workflowCandidateSummaryIncludesCategoryAndTags() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag firstTag = persistTag("Business", currentMockUser());
        Tag secondTag = persistTag("Ride share", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(category);
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.setTags(new HashSet<>(Set.of(secondTag, firstTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(get(workflowUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].candidate.id").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categoryName").value("Transport"))
            .andExpect(jsonPath("$.rows[0].candidate.categorySource").value("MANUAL"))
            .andExpect(jsonPath("$.rows[0].candidate.tagIds[0]").value(firstTag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.tagIds[1]").value(secondTag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.tagNames[0]").value("Business"))
            .andExpect(jsonPath("$.rows[0].candidate.tagNames[1]").value("Ride share"))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags[0].tagId").value(firstTag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags[0].source").value("MANUAL"))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags[1].tagId").value(secondTag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags[1].source").value("MANUAL"))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("USER_SELECTED"));
    }

    @Test
    @Transactional
    void workflowCandidateSummaryIsAbsentBeforePrepareAndGetWorkflowIsReadOnly() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        List<String> rawDataBefore = recordsFor(ingestion).stream().map(IngestionRecord::getRawData).toList();
        long candidateCountBefore = transactionCandidateRepository.count();
        long financialTransactionCountBefore = financialTransactionRepository.count();

        mockMvc.perform(get(workflowUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].candidate").doesNotExist());

        assertThat(transactionCandidateRepository.count()).isEqualTo(candidateCountBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(recordsFor(ingestion).stream().map(IngestionRecord::getRawData).toList()).isEqualTo(rawDataBefore);
    }

    @Test
    @Transactional
    void workflowHandlesRowsWithoutCandidatesAfterPrepare() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithInvalidRow();
        IngestionRecord rejected = recordsFor(ingestion).get(0);
        IngestionRecord valid = recordsFor(ingestion).get(1);
        mockMvc.perform(post(reviewUrl(ingestion, rejected, "disable"))).andExpect(status().isOk());

        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate validCandidate = candidateForRecord(valid);

        mockMvc
            .perform(get(workflowUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].status").value("DISABLED"))
            .andExpect(jsonPath("$.rows[0].candidate").doesNotExist())
            .andExpect(jsonPath("$.rows[1].status").value("VALID"))
            .andExpect(jsonPath("$.rows[1].candidate.id").value(validCandidate.getId()));
    }

    @Test
    @Transactional
    void workflowDoesNotExposeForeignCandidateData() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        User otherUser = createOtherUser();
        TransactionCandidate foreignCandidate = new TransactionCandidate()
            .source(TransactionCandidateSource.FILE_IMPORT)
            .status(TransactionCandidateStatus.READY_TO_POST)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_APPLICABLE)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .transactionDate(LocalDate.parse("2026-01-16"))
            .description("Foreign candidate")
            .signedAmount(new java.math.BigDecimal("-274.00"))
            .amount(new java.math.BigDecimal("274.00"))
            .flow(TransactionFlow.OUT)
            .currencySnapshot(ingestion.getAccount().getCurrency())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(otherUser)
            .account(ingestion.getAccount())
            .transactionIngestion(ingestion)
            .ingestionRecord(record);
        transactionCandidateRepository.saveAndFlush(foreignCandidate);

        mockMvc.perform(get(workflowUrl(ingestion))).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].candidate").doesNotExist());
    }

    @Test
    @Transactional
    void confirmImportPostsPreparedCandidateAndIgnoresRequestBodyCategoryAndTags() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category candidateCategory = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Category payloadCategory = persistCategory("Other", CategoryType.EXPENSE, currentMockUser());
        Tag candidateTag = persistTag("Cash", currentMockUser());
        Tag payloadTag = persistTag("Payload tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(candidateCategory);
        candidate.setTags(new HashSet<>(Set.of(candidateTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImportWithIgnoredBody(
            ingestion,
            List.of(confirmSelection(record.getId(), payloadCategory.getId(), List.of(payloadTag.getId())))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1))
            .andExpect(jsonPath("$.rows[0].candidate.status").value("POSTED"));

        TransactionCandidate postedCandidate = candidateForRecord(record);
        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(postedCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(postedCandidate.getFinancialTransaction().getId()).isEqualTo(transaction.getId());
        assertThat(transaction.getCategory().getId()).isEqualTo(candidateCategory.getId());
        assertThat(transaction.getTags()).extracting(Tag::getId).containsExactly(candidateTag.getId());
        assertThat(transaction.getOrigin().name()).isEqualTo("FILE_IMPORT");
    }

    @Test
    @Transactional
    void fileImportCandidateClassificationPatchSetsCategoryTagsAndUserSelectedWithoutMutatingRawDataOrCreatingTransactions()
        throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag firstTag = persistTag("Business", currentMockUser());
        Tag secondTag = persistTag("Ride share", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        String rawDataBefore = record.getRawData();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            classificationPayload(category.getId(), List.of(firstTag.getId(), secondTag.getId()))
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.candidate.id").value(candidate.getId()))
            .andExpect(jsonPath("$.candidate.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.candidate.categoryName").value("Transport"))
            .andExpect(jsonPath("$.candidate.categorySource").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.tagIds[0]").value(firstTag.getId()))
            .andExpect(jsonPath("$.candidate.tagIds[1]").value(secondTag.getId()))
            .andExpect(jsonPath("$.candidate.selectedTags[0].source").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.selectedTags[1].source").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("USER_SELECTED"));

        TransactionCandidate updated = candidateForRecord(record);
        assertThat(updated.getCategory().getId()).isEqualTo(category.getId());
        assertThat(updated.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(firstTag.getId(), secondTag.getId());
        assertThat(updated.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(updated.getTagSource(firstTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(updated.getTagSource(secondTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(updated.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        assertThat(recordsFor(ingestion).get(0).getRawData()).isEqualTo(rawDataBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateClassificationPatchSupportsPreserveAndClearSemantics() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag originalTag = persistTag("Business", currentMockUser());
        Tag replacementTag = persistTag("Ride share", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(category.getId(), List.of(originalTag.getId()))))
            )
            .andExpect(status().isOk());

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(tagOnlyClassificationPayload(List.of(replacementTag.getId()))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.candidate.categorySource").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.tagIds[0]").value(replacementTag.getId()));

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").doesNotExist())
            .andExpect(jsonPath("$.candidate.categorySource").doesNotExist())
            .andExpect(jsonPath("$.candidate.tagIds.length()").value(0))
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("USER_SELECTED"));

        mockMvc
            .perform(patch(candidateClassificationUrl(ingestion, candidate)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateTagMultiSelectPromotesRetainedTagsToManualWithoutChangingCategoryProvenance() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category automaticCategory = persistCategory("Automatic transport", CategoryType.EXPENSE, currentMockUser());
        Tag automaticRetained = persistTag("Automatic retained", currentMockUser());
        Tag automaticRemoved = persistTag("Automatic removed", currentMockUser());
        Tag manualRetained = persistTag("Manual retained", currentMockUser());
        Tag manualAdded = persistTag("Manual added", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(automaticCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(automaticRetained, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(automaticRemoved, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(manualRetained, TransactionCandidateClassificationSource.MANUAL);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            tagOnlyClassificationPayload(List.of(automaticRetained.getId(), manualRetained.getId(), manualAdded.getId()))
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").value(automaticCategory.getId()))
            .andExpect(jsonPath("$.candidate.categorySource").value("AUTOMATIC"))
            .andExpect(jsonPath("$.candidate.selectedTags.length()").value(3));

        TransactionCandidate reloaded = candidateForRecord(record);
        assertThat(reloaded.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(reloaded.getTags())
            .extracting(Tag::getId)
            .containsExactlyInAnyOrder(automaticRetained.getId(), manualRetained.getId(), manualAdded.getId());
        assertThat(reloaded.getTagSource(automaticRetained)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(reloaded.getTagSource(manualRetained)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(reloaded.getTagSource(manualAdded)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(reloaded.getTagSource(automaticRemoved)).isNull();
        assertThat(candidateTagJoinRows(reloaded.getId()).longValue()).isEqualTo(3L);
    }

    @Test
    @Transactional
    void fileImportCandidateCategoryPatchReplacesAutomaticCategoryAndClearsWithoutChangingTagProvenance() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category automaticCategory = persistCategory("Suggested transport", CategoryType.EXPENSE, currentMockUser());
        Category manualCategory = persistCategory("Manual transport", CategoryType.EXPENSE, currentMockUser());
        Category replacementManualCategory = persistCategory("Replacement manual transport", CategoryType.EXPENSE, currentMockUser());
        Tag automaticTag = persistTag("Suggested tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(automaticCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(automaticTag, TransactionCandidateClassificationSource.AUTOMATIC);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(categoryOnlyClassificationPayload(manualCategory.getId())))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").value(manualCategory.getId()))
            .andExpect(jsonPath("$.candidate.categorySource").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.selectedTags[0].tagId").value(automaticTag.getId()))
            .andExpect(jsonPath("$.candidate.selectedTags[0].source").value("AUTOMATIC"));

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(categoryOnlyClassificationPayload(replacementManualCategory.getId())))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").value(replacementManualCategory.getId()))
            .andExpect(jsonPath("$.candidate.categorySource").value("MANUAL"))
            .andExpect(jsonPath("$.candidate.selectedTags[0].tagId").value(automaticTag.getId()))
            .andExpect(jsonPath("$.candidate.selectedTags[0].source").value("AUTOMATIC"));

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(categoryOnlyClassificationPayload(null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.categoryId").doesNotExist())
            .andExpect(jsonPath("$.candidate.categorySource").doesNotExist())
            .andExpect(jsonPath("$.candidate.selectedTags[0].source").value("AUTOMATIC"));

        TransactionCandidate reloaded = candidateForRecord(record);
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getCategorySource()).isNull();
        assertThat(reloaded.getTagSource(automaticTag)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
    }

    @Test
    @Transactional
    void fileImportCandidateClassificationPatchEnforcesCategoryFlowCompatibilityMatrix() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category expenseCategory = persistCategory("Expense", CategoryType.EXPENSE, currentMockUser());
        Category incomeCategory = persistCategory("Income", CategoryType.INCOME, currentMockUser());
        Category bothCategory = persistCategory("Both", CategoryType.BOTH, currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(expenseCategory.getId(), List.of())))
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(bothCategory.getId(), List.of())))
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(incomeCategory.getId(), List.of())))
            )
            .andExpect(status().isBadRequest());

        candidate = candidateForRecord(record);
        candidate.setSignedAmount(new java.math.BigDecimal("274.00"));
        candidate.setAmount(new java.math.BigDecimal("274.00"));
        candidate.setFlow(TransactionFlow.IN);
        candidate.setCategory(null);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(incomeCategory.getId(), List.of())))
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(bothCategory.getId(), List.of())))
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(expenseCategory.getId(), List.of())))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateClassificationPatchRejectsInvalidCategoryTagAndCandidateScope() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        TransactionIngestion otherIngestion = createWorkflowWithSingleValidRow();
        IngestionRecord otherRecord = recordsFor(otherIngestion).get(0);
        Category incompatibleIncomeCategory = persistCategory("Income", CategoryType.INCOME, currentMockUser());
        User otherUser = createOtherUser();
        Category foreignCategory = persistCategory("Foreign", CategoryType.EXPENSE, otherUser);
        Tag foreignTag = persistTag("Foreign tag", otherUser);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        mockMvc.perform(post(prepareCandidatesUrl(otherIngestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        TransactionCandidate otherCandidate = candidateForRecord(otherRecord);

        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(incompatibleIncomeCategory.getId(), List.of())))
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(foreignCategory.getId(), List.of())))
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of(foreignTag.getId()))))
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, otherCandidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateClassificationPatchRejectsWrongSourceFinalStatusAndNonValidRecord() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);

        candidate.setSource(TransactionCandidateSource.MANUAL);
        transactionCandidateRepository.saveAndFlush(candidate);
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
            )
            .andExpect(status().isBadRequest());

        candidate.setSource(TransactionCandidateSource.API_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
            )
            .andExpect(status().isBadRequest());

        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        for (TransactionCandidateStatus status : List.of(
            TransactionCandidateStatus.POSTED,
            TransactionCandidateStatus.CANCELLED,
            TransactionCandidateStatus.FAILED
        )) {
            candidate.setStatus(status);
            transactionCandidateRepository.saveAndFlush(candidate);
            mockMvc
                .perform(
                    patch(candidateClassificationUrl(ingestion, candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
                )
                .andExpect(status().isBadRequest());
        }

        candidate.setStatus(TransactionCandidateStatus.READY_TO_POST);
        transactionCandidateRepository.saveAndFlush(candidate);
        record.setStatus(IngestionRecordStatus.DISABLED);
        ingestionRecordRepository.saveAndFlush(record);
        mockMvc
            .perform(
                patch(candidateClassificationUrl(ingestion, candidate))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(classificationPayload(null, List.of())))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateRulePreviewUsesFileImportOriginAndIsReadOnly() throws Exception {
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Ride share", currentMockUser());
        TransactionRule rule = persistTransactionRule("File Uber rule", category, List.of(tag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        Instant updatedAtBefore = candidate.getUpdatedAt();
        String rawDataBefore = record.getRawData();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(candidateRulePreviewUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestionId").value(ingestion.getId()))
            .andExpect(jsonPath("$.rows[0].candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("PREVIEWED"))
            .andExpect(jsonPath("$.rows[0].suggestedCategory.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.rows[0].suggestedTags[0].tagId").value(tag.getId()))
            .andExpect(jsonPath("$.rows[0].matchedRules[0].ruleName").value("File Uber rule"))
            .andExpect(jsonPath("$.rows[0].hasSuggestions").value(true));

        TransactionCandidate reloaded = candidateForRecord(record);
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getTags()).isEmpty();
        assertThat(reloaded.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBefore);
        assertThat(recordsFor(ingestion).get(0).getRawData()).isEqualTo(rawDataBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateRulePreviewSupportsScopedCategoryAndTagReevaluationWithoutApplyingSelections() throws Exception {
        Category manualCategory = persistCategory("Manual", CategoryType.EXPENSE, currentMockUser());
        Category suggestedCategory = persistCategory("Suggested", CategoryType.EXPENSE, currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());
        Tag suggestedTag = persistTag("Suggested tag", currentMockUser());
        TransactionRule rule = persistTransactionRule("OXXO rule", suggestedCategory, List.of(suggestedTag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord oxxoRecord = recordsFor(ingestion).get(1);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(oxxoRecord);
        candidate.setCategory(manualCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.setTags(new HashSet<>(Set.of(manualTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);
        String rawDataBefore = oxxoRecord.getRawData();
        Instant updatedAtBefore = candidate.getUpdatedAt();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(
                post(candidateRulePreviewUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(candidatePreviewPayload(List.of(candidate.getId()), "CATEGORY")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].suggestedCategory.categoryId").value(suggestedCategory.getId()))
            .andExpect(jsonPath("$.rows[0].suggestedTags.length()").value(0))
            .andExpect(jsonPath("$.rows[0].hasSuggestions").value(true));

        mockMvc
            .perform(
                post(candidateRulePreviewUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(candidatePreviewPayload(List.of(candidate.getId()), "TAGS")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].suggestedCategory").doesNotExist())
            .andExpect(jsonPath("$.rows[0].suggestedTags[0].tagId").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.rows[0].hasSuggestions").value(true));

        mockMvc
            .perform(
                post(candidateRulePreviewUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(candidatePreviewPayload(List.of(candidate.getId()), "ALL")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].suggestedCategory.categoryId").value(suggestedCategory.getId()))
            .andExpect(jsonPath("$.rows[0].suggestedTags[0].tagId").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.rows[0].hasSuggestions").value(true));

        TransactionCandidate reloaded = candidateForRecord(oxxoRecord);
        assertThat(reloaded.getCategory().getId()).isEqualTo(manualCategory.getId());
        assertThat(reloaded.getTags()).extracting(Tag::getId).containsExactly(manualTag.getId());
        assertThat(reloaded.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(reloaded.getTagSource(manualTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(reloaded.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBefore);
        assertThat(ingestionRecordRepository.findById(oxxoRecord.getId()).orElseThrow().getRawData()).isEqualTo(rawDataBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateRulePreviewSupportsCandidateIdsAndReturnsSkippedRowsForNotEvaluableCandidates() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setDescription(null);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(
                post(candidateRulePreviewUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(candidateBatchPayload(List.of(candidate.getId()))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[0].error").value("Description is required for rule preview"));

        mockMvc
            .perform(
                post(candidateRulePreviewUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(candidateBatchPayload(List.of(candidate.getId() + 9999))))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateRulePreviewAndApplyRejectSourceMismatch() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setSource(TransactionCandidateSource.API_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(post(candidateRulePreviewUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc
            .perform(post(candidateApplyRulesUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fileImportCandidateApplyRulesUsesFillEmptyOnlyAndDoesNotMutateRawDataOrCreateTransactions() throws Exception {
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Ride share", currentMockUser());
        TransactionRule rule = persistTransactionRule("OXXO rule", category, List.of(tag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        String rawDataBefore = record.getRawData();
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(candidateApplyRulesUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[0].categoryApplied").value(true))
            .andExpect(jsonPath("$.rows[0].tagIdsApplied[0]").value(tag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").value(category.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categorySource").value("AUTOMATIC"))
            .andExpect(jsonPath("$.rows[0].candidate.tagIds[0]").value(tag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.selectedTags[0].source").value("AUTOMATIC"))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("SUGGESTED"));

        TransactionCandidate updated = candidateForRecord(record);
        assertThat(updated.getCategory().getId()).isEqualTo(category.getId());
        assertThat(updated.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        assertThat(updated.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(updated.getTagSource(tag)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(updated.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        assertThat(recordsFor(ingestion).get(0).getRawData()).isEqualTo(rawDataBefore);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateApplyRulesPreservesManualSelectionsAndAvoidsDuplicates() throws Exception {
        Category manualCategory = persistCategory("Manual", CategoryType.EXPENSE, currentMockUser());
        Category suggestedCategory = persistCategory("Suggested", CategoryType.EXPENSE, currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());
        Tag suggestedTag = persistTag("Suggested tag", currentMockUser());
        TransactionRule rule = persistTransactionRule("OXXO rule", suggestedCategory, List.of(manualTag, suggestedTag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(manualCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.setTags(new HashSet<>(Set.of(manualTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc
            .perform(post(candidateApplyRulesUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].categoryApplied").value(false))
            .andExpect(jsonPath("$.rows[0].tagIdsApplied.length()").value(1))
            .andExpect(jsonPath("$.rows[0].tagIdsApplied[0]").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").value(manualCategory.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("USER_SELECTED"));

        TransactionCandidate updated = candidateForRecord(record);
        assertThat(updated.getCategory().getId()).isEqualTo(manualCategory.getId());
        assertThat(updated.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(manualTag.getId(), suggestedTag.getId());
        assertThat(updated.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(updated.getTagSource(manualTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(updated.getTagSource(suggestedTag)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
    }

    @Test
    @Transactional
    void automaticScopedApplySynchronizesProvenanceAndCanReplaceManualValuesWhenUnprotected() throws Exception {
        Category oldCategory = persistCategory("Old automatic", CategoryType.EXPENSE, currentMockUser());
        Category suggestedCategory = persistCategory("Suggested automatic", CategoryType.EXPENSE, currentMockUser());
        Tag obsoleteAutomatic = persistTag("Obsolete automatic", currentMockUser());
        Tag retainedAutomatic = persistTag("Retained automatic", currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());
        Tag suggestedTag = persistTag("Suggested automatic", currentMockUser());
        TransactionRule rule = persistTransactionRule("OXXO automatic rule", suggestedCategory, List.of(retainedAutomatic, suggestedTag));
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(oldCategory);
        candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(obsoleteAutomatic, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(retainedAutomatic, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(manualTag, TransactionCandidateClassificationSource.MANUAL);
        transactionCandidateRepository.saveAndFlush(candidate);
        String rawDataBefore = record.getRawData();

        mockMvc
            .perform(
                post(candidateApplyRulesUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(automaticCandidateApplyPayload(List.of(candidate.getId()), "ALL", true)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").value(suggestedCategory.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.categorySource").value("AUTOMATIC"));

        TransactionCandidate protectedResult = candidateForRecord(record);
        assertThat(protectedResult.getTags())
            .extracting(Tag::getId)
            .containsExactlyInAnyOrder(retainedAutomatic.getId(), manualTag.getId(), suggestedTag.getId());
        assertThat(protectedResult.getTagSource(retainedAutomatic)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(protectedResult.getTagSource(manualTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(protectedResult.getTagSource(suggestedTag)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);

        mockMvc
            .perform(
                post(candidateApplyRulesUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(automaticCandidateApplyPayload(List.of(candidate.getId()), "TAGS", false)))
            )
            .andExpect(status().isOk());

        TransactionCandidate unprotectedResult = candidateForRecord(record);
        assertThat(unprotectedResult.getCategory().getId()).isEqualTo(suggestedCategory.getId());
        assertThat(unprotectedResult.getTags())
            .extracting(Tag::getId)
            .containsExactlyInAnyOrder(retainedAutomatic.getId(), suggestedTag.getId());
        assertThat(unprotectedResult.getTagAssociations()).allSatisfy(association ->
            assertThat(association.getSource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC)
        );

        rule.setActive(false);
        transactionRuleRepository.saveAndFlush(rule);
        mockMvc
            .perform(
                post(candidateApplyRulesUrl(ingestion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(automaticCandidateApplyPayload(List.of(candidate.getId()), "CATEGORY", true)))
            )
            .andExpect(status().isOk());

        TransactionCandidate noSuggestionResult = candidateForRecord(record);
        assertThat(noSuggestionResult.getCategory()).isNull();
        assertThat(noSuggestionResult.getCategorySource()).isNull();
        assertThat(noSuggestionResult.getTags())
            .extracting(Tag::getId)
            .containsExactlyInAnyOrder(retainedAutomatic.getId(), suggestedTag.getId());
        assertThat(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData()).isEqualTo(rawDataBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateBatchApplyWithoutCandidateIdsUsesFillEmptyOnlyAcrossTheIngestion() throws Exception {
        Category suggestedExpenseCategory = persistCategory("Suggested expense", CategoryType.EXPENSE, currentMockUser());
        Category manualIncomeCategory = persistCategory("Manual income", CategoryType.INCOME, currentMockUser());
        Tag suggestedTag = persistTag("Suggested tag", currentMockUser());
        Tag manualTag = persistTag("Manual tag", currentMockUser());

        TransactionRule oxxoRule = persistTransactionRule("OXXO batch rule", suggestedExpenseCategory, List.of(suggestedTag));
        persistTransactionRuleCondition(oxxoRule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(oxxoRule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        TransactionRule uberRule = persistTransactionRule("Uber batch rule", suggestedExpenseCategory, List.of(suggestedTag));
        persistTransactionRuleCondition(uberRule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "Uber");
        persistTransactionRuleCondition(uberRule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");

        TransactionIngestion ingestion = createWorkflowWithValidRows();
        List<IngestionRecord> records = recordsFor(ingestion);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate manualCandidate = candidateForRecord(records.get(0));
        TransactionCandidate oxxoCandidate = candidateForRecord(records.get(1));
        TransactionCandidate uberCandidate = candidateForRecord(records.get(2));
        manualCandidate.setCategory(manualIncomeCategory);
        manualCandidate.setTags(new HashSet<>(Set.of(manualTag)));
        manualCandidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(manualCandidate);
        Map<Long, String> rawDataBefore = records.stream().collect(Collectors.toMap(IngestionRecord::getId, IngestionRecord::getRawData));
        long transactionCountBefore = financialTransactionRepository.count();

        mockMvc
            .perform(post(candidateApplyRulesUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(3))
            .andExpect(jsonPath("$.rows[0].candidateId").value(manualCandidate.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("UNCHANGED"))
            .andExpect(jsonPath("$.rows[0].candidate.categoryId").value(manualIncomeCategory.getId()))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("USER_SELECTED"))
            .andExpect(jsonPath("$.rows[1].candidateId").value(oxxoCandidate.getId()))
            .andExpect(jsonPath("$.rows[1].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[1].candidate.categoryId").value(suggestedExpenseCategory.getId()))
            .andExpect(jsonPath("$.rows[1].candidate.tagIds[0]").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.rows[2].candidateId").value(uberCandidate.getId()))
            .andExpect(jsonPath("$.rows[2].action").value("APPLIED"))
            .andExpect(jsonPath("$.rows[2].candidate.categoryId").value(suggestedExpenseCategory.getId()))
            .andExpect(jsonPath("$.rows[2].candidate.tagIds[0]").value(suggestedTag.getId()));

        assertThat(candidateForRecord(records.get(0)).getCategory().getId()).isEqualTo(manualIncomeCategory.getId());
        assertThat(candidateForRecord(records.get(0)).getTags()).extracting(Tag::getId).containsExactly(manualTag.getId());
        assertThat(candidateForRecord(records.get(1)).getCategory().getId()).isEqualTo(suggestedExpenseCategory.getId());
        assertThat(candidateForRecord(records.get(1)).getTags()).extracting(Tag::getId).containsExactly(suggestedTag.getId());
        assertThat(candidateForRecord(records.get(2)).getCategory().getId()).isEqualTo(suggestedExpenseCategory.getId());
        assertThat(candidateForRecord(records.get(2)).getTags()).extracting(Tag::getId).containsExactly(suggestedTag.getId());
        assertThat(recordsFor(ingestion)).allSatisfy(record -> assertThat(record.getRawData()).isEqualTo(rawDataBefore.get(record.getId()))
        );
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    @Transactional
    void fileImportCandidateApplyRulesSetsNotApplicableWhenNoSuggestionsExist() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);

        mockMvc
            .perform(post(candidateApplyRulesUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.rows[0].action").value("UNCHANGED"))
            .andExpect(jsonPath("$.rows[0].hasSuggestions").value(false))
            .andExpect(jsonPath("$.rows[0].candidate.classificationReviewStatus").value("NOT_APPLICABLE"));
    }

    @Test
    @Transactional
    void fileImportCandidateConfirmNoSuggestionsSetsNotApplicableOnlyWhenNoSuggestionsExist() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);

        mockMvc
            .perform(post(candidateConfirmNoSuggestionsUrl(ingestion, candidate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("NOT_APPLICABLE"));

        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        TransactionRule rule = persistTransactionRule("OXXO rule", category, List.of());
        persistTransactionRuleCondition(rule, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "OXXO");
        persistTransactionRuleCondition(rule, TransactionRuleField.ORIGIN, RuleOperator.EQUALS, "FILE_IMPORT");
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        transactionCandidateRepository.saveAndFlush(candidate);

        mockMvc.perform(post(candidateConfirmNoSuggestionsUrl(ingestion, candidate))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void oldClassificationPreviewEndpointIsRemoved() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();

        mockMvc
            .perform(post("/api/transaction-ingestions/{id}/classification-preview", ingestion.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void confirmCreatesFinancialTransactionFromCandidateAndPreservesRawData() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category category = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Tag tag = persistTag("Cash", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        String rawDataBefore = record.getRawData();
        candidate.setDescription("Candidate reviewed description");
        candidate.setSignedAmount(new java.math.BigDecimal("-12.34"));
        candidate.setAmount(new java.math.BigDecimal("12.34"));
        candidate.setFlow(TransactionFlow.OUT);
        candidate.setExternalReference("candidate-ref");
        candidate.setNotes("candidate notes");
        candidate.setCategory(category);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1))
            .andExpect(jsonPath("$.rows[0].status").value("IMPORTED"))
            .andExpect(jsonPath("$.rows[0].candidate.status").value("POSTED"))
            .andExpect(jsonPath("$.rows[0].candidate.financialTransactionId").exists());

        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(transaction.getDescription()).isEqualTo("Candidate reviewed description");
        assertThat(transaction.getAmount()).isEqualByComparingTo("12.34");
        assertThat(transaction.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(transaction.getExternalReference()).isEqualTo("candidate-ref");
        assertThat(transaction.getNotes()).isEqualTo("candidate notes");
        assertThat(transaction.getCategory().getId()).isEqualTo(category.getId());
        assertThat(transaction.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
        assertThat(transaction.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
        assertThat(transaction.getTransactionIngestion().getId()).isEqualTo(ingestion.getId());

        TransactionCandidate postedCandidate = candidateForRecord(record);
        assertThat(postedCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(postedCandidate.getPostedAt()).isNotNull();
        assertThat(postedCandidate.getFinancialTransaction().getId()).isEqualTo(transaction.getId());

        IngestionRecord importedRecord = recordsFor(ingestion).get(0);
        assertThat(importedRecord.getStatus()).isEqualTo(IngestionRecordStatus.IMPORTED);
        assertThat(importedRecord.getFinancialTransaction().getId()).isEqualTo(transaction.getId());
        assertThat(importedRecord.getRawData()).isEqualTo(rawDataBefore);

        JsonNode rawData = objectMapper.readTree(importedRecord.getRawData());
        assertThat(rawData.path("normalized").has("categoryId")).isFalse();
        assertThat(rawData.path("normalized").has("tagIds")).isFalse();
        assertThat(rawData.path("review").has("categoryId")).isFalse();
        assertThat(rawData.path("review").has("tagIds")).isFalse();
    }

    @Test
    @Transactional
    void confirmImportIgnoresRequestBodyAndUsesPersistedCandidateCategoryAndTags() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Category candidateCategory = persistCategory("Transport", CategoryType.EXPENSE, currentMockUser());
        Category foreignCategory = persistCategory("Foreign", CategoryType.EXPENSE, createOtherUser());
        Tag candidateTag = persistTag("Candidate tag", currentMockUser());
        Tag foreignTag = persistTag("Foreign tag", createOtherUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(candidateCategory);
        candidate.setTags(new HashSet<>(Set.of(candidateTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImportWithIgnoredBody(
            ingestion,
            List.of(confirmSelection(record.getId(), foreignCategory.getId(), List.of(foreignTag.getId(), foreignTag.getId())))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(transaction.getCategory().getId()).isEqualTo(candidateCategory.getId());
        assertThat(transaction.getTags()).extracting(Tag::getId).containsExactly(candidateTag.getId());
    }

    @Test
    @Transactional
    void confirmAcceptsNullBodyWhenCandidatesAreReviewed() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        FinancialTransaction transaction = financialTransactionRepository.findAll().get(0);
        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void confirmRejectsMissingCandidateForValidRecord() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Transaction candidate is required for each valid ingestion record"));

        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void confirmRejectsInvalidCandidateSourceStatusValidationClassificationAndAccount() throws Exception {
        for (TransactionCandidateSource source : List.of(TransactionCandidateSource.MANUAL, TransactionCandidateSource.API_IMPORT)) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            TransactionCandidate candidate = preparedCandidateForConfirm(
                ingestion,
                TransactionCandidateClassificationReviewStatus.USER_SELECTED
            );
            candidate.setSource(source);
            transactionCandidateRepository.saveAndFlush(candidate);

            confirmImport(ingestion).andExpect(status().isBadRequest());
        }

        for (TransactionCandidateStatus status : List.of(
            TransactionCandidateStatus.DRAFT,
            TransactionCandidateStatus.CANCELLED,
            TransactionCandidateStatus.FAILED
        )) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            TransactionCandidate candidate = preparedCandidateForConfirm(
                ingestion,
                TransactionCandidateClassificationReviewStatus.USER_SELECTED
            );
            candidate.setStatus(status);
            transactionCandidateRepository.saveAndFlush(candidate);

            confirmImport(ingestion).andExpect(status().isBadRequest());
        }

        for (TransactionCandidateValidationStatus validationStatus : List.of(
            TransactionCandidateValidationStatus.UNKNOWN,
            TransactionCandidateValidationStatus.INVALID,
            TransactionCandidateValidationStatus.STALE
        )) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            TransactionCandidate candidate = preparedCandidateForConfirm(
                ingestion,
                TransactionCandidateClassificationReviewStatus.USER_SELECTED
            );
            candidate.setValidationStatus(validationStatus);
            transactionCandidateRepository.saveAndFlush(candidate);

            confirmImport(ingestion).andExpect(status().isBadRequest());
        }

        for (TransactionCandidateClassificationReviewStatus classificationStatus : List.of(
            TransactionCandidateClassificationReviewStatus.NOT_EVALUATED,
            TransactionCandidateClassificationReviewStatus.STALE
        )) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            TransactionCandidate candidate = preparedCandidateForConfirm(
                ingestion,
                TransactionCandidateClassificationReviewStatus.USER_SELECTED
            );
            candidate.setClassificationReviewStatus(classificationStatus);
            transactionCandidateRepository.saveAndFlush(candidate);

            confirmImport(ingestion).andExpect(status().isBadRequest());
        }

        TransactionIngestion accountMismatch = createWorkflowWithSingleValidRow();
        TransactionCandidate candidate = preparedCandidateForConfirm(
            accountMismatch,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        candidate.setAccount(createCurrentUserAccount());
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(accountMismatch).andExpect(status().isBadRequest());

        TransactionIngestion incompatibleCategory = createWorkflowWithSingleValidRow();
        candidate = preparedCandidateForConfirm(incompatibleCategory, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        candidate.setCategory(persistCategory("Income", CategoryType.INCOME, currentMockUser()));
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(incompatibleCategory).andExpect(status().isBadRequest());

        User otherUser = createOtherUser();
        TransactionIngestion foreignCategory = createWorkflowWithSingleValidRow();
        candidate = preparedCandidateForConfirm(foreignCategory, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        candidate.setCategory(persistCategory("Foreign category", CategoryType.EXPENSE, otherUser));
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(foreignCategory).andExpect(status().isBadRequest());

        TransactionIngestion foreignTag = createWorkflowWithSingleValidRow();
        candidate = preparedCandidateForConfirm(foreignTag, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        candidate.setTags(new HashSet<>(Set.of(persistTag("Foreign tag", otherUser))));
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(foreignTag).andExpect(status().isBadRequest());
        assertThat(financialTransactionRepository.count()).isZero();
    }

    @Test
    @Transactional
    void confirmAllowsReviewedClassificationStatuses() throws Exception {
        for (TransactionCandidateClassificationReviewStatus classificationStatus : List.of(
            TransactionCandidateClassificationReviewStatus.SUGGESTED,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED,
            TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE
        )) {
            TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
            preparedCandidateForConfirm(ingestion, classificationStatus);

            confirmImport(ingestion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdNow").value(1))
                .andExpect(jsonPath("$.rows[0].candidate.status").value("POSTED"))
                .andExpect(jsonPath("$.rows[0].candidate.financialTransactionId").exists());
        }
    }

    @Test
    @Transactional
    void confirmRollsBackAllRowsWhenAnyCandidateIsInvalid() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        List<IngestionRecord> records = recordsFor(ingestion);
        TransactionCandidate invalidCandidate = candidateForRecord(records.get(2));
        invalidCandidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.STALE);
        transactionCandidateRepository.saveAndFlush(invalidCandidate);

        confirmImport(ingestion).andExpect(status().isBadRequest());

        assertThat(financialTransactionRepository.count()).isZero();
        assertThat(recordsFor(ingestion)).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(IngestionRecordStatus.VALID));
        assertThat(
            transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
        ).allSatisfy(candidate -> assertThat(candidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST));
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void confirmRejectsMixedPartialPostedCandidateWhenParentIsNotCompleted() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(ingestion.getAccount());
        financialTransaction.setTransactionIngestion(ingestion);
        financialTransaction = financialTransactionRepository.saveAndFlush(financialTransaction);
        candidate.setStatus(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(financialTransaction);
        candidate.setPostedAt(Instant.now());
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion).andExpect(status().isBadRequest());

        assertThat(ingestionRecordRepository.findById(record.getId()).orElseThrow().getStatus()).isEqualTo(IngestionRecordStatus.VALID);
        assertThat(financialTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void confirmReadyIngestionCreatesFinancialTransactionsFromNormalizedRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        IngestionRecord disabledRecord = recordsFor(ingestion).get(0);
        JsonNode originalRaw = objectMapper.readTree(disabledRecord.getRawData()).path("raw");
        mockMvc.perform(post(reviewUrl(ingestion, disabledRecord, "disable"))).andExpect(status().isOk());
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);

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
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);

        confirmImport(ingestion).andExpect(status().isOk()).andExpect(jsonPath("$.createdNow").value(1));
        long financialTransactionCountAfterFirstConfirm = financialTransactionRepository.count();

        mockMvc
            .perform(post(confirmUrl(ingestion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(0))
            .andExpect(jsonPath("$.alreadyImported").value(1));

        IngestionRecord importedRecord = recordsFor(ingestion).get(0);
        confirmImportWithIgnoredBody(ingestion, List.of(confirmSelection(importedRecord.getId(), null, List.of())))
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
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setCategory(category);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.createdNow").value(1));

        Long financialTransactionId = financialTransactionRepository.findAll().get(0).getId();
        Long candidateId = candidate.getId();
        Number tagJoinRowsBeforeDelete = (Number) em
            .createNativeQuery("select count(*) from rel_financial_transaction__tags where financial_transaction_id = :transactionId")
            .setParameter("transactionId", financialTransactionId)
            .getSingleResult();
        assertThat(tagJoinRowsBeforeDelete.longValue()).isEqualTo(1L);
        Number candidateTagJoinRowsBeforeDelete = candidateTagJoinRows(candidateId);
        assertThat(candidateTagJoinRowsBeforeDelete.longValue()).isEqualTo(1L);

        mockMvc
            .perform(delete("/api/transaction-ingestions/{id}", ingestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        Number tagJoinRowsAfterDelete = (Number) em
            .createNativeQuery("select count(*) from rel_financial_transaction__tags where financial_transaction_id = :transactionId")
            .setParameter("transactionId", financialTransactionId)
            .getSingleResult();
        assertThat(tagJoinRowsAfterDelete.longValue()).isZero();
        assertThat(candidateTagJoinRows(candidateId).longValue()).isZero();
        assertThat(fileIngestionRepository.findById(fileIngestionId)).isEmpty();
        assertThat(transactionCandidateRepository.findById(candidateId)).isEmpty();
        assertThat(ingestionRecordRepository.findById(record.getId())).isEmpty();
        assertThat(financialTransactionRepository.findById(financialTransactionId)).isEmpty();
        assertThat(transactionIngestionRepository.findById(ingestion.getId())).isEmpty();
        assertThat(categoryRepository.findById(category.getId())).isPresent();
        assertThat(tagRepository.findById(tag.getId())).isPresent();
    }

    @Test
    @Transactional
    void deleteWorkflowWithPreparedCandidatesCleansCandidatesAndCandidateTagJoins() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        Tag tag = persistTag("Prepared tag", currentMockUser());
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        TransactionCandidate candidate = candidateForRecord(record);
        candidate.setTags(new HashSet<>(Set.of(tag)));
        transactionCandidateRepository.saveAndFlush(candidate);
        Long candidateId = candidate.getId();

        assertThat(candidateTagJoinRows(candidateId).longValue()).isEqualTo(1L);

        mockMvc
            .perform(delete("/api/transaction-ingestions/{id}", ingestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertThat(candidateTagJoinRows(candidateId).longValue()).isZero();
        assertThat(transactionCandidateRepository.findById(candidateId)).isEmpty();
        assertThat(ingestionRecordRepository.findById(record.getId())).isEmpty();
        assertThat(transactionIngestionRepository.findById(ingestion.getId())).isEmpty();
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
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
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
    void confirmRejectsPostedCandidateBeforeParentCompletedWhenRowIsNotImported() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        JsonNode rawDataBefore = objectMapper.readTree(record.getRawData());
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        FinancialTransaction financialTransaction = createExistingFileTransaction(ingestion);
        candidate.setStatus(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(financialTransaction);
        candidate.setPostedAt(Instant.now());
        transactionCandidateRepository.saveAndFlush(candidate);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Posted transaction candidate requires an imported ingestion record"));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
        IngestionRecord persistedRecord = ingestionRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(persistedRecord.getStatus()).isEqualTo(IngestionRecordStatus.VALID);
        assertThat(objectMapper.readTree(persistedRecord.getRawData())).isEqualTo(rawDataBefore);
    }

    @Test
    @Transactional
    void confirmRejectsImportedRowWhenCandidateIsNotPosted() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        FinancialTransaction financialTransaction = createExistingFileTransaction(ingestion);
        record.setStatus(IngestionRecordStatus.IMPORTED);
        record.setFinancialTransaction(financialTransaction);
        ingestionRecordRepository.saveAndFlush(record);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Imported ingestion record requires a posted transaction candidate"));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
        TransactionCandidate persistedCandidate = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(persistedCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
        assertThat(persistedCandidate.getFinancialTransaction()).isNull();
    }

    @Test
    @Transactional
    void confirmRejectsCandidateLinkedToDisabledRowAndDoesNotImportOtherValidRows() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        List<IngestionRecord> records = recordsFor(ingestion);
        records.get(0).setStatus(IngestionRecordStatus.DISABLED);
        ingestionRecordRepository.saveAndFlush(records.get(0));

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Transaction candidate is linked to a non-valid ingestion record"));

        assertThat(financialTransactionRepository.count()).isZero();
        assertThat(recordsFor(ingestion))
            .extracting(IngestionRecord::getStatus)
            .containsExactly(IngestionRecordStatus.DISABLED, IngestionRecordStatus.VALID, IngestionRecordStatus.VALID);
        assertThat(
            transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
        ).allSatisfy(candidate -> assertThat(candidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST));
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void confirmRejectsCandidateLinkedToRejectedFailedOrSkippedRowAndDoesNotImportOtherValidRows() throws Exception {
        for (IngestionRecordStatus staleRowStatus : List.of(
            IngestionRecordStatus.REJECTED,
            IngestionRecordStatus.FAILED,
            IngestionRecordStatus.SKIPPED_DUPLICATE
        )) {
            TransactionIngestion ingestion = createWorkflowWithValidRows();
            prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            List<IngestionRecord> records = recordsFor(ingestion);
            records.get(0).setStatus(staleRowStatus);
            ingestionRecordRepository.saveAndFlush(records.get(0));

            confirmImport(ingestion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Transaction candidate is linked to a non-valid ingestion record"));

            assertThat(financialTransactionRepository.count()).isZero();
            assertThat(recordsFor(ingestion))
                .extracting(IngestionRecord::getStatus)
                .containsExactly(staleRowStatus, IngestionRecordStatus.VALID, IngestionRecordStatus.VALID);
            assertThat(
                transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
            ).allSatisfy(candidate -> assertThat(candidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST));
            assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(
                IngestionStatus.READY
            );
        }
    }

    @Test
    @Transactional
    void confirmRejectsCandidateRecordMismatchAcrossIngestions() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        TransactionIngestion otherIngestion = createWorkflowWithSingleValidRow();
        IngestionRecord otherRecord = recordsFor(otherIngestion).get(0);
        candidate.setIngestionRecord(otherRecord);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Transaction candidate does not belong to this ingestion"));

        assertThat(financialTransactionRepository.count()).isZero();
        assertThat(recordsFor(ingestion)).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(IngestionRecordStatus.VALID));
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void confirmRejectsPostedCandidateFinancialTransactionMismatch() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        FinancialTransaction recordTransaction = createExistingFileTransaction(ingestion);
        FinancialTransaction candidateTransaction = createExistingFileTransaction(ingestion);
        record.setStatus(IngestionRecordStatus.IMPORTED);
        record.setFinancialTransaction(recordTransaction);
        ingestionRecordRepository.saveAndFlush(record);
        candidate.setStatus(TransactionCandidateStatus.POSTED);
        candidate.setFinancialTransaction(candidateTransaction);
        candidate.setPostedAt(Instant.now());
        transactionCandidateRepository.saveAndFlush(candidate);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Posted transaction candidate financial transaction must match ingestion record"));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void confirmRejectsUnpostedCandidateWithFinancialTransactionLink() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        candidate.setFinancialTransaction(createExistingFileTransaction(ingestion));
        transactionCandidateRepository.saveAndFlush(candidate);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unposted transaction candidate cannot be linked to a financial transaction"));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(recordsFor(ingestion).get(0).getStatus()).isEqualTo(IngestionRecordStatus.VALID);
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void confirmRejectsFileImportCandidateWithoutIngestionRecord() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        candidate.setIngestionRecord(null);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Transaction candidate is missing its ingestion record"));

        assertThat(financialTransactionRepository.count()).isZero();
        TransactionCandidate orphanCandidate = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(orphanCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
        assertThat(orphanCandidate.getFinancialTransaction()).isNull();
    }

    @Test
    @Transactional
    void confirmDoesNotImportOrphanFileImportCandidateWithoutIngestion() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        TransactionCandidate candidate = preparedCandidateForConfirm(
            ingestion,
            TransactionCandidateClassificationReviewStatus.USER_SELECTED
        );
        candidate.setTransactionIngestion(null);
        transactionCandidateRepository.saveAndFlush(candidate);

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Transaction candidate is required for each valid ingestion record"));

        assertThat(financialTransactionRepository.count()).isZero();
        TransactionCandidate orphanCandidate = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(orphanCandidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
        assertThat(orphanCandidate.getFinancialTransaction()).isNull();
    }

    @Test
    @Transactional
    void confirmRollsBackAllRowsWhenAnyCandidateHasCorruptFinancialTransactionLink() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithValidRows();
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        List<IngestionRecord> records = recordsFor(ingestion);
        TransactionCandidate corruptCandidate = candidateForRecord(records.get(1));
        FinancialTransaction existingTransaction = createExistingFileTransaction(ingestion);
        corruptCandidate.setFinancialTransaction(existingTransaction);
        transactionCandidateRepository.saveAndFlush(corruptCandidate);
        long financialTransactionCountBefore = financialTransactionRepository.count();

        confirmImport(ingestion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unposted transaction candidate cannot be linked to a financial transaction"));

        assertThat(financialTransactionRepository.count()).isEqualTo(financialTransactionCountBefore);
        assertThat(recordsFor(ingestion)).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(IngestionRecordStatus.VALID));
        assertThat(
            transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), "user")
        ).allSatisfy(candidate -> assertThat(candidate.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST));
        assertThat(transactionIngestionRepository.findById(ingestion.getId()).orElseThrow().getStatus()).isEqualTo(IngestionStatus.READY);
    }

    @Test
    @Transactional
    void completedIngestionRowsCannotBeReviewed() throws Exception {
        TransactionIngestion ingestion = createWorkflowWithSingleValidRow();
        IngestionRecord record = recordsFor(ingestion).get(0);
        prepareCandidatesForConfirm(ingestion, TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
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

    private TransactionIngestion createWorkflowWithRuleNormalizedUberDescription() throws Exception {
        DescriptionNormalizationRule rule = persistDescriptionNormalizationRule("Normalize Uber", "Uber");
        persistDescriptionNormalizationCondition(rule, "Uber");
        return createWorkflowWithValidRows();
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

    private String workflowUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/workflow";
    }

    private String confirmUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/confirm";
    }

    private String descriptionReevaluationUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/descriptions/reevaluate";
    }

    private String prepareCandidatesUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/prepare";
    }

    private String candidateClassificationUrl(TransactionIngestion ingestion, TransactionCandidate candidate) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/" + candidate.getId() + "/classification";
    }

    private String candidateRulePreviewUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/rule-preview";
    }

    private String candidateApplyRulesUrl(TransactionIngestion ingestion) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/apply-rules";
    }

    private String candidateConfirmNoSuggestionsUrl(TransactionIngestion ingestion, TransactionCandidate candidate) {
        return "/api/transaction-ingestions/" + ingestion.getId() + "/candidates/" + candidate.getId() + "/confirm-no-suggestions";
    }

    private ResultActions confirmImport(TransactionIngestion ingestion) throws Exception {
        return mockMvc.perform(post(confirmUrl(ingestion)));
    }

    private ResultActions confirmImportWithIgnoredBody(TransactionIngestion ingestion, List<Map<String, Object>> recordSelections)
        throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", recordSelections);
        return mockMvc.perform(
            post(confirmUrl(ingestion)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload))
        );
    }

    private void prepareCandidatesForConfirm(
        TransactionIngestion ingestion,
        TransactionCandidateClassificationReviewStatus classificationReviewStatus
    ) throws Exception {
        mockMvc.perform(post(prepareCandidatesUrl(ingestion))).andExpect(status().isOk());
        List<TransactionCandidate> candidates = transactionCandidateRepository.findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(
            ingestion.getId(),
            "user"
        );
        candidates.forEach(candidate -> {
            candidate.setClassificationReviewStatus(classificationReviewStatus);
            candidate.setUpdatedAt(Instant.now());
        });
        transactionCandidateRepository.saveAllAndFlush(candidates);
    }

    private TransactionCandidate preparedCandidateForConfirm(
        TransactionIngestion ingestion,
        TransactionCandidateClassificationReviewStatus classificationReviewStatus
    ) throws Exception {
        prepareCandidatesForConfirm(ingestion, classificationReviewStatus);
        return candidateForRecord(recordsFor(ingestion).get(0));
    }

    private Map<String, Object> confirmSelection(Long recordId, Long categoryId, List<Long> tagIds) {
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("recordId", recordId);
        selection.put("categoryId", categoryId);
        selection.put("tagIds", tagIds);
        return selection;
    }

    private Map<String, Object> classificationPayload(Long categoryId, List<Long> tagIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", categoryId);
        payload.put("tagIds", tagIds);
        return payload;
    }

    private Map<String, Object> tagOnlyClassificationPayload(List<Long> tagIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tagIds", tagIds);
        return payload;
    }

    private Map<String, Object> categoryOnlyClassificationPayload(Long categoryId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", categoryId);
        return payload;
    }

    private Map<String, Object> candidateBatchPayload(List<Long> candidateIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidateIds", candidateIds);
        return payload;
    }

    private Map<String, Object> candidatePreviewPayload(List<Long> candidateIds, String scope) {
        Map<String, Object> payload = candidateBatchPayload(candidateIds);
        payload.put("scope", scope);
        return payload;
    }

    private Map<String, Object> automaticCandidateApplyPayload(List<Long> candidateIds, String scope, boolean protectManualChanges) {
        Map<String, Object> payload = candidateBatchPayload(candidateIds);
        payload.put("scope", scope);
        payload.put("automatic", true);
        payload.put("protectManualChanges", protectManualChanges);
        return payload;
    }

    private Map<String, Object> descriptionReevaluationPayload(List<Long> recordIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordIds", recordIds);
        return payload;
    }

    private Map<String, Object> descriptionReevaluationPayload(List<Long> recordIds, boolean apply, boolean protectManualChanges) {
        Map<String, Object> payload = descriptionReevaluationPayload(recordIds);
        payload.put("apply", apply);
        payload.put("protectManualChanges", protectManualChanges);
        return payload;
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

    private JsonNode rawDataFor(IngestionRecord record) throws Exception {
        return objectMapper.readTree(ingestionRecordRepository.findById(record.getId()).orElseThrow().getRawData());
    }

    private JsonNode descriptionReviewFor(IngestionRecord record) throws Exception {
        return rawDataFor(record).path("review").path("description").deepCopy();
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

    private FinancialTransaction createExistingFileTransaction(TransactionIngestion ingestion) {
        FinancialTransaction financialTransaction = FinancialTransactionResourceIT.createEntity(em);
        financialTransaction.setAccount(ingestion.getAccount());
        financialTransaction.setOrigin(TransactionOrigin.FILE_IMPORT);
        financialTransaction.setTransactionIngestion(ingestion);
        return financialTransactionRepository.saveAndFlush(financialTransaction);
    }

    private TransactionCandidate candidateForRecord(IngestionRecord record) {
        return transactionCandidateRepository.findOneWithRelationshipsByIngestionRecordIdAndUserLogin(record.getId(), "user").orElseThrow();
    }

    private Number candidateTagJoinRows(Long candidateId) {
        return (Number) em
            .createNativeQuery("select count(*) from rel_transaction_candidate__tags where transaction_candidate_id = :candidateId")
            .setParameter("candidateId", candidateId)
            .getSingleResult();
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
