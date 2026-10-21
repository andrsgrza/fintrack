package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateInternalTransferMode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.TransactionBatchCandidatePatchCommand;
import com.fintrack.app.service.dto.TransactionBatchDraftCommand;
import com.fintrack.app.service.dto.TransactionBatchInternalTransferCommand;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

/** Real independent-transaction checks for Batch publication's shared transfer locks and bounded receipt replay. */
@IntegrationTest
@WithMockUser(username = "user")
class TransactionBatchPublishConcurrencyIT {

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionBatchCommandService commandService;

    @Autowired
    private TransactionBatchPublishService publishService;

    @Autowired
    private FinancialAccountHardDeleteService hardDeleteService;

    @Autowired
    private TransactionBatchRepository batchRepository;

    @Autowired
    private TransactionCandidateRepository candidateRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private InternalTransferRepository internalTransferRepository;

    @Test
    void publishesAgainstACounterpartAccountWhileItsHardDeleteIsBlockedByTheTemporaryIntent() throws Exception {
        FinancialAccount source = account();
        FinancialAccount target = account();
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(source.getId());
        TransactionBatchRowMutationDTO row = commandService.addManualDraft(
            batch.getId(),
            draft(batch.getVersion(), "Concurrent hard-delete source", "-40.00")
        );
        TransactionBatchRowMutationDTO configured = commandService.setInternalTransfer(
            batch.getId(),
            row.getCandidate().getId(),
            createCommand(row.getCandidate().getVersion(), target.getId(), "Concurrent hard-delete counterpart")
        );

        List<Invocation> outcomes = invokeConcurrently(
            () -> publishService.publish(batch.getId(), publishCommand(configured.getBatchVersion(), "publish-vs-hard-delete")),
            () -> hardDeleteService.hardDelete(target.getId())
        );

        assertThat(outcomes).filteredOn(Invocation::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null).singleElement();
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(TransactionBatchStatus.PUBLISHED);
        assertThat(accountRepository.findById(target.getId())).isPresent();
        assertThat(candidateRepository.existsByTransactionBatchId(batch.getId())).isFalse();
        List<FinancialTransaction> createdLegs = financialTransactionRepository
            .findAllWithToOneRelationshipsByAccountUserLogin("user")
            .stream()
            .filter(
                transaction ->
                    "Concurrent hard-delete source".equals(transaction.getDescription()) ||
                    "Concurrent hard-delete counterpart".equals(transaction.getDescription())
            )
            .toList();
        assertThat(createdLegs).hasSize(2);
        assertThat(createdLegs).allSatisfy(leg ->
            assertThat(internalTransferRepository.existsByTransactionIdInEitherRole(leg.getId())).isTrue()
        );
    }

    @Test
    void permitsOnlyOneIndependentBatchToClaimTheSameExistingCounterpart() throws Exception {
        FinancialTransaction counterpart = publishedTransaction(account(), TransactionFlow.IN, "55.00", "Concurrent claimed counterpart");
        BatchReference firstBatch = linkedBatch(account(), counterpart, "Concurrent first claimant");
        BatchReference secondBatch = linkedBatch(account(), counterpart, "Concurrent second claimant");

        List<Invocation> outcomes = invokeConcurrently(
            () -> publishService.publish(firstBatch.id(), publishCommand(firstBatch.version(), "concurrent-first-claim")),
            () -> publishService.publish(secondBatch.id(), publishCommand(secondBatch.version(), "concurrent-second-claim"))
        );

        assertThat(outcomes).filteredOn(Invocation::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null).hasSize(1);
        List<TransactionBatch> batches = List.of(
            batchRepository.findById(firstBatch.id()).orElseThrow(),
            batchRepository.findById(secondBatch.id()).orElseThrow()
        );
        assertThat(batches).filteredOn(batch -> batch.getStatus() == TransactionBatchStatus.PUBLISHED).hasSize(1);
        assertThat(batches).filteredOn(batch -> batch.getStatus() == TransactionBatchStatus.OPEN).hasSize(1);
        assertThat(batches)
            .filteredOn(batch -> batch.getStatus() == TransactionBatchStatus.OPEN)
            .allSatisfy(batch -> assertThat(candidateRepository.existsByTransactionBatchId(batch.getId())).isTrue());
        assertThat(internalTransferRepository.existsByTransactionIdInEitherRole(counterpart.getId())).isTrue();
        assertThat(financialTransactionRepository.findById(counterpart.getId())).isPresent();
    }

    @Test
    void serializesCandidateEditAgainstPublishAsOneSuccessAndOneConflictWithoutPartialRows() throws Exception {
        FinancialAccount account = account();
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchRowMutationDTO row = commandService.addManualDraft(
            batch.getId(),
            draft(batch.getVersion(), "Concurrent edit source", "-25.00")
        );
        TransactionBatchCandidatePatchCommand edit = new TransactionBatchCandidatePatchCommand();
        edit.setExpectedCandidateVersion(row.getCandidate().getVersion());
        edit.setNotes("concurrent note");

        List<Invocation> outcomes = invokeConcurrently(
            () -> publishService.publish(batch.getId(), publishCommand(row.getBatchVersion(), "publish-vs-edit")),
            () -> commandService.patchCandidate(batch.getId(), row.getCandidate().getId(), edit)
        );

        assertThat(outcomes).filteredOn(Invocation::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.failure() != null).hasSize(1);
        TransactionBatch persisted = batchRepository.findById(batch.getId()).orElseThrow();
        if (persisted.getStatus() == TransactionBatchStatus.PUBLISHED) {
            assertThat(candidateRepository.existsByTransactionBatchId(batch.getId())).isFalse();
            assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
                .filteredOn(transaction -> "Concurrent edit source".equals(transaction.getDescription()))
                .hasSize(1);
        } else {
            assertThat(persisted.getStatus()).isEqualTo(TransactionBatchStatus.OPEN);
            assertThat(candidateRepository.findAllBatchCandidatesWithRelationships(batch.getId(), "user")).hasSize(1);
            assertThat(financialTransactionRepository.findAllWithToOneRelationshipsByAccountUserLogin("user"))
                .filteredOn(transaction -> "Concurrent edit source".equals(transaction.getDescription()))
                .isEmpty();
        }
    }

    @Test
    void locksSharedAccountsInAscendingOrderWhenTwoBatchesCreateOppositeTransfers() throws Exception {
        FinancialAccount firstAccount = account();
        FinancialAccount secondAccount = account();
        BatchReference firstBatch = createCounterpartBatch(
            firstAccount,
            secondAccount,
            "Concurrent first source",
            "Concurrent first counterpart",
            "-70.00"
        );
        BatchReference secondBatch = createCounterpartBatch(
            secondAccount,
            firstAccount,
            "Concurrent second source",
            "Concurrent second counterpart",
            "-35.00"
        );

        List<Invocation> outcomes = invokeConcurrently(
            () -> publishService.publish(firstBatch.id(), publishCommand(firstBatch.version(), "concurrent-first-create")),
            () -> publishService.publish(secondBatch.id(), publishCommand(secondBatch.version(), "concurrent-second-create"))
        );

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.succeeded()).isTrue());
        assertThat(candidateRepository.existsByTransactionBatchId(firstBatch.id())).isFalse();
        assertThat(candidateRepository.existsByTransactionBatchId(secondBatch.id())).isFalse();
        List<FinancialTransaction> createdLegs = financialTransactionRepository
            .findAllWithToOneRelationshipsByAccountUserLogin("user")
            .stream()
            .filter(
                transaction ->
                    "Concurrent first source".equals(transaction.getDescription()) ||
                    "Concurrent first counterpart".equals(transaction.getDescription()) ||
                    "Concurrent second source".equals(transaction.getDescription()) ||
                    "Concurrent second counterpart".equals(transaction.getDescription())
            )
            .toList();
        assertThat(createdLegs).hasSize(4);
        assertThat(
            createdLegs
                .stream()
                .map(leg -> internalTransferRepository.findOneByTransactionIdInEitherRole(leg.getId()).orElseThrow().getId())
                .distinct()
        ).hasSize(2);
    }

    @Test
    void replaysOneCompletedReceiptConcurrentlyWithoutCreatingExtraFinancialTransactions() throws Exception {
        FinancialAccount account = account();
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(account.getId());
        TransactionBatchRowMutationDTO row = commandService.addManualDraft(
            batch.getId(),
            draft(batch.getVersion(), "Concurrent receipt source", "-31.00")
        );
        TransactionBatchPublicationReceiptDTO accepted = publishService.publish(
            batch.getId(),
            publishCommand(row.getBatchVersion(), "concurrent-receipt")
        );

        List<Invocation> outcomes = invokeConcurrently(
            () -> publishService.publish(batch.getId(), publishCommand(row.getBatchVersion(), "concurrent-receipt")),
            () -> publishService.publish(batch.getId(), publishCommand(row.getBatchVersion(), "concurrent-receipt"))
        );

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.succeeded()).isTrue();
            assertThat((TransactionBatchPublicationReceiptDTO) outcome.result()).satisfies(receipt -> {
                assertThat(receipt.isReplayed()).isTrue();
                assertThat(receipt.getPublishedAt()).isEqualTo(accepted.getPublishedAt());
                assertThat(receipt.getReceiptExpiresAt()).isEqualTo(accepted.getReceiptExpiresAt());
            });
        });
        FinancialTransaction source = financialTransactionRepository
            .findAllWithToOneRelationshipsByAccountUserLogin("user")
            .stream()
            .filter(transaction -> "Concurrent receipt source".equals(transaction.getDescription()))
            .findFirst()
            .orElseThrow();
        assertThat(internalTransferRepository.existsByTransactionIdInEitherRole(source.getId())).isFalse();
    }

    private BatchReference linkedBatch(FinancialAccount source, FinancialTransaction counterpart, String description) {
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(source.getId());
        TransactionBatchRowMutationDTO row = commandService.addManualDraft(batch.getId(), draft(batch.getVersion(), description, "-55.00"));
        TransactionBatchInternalTransferCommand link = new TransactionBatchInternalTransferCommand();
        link.setExpectedCandidateVersion(row.getCandidate().getVersion());
        link.setMode(TransactionCandidateInternalTransferMode.LINK_EXISTING);
        link.setCounterpartTransactionId(counterpart.getId());
        TransactionBatchRowMutationDTO configured = commandService.setInternalTransfer(batch.getId(), row.getCandidate().getId(), link);
        return new BatchReference(batch.getId(), configured.getBatchVersion());
    }

    private BatchReference createCounterpartBatch(
        FinancialAccount source,
        FinancialAccount target,
        String sourceDescription,
        String counterpartDescription,
        String amount
    ) {
        TransactionBatchWorkspaceDTO batch = commandService.createOpenBatch(source.getId());
        TransactionBatchRowMutationDTO row = commandService.addManualDraft(
            batch.getId(),
            draft(batch.getVersion(), sourceDescription, amount)
        );
        TransactionBatchRowMutationDTO configured = commandService.setInternalTransfer(
            batch.getId(),
            row.getCandidate().getId(),
            createCommand(row.getCandidate().getVersion(), target.getId(), counterpartDescription)
        );
        return new BatchReference(batch.getId(), configured.getBatchVersion());
    }

    private TransactionBatchDraftCommand draft(Long expectedBatchVersion, String description, String signedAmount) {
        TransactionBatchDraftCommand command = new TransactionBatchDraftCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setTransactionDate(LocalDate.of(2026, 10, 18));
        command.setDescription(description);
        command.setSignedAmount(new BigDecimal(signedAmount));
        return command;
    }

    private TransactionBatchInternalTransferCommand createCommand(Long expectedCandidateVersion, Long targetAccountId, String description) {
        TransactionBatchInternalTransferCommand command = new TransactionBatchInternalTransferCommand();
        command.setExpectedCandidateVersion(expectedCandidateVersion);
        command.setMode(TransactionCandidateInternalTransferMode.CREATE_COUNTERPART);
        command.setCounterpartAccountId(targetAccountId);
        command.setCounterpartPostingDate(LocalDate.of(2026, 10, 19));
        command.setCounterpartDescription(description);
        return command;
    }

    private TransactionBatchPublishCommand publishCommand(Long expectedBatchVersion, String operationKey) {
        TransactionBatchPublishCommand command = new TransactionBatchPublishCommand();
        command.setExpectedBatchVersion(expectedBatchVersion);
        command.setOperationKey(operationKey);
        return command;
    }

    private List<Invocation> invokeConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Invocation> firstFuture = executor.submit(() -> invokeAsUser(first, ready, start));
            Future<Invocation> secondFuture = executor.submit(() -> invokeAsUser(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Invocation invokeAsUser(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", "unused", List.of()));
        SecurityContextHolder.setContext(context);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent operation did not start");
            return new Invocation(action.call(), null);
        } catch (Throwable failure) {
            return new Invocation(null, failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private FinancialTransaction publishedTransaction(FinancialAccount account, TransactionFlow flow, String amount, String description) {
        FinancialTransaction transaction = new FinancialTransaction()
            .transactionDate(LocalDate.of(2026, 10, 18))
            .description(description)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .account(account);
        return financialTransactionRepository.saveAndFlush(transaction);
    }

    private FinancialAccount account() {
        String suffix = Long.toString(System.nanoTime());
        FinancialAccount account = new FinancialAccount()
            .name("Concurrent Batch account " + suffix)
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

    private User currentUser() {
        return TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
    }

    private record Invocation(Object result, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }

    private record BatchReference(Long id, Long version) {}
}
