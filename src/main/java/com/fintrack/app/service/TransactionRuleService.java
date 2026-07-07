package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import java.util.Optional;
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

    public TransactionRuleService(TransactionRuleRepository transactionRuleRepository, TransactionRuleMapper transactionRuleMapper) {
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleMapper = transactionRuleMapper;
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
        TransactionRule transactionRule = transactionRuleMapper.toEntity(transactionRuleDTO);
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
        LOG.debug("Request to partially update TransactionRule : {}", transactionRuleDTO);

        return transactionRuleRepository
            .findById(transactionRuleDTO.getId())
            .map(existingTransactionRule -> {
                transactionRuleMapper.partialUpdate(existingTransactionRule, transactionRuleDTO);

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
        return transactionRuleRepository.findAllWithEagerRelationships(pageable).map(transactionRuleMapper::toDto);
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
        return transactionRuleRepository.findOneWithEagerRelationships(id).map(transactionRuleMapper::toDto);
    }

    /**
     * Delete the transactionRule by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionRule : {}", id);
        transactionRuleRepository.deleteById(id);
    }
}
