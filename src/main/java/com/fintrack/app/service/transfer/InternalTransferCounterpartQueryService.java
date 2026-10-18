package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.FinancialTransaction;
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
                counterpartFlow,
                normalizedAmount(source),
                source.getTransactionDate(),
                search == null ? "" : search.trim(),
                pageable
            )
            .map(InternalTransferReadModelMapper::toCounterpart);
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
