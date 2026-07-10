package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.criteria.TagCriteria;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.TagMapper;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Tag} entities in the database.
 * The main input is a {@link TagCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link List} of {@link TagDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class TagQueryService extends QueryService<Tag> {

    private static final Logger LOG = LoggerFactory.getLogger(TagQueryService.class);

    private final TagRepository tagRepository;

    private final TagMapper tagMapper;

    private final CurrentUserService currentUserService;

    public TagQueryService(TagRepository tagRepository, TagMapper tagMapper, CurrentUserService currentUserService) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Return a {@link List} of {@link TagDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public List<TagDTO> findByCriteria(TagCriteria criteria) {
        LOG.debug("find by criteria : {}", criteria);
        final Specification<Tag> specification = createSpecification(criteria);
        return tagMapper.toDto(tagRepository.findAll(specification));
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(TagCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Tag> specification = createSpecification(criteria);
        return tagRepository.count(specification);
    }

    /**
     * Function to convert {@link TagCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Tag> createSpecification(TagCriteria criteria) {
        Specification<Tag> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), Tag_.id),
                buildStringSpecification(criteria.getName(), Tag_.name),
                buildStringSpecification(criteria.getDescription(), Tag_.description),
                buildStringSpecification(criteria.getColor(), Tag_.color),
                buildSpecification(criteria.getActive(), Tag_.active),
                buildRangeSpecification(criteria.getCreatedAt(), Tag_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), Tag_.updatedAt),
                buildSpecification(criteria.getUserId(), root -> root.join(Tag_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getFinancialTransactionsId(), root ->
                    root.join(Tag_.financialTransactions, JoinType.LEFT).get(FinancialTransaction_.id)
                ),
                buildSpecification(criteria.getTransactionRulesId(), root ->
                    root.join(Tag_.transactionRules, JoinType.LEFT).get(TransactionRule_.id)
                ),
                buildSpecification(criteria.getSubscriptionsId(), root ->
                    root.join(Tag_.subscriptions, JoinType.LEFT).get(FinancialSubscription_.id)
                ),
                buildSpecification(criteria.getBudgetsId(), root -> root.join(Tag_.budgets, JoinType.LEFT).get(Budget_.id))
            );
            if (!currentUserService.isAdmin()) {
                specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.join(Tag_.user, JoinType.INNER).get(User_.login), currentUserService.getCurrentUserLogin())
                );
            }
        }
        return specification;
    }
}
