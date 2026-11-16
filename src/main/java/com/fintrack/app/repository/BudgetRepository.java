package com.fintrack.app.repository;

import com.fintrack.app.domain.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence boundary for the Budget aggregate, intentionally not a generated CRUD repository. */
@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    @Query("select b from Budget b where b.id = :id and b.user.login = :login")
    Optional<Budget> findOneByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query("select b from Budget b left join fetch b.revisions where b.id = :id and b.user.login = :login")
    Optional<Budget> findOneWithRevisionsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    /**
     * Read aggregate used by Budget financial evaluation. The immutable revision scopes are fetched with the
     * authorized root so evaluating a bounded history does not turn into a query per period or per selected item.
     */
    @Query(
        "select distinct b from Budget b join fetch b.user left join fetch b.revisions revision " +
        "left join fetch revision.accounts left join fetch revision.categories left join fetch revision.tags " +
        "where b.id = :id and b.user.login = :login"
    )
    Optional<Budget> findOneForEvaluationByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    /**
     * Owner-scoped overview read. Scope selections travel with immutable revisions so an overview never needs to
     * recover them through unscoped entity lookups.
     */
    @Query(
        "select distinct b from Budget b join fetch b.user left join fetch b.revisions revision " +
        "left join fetch revision.accounts left join fetch revision.categories left join fetch revision.tags " +
        "where b.user.login = :login order by b.updatedAt desc, b.id desc"
    )
    List<Budget> findAllForOverviewByUserLogin(@Param("login") String login);

    @Query("select b from Budget b left join fetch b.revisions where b.user.login = :login and b.creationRequestId = :requestId")
    Optional<Budget> findOneWithRevisionsByUserLoginAndCreationRequestId(
        @Param("login") String login,
        @Param("requestId") String requestId
    );
}
