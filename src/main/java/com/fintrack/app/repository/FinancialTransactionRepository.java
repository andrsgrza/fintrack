package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FinancialTransaction entity.
 *
 * When extending this class, extend FinancialTransactionRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface FinancialTransactionRepository
    extends
        FinancialTransactionRepositoryWithBagRelationships,
        JpaRepository<FinancialTransaction, Long>,
        JpaSpecificationExecutor<FinancialTransaction> {
    default Optional<FinancialTransaction> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findOneWithToOneRelationships(id));
    }

    default List<FinancialTransaction> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships());
    }

    default Page<FinancialTransaction> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAllWithToOneRelationships(pageable));
    }

    @Query(
        value = "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription",
        countQuery = "select count(financialTransaction) from FinancialTransaction financialTransaction"
    )
    Page<FinancialTransaction> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription"
    )
    List<FinancialTransaction> findAllWithToOneRelationships();

    @Query(
        "select financialTransaction from FinancialTransaction financialTransaction left join fetch financialTransaction.account left join fetch financialTransaction.category left join fetch financialTransaction.financialSubscription where financialTransaction.id =:id"
    )
    Optional<FinancialTransaction> findOneWithToOneRelationships(@Param("id") Long id);
}
