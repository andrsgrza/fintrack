package com.fintrack.app.web.rest;

import com.fintrack.app.service.dto.InternalTransferCounterpartCommand;
import com.fintrack.app.service.dto.InternalTransferCounterpartDTO;
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import com.fintrack.app.service.transfer.InternalTransferCounterpartQueryService;
import com.fintrack.app.service.transfer.InternalTransferErrorCode;
import com.fintrack.app.service.transfer.InternalTransferProductQueryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product API for InternalTransfer. Mutations are intentionally exposed only
 * through explicit, versioned commands; generated CRUD writes are not mapped.
 */
@RestController
@RequestMapping("/api/internal-transfers")
public class InternalTransferResource {

    private final InternalTransferCommandService internalTransferCommandService;
    private final InternalTransferCounterpartQueryService internalTransferCounterpartQueryService;
    private final InternalTransferProductQueryService internalTransferProductQueryService;

    public InternalTransferResource(
        InternalTransferCommandService internalTransferCommandService,
        InternalTransferCounterpartQueryService internalTransferCounterpartQueryService,
        InternalTransferProductQueryService internalTransferProductQueryService
    ) {
        this.internalTransferCommandService = internalTransferCommandService;
        this.internalTransferCounterpartQueryService = internalTransferCounterpartQueryService;
        this.internalTransferProductQueryService = internalTransferProductQueryService;
    }

    /** Creates both new transaction legs and their transfer root atomically. */
    @PostMapping("/commands/create")
    public ResponseEntity<InternalTransferReadDTO> createTransfer(@Valid @RequestBody InternalTransferCreateCommand command) {
        InternalTransferReadDTO result = invoke(() -> internalTransferCommandService.createTransfer(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Links two existing immutable transaction legs without changing their provenance. */
    @PostMapping("/commands/link-existing")
    public ResponseEntity<InternalTransferReadDTO> linkExistingTransactions(@Valid @RequestBody InternalTransferLinkCommand command) {
        InternalTransferReadDTO result = invoke(() -> internalTransferCommandService.linkExistingTransactions(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Creates an opposite manual leg for one existing unlinked transaction and links both atomically. */
    @PostMapping("/transactions/{transactionId}/counterpart")
    public ResponseEntity<InternalTransferReadDTO> createCounterpart(
        @PathVariable Long transactionId,
        @Valid @RequestBody InternalTransferCounterpartCommand command
    ) {
        InternalTransferReadDTO result = invoke(() -> internalTransferCommandService.createCounterpartForTransaction(transactionId, command)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Mutates the aggregate through the required optimistic-version command. */
    @PutMapping("/{id}/aggregate")
    public InternalTransferReadDTO updateTransfer(@PathVariable Long id, @Valid @RequestBody InternalTransferUpdateCommand command) {
        return invoke(() -> internalTransferCommandService.updateTransfer(id, command));
    }

    /** Removes only the transfer relation; both financial transactions remain intact. */
    @PostMapping("/{id}/unlink")
    public InternalTransferReadDTO unlinkTransfer(@PathVariable Long id, @Valid @RequestBody InternalTransferUnlinkCommand command) {
        return invoke(() -> internalTransferCommandService.unlinkTransfer(id, command));
    }

    /** Returns same-owner, unlinked candidate legs matching the required counterpart invariants. */
    @GetMapping("/counterparts/{transactionId}")
    public Page<InternalTransferCounterpartDTO> findEligibleCounterparts(
        @PathVariable Long transactionId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long accountId,
        Pageable pageable
    ) {
        return invoke(() -> internalTransferCounterpartQueryService.findEligibleCounterparts(transactionId, search, accountId, pageable));
    }

    @GetMapping("/product")
    public Page<InternalTransferReadDTO> getProductTransfers(
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate,
        Pageable pageable
    ) {
        return internalTransferProductQueryService.findPage(accountId, fromDate, toDate, pageable);
    }

    @GetMapping("/product/{id}")
    public InternalTransferReadDTO getProductTransfer(@PathVariable Long id) {
        return invoke(() -> internalTransferProductQueryService.findOne(id));
    }

    @GetMapping("/transactions/{transactionId}/product")
    public InternalTransferReadDTO getProductTransferForTransaction(@PathVariable Long transactionId) {
        return invoke(() -> internalTransferProductQueryService.findByTransactionId(transactionId));
    }

    private <T> T invoke(TransferAction<T> action) {
        try {
            return action.execute();
        } catch (InternalTransferCommandException exception) {
            throw new ResponseStatusException(statusFor(exception.getCode()), exception.getCode().name(), exception);
        }
    }

    private HttpStatus statusFor(InternalTransferErrorCode code) {
        return switch (code) {
            case TRANSFER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TRANSFER_LEG_ALREADY_LINKED, TRANSFER_COUNTERPART_STALE, TRANSFER_VERSION_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @FunctionalInterface
    private interface TransferAction<T> {
        T execute();
    }
}
