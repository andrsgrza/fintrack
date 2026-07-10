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
    boolean existsByAccountId(Long accountId);

    default Optional<CreditAccountDetails> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default Optional<CreditAccountDetails> findOneWithEagerRelationshipsByIdAndAccountUserLogin(Long id, String login) {
        return this.findOneWithToOneRelationshipsByIdAndAccountUserLogin(id, login);
    }

    default List<CreditAccountDetails> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<CreditAccountDetails> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    default List<CreditAccountDetails> findAllWithEagerRelationshipsByAccountUserLogin(String login) {
        return this.findAllWithToOneRelationshipsByAccountUserLogin(login);
    }

    @Query(
        value = "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account account left join fetch account.user",
        countQuery = "select count(creditAccountDetails) from CreditAccountDetails creditAccountDetails"
    )
    Page<CreditAccountDetails> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account account left join fetch account.user"
    )
    List<CreditAccountDetails> findAllWithToOneRelationships();

    @Query(
        "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account account left join fetch account.user where creditAccountDetails.id =:id"
    )
    Optional<CreditAccountDetails> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account account left join fetch account.user where creditAccountDetails.id = :id and account.user.login = :login"
    )
    Optional<CreditAccountDetails> findOneWithToOneRelationshipsByIdAndAccountUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        "select creditAccountDetails from CreditAccountDetails creditAccountDetails left join fetch creditAccountDetails.account account left join fetch account.user where account.user.login = :login"
    )
    List<CreditAccountDetails> findAllWithToOneRelationshipsByAccountUserLogin(@Param("login") String login);
}
