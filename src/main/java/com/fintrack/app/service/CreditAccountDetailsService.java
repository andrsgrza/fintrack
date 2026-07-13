package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.CreditAccountDetailsMapper;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

    private final CurrentUserService currentUserService;

    private final FinancialAccountService financialAccountService;

    public CreditAccountDetailsService(
        CreditAccountDetailsRepository creditAccountDetailsRepository,
        CreditAccountDetailsMapper creditAccountDetailsMapper,
        CurrentUserService currentUserService,
        FinancialAccountService financialAccountService
    ) {
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
        this.creditAccountDetailsMapper = creditAccountDetailsMapper;
        this.currentUserService = currentUserService;
        this.financialAccountService = financialAccountService;
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
        creditAccountDetails.setAccount(resolveAccountForCreate(creditAccountDetailsDTO.getAccount()));
        Instant now = Instant.now();
        creditAccountDetails.setCreatedAt(now);
        creditAccountDetails.setUpdatedAt(now);
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
        CreditAccountDetails existingCreditAccountDetails = findAccessibleEntity(creditAccountDetailsDTO.getId()).orElseThrow();
        rejectAccountChange(existingCreditAccountDetails, creditAccountDetailsDTO.getAccount());
        rejectTimestampChange(
            existingCreditAccountDetails.getCreatedAt(),
            creditAccountDetailsDTO.getCreatedAt(),
            "Created at cannot be changed"
        );
        rejectTimestampChange(
            existingCreditAccountDetails.getUpdatedAt(),
            creditAccountDetailsDTO.getUpdatedAt(),
            "Updated at cannot be changed"
        );
        CreditAccountDetails creditAccountDetails = creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO);
        creditAccountDetails.setAccount(existingCreditAccountDetails.getAccount());
        creditAccountDetails.setCreatedAt(existingCreditAccountDetails.getCreatedAt());
        creditAccountDetails.setUpdatedAt(Instant.now());
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
        return partialUpdate(creditAccountDetailsDTO, null);
    }

    /**
     * Partially update a creditAccountDetails, applying account changes only when present in the patch body.
     *
     * @param creditAccountDetailsDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<CreditAccountDetailsDTO> partialUpdate(CreditAccountDetailsDTO creditAccountDetailsDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update CreditAccountDetails : {}", creditAccountDetailsDTO);

        return findAccessibleEntity(creditAccountDetailsDTO.getId())
            .map(existingCreditAccountDetails -> {
                rejectTimestampChanges(existingCreditAccountDetails, creditAccountDetailsDTO, patchNode);
                if (patchNode != null && patchNode.has("account") && patchNode.get("account").isNull()) {
                    throw new IllegalArgumentException("Account cannot be null");
                }
                if (patchNode != null && patchNode.has("account")) {
                    rejectAccountChange(existingCreditAccountDetails, creditAccountDetailsDTO.getAccount());
                }
                creditAccountDetailsMapper.partialUpdate(existingCreditAccountDetails, creditAccountDetailsDTO);
                existingCreditAccountDetails.setCreatedAt(existingCreditAccountDetails.getCreatedAt());
                existingCreditAccountDetails.setUpdatedAt(Instant.now());
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
        if (currentUserService.isAdmin()) {
            return creditAccountDetailsRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(creditAccountDetailsMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return creditAccountDetailsRepository
            .findAllWithEagerRelationshipsByAccountUserLogin(currentUserService.getCurrentUserLogin())
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
        if (currentUserService.isAdmin()) {
            return creditAccountDetailsRepository.findAllWithEagerRelationships(pageable).map(creditAccountDetailsMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
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
        return findAccessibleEntity(id).map(creditAccountDetailsMapper::toDto);
    }

    /**
     * Returns whether the current user can access the credit account details.
     *
     * @param id the id of the entity.
     * @return true when the details exist and are visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the creditAccountDetails by id.
     *
     * @param id the id of the entity.
     * @return true when the details were deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete CreditAccountDetails : {}", id);
        Optional<CreditAccountDetails> creditAccountDetails = findAccessibleEntity(id);
        if (creditAccountDetails.isEmpty()) {
            return false;
        }
        throw new IllegalArgumentException("Credit account details cannot be deleted directly");
    }

    private Optional<CreditAccountDetails> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return creditAccountDetailsRepository.findOneWithEagerRelationships(id);
        }
        return creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private FinancialAccount resolveAccountForCreate(FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            throw new IllegalArgumentException("Account is required");
        }
        FinancialAccount account = financialAccountService
            .findAccessibleAccountEntity(accountDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Account is not accessible"));
        validateCreditCardAccount(account);
        if (creditAccountDetailsRepository.existsByAccountId(account.getId())) {
            throw new IllegalArgumentException("Account already has credit account details");
        }
        return account;
    }

    private void rejectAccountChange(CreditAccountDetails existingCreditAccountDetails, FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            return;
        }
        if (!accountDTO.getId().equals(existingCreditAccountDetails.getAccount().getId())) {
            throw new IllegalArgumentException("Account cannot be changed");
        }
    }

    private void rejectTimestampChanges(
        CreditAccountDetails existingCreditAccountDetails,
        CreditAccountDetailsDTO creditAccountDetailsDTO,
        JsonNode patchNode
    ) {
        if (patchNode == null) {
            if (creditAccountDetailsDTO.getCreatedAt() != null) {
                rejectTimestampChange(
                    existingCreditAccountDetails.getCreatedAt(),
                    creditAccountDetailsDTO.getCreatedAt(),
                    "Created at cannot be changed"
                );
            }
            if (creditAccountDetailsDTO.getUpdatedAt() != null) {
                rejectTimestampChange(
                    existingCreditAccountDetails.getUpdatedAt(),
                    creditAccountDetailsDTO.getUpdatedAt(),
                    "Updated at cannot be changed"
                );
            }
            return;
        }
        if (patchNode.has("createdAt")) {
            rejectTimestampChange(
                existingCreditAccountDetails.getCreatedAt(),
                creditAccountDetailsDTO.getCreatedAt(),
                "Created at cannot be changed"
            );
        }
        if (patchNode.has("updatedAt")) {
            rejectTimestampChange(
                existingCreditAccountDetails.getUpdatedAt(),
                creditAccountDetailsDTO.getUpdatedAt(),
                "Updated at cannot be changed"
            );
        }
    }

    private void rejectTimestampChange(Instant existingTimestamp, Instant requestedTimestamp, String message) {
        if (requestedTimestamp == null || !Objects.equals(existingTimestamp, requestedTimestamp)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateCreditCardAccount(FinancialAccount account) {
        if (account.getAccountType() != AccountType.CREDIT_CARD) {
            throw new IllegalArgumentException("Account must be a credit card account");
        }
    }
}
