package com.fintrack.app.repository;

import com.fintrack.app.domain.PersonalDashboardRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Looks up durable idempotency receipts only in the authenticated owner's scope. */
@Repository
public interface PersonalDashboardRequestRepository extends JpaRepository<PersonalDashboardRequest, Long> {
    @Query(
        "select request from PersonalDashboardRequest request where request.user.id = :userId and request.scope = :scope and request.clientRequestId = :clientRequestId"
    )
    Optional<PersonalDashboardRequest> findByOwnerScopeAndClientRequestId(
        @Param("userId") Long userId,
        @Param("scope") String scope,
        @Param("clientRequestId") String clientRequestId
    );
}
