package com.fintrack.app.web.rest;

import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.service.TransactionRuleConditionService;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.TransactionRuleCondition}.
 */
@RestController
@RequestMapping("/api/transaction-rule-conditions")
public class TransactionRuleConditionResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleConditionResource.class);

    private static final String ENTITY_NAME = "transactionRuleCondition";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionRuleConditionService transactionRuleConditionService;

    private final TransactionRuleConditionRepository transactionRuleConditionRepository;

    public TransactionRuleConditionResource(
        TransactionRuleConditionService transactionRuleConditionService,
        TransactionRuleConditionRepository transactionRuleConditionRepository
    ) {
        this.transactionRuleConditionService = transactionRuleConditionService;
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
    }

    /**
     * {@code POST  /transaction-rule-conditions} : Create a new transactionRuleCondition.
     *
     * @param transactionRuleConditionDTO the transactionRuleConditionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionRuleConditionDTO, or with status {@code 400 (Bad Request)} if the transactionRuleCondition has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionRuleConditionDTO> createTransactionRuleCondition(
        @Valid @RequestBody TransactionRuleConditionDTO transactionRuleConditionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save TransactionRuleCondition : {}", transactionRuleConditionDTO);
        if (transactionRuleConditionDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionRuleCondition cannot already have an ID", ENTITY_NAME, "idexists");
        }
        transactionRuleConditionDTO = transactionRuleConditionService.save(transactionRuleConditionDTO);
        return ResponseEntity.created(new URI("/api/transaction-rule-conditions/" + transactionRuleConditionDTO.getId()))
            .headers(
                HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, transactionRuleConditionDTO.getId().toString())
            )
            .body(transactionRuleConditionDTO);
    }

    /**
     * {@code PUT  /transaction-rule-conditions/:id} : Updates an existing transactionRuleCondition.
     *
     * @param id the id of the transactionRuleConditionDTO to save.
     * @param transactionRuleConditionDTO the transactionRuleConditionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRuleConditionDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRuleConditionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionRuleConditionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionRuleConditionDTO> updateTransactionRuleCondition(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionRuleConditionDTO transactionRuleConditionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionRuleCondition : {}, {}", id, transactionRuleConditionDTO);
        if (transactionRuleConditionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionRuleConditionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRuleConditionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        transactionRuleConditionDTO = transactionRuleConditionService.update(transactionRuleConditionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionRuleConditionDTO.getId().toString()))
            .body(transactionRuleConditionDTO);
    }

    /**
     * {@code PATCH  /transaction-rule-conditions/:id} : Partial updates given fields of an existing transactionRuleCondition, field will ignore if it is null
     *
     * @param id the id of the transactionRuleConditionDTO to save.
     * @param transactionRuleConditionDTO the transactionRuleConditionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRuleConditionDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRuleConditionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionRuleConditionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionRuleConditionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionRuleConditionDTO> partialUpdateTransactionRuleCondition(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody TransactionRuleConditionDTO transactionRuleConditionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionRuleCondition partially : {}, {}", id, transactionRuleConditionDTO);
        if (transactionRuleConditionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionRuleConditionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRuleConditionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionRuleConditionDTO> result = transactionRuleConditionService.partialUpdate(transactionRuleConditionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionRuleConditionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-rule-conditions} : get all the transactionRuleConditions.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionRuleConditions in body.
     */
    @GetMapping("")
    public List<TransactionRuleConditionDTO> getAllTransactionRuleConditions(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all TransactionRuleConditions");
        return transactionRuleConditionService.findAll();
    }

    /**
     * {@code GET  /transaction-rule-conditions/:id} : get the "id" transactionRuleCondition.
     *
     * @param id the id of the transactionRuleConditionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionRuleConditionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionRuleConditionDTO> getTransactionRuleCondition(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionRuleCondition : {}", id);
        Optional<TransactionRuleConditionDTO> transactionRuleConditionDTO = transactionRuleConditionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionRuleConditionDTO);
    }

    /**
     * {@code DELETE  /transaction-rule-conditions/:id} : delete the "id" transactionRuleCondition.
     *
     * @param id the id of the transactionRuleConditionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionRuleCondition(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionRuleCondition : {}", id);
        transactionRuleConditionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
