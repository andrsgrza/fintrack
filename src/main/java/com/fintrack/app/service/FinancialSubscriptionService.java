package com.fintrack.app.service;

import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.FinancialSubscription}.
 */
@Service
@Transactional
public class FinancialSubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialSubscriptionService.class);

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final FinancialSubscriptionMapper financialSubscriptionMapper;

    public FinancialSubscriptionService(
        FinancialSubscriptionRepository financialSubscriptionRepository,
        FinancialSubscriptionMapper financialSubscriptionMapper
    ) {
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.financialSubscriptionMapper = financialSubscriptionMapper;
    }

    /**
     * Save a financialSubscription.
     *
     * @param financialSubscriptionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialSubscriptionDTO save(FinancialSubscriptionDTO financialSubscriptionDTO) {
        LOG.debug("Request to save FinancialSubscription : {}", financialSubscriptionDTO);
        FinancialSubscription financialSubscription = financialSubscriptionMapper.toEntity(financialSubscriptionDTO);
        financialSubscription = financialSubscriptionRepository.save(financialSubscription);
        return financialSubscriptionMapper.toDto(financialSubscription);
    }

    /**
     * Update a financialSubscription.
     *
     * @param financialSubscriptionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialSubscriptionDTO update(FinancialSubscriptionDTO financialSubscriptionDTO) {
        LOG.debug("Request to update FinancialSubscription : {}", financialSubscriptionDTO);
        FinancialSubscription financialSubscription = financialSubscriptionMapper.toEntity(financialSubscriptionDTO);
        financialSubscription = financialSubscriptionRepository.save(financialSubscription);
        return financialSubscriptionMapper.toDto(financialSubscription);
    }

    /**
     * Partially update a financialSubscription.
     *
     * @param financialSubscriptionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FinancialSubscriptionDTO> partialUpdate(FinancialSubscriptionDTO financialSubscriptionDTO) {
        LOG.debug("Request to partially update FinancialSubscription : {}", financialSubscriptionDTO);

        return financialSubscriptionRepository
            .findById(financialSubscriptionDTO.getId())
            .map(existingFinancialSubscription -> {
                financialSubscriptionMapper.partialUpdate(existingFinancialSubscription, financialSubscriptionDTO);

                return existingFinancialSubscription;
            })
            .map(financialSubscriptionRepository::save)
            .map(financialSubscriptionMapper::toDto);
    }

    /**
     * Get all the financialSubscriptions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<FinancialSubscriptionDTO> findAllWithEagerRelationships(Pageable pageable) {
        return financialSubscriptionRepository.findAllWithEagerRelationships(pageable).map(financialSubscriptionMapper::toDto);
    }

    /**
     * Get one financialSubscription by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialSubscriptionDTO> findOne(Long id) {
        LOG.debug("Request to get FinancialSubscription : {}", id);
        return financialSubscriptionRepository.findOneWithEagerRelationships(id).map(financialSubscriptionMapper::toDto);
    }

    /**
     * Delete the financialSubscription by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete FinancialSubscription : {}", id);
        financialSubscriptionRepository.deleteById(id);
    }
}
