package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.TransactionRuleQueryService;
import com.fintrack.app.service.TransactionRuleService;
import com.fintrack.app.service.criteria.TransactionRuleCriteria;
import com.fintrack.app.service.dto.TransactionRuleDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.TransactionRule}.
 */
@RestController
@RequestMapping("/api/transaction-rules")
public class TransactionRuleResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleResource.class);

    private static final String ENTITY_NAME = "transactionRule";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionRuleService transactionRuleService;

    private final TransactionRuleQueryService transactionRuleQueryService;

    private final ObjectMapper objectMapper;

    public TransactionRuleResource(
        TransactionRuleService transactionRuleService,
        TransactionRuleQueryService transactionRuleQueryService,
        ObjectMapper objectMapper
    ) {
        this.transactionRuleService = transactionRuleService;
        this.transactionRuleQueryService = transactionRuleQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /transaction-rules} : Create a new transactionRule.
     *
     * @param transactionRuleDTO the transactionRuleDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionRuleDTO, or with status {@code 400 (Bad Request)} if the transactionRule has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionRuleDTO> createTransactionRule(@Valid @RequestBody TransactionRuleDTO transactionRuleDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save TransactionRule : {}", transactionRuleDTO);
        if (transactionRuleDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionRule cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            transactionRuleDTO = transactionRuleService.save(transactionRuleDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/transaction-rules/" + transactionRuleDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, transactionRuleDTO.getId().toString()))
            .body(transactionRuleDTO);
    }

    /**
     * {@code PUT  /transaction-rules/:id} : Updates an existing transactionRule.
     *
     * @param id the id of the transactionRuleDTO to save.
     * @param transactionRuleDTO the transactionRuleDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRuleDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRuleDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionRuleDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionRuleDTO> updateTransactionRule(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionRuleDTO transactionRuleDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionRule : {}, {}", id, transactionRuleDTO);
        if (transactionRuleDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionRuleDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRuleService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            transactionRuleDTO = transactionRuleService.update(transactionRuleDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionRuleDTO.getId().toString()))
            .body(transactionRuleDTO);
    }

    /**
     * {@code PATCH  /transaction-rules/:id} : Partial updates given fields of an existing transactionRule.
     * Field presence is significant: absent fields are preserved, while explicit null is accepted only for nullable fields.
     *
     * @param id the id of the transactionRuleDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRuleDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRuleDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionRuleDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionRuleDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionRuleDTO> partialUpdateTransactionRule(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionRule partially : {}, {}", id, patchNode);
        TransactionRuleDTO transactionRuleDTO;
        try {
            transactionRuleDTO = objectMapper.treeToValue(patchNode, TransactionRuleDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (transactionRuleDTO.getId() == null) {
            transactionRuleDTO.setId(id);
        }
        if (!Objects.equals(id, transactionRuleDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRuleService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionRuleDTO> result;
        try {
            result = transactionRuleService.partialUpdate(transactionRuleDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionRuleDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-rules} : get all the transactionRules.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionRules in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionRuleDTO>> getAllTransactionRules(TransactionRuleCriteria criteria) {
        LOG.debug("REST request to get TransactionRules by criteria: {}", criteria);

        List<TransactionRuleDTO> entityList = transactionRuleQueryService.findByCriteria(criteria);
        return ResponseEntity.ok().body(entityList);
    }

    /**
     * {@code GET  /transaction-rules/count} : count all the transactionRules.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionRules(TransactionRuleCriteria criteria) {
        LOG.debug("REST request to count TransactionRules by criteria: {}", criteria);
        return ResponseEntity.ok().body(transactionRuleQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /transaction-rules/:id} : get the "id" transactionRule.
     *
     * @param id the id of the transactionRuleDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionRuleDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionRuleDTO> getTransactionRule(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionRule : {}", id);
        Optional<TransactionRuleDTO> transactionRuleDTO = transactionRuleService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionRuleDTO);
    }

    /**
     * {@code DELETE  /transaction-rules/:id} : delete the "id" transactionRule.
     *
     * @param id the id of the transactionRuleDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionRule(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionRule : {}", id);
        if (!transactionRuleService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
