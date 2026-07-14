package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.criteria.TransactionRuleCriteria;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link TransactionRule} entities in the database.
 * The main input is a {@link TransactionRuleCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link TransactionRuleDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class TransactionRuleQueryService extends QueryService<TransactionRule> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleQueryService.class);

    private final TransactionRuleRepository transactionRuleRepository;

    private final TransactionRuleMapper transactionRuleMapper;

    private final CurrentUserService currentUserService;

    public TransactionRuleQueryService(
        TransactionRuleRepository transactionRuleRepository,
        TransactionRuleMapper transactionRuleMapper,
        CurrentUserService currentUserService
    ) {
        this.transactionRuleRepository = transactionRuleRepository;
        this.transactionRuleMapper = transactionRuleMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Return a {@link List} of {@link TransactionRuleDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<TransactionRuleDTO> findByCriteria(TransactionRuleCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<TransactionRule> specification = createSpecification(criteria);
        List<TransactionRuleDTO> transactionRules = transactionRuleMapper.toDto(
            transactionRuleRepository.fetchBagRelationships(transactionRuleRepository.findAll(specification))
        );
        transactionRules.sort(
            Comparator.comparing(TransactionRuleDTO::getPriority, Comparator.nullsLast(Integer::compareTo)).thenComparing(
                TransactionRuleDTO::getId,
                Comparator.nullsLast(Long::compareTo)
            )
        );
        return transactionRules;
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(TransactionRuleCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<TransactionRule> specification = createSpecification(criteria);
        return transactionRuleRepository.count(specification);
    }

    /**
     * Function to convert {@link TransactionRuleCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<TransactionRule> createSpecification(TransactionRuleCriteria criteria) {
        Specification<TransactionRule> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), TransactionRule_.id),
                buildStringSpecification(criteria.getName(), TransactionRule_.name),
                buildStringSpecification(criteria.getDescription(), TransactionRule_.description),
                buildRangeSpecification(criteria.getPriority(), TransactionRule_.priority),
                buildSpecification(criteria.getConditionLogic(), TransactionRule_.conditionLogic),
                buildStringSpecification(criteria.getResultingDescription(), TransactionRule_.resultingDescription),
                buildSpecification(criteria.getActive(), TransactionRule_.active),
                buildRangeSpecification(criteria.getCreatedAt(), TransactionRule_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), TransactionRule_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(TransactionRule_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getResultingCategoryId(), root ->
                    root.join(TransactionRule_.resultingCategory, JoinType.LEFT).get(Category_.id)
                ),
                buildSpecification(criteria.getResultingFinancialSubscriptionId(), root ->
                    root.join(TransactionRule_.resultingFinancialSubscription, JoinType.LEFT).get(FinancialSubscription_.id)
                ),
                buildSpecification(criteria.getResultingTagsId(), root ->
                    root.join(TransactionRule_.resultingTags, JoinType.LEFT).get(Tag_.id)
                ),
                buildSpecification(criteria.getConditionsId(), root ->
                    root.join(TransactionRule_.conditions, JoinType.LEFT).get(TransactionRuleCondition_.id)
                )
            );
            if (!currentUserService.isAdmin()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        root.join(TransactionRule_.user, JoinType.INNER).get(User_.login),
                        currentUserService.getCurrentUserLogin()
                    )
                );
            }
        }
        return specification;
    }
}
