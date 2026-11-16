package com.fintrack.app.repository;

import com.fintrack.app.domain.Budget;
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

    @Query("select b from Budget b left join fetch b.revisions where b.user.login = :login and b.creationRequestId = :requestId")
    Optional<Budget> findOneWithRevisionsByUserLoginAndCreationRequestId(
        @Param("login") String login,
        @Param("requestId") String requestId
    );
}
