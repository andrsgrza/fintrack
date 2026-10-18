package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.InternalTransferService;
import com.fintrack.app.service.dto.InternalTransferCounterpartCommand;
import com.fintrack.app.service.dto.InternalTransferCounterpartDTO;
import com.fintrack.app.service.dto.InternalTransferCreateCommand;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.dto.InternalTransferLinkCommand;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import com.fintrack.app.service.dto.InternalTransferUnlinkCommand;
import com.fintrack.app.service.dto.InternalTransferUpdateCommand;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCommandService;
import com.fintrack.app.service.transfer.InternalTransferCounterpartQueryService;
import com.fintrack.app.service.transfer.InternalTransferErrorCode;
import com.fintrack.app.service.transfer.InternalTransferProductQueryService;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.InternalTransfer}.
 */
@RestController
@RequestMapping("/api/internal-transfers")
public class InternalTransferResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalTransferResource.class);

    private static final String ENTITY_NAME = "internalTransfer";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final InternalTransferService internalTransferService;

    private final ObjectMapper objectMapper;

    private final InternalTransferCommandService internalTransferCommandService;

    private final InternalTransferCounterpartQueryService internalTransferCounterpartQueryService;

    private final InternalTransferProductQueryService internalTransferProductQueryService;

    public InternalTransferResource(
        InternalTransferService internalTransferService,
        ObjectMapper objectMapper,
        InternalTransferCommandService internalTransferCommandService,
        InternalTransferCounterpartQueryService internalTransferCounterpartQueryService,
        InternalTransferProductQueryService internalTransferProductQueryService
    ) {
        this.internalTransferService = internalTransferService;
        this.objectMapper = objectMapper;
        this.internalTransferCommandService = internalTransferCommandService;
        this.internalTransferCounterpartQueryService = internalTransferCounterpartQueryService;
        this.internalTransferProductQueryService = internalTransferProductQueryService;
    }

    /**
     * Creates the two new transaction legs and their transfer root as one atomic,
     * rule-free manual transfer command.
     */
    @PostMapping("/commands/create")
    public ResponseEntity<InternalTransferReadDTO> createTransfer(@Valid @RequestBody InternalTransferCreateCommand command) {
        InternalTransferReadDTO result = invoke(() -> internalTransferCommandService.createTransfer(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Links two existing immutable transaction legs without changing their classifications or provenance. */
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

    /** Mutates the aggregate's shared amount/date and bounded presentation fields with optimistic version checking. */
    @PutMapping("/{id}/aggregate")
    public InternalTransferReadDTO updateTransfer(@PathVariable Long id, @Valid @RequestBody InternalTransferUpdateCommand command) {
        return invoke(() -> internalTransferCommandService.updateTransfer(id, command));
    }

    /** Removes only the transfer relation; both financial transactions remain intact. */
    @PostMapping("/{id}/unlink")
    public InternalTransferReadDTO unlinkTransfer(@PathVariable Long id, @Valid @RequestBody InternalTransferUnlinkCommand command) {
        return invoke(() -> internalTransferCommandService.unlinkTransfer(id, command));
    }

    /**
     * Returns only same-owner, unlinked candidate legs matching currency, amount,
     * date, and the opposite flow. It never searches another owner's data.
     */
    @GetMapping("/counterparts/{transactionId}")
    public Page<InternalTransferCounterpartDTO> findEligibleCounterparts(
        @PathVariable Long transactionId,
        @RequestParam(required = false) String search,
        Pageable pageable
    ) {
        return invoke(() -> internalTransferCounterpartQueryService.findEligibleCounterparts(transactionId, search, pageable));
    }

    /** Product list.  It is intentionally separate from the legacy generated CRUD collection endpoint. */
    @GetMapping("/product")
    public Page<InternalTransferReadDTO> getProductTransfers(
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate,
        Pageable pageable
    ) {
        return internalTransferProductQueryService.findPage(accountId, fromDate, toDate, pageable);
    }

    /** Product detail with category/tag history for both linked legs. */
    @GetMapping("/product/{id}")
    public InternalTransferReadDTO getProductTransfer(@PathVariable Long id) {
        return invoke(() -> internalTransferProductQueryService.findOne(id));
    }

    /** Compact relationship read model used by a posted FinancialTransaction page. */
    @GetMapping("/transactions/{transactionId}/product")
    public InternalTransferReadDTO getProductTransferForTransaction(@PathVariable Long transactionId) {
        return invoke(() -> internalTransferProductQueryService.findByTransactionId(transactionId));
    }

    /**
     * {@code POST  /internal-transfers} : Create a new internalTransfer.
     *
     * @param internalTransferDTO the internalTransferDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new internalTransferDTO, or with status {@code 400 (Bad Request)} if the internalTransfer has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<InternalTransferDTO> createInternalTransfer(@Valid @RequestBody InternalTransferDTO internalTransferDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save InternalTransfer : {}", internalTransferDTO);
        if (internalTransferDTO.getId() != null) {
            throw new BadRequestAlertException("A new internalTransfer cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            InternalTransferDTO transferToCreate = internalTransferDTO;
            internalTransferDTO = internalTransferService.save(transferToCreate);
        } catch (InternalTransferCommandException | IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/internal-transfers/" + internalTransferDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, internalTransferDTO.getId().toString()))
            .body(internalTransferDTO);
    }

    /**
     * {@code PUT  /internal-transfers/:id} : Updates an existing internalTransfer.
     *
     * @param id the id of the internalTransferDTO to save.
     * @param internalTransferDTO the internalTransferDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated internalTransferDTO,
     * or with status {@code 400 (Bad Request)} if the internalTransferDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the internalTransferDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<InternalTransferDTO> updateInternalTransfer(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode updateNode
    ) throws URISyntaxException {
        InternalTransferDTO internalTransferDTO;
        try {
            internalTransferDTO = objectMapper.treeToValue(updateNode, InternalTransferDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid update payload", ENTITY_NAME, "invalid");
        }
        LOG.debug("REST request to update InternalTransfer : {}, {}", id, internalTransferDTO);
        if (internalTransferDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, internalTransferDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!internalTransferService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            internalTransferDTO = internalTransferService.update(internalTransferDTO, updateNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, internalTransferDTO.getId().toString()))
            .body(internalTransferDTO);
    }

    /**
     * {@code PATCH  /internal-transfers/:id} : Partial updates given fields of an existing internalTransfer, field will ignore if it is null
     *
     * @param id the id of the internalTransferDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated internalTransferDTO,
     * or with status {@code 400 (Bad Request)} if the internalTransferDTO is not valid,
     * or with status {@code 404 (Not Found)} if the internalTransferDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the internalTransferDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<InternalTransferDTO> partialUpdateInternalTransfer(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update InternalTransfer partially : {}, {}", id, patchNode);
        if (patchNode.has("outgoingTransaction") && patchNode.get("outgoingTransaction").isNull()) {
            throw new BadRequestAlertException("Outgoing transaction cannot be null", ENTITY_NAME, "invalid");
        }
        if (patchNode.has("incomingTransaction") && patchNode.get("incomingTransaction").isNull()) {
            throw new BadRequestAlertException("Incoming transaction cannot be null", ENTITY_NAME, "invalid");
        }
        if (patchNode.has("createdAt") && patchNode.get("createdAt").isNull()) {
            throw new BadRequestAlertException("Created at cannot be null", ENTITY_NAME, "invalid");
        }
        InternalTransferDTO internalTransferDTO;
        try {
            internalTransferDTO = objectMapper.treeToValue(patchNode, InternalTransferDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (internalTransferDTO.getId() == null) {
            internalTransferDTO.setId(id);
        }
        if (!Objects.equals(id, internalTransferDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!internalTransferService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<InternalTransferDTO> result;
        try {
            result = internalTransferService.partialUpdate(internalTransferDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, internalTransferDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /internal-transfers} : get all the internalTransfers.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of internalTransfers in body.
     */
    @GetMapping("")
    public List<InternalTransferDTO> getAllInternalTransfers() {
        LOG.debug("REST request to get all InternalTransfers");
        return internalTransferService.findAll();
    }

    /**
     * {@code GET  /internal-transfers/:id} : get the "id" internalTransfer.
     *
     * @param id the id of the internalTransferDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the internalTransferDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InternalTransferDTO> getInternalTransfer(@PathVariable("id") Long id) {
        LOG.debug("REST request to get InternalTransfer : {}", id);
        Optional<InternalTransferDTO> internalTransferDTO = internalTransferService.findOne(id);
        return ResponseUtil.wrapOrNotFound(internalTransferDTO);
    }

    /**
     * {@code DELETE  /internal-transfers/:id} : delete the "id" internalTransfer.
     *
     * @param id the id of the internalTransferDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInternalTransfer(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete InternalTransfer : {}", id);
        if (!invoke(() -> internalTransferService.delete(id))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
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
