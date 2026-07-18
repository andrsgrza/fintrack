package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.CsvIngestionConfirmImportService;
import com.fintrack.app.service.CsvIngestionPreviewService;
import com.fintrack.app.service.CsvIngestionRecordReviewService;
import com.fintrack.app.service.TransactionIngestionQueryService;
import com.fintrack.app.service.TransactionIngestionService;
import com.fintrack.app.service.criteria.TransactionIngestionCriteria;
import com.fintrack.app.service.dto.CsvIngestionConfirmImportResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionPreviewResponseDTO;
import com.fintrack.app.service.dto.CsvIngestionRecordReviewRequestDTO;
import com.fintrack.app.service.dto.CsvIngestionRecordReviewResponseDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.TransactionIngestion}.
 */
@RestController
@RequestMapping("/api/transaction-ingestions")
public class TransactionIngestionResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionIngestionResource.class);

    private static final String ENTITY_NAME = "transactionIngestion";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionIngestionService transactionIngestionService;

    private final TransactionIngestionQueryService transactionIngestionQueryService;

    private final CsvIngestionPreviewService csvIngestionPreviewService;

    private final CsvIngestionRecordReviewService csvIngestionRecordReviewService;

    private final CsvIngestionConfirmImportService csvIngestionConfirmImportService;

    private final ObjectMapper objectMapper;

    public TransactionIngestionResource(
        TransactionIngestionService transactionIngestionService,
        TransactionIngestionQueryService transactionIngestionQueryService,
        CsvIngestionPreviewService csvIngestionPreviewService,
        CsvIngestionRecordReviewService csvIngestionRecordReviewService,
        CsvIngestionConfirmImportService csvIngestionConfirmImportService,
        ObjectMapper objectMapper
    ) {
        this.transactionIngestionService = transactionIngestionService;
        this.transactionIngestionQueryService = transactionIngestionQueryService;
        this.csvIngestionPreviewService = csvIngestionPreviewService;
        this.csvIngestionRecordReviewService = csvIngestionRecordReviewService;
        this.csvIngestionConfirmImportService = csvIngestionConfirmImportService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /transaction-ingestions} : Create a new transactionIngestion.
     *
     * @param transactionIngestionDTO the transactionIngestionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionIngestionDTO, or with status {@code 400 (Bad Request)} if the transactionIngestion has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionIngestionDTO> createTransactionIngestion(
        @Valid @RequestBody TransactionIngestionDTO transactionIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save TransactionIngestion : {}", transactionIngestionDTO);
        if (transactionIngestionDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionIngestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            transactionIngestionDTO = transactionIngestionService.save(transactionIngestionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/transaction-ingestions/" + transactionIngestionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, transactionIngestionDTO.getId().toString()))
            .body(transactionIngestionDTO);
    }

    /**
     * {@code POST /transaction-ingestions/file-preview} : Parse and persist a canonical CSV ingestion preview.
     *
     * I1 preview creates TransactionIngestion, FileIngestion and IngestionRecord rows only.
     * It does not create FinancialTransactions and does not run the Rule Engine.
     *
     * @param accountId the target account id.
     * @param file the canonical CSV upload.
     * @return the persisted preview response.
     */
    @PostMapping(value = "/file-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvIngestionPreviewResponseDTO> createFilePreview(
        @RequestParam(value = "accountId", required = false) Long accountId,
        @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        LOG.debug("REST request to create CSV FileIngestion preview for account : {}", accountId);
        try {
            return ResponseEntity.ok(csvIngestionPreviewService.createPreview(accountId, file));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @GetMapping("/{id}/file-preview")
    public ResponseEntity<CsvIngestionPreviewResponseDTO> getFilePreview(@PathVariable("id") Long id) {
        LOG.debug("REST request to get CSV FileIngestion preview for transaction ingestion : {}", id);
        try {
            return ResponseEntity.ok(csvIngestionPreviewService.getPreview(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PostMapping("/{ingestionId}/records/{recordId}/disable")
    public ResponseEntity<CsvIngestionRecordReviewResponseDTO> disableFilePreviewRecord(
        @PathVariable("ingestionId") Long ingestionId,
        @PathVariable("recordId") Long recordId
    ) {
        LOG.debug("REST request to disable CSV ingestion record : {}, {}", ingestionId, recordId);
        try {
            return ResponseEntity.ok(csvIngestionRecordReviewService.disable(ingestionId, recordId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PostMapping("/{ingestionId}/records/{recordId}/enable")
    public ResponseEntity<CsvIngestionRecordReviewResponseDTO> enableFilePreviewRecord(
        @PathVariable("ingestionId") Long ingestionId,
        @PathVariable("recordId") Long recordId
    ) {
        LOG.debug("REST request to enable CSV ingestion record : {}, {}", ingestionId, recordId);
        try {
            return ResponseEntity.ok(csvIngestionRecordReviewService.enable(ingestionId, recordId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PatchMapping("/{ingestionId}/records/{recordId}")
    public ResponseEntity<CsvIngestionRecordReviewResponseDTO> editFilePreviewRecord(
        @PathVariable("ingestionId") Long ingestionId,
        @PathVariable("recordId") Long recordId,
        @RequestBody(required = false) CsvIngestionRecordReviewRequestDTO request
    ) {
        LOG.debug("REST request to edit CSV ingestion record : {}, {}", ingestionId, recordId);
        try {
            return ResponseEntity.ok(csvIngestionRecordReviewService.edit(ingestionId, recordId, request));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<CsvIngestionConfirmImportResponseDTO> confirmFilePreviewImport(@PathVariable("id") Long id) {
        LOG.debug("REST request to confirm CSV FileIngestion import : {}", id);
        try {
            return ResponseEntity.ok(csvIngestionConfirmImportService.confirm(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    /**
     * {@code PUT  /transaction-ingestions/:id} : Updates an existing transactionIngestion.
     *
     * @param id the id of the transactionIngestionDTO to save.
     * @param transactionIngestionDTO the transactionIngestionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the transactionIngestionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionIngestionDTO> updateTransactionIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionIngestionDTO transactionIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionIngestion : {}, {}", id, transactionIngestionDTO);
        if (transactionIngestionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionIngestionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            transactionIngestionDTO = transactionIngestionService.update(transactionIngestionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionIngestionDTO.getId().toString()))
            .body(transactionIngestionDTO);
    }

    /**
     * {@code PATCH  /transaction-ingestions/:id} : Partial updates given fields of an existing transactionIngestion, field will ignore if it is null
     *
     * @param id the id of the transactionIngestionDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the transactionIngestionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionIngestionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionIngestionDTO> partialUpdateTransactionIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionIngestion partially : {}, {}", id, patchNode);
        if (patchNode.has("account") && patchNode.get("account").isNull()) {
            throw new BadRequestAlertException("Account cannot be null", ENTITY_NAME, "invalid");
        }
        if (patchNode.has("ingestionType") && patchNode.get("ingestionType").isNull()) {
            throw new BadRequestAlertException("Ingestion type cannot be null", ENTITY_NAME, "invalid");
        }
        TransactionIngestionDTO transactionIngestionDTO;
        try {
            transactionIngestionDTO = objectMapper.treeToValue(patchNode, TransactionIngestionDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (transactionIngestionDTO.getId() == null) {
            transactionIngestionDTO.setId(id);
        }
        if (!Objects.equals(id, transactionIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionIngestionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionIngestionDTO> result;
        try {
            result = transactionIngestionService.partialUpdate(transactionIngestionDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, transactionIngestionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-ingestions} : get all the transactionIngestions.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionIngestions in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionIngestionDTO>> getAllTransactionIngestions(
        TransactionIngestionCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get TransactionIngestions by criteria: {}", criteria);

        Page<TransactionIngestionDTO> page = transactionIngestionQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-ingestions/count} : count all the transactionIngestions.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionIngestions(TransactionIngestionCriteria criteria) {
        LOG.debug("REST request to count TransactionIngestions by criteria: {}", criteria);
        return ResponseEntity.ok().body(transactionIngestionQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /transaction-ingestions/file-ingestion-is-null} : get FILE ingestions without file metadata (scoped).
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list in body.
     */
    @GetMapping("/file-ingestion-is-null")
    public List<TransactionIngestionDTO> getAllTransactionIngestionsWhereFileIngestionIsNull() {
        LOG.debug("REST request to get TransactionIngestions where FileIngestion is null");
        return transactionIngestionService.findAllWhereFileIngestionIsNull();
    }

    /**
     * {@code GET  /transaction-ingestions/api-ingestion-is-null} : get API ingestions without api metadata (scoped).
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list in body.
     */
    @GetMapping("/api-ingestion-is-null")
    public List<TransactionIngestionDTO> getAllTransactionIngestionsWhereApiIngestionIsNull() {
        LOG.debug("REST request to get TransactionIngestions where ApiIngestion is null");
        return transactionIngestionService.findAllWhereApiIngestionIsNull();
    }

    /**
     * {@code GET  /transaction-ingestions/:id} : get the "id" transactionIngestion.
     *
     * @param id the id of the transactionIngestionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionIngestionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionIngestionDTO> getTransactionIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionIngestion : {}", id);
        Optional<TransactionIngestionDTO> transactionIngestionDTO = transactionIngestionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionIngestionDTO);
    }

    /**
     * {@code DELETE  /transaction-ingestions/:id} : delete the "id" transactionIngestion.
     *
     * @param id the id of the transactionIngestionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionIngestion : {}", id);
        if (!transactionIngestionService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
