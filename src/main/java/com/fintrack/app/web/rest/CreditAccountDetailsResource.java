package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.CreditAccountDetailsService;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.CreditAccountDetails}.
 */
@RestController
@RequestMapping("/api/credit-account-details")
public class CreditAccountDetailsResource {

    private static final Logger LOG = LoggerFactory.getLogger(CreditAccountDetailsResource.class);

    private static final String ENTITY_NAME = "creditAccountDetails";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CreditAccountDetailsService creditAccountDetailsService;

    private final ObjectMapper objectMapper;

    public CreditAccountDetailsResource(CreditAccountDetailsService creditAccountDetailsService, ObjectMapper objectMapper) {
        this.creditAccountDetailsService = creditAccountDetailsService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /credit-account-details} : Create a new creditAccountDetails.
     *
     * @param creditAccountDetailsDTO the creditAccountDetailsDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new creditAccountDetailsDTO, or with status {@code 400 (Bad Request)} if the creditAccountDetails has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<CreditAccountDetailsDTO> createCreditAccountDetails(
        @Valid @RequestBody CreditAccountDetailsDTO creditAccountDetailsDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save CreditAccountDetails : {}", creditAccountDetailsDTO);
        if (creditAccountDetailsDTO.getId() != null) {
            throw new BadRequestAlertException("A new creditAccountDetails cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            creditAccountDetailsDTO = creditAccountDetailsService.save(creditAccountDetailsDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/credit-account-details/" + creditAccountDetailsDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, creditAccountDetailsDTO.getId().toString()))
            .body(creditAccountDetailsDTO);
    }

    /**
     * {@code PUT  /credit-account-details/:id} : Updates an existing creditAccountDetails.
     *
     * @param id the id of the creditAccountDetailsDTO to save.
     * @param creditAccountDetailsDTO the creditAccountDetailsDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated creditAccountDetailsDTO,
     * or with status {@code 400 (Bad Request)} if the creditAccountDetailsDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the creditAccountDetailsDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CreditAccountDetailsDTO> updateCreditAccountDetails(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody CreditAccountDetailsDTO creditAccountDetailsDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update CreditAccountDetails : {}, {}", id, creditAccountDetailsDTO);
        if (creditAccountDetailsDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, creditAccountDetailsDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!creditAccountDetailsService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            creditAccountDetailsDTO = creditAccountDetailsService.update(creditAccountDetailsDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, creditAccountDetailsDTO.getId().toString()))
            .body(creditAccountDetailsDTO);
    }

    /**
     * {@code PATCH  /credit-account-details/:id} : Partial updates given fields of an existing creditAccountDetails, field will ignore if it is null
     *
     * @param id the id of the creditAccountDetailsDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated creditAccountDetailsDTO,
     * or with status {@code 400 (Bad Request)} if the creditAccountDetailsDTO is not valid,
     * or with status {@code 404 (Not Found)} if the creditAccountDetailsDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the creditAccountDetailsDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<CreditAccountDetailsDTO> partialUpdateCreditAccountDetails(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update CreditAccountDetails partially : {}, {}", id, patchNode);
        if (patchNode.has("account") && patchNode.get("account").isNull()) {
            throw new BadRequestAlertException("Account cannot be null", ENTITY_NAME, "invalid");
        }
        CreditAccountDetailsDTO creditAccountDetailsDTO;
        try {
            creditAccountDetailsDTO = objectMapper.treeToValue(patchNode, CreditAccountDetailsDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (creditAccountDetailsDTO.getId() == null) {
            creditAccountDetailsDTO.setId(id);
        }
        if (!Objects.equals(id, creditAccountDetailsDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!creditAccountDetailsService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<CreditAccountDetailsDTO> result;
        try {
            result = creditAccountDetailsService.partialUpdate(creditAccountDetailsDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, creditAccountDetailsDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /credit-account-details} : get all the creditAccountDetails.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of creditAccountDetails in body.
     */
    @GetMapping("")
    public List<CreditAccountDetailsDTO> getAllCreditAccountDetails(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all CreditAccountDetails");
        return creditAccountDetailsService.findAll();
    }

    /**
     * {@code GET  /credit-account-details/by-account/:accountId} : get credit card details for a financial account.
     *
     * @param accountId the id of the parent financial account.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the creditAccountDetailsDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<CreditAccountDetailsDTO> getCreditAccountDetailsByAccountId(@PathVariable("accountId") Long accountId) {
        LOG.debug("REST request to get CreditAccountDetails by FinancialAccount : {}", accountId);
        Optional<CreditAccountDetailsDTO> creditAccountDetailsDTO = creditAccountDetailsService.findOneByAccountId(accountId);
        return ResponseUtil.wrapOrNotFound(creditAccountDetailsDTO);
    }

    /**
     * {@code GET  /credit-account-details/:id} : get the "id" creditAccountDetails.
     *
     * @param id the id of the creditAccountDetailsDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the creditAccountDetailsDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CreditAccountDetailsDTO> getCreditAccountDetails(@PathVariable("id") Long id) {
        LOG.debug("REST request to get CreditAccountDetails : {}", id);
        Optional<CreditAccountDetailsDTO> creditAccountDetailsDTO = creditAccountDetailsService.findOne(id);
        return ResponseUtil.wrapOrNotFound(creditAccountDetailsDTO);
    }

    /**
     * {@code DELETE  /credit-account-details/:id} : delete the "id" creditAccountDetails.
     *
     * @param id the id of the creditAccountDetailsDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCreditAccountDetails(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete CreditAccountDetails : {}", id);
        try {
            if (!creditAccountDetailsService.delete(id)) {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
