package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.repository.DescriptionNormalizationRuleConditionRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import com.fintrack.app.service.mapper.DescriptionNormalizationRuleMapper;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DescriptionNormalizationRuleService {

    private final DescriptionNormalizationRuleRepository ruleRepository;
    private final DescriptionNormalizationRuleConditionRepository conditionRepository;
    private final DescriptionNormalizationRuleMapper ruleMapper;
    private final CurrentUserService currentUserService;

    public DescriptionNormalizationRuleService(
        DescriptionNormalizationRuleRepository ruleRepository,
        DescriptionNormalizationRuleConditionRepository conditionRepository,
        DescriptionNormalizationRuleMapper ruleMapper,
        CurrentUserService currentUserService
    ) {
        this.ruleRepository = ruleRepository;
        this.conditionRepository = conditionRepository;
        this.ruleMapper = ruleMapper;
        this.currentUserService = currentUserService;
    }

    public DescriptionNormalizationRuleDTO save(DescriptionNormalizationRuleDTO dto) {
        DescriptionNormalizationRule rule = ruleMapper.toEntity(dto);
        rule.setUser(currentUserService.getCurrentUser());
        rule.setPriority(nextPriorityForUser(rule.getUser().getId()));
        Instant now = Instant.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        normalizeAndValidate(rule, null);
        return ruleMapper.toDto(ruleRepository.save(rule));
    }

    public DescriptionNormalizationRuleDTO update(DescriptionNormalizationRuleDTO dto, JsonNode requestNode) {
        DescriptionNormalizationRule existing = findAccessibleEntity(dto.getId()).orElseThrow();
        rejectPriorityChange(existing, dto.getPriority(), requestNode != null && requestNode.has("priority"));
        rejectCreatedAtChange(existing, dto.getCreatedAt());
        rejectUpdatedAtChange(existing, dto.getUpdatedAt());
        DescriptionNormalizationRule rule = ruleMapper.toEntity(dto);
        rule.setUser(existing.getUser());
        rule.setPriority(existing.getPriority());
        rule.setCreatedAt(existing.getCreatedAt());
        rule.setUpdatedAt(Instant.now());
        normalizeAndValidate(rule, existing.getId());
        return ruleMapper.toDto(ruleRepository.save(rule));
    }

    public Optional<DescriptionNormalizationRuleDTO> partialUpdate(DescriptionNormalizationRuleDTO dto, JsonNode patchNode) {
        return findAccessibleEntity(dto.getId())
            .map(existing -> {
                rejectNullRequiredPatchFields(patchNode);
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existing, dto.getCreatedAt());
                }
                if (patchNode != null && patchNode.has("updatedAt")) {
                    rejectUpdatedAtChange(existing, dto.getUpdatedAt());
                }
                if (patchNode != null && patchNode.has("priority")) {
                    rejectPriorityChange(existing, dto.getPriority(), true);
                }
                if (patchNode != null && patchNode.has("active") && Boolean.TRUE.equals(dto.getActive())) {
                    validateActiveRuleHasConditions(existing);
                }
                RuleSnapshot snapshot = RuleSnapshot.from(existing);
                try {
                    ruleMapper.partialUpdate(existing, dto);
                    existing.setUpdatedAt(Instant.now());
                    normalizeAndValidate(existing, existing.getId());
                } catch (IllegalArgumentException e) {
                    snapshot.restore(existing);
                    throw e;
                }
                return existing;
            })
            .map(ruleRepository::save)
            .map(ruleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<DescriptionNormalizationRuleDTO> findAll(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return ruleRepository.findAllWithToOneRelationships(pageable).map(ruleMapper::toDto);
        }
        return ruleRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(ruleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long count() {
        if (currentUserService.isAdmin()) {
            return ruleRepository.count();
        }
        return ruleRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), Pageable.unpaged())
            .getTotalElements();
    }

    @Transactional(readOnly = true)
    public Optional<DescriptionNormalizationRuleDTO> findOne(Long id) {
        return findAccessibleEntity(id).map(ruleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    public boolean delete(Long id) {
        Optional<DescriptionNormalizationRule> rule = findAccessibleEntity(id);
        if (rule.isEmpty()) {
            return false;
        }
        Long ownerId = rule.get().getUser().getId();
        conditionRepository.deleteByDescriptionNormalizationRuleId(id);
        ruleRepository.deleteById(id);
        ruleRepository.flush();
        reindexPriorities(ownerId);
        return true;
    }

    public List<DescriptionNormalizationRuleDTO> reorder(List<Long> orderedIds) {
        Long ownerId = currentUserService.getCurrentUser().getId();
        List<DescriptionNormalizationRule> currentRules = ruleRepository.findByUserIdOrderByPriorityAscIdAsc(ownerId);
        validateReorderIds(orderedIds, currentRules);
        Map<Long, DescriptionNormalizationRule> rulesById = currentRules
            .stream()
            .collect(Collectors.toMap(DescriptionNormalizationRule::getId, Function.identity()));
        List<DescriptionNormalizationRule> orderedRules = orderedIds.stream().map(rulesById::get).toList();
        for (int index = 0; index < orderedRules.size(); index++) {
            orderedRules.get(index).setPriority(index);
        }
        return ruleMapper.toDto(ruleRepository.saveAll(orderedRules));
    }

    private Optional<DescriptionNormalizationRule> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return ruleRepository.findOneWithToOneRelationships(id);
        }
        return ruleRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private Integer nextPriorityForUser(Long userId) {
        Integer maxPriority = ruleRepository.findMaxPriorityByUserId(userId);
        return maxPriority == null ? 0 : maxPriority + 1;
    }

    private void normalizeAndValidate(DescriptionNormalizationRule rule, Long excludeId) {
        rule.setName(trimToNull(rule.getName()));
        rule.setDescription(trimToNull(rule.getDescription()));
        rule.setResultingDescription(trimToNull(rule.getResultingDescription()));
        if (rule.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (rule.getName().length() > 100) {
            throw new IllegalArgumentException("Name must be at most 100 characters");
        }
        if (rule.getDescription() != null && rule.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description must be at most 500 characters");
        }
        if (rule.getResultingDescription() == null) {
            throw new IllegalArgumentException("Resulting description is required");
        }
        if (rule.getResultingDescription().length() > 500) {
            throw new IllegalArgumentException("Resulting description must be at most 500 characters");
        }
        if (rule.getPriority() == null || rule.getPriority() < 0) {
            throw new IllegalArgumentException("Priority is required");
        }
        if (rule.getConditionOperator() == null) {
            throw new IllegalArgumentException("Condition operator is required");
        }
        if (rule.getActive() == null) {
            throw new IllegalArgumentException("Active is required");
        }
        if (ruleRepository.existsByUserLoginAndNormalizedName(rule.getUser().getLogin(), rule.getName().toLowerCase(), excludeId)) {
            throw new IllegalArgumentException("Description normalization rule name already exists");
        }
        validateActiveRuleHasConditions(rule);
    }

    private void validateActiveRuleHasConditions(DescriptionNormalizationRule rule) {
        if (!Boolean.TRUE.equals(rule.getActive())) {
            return;
        }
        if (rule.getId() == null || conditionRepository.countByDescriptionNormalizationRuleId(rule.getId()) == 0) {
            throw new IllegalArgumentException("Active description normalization rule must have at least one condition");
        }
    }

    private void rejectNullRequiredPatchFields(JsonNode patchNode) {
        if (patchNode == null) {
            return;
        }
        rejectNullPatchField(patchNode, "name");
        rejectNullPatchField(patchNode, "active");
        rejectNullPatchField(patchNode, "priority");
        rejectNullPatchField(patchNode, "conditionOperator");
        rejectNullPatchField(patchNode, "resultingDescription");
        rejectNullPatchField(patchNode, "createdAt");
        rejectNullPatchField(patchNode, "updatedAt");
    }

    private void rejectNullPatchField(JsonNode patchNode, String fieldName) {
        if (patchNode.has(fieldName) && patchNode.get(fieldName).isNull()) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private void rejectPriorityChange(DescriptionNormalizationRule existing, Integer requestedPriority, boolean priorityPresent) {
        if (priorityPresent && requestedPriority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        if (requestedPriority != null && !requestedPriority.equals(existing.getPriority())) {
            throw new IllegalArgumentException("Priority is server-managed");
        }
    }

    private void rejectCreatedAtChange(DescriptionNormalizationRule existing, Instant requestedCreatedAt) {
        if (requestedCreatedAt == null || !requestedCreatedAt.equals(existing.getCreatedAt())) {
            throw new IllegalArgumentException("createdAt cannot be changed");
        }
    }

    private void rejectUpdatedAtChange(DescriptionNormalizationRule existing, Instant requestedUpdatedAt) {
        if (requestedUpdatedAt == null || !requestedUpdatedAt.equals(existing.getUpdatedAt())) {
            throw new IllegalArgumentException("updatedAt cannot be changed");
        }
    }

    private void reindexPriorities(Long ownerId) {
        List<DescriptionNormalizationRule> rules = ruleRepository.findByUserIdOrderByPriorityAscIdAsc(ownerId);
        for (int index = 0; index < rules.size(); index++) {
            rules.get(index).setPriority(index);
        }
        ruleRepository.saveAll(rules);
    }

    private void validateReorderIds(List<Long> orderedIds, List<DescriptionNormalizationRule> currentRules) {
        if (orderedIds == null) {
            throw new IllegalArgumentException("orderedIds is required");
        }
        if (!currentRules.isEmpty() && orderedIds.isEmpty()) {
            throw new IllegalArgumentException("orderedIds cannot be empty");
        }
        Set<Long> requestedIds = new LinkedHashSet<>(orderedIds);
        if (requestedIds.size() != orderedIds.size()) {
            throw new IllegalArgumentException("orderedIds cannot contain duplicates");
        }
        Set<Long> currentIds = currentRules.stream().map(DescriptionNormalizationRule::getId).collect(Collectors.toSet());
        if (!requestedIds.equals(currentIds)) {
            throw new IllegalArgumentException("orderedIds must contain each current user description normalization rule exactly once");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RuleSnapshot(
        String name,
        String description,
        Boolean active,
        Integer priority,
        com.fintrack.app.domain.enumeration.RuleConditionLogic conditionOperator,
        String resultingDescription,
        Instant createdAt,
        Instant updatedAt
    ) {
        private static RuleSnapshot from(DescriptionNormalizationRule rule) {
            return new RuleSnapshot(
                rule.getName(),
                rule.getDescription(),
                rule.getActive(),
                rule.getPriority(),
                rule.getConditionOperator(),
                rule.getResultingDescription(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
            );
        }

        private void restore(DescriptionNormalizationRule rule) {
            rule.setName(name);
            rule.setDescription(description);
            rule.setActive(active);
            rule.setPriority(priority);
            rule.setConditionOperator(conditionOperator);
            rule.setResultingDescription(resultingDescription);
            rule.setCreatedAt(createdAt);
            rule.setUpdatedAt(updatedAt);
        }
    }
}
