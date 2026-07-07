package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
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

    public TransactionRuleConditionService(
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        TransactionRuleConditionMapper transactionRuleConditionMapper
    ) {
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.transactionRuleConditionMapper = transactionRuleConditionMapper;
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
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
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
        LOG.debug("Request to partially update TransactionRuleCondition : {}", transactionRuleConditionDTO);

        return transactionRuleConditionRepository
            .findById(transactionRuleConditionDTO.getId())
            .map(existingTransactionRuleCondition -> {
                transactionRuleConditionMapper.partialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO);

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
        return transactionRuleConditionRepository
            .findAll()
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
        return transactionRuleConditionRepository.findAllWithEagerRelationships(pageable).map(transactionRuleConditionMapper::toDto);
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
        return transactionRuleConditionRepository.findOneWithEagerRelationships(id).map(transactionRuleConditionMapper::toDto);
    }

    /**
     * Delete the transactionRuleCondition by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionRuleCondition : {}", id);
        transactionRuleConditionRepository.deleteById(id);
    }
}
