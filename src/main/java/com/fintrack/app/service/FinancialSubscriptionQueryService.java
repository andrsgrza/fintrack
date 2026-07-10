package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.service.criteria.FinancialSubscriptionCriteria;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link FinancialSubscription} entities in the database.
 * The main input is a {@link FinancialSubscriptionCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link FinancialSubscriptionDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class FinancialSubscriptionQueryService extends QueryService<FinancialSubscription> {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialSubscriptionQueryService.class);

    private final FinancialSubscriptionRepository financialSubscriptionRepository;

    private final FinancialSubscriptionMapper financialSubscriptionMapper;

    private final CurrentUserService currentUserService;

    public FinancialSubscriptionQueryService(
        FinancialSubscriptionRepository financialSubscriptionRepository,
        FinancialSubscriptionMapper financialSubscriptionMapper,
        CurrentUserService currentUserService
    ) {
        this.financialSubscriptionRepository = financialSubscriptionRepository;
        this.financialSubscriptionMapper = financialSubscriptionMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Return a {@link List} of {@link FinancialSubscriptionDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialSubscriptionDTO> findByCriteria(FinancialSubscriptionCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<FinancialSubscription> specification = createSpecification(criteria);
        return financialSubscriptionMapper.toDto(
            financialSubscriptionRepository.fetchBagRelationships(financialSubscriptionRepository.findAll(specification))
        );
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(FinancialSubscriptionCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<FinancialSubscription> specification = createSpecification(criteria);
        return financialSubscriptionRepository.count(specification);
    }

    /**
     * Function to convert {@link FinancialSubscriptionCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<FinancialSubscription> createSpecification(FinancialSubscriptionCriteria criteria) {
        Specification<FinancialSubscription> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), FinancialSubscription_.id),
                buildStringSpecification(criteria.getName(), FinancialSubscription_.name),
                buildStringSpecification(criteria.getDescription(), FinancialSubscription_.description),
                buildSpecification(criteria.getStatus(), FinancialSubscription_.status),
                buildRangeSpecification(criteria.getExpectedAmount(), FinancialSubscription_.expectedAmount),
                buildRangeSpecification(criteria.getAmountTolerancePercentage(), FinancialSubscription_.amountTolerancePercentage),
                buildSpecification(criteria.getCurrency(), FinancialSubscription_.currency),
                buildSpecification(criteria.getRecurrenceUnit(), FinancialSubscription_.recurrenceUnit),
                buildRangeSpecification(criteria.getIntervalCount(), FinancialSubscription_.intervalCount),
                buildRangeSpecification(criteria.getStartDate(), FinancialSubscription_.startDate),
                buildRangeSpecification(criteria.getNextExpectedDate(), FinancialSubscription_.nextExpectedDate),
                buildRangeSpecification(criteria.getEndDate(), FinancialSubscription_.endDate),
                buildSpecification(criteria.getAutomaticPayment(), FinancialSubscription_.automaticPayment),
                buildStringSpecification(criteria.getNotes(), FinancialSubscription_.notes),
                buildRangeSpecification(criteria.getCreatedAt(), FinancialSubscription_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), FinancialSubscription_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(FinancialSubscription_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getAccountId(), root ->
                    root.join(FinancialSubscription_.account, JoinType.LEFT).get(FinancialAccount_.id)
                ),
                buildSpecification(criteria.getCategoryId(), root ->
                    root.join(FinancialSubscription_.category, JoinType.LEFT).get(Category_.id)
                ),
                buildSpecification(criteria.getTagsId(), root -> root.join(FinancialSubscription_.tags, JoinType.LEFT).get(Tag_.id)),
                buildSpecification(criteria.getFinancialTransactionsId(), root ->
                    root.join(FinancialSubscription_.financialTransactions, JoinType.LEFT).get(FinancialTransaction_.id)
                ),
                buildSpecification(criteria.getTransactionRulesId(), root ->
                    root.join(FinancialSubscription_.transactionRules, JoinType.LEFT).get(TransactionRule_.id)
                )
            );
            if (!currentUserService.isAdmin()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        root.join(FinancialSubscription_.user, JoinType.INNER).get(User_.login),
                        currentUserService.getCurrentUserLogin()
                    )
                );
            }
        }
        return specification;
    }
}
