package com.fintrack.app.repository;

import com.fintrack.app.domain.PersonalDashboardPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Holds exactly one authoritative default-dashboard preference per owner. */
@Repository
public interface PersonalDashboardPreferenceRepository extends JpaRepository<PersonalDashboardPreference, Long> {
    @EntityGraph(attributePaths = { "user", "defaultDashboard", "defaultDashboard.user" })
    Optional<PersonalDashboardPreference> findByUserId(Long userId);
}
