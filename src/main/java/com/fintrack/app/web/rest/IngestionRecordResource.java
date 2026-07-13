package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.IngestionRecordQueryService;
import com.fintrack.app.service.IngestionRecordService;
import com.fintrack.app.service.criteria.IngestionRecordCriteria;
import com.fintrack.app.service.dto.IngestionRecordDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.IngestionRecord}.
 */
@RestController
@RequestMapping("/api/ingestion-records")
public class IngestionRecordResource {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRecordResource.class);

    private static final String ENTITY_NAME = "ingestionRecord";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final IngestionRecordService ingestionRecordService;

    private final IngestionRecordQueryService ingestionRecordQueryService;

    private final ObjectMapper objectMapper;

    public IngestionRecordResource(
        IngestionRecordService ingestionRecordService,
        IngestionRecordQueryService ingestionRecordQueryService,
        ObjectMapper objectMapper
    ) {
        this.ingestionRecordService = ingestionRecordService;
        this.ingestionRecordQueryService = ingestionRecordQueryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<IngestionRecordDTO> createIngestionRecord(@Valid @RequestBody IngestionRecordDTO ingestionRecordDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save IngestionRecord : {}", ingestionRecordDTO);
        if (ingestionRecordDTO.getId() != null) {
            throw new BadRequestAlertException("A new ingestionRecord cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            ingestionRecordDTO = ingestionRecordService.save(ingestionRecordDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/ingestion-records/" + ingestionRecordDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString()))
            .body(ingestionRecordDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IngestionRecordDTO> updateIngestionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode updateNode
    ) throws URISyntaxException {
        LOG.debug("REST request to update IngestionRecord : {}, {}", id, updateNode);
        IngestionRecordDTO ingestionRecordDTO;
        try {
            ingestionRecordDTO = objectMapper.treeToValue(updateNode, IngestionRecordDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid update payload", ENTITY_NAME, "invalid");
        }
        if (ingestionRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ingestionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ingestionRecordService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            ingestionRecordDTO = ingestionRecordService.update(ingestionRecordDTO, updateNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString()))
            .body(ingestionRecordDTO);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<IngestionRecordDTO> partialUpdateIngestionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update IngestionRecord partially : {}, {}", id, patchNode);
        if (patchNode.has("transactionIngestion") && patchNode.get("transactionIngestion").isNull()) {
            throw new BadRequestAlertException("Transaction ingestion cannot be changed", ENTITY_NAME, "invalid");
        }
        IngestionRecordDTO ingestionRecordDTO;
        try {
            ingestionRecordDTO = objectMapper.treeToValue(patchNode, IngestionRecordDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (ingestionRecordDTO.getId() == null) {
            ingestionRecordDTO.setId(id);
        }
        if (!Objects.equals(id, ingestionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ingestionRecordService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<IngestionRecordDTO> result;
        try {
            result = ingestionRecordService.partialUpdate(ingestionRecordDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<IngestionRecordDTO>> getAllIngestionRecords(
        IngestionRecordCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get IngestionRecords by criteria: {}", criteria);

        Page<IngestionRecordDTO> page = ingestionRecordQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countIngestionRecords(IngestionRecordCriteria criteria) {
        LOG.debug("REST request to count IngestionRecords by criteria: {}", criteria);
        return ResponseEntity.ok().body(ingestionRecordQueryService.countByCriteria(criteria));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngestionRecordDTO> getIngestionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to get IngestionRecord : {}", id);
        Optional<IngestionRecordDTO> ingestionRecordDTO = ingestionRecordService.findOne(id);
        return ResponseUtil.wrapOrNotFound(ingestionRecordDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIngestionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete IngestionRecord : {}", id);
        try {
            if (!ingestionRecordService.delete(id)) {
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
