package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TransactionBatchAppendOperationRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.*;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed contracts for the internal append receipt and post-append rule orchestration. */
@IntegrationTest
@WithMockUser(username = "user")
class TransactionBatchCsvAppendServiceIT {

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionBatchCommandService commandService;

    @Autowired
    private TransactionBatchCsvAppendService appendService;

    @Autowired
    private TransactionBatchSettingsService settingsService;

    @Autowired
    private TransactionBatchRuleOrchestrationService ruleOrchestrationService;

    @Autowired
    private TransactionBatchRuleEvaluationService ruleEvaluationService;

    @Autowired
    private DescriptionNormalizationRuleService descriptionNormalizationRuleService;

    @Autowired
    private TransactionRuleService transactionRuleService;

    @Autowired
    private TransactionCandidateRepository candidateRepository;

    @Autowired
    private TransactionBatchAppendOperationRepository operationRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    private Long committedBatchId;
    private Long committedAccountId;

    /**
     * The append/re-evaluation boundary test intentionally has no surrounding test transaction. Remove only its
     * committed fixture so it cannot affect repository-wide count/filter assertions that run later in the same JVM.
     */
    @AfterEach
    void removeCommittedAppendFixture() {
        if (committedBatchId != null) {
            commandService
                .findOpenBatch(committedBatchId)
                .ifPresent(batch -> commandService.discardOpenBatch(committedBatchId, batch.getVersion()));
        }
        if (committedAccountId != null) {
            accountRepository.deleteById(committedAccountId);
        }
        committedBatchId = null;
        committedAccountId = null;
    }

    @Test
    void appendsRepairableCsvRowsAndReturnsAnIdempotentReceiptWithoutLegacyFileState() {
        FinancialAccount appendAccount = account();
        committedAccountId = appendAccount.getId();
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(appendAccount.getId());
        committedBatchId = batch.getId();
        TransactionBatchAppendResultDTO accepted = appendService.append(
            batch.getId(),
            csvCommand(batch.getVersion(), "append-a", validCsv())
        );

        assertThat(accepted.isReplayed()).isFalse();
        assertThat(accepted.getAcceptedCandidateCount()).isEqualTo(2);
        assertThat(accepted.getAppendedCandidateIds()).hasSize(2);
        assertThat(operationRepository.findOneByBatchIdAndOwnerLoginAndOperationKey(batch.getId(), "user", "append-a")).isPresent();

        List<TransactionBatchCandidateViewDTO> rows = commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates();
        assertThat(rows).extracting(TransactionBatchCandidateViewDTO::getRowPosition).containsExactly(1L, 2L);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getSource()).isEqualTo(TransactionCandidateSource.FILE_IMPORT);
            assertThat(row.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.VALID);
            assertThat(row.getDescriptionSource()).isEqualTo(TransactionCandidateDescriptionSource.SOURCE);
        });
        assertThat(rows).extracting(TransactionBatchCandidateViewDTO::getSourceDescription).containsExactly("Uber ride", "Salary");
        TransactionCandidate persisted = candidateRepository.findById(accepted.getAppendedCandidateIds().get(0)).orElseThrow();
        assertThat(persisted.getTransactionIngestion()).isNull();
        assertThat(persisted.getIngestionRecord()).isNull();
        assertThat(persisted.getSourceDescription()).isEqualTo("Uber ride");

        TransactionBatchCandidatePatchCommand patch = new TransactionBatchCandidatePatchCommand();
        patch.setExpectedCandidateVersion(rows.get(0).getVersion());
        patch.setNotes("kept after replay");
        commandService.patchCandidate(batch.getId(), rows.get(0).getId(), patch);

        TransactionBatchWorkspaceDTO afterEdit = commandService.findOpenBatch(batch.getId()).orElseThrow();
        TransactionBatchAppendResultDTO secondAppend = appendService.append(
            batch.getId(),
            csvCommand(afterEdit.getVersion(), "append-b", header() + "2026-10-03,,Later row,-10.00,MXN,later,\n")
        );
        assertThat(secondAppend.getAcceptedCandidateCount()).isOne();

        TransactionBatchAppendResultDTO replay = appendService.append(
            batch.getId(),
            csvCommand(batch.getVersion(), "append-a", validCsv())
        );
        assertThat(replay.isReplayed()).isTrue();
        assertThat(replay.getAcceptedCandidateCount()).isEqualTo(2);
        assertThat(replay.getAppendedCandidateIds()).isEmpty();
        assertThat(commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates()).hasSize(3);

        assertThatThrownBy(() -> appendService.append(batch.getId(), csvCommand(batch.getVersion(), "append-a", differentCsv())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT");
    }

    @Test
    @Transactional
    void retainsOnlyRepairEvidenceForInvalidCurrencyAndLetsTheRelevantPatchRepairIt() {
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account().getId());
        String csv = header() + "2026-10-01,,Foreign charge,-100.00,USD,,\n";
        TransactionBatchAppendResultDTO accepted = appendService.append(batch.getId(), csvCommand(batch.getVersion(), "currency-a", csv));
        TransactionBatchCandidateViewDTO candidate = commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates().get(0);

        assertThat(candidate.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.INVALID);
        assertThat(candidate.getSignedAmount()).isNull();
        assertThat(candidate.getCurrencySnapshot()).isNull();
        assertThat(candidate.getInputIssues())
            .singleElement()
            .satisfies(issue -> {
                assertThat(issue.getField()).isEqualTo(TransactionCandidateInputIssueField.CURRENCY);
                assertThat(issue.getReasonCode()).isEqualTo(TransactionCandidateInputIssueReason.CURRENCY_MISMATCH);
                assertThat(issue.getSubmittedValue()).isEqualTo("USD");
            });

        TransactionBatchCandidatePatchCommand repair = new TransactionBatchCandidatePatchCommand();
        repair.setExpectedCandidateVersion(candidate.getVersion());
        repair.setSignedAmount(new BigDecimal("-100.00"));
        TransactionBatchRowMutationDTO repaired = commandService.patchCandidate(batch.getId(), candidate.getId(), repair);
        assertThat(repaired.getCandidate().getInputIssues()).isEmpty();
        assertThat(repaired.getCandidate().getCurrencySnapshot()).isEqualTo(CurrencyCode.MXN);
        assertThat(repaired.getCandidate().getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.VALID);
        assertThat(accepted.getAcceptedCandidateCount()).isOne();
    }

    @Test
    @Transactional
    void evaluatesDescriptionBeforeClassificationPersistsDeletionSafeAttributionAndNeverNeedsALegacyIngestionAggregate() {
        FinancialAccount account = account();
        Category category = category(CategoryType.EXPENSE);
        Tag tag = tag();
        DescriptionNormalizationRule descriptionRule = descriptionRule("Normalize Uber", "uber", "UBER");
        TransactionRule classificationRule = classificationRule("Classify normalized Uber", "UBER", category, tag);

        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchSettingsCommand settings = new TransactionBatchSettingsCommand();
        settings.setExpectedBatchVersion(batch.getVersion());
        settings.setAutoApplyDescriptions(true);
        settings.setAutoApplyCategories(true);
        settings.setAutoApplyTags(true);
        settings.setProtectManualChanges(true);
        TransactionBatchSettingsResultDTO savedSettings = settingsService.save(batch.getId(), settings);

        TransactionBatchAppendResultDTO accepted = appendService.append(
            batch.getId(),
            csvCommand(savedSettings.getBatchVersion(), "rules-a", header() + "2026-10-01,,uber ride,-23.50,MXN,,\n")
        );
        TransactionBatchCandidateViewDTO row = commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates().get(0);

        assertThat(accepted.getEvaluationStatus()).isEqualTo("EVALUATED");
        assertThat(row.getDescription()).isEqualTo("UBER");
        assertThat(row.getDescriptionSource()).isEqualTo(TransactionCandidateDescriptionSource.AUTOMATIC);
        assertThat(row.getDescriptionAppliedByRule()).satisfies(attribution -> {
            assertThat(attribution.getId()).isEqualTo(descriptionRule.getId());
            assertThat(attribution.getName()).isEqualTo("Normalize Uber");
        });
        assertThat(row.getCategoryId()).isEqualTo(category.getId());
        assertThat(row.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(row.getCategoryAppliedByRule()).satisfies(attribution -> {
            assertThat(attribution.getId()).isEqualTo(classificationRule.getId());
            assertThat(attribution.getName()).isEqualTo("Classify normalized Uber");
        });
        assertThat(row.getTags())
            .singleElement()
            .satisfies(appliedTag -> {
                assertThat(appliedTag.getId()).isEqualTo(tag.getId());
                assertThat(appliedTag.getAppliedByRule()).satisfies(attribution -> {
                    assertThat(attribution.getId()).isEqualTo(classificationRule.getId());
                    assertThat(attribution.getName()).isEqualTo("Classify normalized Uber");
                });
            });
        assertThat(candidateRepository.findById(row.getId()).orElseThrow().getTransactionIngestion()).isNull();

        em.clear();
        assertThat(descriptionNormalizationRuleService.delete(descriptionRule.getId())).isTrue();
        assertThat(transactionRuleService.delete(classificationRule.getId())).isTrue();
        em.flush();
        em.clear();

        TransactionBatchCandidateViewDTO afterRuleDeletion = commandService
            .findOpenBatch(batch.getId())
            .orElseThrow()
            .getCandidates()
            .get(0);
        assertThat(afterRuleDeletion.getDescription()).isEqualTo("UBER");
        assertThat(afterRuleDeletion.getDescriptionAppliedByRule()).satisfies(attribution -> {
            assertThat(attribution.getId()).isNull();
            assertThat(attribution.getName()).isEqualTo("Normalize Uber");
        });
        assertThat(afterRuleDeletion.getCategoryId()).isEqualTo(category.getId());
        assertThat(afterRuleDeletion.getCategoryAppliedByRule()).satisfies(attribution -> {
            assertThat(attribution.getId()).isNull();
            assertThat(attribution.getName()).isEqualTo("Classify normalized Uber");
        });
        assertThat(afterRuleDeletion.getTags())
            .singleElement()
            .satisfies(appliedTag -> {
                assertThat(appliedTag.getId()).isEqualTo(tag.getId());
                assertThat(appliedTag.getAppliedByRule()).satisfies(attribution -> {
                    assertThat(attribution.getId()).isNull();
                    assertThat(attribution.getName()).isEqualTo("Classify normalized Uber");
                });
            });
    }

    @Test
    @Transactional
    void keepsPreviewsPureAndRespectsManualClassificationIntentUntilExplicitAcceptance() {
        FinancialAccount account = account();
        Category manualCategory = category(CategoryType.EXPENSE);
        Category ruleCategory = category(CategoryType.EXPENSE);
        Tag manualTag = tag();
        Tag ruleTag = tag();
        classificationRule("Classify coffee", "coffee", ruleCategory, ruleTag);

        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchAppendResultDTO accepted = appendService.append(
            batch.getId(),
            csvCommand(batch.getVersion(), "intent-a", header() + "2026-10-01,,Coffee shop,-23.50,MXN,,\n")
        );
        TransactionBatchWorkspaceDTO untouched = commandService.findOpenBatch(batch.getId()).orElseThrow();
        TransactionBatchCandidateViewDTO initial = untouched.getCandidates().get(0);

        List<TransactionBatchRulePreviewDTO> previews = ruleEvaluationService.previewBatch(batch.getId());
        TransactionBatchWorkspaceDTO afterPreview = commandService.findOpenBatch(batch.getId()).orElseThrow();
        assertThat(previews)
            .singleElement()
            .satisfies(preview -> {
                assertThat(preview.getSuggestedCategoryId()).isEqualTo(ruleCategory.getId());
                assertThat(preview.getSuggestedTagIds()).containsExactly(ruleTag.getId());
            });
        assertThat(afterPreview.getVersion()).isEqualTo(untouched.getVersion());
        assertThat(afterPreview.getCandidates().get(0).getVersion()).isEqualTo(initial.getVersion());

        TransactionBatchRowMutationDTO categorySet = commandService.setCategory(
            batch.getId(),
            initial.getId(),
            initial.getVersion(),
            manualCategory.getId()
        );
        TransactionBatchRowMutationDTO tagAdded = commandService.addTag(
            batch.getId(),
            initial.getId(),
            categorySet.getCandidate().getVersion(),
            manualTag.getId()
        );
        TransactionBatchSettingsCommand settings = new TransactionBatchSettingsCommand();
        settings.setExpectedBatchVersion(tagAdded.getBatchVersion());
        settings.setAutoApplyDescriptions(false);
        settings.setAutoApplyCategories(true);
        settings.setAutoApplyTags(true);
        settings.setProtectManualChanges(true);
        settingsService.save(batch.getId(), settings);

        ruleOrchestrationService.reevaluate(batch.getId());
        TransactionBatchCandidateViewDTO automaticallyReviewed = commandService
            .findOpenBatch(batch.getId())
            .orElseThrow()
            .getCandidates()
            .get(0);
        assertThat(automaticallyReviewed.getCategoryId()).isEqualTo(manualCategory.getId());
        assertThat(automaticallyReviewed.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(automaticallyReviewed.getTags())
            .extracting(TransactionBatchTagViewDTO::getId)
            .containsExactlyInAnyOrder(manualTag.getId(), ruleTag.getId());

        TransactionBatchRowMutationDTO tagRemoved = commandService.removeTag(
            batch.getId(),
            automaticallyReviewed.getId(),
            automaticallyReviewed.getVersion(),
            ruleTag.getId()
        );
        ruleOrchestrationService.reevaluate(batch.getId());
        TransactionBatchCandidateViewDTO suppressed = commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates().get(0);
        assertThat(suppressed.getTags()).extracting(TransactionBatchTagViewDTO::getId).containsExactly(manualTag.getId());

        TransactionBatchRuleApplyCommand apply = new TransactionBatchRuleApplyCommand();
        apply.setExpectedCandidateVersion(suppressed.getVersion());
        apply.setApplyTags(true);
        apply.setExpectedSuggestedTagIds(Set.of(ruleTag.getId()));
        ruleEvaluationService.applyExplicit(batch.getId(), suppressed.getId(), apply);

        TransactionBatchCandidateViewDTO explicitlyAccepted = commandService
            .findOpenBatch(batch.getId())
            .orElseThrow()
            .getCandidates()
            .get(0);
        assertThat(explicitlyAccepted.getTags())
            .extracting(TransactionBatchTagViewDTO::getId)
            .containsExactlyInAnyOrder(manualTag.getId(), ruleTag.getId());
        assertThat(accepted.getEvaluationStatus()).isEqualTo("EVALUATED");
        assertThat(tagRemoved.getCandidate().getId()).isEqualTo(initial.getId());
    }

    @Test
    @Transactional
    void rejectsStructuralCsvWithoutMutatingAndHandlesRepresentativeFortyAndFiveHundredRowAppends() {
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account().getId());

        assertThatThrownBy(() -> appendService.append(batch.getId(), csvCommand(batch.getVersion(), "bad-header", "wrong,header\n"))
        ).isInstanceOf(IllegalArgumentException.class);
        TransactionBatchWorkspaceDTO afterRejectedFile = commandService.findOpenBatch(batch.getId()).orElseThrow();
        assertThat(afterRejectedFile.getVersion()).isEqualTo(batch.getVersion());
        assertThat(afterRejectedFile.getCandidates()).isEmpty();

        TransactionBatchAppendResultDTO forty = appendService.append(
            batch.getId(),
            csvCommand(afterRejectedFile.getVersion(), "forty-rows", csvRows(40, 1))
        );
        TransactionBatchWorkspaceDTO afterForty = commandService.findOpenBatch(batch.getId()).orElseThrow();
        TransactionBatchAppendResultDTO fiveHundred = appendService.append(
            batch.getId(),
            csvCommand(afterForty.getVersion(), "five-hundred-rows", csvRows(500, 41))
        );
        List<TransactionBatchCandidateViewDTO> candidates = commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates();

        assertThat(forty.getAcceptedCandidateCount()).isEqualTo(40);
        assertThat(fiveHundred.getAcceptedCandidateCount()).isEqualTo(500);
        assertThat(candidates).hasSize(540);
        assertThat(candidates.get(0).getRowPosition()).isEqualTo(1L);
        assertThat(candidates.get(candidates.size() - 1).getRowPosition()).isEqualTo(540L);
    }

    @Test
    void concurrentSameKeyAppendAcceptsExactlyOneSetOfCandidatesAndReplaysTheOtherRequest() throws Exception {
        FinancialAccount appendAccount = account();
        committedAccountId = appendAccount.getId();
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(appendAccount.getId());
        committedBatchId = batch.getId();
        TransactionBatchAppendCsvCommand command = csvCommand(
            batch.getVersion(),
            "concurrent-key",
            header() + "2026-10-01,,Concurrent row,-23.50,MXN,,\n"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TransactionBatchAppendResultDTO> first = executor.submit(() -> concurrentAppend(batch.getId(), command, ready, start));
            Future<TransactionBatchAppendResultDTO> second = executor.submit(() -> concurrentAppend(batch.getId(), command, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<TransactionBatchAppendResultDTO> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(TransactionBatchAppendResultDTO::isReplayed).hasSize(1);
            assertThat(results).filteredOn(result -> !result.isReplayed()).hasSize(1);
            assertThat(commandService.findOpenBatch(batch.getId()).orElseThrow().getCandidates()).hasSize(1);
            assertThat(
                operationRepository.findOneByBatchIdAndOwnerLoginAndOperationKey(batch.getId(), "user", "concurrent-key")
            ).isPresent();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private TransactionBatchAppendCsvCommand csvCommand(Long expectedBatchVersion, String operationKey, String csv) {
        TransactionBatchAppendCsvCommand command = new TransactionBatchAppendCsvCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setOperationKey(operationKey);
        command.setFileName(operationKey + ".csv");
        command.setBytes(csv.getBytes(StandardCharsets.UTF_8));
        return command;
    }

    private String header() {
        return "transactionDate,postingDate,description,signedAmount,currency,externalReference,notes\n";
    }

    private String validCsv() {
        return header() + "2026-10-01,,Uber ride,-23.50,MXN,first,\n" + "2026-10-02,,Salary,1000.00,MXN,second,\n";
    }

    private String differentCsv() {
        return header() + "2026-10-03,,Different,-1.00,MXN,different,\n";
    }

    private String csvRows(int count, int firstRow) {
        StringBuilder csv = new StringBuilder(header());
        for (int index = firstRow; index < firstRow + count; index++) {
            csv.append("2026-10-01,,Bulk row ").append(index).append(",-1.00,MXN,bulk-").append(index).append(",\n");
        }
        return csv.toString();
    }

    private TransactionBatchAppendResultDTO concurrentAppend(
        Long batchId,
        TransactionBatchAppendCsvCommand command,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", "unused", List.of()));
        SecurityContextHolder.setContext(context);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent append did not start");
            return appendService.append(batchId, copy(command));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private TransactionBatchAppendCsvCommand copy(TransactionBatchAppendCsvCommand source) {
        TransactionBatchAppendCsvCommand copy = new TransactionBatchAppendCsvCommand();
        copy.setExpectedBatchVersion(source.getExpectedBatchVersion());
        copy.setOperationKey(source.getOperationKey());
        copy.setFileName(source.getFileName());
        copy.setContentType(source.getContentType());
        copy.setParserVersion(source.getParserVersion());
        copy.setBytes(source.getBytes());
        return copy;
    }

    private User user() {
        return TestUtil.findAll(em, User.class).stream().filter(current -> "user".equals(current.getLogin())).findFirst().orElseThrow();
    }

    private FinancialAccount account() {
        String suffix = Long.toString(System.nanoTime());
        FinancialAccount account = new FinancialAccount()
            .name("Append account " + suffix)
            .accountType(AccountType.DEBIT)
            .currency(CurrencyCode.MXN)
            .initialBalance(BigDecimal.ZERO)
            .initialBalanceDate(LocalDate.of(2026, 1, 1))
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        return accountRepository.saveAndFlush(account);
    }

    private Category category(CategoryType type) {
        Category category = new Category()
            .name("Append category " + System.nanoTime())
            .categoryType(type)
            .color("#224466")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        em.persist(category);
        return category;
    }

    private Tag tag() {
        Tag tag = new Tag()
            .name("Append tag " + System.nanoTime())
            .color("#224466")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        em.persist(tag);
        return tag;
    }

    private DescriptionNormalizationRule descriptionRule(String name, String contains, String result) {
        DescriptionNormalizationRule rule = new DescriptionNormalizationRule()
            .name(name)
            .active(true)
            .priority(0)
            .conditionOperator(RuleConditionLogic.ALL)
            .resultingDescription(result)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        em.persist(rule);
        DescriptionNormalizationRuleCondition condition = new DescriptionNormalizationRuleCondition()
            .operator(DescriptionNormalizationRuleOperator.CONTAINS)
            .value(contains)
            .caseSensitive(false)
            .position(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .descriptionNormalizationRule(rule);
        // Keep both sides in sync inside this test transaction.  The evaluation query is deliberately loaded through
        // the aggregate rather than relying on a subsequent persistence-context refresh.
        rule.addConditions(condition);
        em.persist(condition);
        em.flush();
        return rule;
    }

    private TransactionRule classificationRule(String name, String description, Category category, Tag tag) {
        TransactionRule rule = new TransactionRule()
            .name(name)
            .priority(0)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user())
            .resultingCategory(category);
        rule.addResultingTags(tag);
        em.persist(rule);
        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(TransactionRuleField.DESCRIPTION)
            .operator(RuleOperator.CONTAINS)
            .value(description)
            .caseSensitive(false)
            .position(0)
            .transactionRule(rule);
        rule.addConditions(condition);
        em.persist(condition);
        em.flush();
        return rule;
    }
}
