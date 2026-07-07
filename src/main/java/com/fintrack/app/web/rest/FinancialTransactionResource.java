package com.fintrack.app.web.rest;

import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.FinancialTransactionQueryService;
import com.fintrack.app.service.FinancialTransactionService;
import com.fintrack.app.service.criteria.FinancialTransactionCriteria;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.FinancialTransaction}.
 */
@RestController
@RequestMapping("/api/financial-transactions")
public class FinancialTransactionResource {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialTransactionResource.class);

    private static final String ENTITY_NAME = "financialTransaction";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FinancialTransactionService financialTransactionService;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialTransactionQueryService financialTransactionQueryService;

    public FinancialTransactionResource(
        FinancialTransactionService financialTransactionService,
        FinancialTransactionRepository financialTransactionRepository,
        FinancialTransactionQueryService financialTransactionQueryService
    ) {
        this.financialTransactionService = financialTransactionService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionQueryService = financialTransactionQueryService;
    }

    /**
     * {@code POST  /financial-transactions} : Create a new financialTransaction.
     *
     * @param financialTransactionDTO the financialTransactionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new financialTransactionDTO, or with status {@code 400 (Bad Request)} if the financialTransaction has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<FinancialTransactionDTO> createFinancialTransaction(
        @Valid @RequestBody FinancialTransactionDTO financialTransactionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save FinancialTransaction : {}", financialTransactionDTO);
        if (financialTransactionDTO.getId() != null) {
            throw new BadRequestAlertException("A new financialTransaction cannot already have an ID", ENTITY_NAME, "idexists");
        }
        financialTransactionDTO = financialTransactionService.save(financialTransactionDTO);
        return ResponseEntity.created(new URI("/api/financial-transactions/" + financialTransactionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, financialTransactionDTO.getId().toString()))
            .body(financialTransactionDTO);
    }

    /**
     * {@code PUT  /financial-transactions/:id} : Updates an existing financialTransaction.
     *
     * @param id the id of the financialTransactionDTO to save.
     * @param financialTransactionDTO the financialTransactionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialTransactionDTO,
     * or with status {@code 400 (Bad Request)} if the financialTransactionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the financialTransactionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionDTO> updateFinancialTransaction(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody FinancialTransactionDTO financialTransactionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update FinancialTransaction : {}, {}", id, financialTransactionDTO);
        if (financialTransactionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, financialTransactionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialTransactionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        financialTransactionDTO = financialTransactionService.update(financialTransactionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialTransactionDTO.getId().toString()))
            .body(financialTransactionDTO);
    }

    /**
     * {@code PATCH  /financial-transactions/:id} : Partial updates given fields of an existing financialTransaction, field will ignore if it is null
     *
     * @param id the id of the financialTransactionDTO to save.
     * @param financialTransactionDTO the financialTransactionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialTransactionDTO,
     * or with status {@code 400 (Bad Request)} if the financialTransactionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the financialTransactionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the financialTransactionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<FinancialTransactionDTO> partialUpdateFinancialTransaction(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody FinancialTransactionDTO financialTransactionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update FinancialTransaction partially : {}, {}", id, financialTransactionDTO);
        if (financialTransactionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, financialTransactionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialTransactionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<FinancialTransactionDTO> result = financialTransactionService.partialUpdate(financialTransactionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialTransactionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /financial-transactions} : get all the financialTransactions.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of financialTransactions in body.
     */
    @GetMapping("")
    public ResponseEntity<List<FinancialTransactionDTO>> getAllFinancialTransactions(
        FinancialTransactionCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get FinancialTransactions by criteria: {}", criteria);

        Page<FinancialTransactionDTO> page = financialTransactionQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /financial-transactions/count} : count all the financialTransactions.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countFinancialTransactions(FinancialTransactionCriteria criteria) {
        LOG.debug("REST request to count FinancialTransactions by criteria: {}", criteria);
        return ResponseEntity.ok().body(financialTransactionQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /financial-transactions/:id} : get the "id" financialTransaction.
     *
     * @param id the id of the financialTransactionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the financialTransactionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionDTO> getFinancialTransaction(@PathVariable("id") Long id) {
        LOG.debug("REST request to get FinancialTransaction : {}", id);
        Optional<FinancialTransactionDTO> financialTransactionDTO = financialTransactionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(financialTransactionDTO);
    }

    /**
     * {@code DELETE  /financial-transactions/:id} : delete the "id" financialTransaction.
     *
     * @param id the id of the financialTransactionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFinancialTransaction(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete FinancialTransaction : {}", id);
        financialTransactionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
