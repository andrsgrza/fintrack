package com.fintrack.app.repository;

import com.fintrack.app.domain.PersonalDashboard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Owner-scoped repository for the personal dashboard aggregate. */
@Repository
public interface PersonalDashboardRepository extends JpaRepository<PersonalDashboard, Long> {
    @EntityGraph(attributePaths = { "user" })
    @Query(
        "select dashboard from PersonalDashboard dashboard where dashboard.user.login = :login order by dashboard.updatedAt desc, dashboard.id desc"
    )
    List<PersonalDashboard> findAllByUserLogin(@Param("login") String login);

    @EntityGraph(attributePaths = { "user", "widgets" })
    @Query(
        "select distinct dashboard from PersonalDashboard dashboard left join fetch dashboard.widgets where dashboard.id = :id and dashboard.user.login = :login"
    )
    Optional<PersonalDashboard> findOneWithWidgetsByIdAndUserLogin(@Param("id") Long id, @Param("login") String login);

    @Query("select count(dashboard) from PersonalDashboard dashboard where dashboard.user.login = :login")
    long countByUserLogin(@Param("login") String login);

    boolean existsByUserLoginAndNormalizedName(String login, String normalizedName);
}
