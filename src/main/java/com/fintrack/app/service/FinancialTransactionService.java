package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.FinancialTransaction}.
 */
@Service
@Transactional
public class FinancialTransactionService {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialTransactionService.class);

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    private final FinancialAccountService financialAccountService;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final CurrentUserService currentUserService;

    public FinancialTransactionService(
        FinancialTransactionRepository financialTransactionRepository,
        FinancialTransactionMapper financialTransactionMapper,
        FinancialAccountService financialAccountService,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        CurrentUserService currentUserService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionMapper = financialTransactionMapper;
        this.financialAccountService = financialAccountService;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Save a financialTransaction.
     *
     * @param financialTransactionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialTransactionDTO save(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to save FinancialTransaction : {}", financialTransactionDTO);
        validateAmount(financialTransactionDTO.getAmount());
        FinancialTransaction financialTransaction = financialTransactionMapper.toEntity(financialTransactionDTO);
        applyRelationshipsForCreate(financialTransaction, financialTransactionDTO);
        financialTransaction = financialTransactionRepository.save(financialTransaction);
        return financialTransactionMapper.toDto(financialTransaction);
    }

    /**
     * Update a financialTransaction.
     *
     * @param financialTransactionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialTransactionDTO update(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to update FinancialTransaction : {}", financialTransactionDTO);
        FinancialTransaction existingFinancialTransaction = findAccessibleEntity(financialTransactionDTO.getId()).orElseThrow();
        validateAmount(financialTransactionDTO.getAmount());
        FinancialTransaction financialTransaction = financialTransactionMapper.toEntity(financialTransactionDTO);
        applyRelationshipsForUpdate(financialTransaction, financialTransactionDTO);
        preserveImmutableFields(financialTransaction, existingFinancialTransaction);
        financialTransaction = financialTransactionRepository.save(financialTransaction);
        return financialTransactionMapper.toDto(financialTransaction);
    }

    /**
     * Partially update a financialTransaction.
     *
     * @param financialTransactionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FinancialTransactionDTO> partialUpdate(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to partially update FinancialTransaction : {}", financialTransactionDTO);

        return findAccessibleEntity(financialTransactionDTO.getId())
            .map(existingFinancialTransaction -> {
                TransactionOrigin origin = existingFinancialTransaction.getOrigin();
                var transactionIngestion = existingFinancialTransaction.getTransactionIngestion();
                if (financialTransactionDTO.getAmount() != null) {
                    validateAmount(financialTransactionDTO.getAmount());
                }
                financialTransactionMapper.partialUpdate(existingFinancialTransaction, financialTransactionDTO);
                applyRelationshipsForPartialUpdate(existingFinancialTransaction, financialTransactionDTO);
                existingFinancialTransaction.setOrigin(origin);
                existingFinancialTransaction.setTransactionIngestion(transactionIngestion);
                return existingFinancialTransaction;
            })
            .map(financialTransactionRepository::save)
            .map(financialTransactionMapper::toDto);
    }

    /**
     * Get all the financialTransactions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<FinancialTransactionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return financialTransactionRepository.findAllWithEagerRelationships(pageable).map(financialTransactionMapper::toDto);
        }
        return financialTransactionRepository
            .findAllAccessibleWithToOneRelationshipsByAccountUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(financialTransactionMapper::toDto);
    }

    /**
     *  Get all the financialTransactions where OutgoingInternalTransfer is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereOutgoingInternalTransferIsNull() {
        LOG.debug("Request to get all financialTransactions where OutgoingInternalTransfer is null");
        return accessibleTransactionStream()
            .filter(financialTransaction -> financialTransaction.getOutgoingInternalTransfer() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the financialTransactions where IncomingInternalTransfer is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereIncomingInternalTransferIsNull() {
        LOG.debug("Request to get all financialTransactions where IncomingInternalTransfer is null");
        return accessibleTransactionStream()
            .filter(financialTransaction -> financialTransaction.getIncomingInternalTransfer() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the financialTransactions where IngestionRecord is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereIngestionRecordIsNull() {
        LOG.debug("Request to get all financialTransactions where IngestionRecord is null");
        return accessibleTransactionStream()
            .filter(financialTransaction -> financialTransaction.getIngestionRecord() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one financialTransaction by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialTransactionDTO> findOne(Long id) {
        LOG.debug("Request to get FinancialTransaction : {}", id);
        return findAccessibleEntity(id).map(financialTransactionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the financial transaction.
     *
     * @param id the id of the entity.
     * @return true when the transaction exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the financialTransaction by id.
     *
     * @param id the id of the entity.
     * @return true when the transaction was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete FinancialTransaction : {}", id);
        Optional<FinancialTransaction> financialTransaction = findAccessibleEntity(id);
        if (financialTransaction.isEmpty()) {
            return false;
        }
        financialTransactionRepository.deleteById(id);
        return true;
    }

    private Stream<FinancialTransaction> accessibleTransactionStream() {
        if (currentUserService.isAdmin()) {
            return StreamSupport.stream(financialTransactionRepository.findAll().spliterator(), false);
        }
        return financialTransactionRepository
            .findAllAccessibleWithToOneRelationshipsByAccountUserLogin(currentUserService.getCurrentUserLogin())
            .stream();
    }

    private Optional<FinancialTransaction> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return financialTransactionRepository.findOneWithEagerRelationships(id);
        }
        return financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
    }

    private void applyRelationshipsForCreate(FinancialTransaction entity, FinancialTransactionDTO dto) {
        entity.setAccount(resolveAccount(dto.getAccount()));
        entity.setCategory(resolveOptionalCategory(dto.getCategory()));
        entity.setFinancialSubscription(resolveOptionalSubscription(dto.getFinancialSubscription()));
        entity.setTags(resolveTags(dto.getTags()));
        entity.setOrigin(TransactionOrigin.MANUAL);
        entity.setTransactionIngestion(null);
    }

    private void applyRelationshipsForUpdate(FinancialTransaction entity, FinancialTransactionDTO dto) {
        entity.setAccount(resolveAccount(dto.getAccount()));
        entity.setCategory(resolveOptionalCategory(dto.getCategory()));
        entity.setFinancialSubscription(resolveOptionalSubscription(dto.getFinancialSubscription()));
        entity.setTags(resolveTags(dto.getTags()));
    }

    private void applyRelationshipsForPartialUpdate(FinancialTransaction entity, FinancialTransactionDTO dto) {
        if (dto.getAccount() != null) {
            entity.setAccount(resolveAccount(dto.getAccount()));
        }
        if (dto.getCategory() != null) {
            entity.setCategory(resolveOptionalCategory(dto.getCategory()));
        }
        if (dto.getFinancialSubscription() != null) {
            entity.setFinancialSubscription(resolveOptionalSubscription(dto.getFinancialSubscription()));
        }
        if (dto.getTags() != null) {
            entity.setTags(resolveTags(dto.getTags()));
        }
    }

    private void preserveImmutableFields(FinancialTransaction entity, FinancialTransaction existing) {
        entity.setOrigin(existing.getOrigin());
        entity.setTransactionIngestion(existing.getTransactionIngestion());
    }

    private FinancialAccount resolveAccount(FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            throw new IllegalArgumentException("Financial account is required");
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

    private FinancialSubscription resolveOptionalSubscription(FinancialSubscriptionDTO subscriptionDTO) {
        if (subscriptionDTO == null || subscriptionDTO.getId() == null) {
            return null;
        }
        return findAccessibleSubscription(subscriptionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Financial subscription is not accessible")
        );
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

    private Optional<FinancialSubscription> findAccessibleSubscription(Long id) {
        if (currentUserService.isAdmin()) {
            return financialSubscriptionRepository.findById(id);
        }
        return financialSubscriptionRepository.findOneByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }
}
