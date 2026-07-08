package com.fintrack.app.repository;

import com.fintrack.app.domain.FinancialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FinancialAccount entity.
 */
@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long>, JpaSpecificationExecutor<FinancialAccount> {
    @Query("select financialAccount from FinancialAccount financialAccount where financialAccount.user.login = ?#{authentication.name}")
    List<FinancialAccount> findByUserIsCurrentUser();

    @Query(
        "select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user where financialAccount.id = :id and financialAccount.user.login = :login"
    )
    Optional<FinancialAccount> findOneWithToOneRelationshipsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query(
        value = "select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user where financialAccount.user.login = :login",
        countQuery = "select count(financialAccount) from FinancialAccount financialAccount where financialAccount.user.login = :login"
    )
    Page<FinancialAccount> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query(
        "select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user where financialAccount.user.login = :login"
    )
    List<FinancialAccount> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login);

    boolean existsByIdAndUser_Login(Long id, String login);

    default Optional<FinancialAccount> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<FinancialAccount> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<FinancialAccount> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user",
        countQuery = "select count(financialAccount) from FinancialAccount financialAccount"
    )
    Page<FinancialAccount> findAllWithToOneRelationships(Pageable pageable);

    @Query("select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user")
    List<FinancialAccount> findAllWithToOneRelationships();

    @Query(
        "select financialAccount from FinancialAccount financialAccount left join fetch financialAccount.user where financialAccount.id =:id"
    )
    Optional<FinancialAccount> findOneWithToOneRelationships(@Param("id") Long id);
}
