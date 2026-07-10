package com.fintrack.app.service;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    private final CurrentUserService currentUserService;

    public FinancialAccountService(
        FinancialAccountRepository financialAccountRepository,
        FinancialAccountMapper financialAccountMapper,
        CurrentUserService currentUserService
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialAccountMapper = financialAccountMapper;
        this.currentUserService = currentUserService;
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
        financialAccount.setUser(currentUserService.getCurrentUser());
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
        FinancialAccount existingFinancialAccount = findAccessibleEntity(financialAccountDTO.getId()).orElseThrow();
        FinancialAccount financialAccount = financialAccountMapper.toEntity(financialAccountDTO);
        financialAccount.setUser(existingFinancialAccount.getUser());
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

        return findAccessibleEntity(financialAccountDTO.getId())
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
        if (currentUserService.isAdmin()) {
            return financialAccountRepository.findAllWithEagerRelationships(pageable).map(financialAccountMapper::toDto);
        }
        return financialAccountRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(financialAccountMapper::toDto);
    }

    /**
     *  Get all the financialAccounts where CreditAccountDetails is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialAccountDTO> findAllWhereCreditAccountDetailsIsNull() {
        LOG.debug("Request to get all financialAccounts where CreditAccountDetails is null");
        Stream<FinancialAccount> accountStream;
        if (currentUserService.isAdmin()) {
            accountStream = StreamSupport.stream(financialAccountRepository.findAll().spliterator(), false);
        } else {
            accountStream = financialAccountRepository
                .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin())
                .stream();
        }
        return accountStream
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
        return findAccessibleEntity(id).map(financialAccountMapper::toDto);
    }

    /**
     * Returns whether the current user can access the financial account.
     *
     * @param id the id of the entity.
     * @return true when the account exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Returns the financial account when it is accessible to the current user.
     *
     * @param id the id of the entity.
     * @return the account entity when accessible.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialAccount> findAccessibleAccountEntity(Long id) {
        return findAccessibleEntity(id);
    }

    /**
     * Delete the financialAccount by id.
     *
     * @param id the id of the entity.
     * @return true when the account was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete FinancialAccount : {}", id);
        Optional<FinancialAccount> financialAccount = findAccessibleEntity(id);
        if (financialAccount.isEmpty()) {
            return false;
        }
        financialAccountRepository.deleteById(id);
        return true;
    }

    private Optional<FinancialAccount> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return financialAccountRepository.findOneWithEagerRelationships(id);
        }
        return financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }
}
