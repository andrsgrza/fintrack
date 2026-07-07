package com.fintrack.app.service;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.mapper.CreditAccountDetailsMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.CreditAccountDetails}.
 */
@Service
@Transactional
public class CreditAccountDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(CreditAccountDetailsService.class);

    private final CreditAccountDetailsRepository creditAccountDetailsRepository;

    private final CreditAccountDetailsMapper creditAccountDetailsMapper;

    public CreditAccountDetailsService(
        CreditAccountDetailsRepository creditAccountDetailsRepository,
        CreditAccountDetailsMapper creditAccountDetailsMapper
    ) {
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
        this.creditAccountDetailsMapper = creditAccountDetailsMapper;
    }

    /**
     * Save a creditAccountDetails.
     *
     * @param creditAccountDetailsDTO the entity to save.
     * @return the persisted entity.
     */
    public CreditAccountDetailsDTO save(CreditAccountDetailsDTO creditAccountDetailsDTO) {
        LOG.debug("Request to save CreditAccountDetails : {}", creditAccountDetailsDTO);
        CreditAccountDetails creditAccountDetails = creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO);
        creditAccountDetails = creditAccountDetailsRepository.save(creditAccountDetails);
        return creditAccountDetailsMapper.toDto(creditAccountDetails);
    }

    /**
     * Update a creditAccountDetails.
     *
     * @param creditAccountDetailsDTO the entity to save.
     * @return the persisted entity.
     */
    public CreditAccountDetailsDTO update(CreditAccountDetailsDTO creditAccountDetailsDTO) {
        LOG.debug("Request to update CreditAccountDetails : {}", creditAccountDetailsDTO);
        CreditAccountDetails creditAccountDetails = creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO);
        creditAccountDetails = creditAccountDetailsRepository.save(creditAccountDetails);
        return creditAccountDetailsMapper.toDto(creditAccountDetails);
    }

    /**
     * Partially update a creditAccountDetails.
     *
     * @param creditAccountDetailsDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<CreditAccountDetailsDTO> partialUpdate(CreditAccountDetailsDTO creditAccountDetailsDTO) {
        LOG.debug("Request to partially update CreditAccountDetails : {}", creditAccountDetailsDTO);

        return creditAccountDetailsRepository
            .findById(creditAccountDetailsDTO.getId())
            .map(existingCreditAccountDetails -> {
                creditAccountDetailsMapper.partialUpdate(existingCreditAccountDetails, creditAccountDetailsDTO);

                return existingCreditAccountDetails;
            })
            .map(creditAccountDetailsRepository::save)
            .map(creditAccountDetailsMapper::toDto);
    }

    /**
     * Get all the creditAccountDetails.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<CreditAccountDetailsDTO> findAll() {
        LOG.debug("Request to get all CreditAccountDetails");
        return creditAccountDetailsRepository
            .findAll()
            .stream()
            .map(creditAccountDetailsMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the creditAccountDetails with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<CreditAccountDetailsDTO> findAllWithEagerRelationships(Pageable pageable) {
        return creditAccountDetailsRepository.findAllWithEagerRelationships(pageable).map(creditAccountDetailsMapper::toDto);
    }

    /**
     * Get one creditAccountDetails by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<CreditAccountDetailsDTO> findOne(Long id) {
        LOG.debug("Request to get CreditAccountDetails : {}", id);
        return creditAccountDetailsRepository.findOneWithEagerRelationships(id).map(creditAccountDetailsMapper::toDto);
    }

    /**
     * Delete the creditAccountDetails by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete CreditAccountDetails : {}", id);
        creditAccountDetailsRepository.deleteById(id);
    }
}
