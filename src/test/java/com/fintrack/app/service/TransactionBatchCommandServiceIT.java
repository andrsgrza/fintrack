package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.dto.*;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed contract tests for the internal TransactionBatch command boundary. */
@IntegrationTest
@WithMockUser(username = "user")
class TransactionBatchCommandServiceIT {

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionBatchCommandService service;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private TransactionCandidateRepository candidateRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @Transactional
    void createsIndependentOpenBatchesWithApprovedDefaultsForAnActiveOwnedAccount() {
        FinancialAccount account = account();

        TransactionBatchWorkspaceDTO first = service.createOpenBatch(account.getId());
        TransactionBatchWorkspaceDTO second = service.createOpenBatch(account.getId());

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(TransactionBatchStatus.OPEN);
        assertThat(first.getAccountId()).isEqualTo(account.getId());
        assertThat(first.getAutoApplyDescriptions()).isFalse();
        assertThat(first.getAutoApplyCategories()).isFalse();
        assertThat(first.getAutoApplyTags()).isFalse();
        assertThat(first.getProtectManualChanges()).isTrue();
        assertThat(service.findOpenBatch(first.getId())).isPresent();
        assertThat(batchRepository.findAllByUserLoginAndStatus("user", TransactionBatchStatus.OPEN)).hasSize(2);
    }

    @Test
    @Transactional
    void addsIncompleteRowsWithStablePositionsAndLeavesTheBatchRevisionOutOfPerRowEdits() {
        TransactionBatchWorkspaceDTO batch = service.createOpenBatch(account().getId());
        TransactionBatchRowMutationDTO first = service.addManualDraft(batch.getId(), draftFor(batch.getVersion()));
        TransactionBatchRowMutationDTO second = service.addManualDraft(batch.getId(), draftFor(first.getBatchVersion()));

        assertThat(first.getCandidate().getSource()).isEqualTo(TransactionCandidateSource.FILE_IMPORT);
        assertThat(first.getCandidate().getRowPosition()).isEqualTo(1L);
        assertThat(first.getCandidate().getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.UNKNOWN);
        assertThat(first.getCandidate().getCurrencySnapshot()).isEqualTo(CurrencyCode.MXN);
        assertThat(second.getCandidate().getRowPosition()).isEqualTo(2L);
        assertThatThrownBy(() -> service.addManualDraft(batch.getId(), draftFor(batch.getVersion())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stale");

        TransactionBatchCandidatePatchCommand patch = new TransactionBatchCandidatePatchCommand();
        patch.setExpectedCandidateVersion(first.getCandidate().getVersion());
        patch.setDescription("  office taxi  ");
        TransactionBatchRowMutationDTO edited = service.patchCandidate(batch.getId(), first.getCandidate().getId(), patch);

        assertThat(edited.getCandidate().getDescription()).isEqualTo("office taxi");
        assertThat(edited.getCandidate().getVersion()).isGreaterThan(first.getCandidate().getVersion());
        assertThat(edited.getBatchVersion()).isEqualTo(second.getBatchVersion());
        Long versionAfterDelete = service.deleteCandidate(batch.getId(), second.getCandidate().getId(), second.getCandidate().getVersion());

        TransactionBatchRowMutationDTO third = service.addManualDraft(batch.getId(), draftFor(versionAfterDelete));
        assertThat(third.getCandidate().getRowPosition()).isEqualTo(3L);
        assertThat(versionAfterDelete).isLessThan(third.getBatchVersion());
    }

    @Test
    @Transactional
    void distinguishesAnOmittedPatchFromAnExplicitClearAndDoesNotResolveUnrelatedIssues() {
        TransactionBatchWorkspaceDTO batch = service.createOpenBatch(account().getId());
        TransactionBatchDraftCommand draft = new TransactionBatchDraftCommand();
        draft.setExpectedBatchVersion(batch.getVersion());
        draft.setDescription("Initial");
        draft.setSignedAmount(new BigDecimal("-18.25"));
        draft.setTransactionDate(LocalDate.of(2026, 10, 15));
        TransactionBatchRowMutationDTO created = service.addManualDraft(batch.getId(), draft);

        TransactionBatchRowMutationDTO withCurrencyIssue = service.recordInputIssue(
            batch.getId(),
            created.getCandidate().getId(),
            created.getCandidate().getVersion(),
            TransactionCandidateInputIssueField.CURRENCY,
            "USD",
            TransactionCandidateInputIssueReason.CURRENCY_MISMATCH
        );
        TransactionBatchCandidatePatchCommand unrelatedPatch = new TransactionBatchCandidatePatchCommand();
        unrelatedPatch.setExpectedCandidateVersion(withCurrencyIssue.getCandidate().getVersion());
        unrelatedPatch.setNotes("unchanged monetary input remains blocked");
        TransactionBatchRowMutationDTO unrelatedResult = service.patchCandidate(
            batch.getId(),
            created.getCandidate().getId(),
            unrelatedPatch
        );

        assertThat(unrelatedResult.getCandidate().getDescription()).isEqualTo("Initial");
        assertThat(unrelatedResult.getCandidate().getInputIssues()).hasSize(1);
        assertThat(unrelatedResult.getCandidate().getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.INVALID);

        TransactionBatchCandidatePatchCommand clearPatch = new TransactionBatchCandidatePatchCommand();
        clearPatch.setExpectedCandidateVersion(unrelatedResult.getCandidate().getVersion());
        clearPatch.setClearFields(Set.of(TransactionBatchCandidatePatchField.DESCRIPTION));
        TransactionBatchRowMutationDTO cleared = service.patchCandidate(batch.getId(), created.getCandidate().getId(), clearPatch);

        assertThat(cleared.getCandidate().getDescription()).isNull();
        assertThat(cleared.getCandidate().getDescriptionSource()).isEqualTo(TransactionCandidateDescriptionSource.MANUAL);
        assertThat(cleared.getCandidate().getInputIssues()).hasSize(1);

        TransactionBatchCandidatePatchCommand repairedAmount = new TransactionBatchCandidatePatchCommand();
        repairedAmount.setExpectedCandidateVersion(cleared.getCandidate().getVersion());
        repairedAmount.setSignedAmount(new BigDecimal("-20.00"));
        TransactionBatchRowMutationDTO repaired = service.patchCandidate(batch.getId(), created.getCandidate().getId(), repairedAmount);
        assertThat(repaired.getCandidate().getInputIssues()).isEmpty();
        assertThat(repaired.getCandidate().getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.UNKNOWN);
    }

    @Test
    @Transactional
    void preservesManualCategoryAndTagIntentIncludingInactiveHistoricalReferences() {
        FinancialAccount account = account();
        Category category = category(CategoryType.EXPENSE, true);
        Tag firstTag = tag(true);
        Tag inactiveNewTag = tag(false);
        TransactionBatchWorkspaceDTO batch = service.createOpenBatch(account.getId());
        TransactionBatchDraftCommand draft = new TransactionBatchDraftCommand();
        draft.setExpectedBatchVersion(batch.getVersion());
        draft.setSignedAmount(new BigDecimal("-10.00"));
        TransactionBatchRowMutationDTO created = service.addManualDraft(batch.getId(), draft);

        TransactionBatchRowMutationDTO categorized = service.setCategory(
            batch.getId(),
            created.getCandidate().getId(),
            created.getCandidate().getVersion(),
            category.getId()
        );
        TransactionBatchRowMutationDTO tagged = service.addTag(
            batch.getId(),
            created.getCandidate().getId(),
            categorized.getCandidate().getVersion(),
            firstTag.getId()
        );
        TransactionBatchRowMutationDTO removed = service.removeTag(
            batch.getId(),
            created.getCandidate().getId(),
            tagged.getCandidate().getVersion(),
            firstTag.getId()
        );

        assertThat(removed.getCandidate().getTags()).isEmpty();
        assertThat(removed.getCandidate().getSuppressedTagIds()).containsExactly(firstTag.getId());

        TransactionBatchRowMutationDTO readded = service.addTag(
            batch.getId(),
            created.getCandidate().getId(),
            removed.getCandidate().getVersion(),
            firstTag.getId()
        );
        assertThat(readded.getCandidate().getSuppressedTagIds()).isEmpty();
        assertThat(readded.getCandidate().getTags())
            .singleElement()
            .extracting(TransactionBatchTagViewDTO::getSource)
            .isEqualTo(TransactionCandidateClassificationSource.MANUAL);

        category.setActive(false);
        firstTag.setActive(false);
        categoryRepository.saveAndFlush(category);
        tagRepository.saveAndFlush(firstTag);
        TransactionBatchRowMutationDTO sameInactiveCategory = service.setCategory(
            batch.getId(),
            created.getCandidate().getId(),
            readded.getCandidate().getVersion(),
            category.getId()
        );
        TransactionBatchRowMutationDTO retainedInactiveTag = service.replaceTags(
            batch.getId(),
            created.getCandidate().getId(),
            sameInactiveCategory.getCandidate().getVersion(),
            Set.of(firstTag.getId())
        );
        assertThat(retainedInactiveTag.getCandidate().getTags())
            .extracting(TransactionBatchTagViewDTO::getId)
            .containsExactly(firstTag.getId());

        assertThatThrownBy(() ->
            service.addTag(
                batch.getId(),
                created.getCandidate().getId(),
                retainedInactiveTag.getCandidate().getVersion(),
                inactiveNewTag.getId()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Inactive tag");
        TransactionBatchRowMutationDTO cleared = service.clearCategory(
            batch.getId(),
            created.getCandidate().getId(),
            retainedInactiveTag.getCandidate().getVersion()
        );
        assertThat(cleared.getCandidate().getCategoryId()).isNull();
        assertThat(cleared.getCandidate().getCategorySource()).isNull();
        assertThat(cleared.getCandidate().getCategoryManualClear()).isTrue();
    }

    @Test
    @Transactional
    void rejectsStaleCandidateCommandsAndDiscardRemovesOnlyTemporaryWorkspaceData() {
        TransactionBatchWorkspaceDTO batch = service.createOpenBatch(account().getId());
        TransactionBatchRowMutationDTO created = service.addManualDraft(batch.getId(), draftFor(batch.getVersion()));
        TransactionBatchCandidatePatchCommand currentPatch = new TransactionBatchCandidatePatchCommand();
        currentPatch.setExpectedCandidateVersion(created.getCandidate().getVersion());
        currentPatch.setNotes("first write");
        TransactionBatchRowMutationDTO current = service.patchCandidate(batch.getId(), created.getCandidate().getId(), currentPatch);

        TransactionBatchCandidatePatchCommand stalePatch = new TransactionBatchCandidatePatchCommand();
        stalePatch.setExpectedCandidateVersion(created.getCandidate().getVersion());
        stalePatch.setNotes("stale write");
        assertThatThrownBy(() -> service.patchCandidate(batch.getId(), created.getCandidate().getId(), stalePatch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stale");

        assertThatThrownBy(() -> service.discardOpenBatch(batch.getId(), current.getBatchVersion() - 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stale");
        assertThat(service.discardOpenBatch(batch.getId(), current.getBatchVersion())).isTrue();
        em.flush();
        em.clear();

        assertThat(batchRepository.findById(batch.getId())).isEmpty();
        assertThat(candidateRepository.findById(created.getCandidate().getId())).isEmpty();
    }

    private User currentUser() {
        return TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
    }

    private TransactionBatchDraftCommand draftFor(Long expectedBatchVersion) {
        TransactionBatchDraftCommand command = new TransactionBatchDraftCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        return command;
    }

    private FinancialAccount account() {
        String suffix = Long.toString(System.nanoTime());
        FinancialAccount account = new FinancialAccount()
            .name("Batch account " + suffix)
            .accountType(AccountType.DEBIT)
            .currency(CurrencyCode.MXN)
            .initialBalance(BigDecimal.ZERO)
            .initialBalanceDate(LocalDate.of(2026, 1, 1))
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
        account.setUser(currentUser());
        return accountRepository.saveAndFlush(account);
    }

    private Category category(CategoryType type, boolean active) {
        Category category = new Category()
            .name("Batch category " + System.nanoTime())
            .categoryType(type)
            .color("#224466")
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
        category.setUser(currentUser());
        return categoryRepository.saveAndFlush(category);
    }

    private Tag tag(boolean active) {
        Tag tag = new Tag()
            .name("Batch tag " + System.nanoTime())
            .color("#224466")
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
        tag.setUser(currentUser());
        return tagRepository.saveAndFlush(tag);
    }
}
