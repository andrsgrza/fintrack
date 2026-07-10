package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.FinancialSubscriptionQueryService;
import com.fintrack.app.service.FinancialSubscriptionService;
import com.fintrack.app.service.criteria.FinancialSubscriptionCriteria;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.FinancialSubscription}.
 */
@RestController
@RequestMapping("/api/financial-subscriptions")
public class FinancialSubscriptionResource {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialSubscriptionResource.class);

    private static final String ENTITY_NAME = "financialSubscription";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FinancialSubscriptionService financialSubscriptionService;

    private final FinancialSubscriptionQueryService financialSubscriptionQueryService;

    private final ObjectMapper objectMapper;

    public FinancialSubscriptionResource(
        FinancialSubscriptionService financialSubscriptionService,
        FinancialSubscriptionQueryService financialSubscriptionQueryService,
        ObjectMapper objectMapper
    ) {
        this.financialSubscriptionService = financialSubscriptionService;
        this.financialSubscriptionQueryService = financialSubscriptionQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /financial-subscriptions} : Create a new financialSubscription.
     *
     * @param financialSubscriptionDTO the financialSubscriptionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new financialSubscriptionDTO, or with status {@code 400 (Bad Request)} if the financialSubscription has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<FinancialSubscriptionDTO> createFinancialSubscription(
        @Valid @RequestBody FinancialSubscriptionDTO financialSubscriptionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save FinancialSubscription : {}", financialSubscriptionDTO);
        if (financialSubscriptionDTO.getId() != null) {
            throw new BadRequestAlertException("A new financialSubscription cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            financialSubscriptionDTO = financialSubscriptionService.save(financialSubscriptionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/financial-subscriptions/" + financialSubscriptionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, financialSubscriptionDTO.getId().toString()))
            .body(financialSubscriptionDTO);
    }

    /**
     * {@code PUT  /financial-subscriptions/:id} : Updates an existing financialSubscription.
     *
     * @param id the id of the financialSubscriptionDTO to save.
     * @param financialSubscriptionDTO the financialSubscriptionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialSubscriptionDTO,
     * or with status {@code 400 (Bad Request)} if the financialSubscriptionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the financialSubscriptionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FinancialSubscriptionDTO> updateFinancialSubscription(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody FinancialSubscriptionDTO financialSubscriptionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update FinancialSubscription : {}, {}", id, financialSubscriptionDTO);
        if (financialSubscriptionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, financialSubscriptionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialSubscriptionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            financialSubscriptionDTO = financialSubscriptionService.update(financialSubscriptionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialSubscriptionDTO.getId().toString()))
            .body(financialSubscriptionDTO);
    }

    /**
     * {@code PATCH  /financial-subscriptions/:id} : Partial updates given fields of an existing financialSubscription, field will ignore if it is null
     *
     * @param id the id of the financialSubscriptionDTO to save.
     * @param financialSubscriptionDTO the financialSubscriptionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialSubscriptionDTO,
     * or with status {@code 400 (Bad Request)} if the financialSubscriptionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the financialSubscriptionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the financialSubscriptionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<FinancialSubscriptionDTO> partialUpdateFinancialSubscription(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update FinancialSubscription partially : {}, {}", id, patchNode);
        FinancialSubscriptionDTO financialSubscriptionDTO;
        try {
            financialSubscriptionDTO = objectMapper.treeToValue(patchNode, FinancialSubscriptionDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (financialSubscriptionDTO.getId() == null) {
            financialSubscriptionDTO.setId(id);
        }
        if (!Objects.equals(id, financialSubscriptionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialSubscriptionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<FinancialSubscriptionDTO> result;
        try {
            result = financialSubscriptionService.partialUpdate(financialSubscriptionDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialSubscriptionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /financial-subscriptions} : get all the financialSubscriptions.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of financialSubscriptions in body.
     */
    @GetMapping("")
    public ResponseEntity<List<FinancialSubscriptionDTO>> getAllFinancialSubscriptions(FinancialSubscriptionCriteria criteria) {
        LOG.debug("REST request to get FinancialSubscriptions by criteria: {}", criteria);

        List<FinancialSubscriptionDTO> entityList = financialSubscriptionQueryService.findByCriteria(criteria);
        return ResponseEntity.ok().body(entityList);
    }

    /**
     * {@code GET  /financial-subscriptions/count} : count all the financialSubscriptions.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countFinancialSubscriptions(FinancialSubscriptionCriteria criteria) {
        LOG.debug("REST request to count FinancialSubscriptions by criteria: {}", criteria);
        return ResponseEntity.ok().body(financialSubscriptionQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /financial-subscriptions/:id} : get the "id" financialSubscription.
     *
     * @param id the id of the financialSubscriptionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the financialSubscriptionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FinancialSubscriptionDTO> getFinancialSubscription(@PathVariable("id") Long id) {
        LOG.debug("REST request to get FinancialSubscription : {}", id);
        Optional<FinancialSubscriptionDTO> financialSubscriptionDTO = financialSubscriptionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(financialSubscriptionDTO);
    }

    /**
     * {@code DELETE  /financial-subscriptions/:id} : delete the "id" financialSubscription.
     *
     * @param id the id of the financialSubscriptionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFinancialSubscription(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete FinancialSubscription : {}", id);
        if (!financialSubscriptionService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
