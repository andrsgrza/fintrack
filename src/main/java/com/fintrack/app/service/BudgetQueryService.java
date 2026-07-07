package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.Budget;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.service.criteria.BudgetCriteria;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Budget} entities in the database.
 * The main input is a {@link BudgetCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link BudgetDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class BudgetQueryService extends QueryService<Budget> {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetQueryService.class);

    private final BudgetRepository budgetRepository;

    private final BudgetMapper budgetMapper;

    public BudgetQueryService(BudgetRepository budgetRepository, BudgetMapper budgetMapper) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
    }

    /**
     * Return a {@link List} of {@link BudgetDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<BudgetDTO> findByCriteria(BudgetCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<Budget> specification = createSpecification(criteria);
        return budgetMapper.toDto(budgetRepository.fetchBagRelationships(budgetRepository.findAll(specification)));
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(BudgetCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Budget> specification = createSpecification(criteria);
        return budgetRepository.count(specification);
    }

    /**
     * Function to convert {@link BudgetCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Budget> createSpecification(BudgetCriteria criteria) {
        Specification<Budget> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), Budget_.id),
                buildStringSpecification(criteria.getName(), Budget_.name),
                buildRangeSpecification(criteria.getAmount(), Budget_.amount),
                buildStringSpecification(criteria.getCurrency(), Budget_.currency),
                buildSpecification(criteria.getPeriod(), Budget_.period),
                buildRangeSpecification(criteria.getStartDate(), Budget_.startDate),
                buildRangeSpecification(criteria.getEndDate(), Budget_.endDate),
                buildSpecification(criteria.getStatus(), Budget_.status),
                buildSpecification(criteria.getTagMatchMode(), Budget_.tagMatchMode),
                buildRangeSpecification(criteria.getWarningPercentage(), Budget_.warningPercentage),
                buildRangeSpecification(criteria.getCreatedAt(), Budget_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), Budget_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(Budget_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getAccountsId(), root -> root.join(Budget_.accounts, JoinType.LEFT).get(FinancialAccount_.id)),
                buildSpecification(criteria.getCategoriesId(), root -> root.join(Budget_.categories, JoinType.LEFT).get(Category_.id)),
                buildSpecification(criteria.getTagsId(), root -> root.join(Budget_.tags, JoinType.LEFT).get(Tag_.id))
            );
        }
        return specification;
    }
}
