package com.fintrack.app.web.rest;

import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.InternalTransferService;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    private final InternalTransferRepository internalTransferRepository;

    public InternalTransferResource(
        InternalTransferService internalTransferService,
        InternalTransferRepository internalTransferRepository
    ) {
        this.internalTransferService = internalTransferService;
        this.internalTransferRepository = internalTransferRepository;
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
        internalTransferDTO = internalTransferService.save(internalTransferDTO);
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
        @Valid @RequestBody InternalTransferDTO internalTransferDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update InternalTransfer : {}, {}", id, internalTransferDTO);
        if (internalTransferDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, internalTransferDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!internalTransferRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        internalTransferDTO = internalTransferService.update(internalTransferDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, internalTransferDTO.getId().toString()))
            .body(internalTransferDTO);
    }

    /**
     * {@code PATCH  /internal-transfers/:id} : Partial updates given fields of an existing internalTransfer, field will ignore if it is null
     *
     * @param id the id of the internalTransferDTO to save.
     * @param internalTransferDTO the internalTransferDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated internalTransferDTO,
     * or with status {@code 400 (Bad Request)} if the internalTransferDTO is not valid,
     * or with status {@code 404 (Not Found)} if the internalTransferDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the internalTransferDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<InternalTransferDTO> partialUpdateInternalTransfer(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody InternalTransferDTO internalTransferDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update InternalTransfer partially : {}, {}", id, internalTransferDTO);
        if (internalTransferDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, internalTransferDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!internalTransferRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<InternalTransferDTO> result = internalTransferService.partialUpdate(internalTransferDTO);

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
        internalTransferService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
