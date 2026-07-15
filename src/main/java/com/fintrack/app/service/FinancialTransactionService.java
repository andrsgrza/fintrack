package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
import com.fintrack.app.service.rules.TagSuggestion;
import com.fintrack.app.service.rules.TransactionRuleEvaluationInput;
import com.fintrack.app.service.rules.TransactionRuleEvaluationResult;
import com.fintrack.app.service.rules.TransactionRuleEvaluationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final String FINANCIAL_TRANSACTION_DELETED = "FINANCIAL_TRANSACTION_DELETED";

    private static final String FINANCIAL_TRANSACTION_DELETED_MESSAGE = "Financial transaction was deleted manually.";

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    private final FinancialAccountService financialAccountService;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final InternalTransferRepository internalTransferRepository;

    private final IngestionRecordRepository ingestionRecordRepository;

    private final CurrentUserService currentUserService;

    private final TransactionRuleEvaluationService transactionRuleEvaluationService;

    public FinancialTransactionService(
        FinancialTransactionRepository financialTransactionRepository,
        FinancialTransactionMapper financialTransactionMapper,
        FinancialAccountService financialAccountService,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        FinancialSubscriptionRepository financialSubscriptionRepository,
        TransactionIngestionRepository transactionIngestionRepository,
        InternalTransferRepository internalTransferRepository,
        IngestionRecordRepository ingestionRecordRepository,
        CurrentUserService currentUserService,
        TransactionRuleEvaluationService transactionRuleEvaluationService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionMapper = financialTransactionMapper;
        this.financialAccountService = financialAccountService;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.currentUserService = currentUserService;
        this.transactionRuleEvaluationService = transactionRuleEvaluationService;
    }

    /**
     * Save a financialTransaction.
     *
     * @param financialTransactionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialTransactionDTO save(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to save FinancialTransaction");
        FinancialTransaction financialTransaction = financialTransactionMapper.toEntity(financialTransactionDTO);
        FinancialAccount account = resolveAccountForCreate(financialTransactionDTO.getAccount());
        financialTransaction.setAccount(account);
        financialTransaction.setCategory(resolveOptionalCategoryForOwner(financialTransactionDTO.getCategory(), ownerLogin(account)));
        financialTransaction.setFinancialSubscription(
            resolveOptionalSubscriptionForOwner(financialTransactionDTO.getFinancialSubscription(), account)
        );
        financialTransaction.setTags(resolveTagsForOwner(financialTransactionDTO.getTags(), ownerLogin(account)));
        financialTransaction.setTransactionIngestion(
            resolveOptionalTransactionIngestionForCreate(financialTransactionDTO.getTransactionIngestion(), account)
        );
        normalizeFields(financialTransaction);
        Instant now = Instant.now();
        financialTransaction.setCreatedAt(now);
        financialTransaction.setUpdatedAt(now);
        validateMergedState(financialTransaction);
        applyRulesOnCreate(financialTransaction, ownerLogin(account));
        validateMergedState(financialTransaction);
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
        return update(financialTransactionDTO, null);
    }

    public FinancialTransactionDTO update(FinancialTransactionDTO financialTransactionDTO, JsonNode updateNode) {
        LOG.debug("Request to update FinancialTransaction : {}", financialTransactionDTO.getId());
        FinancialTransaction existing = findAccessibleEntity(financialTransactionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        applyUpdate(existing, financialTransactionDTO, updateNode);
        existing = financialTransactionRepository.save(existing);
        return financialTransactionMapper.toDto(existing);
    }

    /**
     * Partially update a financialTransaction.
     *
     * @param financialTransactionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FinancialTransactionDTO> partialUpdate(FinancialTransactionDTO financialTransactionDTO) {
        return partialUpdate(financialTransactionDTO, null);
    }

    public Optional<FinancialTransactionDTO> partialUpdate(FinancialTransactionDTO financialTransactionDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update FinancialTransaction : {}", financialTransactionDTO.getId());
        return findAccessibleEntity(financialTransactionDTO.getId())
            .map(existing -> {
                applyUpdate(existing, financialTransactionDTO, patchNode);
                return existing;
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
     * Get OUT transactions that can be linked as the outgoing leg of an internal transfer.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findOutgoingInternalTransferCandidates() {
        LOG.debug("Request to get outgoing internal transfer candidates");
        return accessibleTransactionStream()
            .filter(financialTransaction -> !internalTransferRepository.existsByTransactionIdInEitherRole(financialTransaction.getId()))
            .filter(financialTransaction -> financialTransaction.getFlow() == TransactionFlow.OUT)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get IN transactions that can be linked as the incoming leg of an internal transfer.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findIncomingInternalTransferCandidates() {
        LOG.debug("Request to get incoming internal transfer candidates");
        return accessibleTransactionStream()
            .filter(financialTransaction -> !internalTransferRepository.existsByTransactionIdInEitherRole(financialTransaction.getId()))
            .filter(financialTransaction -> financialTransaction.getFlow() == TransactionFlow.IN)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Returns an accessible financial transaction entity for the current user.
     *
     * @param id the id of the transaction.
     * @return the entity when accessible.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialTransaction> findAccessibleTransactionEntity(Long id) {
        return findAccessibleEntity(id);
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
            .filter(financialTransaction -> !ingestionRecordRepository.existsByFinancialTransactionId(financialTransaction.getId()))
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
        ingestionRecordRepository.markFinancialTransactionDeleted(
            id,
            IngestionRecordStatus.REJECTED,
            FINANCIAL_TRANSACTION_DELETED,
            FINANCIAL_TRANSACTION_DELETED_MESSAGE
        );
        internalTransferRepository.deleteByTransactionIdInEitherRole(id);
        financialTransactionRepository.deleteTagLinksByFinancialTransactionId(id);
        financialTransactionRepository.deleteById(id);
        return true;
    }

    /**
     * Delete all remaining financial transactions for the given account.
     *
     * @param account the account whose transactions should be deleted.
     */
    public void deleteAllForAccount(FinancialAccount account) {
        Long accountId = requireAccountId(account);
        LOG.debug("Request to delete all FinancialTransactions for FinancialAccount : {}", accountId);
        ingestionRecordRepository.markFinancialTransactionsDeletedByAccountId(
            accountId,
            IngestionRecordStatus.REJECTED,
            FINANCIAL_TRANSACTION_DELETED,
            FINANCIAL_TRANSACTION_DELETED_MESSAGE
        );
        internalTransferRepository.deleteByAccountIdInEitherRole(accountId);
        financialTransactionRepository.deleteTagLinksByAccountId(accountId);
        financialTransactionRepository.deleteByAccountId(accountId);
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

    private Long requireAccountId(FinancialAccount account) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("Financial account is required");
        }
        return account.getId();
    }

    private void applyUpdate(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        rejectAccountChange(existing, dto, updateNode);
        rejectOriginChange(existing, dto, updateNode);
        rejectCreatedAtChange(existing, dto, updateNode);
        rejectUpdatedAtChange(existing, dto, updateNode);
        rejectTransactionIngestionChange(existing, dto, updateNode);

        boolean linkedToInternalTransfer = internalTransferRepository.existsByTransactionIdInEitherRole(existing.getId());
        if (fieldPresent(updateNode, "amount")) {
            if (dto.getAmount() == null) {
                throw new IllegalArgumentException("Amount is required");
            }
            BigDecimal normalizedAmount = normalizeAmount(dto.getAmount());
            if (linkedToInternalTransfer && !sameAmount(existing.getAmount(), normalizedAmount)) {
                throw new IllegalArgumentException("Amount cannot be changed while transaction is linked to an internal transfer");
            }
            existing.setAmount(normalizedAmount);
        }
        if (fieldPresent(updateNode, "flow")) {
            if (dto.getFlow() == null) {
                throw new IllegalArgumentException("Flow is required");
            }
            if (linkedToInternalTransfer && existing.getFlow() != dto.getFlow()) {
                throw new IllegalArgumentException("Flow cannot be changed while transaction is linked to an internal transfer");
            }
            existing.setFlow(dto.getFlow());
        }
        if (fieldPresent(updateNode, "transactionDate")) {
            if (dto.getTransactionDate() == null) {
                throw new IllegalArgumentException("Transaction date is required");
            }
            existing.setTransactionDate(dto.getTransactionDate());
        }
        if (fieldPresent(updateNode, "postingDate")) {
            existing.setPostingDate(dto.getPostingDate());
        }
        if (fieldPresent(updateNode, "description")) {
            existing.setDescription(dto.getDescription());
        }
        if (fieldPresent(updateNode, "externalReference")) {
            String externalReference = normalizeOptionalText(dto.getExternalReference(), 150, "External reference");
            if (existing.getTransactionIngestion() != null && !Objects.equals(existing.getExternalReference(), externalReference)) {
                throw new IllegalArgumentException("External reference cannot be changed once transaction ingestion is set");
            }
            existing.setExternalReference(externalReference);
        }
        if (fieldPresent(updateNode, "notes")) {
            existing.setNotes(dto.getNotes());
        }
        if (fieldPresent(updateNode, "category")) {
            existing.setCategory(resolveOptionalCategoryPatch(dto.getCategory(), ownerLogin(existing.getAccount()), updateNode));
        }
        if (fieldPresent(updateNode, "financialSubscription")) {
            existing.setFinancialSubscription(
                resolveOptionalSubscriptionPatch(dto.getFinancialSubscription(), existing.getAccount(), updateNode)
            );
        }
        if (fieldPresent(updateNode, "tags")) {
            existing.setTags(resolveTagsPatch(dto.getTags(), ownerLogin(existing.getAccount()), updateNode));
        }

        normalizeFields(existing);
        validateMergedState(existing);
        existing.setUpdatedAt(Instant.now());
    }

    private void validateMergedState(FinancialTransaction entity) {
        if (entity.getAccount() == null || entity.getAccount().getId() == null) {
            throw new IllegalArgumentException("Financial account is required");
        }
        if (entity.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }
        if (entity.getFlow() == null) {
            throw new IllegalArgumentException("Flow is required");
        }
        if (entity.getOrigin() == null) {
            throw new IllegalArgumentException("Origin is required");
        }
        entity.setAmount(normalizeAmount(entity.getAmount()));
        validateDescription(entity.getDescription());
        validateCategoryCompatibility(entity.getCategory(), entity.getFlow());
        validateTransactionIngestionMatchesAccountAndOrigin(entity.getTransactionIngestion(), entity.getAccount(), entity.getOrigin());
    }

    private void normalizeFields(FinancialTransaction entity) {
        entity.setDescription(normalizeRequiredText(entity.getDescription(), 500, "Description"));
        entity.setExternalReference(normalizeOptionalText(entity.getExternalReference(), 150, "External reference"));
        entity.setNotes(normalizeOptionalText(entity.getNotes(), 1000, "Notes"));
        if (entity.getAmount() != null) {
            entity.setAmount(normalizeAmount(entity.getAmount()));
        }
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Transaction amount must have at most 2 decimal places");
        }
    }

    private void validateDescription(String description) {
        normalizeRequiredText(description, 500, "Description");
    }

    private FinancialAccount resolveAccountForCreate(FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            throw new IllegalArgumentException("Financial account is required");
        }
        FinancialAccount account = financialAccountService
            .findAccessibleAccountEntity(accountDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
        if (!Objects.equals(ownerLogin(account), currentUserService.getCurrentUserLogin())) {
            throw new IllegalArgumentException("Financial account is not accessible");
        }
        return account;
    }

    private void applyRulesOnCreate(FinancialTransaction financialTransaction, String ownerLogin) {
        TransactionRuleEvaluationResult evaluation = transactionRuleEvaluationService.evaluate(
            new TransactionRuleEvaluationInput(
                ownerLogin,
                financialTransaction.getDescription(),
                financialTransaction.getAmount(),
                financialTransaction.getFlow(),
                financialTransaction.getExternalReference(),
                financialTransaction.getOrigin(),
                financialTransaction.getTransactionDate(),
                financialTransaction.getPostingDate(),
                financialTransaction.getAccount().getId(),
                financialTransaction.getCategory() == null ? null : financialTransaction.getCategory().getId(),
                financialTransaction.getCategory() == null ? null : financialTransaction.getCategory().getName(),
                currentTagIds(financialTransaction),
                currentTagNames(financialTransaction)
            )
        );
        applyRuleEvaluationOnCreate(financialTransaction, evaluation, ownerLogin);
    }

    private void applyRuleEvaluationOnCreate(
        FinancialTransaction financialTransaction,
        TransactionRuleEvaluationResult evaluation,
        String ownerLogin
    ) {
        if (
            financialTransaction.getCategory() == null &&
            evaluation.suggestedCategory() != null &&
            !evaluation.suggestedCategory().conflictsWithCurrentValue()
        ) {
            Category suggestedCategory = categoryRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(evaluation.suggestedCategory().categoryId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Suggested category is not accessible"));
            financialTransaction.setCategory(suggestedCategory);
        }

        Set<Long> tagIds = currentTagIds(financialTransaction);
        for (TagSuggestion suggestedTag : evaluation.suggestedTags()) {
            if (suggestedTag.alreadyPresent() || suggestedTag.duplicateOfEarlierSuggestion() || tagIds.contains(suggestedTag.tagId())) {
                continue;
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(suggestedTag.tagId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Suggested tag is not accessible"));
            financialTransaction.addTags(tag);
            tagIds.add(tag.getId());
        }
    }

    private Set<Long> currentTagIds(FinancialTransaction financialTransaction) {
        if (financialTransaction.getTags() == null || financialTransaction.getTags().isEmpty()) {
            return new HashSet<>();
        }
        return financialTransaction.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Map<Long, String> currentTagNames(FinancialTransaction financialTransaction) {
        if (financialTransaction.getTags() == null || financialTransaction.getTags().isEmpty()) {
            return Map.of();
        }
        return financialTransaction
            .getTags()
            .stream()
            .filter(tag -> tag.getId() != null)
            .collect(Collectors.toMap(Tag::getId, Tag::getName, (left, right) -> left));
    }

    private Category resolveOptionalCategoryForOwner(CategoryDTO categoryDTO, String ownerLogin) {
        if (categoryDTO == null) {
            return null;
        }
        if (categoryDTO.getId() == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Category resolveOptionalCategoryPatch(CategoryDTO categoryDTO, String ownerLogin, JsonNode updateNode) {
        if (relationshipExplicitNull(updateNode, "category")) {
            return null;
        }
        return resolveOptionalCategoryForOwner(categoryDTO, ownerLogin);
    }

    private Set<Tag> resolveTagsForOwner(Set<TagDTO> tagDTOs, String ownerLogin) {
        Set<Tag> tags = new HashSet<>();
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return tags;
        }
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO == null || tagDTO.getId() == null) {
                throw new IllegalArgumentException("Tag id is required");
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }

    private Set<Tag> resolveTagsPatch(Set<TagDTO> tagDTOs, String ownerLogin, JsonNode updateNode) {
        if (relationshipExplicitNull(updateNode, "tags")) {
            return new HashSet<>();
        }
        return resolveTagsForOwner(tagDTOs, ownerLogin);
    }

    private FinancialSubscription resolveOptionalSubscriptionForOwner(FinancialSubscriptionDTO subscriptionDTO, FinancialAccount account) {
        if (subscriptionDTO == null) {
            return null;
        }
        if (subscriptionDTO.getId() == null) {
            throw new IllegalArgumentException("Financial subscription id is required");
        }
        FinancialSubscription subscription = financialSubscriptionRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(subscriptionDTO.getId(), ownerLogin(account))
            .orElseThrow(() -> new IllegalArgumentException("Financial subscription is not accessible"));
        validateSubscriptionMatchesAccount(subscription, account);
        return subscription;
    }

    private FinancialSubscription resolveOptionalSubscriptionPatch(
        FinancialSubscriptionDTO subscriptionDTO,
        FinancialAccount account,
        JsonNode updateNode
    ) {
        if (relationshipExplicitNull(updateNode, "financialSubscription")) {
            return null;
        }
        return resolveOptionalSubscriptionForOwner(subscriptionDTO, account);
    }

    private void validateSubscriptionMatchesAccount(FinancialSubscription subscription, FinancialAccount account) {
        if (!Objects.equals(subscription.getCurrency(), account.getCurrency())) {
            throw new IllegalArgumentException("Financial subscription currency must match transaction account currency");
        }
        if (subscription.getAccount() != null && !Objects.equals(subscription.getAccount().getId(), account.getId())) {
            throw new IllegalArgumentException("Financial subscription account must match transaction account");
        }
    }

    private TransactionIngestion resolveOptionalTransactionIngestionForCreate(
        TransactionIngestionDTO transactionIngestionDTO,
        FinancialAccount account
    ) {
        if (transactionIngestionDTO == null) {
            return null;
        }
        if (transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion id is required");
        }
        TransactionIngestion transactionIngestion;
        if (currentUserService.isAdmin()) {
            transactionIngestion = transactionIngestionRepository
                .findOneWithToOneRelationships(transactionIngestionDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        } else {
            transactionIngestion = transactionIngestionRepository
                .findOneWithToOneRelationshipsByIdAndAccountUserLogin(
                    transactionIngestionDTO.getId(),
                    currentUserService.getCurrentUserLogin()
                )
                .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        }
        if (
            transactionIngestion.getAccount() == null ||
            transactionIngestion.getAccount().getId() == null ||
            !Objects.equals(transactionIngestion.getAccount().getId(), account.getId())
        ) {
            throw new IllegalArgumentException("Transaction ingestion account must match transaction account");
        }
        return transactionIngestion;
    }

    private void validateTransactionIngestionMatchesAccountAndOrigin(
        TransactionIngestion transactionIngestion,
        FinancialAccount account,
        TransactionOrigin origin
    ) {
        if (transactionIngestion == null) {
            return;
        }
        if (
            transactionIngestion.getAccount() == null ||
            transactionIngestion.getAccount().getId() == null ||
            !Objects.equals(transactionIngestion.getAccount().getId(), account.getId())
        ) {
            throw new IllegalArgumentException("Transaction ingestion account must match transaction account");
        }
        TransactionOrigin requiredOrigin = requiredOriginFor(transactionIngestion.getIngestionType());
        if (origin != requiredOrigin) {
            throw new IllegalArgumentException("Transaction origin must match transaction ingestion type");
        }
    }

    private TransactionOrigin requiredOriginFor(IngestionType ingestionType) {
        if (ingestionType == IngestionType.FILE) {
            return TransactionOrigin.FILE_IMPORT;
        }
        if (ingestionType == IngestionType.API) {
            return TransactionOrigin.API;
        }
        throw new IllegalArgumentException("Unsupported transaction ingestion type");
    }

    private void validateCategoryCompatibility(Category category, TransactionFlow flow) {
        if (category == null || flow == null) {
            return;
        }
        CategoryType categoryType = category.getCategoryType();
        if (flow == TransactionFlow.OUT && categoryType != CategoryType.EXPENSE && categoryType != CategoryType.BOTH) {
            throw new IllegalArgumentException("Category type is not compatible with transaction flow");
        }
        if (flow == TransactionFlow.IN && categoryType != CategoryType.INCOME && categoryType != CategoryType.BOTH) {
            throw new IllegalArgumentException("Category type is not compatible with transaction flow");
        }
    }

    private void rejectAccountChange(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        if (!fieldPresent(updateNode, "account")) {
            return;
        }
        if (dto.getAccount() == null || dto.getAccount().getId() == null) {
            throw new IllegalArgumentException("Financial account cannot be changed");
        }
        if (!Objects.equals(existing.getAccount().getId(), dto.getAccount().getId())) {
            throw new IllegalArgumentException("Financial account cannot be changed");
        }
    }

    private void rejectOriginChange(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        if (!fieldPresent(updateNode, "origin")) {
            return;
        }
        if (dto.getOrigin() == null || existing.getOrigin() != dto.getOrigin()) {
            throw new IllegalArgumentException("Origin cannot be changed");
        }
    }

    private void rejectCreatedAtChange(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        if (!fieldPresent(updateNode, "createdAt")) {
            return;
        }
        if (dto.getCreatedAt() == null || !Objects.equals(existing.getCreatedAt(), dto.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private void rejectUpdatedAtChange(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        if (!fieldPresent(updateNode, "updatedAt")) {
            return;
        }
        if (dto.getUpdatedAt() == null || !Objects.equals(existing.getUpdatedAt(), dto.getUpdatedAt())) {
            throw new IllegalArgumentException("Updated at cannot be changed");
        }
    }

    private void rejectTransactionIngestionChange(FinancialTransaction existing, FinancialTransactionDTO dto, JsonNode updateNode) {
        if (!fieldPresent(updateNode, "transactionIngestion")) {
            return;
        }
        Long existingId = existing.getTransactionIngestion() == null ? null : existing.getTransactionIngestion().getId();
        TransactionIngestionDTO transactionIngestionDTO = dto.getTransactionIngestion();
        if (transactionIngestionDTO == null) {
            if (existingId == null) {
                return;
            }
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
        if (transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion id is required");
        }
        if (existingId == null || !Objects.equals(existingId, transactionIngestionDTO.getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private String ownerLogin(FinancialAccount account) {
        if (account == null || account.getUser() == null || account.getUser().getLogin() == null) {
            throw new IllegalArgumentException("Financial account owner is required");
        }
        return account.getUser().getLogin();
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private boolean fieldPresent(JsonNode node, String fieldName) {
        return node == null || node.has(fieldName);
    }

    private boolean relationshipExplicitNull(JsonNode node, String fieldName) {
        return node != null && node.has(fieldName) && node.get(fieldName).isNull();
    }
}
