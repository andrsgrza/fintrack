package com.fintrack.app.repository;

import com.fintrack.app.domain.TransactionIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionIngestion entity.
 *
 * When extending this class, extend TransactionIngestionRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface TransactionIngestionRepository
    extends
        TransactionIngestionRepositoryWithBagRelationships,
        JpaRepository<TransactionIngestion, Long>,
        JpaSpecificationExecutor<TransactionIngestion> {
    default Optional<TransactionIngestion> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findById(id));
    }

    default List<TransactionIngestion> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAll());
    }

    default Page<TransactionIngestion> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAll(pageable));
    }
}
