package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.repository.DescriptionNormalizationRuleConditionRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConditionDTO;
import com.fintrack.app.service.mapper.DescriptionNormalizationRuleConditionMapper;
import com.fintrack.app.service.rules.TextConditionMatcher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DescriptionNormalizationRuleConditionService {

    private final DescriptionNormalizationRuleConditionRepository conditionRepository;
    private final DescriptionNormalizationRuleRepository ruleRepository;
    private final DescriptionNormalizationRuleConditionMapper conditionMapper;
    private final CurrentUserService currentUserService;
    private final TextConditionMatcher textConditionMatcher;

    public DescriptionNormalizationRuleConditionService(
        DescriptionNormalizationRuleConditionRepository conditionRepository,
        DescriptionNormalizationRuleRepository ruleRepository,
        DescriptionNormalizationRuleConditionMapper conditionMapper,
        CurrentUserService currentUserService,
        TextConditionMatcher textConditionMatcher
    ) {
        this.conditionRepository = conditionRepository;
        this.ruleRepository = ruleRepository;
        this.conditionMapper = conditionMapper;
        this.currentUserService = currentUserService;
        this.textConditionMatcher = textConditionMatcher;
    }

    public DescriptionNormalizationRuleConditionDTO save(DescriptionNormalizationRuleConditionDTO dto) {
        DescriptionNormalizationRule parent = resolveAccessibleRule(requiredRuleId(dto));
        DescriptionNormalizationRuleCondition condition = conditionMapper.toEntity(dto);
        condition.setDescriptionNormalizationRule(parent);
        condition.setPosition(nextPosition(parent.getId()));
        Instant now = Instant.now();
        condition.setCreatedAt(now);
        condition.setUpdatedAt(now);
        normalizeAndValidate(condition, null);
        return conditionMapper.toDto(conditionRepository.save(condition));
    }

    public DescriptionNormalizationRuleConditionDTO update(DescriptionNormalizationRuleConditionDTO dto, JsonNode requestNode) {
        DescriptionNormalizationRuleCondition existing = findAccessibleEntity(dto.getId()).orElseThrow();
        rejectParentChange(existing, dto);
        rejectPositionChange(existing, dto.getPosition(), requestNode != null && requestNode.has("position"));
        rejectCreatedAtChange(existing, dto.getCreatedAt());
        rejectUpdatedAtChange(existing, dto.getUpdatedAt());
        DescriptionNormalizationRuleCondition condition = conditionMapper.toEntity(dto);
        condition.setDescriptionNormalizationRule(existing.getDescriptionNormalizationRule());
        condition.setPosition(existing.getPosition());
        condition.setCreatedAt(existing.getCreatedAt());
        condition.setUpdatedAt(Instant.now());
        normalizeAndValidate(condition, existing.getId());
        return conditionMapper.toDto(conditionRepository.save(condition));
    }

    public Optional<DescriptionNormalizationRuleConditionDTO> partialUpdate(
        DescriptionNormalizationRuleConditionDTO dto,
        JsonNode patchNode
    ) {
        return findAccessibleEntity(dto.getId())
            .map(existing -> {
                rejectNullRequiredPatchFields(patchNode);
                if (patchNode != null && patchNode.has("descriptionNormalizationRule")) {
                    rejectParentChange(existing, dto);
                }
                if (patchNode != null && patchNode.has("position")) {
                    rejectPositionChange(existing, dto.getPosition(), true);
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existing, dto.getCreatedAt());
                }
                if (patchNode != null && patchNode.has("updatedAt")) {
                    rejectUpdatedAtChange(existing, dto.getUpdatedAt());
                }
                ConditionSnapshot snapshot = ConditionSnapshot.from(existing);
                try {
                    conditionMapper.partialUpdate(existing, dto);
                    existing.setUpdatedAt(Instant.now());
                    normalizeAndValidate(existing, existing.getId());
                } catch (IllegalArgumentException e) {
                    snapshot.restore(existing);
                    throw e;
                }
                return existing;
            })
            .map(conditionRepository::save)
            .map(conditionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<DescriptionNormalizationRuleConditionDTO> findAll() {
        if (currentUserService.isAdmin()) {
            return conditionMapper.toDto(conditionRepository.findAllWithToOneRelationships());
        }
        return conditionMapper.toDto(
            conditionRepository.findAllWithToOneRelationshipsByRuleUserLogin(currentUserService.getCurrentUserLogin())
        );
    }

    @Transactional(readOnly = true)
    public List<DescriptionNormalizationRuleConditionDTO> findByDescriptionNormalizationRuleId(Long ruleId) {
        resolveAccessibleRule(ruleId);
        return conditionMapper.toDto(conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(ruleId));
    }

    @Transactional(readOnly = true)
    public Optional<DescriptionNormalizationRuleConditionDTO> findOne(Long id) {
        return findAccessibleEntity(id).map(conditionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    public boolean delete(Long id) {
        Optional<DescriptionNormalizationRuleCondition> condition = findAccessibleEntity(id);
        if (condition.isEmpty()) {
            return false;
        }
        Long ruleId = condition.get().getDescriptionNormalizationRule().getId();
        conditionRepository.deleteById(id);
        conditionRepository.flush();
        reindexPositions(ruleId);
        return true;
    }

    private Optional<DescriptionNormalizationRuleCondition> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return conditionRepository.findOneWithToOneRelationships(id);
        }
        return conditionRepository.findOneWithToOneRelationshipsByIdAndRuleUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private DescriptionNormalizationRule resolveAccessibleRule(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Description normalization rule is required");
        }
        Optional<DescriptionNormalizationRule> rule = currentUserService.isAdmin()
            ? ruleRepository.findOneWithToOneRelationships(id)
            : ruleRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
        return rule.orElseThrow(() -> new IllegalArgumentException("Description normalization rule is not accessible"));
    }

    private Long requiredRuleId(DescriptionNormalizationRuleConditionDTO dto) {
        if (dto == null || dto.getDescriptionNormalizationRule() == null || dto.getDescriptionNormalizationRule().getId() == null) {
            throw new IllegalArgumentException("Description normalization rule id is required");
        }
        return dto.getDescriptionNormalizationRule().getId();
    }

    private Integer nextPosition(Long ruleId) {
        Integer maxPosition = conditionRepository.findMaxPositionByDescriptionNormalizationRuleId(ruleId);
        return maxPosition == null ? 0 : maxPosition + 1;
    }

    private void normalizeAndValidate(DescriptionNormalizationRuleCondition condition, Long excludeId) {
        condition.setValue(trimToNull(condition.getValue()));
        if (condition.getOperator() == null) {
            throw new IllegalArgumentException("Operator is required");
        }
        if (condition.getValue() == null) {
            throw new IllegalArgumentException("Value is required");
        }
        if (condition.getValue().length() > 1000) {
            throw new IllegalArgumentException("Value must be at most 1000 characters");
        }
        if (condition.getCaseSensitive() == null) {
            throw new IllegalArgumentException("Case sensitive is required");
        }
        if (condition.getPosition() == null || condition.getPosition() < 0) {
            throw new IllegalArgumentException("Position is required");
        }
        textConditionMatcher.validatePattern(condition.getOperator(), condition.getValue());
        List<DescriptionNormalizationRuleCondition> duplicates = conditionRepository.findPotentialDuplicates(
            condition.getDescriptionNormalizationRule().getId(),
            condition.getOperator(),
            condition.getCaseSensitive(),
            condition.getValue(),
            excludeId
        );
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Description normalization rule condition already exists");
        }
    }

    private void rejectParentChange(DescriptionNormalizationRuleCondition existing, DescriptionNormalizationRuleConditionDTO dto) {
        Long requestedId = requiredRuleId(dto);
        if (!requestedId.equals(existing.getDescriptionNormalizationRule().getId())) {
            throw new IllegalArgumentException("Description normalization rule cannot be changed");
        }
    }

    private void rejectPositionChange(DescriptionNormalizationRuleCondition existing, Integer requestedPosition, boolean positionPresent) {
        if (positionPresent && requestedPosition == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (requestedPosition != null && !requestedPosition.equals(existing.getPosition())) {
            throw new IllegalArgumentException("Position is server-managed");
        }
    }

    private void rejectCreatedAtChange(DescriptionNormalizationRuleCondition existing, Instant requestedCreatedAt) {
        if (requestedCreatedAt == null || !requestedCreatedAt.equals(existing.getCreatedAt())) {
            throw new IllegalArgumentException("createdAt cannot be changed");
        }
    }

    private void rejectUpdatedAtChange(DescriptionNormalizationRuleCondition existing, Instant requestedUpdatedAt) {
        if (requestedUpdatedAt == null || !requestedUpdatedAt.equals(existing.getUpdatedAt())) {
            throw new IllegalArgumentException("updatedAt cannot be changed");
        }
    }

    private void rejectNullRequiredPatchFields(JsonNode patchNode) {
        if (patchNode == null) {
            return;
        }
        rejectNullPatchField(patchNode, "operator");
        rejectNullPatchField(patchNode, "value");
        rejectNullPatchField(patchNode, "caseSensitive");
        rejectNullPatchField(patchNode, "position");
        rejectNullPatchField(patchNode, "descriptionNormalizationRule");
        rejectNullPatchField(patchNode, "createdAt");
        rejectNullPatchField(patchNode, "updatedAt");
    }

    private void rejectNullPatchField(JsonNode patchNode, String fieldName) {
        if (patchNode.has(fieldName) && patchNode.get(fieldName).isNull()) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private void reindexPositions(Long ruleId) {
        List<DescriptionNormalizationRuleCondition> conditions =
            conditionRepository.findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(ruleId);
        for (int index = 0; index < conditions.size(); index++) {
            conditions.get(index).setPosition(index);
        }
        conditionRepository.saveAll(conditions);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ConditionSnapshot(
        com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator operator,
        String value,
        Boolean caseSensitive,
        Integer position,
        Instant createdAt,
        Instant updatedAt
    ) {
        private static ConditionSnapshot from(DescriptionNormalizationRuleCondition condition) {
            return new ConditionSnapshot(
                condition.getOperator(),
                condition.getValue(),
                condition.getCaseSensitive(),
                condition.getPosition(),
                condition.getCreatedAt(),
                condition.getUpdatedAt()
            );
        }

        private void restore(DescriptionNormalizationRuleCondition condition) {
            condition.setOperator(operator);
            condition.setValue(value);
            condition.setCaseSensitive(caseSensitive);
            condition.setPosition(position);
            condition.setCreatedAt(createdAt);
            condition.setUpdatedAt(updatedAt);
        }
    }
}
