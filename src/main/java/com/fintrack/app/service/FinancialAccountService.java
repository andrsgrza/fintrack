package com.fintrack.app.service;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.FinancialAccount}.
 */
@Service
@Transactional
public class FinancialAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialAccountService.class);

    private final FinancialAccountRepository financialAccountRepository;

    private final FinancialAccountMapper financialAccountMapper;

    public FinancialAccountService(FinancialAccountRepository financialAccountRepository, FinancialAccountMapper financialAccountMapper) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialAccountMapper = financialAccountMapper;
    }

    /**
     * Save a financialAccount.
     *
     * @param financialAccountDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialAccountDTO save(FinancialAccountDTO financialAccountDTO) {
        LOG.debug("Request to save FinancialAccount : {}", financialAccountDTO);
        FinancialAccount financialAccount = financialAccountMapper.toEntity(financialAccountDTO);
        financialAccount = financialAccountRepository.save(financialAccount);
        return financialAccountMapper.toDto(financialAccount);
    }

    /**
     * Update a financialAccount.
     *
     * @param financialAccountDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialAccountDTO update(FinancialAccountDTO financialAccountDTO) {
        LOG.debug("Request to update FinancialAccount : {}", financialAccountDTO);
        FinancialAccount financialAccount = financialAccountMapper.toEntity(financialAccountDTO);
        financialAccount = financialAccountRepository.save(financialAccount);
        return financialAccountMapper.toDto(financialAccount);
    }

    /**
     * Partially update a financialAccount.
     *
     * @param financialAccountDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FinancialAccountDTO> partialUpdate(FinancialAccountDTO financialAccountDTO) {
        LOG.debug("Request to partially update FinancialAccount : {}", financialAccountDTO);

        return financialAccountRepository
            .findById(financialAccountDTO.getId())
            .map(existingFinancialAccount -> {
                financialAccountMapper.partialUpdate(existingFinancialAccount, financialAccountDTO);

                return existingFinancialAccount;
            })
            .map(financialAccountRepository::save)
            .map(financialAccountMapper::toDto);
    }

    /**
     * Get all the financialAccounts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<FinancialAccountDTO> findAllWithEagerRelationships(Pageable pageable) {
        return financialAccountRepository.findAllWithEagerRelationships(pageable).map(financialAccountMapper::toDto);
    }

    /**
     *  Get all the financialAccounts where CreditAccountDetails is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialAccountDTO> findAllWhereCreditAccountDetailsIsNull() {
        LOG.debug("Request to get all financialAccounts where CreditAccountDetails is null");
        return StreamSupport.stream(financialAccountRepository.findAll().spliterator(), false)
            .filter(financialAccount -> financialAccount.getCreditAccountDetails() == null)
            .map(financialAccountMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one financialAccount by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialAccountDTO> findOne(Long id) {
        LOG.debug("Request to get FinancialAccount : {}", id);
        return financialAccountRepository.findOneWithEagerRelationships(id).map(financialAccountMapper::toDto);
    }

    /**
     * Delete the financialAccount by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete FinancialAccount : {}", id);
        financialAccountRepository.deleteById(id);
    }
}
