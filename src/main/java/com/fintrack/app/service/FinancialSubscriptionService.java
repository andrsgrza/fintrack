package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.RecurrenceUnit;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
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

    private final FinancialAccountRepository financialAccountRepository;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    public FinancialSubscriptionService(
        FinancialSubscriptionRepository financialSubscriptionRepository,
        FinancialSubscriptionMapper financialSubscriptionMapper,
        CurrentUserService currentUserService,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.financialSubscriptionMapper = financialSubscriptionMapper;
        this.currentUserService = currentUserService;
        this.financialAccountRepository = financialAccountRepository;
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
        applyRelationships(financialSubscription, financialSubscriptionDTO, financialSubscription.getUser().getLogin());
        validateDates(financialSubscription);
        validateAccountCurrency(financialSubscription);
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
        validateStructuralFieldsImmutable(existingFinancialSubscription, financialSubscription);
        applyRelationships(financialSubscription, financialSubscriptionDTO, existingFinancialSubscription.getUser().getLogin());
        validateDates(financialSubscription);
        validateAccountCurrency(financialSubscription);
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
        return partialUpdate(financialSubscriptionDTO, null);
    }

    /**
     * Partially update a financialSubscription, applying link changes only for JSON fields present in the patch body.
     *
     * @param financialSubscriptionDTO the entity to update partially.
     * @param patchNode the raw patch payload; when null, link fields are updated only if non-null in the DTO.
     * @return the persisted entity.
     */
    public Optional<FinancialSubscriptionDTO> partialUpdate(FinancialSubscriptionDTO financialSubscriptionDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update FinancialSubscription : {}", financialSubscriptionDTO);

        return findAccessibleEntity(financialSubscriptionDTO.getId())
            .map(existingFinancialSubscription -> {
                CurrencyCode previousCurrency = existingFinancialSubscription.getCurrency();
                RecurrenceUnit previousRecurrenceUnit = existingFinancialSubscription.getRecurrenceUnit();
                Integer previousIntervalCount = existingFinancialSubscription.getIntervalCount();

                financialSubscriptionMapper.partialUpdate(existingFinancialSubscription, financialSubscriptionDTO);
                applyRelationshipsForPartialUpdate(
                    existingFinancialSubscription,
                    financialSubscriptionDTO,
                    patchNode,
                    existingFinancialSubscription.getUser().getLogin()
                );
                validateStructuralFieldsImmutableAfterPartialUpdate(
                    existingFinancialSubscription,
                    previousCurrency,
                    previousRecurrenceUnit,
                    previousIntervalCount
                );
                validateDates(existingFinancialSubscription);
                validateAccountCurrency(existingFinancialSubscription);
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
        Long subscriptionId = financialSubscription.orElseThrow().getId();
        unlinkFinancialSubscriptionFromAllRelationships(subscriptionId);
        financialSubscriptionRepository.deleteById(subscriptionId);
        return true;
    }

    private void unlinkFinancialSubscriptionFromAllRelationships(Long subscriptionId) {
        financialSubscriptionRepository.clearFinancialTransactionSubscriptionReferences(subscriptionId);
        financialSubscriptionRepository.clearTransactionRuleResultingSubscriptionReferences(subscriptionId);
        financialSubscriptionRepository.deleteTagLinksByFinancialSubscriptionId(subscriptionId);
    }

    private Optional<FinancialSubscription> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return financialSubscriptionRepository.findOneWithEagerRelationships(id);
        }
        return financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void applyRelationships(
        FinancialSubscription financialSubscription,
        FinancialSubscriptionDTO financialSubscriptionDTO,
        String ownerLogin
    ) {
        financialSubscription.setAccount(resolveOptionalAccount(financialSubscriptionDTO.getAccount(), ownerLogin));
        financialSubscription.setCategory(resolveOptionalCategory(financialSubscriptionDTO.getCategory(), ownerLogin));
        financialSubscription.setTags(resolveTags(financialSubscriptionDTO.getTags(), ownerLogin));
    }

    private void applyRelationshipsForPartialUpdate(
        FinancialSubscription financialSubscription,
        FinancialSubscriptionDTO financialSubscriptionDTO,
        JsonNode patchNode,
        String ownerLogin
    ) {
        if (patchNode != null) {
            if (patchNode.has("account")) {
                financialSubscription.setAccount(resolveOptionalAccount(financialSubscriptionDTO.getAccount(), ownerLogin));
            }
            if (patchNode.has("category")) {
                financialSubscription.setCategory(resolveOptionalCategory(financialSubscriptionDTO.getCategory(), ownerLogin));
            }
            if (patchNode.has("tags")) {
                financialSubscription.setTags(resolveTags(financialSubscriptionDTO.getTags(), ownerLogin));
            }
            return;
        }
        if (financialSubscriptionDTO.getAccount() != null) {
            financialSubscription.setAccount(resolveOptionalAccount(financialSubscriptionDTO.getAccount(), ownerLogin));
        }
        if (financialSubscriptionDTO.getCategory() != null) {
            financialSubscription.setCategory(resolveOptionalCategory(financialSubscriptionDTO.getCategory(), ownerLogin));
        }
        if (financialSubscriptionDTO.getTags() != null) {
            financialSubscription.setTags(resolveTags(financialSubscriptionDTO.getTags(), ownerLogin));
        }
    }

    private FinancialAccount resolveOptionalAccount(FinancialAccountDTO accountDTO, String ownerLogin) {
        if (accountDTO == null || accountDTO.getId() == null) {
            return null;
        }
        return financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
    }

    private Category resolveOptionalCategory(CategoryDTO categoryDTO, String ownerLogin) {
        if (categoryDTO == null || categoryDTO.getId() == null) {
            return null;
        }
        return categoryRepository
            .findOneByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
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

    private void validateDates(FinancialSubscription financialSubscription) {
        LocalDate startDate = financialSubscription.getStartDate();
        if (startDate == null) {
            return;
        }
        LocalDate endDate = normalizeOptionalDate(financialSubscription.getEndDate());
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        LocalDate nextExpectedDate = normalizeOptionalDate(financialSubscription.getNextExpectedDate());
        if (nextExpectedDate != null && nextExpectedDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Next expected date must be on or after start date");
        }
    }

    private LocalDate normalizeOptionalDate(LocalDate date) {
        if (date == null || date.equals(LocalDate.ofEpochDay(0L))) {
            return null;
        }
        return date;
    }

    private void validateAccountCurrency(FinancialSubscription financialSubscription) {
        FinancialAccount account = financialSubscription.getAccount();
        if (account == null || account.getCurrency() == null || financialSubscription.getCurrency() == null) {
            return;
        }
        if (!account.getCurrency().equals(financialSubscription.getCurrency())) {
            throw new IllegalArgumentException("Account currency must match subscription currency");
        }
    }

    private void validateStructuralFieldsImmutable(FinancialSubscription existing, FinancialSubscription updated) {
        if (!hasLinkedFinancialTransactions(existing.getId())) {
            return;
        }
        if (!Objects.equals(existing.getCurrency(), updated.getCurrency())) {
            throw new IllegalArgumentException("Currency cannot be changed while subscription is linked to transactions");
        }
        if (!Objects.equals(existing.getRecurrenceUnit(), updated.getRecurrenceUnit())) {
            throw new IllegalArgumentException("Recurrence unit cannot be changed while subscription is linked to transactions");
        }
        if (!Objects.equals(existing.getIntervalCount(), updated.getIntervalCount())) {
            throw new IllegalArgumentException("Interval count cannot be changed while subscription is linked to transactions");
        }
    }

    private void validateStructuralFieldsImmutableAfterPartialUpdate(
        FinancialSubscription financialSubscription,
        CurrencyCode previousCurrency,
        RecurrenceUnit previousRecurrenceUnit,
        Integer previousIntervalCount
    ) {
        if (!hasLinkedFinancialTransactions(financialSubscription.getId())) {
            return;
        }
        if (!Objects.equals(previousCurrency, financialSubscription.getCurrency())) {
            throw new IllegalArgumentException("Currency cannot be changed while subscription is linked to transactions");
        }
        if (!Objects.equals(previousRecurrenceUnit, financialSubscription.getRecurrenceUnit())) {
            throw new IllegalArgumentException("Recurrence unit cannot be changed while subscription is linked to transactions");
        }
        if (!Objects.equals(previousIntervalCount, financialSubscription.getIntervalCount())) {
            throw new IllegalArgumentException("Interval count cannot be changed while subscription is linked to transactions");
        }
    }

    private boolean hasLinkedFinancialTransactions(Long subscriptionId) {
        return financialSubscriptionRepository.existsFinancialTransactionByFinancialSubscriptionId(subscriptionId);
    }
}
