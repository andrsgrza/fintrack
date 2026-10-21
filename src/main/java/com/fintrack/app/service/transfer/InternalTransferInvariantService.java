package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.InternalTransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Central application-domain validation reused by every transfer mutation command. */
@Service
public class InternalTransferInvariantService {

    private final InternalTransferRepository internalTransferRepository;

    public InternalTransferInvariantService(InternalTransferRepository internalTransferRepository) {
        this.internalTransferRepository = internalTransferRepository;
    }

    public void validateNewAccounts(FinancialAccount source, FinancialAccount destination) {
        validateAccounts(source, destination);
        if (!Boolean.TRUE.equals(source.getActive()) || !Boolean.TRUE.equals(destination.getActive())) {
            throw failure(InternalTransferErrorCode.TRANSFER_ACCOUNT_INACTIVE);
        }
    }

    /** An existing historical leg may be inactive; only the newly-created counterpart requires an active account. */
    public void validateExistingAccountAndNewCounterpart(FinancialAccount existingAccount, FinancialAccount targetAccount) {
        validateAccounts(existingAccount, targetAccount);
        if (!Boolean.TRUE.equals(targetAccount.getActive())) {
            throw failure(InternalTransferErrorCode.TRANSFER_ACCOUNT_INACTIVE);
        }
    }

    /** Existing historical legs may belong to now-inactive accounts; they remain linkable for reconciliation. */
    public void validateExistingPair(FinancialTransaction outgoing, FinancialTransaction incoming) {
        if (outgoing == null || incoming == null || outgoing.getId() == null || incoming.getId() == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        if (Objects.equals(outgoing.getId(), incoming.getId())) {
            throw failure(InternalTransferErrorCode.TRANSFER_SAME_ACCOUNT);
        }
        validateAccounts(outgoing.getAccount(), incoming.getAccount());
        if (outgoing.getFlow() != TransactionFlow.OUT || incoming.getFlow() != TransactionFlow.IN) {
            throw failure(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH);
        }
        if (!sameCanonicalAmount(outgoing.getAmount(), incoming.getAmount())) {
            throw failure(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH);
        }
        if (!Objects.equals(outgoing.getTransactionDate(), incoming.getTransactionDate())) {
            throw failure(InternalTransferErrorCode.TRANSFER_DATE_MISMATCH);
        }
    }

    /**
     * Validates a pre-post candidate against an already-published counterpart.
     * The candidate is not a financial leg yet, so this deliberately performs
     * the same business checks without requiring an id for it. Callers still
     * re-run this check after obtaining write locks during publication.
     */
    public void validateCandidateAgainstExistingCounterpart(TransactionCandidate candidate, FinancialTransaction counterpart) {
        if (
            candidate == null ||
            counterpart == null ||
            counterpart.getId() == null ||
            candidate.getAccount() == null ||
            candidate.getFlow() == null ||
            candidate.getTransactionDate() == null
        ) {
            throw failure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        validateNewAccounts(candidate.getAccount(), counterpart.getAccount());
        if (
            (candidate.getFlow() == TransactionFlow.OUT && counterpart.getFlow() != TransactionFlow.IN) ||
            (candidate.getFlow() == TransactionFlow.IN && counterpart.getFlow() != TransactionFlow.OUT) ||
            (candidate.getFlow() != TransactionFlow.OUT && candidate.getFlow() != TransactionFlow.IN)
        ) {
            throw failure(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH);
        }
        if (!sameCanonicalAmount(candidate.getAmount(), counterpart.getAmount())) {
            throw failure(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH);
        }
        if (!Objects.equals(candidate.getTransactionDate(), counterpart.getTransactionDate())) {
            throw failure(InternalTransferErrorCode.TRANSFER_DATE_MISMATCH);
        }
    }

    /** Must be called after the legs have been deterministically locked. */
    public void requireUnlinked(FinancialTransaction outgoing, FinancialTransaction incoming) {
        requireUnlinked(outgoing);
        requireUnlinked(incoming);
    }

    /**
     * Verifies one already-persisted leg before it is selected as a counterpart.
     * Keeping this as a one-leg operation avoids callers manufacturing a fake
     * pair solely to use the preflight guard.
     */
    public void requireUnlinked(FinancialTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_COUNTERPART_STALE);
        }
        if (internalTransferRepository.existsByTransactionIdInEitherRole(transaction.getId())) {
            throw failure(InternalTransferErrorCode.TRANSFER_LEG_ALREADY_LINKED);
        }
    }

    public BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw failure(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH);
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw failure(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH);
        }
    }

    private void validateAccounts(FinancialAccount source, FinancialAccount destination) {
        if (source == null || destination == null || source.getId() == null || destination.getId() == null) {
            throw failure(InternalTransferErrorCode.TRANSFER_NOT_FOUND);
        }
        if (Objects.equals(source.getId(), destination.getId())) {
            throw failure(InternalTransferErrorCode.TRANSFER_SAME_ACCOUNT);
        }
        if (!Objects.equals(source.getCurrency(), destination.getCurrency())) {
            throw failure(InternalTransferErrorCode.TRANSFER_CURRENCY_MISMATCH);
        }
        User sourceOwner = source.getUser();
        User destinationOwner = destination.getUser();
        if (
            sourceOwner == null ||
            destinationOwner == null ||
            sourceOwner.getId() == null ||
            !Objects.equals(sourceOwner.getId(), destinationOwner.getId())
        ) {
            throw failure(InternalTransferErrorCode.TRANSFER_CROSS_OWNER);
        }
    }

    private boolean sameCanonicalAmount(BigDecimal left, BigDecimal right) {
        try {
            return normalizeAmount(left).compareTo(normalizeAmount(right)) == 0;
        } catch (InternalTransferCommandException exception) {
            return false;
        }
    }

    private InternalTransferCommandException failure(InternalTransferErrorCode code) {
        return new InternalTransferCommandException(code);
    }
}
