package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.criteria.FinancialTransactionCriteria;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link FinancialTransaction} entities in the database.
 * The main input is a {@link FinancialTransactionCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link FinancialTransactionDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class FinancialTransactionQueryService extends QueryService<FinancialTransaction> {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialTransactionQueryService.class);

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    private final CurrentUserService currentUserService;

    public FinancialTransactionQueryService(
        FinancialTransactionRepository financialTransactionRepository,
        FinancialTransactionMapper financialTransactionMapper,
        CurrentUserService currentUserService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionMapper = financialTransactionMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Return a {@link Page} of {@link FinancialTransactionDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<FinancialTransactionDTO> findByCriteria(FinancialTransactionCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<FinancialTransaction> specification = createSpecification(criteria);
        return financialTransactionRepository
            .fetchBagRelationships(financialTransactionRepository.findAll(specification, page))
            .map(financialTransactionMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(FinancialTransactionCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<FinancialTransaction> specification = createSpecification(criteria);
        return financialTransactionRepository.count(specification);
    }

    /**
     * Function to convert {@link FinancialTransactionCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<FinancialTransaction> createSpecification(FinancialTransactionCriteria criteria) {
        Specification<FinancialTransaction> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), FinancialTransaction_.id),
                buildRangeSpecification(criteria.getTransactionDate(), FinancialTransaction_.transactionDate),
                buildRangeSpecification(criteria.getPostingDate(), FinancialTransaction_.postingDate),
                buildStringSpecification(criteria.getDescription(), FinancialTransaction_.description),
                buildRangeSpecification(criteria.getAmount(), FinancialTransaction_.amount),
                buildSpecification(criteria.getFlow(), FinancialTransaction_.flow),
                buildSpecification(criteria.getOrigin(), FinancialTransaction_.origin),
                buildStringSpecification(criteria.getExternalReference(), FinancialTransaction_.externalReference),
                buildStringSpecification(criteria.getNotes(), FinancialTransaction_.notes),
                buildRangeSpecification(criteria.getCreatedAt(), FinancialTransaction_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), FinancialTransaction_.updatedAt),
                buildSpecification(criteria.getAccountId(), root ->
                    root.join(FinancialTransaction_.account, JoinType.LEFT).get(FinancialAccount_.id)
                ),
                buildSpecification(criteria.getCategoryId(), root ->
                    root.join(FinancialTransaction_.category, JoinType.LEFT).get(Category_.id)
                ),
                buildSpecification(criteria.getFinancialSubscriptionId(), root ->
                    root.join(FinancialTransaction_.financialSubscription, JoinType.LEFT).get(FinancialSubscription_.id)
                ),
                buildSpecification(criteria.getTransactionIngestionId(), root ->
                    root.join(FinancialTransaction_.transactionIngestion, JoinType.LEFT).get(TransactionIngestion_.id)
                ),
                buildSpecification(criteria.getTagsId(), root -> root.join(FinancialTransaction_.tags, JoinType.LEFT).get(Tag_.id)),
                buildSpecification(criteria.getOutgoingInternalTransferId(), root ->
                    root.join(FinancialTransaction_.outgoingInternalTransfer, JoinType.LEFT).get(InternalTransfer_.id)
                ),
                buildSpecification(criteria.getIncomingInternalTransferId(), root ->
                    root.join(FinancialTransaction_.incomingInternalTransfer, JoinType.LEFT).get(InternalTransfer_.id)
                ),
                buildSpecification(criteria.getIngestionRecordId(), root ->
                    root.join(FinancialTransaction_.ingestionRecord, JoinType.LEFT).get(IngestionRecord_.id)
                )
            );
            if (!currentUserService.isAdmin()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        root
                            .join(FinancialTransaction_.account, JoinType.INNER)
                            .join(FinancialAccount_.user, JoinType.INNER)
                            .get(User_.login),
                        currentUserService.getCurrentUserLogin()
                    )
                );
            }
        }
        return specification;
    }
}
