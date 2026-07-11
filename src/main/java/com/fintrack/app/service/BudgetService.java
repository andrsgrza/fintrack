package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.Budget}.
 */
@Service
@Transactional
public class BudgetService {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;

    private final BudgetMapper budgetMapper;

    private final CurrentUserService currentUserService;

    private final FinancialAccountRepository financialAccountRepository;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    public BudgetService(
        BudgetRepository budgetRepository,
        BudgetMapper budgetMapper,
        CurrentUserService currentUserService,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
        this.currentUserService = currentUserService;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    /**
     * Save a budget.
     *
     * @param budgetDTO the entity to save.
     * @return the persisted entity.
     */
    public BudgetDTO save(BudgetDTO budgetDTO) {
        LOG.debug("Request to save Budget : {}", budgetDTO);
        Budget budget = budgetMapper.toEntity(budgetDTO);
        budget.setUser(currentUserService.getCurrentUser());
        applyRelationships(budget, budgetDTO, budget.getUser().getLogin());
        validateBudget(budget);
        budget = budgetRepository.save(budget);
        return budgetMapper.toDto(budget);
    }

    /**
     * Update a budget.
     *
     * @param budgetDTO the entity to save.
     * @return the persisted entity.
     */
    public BudgetDTO update(BudgetDTO budgetDTO) {
        LOG.debug("Request to update Budget : {}", budgetDTO);
        Budget existingBudget = findAccessibleEntity(budgetDTO.getId()).orElseThrow();
        Budget budget = budgetMapper.toEntity(budgetDTO);
        budget.setUser(existingBudget.getUser());
        applyRelationships(budget, budgetDTO, existingBudget.getUser().getLogin());
        validateBudget(budget);
        budget = budgetRepository.save(budget);
        return budgetMapper.toDto(budget);
    }

    /**
     * Partially update a budget.
     *
     * @param budgetDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<BudgetDTO> partialUpdate(BudgetDTO budgetDTO) {
        return partialUpdate(budgetDTO, null);
    }

    /**
     * Partially update a budget, applying M2M link changes only for JSON fields present in the patch body.
     *
     * @param budgetDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<BudgetDTO> partialUpdate(BudgetDTO budgetDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update Budget : {}", budgetDTO);

        return findAccessibleEntity(budgetDTO.getId())
            .map(existingBudget -> {
                budgetMapper.partialUpdate(existingBudget, budgetDTO);
                applyRelationshipsForPartialUpdate(existingBudget, budgetDTO, patchNode, existingBudget.getUser().getLogin());
                validateBudget(existingBudget);
                return existingBudget;
            })
            .map(budgetRepository::save)
            .map(budgetMapper::toDto);
    }

    /**
     * Get all the budgets with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<BudgetDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return budgetRepository.findAllWithEagerRelationships(pageable).map(budgetMapper::toDto);
        }
        return budgetRepository
            .findAllWithEagerRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(budgetMapper::toDto);
    }

    /**
     * Get one budget by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<BudgetDTO> findOne(Long id) {
        LOG.debug("Request to get Budget : {}", id);
        return findAccessibleEntity(id).map(budgetMapper::toDto);
    }

    /**
     * Returns whether the current user can access the budget.
     *
     * @param id the id of the entity.
     * @return true when the budget exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the budget by id.
     *
     * @param id the id of the entity.
     * @return true when the budget was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete Budget : {}", id);
        Optional<Budget> budget = findAccessibleEntity(id);
        if (budget.isEmpty()) {
            return false;
        }
        Long budgetId = budget.orElseThrow().getId();
        unlinkBudgetFromAllRelationships(budgetId);
        budgetRepository.deleteById(budgetId);
        return true;
    }

    private void unlinkBudgetFromAllRelationships(Long budgetId) {
        budgetRepository.deleteAccountLinksByBudgetId(budgetId);
        budgetRepository.deleteCategoryLinksByBudgetId(budgetId);
        budgetRepository.deleteTagLinksByBudgetId(budgetId);
    }

    private Optional<Budget> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return budgetRepository.findOneWithEagerRelationships(id);
        }
        return budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRelationships(Budget budget, BudgetDTO budgetDTO, String ownerLogin) {
        budget.setAccounts(resolveAccounts(budgetDTO.getAccounts(), ownerLogin));
        budget.setCategories(resolveCategories(budgetDTO.getCategories(), ownerLogin));
        budget.setTags(resolveTags(budgetDTO.getTags(), ownerLogin));
    }

    private void applyRelationshipsForPartialUpdate(Budget budget, BudgetDTO budgetDTO, JsonNode patchNode, String ownerLogin) {
        if (patchNode != null) {
            if (patchNode.has("accounts")) {
                budget.setAccounts(resolveAccounts(budgetDTO.getAccounts(), ownerLogin));
            }
            if (patchNode.has("categories")) {
                budget.setCategories(resolveCategories(budgetDTO.getCategories(), ownerLogin));
            }
            if (patchNode.has("tags")) {
                budget.setTags(resolveTags(budgetDTO.getTags(), ownerLogin));
            }
            return;
        }
        if (budgetDTO.getAccounts() != null) {
            budget.setAccounts(resolveAccounts(budgetDTO.getAccounts(), ownerLogin));
        }
        if (budgetDTO.getCategories() != null) {
            budget.setCategories(resolveCategories(budgetDTO.getCategories(), ownerLogin));
        }
        if (budgetDTO.getTags() != null) {
            budget.setTags(resolveTags(budgetDTO.getTags(), ownerLogin));
        }
    }

    private Set<FinancialAccount> resolveAccounts(Set<FinancialAccountDTO> accountDTOs, String ownerLogin) {
        if (accountDTOs == null || accountDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<FinancialAccount> accounts = new HashSet<>();
        for (FinancialAccountDTO accountDTO : accountDTOs) {
            if (accountDTO.getId() == null) {
                continue;
            }
            FinancialAccount account = financialAccountRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(accountDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
            accounts.add(account);
        }
        return accounts;
    }

    private Set<Category> resolveCategories(Set<CategoryDTO> categoryDTOs, String ownerLogin) {
        if (categoryDTOs == null || categoryDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Category> categories = new HashSet<>();
        for (CategoryDTO categoryDTO : categoryDTOs) {
            if (categoryDTO.getId() == null) {
                continue;
            }
            Category category = categoryRepository
                .findOneByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
            validateCategoryTypeForExpenseBudget(category);
            categories.add(category);
        }
        return categories;
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs, String ownerLogin) {
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO.getId() == null) {
                continue;
            }
            Tag tag = tagRepository
                .findOneByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }

    private void validateBudget(Budget budget) {
        validateAmountPositive(budget.getAmount());
        validateDateRange(budget);
        validateAccountCurrencyCompatibility(budget);
    }

    private void validateAmountPositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateDateRange(Budget budget) {
        LocalDate startDate = budget.getStartDate();
        if (startDate == null) {
            return;
        }
        LocalDate endDate = normalizeOptionalDate(budget.getEndDate());
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
    }

    private LocalDate normalizeOptionalDate(LocalDate date) {
        if (date == null || date.equals(LocalDate.ofEpochDay(0L))) {
            return null;
        }
        return date;
    }

    private void validateAccountCurrencyCompatibility(Budget budget) {
        CurrencyCode budgetCurrency = budget.getCurrency();
        if (budgetCurrency == null || budget.getAccounts() == null || budget.getAccounts().isEmpty()) {
            return;
        }
        for (FinancialAccount account : budget.getAccounts()) {
            if (account.getCurrency() == null || !account.getCurrency().equals(budgetCurrency)) {
                throw new IllegalArgumentException("Account currency must match budget currency");
            }
        }
    }

    private void validateCategoryTypeForExpenseBudget(Category category) {
        CategoryType categoryType = category.getCategoryType();
        if (categoryType == null) {
            return;
        }
        if (categoryType == CategoryType.INCOME) {
            throw new IllegalArgumentException("Income categories cannot be linked to expense budgets");
        }
        if (categoryType != CategoryType.EXPENSE && categoryType != CategoryType.BOTH) {
            throw new IllegalArgumentException("Category type is not allowed for expense budgets");
        }
    }
}
