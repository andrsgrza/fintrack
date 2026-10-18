package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.dto.InternalTransferCounterpartCommand;
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferLegSelectionCommand;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Intent-oriented mutation boundary for InternalTransfer.
 *
 * <p>Every command uses one lock order: FinancialAccounts ascending by id,
 * FinancialTransactions ascending by id, then InternalTransfer when one
 * already exists. New transfer legs are intentionally built directly rather
 * than through FinancialTransactionService, so TransactionRules and DNR never
 * run implicitly for this product action.</p>
 */
@Service
@Transactional
public class InternalTransferCommandService {

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final InternalTransferInvariantService invariantService;
    private final EntityManager entityManager;

    public InternalTransferCommandService(
        FinancialAccountRepository financialAccountRepository,
        FinancialTransactionRepository financialTransactionRepository,
        InternalTransferRepository internalTransferRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        InternalTransferInvariantService invariantService,
        EntityManager entityManager
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.invariantService = invariantService;
        this.entityManager = entityManager;
    }

    public InternalTransferReadDTO createTransfer(InternalTransferCreateCommand command) {
        if (command == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        Map<Long, FinancialAccount> accounts = lockAccounts(command.getSourceAccountId(), command.getDestinationAccountId());
        FinancialAccount sourceAccount = requiredAccount(accounts, command.getSourceAccountId());
        FinancialAccount destinationAccount = requiredAccount(accounts, command.getDestinationAccountId());
        invariantService.validateNewAccounts(sourceAccount, destinationAccount);

        BigDecimal amount = invariantService.normalizeAmount(command.getAmount());
        Instant now = Instant.now();
        FinancialTransaction source = buildNewLeg(
            sourceAccount,
            TransactionFlow.OUT,
            amount,
            command.getTransactionDate(),
            command.getSourcePostingDate(),
            command.getSourceDescription(),
            command.getSourceExternalReference(),
            command.getSourceNotes(),
            command.getSourceClassification(),
            now
        );
        FinancialTransaction destination = buildNewLeg(
            destinationAccount,
            TransactionFlow.IN,
            amount,
            command.getTransactionDate(),
            command.getDestinationPostingDate(),
            command.getDestinationDescription(),
            command.getDestinationExternalReference(),
            command.getDestinationNotes(),
            command.getDestinationClassification(),
            now
        );

        List<FinancialTransaction> persistedLegs = financialTransactionRepository.saveAllAndFlush(List.of(source, destination));
        FinancialTransaction persistedSource = persistedLegs.get(0);
        FinancialTransaction persistedDestination = persistedLegs.get(1);
        invariantService.validateExistingPair(persistedSource, persistedDestination);
        return toRead(persistTransfer(persistedSource, persistedDestination, command.getTransferNotes(), now));
    }

    public InternalTransferReadDTO linkExistingTransactions(InternalTransferLinkCommand command) {
        if (command == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        FinancialTransaction outgoingSnapshot = findAccessibleTransaction(command.getOutgoingTransactionId());
        FinancialTransaction incomingSnapshot = findAccessibleTransaction(command.getIncomingTransactionId());
        Map<Long, FinancialAccount> accounts = lockAccounts(outgoingSnapshot.getAccount().getId(), incomingSnapshot.getAccount().getId());
        Map<Long, FinancialTransaction> legs = lockTransactions(outgoingSnapshot.getId(), incomingSnapshot.getId());
        FinancialTransaction outgoing = requiredTransaction(legs, command.getOutgoingTransactionId());
        FinancialTransaction incoming = requiredTransaction(legs, command.getIncomingTransactionId());

        invariantService.validateExistingPair(outgoing, incoming);
        invariantService.requireUnlinked(outgoing, incoming);
        // Ensures the post-lock accounts still represent the transfer's two persisted legs.
        requiredAccount(accounts, outgoing.getAccount().getId());
        requiredAccount(accounts, incoming.getAccount().getId());
        return toRead(persistTransfer(outgoing, incoming, command.getNotes(), Instant.now()));
    }

    public InternalTransferReadDTO createCounterpartForTransaction(Long existingTransactionId, InternalTransferCounterpartCommand command) {
        if (command == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        FinancialTransaction existingSnapshot = findAccessibleTransaction(existingTransactionId);
        Map<Long, FinancialAccount> accounts = lockAccounts(existingSnapshot.getAccount().getId(), command.getTargetAccountId());
        FinancialTransaction existing = requiredTransaction(lockTransactions(existingTransactionId), existingTransactionId);
        FinancialAccount targetAccount = requiredAccount(accounts, command.getTargetAccountId());

        invariantService.validateExistingAccountAndNewCounterpart(existing.getAccount(), targetAccount);
        if (internalTransferRepository.existsByTransactionIdInEitherRole(existing.getId())) {
            throw failure(InternalTransferErrorCode.TRANSFER_LEG_ALREADY_LINKED);
        }

        TransactionFlow counterpartFlow = existing.getFlow() == TransactionFlow.OUT ? TransactionFlow.IN : TransactionFlow.OUT;
        if (existing.getFlow() != TransactionFlow.OUT && existing.getFlow() != TransactionFlow.IN) {
            throw failure(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH);
        }
        Instant now = Instant.now();
        FinancialTransaction counterpart = buildNewLeg(
            targetAccount,
            counterpartFlow,
            invariantService.normalizeAmount(existing.getAmount()),
            existing.getTransactionDate(),
            command.getPostingDate(),
            command.getDescription(),
            command.getExternalReference(),
            command.getNotes(),
            command.getClassification(),
            now
        );
        counterpart = financialTransactionRepository.saveAndFlush(counterpart);
        FinancialTransaction outgoing = existing.getFlow() == TransactionFlow.OUT ? existing : counterpart;
        FinancialTransaction incoming = existing.getFlow() == TransactionFlow.IN ? existing : counterpart;
        invariantService.validateExistingPair(outgoing, incoming);
        return toRead(persistTransfer(outgoing, incoming, command.getTransferNotes(), now));
    }

    public InternalTransferReadDTO updateTransfer(Long transferId, InternalTransferUpdateCommand command) {
        if (command == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        InternalTransfer snapshot = findAccessibleTransfer(transferId);
        lockAccounts(snapshot.getOutgoingTransaction().getAccount().getId(), snapshot.getIncomingTransaction().getAccount().getId());
        Map<Long, FinancialTransaction> legs = lockTransactions(
            snapshot.getOutgoingTransaction().getId(),
            snapshot.getIncomingTransaction().getId()
        );
        InternalTransfer transfer = lockTransfer(transferId);
        requireVersion(transfer, command.getExpectedVersion());
        FinancialTransaction outgoing = requiredTransaction(legs, transfer.getOutgoingTransaction().getId());
        FinancialTransaction incoming = requiredTransaction(legs, transfer.getIncomingTransaction().getId());

        BigDecimal amount = invariantService.normalizeAmount(command.getAmount());
        String sourceDescription = normalizeRequired(command.getSourceDescription(), 500);
        String destinationDescription = normalizeRequired(command.getDestinationDescription(), 500);
        String notes = normalizeOptional(command.getNotes(), 500);
        boolean legChanged = hasStructuralLegChange(
            outgoing,
            incoming,
            amount,
            command.getTransactionDate(),
            command.getSourcePostingDate(),
            command.getDestinationPostingDate(),
            sourceDescription,
            destinationDescription
        );
        boolean notesChanged = !Objects.equals(transfer.getNotes(), notes);
        outgoing.setAmount(amount);
        incoming.setAmount(amount);
        outgoing.setTransactionDate(requireDate(command.getTransactionDate()));
        incoming.setTransactionDate(command.getTransactionDate());
        outgoing.setPostingDate(command.getSourcePostingDate());
        incoming.setPostingDate(command.getDestinationPostingDate());
        outgoing.setDescription(sourceDescription);
        incoming.setDescription(destinationDescription);
        Instant now = Instant.now();
        outgoing.setUpdatedAt(now);
        incoming.setUpdatedAt(now);
        invariantService.validateExistingPair(outgoing, incoming);
        financialTransactionRepository.saveAllAndFlush(List.of(outgoing, incoming));
        transfer.setNotes(notes);
        if (legChanged || notesChanged) {
            // The legs live in separate rows. Marking the aggregate root as
            // changed makes JPA advance @Version at this flush, so the returned
            // version is the token the next aggregate command must present.
            transfer.setUpdatedAt(now);
        }
        entityManager.flush();
        return toRead(transfer);
    }

    public InternalTransferReadDTO unlinkTransfer(Long transferId, InternalTransferUnlinkCommand command) {
        if (command == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        InternalTransfer snapshot = findAccessibleTransfer(transferId);
        lockAccounts(snapshot.getOutgoingTransaction().getAccount().getId(), snapshot.getIncomingTransaction().getAccount().getId());
        lockTransactions(snapshot.getOutgoingTransaction().getId(), snapshot.getIncomingTransaction().getId());
        InternalTransfer transfer = lockTransfer(transferId);
        requireVersion(transfer, command.getExpectedVersion());
        InternalTransferReadDTO result = toRead(transfer);
        deleteTransferLink(transfer);
        return result;
    }

    /** Used by FinancialTransaction delete so removal of one leg never leaves an FK-dangling transfer. */
    public void unlinkForFinancialTransactionDeletion(Long transactionId) {
        FinancialTransaction snapshot = findAccessibleTransaction(transactionId);
        Optional<InternalTransfer> existingTransfer = internalTransferRepository.findOneByTransactionIdInEitherRole(transactionId);
        if (existingTransfer.isEmpty()) {
            lockAccounts(snapshot.getAccount().getId());
            requiredTransaction(lockTransactions(transactionId), transactionId);
            return;
        }

        InternalTransfer transferSnapshot = existingTransfer.orElseThrow();
        Long outgoingTransactionId = transferSnapshot.getOutgoingTransaction().getId();
        Long incomingTransactionId = transferSnapshot.getIncomingTransaction().getId();
        lockAccounts(
            transferSnapshot.getOutgoingTransaction().getAccount().getId(),
            transferSnapshot.getIncomingTransaction().getAccount().getId()
        );
        lockTransactions(outgoingTransactionId, incomingTransactionId);
        InternalTransfer lockedTransfer = lockTransfer(transferSnapshot.getId());
        deleteTransferLink(lockedTransfer);
    }

    private InternalTransfer persistTransfer(
        FinancialTransaction outgoing,
        FinancialTransaction incoming,
        String notes,
        Instant createdAt
    ) {
        InternalTransfer transfer = new InternalTransfer();
        transfer.setOutgoingTransaction(outgoing);
        transfer.setIncomingTransaction(incoming);
        transfer.setNotes(normalizeOptional(notes, 500));
        transfer.setCreatedAt(createdAt);
        transfer.setUpdatedAt(createdAt);
        return internalTransferRepository.saveAndFlush(transfer);
    }

    /**
     * Clears both in-memory sides before deleting the association row. The
     * transaction-side one-to-one mappings are inverse, but Hibernate may have
     * initialized them while an aggregate update was processed. Removing only
     * the owning transfer in that state leaves managed legs pointing at a
     * removed association and makes flush treat it as transient.
     */
    private void deleteTransferLink(InternalTransfer transfer) {
        FinancialTransaction outgoing = transfer.getOutgoingTransaction();
        FinancialTransaction incoming = transfer.getIncomingTransaction();
        if (outgoing != null) {
            outgoing.setOutgoingInternalTransfer(null);
        }
        if (incoming != null) {
            incoming.setIncomingInternalTransfer(null);
        }
        transfer.setOutgoingTransaction(null);
        transfer.setIncomingTransaction(null);
        internalTransferRepository.delete(transfer);
        internalTransferRepository.flush();
    }

    private boolean hasStructuralLegChange(
        FinancialTransaction outgoing,
        FinancialTransaction incoming,
        BigDecimal amount,
        java.time.LocalDate transactionDate,
        java.time.LocalDate sourcePostingDate,
        java.time.LocalDate destinationPostingDate,
        String sourceDescription,
        String destinationDescription
    ) {
        return (
            !sameAmount(outgoing.getAmount(), amount) ||
            !sameAmount(incoming.getAmount(), amount) ||
            !Objects.equals(outgoing.getTransactionDate(), transactionDate) ||
            !Objects.equals(incoming.getTransactionDate(), transactionDate) ||
            !Objects.equals(outgoing.getPostingDate(), sourcePostingDate) ||
            !Objects.equals(incoming.getPostingDate(), destinationPostingDate) ||
            !Objects.equals(outgoing.getDescription(), sourceDescription) ||
            !Objects.equals(incoming.getDescription(), destinationDescription)
        );
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private FinancialTransaction buildNewLeg(
        FinancialAccount account,
        TransactionFlow flow,
        BigDecimal amount,
        java.time.LocalDate transactionDate,
        java.time.LocalDate postingDate,
        String description,
        String externalReference,
        String notes,
        InternalTransferLegSelectionCommand selection,
        Instant now
    ) {
        if (transactionDate == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_DATE_MISMATCH);
        }
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setAccount(account);
        transaction.setFlow(flow);
        transaction.setOrigin(TransactionOrigin.MANUAL);
        transaction.setAmount(amount);
        transaction.setTransactionDate(transactionDate);
        transaction.setPostingDate(postingDate);
        transaction.setDescription(normalizeRequired(description, 500));
        transaction.setExternalReference(normalizeOptional(externalReference, 150));
        transaction.setNotes(normalizeOptional(notes, 1000));
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        Classification selectionResult = resolveClassification(selection, ownerLogin(account), flow);
        transaction.setCategory(selectionResult.category());
        transaction.setTags(selectionResult.tags());
        return transaction;
    }

    private Classification resolveClassification(InternalTransferLegSelectionCommand selection, String ownerLogin, TransactionFlow flow) {
        if (selection == null) {
            return new Classification(null, Set.of());
        }
        Category category = null;
        if (selection.getCategoryId() != null) {
            category = categoryRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(selection.getCategoryId(), ownerLogin)
                .orElseThrow(() -> failure(InternalTransferErrorCode.TRANSFER_INVALID_CLASSIFICATION));
            if (
                !Boolean.TRUE.equals(category.getActive()) ||
                !CategoryFlowCompatibilityValidator.isCompatible(category.getCategoryType(), flow)
            ) {
                throw failure(InternalTransferErrorCode.TRANSFER_INVALID_CLASSIFICATION);
            }
        }
        Set<Tag> tags = new HashSet<>();
        for (Long tagId : selection.getTagIds()) {
            if (tagId == null) {
                throw failure(InternalTransferErrorCode.TRANSFER_INVALID_CLASSIFICATION);
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(tagId, ownerLogin)
                .orElseThrow(() -> failure(InternalTransferErrorCode.TRANSFER_INVALID_CLASSIFICATION));
            if (!Boolean.TRUE.equals(tag.getActive())) {
                throw failure(InternalTransferErrorCode.TRANSFER_INVALID_CLASSIFICATION);
            }
            tags.add(tag);
        }
        return new Classification(category, tags);
    }

    private FinancialTransaction findAccessibleTransaction(Long id) {
        if (id == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        Optional<FinancialTransaction> transaction = currentUserService.isAdmin()
            ? financialTransactionRepository.findOneWithEagerRelationships(id)
            : financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(id, currentUserService.getCurrentUserLogin());
        return transaction.orElseThrow(() -> failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND));
    }

    private InternalTransfer findAccessibleTransfer(Long id) {
        if (id == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        Optional<InternalTransfer> transfer = currentUserService.isAdmin()
            ? internalTransferRepository.findOneWithEagerRelationships(id)
            : internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(id, currentUserService.getCurrentUserLogin());
        return transfer.orElseThrow(() -> failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND));
    }

    private Map<Long, FinancialAccount> lockAccounts(Long... ids) {
        List<Long> orderedIds = sortedDistinct(ids);
        if (orderedIds.stream().anyMatch(Objects::isNull)) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        List<FinancialAccount> accounts = currentUserService.isAdmin()
            ? financialAccountRepository.findAllForInternalTransferMutationByIdIn(orderedIds)
            : financialAccountRepository.findAllForInternalTransferMutationByIdInAndUserLogin(
                orderedIds,
                currentUserService.getCurrentUserLogin()
            );
        if (accounts.size() != orderedIds.size()) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        return accounts.stream().collect(java.util.stream.Collectors.toMap(FinancialAccount::getId, account -> account));
    }

    private Map<Long, FinancialTransaction> lockTransactions(Long... ids) {
        List<Long> orderedIds = sortedDistinct(ids);
        if (orderedIds.stream().anyMatch(Objects::isNull)) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        List<FinancialTransaction> transactions = currentUserService.isAdmin()
            ? financialTransactionRepository.findAllForInternalTransferMutationByIdIn(orderedIds)
            : financialTransactionRepository.findAllForInternalTransferMutationByIdInAndAccountUserLogin(
                orderedIds,
                currentUserService.getCurrentUserLogin()
            );
        if (transactions.size() != orderedIds.size()) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        return transactions.stream().collect(java.util.stream.Collectors.toMap(FinancialTransaction::getId, transaction -> transaction));
    }

    private InternalTransfer lockTransfer(Long id) {
        Optional<InternalTransfer> transfer = currentUserService.isAdmin()
            ? internalTransferRepository.findOneForInternalTransferMutation(id)
            : internalTransferRepository.findOneForInternalTransferMutationByIdAndAccountUserLogin(
                id,
                currentUserService.getCurrentUserLogin()
            );
        return transfer.orElseThrow(() -> failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND));
    }

    private FinancialAccount requiredAccount(Map<Long, FinancialAccount> accounts, Long id) {
        FinancialAccount account = accounts.get(id);
        if (account == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        return account;
    }

    private FinancialTransaction requiredTransaction(Map<Long, FinancialTransaction> transactions, Long id) {
        FinancialTransaction transaction = transactions.get(id);
        if (transaction == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        return transaction;
    }

    private List<Long> sortedDistinct(Long... ids) {
        List<Long> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(id);
        }
        result.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        return result.stream().distinct().toList();
    }

    private void requireVersion(InternalTransfer transfer, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(transfer.getVersion(), expectedVersion)) {
            throw failure(InternalTransferErrorCode.TRANSFER_VERSION_CONFLICT);
        }
    }

    private java.time.LocalDate requireDate(java.time.LocalDate value) {
        if (value == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_DATE_MISMATCH);
        }
        return value;
    }

    private String normalizeRequired(String value, int maxLength) {
        if (value == null || value.trim().isBlank() || value.trim().length() > maxLength) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        return value.trim();
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw failure(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        return normalized;
    }

    private String ownerLogin(FinancialAccount account) {
        if (account.getUser() == null || account.getUser().getLogin() == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_CROSS_OWNER);
        }
        return account.getUser().getLogin();
    }

    private InternalTransferReadDTO toRead(InternalTransfer transfer) {
        return InternalTransferReadModelMapper.toRead(transfer);
    }

    private InternalTransferCommandException failure(InternalTransferErrorCode code) {
        return new InternalTransferCommandException(code);
    }

    private record Classification(Category category, Set<Tag> tags) {}
}
