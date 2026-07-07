package com.fintrack.app.repository;

import com.fintrack.app.domain.CreditAccountDetails;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the CreditAccountDetails entity.
 */
@Repository
public interface CreditAccountDetailsRepository extends JpaRepository<CreditAccountDetails, Long> {
    default Optional<CreditAccountDetails> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<CreditAccountDetails> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<CreditAccountDetails> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account",
        countQuery = "select count(creditAccountDetails) from CreditAccountDetails creditAccountDetails"
    )
    Page<CreditAccountDetails> findAllWithToOneRelationships(Pageable pageable);

    @Query("select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account")
    List<CreditAccountDetails> findAllWithToOneRelationships();

    @Query(
        "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account where creditAccountDetails.id =:id"
    )
    Optional<CreditAccountDetails> findOneWithToOneRelationships(@Param("id") Long id);
}
