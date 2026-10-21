package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.dto.InternalTransferCounterpartDTO;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, owner-scoped matcher for linking the other leg of an internal transfer. */
@Service
@Transactional(readOnly = true)
public class InternalTransferCounterpartQueryService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final CurrentUserService currentUserService;
    private final InternalTransferInvariantService invariantService;

    public InternalTransferCounterpartQueryService(
        FinancialTransactionRepository financialTransactionRepository,
        InternalTransferRepository internalTransferRepository,
        CurrentUserService currentUserService,
        InternalTransferInvariantService invariantService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.currentUserService = currentUserService;
        this.invariantService = invariantService;
    }

    public Page<InternalTransferCounterpartDTO> findEligibleCounterparts(Long sourceTransactionId, String search, Pageable pageable) {
        return findEligibleCounterparts(sourceTransactionId, search, null, pageable);
    }

    /**
     * Owner-scoped counterpart lookup with an optional account narrowing. The
     * repository still enforces every eligibility invariant; accountId only
     * narrows that already-safe set.
     */
    public Page<InternalTransferCounterpartDTO> findEligibleCounterparts(
        Long sourceTransactionId,
        String search,
        Long accountId,
        Pageable pageable
    ) {
        FinancialTransaction source = findAccessible(sourceTransactionId);
        if (internalTransferRepository.existsByTransactionIdInEitherRole(source.getId())) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_LEG_ALREADY_LINKED);
        }
        if (source.getFlow() != TransactionFlow.OUT && source.getFlow() != TransactionFlow.IN) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH);
        }

        TransactionFlow counterpartFlow = source.getFlow() == TransactionFlow.OUT ? TransactionFlow.IN : TransactionFlow.OUT;
        return financialTransactionRepository
            .findEligibleInternalTransferCounterparts(
                source.getAccount().getUser().getLogin(),
                source.getId(),
                source.getAccount().getId(),
                source.getAccount().getCurrency(),
                accountId,
                counterpartFlow,
                normalizedAmount(source),
                source.getTransactionDate(),
                search == null ? "" : search.trim(),
                pageable
            )
            .map(InternalTransferReadModelMapper::toCounterpart);
    }

    /**
     * Equivalent server-authoritative lookup while the current transfer leg is
     * still a MANUAL candidate. The candidate is never materialised as a
     * FinancialTransaction merely to populate a picker.
     */
    public Page<InternalTransferCounterpartDTO> findEligibleCounterpartsForManualCandidate(
        TransactionCandidate candidate,
        String search,
        Pageable pageable
    ) {
        return findEligibleCounterpartsForManualCandidate(candidate, search, null, pageable);
    }

    public Page<InternalTransferCounterpartDTO> findEligibleCounterpartsForManualCandidate(
        TransactionCandidate candidate,
        String search,
        Long accountId,
        Pageable pageable
    ) {
        return findEligibleCounterpartsForCandidate(candidate, search, accountId, pageable);
    }

    /** Shared candidate matcher used by both standalone MANUAL drafts and Batch rows. */
    public Page<InternalTransferCounterpartDTO> findEligibleCounterpartsForCandidate(
        TransactionCandidate candidate,
        String search,
        Pageable pageable
    ) {
        return findEligibleCounterpartsForCandidate(candidate, search, null, pageable);
    }

    public Page<InternalTransferCounterpartDTO> findEligibleCounterpartsForCandidate(
        TransactionCandidate candidate,
        String search,
        Long accountId,
        Pageable pageable
    ) {
        if (
            candidate == null ||
            candidate.getAccount() == null ||
            candidate.getAccount().getId() == null ||
            candidate.getAccount().getUser() == null ||
            candidate.getAccount().getUser().getLogin() == null ||
            candidate.getFlow() == null ||
            candidate.getTransactionDate() == null
        ) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_INVALID_REQUEST);
        }
        if (candidate.getFlow() != TransactionFlow.OUT && candidate.getFlow() != TransactionFlow.IN) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH);
        }
        if (!Boolean.TRUE.equals(candidate.getAccount().getActive())) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_ACCOUNT_INACTIVE);
        }
        TransactionFlow counterpartFlow = candidate.getFlow() == TransactionFlow.OUT ? TransactionFlow.IN : TransactionFlow.OUT;
        return financialTransactionRepository
            .findEligibleInternalTransferCounterpartsForCandidate(
                candidate.getAccount().getUser().getLogin(),
                candidate.getAccount().getId(),
                candidate.getAccount().getCurrency(),
                accountId,
                counterpartFlow,
                invariantService.normalizeAmount(candidate.getAmount()),
                candidate.getTransactionDate(),
                search == null ? "" : search.trim(),
                pageable
            )
            .map(InternalTransferReadModelMapper::toCounterpart);
    }

    /**
     * Resolves and validates a selected counterpart without creating a relation.
     * Publication must call the invariant again after it has acquired write locks.
     */
    public FinancialTransaction requireEligibleCounterpartForCandidate(TransactionCandidate candidate, Long counterpartTransactionId) {
        FinancialTransaction counterpart = findAccessible(counterpartTransactionId);
        invariantService.validateCandidateAgainstExistingCounterpart(candidate, counterpart);
        invariantService.requireUnlinked(counterpart);
        return counterpart;
    }

    private FinancialTransaction findAccessible(Long id) {
        if (id == null) {
            throw new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        Optional<FinancialTransaction> transaction = currentUserService.isAdmin()
            ? financialTransactionRepository.findOneWithEagerRelationships(id)
            : financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(id, currentUserService.getCurrentUserLogin());
        return transaction.orElseThrow(() -> new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_NOT_FOUND));
    }

    private BigDecimal normalizedAmount(FinancialTransaction source) {
        return invariantService.normalizeAmount(source.getAmount());
    }
}
