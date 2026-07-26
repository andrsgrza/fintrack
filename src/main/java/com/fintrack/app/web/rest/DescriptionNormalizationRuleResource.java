package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.DescriptionNormalizationRuleConditionService;
import com.fintrack.app.service.DescriptionNormalizationRuleService;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConditionDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleReorderRequestDTO;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

@RestController
@RequestMapping("/api/description-normalization-rules")
public class DescriptionNormalizationRuleResource {

    private static final String ENTITY_NAME = "descriptionNormalizationRule";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DescriptionNormalizationRuleService ruleService;
    private final DescriptionNormalizationRuleConditionService conditionService;
    private final ObjectMapper objectMapper;

    public DescriptionNormalizationRuleResource(
        DescriptionNormalizationRuleService ruleService,
        DescriptionNormalizationRuleConditionService conditionService,
        ObjectMapper objectMapper
    ) {
        this.ruleService = ruleService;
        this.conditionService = conditionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<DescriptionNormalizationRuleDTO> createDescriptionNormalizationRule(
        @Valid @RequestBody DescriptionNormalizationRuleDTO dto
    ) throws URISyntaxException {
        if (dto.getId() != null) {
            throw new BadRequestAlertException("A new descriptionNormalizationRule cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            dto = ruleService.save(dto);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/description-normalization-rules/" + dto.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<DescriptionNormalizationRuleDTO>> reorderDescriptionNormalizationRules(
        @RequestBody(required = false) DescriptionNormalizationRuleReorderRequestDTO request
    ) {
        try {
            return ResponseEntity.ok().body(ruleService.reorder(request == null ? null : request.getOrderedIds()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DescriptionNormalizationRuleDTO> updateDescriptionNormalizationRule(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode requestNode
    ) throws URISyntaxException {
        DescriptionNormalizationRuleDTO dto;
        try {
            dto = objectMapper.treeToValue(requestNode, DescriptionNormalizationRuleDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid update payload", ENTITY_NAME, "invalid");
        }
        validateId(id, dto.getId());
        if (!ruleService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        try {
            dto = ruleService.update(dto, requestNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dto.getId().toString()))
            .body(dto);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<DescriptionNormalizationRuleDTO> partialUpdateDescriptionNormalizationRule(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        DescriptionNormalizationRuleDTO dto;
        try {
            dto = objectMapper.treeToValue(patchNode, DescriptionNormalizationRuleDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (dto.getId() == null) {
            dto.setId(id);
        }
        validateId(id, dto.getId());
        if (!ruleService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        Optional<DescriptionNormalizationRuleDTO> result;
        try {
            result = ruleService.partialUpdate(dto, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, dto.getId().toString())
        );
    }

    @GetMapping("")
    public ResponseEntity<List<DescriptionNormalizationRuleDTO>> getAllDescriptionNormalizationRules(Pageable pageable) {
        var page = ruleService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countDescriptionNormalizationRules() {
        return ResponseEntity.ok().body(ruleService.count());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DescriptionNormalizationRuleDTO> getDescriptionNormalizationRule(@PathVariable("id") Long id) {
        return ResponseUtil.wrapOrNotFound(ruleService.findOne(id));
    }

    @GetMapping("/{id}/conditions")
    public ResponseEntity<List<DescriptionNormalizationRuleConditionDTO>> getConditions(@PathVariable("id") Long id) {
        if (!ruleService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        return ResponseEntity.ok().body(conditionService.findByDescriptionNormalizationRuleId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDescriptionNormalizationRule(@PathVariable("id") Long id) {
        if (!ruleService.delete(id)) {
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
