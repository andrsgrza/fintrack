package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleConditionMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionRuleCondition}.
 */
@Service
@Transactional
public class TransactionRuleConditionService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleConditionService.class);

    private final TransactionRuleConditionRepository transactionRuleConditionRepository;

    private final TransactionRuleConditionMapper transactionRuleConditionMapper;

    private final CurrentUserService currentUserService;

    private final TransactionRuleRepository transactionRuleRepository;

    private final TransactionRuleConditionValidator transactionRuleConditionValidator;

    private final TransactionRuleFlowCategoryCompatibilityValidator transactionRuleFlowCategoryCompatibilityValidator;

    public TransactionRuleConditionService(
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        TransactionRuleConditionMapper transactionRuleConditionMapper,
        CurrentUserService currentUserService,
        TransactionRuleRepository transactionRuleRepository,
        TransactionRuleConditionValidator transactionRuleConditionValidator,
        TransactionRuleFlowCategoryCompatibilityValidator transactionRuleFlowCategoryCompatibilityValidator
    ) {
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.transactionRuleConditionMapper = transactionRuleConditionMapper;
        this.currentUserService = currentUserService;
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleConditionValidator = transactionRuleConditionValidator;
        this.transactionRuleFlowCategoryCompatibilityValidator = transactionRuleFlowCategoryCompatibilityValidator;
    }

    public TransactionRuleConditionDTO save(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        LOG.debug("Request to save TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        TransactionRule transactionRule = resolveTransactionRuleForCreate(transactionRuleConditionDTO.getTransactionRule());
        transactionRuleCondition.setTransactionRule(transactionRule);
        transactionRuleCondition.setPosition(nextPositionForRule(transactionRule.getId()));
        transactionRuleConditionValidator.validateCondition(transactionRuleCondition, null);
        validateActiveParentAfterConditionChange(transactionRule, transactionRuleCondition);
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    public TransactionRuleConditionDTO update(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        return update(transactionRuleConditionDTO, null);
    }

    public TransactionRuleConditionDTO update(TransactionRuleConditionDTO transactionRuleConditionDTO, JsonNode requestNode) {
        LOG.debug("Request to update TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition existingTransactionRuleCondition = findAccessibleEntity(transactionRuleConditionDTO.getId()).orElseThrow();
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        enforceImmutableTransactionRule(existingTransactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), true);
        enforceImmutablePosition(existingTransactionRuleCondition, transactionRuleConditionDTO.getPosition(), requestNode);
        transactionRuleCondition.setTransactionRule(existingTransactionRuleCondition.getTransactionRule());
        transactionRuleCondition.setPosition(existingTransactionRuleCondition.getPosition());
        transactionRuleConditionValidator.validateCondition(
            transactionRuleCondition,
            existingTransactionRuleCondition.getId(),
            existingTransactionRuleCondition
        );
        validateActiveParentAfterConditionChange(
            existingTransactionRuleCondition.getTransactionRule(),
            transactionRuleCondition,
            existingTransactionRuleCondition.getId()
        );
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    public Optional<TransactionRuleConditionDTO> partialUpdate(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        return partialUpdate(transactionRuleConditionDTO, null);
    }

    public Optional<TransactionRuleConditionDTO> partialUpdate(
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        LOG.debug("Request to partially update TransactionRuleCondition : {}", transactionRuleConditionDTO);

        return findAccessibleEntity(transactionRuleConditionDTO.getId())
            .map(existingTransactionRuleCondition -> {
                TransactionRuleCondition previousCondition = conditionSnapshot(existingTransactionRuleCondition);
                if (patchNode != null && patchNode.has("transactionRule") && patchNode.get("transactionRule").isNull()) {
                    throw new IllegalArgumentException("Transaction rule cannot be null");
                }
                rejectNullRequiredPatchFields(patchNode);
                enforceImmutablePosition(existingTransactionRuleCondition, transactionRuleConditionDTO.getPosition(), patchNode);
                Integer preservedPosition = existingTransactionRuleCondition.getPosition();
                transactionRuleConditionMapper.partialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO);
                existingTransactionRuleCondition.setPosition(preservedPosition);
                applyNullableFieldsForPartialUpdate(existingTransactionRuleCondition, patchNode);
                applyTransactionRuleForPartialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO, patchNode);
                transactionRuleConditionValidator.validateCondition(
                    existingTransactionRuleCondition,
                    existingTransactionRuleCondition.getId(),
                    previousCondition
                );
                validateActiveParentAfterConditionChange(
                    existingTransactionRuleCondition.getTransactionRule(),
                    existingTransactionRuleCondition,
                    existingTransactionRuleCondition.getId()
                );
                return existingTransactionRuleCondition;
            })
            .map(transactionRuleConditionRepository::save)
            .map(transactionRuleConditionMapper::toDto);
    }

    private void rejectNullRequiredPatchFields(JsonNode patchNode) {
        if (patchNode == null) {
            return;
        }
        rejectNullPatchField(patchNode, "field");
        rejectNullPatchField(patchNode, "operator");
        rejectNullPatchField(patchNode, "value");
        rejectNullPatchField(patchNode, "caseSensitive");
        rejectNullPatchField(patchNode, "position");
    }

    private void rejectNullPatchField(JsonNode patchNode, String fieldName) {
        if (patchNode.has(fieldName) && patchNode.get(fieldName).isNull()) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private void applyNullableFieldsForPartialUpdate(TransactionRuleCondition condition, JsonNode patchNode) {
        if (patchNode != null && patchNode.has("secondValue") && patchNode.get("secondValue").isNull()) {
            condition.setSecondValue(null);
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionRuleConditionDTO> findAll() {
        LOG.debug("Request to get all TransactionRuleConditions");
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(transactionRuleConditionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return transactionRuleConditionRepository
            .findAllWithEagerRelationshipsByRuleUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(transactionRuleConditionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    public Page<TransactionRuleConditionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository.findAllWithEagerRelationships(pageable).map(transactionRuleConditionMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    @Transactional(readOnly = true)
    public Optional<TransactionRuleConditionDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionRuleCondition : {}", id);
        return findAccessibleEntity(id).map(transactionRuleConditionMapper::toDto);
    }

    /**
     * Get all conditions for an accessible transaction rule.
     *
     * @param transactionRuleId the id of the parent transaction rule.
     * @return conditions ordered by position, then id, or empty when the parent rule is inaccessible.
     */
    @Transactional(readOnly = true)
    public Optional<List<TransactionRuleConditionDTO>> findByTransactionRuleId(Long transactionRuleId) {
        LOG.debug("Request to get TransactionRuleConditions for TransactionRule : {}", transactionRuleId);
        if (findAccessibleTransactionRule(transactionRuleId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
            transactionRuleConditionRepository
                .findByTransactionRuleIdOrderByPositionAscIdAsc(transactionRuleId)
                .stream()
                .map(transactionRuleConditionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new))
        );
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionRuleCondition : {}", id);
        Optional<TransactionRuleCondition> transactionRuleCondition = findAccessibleEntity(id);
        if (transactionRuleCondition.isEmpty()) {
            return false;
        }
        TransactionRuleCondition condition = transactionRuleCondition.orElseThrow();
        TransactionRule parentRule = condition.getTransactionRule();
        Long parentRuleId = parentRule.getId();
        long conditionCount = transactionRuleConditionRepository.countByTransactionRuleId(parentRuleId);

        transactionRuleConditionRepository.deleteById(id);

        if (conditionCount == 1) {
            parentRule.setActive(false);
            parentRule.setUpdatedAt(Instant.now());
            transactionRuleRepository.save(parentRule);
        }
        return true;
    }

    private Optional<TransactionRuleCondition> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository.findOneWithEagerRelationships(id);
        }
        return transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private Optional<TransactionRule> findAccessibleTransactionRule(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithEagerRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyTransactionRuleForPartialUpdate(
        TransactionRuleCondition transactionRuleCondition,
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        if (patchNode != null) {
            if (patchNode.has("transactionRule")) {
                enforceImmutableTransactionRule(transactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), false);
            }
            return;
        }
        if (transactionRuleConditionDTO.getTransactionRule() != null) {
            enforceImmutableTransactionRule(transactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), true);
        }
    }

    private TransactionRule resolveTransactionRuleForCreate(TransactionRuleDTO transactionRuleDTO) {
        if (transactionRuleDTO == null || transactionRuleDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction rule is required");
        }
        return findAccessibleRule(transactionRuleDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction rule is not accessible")
        );
    }

    private Integer nextPositionForRule(Long transactionRuleId) {
        return transactionRuleConditionRepository
            .findMaxPositionByTransactionRuleId(transactionRuleId)
            .map(position -> position + 1)
            .orElse(0);
    }

    private void enforceImmutableTransactionRule(
        TransactionRuleCondition existingCondition,
        TransactionRuleDTO requestedRuleDTO,
        boolean requiredOnPut
    ) {
        TransactionRule existingRule = existingCondition.getTransactionRule();
        if (requestedRuleDTO == null || requestedRuleDTO.getId() == null) {
            if (requiredOnPut) {
                throw new IllegalArgumentException("Transaction rule is required");
            }
            return;
        }
        if (!requestedRuleDTO.getId().equals(existingRule.getId())) {
            throw new IllegalArgumentException("Transaction rule cannot be changed");
        }
    }

    private void enforceImmutablePosition(TransactionRuleCondition existingCondition, Integer requestedPosition, JsonNode requestNode) {
        if (requestNode != null && !requestNode.has("position")) {
            return;
        }
        if (requestNode != null && requestNode.has("position") && requestNode.get("position").isNull()) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (requestedPosition == null) {
            return;
        }
        if (!requestedPosition.equals(existingCondition.getPosition())) {
            throw new IllegalArgumentException("Position cannot be changed");
        }
    }

    private Optional<TransactionRule> findAccessibleRule(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithToOneRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private TransactionRuleCondition conditionSnapshot(TransactionRuleCondition source) {
        TransactionRuleCondition snapshot = new TransactionRuleCondition();
        snapshot.setId(source.getId());
        snapshot.setField(source.getField());
        snapshot.setOperator(source.getOperator());
        snapshot.setValue(source.getValue());
        snapshot.setSecondValue(source.getSecondValue());
        snapshot.setCaseSensitive(source.getCaseSensitive());
        snapshot.setPosition(source.getPosition());
        snapshot.setTransactionRule(source.getTransactionRule());
        return snapshot;
    }

    private void validateActiveParentAfterConditionChange(TransactionRule parentRule, TransactionRuleCondition changedCondition) {
        validateActiveParentAfterConditionChange(parentRule, changedCondition, null);
    }

    private void validateActiveParentAfterConditionChange(
        TransactionRule parentRule,
        TransactionRuleCondition changedCondition,
        Long changedConditionId
    ) {
        if (!Boolean.TRUE.equals(parentRule.getActive())) {
            return;
        }
        List<TransactionRuleCondition> finalConditions = new ArrayList<>(
            transactionRuleConditionRepository.findByTransactionRuleIdOrderByPositionAscIdAsc(parentRule.getId())
        );
        if (changedConditionId == null) {
            finalConditions.add(changedCondition);
        } else {
            finalConditions = finalConditions
                .stream()
                .map(condition -> changedConditionId.equals(condition.getId()) ? changedCondition : condition)
                .collect(Collectors.toCollection(ArrayList::new));
        }
        transactionRuleFlowCategoryCompatibilityValidator.validate(parentRule, finalConditions);
    }
}
