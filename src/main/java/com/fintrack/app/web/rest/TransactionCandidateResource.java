package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.TransactionCandidateService;
import com.fintrack.app.service.dto.ManualTransactionDraftSummaryDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionCandidateRuleApplyResponseDTO;
import com.fintrack.app.service.dto.TransactionCandidateRulePreviewResponseDTO;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.TransactionCandidate}.
 *
 * TC-1 backend foundation only: this technical endpoint is not wired into the product UI yet.
 */
@RestController
@RequestMapping("/api/transaction-candidates")
public class TransactionCandidateResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionCandidateResource.class);

    private static final String ENTITY_NAME = "transactionCandidate";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionCandidateService transactionCandidateService;

    private final ObjectMapper objectMapper;

    public TransactionCandidateResource(TransactionCandidateService transactionCandidateService, ObjectMapper objectMapper) {
        this.transactionCandidateService = transactionCandidateService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /transaction-candidates} : Create a new transactionCandidate.
     *
     * @param transactionCandidateDTO the transactionCandidateDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionCandidateDTO.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionCandidateDTO> createTransactionCandidate(
        @Valid @RequestBody TransactionCandidateDTO transactionCandidateDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save TransactionCandidate : {}", transactionCandidateDTO);
        if (transactionCandidateDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionCandidate cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            transactionCandidateDTO = transactionCandidateService.save(transactionCandidateDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/transaction-candidates/" + transactionCandidateDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, transactionCandidateDTO.getId().toString()))
            .body(transactionCandidateDTO);
    }

    /**
     * {@code POST  /transaction-candidates/manual} : Create a recoverable manual transaction draft.
     *
     * @param transactionCandidateDTO the editable manual draft fields.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new manual draft.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/manual")
    public ResponseEntity<TransactionCandidateDTO> createManualTransactionCandidate(
        @RequestBody TransactionCandidateDTO transactionCandidateDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to create manual TransactionCandidate draft");
        if (transactionCandidateDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionCandidate cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            transactionCandidateDTO = transactionCandidateService.createManualDraft(transactionCandidateDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/transaction-candidates/" + transactionCandidateDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, transactionCandidateDTO.getId().toString()))
            .body(transactionCandidateDTO);
    }

    /**
     * {@code PUT  /transaction-candidates/:id} : Updates an existing transactionCandidate.
     *
     * @param id the id of the transactionCandidateDTO to save.
     * @param transactionCandidateDTO the transactionCandidateDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionCandidateDTO.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionCandidateDTO> updateTransactionCandidate(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionCandidateDTO transactionCandidateDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionCandidate : {}, {}", id, transactionCandidateDTO);
        if (transactionCandidateDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionCandidateDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!transactionCandidateService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            transactionCandidateDTO = transactionCandidateService.update(transactionCandidateDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionCandidateDTO.getId().toString()))
            .body(transactionCandidateDTO);
    }

    /**
     * {@code PATCH  /transaction-candidates/:id} : Partial updates given fields of an existing transactionCandidate.
     *
     * @param id the id of the transactionCandidateDTO to save.
     * @param patchNode the raw patch payload.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionCandidateDTO.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionCandidateDTO> partialUpdateTransactionCandidate(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionCandidate partially : {}, {}", id, patchNode);
        TransactionCandidateDTO transactionCandidateDTO;
        try {
            transactionCandidateDTO = objectMapper.treeToValue(patchNode, TransactionCandidateDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (transactionCandidateDTO.getId() == null) {
            transactionCandidateDTO.setId(id);
        }
        if (transactionCandidateDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionCandidateDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!transactionCandidateService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionCandidateDTO> result;
        try {
            result = transactionCandidateService.partialUpdate(transactionCandidateDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionCandidateDTO.getId().toString())
        );
    }

    /**
     * {@code PATCH  /transaction-candidates/:id/manual-draft} : Autosave editable fields on a manual draft.
     *
     * @param id the id of the manual draft to update.
     * @param patchNode the raw patch payload.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated manual draft.
     */
    @PatchMapping(value = "/{id}/manual-draft", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionCandidateDTO> updateManualTransactionCandidateDraft(
        @PathVariable("id") final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) {
        LOG.debug("REST request to autosave manual TransactionCandidate draft : {}, {}", id, patchNode);
        TransactionCandidateDTO transactionCandidateDTO;
        try {
            transactionCandidateDTO = objectMapper.treeToValue(patchNode, TransactionCandidateDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        transactionCandidateDTO.setId(id);

        Optional<TransactionCandidateDTO> result;
        try {
            result = transactionCandidateService.updateManualDraft(id, transactionCandidateDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionCandidateDTO.getId().toString())
        );
    }

    /**
     * {@code POST  /transaction-candidates/:id/cancel} : Cancel a manual draft.
     *
     * @param id the id of the manual draft to cancel.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the cancelled draft.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<TransactionCandidateDTO> cancelManualTransactionCandidate(@PathVariable("id") Long id) {
        LOG.debug("REST request to cancel manual TransactionCandidate draft : {}", id);
        Optional<TransactionCandidateDTO> result;
        try {
            result = transactionCandidateService.cancelManualDraft(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * {@code POST  /transaction-candidates/:id/post} : Post a manual draft into a FinancialTransaction.
     *
     * @param id the id of the manual draft to post.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the posted draft.
     */
    @PostMapping("/{id}/post")
    public ResponseEntity<TransactionCandidateDTO> postManualTransactionCandidate(@PathVariable("id") Long id) {
        LOG.debug("REST request to post manual TransactionCandidate draft : {}", id);
        Optional<TransactionCandidateDTO> result;
        try {
            result = transactionCandidateService.postManualDraft(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * {@code POST  /transaction-candidates/:id/rule-preview} : Preview TransactionRule suggestions for a manual draft.
     *
     * @param id the id of the manual draft to preview.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transient rule preview.
     */
    @PostMapping("/{id}/rule-preview")
    public ResponseEntity<TransactionCandidateRulePreviewResponseDTO> previewManualTransactionCandidateRules(@PathVariable("id") Long id) {
        LOG.debug("REST request to preview TransactionRule suggestions for TransactionCandidate : {}", id);
        Optional<TransactionCandidateRulePreviewResponseDTO> result;
        try {
            result = transactionCandidateService.previewRules(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * {@code POST  /transaction-candidates/:id/apply-rules} : Apply TransactionRule suggestions to a manual draft.
     *
     * @param id the id of the manual draft to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated candidate plus rule metadata.
     */
    @PostMapping("/{id}/apply-rules")
    public ResponseEntity<TransactionCandidateRuleApplyResponseDTO> applyManualTransactionCandidateRules(@PathVariable("id") Long id) {
        LOG.debug("REST request to apply TransactionRule suggestions to TransactionCandidate : {}", id);
        Optional<TransactionCandidateRuleApplyResponseDTO> result;
        try {
            result = transactionCandidateService.applyRules(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * {@code GET  /transaction-candidates} : get all the transactionCandidates for the current owner.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionCandidates in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionCandidateDTO>> getAllTransactionCandidates(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of TransactionCandidates");
        var page = transactionCandidateService.findAll(pageable);
        var headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-candidates/manual-drafts} : get recoverable manual transaction draft summaries for the current owner.
     *
     * This product-safe query only returns MANUAL candidates that can still be resumed in the manual transaction flow.
     * Generic TransactionCandidate CRUD remains technical/debug only.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the page of draft summaries in body.
     */
    @GetMapping("/manual-drafts")
    public ResponseEntity<List<ManualTransactionDraftSummaryDTO>> getManualTransactionDrafts(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get recoverable manual TransactionCandidate drafts");
        var page = transactionCandidateService.findRecoverableManualDrafts(pageable);
        var headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-candidates/count} : count all visible transactionCandidates.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionCandidates() {
        LOG.debug("REST request to count TransactionCandidates");
        return ResponseEntity.ok().body(transactionCandidateService.count());
    }

    /**
     * {@code GET  /transaction-candidates/:id} : get the "id" transactionCandidate.
     *
     * @param id the id of the transactionCandidateDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionCandidateDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionCandidateDTO> getTransactionCandidate(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionCandidate : {}", id);
        Optional<TransactionCandidateDTO> transactionCandidateDTO = transactionCandidateService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionCandidateDTO);
    }

    /**
     * {@code DELETE  /transaction-candidates/:id} : delete the "id" transactionCandidate.
     *
     * @param id the id of the transactionCandidateDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionCandidate(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionCandidate : {}", id);
        try {
            if (!transactionCandidateService.delete(id)) {
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
