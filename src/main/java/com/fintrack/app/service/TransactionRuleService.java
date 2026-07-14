package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionRule}.
 */
@Service
@Transactional
public class TransactionRuleService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleService.class);

    private final TransactionRuleRepository transactionRuleRepository;

    private final TransactionRuleMapper transactionRuleMapper;

    private final CurrentUserService currentUserService;

    private final CategoryRepository categoryRepository;

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final TagRepository tagRepository;

    private final TransactionRuleConditionRepository transactionRuleConditionRepository;

    public TransactionRuleService(
        TransactionRuleRepository transactionRuleRepository,
        TransactionRuleMapper transactionRuleMapper,
        CurrentUserService currentUserService,
        CategoryRepository categoryRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        TagRepository tagRepository,
        TransactionRuleConditionRepository transactionRuleConditionRepository
    ) {
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleMapper = transactionRuleMapper;
        this.currentUserService = currentUserService;
        this.categoryRepository = categoryRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.tagRepository = tagRepository;
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
    }

    /**
     * Save a transactionRule.
     *
     * @param transactionRuleDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRuleDTO save(TransactionRuleDTO transactionRuleDTO) {
        LOG.debug("Request to save TransactionRule : {}", transactionRuleDTO);
        TransactionRule transactionRule = transactionRuleMapper.toEntity(transactionRuleDTO);
        transactionRule.setUser(currentUserService.getCurrentUser());
        applyRelationships(transactionRule, transactionRuleDTO, transactionRule.getUser().getLogin());
        transactionRule.setPriority(nextPriorityForUser(transactionRule.getUser().getId()));
        Instant now = Instant.now();
        transactionRule.setCreatedAt(now);
        transactionRule.setUpdatedAt(now);
        normalizeAndValidate(transactionRule, null);
        transactionRule = transactionRuleRepository.save(transactionRule);
        return transactionRuleMapper.toDto(transactionRule);
    }

    /**
     * Update a transactionRule.
     *
     * @param transactionRuleDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRuleDTO update(TransactionRuleDTO transactionRuleDTO) {
        return update(transactionRuleDTO, null);
    }

    /**
     * Update a transactionRule.
     *
     * @param transactionRuleDTO the entity to save.
     * @param requestNode the raw PUT payload, used to distinguish omitted priority from explicit null.
     * @return the persisted entity.
     */
    public TransactionRuleDTO update(TransactionRuleDTO transactionRuleDTO, JsonNode requestNode) {
        LOG.debug("Request to update TransactionRule : {}", transactionRuleDTO);
        TransactionRule existingTransactionRule = findAccessibleEntity(transactionRuleDTO.getId()).orElseThrow();
        rejectPriorityChange(existingTransactionRule, transactionRuleDTO.getPriority(), requestNode != null && requestNode.has("priority"));
        rejectCreatedAtChange(existingTransactionRule, transactionRuleDTO.getCreatedAt());
        rejectUpdatedAtChange(existingTransactionRule, transactionRuleDTO.getUpdatedAt());
        TransactionRule transactionRule = transactionRuleMapper.toEntity(transactionRuleDTO);
        transactionRule.setUser(existingTransactionRule.getUser());
        transactionRule.setPriority(existingTransactionRule.getPriority());
        applyRelationships(transactionRule, transactionRuleDTO, existingTransactionRule.getUser().getLogin());
        transactionRule.setCreatedAt(existingTransactionRule.getCreatedAt());
        transactionRule.setUpdatedAt(Instant.now());
        normalizeAndValidate(transactionRule, existingTransactionRule.getId());
        transactionRule = transactionRuleRepository.save(transactionRule);
        return transactionRuleMapper.toDto(transactionRule);
    }

    /**
     * Partially update a transactionRule.
     *
     * @param transactionRuleDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionRuleDTO> partialUpdate(TransactionRuleDTO transactionRuleDTO) {
        return partialUpdate(transactionRuleDTO, null);
    }

    /**
     * Partially update a transactionRule, applying link changes only for JSON fields present in the patch body.
     *
     * @param transactionRuleDTO the entity to update partially.
     * @param patchNode the raw patch payload; when null, link fields are updated only if non-null in the DTO.
     * @return the persisted entity.
     */
    public Optional<TransactionRuleDTO> partialUpdate(TransactionRuleDTO transactionRuleDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update TransactionRule : {}", transactionRuleDTO);

        return findAccessibleEntity(transactionRuleDTO.getId())
            .map(existingTransactionRule -> {
                rejectNullRequiredPatchFields(patchNode);
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existingTransactionRule, transactionRuleDTO.getCreatedAt());
                }
                if (patchNode != null && patchNode.has("updatedAt")) {
                    rejectUpdatedAtChange(existingTransactionRule, transactionRuleDTO.getUpdatedAt());
                }
                if (patchNode != null && patchNode.has("priority")) {
                    rejectPriorityChange(existingTransactionRule, transactionRuleDTO.getPriority(), true);
                }
                if (patchNode != null && patchNode.has("active") && Boolean.TRUE.equals(transactionRuleDTO.getActive())) {
                    validateActiveRuleHasConditions(existingTransactionRule);
                }
                TransactionRuleSnapshot snapshot = TransactionRuleSnapshot.from(existingTransactionRule);
                try {
                    transactionRuleMapper.partialUpdate(existingTransactionRule, transactionRuleDTO);
                    applyRelationshipsForPartialUpdate(
                        existingTransactionRule,
                        transactionRuleDTO,
                        patchNode,
                        existingTransactionRule.getUser().getLogin()
                    );
                    existingTransactionRule.setUpdatedAt(Instant.now());
                    normalizeAndValidate(existingTransactionRule, existingTransactionRule.getId());
                } catch (IllegalArgumentException e) {
                    snapshot.restore(existingTransactionRule);
                    throw e;
                }
                return existingTransactionRule;
            })
            .map(transactionRuleRepository::save)
            .map(transactionRuleMapper::toDto);
    }

    /**
     * Get all the transactionRules with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionRuleDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findAllWithEagerRelationships(pageable).map(transactionRuleMapper::toDto);
        }
        return transactionRuleRepository
            .findAllWithEagerRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(transactionRuleMapper::toDto);
    }

    /**
     * Get one transactionRule by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionRuleDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionRule : {}", id);
        return findAccessibleEntity(id).map(transactionRuleMapper::toDto);
    }

    /**
     * Returns whether the current user can access the transaction rule.
     *
     * @param id the id of the entity.
     * @return true when the rule exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the transactionRule by id.
     *
     * @param id the id of the entity.
     * @return true when the rule was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionRule : {}", id);
        Optional<TransactionRule> transactionRule = findAccessibleEntity(id);
        if (transactionRule.isEmpty()) {
            return false;
        }
        transactionRuleConditionRepository.deleteByTransactionRuleId(id);
        transactionRuleRepository.deleteResultingTagsByRuleId(id);
        Long ownerId = transactionRule.get().getUser().getId();
        transactionRuleRepository.deleteById(id);
        transactionRuleRepository.flush();
        reindexPriorities(ownerId);
        return true;
    }

    /**
     * Reorder the current user's transaction rules.
     *
     * @param orderedIds the complete desired order of the current user's rule ids.
     * @return the reordered rules.
     */
    public List<TransactionRuleDTO> reorder(List<Long> orderedIds) {
        LOG.debug("Request to reorder TransactionRules : {}", orderedIds);
        Long ownerId = currentUserService.getCurrentUser().getId();
        List<TransactionRule> currentRules = transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(ownerId);
        validateReorderIds(orderedIds, currentRules);

        Map<Long, TransactionRule> rulesById = currentRules.stream().collect(Collectors.toMap(TransactionRule::getId, Function.identity()));
        List<TransactionRule> orderedRules = orderedIds.stream().map(rulesById::get).toList();
        for (int index = 0; index < orderedRules.size(); index++) {
            orderedRules.get(index).setPriority(index);
        }
        transactionRuleRepository.saveAll(orderedRules);
        return transactionRuleMapper.toDto(orderedRules);
    }

    private Optional<TransactionRule> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithEagerRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRelationships(TransactionRule transactionRule, TransactionRuleDTO transactionRuleDTO, String ownerLogin) {
        transactionRule.setResultingCategory(resolveOptionalCategory(transactionRuleDTO.getResultingCategory(), ownerLogin));
        transactionRule.setResultingFinancialSubscription(
            resolveOptionalSubscription(transactionRuleDTO.getResultingFinancialSubscription(), ownerLogin)
        );
        transactionRule.setResultingTags(resolveTags(transactionRuleDTO.getResultingTags(), ownerLogin));
    }

    private void applyRelationshipsForPartialUpdate(
        TransactionRule transactionRule,
        TransactionRuleDTO transactionRuleDTO,
        JsonNode patchNode,
        String ownerLogin
    ) {
        if (patchNode != null) {
            if (patchNode.has("resultingCategory")) {
                transactionRule.setResultingCategory(
                    resolveOptionalCategoryForPatch(
                        transactionRuleDTO.getResultingCategory(),
                        patchNode.get("resultingCategory"),
                        ownerLogin
                    )
                );
            }
            if (patchNode.has("resultingFinancialSubscription")) {
                transactionRule.setResultingFinancialSubscription(
                    resolveOptionalSubscriptionForPatch(
                        transactionRuleDTO.getResultingFinancialSubscription(),
                        patchNode.get("resultingFinancialSubscription"),
                        ownerLogin
                    )
                );
            }
            if (patchNode.has("resultingTags")) {
                transactionRule.setResultingTags(
                    resolveTagsForPatch(transactionRuleDTO.getResultingTags(), patchNode.get("resultingTags"), ownerLogin)
                );
            }
            return;
        }
        if (transactionRuleDTO.getResultingCategory() != null) {
            transactionRule.setResultingCategory(resolveOptionalCategory(transactionRuleDTO.getResultingCategory(), ownerLogin));
        }
        if (transactionRuleDTO.getResultingFinancialSubscription() != null) {
            transactionRule.setResultingFinancialSubscription(
                resolveOptionalSubscription(transactionRuleDTO.getResultingFinancialSubscription(), ownerLogin)
            );
        }
        if (transactionRuleDTO.getResultingTags() != null) {
            transactionRule.setResultingTags(resolveTags(transactionRuleDTO.getResultingTags(), ownerLogin));
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
            .findOneByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Category resolveOptionalCategoryForPatch(CategoryDTO categoryDTO, JsonNode categoryNode, String ownerLogin) {
        if (categoryNode == null || categoryNode.isNull()) {
            return null;
        }
        return resolveOptionalCategory(categoryDTO, ownerLogin);
    }

    private FinancialSubscription resolveOptionalSubscription(FinancialSubscriptionDTO subscriptionDTO, String ownerLogin) {
        if (subscriptionDTO == null) {
            return null;
        }
        if (subscriptionDTO.getId() == null) {
            throw new IllegalArgumentException("Financial subscription id is required");
        }
        return financialSubscriptionRepository
            .findOneByIdAndUserLogin(subscriptionDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial subscription is not accessible"));
    }

    private FinancialSubscription resolveOptionalSubscriptionForPatch(
        FinancialSubscriptionDTO subscriptionDTO,
        JsonNode subscriptionNode,
        String ownerLogin
    ) {
        if (subscriptionNode == null || subscriptionNode.isNull()) {
            return null;
        }
        return resolveOptionalSubscription(subscriptionDTO, ownerLogin);
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs, String ownerLogin) {
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO == null || tagDTO.getId() == null) {
                throw new IllegalArgumentException("Tag id is required");
            }
            Tag tag = tagRepository
                .findOneByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }

    private Set<Tag> resolveTagsForPatch(Set<TagDTO> tagDTOs, JsonNode tagsNode, String ownerLogin) {
        if (tagsNode == null || tagsNode.isNull()) {
            return new HashSet<>();
        }
        return resolveTags(tagDTOs, ownerLogin);
    }

    private void rejectNullRequiredPatchFields(JsonNode patchNode) {
        if (patchNode == null) {
            return;
        }
        rejectNullPatchField(patchNode, "name");
        rejectNullPatchField(patchNode, "priority");
        rejectNullPatchField(patchNode, "conditionLogic");
        rejectNullPatchField(patchNode, "active");
        rejectNullPatchField(patchNode, "createdAt");
        rejectNullPatchField(patchNode, "updatedAt");
    }

    private void rejectNullPatchField(JsonNode patchNode, String fieldName) {
        if (patchNode.has(fieldName) && patchNode.get(fieldName).isNull()) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private Integer nextPriorityForUser(Long userId) {
        Integer maxPriority = transactionRuleRepository.findMaxPriorityByUserId(userId);
        return maxPriority == null ? 0 : maxPriority + 1;
    }

    private void rejectPriorityChange(TransactionRule existingTransactionRule, Integer requestedPriority, boolean priorityPresent) {
        if (priorityPresent && requestedPriority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        if (requestedPriority != null && !requestedPriority.equals(existingTransactionRule.getPriority())) {
            throw new IllegalArgumentException("Priority is server-managed");
        }
    }

    private void reindexPriorities(Long ownerId) {
        List<TransactionRule> rules = transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(ownerId);
        for (int index = 0; index < rules.size(); index++) {
            TransactionRule rule = rules.get(index);
            if (!Integer.valueOf(index).equals(rule.getPriority())) {
                rule.setPriority(index);
            }
        }
        transactionRuleRepository.saveAll(rules);
    }

    private void validateReorderIds(List<Long> orderedIds, List<TransactionRule> currentRules) {
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
        Set<Long> currentIds = currentRules.stream().map(TransactionRule::getId).collect(Collectors.toSet());
        if (!requestedIds.equals(currentIds)) {
            throw new IllegalArgumentException("orderedIds must contain each current user transaction rule exactly once");
        }
    }

    private void rejectCreatedAtChange(TransactionRule existingTransactionRule, Instant requestedCreatedAt) {
        if (requestedCreatedAt == null || !requestedCreatedAt.equals(existingTransactionRule.getCreatedAt())) {
            throw new IllegalArgumentException("createdAt cannot be changed");
        }
    }

    private void rejectUpdatedAtChange(TransactionRule existingTransactionRule, Instant requestedUpdatedAt) {
        if (requestedUpdatedAt == null || !requestedUpdatedAt.equals(existingTransactionRule.getUpdatedAt())) {
            throw new IllegalArgumentException("updatedAt cannot be changed");
        }
    }

    private void normalizeAndValidate(TransactionRule transactionRule, Long excludeId) {
        normalizeTextFields(transactionRule);
        validateRequiredFields(transactionRule);
        validateUniqueName(transactionRule, excludeId);
        validateHasOutput(transactionRule);
        validateActiveRuleHasConditions(transactionRule);
    }

    private void normalizeTextFields(TransactionRule transactionRule) {
        transactionRule.setName(trimToNull(transactionRule.getName()));
        transactionRule.setDescription(trimToNull(transactionRule.getDescription()));
        transactionRule.setResultingDescription(trimToNull(transactionRule.getResultingDescription()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateRequiredFields(TransactionRule transactionRule) {
        if (transactionRule.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (transactionRule.getName().length() > 100) {
            throw new IllegalArgumentException("Name must be at most 100 characters");
        }
        if (transactionRule.getDescription() != null && transactionRule.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description must be at most 500 characters");
        }
        if (transactionRule.getResultingDescription() != null && transactionRule.getResultingDescription().length() > 500) {
            throw new IllegalArgumentException("Resulting description must be at most 500 characters");
        }
        if (transactionRule.getPriority() == null) {
            throw new IllegalArgumentException("Priority is required");
        }
        if (transactionRule.getPriority() < 0) {
            throw new IllegalArgumentException("Priority must be greater than or equal to 0");
        }
        if (transactionRule.getConditionLogic() == null) {
            throw new IllegalArgumentException("Condition logic is required");
        }
        if (transactionRule.getActive() == null) {
            throw new IllegalArgumentException("Active is required");
        }
    }

    private void validateUniqueName(TransactionRule transactionRule, Long excludeId) {
        boolean exists = transactionRuleRepository.existsByUserLoginAndNormalizedName(
            transactionRule.getUser().getLogin(),
            transactionRule.getName().toLowerCase(),
            excludeId
        );
        if (exists) {
            throw new IllegalArgumentException("Transaction rule name already exists");
        }
    }

    private void validateHasOutput(TransactionRule transactionRule) {
        boolean hasOutput =
            transactionRule.getResultingCategory() != null ||
            transactionRule.getResultingFinancialSubscription() != null ||
            (transactionRule.getResultingTags() != null && !transactionRule.getResultingTags().isEmpty()) ||
            transactionRule.getResultingDescription() != null;
        if (!hasOutput) {
            throw new IllegalArgumentException("Transaction rule must have at least one output");
        }
    }

    private void validateActiveRuleHasConditions(TransactionRule transactionRule) {
        if (!Boolean.TRUE.equals(transactionRule.getActive())) {
            return;
        }
        if (transactionRule.getId() == null || transactionRuleConditionRepository.countByTransactionRuleId(transactionRule.getId()) == 0) {
            throw new IllegalArgumentException("Active transaction rule must have at least one condition");
        }
    }

    private record TransactionRuleSnapshot(
        String name,
        String description,
        Integer priority,
        com.fintrack.app.domain.enumeration.RuleConditionLogic conditionLogic,
        String resultingDescription,
        Boolean active,
        Instant createdAt,
        Instant updatedAt,
        Category resultingCategory,
        FinancialSubscription resultingFinancialSubscription,
        Set<Tag> resultingTags
    ) {
        private static TransactionRuleSnapshot from(TransactionRule transactionRule) {
            return new TransactionRuleSnapshot(
                transactionRule.getName(),
                transactionRule.getDescription(),
                transactionRule.getPriority(),
                transactionRule.getConditionLogic(),
                transactionRule.getResultingDescription(),
                transactionRule.getActive(),
                transactionRule.getCreatedAt(),
                transactionRule.getUpdatedAt(),
                transactionRule.getResultingCategory(),
                transactionRule.getResultingFinancialSubscription(),
                new HashSet<>(transactionRule.getResultingTags())
            );
        }

        private void restore(TransactionRule transactionRule) {
            transactionRule.setName(name);
            transactionRule.setDescription(description);
            transactionRule.setPriority(priority);
            transactionRule.setConditionLogic(conditionLogic);
            transactionRule.setResultingDescription(resultingDescription);
            transactionRule.setActive(active);
            transactionRule.setCreatedAt(createdAt);
            transactionRule.setUpdatedAt(updatedAt);
            transactionRule.setResultingCategory(resultingCategory);
            transactionRule.setResultingFinancialSubscription(resultingFinancialSubscription);
            transactionRule.setResultingTags(new HashSet<>(resultingTags));
        }
    }
}
