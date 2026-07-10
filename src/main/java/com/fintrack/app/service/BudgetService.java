package com.fintrack.app.service;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
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

    private final FinancialAccountService financialAccountService;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    public BudgetService(
        BudgetRepository budgetRepository,
        BudgetMapper budgetMapper,
        CurrentUserService currentUserService,
        FinancialAccountService financialAccountService,
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
        this.currentUserService = currentUserService;
        this.financialAccountService = financialAccountService;
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
        applyRelationships(budget, budgetDTO);
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
        applyRelationships(budget, budgetDTO);
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
        LOG.debug("Request to partially update Budget : {}", budgetDTO);

        return findAccessibleEntity(budgetDTO.getId())
            .map(existingBudget -> {
                budgetMapper.partialUpdate(existingBudget, budgetDTO);
                applyRelationshipsForPartialUpdate(existingBudget, budgetDTO);
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
        budgetRepository.deleteById(id);
        return true;
    }

    private Optional<Budget> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return budgetRepository.findOneWithEagerRelationships(id);
        }
        return budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRelationships(Budget budget, BudgetDTO budgetDTO) {
        budget.setAccounts(resolveAccounts(budgetDTO.getAccounts()));
        budget.setCategories(resolveCategories(budgetDTO.getCategories()));
        budget.setTags(resolveTags(budgetDTO.getTags()));
    }

    private void applyRelationshipsForPartialUpdate(Budget budget, BudgetDTO budgetDTO) {
        if (budgetDTO.getAccounts() != null) {
            budget.setAccounts(resolveAccounts(budgetDTO.getAccounts()));
        }
        if (budgetDTO.getCategories() != null) {
            budget.setCategories(resolveCategories(budgetDTO.getCategories()));
        }
        if (budgetDTO.getTags() != null) {
            budget.setTags(resolveTags(budgetDTO.getTags()));
        }
    }

    private Set<FinancialAccount> resolveAccounts(Set<FinancialAccountDTO> accountDTOs) {
        if (accountDTOs == null || accountDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<FinancialAccount> accounts = new HashSet<>();
        for (FinancialAccountDTO accountDTO : accountDTOs) {
            if (accountDTO.getId() == null) {
                continue;
            }
            FinancialAccount account = financialAccountService
                .findAccessibleAccountEntity(accountDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
            accounts.add(account);
        }
        return accounts;
    }

    private Set<Category> resolveCategories(Set<CategoryDTO> categoryDTOs) {
        if (categoryDTOs == null || categoryDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Category> categories = new HashSet<>();
        for (CategoryDTO categoryDTO : categoryDTOs) {
            if (categoryDTO.getId() == null) {
                continue;
            }
            Category category = findAccessibleCategory(categoryDTO.getId()).orElseThrow(() ->
                new IllegalArgumentException("Category is not accessible")
            );
            categories.add(category);
        }
        return categories;
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs) {
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO.getId() == null) {
                continue;
            }
            Tag tag = findAccessibleTag(tagDTO.getId()).orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }

    private Optional<Category> findAccessibleCategory(Long id) {
        if (currentUserService.isAdmin()) {
            return categoryRepository.findById(id);
        }
        return categoryRepository.findOneByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private Optional<Tag> findAccessibleTag(Long id) {
        if (currentUserService.isAdmin()) {
            return tagRepository.findById(id);
        }
        return tagRepository.findOneByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }
}
