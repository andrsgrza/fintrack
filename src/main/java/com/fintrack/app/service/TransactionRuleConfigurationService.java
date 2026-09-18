package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleConfiguredConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleConfiguredRequestDTO;
import com.fintrack.app.service.dto.TransactionRuleConfiguredResponseDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionRuleConfigurationService {

    private final TransactionRuleRepository transactionRuleRepository;

    private final TransactionRuleConditionRepository transactionRuleConditionRepository;

    private final CurrentUserService currentUserService;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final TransactionRuleConditionValidator transactionRuleConditionValidator;

    private final TransactionRuleFlowCategoryCompatibilityValidator transactionRuleFlowCategoryCompatibilityValidator;

    public TransactionRuleConfigurationService(
        TransactionRuleRepository transactionRuleRepository,
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        CurrentUserService currentUserService,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        TransactionRuleConditionValidator transactionRuleConditionValidator,
        TransactionRuleFlowCategoryCompatibilityValidator transactionRuleFlowCategoryCompatibilityValidator
    ) {
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.currentUserService = currentUserService;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.transactionRuleConditionValidator = transactionRuleConditionValidator;
        this.transactionRuleFlowCategoryCompatibilityValidator = transactionRuleFlowCategoryCompatibilityValidator;
    }

    public TransactionRuleConfiguredResponseDTO create(TransactionRuleConfiguredRequestDTO request) {
        TransactionRule rule = new TransactionRule();
        rule.setUser(currentUserService.getCurrentUser());
        rule.setPriority(nextPriorityForUser(rule.getUser().getId()));
        Instant now = Instant.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        applyRequest(rule, request, rule.getUser().getLogin(), null);
        List<TransactionRuleCondition> conditions = toConditions(request, rule);
        validateConfiguredRule(rule, conditions, null, List.of());
        conditions.forEach(condition -> condition.setId(null));

        rule = transactionRuleRepository.save(rule);
        for (TransactionRuleCondition condition : conditions) {
            condition.setTransactionRule(rule);
        }
        transactionRuleConditionRepository.saveAll(conditions);
        return toResponse(rule, conditions);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionRuleConfiguredResponseDTO> findOne(Long id) {
        return findAccessibleEntity(id).map(rule ->
            toResponse(rule, transactionRuleConditionRepository.findByTransactionRuleIdOrderByPositionAscIdAsc(rule.getId()))
        );
    }

    public TransactionRuleConfiguredResponseDTO update(Long id, TransactionRuleConfiguredRequestDTO request) {
        TransactionRule rule = findAccessibleEntity(id).orElseThrow(() -> new IllegalArgumentException("Entity not found"));
        List<TransactionRuleCondition> existingConditions =
            transactionRuleConditionRepository.findByTransactionRuleIdOrderByPositionAscIdAsc(rule.getId());
        applyRequest(rule, request, rule.getUser().getLogin(), rule);
        rule.setUpdatedAt(Instant.now());
        List<TransactionRuleCondition> conditions = toConditions(request, rule);
        validateConfiguredRule(rule, conditions, rule.getId(), existingConditions);

        // Configured updates replace the condition collection atomically. Ids are retained only during validation so an
        // unchanged historical inactive ACCOUNT condition can be distinguished from a newly introduced reference.
        conditions.forEach(condition -> condition.setId(null));

        transactionRuleRepository.save(rule);
        transactionRuleConditionRepository.deleteByTransactionRuleId(rule.getId());
        transactionRuleConditionRepository.flush();
        transactionRuleConditionRepository.saveAll(conditions);
        return toResponse(rule, conditions);
    }

    private Optional<TransactionRule> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithEagerRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRequest(
        TransactionRule rule,
        TransactionRuleConfiguredRequestDTO request,
        String ownerLogin,
        TransactionRule existingReference
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction rule payload is required");
        }
        rule.setName(trimToNull(request.getName()));
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setConditionLogic(request.getConditionLogic());
        rule.setActive(request.getActive());
        Category category = resolveOptionalCategory(request.getResultingCategory(), ownerLogin);
        Long existingCategoryId = existingReference == null || existingReference.getResultingCategory() == null
            ? null
            : existingReference.getResultingCategory().getId();
        if (category != null && !java.util.Objects.equals(category.getId(), existingCategoryId)) {
            CategoryReferenceValidator.validateActiveForNewReference(category);
        }
        rule.setResultingCategory(category);
        Set<Tag> tags = resolveTags(request.getResultingTags(), ownerLogin);
        Set<Long> existingTagIds = existingReference == null
            ? Set.of()
            : existingReference.getResultingTags().stream().map(Tag::getId).collect(Collectors.toSet());
        tags.stream().filter(tag -> !existingTagIds.contains(tag.getId())).forEach(TagReferenceValidator::validateActiveForNewReference);
        rule.setResultingTags(tags);
    }

    private List<TransactionRuleCondition> toConditions(TransactionRuleConfiguredRequestDTO request, TransactionRule rule) {
        if (request == null || request.getConditions() == null) {
            return List.of();
        }
        List<TransactionRuleCondition> conditions = new ArrayList<>();
        for (int index = 0; index < request.getConditions().size(); index++) {
            TransactionRuleConfiguredConditionDTO conditionDTO = request.getConditions().get(index);
            TransactionRuleCondition condition = new TransactionRuleCondition();
            condition.setId(conditionDTO.getId());
            condition.setField(conditionDTO.getField());
            condition.setOperator(conditionDTO.getOperator());
            condition.setValue(trimToNull(conditionDTO.getValue()));
            condition.setSecondValue(trimToNull(conditionDTO.getSecondValue()));
            condition.setCaseSensitive(conditionDTO.getCaseSensitive());
            condition.setPosition(index);
            condition.setTransactionRule(rule);
            conditions.add(condition);
        }
        return conditions;
    }

    private void validateConfiguredRule(
        TransactionRule rule,
        List<TransactionRuleCondition> conditions,
        Long excludeId,
        List<TransactionRuleCondition> existingConditions
    ) {
        validateRequiredFields(rule);
        validateUniqueName(rule, excludeId);
        validateHasOutput(rule);
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("Transaction rule must have at least one condition");
        }
        transactionRuleConditionValidator.validateConditionSet(conditions, existingConditions);
        transactionRuleFlowCategoryCompatibilityValidator.validate(rule, conditions);
    }

    private void validateRequiredFields(TransactionRule rule) {
        if (rule.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (rule.getName().length() > 100) {
            throw new IllegalArgumentException("Name must be at most 100 characters");
        }
        if (rule.getDescription() != null && rule.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description must be at most 500 characters");
        }
        if (rule.getPriority() == null || rule.getPriority() < 0) {
            throw new IllegalArgumentException("Priority must be greater than or equal to 0");
        }
        if (rule.getConditionLogic() == null) {
            throw new IllegalArgumentException("Condition logic is required");
        }
        if (rule.getActive() == null) {
            throw new IllegalArgumentException("Active is required");
        }
    }

    private void validateUniqueName(TransactionRule rule, Long excludeId) {
        boolean exists = transactionRuleRepository.existsByUserLoginAndNormalizedName(
            rule.getUser().getLogin(),
            rule.getName().toLowerCase(),
            excludeId
        );
        if (exists) {
            throw new IllegalArgumentException("Transaction rule name already exists");
        }
    }

    private void validateHasOutput(TransactionRule rule) {
        boolean hasOutput = rule.getResultingCategory() != null || (rule.getResultingTags() != null && !rule.getResultingTags().isEmpty());
        if (!hasOutput) {
            throw new IllegalArgumentException("Transaction rule must have at least one output");
        }
    }

    private Category resolveOptionalCategory(CategoryDTO categoryDTO, String ownerLogin) {
        if (categoryDTO == null) {
            return null;
        }
        if (categoryDTO.getId() == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs, String ownerLogin) {
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Tag> tags = new LinkedHashSet<>();
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO == null || tagDTO.getId() == null) {
                throw new IllegalArgumentException("Tag id is required");
            }
            tags.add(
                tagRepository
                    .findOneWithToOneRelationshipsByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                    .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"))
            );
        }
        return tags;
    }

    private Integer nextPriorityForUser(Long userId) {
        Integer maxPriority = transactionRuleRepository.findMaxPriorityByUserId(userId);
        return maxPriority == null ? 0 : maxPriority + 1;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TransactionRuleConfiguredResponseDTO toResponse(TransactionRule rule, List<TransactionRuleCondition> conditions) {
        TransactionRuleConfiguredResponseDTO response = new TransactionRuleConfiguredResponseDTO();
        response.setId(rule.getId());
        response.setName(rule.getName());
        response.setDescription(rule.getDescription());
        response.setPriority(rule.getPriority());
        response.setConditionLogic(rule.getConditionLogic());
        response.setActive(rule.getActive());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        response.setResultingCategory(toCategoryDTO(rule.getResultingCategory()));
        response.setResultingTags(
            rule.getResultingTags().stream().map(this::toTagDTO).collect(Collectors.toCollection(LinkedHashSet::new))
        );
        response.setConditions(
            conditions
                .stream()
                .sorted(
                    Comparator.comparing(TransactionRuleCondition::getPosition).thenComparing(condition ->
                        condition.getId() == null ? 0L : condition.getId()
                    )
                )
                .map(this::toConditionDTO)
                .toList()
        );
        return response;
    }

    private CategoryDTO toCategoryDTO(Category category) {
        if (category == null) {
            return null;
        }
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setCategoryType(category.getCategoryType());
        return dto;
    }

    private TagDTO toTagDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        return dto;
    }

    private TransactionRuleConfiguredConditionDTO toConditionDTO(TransactionRuleCondition condition) {
        TransactionRuleConfiguredConditionDTO dto = new TransactionRuleConfiguredConditionDTO();
        dto.setId(condition.getId());
        dto.setField(condition.getField());
        dto.setOperator(condition.getOperator());
        dto.setValue(condition.getValue());
        dto.setSecondValue(condition.getSecondValue());
        dto.setCaseSensitive(condition.getCaseSensitive());
        dto.setPosition(condition.getPosition());
        return dto;
    }
}
