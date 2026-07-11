package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.FinancialAccountQueryService;
import com.fintrack.app.service.FinancialAccountService;
import com.fintrack.app.service.criteria.FinancialAccountCriteria;
import com.fintrack.app.service.dto.FinancialAccountDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.FinancialAccount}.
 */
@RestController
@RequestMapping("/api/financial-accounts")
public class FinancialAccountResource {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialAccountResource.class);

    private static final String ENTITY_NAME = "financialAccount";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FinancialAccountService financialAccountService;

    private final FinancialAccountQueryService financialAccountQueryService;

    private final ObjectMapper objectMapper;

    public FinancialAccountResource(
        FinancialAccountService financialAccountService,
        FinancialAccountQueryService financialAccountQueryService,
        ObjectMapper objectMapper
    ) {
        this.financialAccountService = financialAccountService;
        this.financialAccountQueryService = financialAccountQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /financial-accounts} : Create a new financialAccount.
     *
     * @param financialAccountDTO the financialAccountDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new financialAccountDTO, or with status {@code 400 (Bad Request)} if the financialAccount has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<FinancialAccountDTO> createFinancialAccount(@Valid @RequestBody FinancialAccountDTO financialAccountDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save FinancialAccount : {}", financialAccountDTO);
        if (financialAccountDTO.getId() != null) {
            throw new BadRequestAlertException("A new financialAccount cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            financialAccountDTO = financialAccountService.save(financialAccountDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/financial-accounts/" + financialAccountDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, financialAccountDTO.getId().toString()))
            .body(financialAccountDTO);
    }

    /**
     * {@code PUT  /financial-accounts/:id} : Updates an existing financialAccount.
     *
     * @param id the id of the financialAccountDTO to save.
     * @param financialAccountDTO the financialAccountDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialAccountDTO,
     * or with status {@code 400 (Bad Request)} if the financialAccountDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the financialAccountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FinancialAccountDTO> updateFinancialAccount(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody FinancialAccountDTO financialAccountDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update FinancialAccount : {}, {}", id, financialAccountDTO);
        if (financialAccountDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, financialAccountDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialAccountService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            financialAccountDTO = financialAccountService.update(financialAccountDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialAccountDTO.getId().toString()))
            .body(financialAccountDTO);
    }

    /**
     * {@code PATCH  /financial-accounts/:id} : Partial updates given fields of an existing financialAccount, field will ignore if it is null
     *
     * @param id the id of the financialAccountDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated financialAccountDTO,
     * or with status {@code 400 (Bad Request)} if the financialAccountDTO is not valid,
     * or with status {@code 404 (Not Found)} if the financialAccountDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the financialAccountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<FinancialAccountDTO> partialUpdateFinancialAccount(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update FinancialAccount partially : {}, {}", id, patchNode);
        if (patchNode.has("currency") && patchNode.get("currency").isNull()) {
            throw new BadRequestAlertException("Currency cannot be changed", ENTITY_NAME, "invalid");
        }
        if (patchNode.has("accountType") && patchNode.get("accountType").isNull()) {
            throw new BadRequestAlertException("Account type cannot be changed", ENTITY_NAME, "invalid");
        }
        FinancialAccountDTO financialAccountDTO;
        try {
            financialAccountDTO = objectMapper.treeToValue(patchNode, FinancialAccountDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (financialAccountDTO.getId() == null) {
            financialAccountDTO.setId(id);
        }
        if (!Objects.equals(id, financialAccountDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!financialAccountService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<FinancialAccountDTO> result;
        try {
            result = financialAccountService.partialUpdate(financialAccountDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, financialAccountDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /financial-accounts} : get all the financialAccounts.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of financialAccounts in body.
     */
    @GetMapping("")
    public ResponseEntity<List<FinancialAccountDTO>> getAllFinancialAccounts(FinancialAccountCriteria criteria) {
        LOG.debug("REST request to get FinancialAccounts by criteria: {}", criteria);

        List<FinancialAccountDTO> entityList = financialAccountQueryService.findByCriteria(criteria);
        return ResponseEntity.ok().body(entityList);
    }

    /**
     * {@code GET  /financial-accounts/count} : count all the financialAccounts.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countFinancialAccounts(FinancialAccountCriteria criteria) {
        LOG.debug("REST request to count FinancialAccounts by criteria: {}", criteria);
        return ResponseEntity.ok().body(financialAccountQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /financial-accounts/:id} : get the "id" financialAccount.
     *
     * @param id the id of the financialAccountDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the financialAccountDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FinancialAccountDTO> getFinancialAccount(@PathVariable("id") Long id) {
        LOG.debug("REST request to get FinancialAccount : {}", id);
        Optional<FinancialAccountDTO> financialAccountDTO = financialAccountService.findOne(id);
        return ResponseUtil.wrapOrNotFound(financialAccountDTO);
    }

    /**
     * {@code DELETE  /financial-accounts/:id} : delete the "id" financialAccount.
     *
     * @param id the id of the financialAccountDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFinancialAccount(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete FinancialAccount : {}", id);
        if (!financialAccountService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
