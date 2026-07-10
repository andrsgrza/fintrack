package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

    public TransactionRuleService(
        TransactionRuleRepository transactionRuleRepository,
        TransactionRuleMapper transactionRuleMapper,
        CurrentUserService currentUserService,
        CategoryRepository categoryRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        TagRepository tagRepository
    ) {
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleMapper = transactionRuleMapper;
        this.currentUserService = currentUserService;
        this.categoryRepository = categoryRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.tagRepository = tagRepository;
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
        LOG.debug("Request to update TransactionRule : {}", transactionRuleDTO);
        TransactionRule existingTransactionRule = findAccessibleEntity(transactionRuleDTO.getId()).orElseThrow();
        TransactionRule transactionRule = transactionRuleMapper.toEntity(transactionRuleDTO);
        transactionRule.setUser(existingTransactionRule.getUser());
        applyRelationships(transactionRule, transactionRuleDTO, existingTransactionRule.getUser().getLogin());
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
                transactionRuleMapper.partialUpdate(existingTransactionRule, transactionRuleDTO);
                applyRelationshipsForPartialUpdate(
                    existingTransactionRule,
                    transactionRuleDTO,
                    patchNode,
                    existingTransactionRule.getUser().getLogin()
                );
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
        transactionRuleRepository.deleteById(id);
        return true;
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
                transactionRule.setResultingCategory(resolveOptionalCategory(transactionRuleDTO.getResultingCategory(), ownerLogin));
            }
            if (patchNode.has("resultingFinancialSubscription")) {
                transactionRule.setResultingFinancialSubscription(
                    resolveOptionalSubscription(transactionRuleDTO.getResultingFinancialSubscription(), ownerLogin)
                );
            }
            if (patchNode.has("resultingTags")) {
                transactionRule.setResultingTags(resolveTags(transactionRuleDTO.getResultingTags(), ownerLogin));
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
        if (categoryDTO == null || categoryDTO.getId() == null) {
            return null;
        }
        return categoryRepository
            .findOneByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private FinancialSubscription resolveOptionalSubscription(FinancialSubscriptionDTO subscriptionDTO, String ownerLogin) {
        if (subscriptionDTO == null || subscriptionDTO.getId() == null) {
            return null;
        }
        return financialSubscriptionRepository
            .findOneByIdAndUserLogin(subscriptionDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial subscription is not accessible"));
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs, String ownerLogin) {
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO.getId() == null) {
                continue;
            }
            Tag tag = tagRepository
                .findOneByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }
}
