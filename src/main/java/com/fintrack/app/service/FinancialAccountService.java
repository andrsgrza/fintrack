package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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

    private final TransactionIngestionService transactionIngestionService;

    private final FinancialTransactionService financialTransactionService;

    private final BudgetRepository budgetRepository;

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final CreditAccountDetailsRepository creditAccountDetailsRepository;

    private final FinancialTransactionRepository financialTransactionRepository;

    public FinancialAccountService(
        FinancialAccountRepository financialAccountRepository,
        FinancialAccountMapper financialAccountMapper,
        CurrentUserService currentUserService,
        @Lazy TransactionIngestionService transactionIngestionService,
        @Lazy FinancialTransactionService financialTransactionService,
        BudgetRepository budgetRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        CreditAccountDetailsRepository creditAccountDetailsRepository,
        FinancialTransactionRepository financialTransactionRepository
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialAccountMapper = financialAccountMapper;
        this.currentUserService = currentUserService;
        this.transactionIngestionService = transactionIngestionService;
        this.financialTransactionService = financialTransactionService;
        this.budgetRepository = budgetRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
        this.financialTransactionRepository = financialTransactionRepository;
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
        Instant now = Instant.now();
        financialAccount.setCreatedAt(now);
        financialAccount.setUpdatedAt(now);
        validateInitialBalanceDateFloor(financialAccount);
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
        rejectCurrencyChange(existingFinancialAccount, financialAccountDTO.getCurrency());
        rejectAccountTypeChange(existingFinancialAccount, financialAccountDTO.getAccountType());
        rejectTimestampChange(existingFinancialAccount.getCreatedAt(), financialAccountDTO.getCreatedAt(), "Created at cannot be changed");
        rejectTimestampChange(existingFinancialAccount.getUpdatedAt(), financialAccountDTO.getUpdatedAt(), "Updated at cannot be changed");
        FinancialAccount financialAccount = financialAccountMapper.toEntity(financialAccountDTO);
        financialAccount.setUser(existingFinancialAccount.getUser());
        financialAccount.setCurrency(existingFinancialAccount.getCurrency());
        financialAccount.setAccountType(existingFinancialAccount.getAccountType());
        financialAccount.setCreatedAt(existingFinancialAccount.getCreatedAt());
        financialAccount.setUpdatedAt(Instant.now());
        validateInitialBalanceDateFloor(financialAccount);
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
        return partialUpdate(financialAccountDTO, null);
    }

    /**
     * Partially update a financialAccount, applying immutable field checks only when present in the patch body.
     *
     * @param financialAccountDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<FinancialAccountDTO> partialUpdate(FinancialAccountDTO financialAccountDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update FinancialAccount : {}", financialAccountDTO);

        return findAccessibleEntity(financialAccountDTO.getId())
            .map(existingFinancialAccount -> {
                rejectImmutableFieldChanges(existingFinancialAccount, financialAccountDTO, patchNode);
                financialAccountMapper.partialUpdate(existingFinancialAccount, financialAccountDTO);
                existingFinancialAccount.setUpdatedAt(Instant.now());
                validateInitialBalanceDateFloor(existingFinancialAccount);
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
        FinancialAccount account = financialAccount.get();
        transactionIngestionService.deleteAllForAccount(account);
        financialTransactionService.deleteAllForAccount(account);
        budgetRepository.deleteAccountLinksByAccountId(id);
        financialSubscriptionRepository.clearAccountByAccountId(id);
        creditAccountDetailsRepository.deleteByAccountId(id);
        financialAccountRepository.deleteById(id);
        return true;
    }

    private Optional<FinancialAccount> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return financialAccountRepository.findOneWithEagerRelationships(id);
        }
        return financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void rejectImmutableFieldChanges(FinancialAccount existing, FinancialAccountDTO financialAccountDTO, JsonNode patchNode) {
        if (patchNode == null) {
            if (financialAccountDTO.getCurrency() != null) {
                rejectCurrencyChange(existing, financialAccountDTO.getCurrency());
            }
            if (financialAccountDTO.getAccountType() != null) {
                rejectAccountTypeChange(existing, financialAccountDTO.getAccountType());
            }
            if (financialAccountDTO.getCreatedAt() != null) {
                rejectTimestampChange(existing.getCreatedAt(), financialAccountDTO.getCreatedAt(), "Created at cannot be changed");
            }
            if (financialAccountDTO.getUpdatedAt() != null) {
                rejectTimestampChange(existing.getUpdatedAt(), financialAccountDTO.getUpdatedAt(), "Updated at cannot be changed");
            }
            return;
        }
        if (patchNode.has("currency")) {
            rejectCurrencyChange(existing, financialAccountDTO.getCurrency());
        }
        if (patchNode.has("accountType")) {
            rejectAccountTypeChange(existing, financialAccountDTO.getAccountType());
        }
        if (patchNode.has("createdAt")) {
            rejectTimestampChange(existing.getCreatedAt(), financialAccountDTO.getCreatedAt(), "Created at cannot be changed");
        }
        if (patchNode.has("updatedAt")) {
            rejectTimestampChange(existing.getUpdatedAt(), financialAccountDTO.getUpdatedAt(), "Updated at cannot be changed");
        }
    }

    private void rejectCurrencyChange(FinancialAccount existing, CurrencyCode currency) {
        if (currency == null || !currency.equals(existing.getCurrency())) {
            throw new IllegalArgumentException("Currency cannot be changed");
        }
    }

    private void rejectAccountTypeChange(FinancialAccount existing, AccountType accountType) {
        if (accountType == null || !accountType.equals(existing.getAccountType())) {
            throw new IllegalArgumentException("Account type cannot be changed");
        }
    }

    private void rejectTimestampChange(Instant existingTimestamp, Instant requestedTimestamp, String message) {
        if (requestedTimestamp == null || !Objects.equals(existingTimestamp, requestedTimestamp)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateInitialBalanceDateFloor(FinancialAccount financialAccount) {
        if (financialAccount.getId() == null || financialAccount.getInitialBalanceDate() == null) {
            return;
        }
        Optional<LocalDate> earliestTransactionDate = financialTransactionRepository.findEarliestTransactionDateByAccountId(
            financialAccount.getId()
        );
        if (
            earliestTransactionDate.isPresent() && financialAccount.getInitialBalanceDate().isAfter(earliestTransactionDate.orElseThrow())
        ) {
            throw new IllegalArgumentException("Initial balance date cannot be after the earliest transaction date");
        }
    }
}
