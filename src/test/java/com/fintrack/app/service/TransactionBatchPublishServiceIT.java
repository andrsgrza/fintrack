package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.TransactionBatchDraftCommand;
import com.fintrack.app.service.dto.TransactionBatchPublicationReceiptDTO;
import com.fintrack.app.service.dto.TransactionBatchPublishCommand;
import com.fintrack.app.service.dto.TransactionBatchRowMutationDTO;
import com.fintrack.app.service.dto.TransactionBatchWorkspaceDTO;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL contracts for atomic batch publication and bounded receipt retention. */
@IntegrationTest
@WithMockUser(username = "user")
class TransactionBatchPublishServiceIT {

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionBatchCommandService commandService;

    @Autowired
    private TransactionBatchPublishService publishService;

    @Autowired
    private TransactionBatchReceiptCleanupService cleanupService;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private TransactionCandidateRepository candidateRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @Transactional
    void publishesAllCurrentValuesAtomicallyAndReplaysTheOriginalReceiptWithoutRules() {
        FinancialAccount account = account();
        Category category = category(true);
        Tag tag = tag(true);
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchRowMutationDTO first = commandService.addManualDraft(
            batch.getId(),
            completeDraft(batch.getVersion(), "Coffee", "-45.25")
        );
        TransactionBatchRowMutationDTO categorized = commandService.setCategory(
            batch.getId(),
            first.getCandidate().getId(),
            first.getCandidate().getVersion(),
            category.getId()
        );
        TransactionBatchRowMutationDTO tagged = commandService.addTag(
            batch.getId(),
            first.getCandidate().getId(),
            categorized.getCandidate().getVersion(),
            tag.getId()
        );
        TransactionBatchRowMutationDTO second = commandService.addManualDraft(
            batch.getId(),
            completeDraft(tagged.getBatchVersion(), "Salary", "1500.00")
        );

        TransactionBatchPublicationReceiptDTO receipt = publishService.publish(
            batch.getId(),
            publishCommand(second.getBatchVersion(), "publish-a")
        );

        assertThat(receipt.getStatus()).isEqualTo(TransactionBatchStatus.PUBLISHED);
        assertThat(receipt.getPublishedTransactionCount()).isEqualTo(2);
        assertThat(receipt.getPublishedAt()).isNotNull();
        assertThat(receipt.getReceiptExpiresAt()).isAfter(receipt.getPublishedAt());
        assertThat(receipt.isReplayed()).isFalse();
        assertThat(candidateRepository.existsByTransactionBatchId(batch.getId())).isFalse();

        List<FinancialTransaction> transactions = financialTransactionRepository
            .findAllWithToOneRelationshipsByAccountUserLogin("user")
            .stream()
            .filter(transaction -> transaction.getAccount().getId().equals(account.getId()))
            .toList();
        assertThat(transactions).hasSize(2);
        assertThat(transactions)
            .filteredOn(transaction -> "Coffee".equals(transaction.getDescription()))
            .singleElement()
            .satisfies(transaction -> {
                assertThat(transaction.getAmount()).isEqualByComparingTo("45.25");
                assertThat(transaction.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
                assertThat(transaction.getCategory().getId()).isEqualTo(category.getId());
                assertThat(transaction.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
                assertThat(transaction.getTransactionIngestion()).isNull();
            });

        TransactionBatch persistedReceipt = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(persistedReceipt.getAccount()).isNull();
        assertThat(persistedReceipt.getNextRowPosition()).isNull();
        assertThat(persistedReceipt.getAutoApplyDescriptions()).isNull();
        assertThat(persistedReceipt.getPublishedTransactionCount()).isEqualTo(2);

        TransactionBatchPublicationReceiptDTO replay = publishService.publish(batch.getId(), publishCommand(0L, "publish-a"));
        assertThat(replay.isReplayed()).isTrue();
        assertThat(replay.getPublishedAt()).isEqualTo(receipt.getPublishedAt());
        assertThat(replay.getReceiptExpiresAt()).isEqualTo(receipt.getReceiptExpiresAt());
        assertThat(replay.getPublishedTransactionCount()).isEqualTo(2);
        assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
            .filteredOn(transaction -> transaction.getAccount().getId().equals(account.getId()))
            .hasSize(2);
        assertThatThrownBy(() -> publishService.publish(batch.getId(), publishCommand(0L, "other-operation")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("BATCH_ALREADY_PUBLISHED");
    }

    @Test
    @Transactional
    void rejectsTheWholeBatchWhenOneDraftIsIncompleteAndLeavesTheWorkspaceOpen() {
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account().getId());
        commandService.addManualDraft(batch.getId(), completeDraft(batch.getVersion(), "Valid", "-10.00"));
        TransactionBatchDraftCommand incompleteDraft = new TransactionBatchDraftCommand();
        incompleteDraft.setExpectedBatchVersion(commandService.findOpenBatch(batch.getId()).orElseThrow().getVersion());
        TransactionBatchRowMutationDTO incomplete = commandService.addManualDraft(batch.getId(), incompleteDraft);
        assertThat(incomplete.getCandidate().getId()).isNotNull();

        assertThatThrownBy(() -> publishService.publish(batch.getId(), publishCommand(incomplete.getBatchVersion(), "publish-invalid")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unresolved candidate input issues");
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(TransactionBatchStatus.OPEN);
        assertThat(candidateRepository.existsByTransactionBatchId(batch.getId())).isTrue();
        assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
            .filteredOn(transaction -> "Valid".equals(transaction.getDescription()))
            .isEmpty();
    }

    @Test
    @Transactional
    void preservesAnAlreadySelectedInactiveCategoryAndRemovesAnExpiredMinimalReceiptOnly() {
        FinancialAccount account = account();
        Category category = category(true);
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchRowMutationDTO draft = commandService.addManualDraft(
            batch.getId(),
            completeDraft(batch.getVersion(), "Historical category", "-16.00")
        );
        TransactionBatchRowMutationDTO selected = commandService.setCategory(
            batch.getId(),
            draft.getCandidate().getId(),
            draft.getCandidate().getVersion(),
            category.getId()
        );
        category.setActive(false);
        categoryRepository.saveAndFlush(category);

        TransactionBatchPublicationReceiptDTO receipt = publishService.publish(
            batch.getId(),
            publishCommand(selected.getBatchVersion(), "publish-history")
        );
        assertThat(receipt.getPublishedTransactionCount()).isOne();
        assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
            .filteredOn(transaction -> "Historical category".equals(transaction.getDescription()))
            .singleElement()
            .extracting(transaction -> transaction.getCategory().getId())
            .isEqualTo(category.getId());

        TransactionBatch expired = batchRepository.findById(batch.getId()).orElseThrow();
        expired.setReceiptExpiresAt(Instant.now());
        batchRepository.saveAndFlush(expired);
        assertThat(cleanupService.cleanupExpiredReceipts()).isOne();
        assertThat(batchRepository.findById(batch.getId())).isEmpty();
        assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
            .filteredOn(transaction -> "Historical category".equals(transaction.getDescription()))
            .hasSize(1);
    }

    private TransactionBatchPublishCommand publishCommand(Long expectedBatchVersion, String operationKey) {
        TransactionBatchPublishCommand command = new TransactionBatchPublishCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setOperationKey(operationKey);
        return command;
    }

    private TransactionBatchDraftCommand completeDraft(Long expectedBatchVersion, String description, String signedAmount) {
        TransactionBatchDraftCommand command = new TransactionBatchDraftCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setTransactionDate(LocalDate.of(2026, 10, 16));
        command.setDescription(description);
        command.setSignedAmount(new BigDecimal(signedAmount));
        return command;
    }

    private FinancialAccount account() {
        String suffix = Long.toString(System.nanoTime());
        FinancialAccount account = new FinancialAccount()
            .name("Publication account " + suffix)
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

    private Category category(boolean active) {
        Category category = new Category()
            .name("Publication category " + System.nanoTime())
            .categoryType(CategoryType.EXPENSE)
            .color("#224466")
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        return categoryRepository.saveAndFlush(category);
    }

    private Tag tag(boolean active) {
        Tag tag = new Tag()
            .name("Publication tag " + System.nanoTime())
            .color("#224466")
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user());
        return tagRepository.saveAndFlush(tag);
    }

    private User user() {
        return TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
    }
}
