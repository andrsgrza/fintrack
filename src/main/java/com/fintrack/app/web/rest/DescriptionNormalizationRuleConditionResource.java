package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.DescriptionNormalizationRuleConditionService;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConditionDTO;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

@RestController
@RequestMapping("/api/description-normalization-rule-conditions")
public class DescriptionNormalizationRuleConditionResource {

    private static final String ENTITY_NAME = "descriptionNormalizationRuleCondition";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DescriptionNormalizationRuleConditionService conditionService;
    private final ObjectMapper objectMapper;

    public DescriptionNormalizationRuleConditionResource(
        DescriptionNormalizationRuleConditionService conditionService,
        ObjectMapper objectMapper
    ) {
        this.conditionService = conditionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<DescriptionNormalizationRuleConditionDTO> createDescriptionNormalizationRuleCondition(
        @Valid @RequestBody DescriptionNormalizationRuleConditionDTO dto
    ) throws URISyntaxException {
        if (dto.getId() != null) {
            throw new BadRequestAlertException(
                "A new descriptionNormalizationRuleCondition cannot already have an ID",
                ENTITY_NAME,
                "idexists"
            );
        }
        try {
            dto = conditionService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/description-normalization-rule-conditions/" + dto.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DescriptionNormalizationRuleConditionDTO> updateDescriptionNormalizationRuleCondition(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode requestNode
    ) throws URISyntaxException {
        DescriptionNormalizationRuleConditionDTO dto;
        try {
            dto = objectMapper.treeToValue(requestNode, DescriptionNormalizationRuleConditionDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid update payload", ENTITY_NAME, "invalid");
        }
        validateId(id, dto.getId());
        if (!conditionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            dto = conditionService.update(dto, requestNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DescriptionNormalizationRuleConditionDTO> partialUpdateDescriptionNormalizationRuleCondition(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        DescriptionNormalizationRuleConditionDTO dto;
        try {
            dto = objectMapper.treeToValue(patchNode, DescriptionNormalizationRuleConditionDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (dto.getId() == null) {
            dto.setId(id);
        }
        validateId(id, dto.getId());
        if (!conditionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        Optional<DescriptionNormalizationRuleConditionDTO> result;
        try {
            result = conditionService.partialUpdate(dto, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dto.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<DescriptionNormalizationRuleConditionDTO>> getAllDescriptionNormalizationRuleConditions() {
        return ResponseEntity.ok().body(conditionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DescriptionNormalizationRuleConditionDTO> getDescriptionNormalizationRuleCondition(@PathVariable("id") Long id) {
        return ResponseUtil.wrapOrNotFound(conditionService.findOne(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDescriptionNormalizationRuleCondition(@PathVariable("id") Long id) {
        if (!conditionService.delete(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    private void validateId(Long pathId, Long bodyId) {
        if (bodyId == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(pathId, bodyId)) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
    }
}
