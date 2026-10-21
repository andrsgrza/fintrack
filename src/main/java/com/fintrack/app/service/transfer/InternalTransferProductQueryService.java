package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product-safe, owner-scoped read boundary for the Internal Transfers screens. */
@Service
@Transactional(readOnly = true)
public class InternalTransferProductQueryService {

    private final InternalTransferRepository internalTransferRepository;
    private final CurrentUserService currentUserService;

    public InternalTransferProductQueryService(
        InternalTransferRepository internalTransferRepository,
        CurrentUserService currentUserService
    ) {
        this.internalTransferRepository = internalTransferRepository;
        this.currentUserService = currentUserService;
    }

    public Page<InternalTransferReadDTO> findPage(Long accountId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<InternalTransfer> transfers = currentUserService.isAdmin()
            ? internalTransferRepository.findProductPage(accountId, fromDate, toDate, pageable)
            : internalTransferRepository.findProductPageByAccountUserLogin(
                currentUserService.getCurrentUserLogin(),
                accountId,
                fromDate,
                toDate,
                pageable
            );
        return transfers.map(InternalTransferReadModelMapper::toSummary);
    }

    public InternalTransferReadDTO findOne(Long id) {
        return (
            currentUserService.isAdmin()
                ? internalTransferRepository.findProductDetailById(id)
                : internalTransferRepository.findProductDetailByIdAndAccountUserLogin(id, currentUserService.getCurrentUserLogin())
        ).map(InternalTransferReadModelMapper::toRead).orElseThrow(() ->
                new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_NOT_FOUND)
            );
    }

    /** Returns the aggregate containing an accessible financial transaction, without exposing a raw association. */
    public InternalTransferReadDTO findByTransactionId(Long transactionId) {
        return (
            currentUserService.isAdmin()
                ? internalTransferRepository.findProductDetailByTransactionId(transactionId)
                : internalTransferRepository.findProductDetailByTransactionIdAndAccountUserLogin(
                    transactionId,
                    currentUserService.getCurrentUserLogin()
                )
        ).map(InternalTransferReadModelMapper::toRead).orElseThrow(() ->
                new InternalTransferCommandException(InternalTransferErrorCode.TRANSFER_NOT_FOUND)
            );
    }
}
