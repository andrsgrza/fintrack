package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.FinancialSubscription}.
 */
@Service
@Transactional
public class FinancialSubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialSubscriptionService.class);

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final FinancialSubscriptionMapper financialSubscriptionMapper;

    private final CurrentUserService currentUserService;

    private final FinancialAccountService financialAccountService;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    public FinancialSubscriptionService(
        FinancialSubscriptionRepository financialSubscriptionRepository,
        FinancialSubscriptionMapper financialSubscriptionMapper,
        CurrentUserService currentUserService,
        FinancialAccountService financialAccountService,
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.financialSubscriptionMapper = financialSubscriptionMapper;
        this.currentUserService = currentUserService;
        this.financialAccountService = financialAccountService;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
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
        financialSubscription.setUser(currentUserService.getCurrentUser());
        applyRelationships(financialSubscription, financialSubscriptionDTO);
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
        FinancialSubscription existingFinancialSubscription = findAccessibleEntity(financialSubscriptionDTO.getId()).orElseThrow();
        FinancialSubscription financialSubscription = financialSubscriptionMapper.toEntity(financialSubscriptionDTO);
        financialSubscription.setUser(existingFinancialSubscription.getUser());
        applyRelationships(financialSubscription, financialSubscriptionDTO);
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

        return findAccessibleEntity(financialSubscriptionDTO.getId())
            .map(existingFinancialSubscription -> {
                financialSubscriptionMapper.partialUpdate(existingFinancialSubscription, financialSubscriptionDTO);
                applyRelationshipsForPartialUpdate(existingFinancialSubscription, financialSubscriptionDTO);
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
        if (currentUserService.isAdmin()) {
            return financialSubscriptionRepository.findAllWithEagerRelationships(pageable).map(financialSubscriptionMapper::toDto);
        }
        return financialSubscriptionRepository
            .findAllWithEagerRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(financialSubscriptionMapper::toDto);
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
        return findAccessibleEntity(id).map(financialSubscriptionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the financial subscription.
     *
     * @param id the id of the entity.
     * @return true when the subscription exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the financialSubscription by id.
     *
     * @param id the id of the entity.
     * @return true when the subscription was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete FinancialSubscription : {}", id);
        Optional<FinancialSubscription> financialSubscription = findAccessibleEntity(id);
        if (financialSubscription.isEmpty()) {
            return false;
        }
        financialSubscriptionRepository.deleteById(id);
        return true;
    }

    private Optional<FinancialSubscription> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return financialSubscriptionRepository.findOneWithEagerRelationships(id);
        }
        return financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRelationships(FinancialSubscription financialSubscription, FinancialSubscriptionDTO financialSubscriptionDTO) {
        financialSubscription.setAccount(resolveOptionalAccount(financialSubscriptionDTO.getAccount()));
        financialSubscription.setCategory(resolveOptionalCategory(financialSubscriptionDTO.getCategory()));
        financialSubscription.setTags(resolveTags(financialSubscriptionDTO.getTags()));
    }

    private void applyRelationshipsForPartialUpdate(
        FinancialSubscription financialSubscription,
        FinancialSubscriptionDTO financialSubscriptionDTO
    ) {
        if (financialSubscriptionDTO.getAccount() != null) {
            financialSubscription.setAccount(resolveOptionalAccount(financialSubscriptionDTO.getAccount()));
        }
        if (financialSubscriptionDTO.getCategory() != null) {
            financialSubscription.setCategory(resolveOptionalCategory(financialSubscriptionDTO.getCategory()));
        }
        if (financialSubscriptionDTO.getTags() != null) {
            financialSubscription.setTags(resolveTags(financialSubscriptionDTO.getTags()));
        }
    }

    private FinancialAccount resolveOptionalAccount(FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            return null;
        }
        return financialAccountService
            .findAccessibleAccountEntity(accountDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
    }

    private Category resolveOptionalCategory(CategoryDTO categoryDTO) {
        if (categoryDTO == null || categoryDTO.getId() == null) {
            return null;
        }
        return findAccessibleCategory(categoryDTO.getId()).orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
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
