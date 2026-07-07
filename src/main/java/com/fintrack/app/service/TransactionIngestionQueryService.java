package com.fintrack.app.service;

import com.fintrack.app.domain.*; // for static metamodels
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.criteria.TransactionIngestionCriteria;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
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
 * Service for executing complex queries for {@link TransactionIngestion} entities in the database.
 * The main input is a {@link TransactionIngestionCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link TransactionIngestionDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class TransactionIngestionQueryService extends QueryService<TransactionIngestion> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionIngestionQueryService.class);

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final TransactionIngestionMapper transactionIngestionMapper;

    public TransactionIngestionQueryService(
        TransactionIngestionRepository transactionIngestionRepository,
        TransactionIngestionMapper transactionIngestionMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.transactionIngestionMapper = transactionIngestionMapper;
    }

    /**
     * Return a {@link Page} of {@link TransactionIngestionDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<TransactionIngestionDTO> findByCriteria(TransactionIngestionCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<TransactionIngestion> specification = createSpecification(criteria);
        return transactionIngestionRepository
            .fetchBagRelationships(transactionIngestionRepository.findAll(specification, page))
            .map(transactionIngestionMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(TransactionIngestionCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<TransactionIngestion> specification = createSpecification(criteria);
        return transactionIngestionRepository.count(specification);
    }

    /**
     * Function to convert {@link TransactionIngestionCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<TransactionIngestion> createSpecification(TransactionIngestionCriteria criteria) {
        Specification<TransactionIngestion> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), TransactionIngestion_.id),
                buildSpecification(criteria.getIngestionType(), TransactionIngestion_.ingestionType),
                buildSpecification(criteria.getStatus(), TransactionIngestion_.status),
                buildStringSpecification(criteria.getSourceLabel(), TransactionIngestion_.sourceLabel),
                buildRangeSpecification(criteria.getStartedAt(), TransactionIngestion_.startedAt),
                buildRangeSpecification(criteria.getCompletedAt(), TransactionIngestion_.completedAt),
                buildRangeSpecification(criteria.getRecordsReceived(), TransactionIngestion_.recordsReceived),
                buildRangeSpecification(criteria.getRecordsCreated(), TransactionIngestion_.recordsCreated),
                buildRangeSpecification(criteria.getRecordsSkipped(), TransactionIngestion_.recordsSkipped),
                buildRangeSpecification(criteria.getRecordsRejected(), TransactionIngestion_.recordsRejected),
                buildStringSpecification(criteria.getErrorMessage(), TransactionIngestion_.errorMessage),
                buildRangeSpecification(criteria.getCreatedAt(), TransactionIngestion_.createdAt),
                buildSpecification(criteria.getAccountsId(), root ->
                    root.join(TransactionIngestion_.accounts, JoinType.LEFT).get(FinancialAccount_.id)
                ),
                buildSpecification(criteria.getFileIngestionId(), root ->
                    root.join(TransactionIngestion_.fileIngestion, JoinType.LEFT).get(FileIngestion_.id)
                ),
                buildSpecification(criteria.getApiIngestionId(), root ->
                    root.join(TransactionIngestion_.apiIngestion, JoinType.LEFT).get(ApiIngestion_.id)
                ),
                buildSpecification(criteria.getFinancialTransactionsId(), root ->
                    root.join(TransactionIngestion_.financialTransactions, JoinType.LEFT).get(FinancialTransaction_.id)
                ),
                buildSpecification(criteria.getRecordsId(), root ->
                    root.join(TransactionIngestion_.records, JoinType.LEFT).get(IngestionRecord_.id)
                )
            );
        }
        return specification;
    }
}
