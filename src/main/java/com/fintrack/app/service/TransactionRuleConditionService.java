package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleConditionMapper;
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

    public TransactionRuleConditionService(
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        TransactionRuleConditionMapper transactionRuleConditionMapper,
        CurrentUserService currentUserService,
        TransactionRuleRepository transactionRuleRepository
    ) {
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.transactionRuleConditionMapper = transactionRuleConditionMapper;
        this.currentUserService = currentUserService;
        this.transactionRuleRepository = transactionRuleRepository;
    }

    /**
     * Save a transactionRuleCondition.
     *
     * @param transactionRuleConditionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRuleConditionDTO save(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        LOG.debug("Request to save TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        transactionRuleCondition.setTransactionRule(resolveTransactionRule(transactionRuleConditionDTO.getTransactionRule(), null));
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    /**
     * Update a transactionRuleCondition.
     *
     * @param transactionRuleConditionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRuleConditionDTO update(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        LOG.debug("Request to update TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition existingTransactionRuleCondition = findAccessibleEntity(transactionRuleConditionDTO.getId()).orElseThrow();
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        transactionRuleCondition.setTransactionRule(
            resolveTransactionRule(transactionRuleConditionDTO.getTransactionRule(), existingTransactionRuleCondition.getTransactionRule())
        );
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    /**
     * Partially update a transactionRuleCondition.
     *
     * @param transactionRuleConditionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionRuleConditionDTO> partialUpdate(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        return partialUpdate(transactionRuleConditionDTO, null);
    }

    /**
     * Partially update a transactionRuleCondition, applying parent changes only when present in the patch body.
     *
     * @param transactionRuleConditionDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<TransactionRuleConditionDTO> partialUpdate(
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        LOG.debug("Request to partially update TransactionRuleCondition : {}", transactionRuleConditionDTO);

        return findAccessibleEntity(transactionRuleConditionDTO.getId())
            .map(existingTransactionRuleCondition -> {
                if (patchNode != null && patchNode.has("transactionRule") && patchNode.get("transactionRule").isNull()) {
                    throw new IllegalArgumentException("Transaction rule cannot be null");
                }
                transactionRuleConditionMapper.partialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO);
                applyTransactionRuleForPartialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO, patchNode);
                return existingTransactionRuleCondition;
            })
            .map(transactionRuleConditionRepository::save)
            .map(transactionRuleConditionMapper::toDto);
    }

    /**
     * Get all the transactionRuleConditions.
     *
     * @return the list of entities.
     */
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

    /**
     * Get all the transactionRuleConditions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionRuleConditionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository.findAllWithEagerRelationships(pageable).map(transactionRuleConditionMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    /**
     * Get one transactionRuleCondition by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionRuleConditionDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionRuleCondition : {}", id);
        return findAccessibleEntity(id).map(transactionRuleConditionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the transaction rule condition.
     *
     * @param id the id of the entity.
     * @return true when the condition exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the transactionRuleCondition by id.
     *
     * @param id the id of the entity.
     * @return true when the condition was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionRuleCondition : {}", id);
        Optional<TransactionRuleCondition> transactionRuleCondition = findAccessibleEntity(id);
        if (transactionRuleCondition.isEmpty()) {
            return false;
        }
        transactionRuleConditionRepository.deleteById(id);
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

    private void applyTransactionRuleForPartialUpdate(
        TransactionRuleCondition transactionRuleCondition,
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        if (patchNode != null) {
            if (patchNode.has("transactionRule")) {
                transactionRuleCondition.setTransactionRule(
                    resolveTransactionRule(transactionRuleConditionDTO.getTransactionRule(), transactionRuleCondition.getTransactionRule())
                );
            }
            return;
        }
        if (transactionRuleConditionDTO.getTransactionRule() != null) {
            transactionRuleCondition.setTransactionRule(
                resolveTransactionRule(transactionRuleConditionDTO.getTransactionRule(), transactionRuleCondition.getTransactionRule())
            );
        }
    }

    private TransactionRule resolveTransactionRule(TransactionRuleDTO transactionRuleDTO, TransactionRule existingParentRule) {
        if (transactionRuleDTO == null || transactionRuleDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction rule is required");
        }
        TransactionRule transactionRule = findAccessibleRule(transactionRuleDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction rule is not accessible")
        );
        if (existingParentRule != null) {
            String existingOwnerLogin = existingParentRule.getUser().getLogin();
            if (!existingOwnerLogin.equals(transactionRule.getUser().getLogin())) {
                throw new IllegalArgumentException("Transaction rule must belong to the same owner");
            }
        }
        return transactionRule;
    }

    private Optional<TransactionRule> findAccessibleRule(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithToOneRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }
}
