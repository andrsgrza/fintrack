package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.service.criteria.FinancialAccountCriteria;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link FinancialAccount} entities in the database.
 * The main input is a {@link FinancialAccountCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link FinancialAccountDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class FinancialAccountQueryService extends QueryService<FinancialAccount> {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialAccountQueryService.class);

    private final FinancialAccountRepository financialAccountRepository;

    private final FinancialAccountMapper financialAccountMapper;

    public FinancialAccountQueryService(
        FinancialAccountRepository financialAccountRepository,
        FinancialAccountMapper financialAccountMapper
    ) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialAccountMapper = financialAccountMapper;
    }

    /**
     * Return a {@link List} of {@link FinancialAccountDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialAccountDTO> findByCriteria(FinancialAccountCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<FinancialAccount> specification = createSpecification(criteria);
        return financialAccountMapper.toDto(financialAccountRepository.findAll(specification));
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(FinancialAccountCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<FinancialAccount> specification = createSpecification(criteria);
        return financialAccountRepository.count(specification);
    }

    /**
     * Function to convert {@link FinancialAccountCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<FinancialAccount> createSpecification(FinancialAccountCriteria criteria) {
        Specification<FinancialAccount> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), FinancialAccount_.id),
                buildStringSpecification(criteria.getName(), FinancialAccount_.name),
                buildStringSpecification(criteria.getInstitutionName(), FinancialAccount_.institutionName),
                buildSpecification(criteria.getAccountType(), FinancialAccount_.accountType),
                buildSpecification(criteria.getCurrency(), FinancialAccount_.currency),
                buildRangeSpecification(criteria.getInitialBalance(), FinancialAccount_.initialBalance),
                buildRangeSpecification(criteria.getInitialBalanceDate(), FinancialAccount_.initialBalanceDate),
                buildStringSpecification(criteria.getLastFourDigits(), FinancialAccount_.lastFourDigits),
                buildStringSpecification(criteria.getDescription(), FinancialAccount_.description),
                buildStringSpecification(criteria.getColor(), FinancialAccount_.color),
                buildStringSpecification(criteria.getIcon(), FinancialAccount_.icon),
                buildSpecification(criteria.getActive(), FinancialAccount_.active),
                buildRangeSpecification(criteria.getCreatedAt(), FinancialAccount_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), FinancialAccount_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(FinancialAccount_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getCreditAccountDetailsId(), root ->
                    root.join(FinancialAccount_.creditAccountDetails, JoinType.LEFT).get(CreditAccountDetails_.id)
                ),
                buildSpecification(criteria.getFinancialTransactionsId(), root ->
                    root.join(FinancialAccount_.financialTransactions, JoinType.LEFT).get(FinancialTransaction_.id)
                ),
                buildSpecification(criteria.getSubscriptionsId(), root ->
                    root.join(FinancialAccount_.subscriptions, JoinType.LEFT).get(FinancialSubscription_.id)
                ),
                buildSpecification(criteria.getBudgetsId(), root -> root.join(FinancialAccount_.budgets, JoinType.LEFT).get(Budget_.id)),
                buildSpecification(criteria.getTransactionIngestionsId(), root ->
                    root.join(FinancialAccount_.transactionIngestions, JoinType.LEFT).get(TransactionIngestion_.id)
                )
            );
        }
        return specification;
    }
}
