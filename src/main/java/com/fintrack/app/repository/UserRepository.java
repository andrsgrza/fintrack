package com.fintrack.app.repository;

import com.fintrack.app.domain.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findOneByActivationKey(String activationKey);
    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant dateTime);
    Optional<User> findOneByResetKey(String resetKey);
    Optional<User> findOneByEmailIgnoreCase(String email);
    Optional<User> findOneByLogin(String login);

    /** Serializes owner-scoped creation commands before their idempotency key is checked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.login = :login")
    Optional<User> findOneForBudgetCreationByLogin(@Param("login") String login);

    /**
     * Serializes an owner's configured financial mutations with preference
     * changes and threshold capture. This is the first lock in the documented
     * notification lock order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.login = :login")
    Optional<User> findOneForBudgetNotificationMutationByLogin(@Param("login") String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);
}
