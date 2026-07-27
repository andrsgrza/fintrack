package com.fintrack.app.repository;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DescriptionNormalizationRuleRepository
    extends JpaRepository<DescriptionNormalizationRule, Long>, JpaSpecificationExecutor<DescriptionNormalizationRule> {
    @Query("select rule from DescriptionNormalizationRule rule where rule.user.login = ?#{authentication.name}")
    List<DescriptionNormalizationRule> findByUserIsCurrentUser();

    @Query("select rule from DescriptionNormalizationRule rule left join fetch rule.user where rule.id = :id")
    Optional<DescriptionNormalizationRule> findOneWithToOneRelationships(@Param("id") Long id);

    @Query("select rule from DescriptionNormalizationRule rule left join fetch rule.user where rule.id = :id and rule.user.login = :login")
    Optional<DescriptionNormalizationRule> findOneWithToOneRelationshipsByIdAndUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    @Query(
        value = "select rule from DescriptionNormalizationRule rule left join fetch rule.user",
        countQuery = "select count(rule) from DescriptionNormalizationRule rule"
    )
    Page<DescriptionNormalizationRule> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        value = "select rule from DescriptionNormalizationRule rule left join fetch rule.user where rule.user.login = :login",
        countQuery = "select count(rule) from DescriptionNormalizationRule rule where rule.user.login = :login"
    )
    Page<DescriptionNormalizationRule> findAllWithToOneRelationshipsByUserLogin(@Param("login") String login, Pageable pageable);

    @Query(
        "select count(rule) > 0 from DescriptionNormalizationRule rule " +
        "where rule.user.login = :login " +
        "and lower(trim(rule.name)) = lower(:normalizedName) " +
        "and (:excludeId is null or rule.id <> :excludeId)"
    )
    boolean existsByUserLoginAndNormalizedName(
        @Param("login") String login,
        @Param("normalizedName") String normalizedName,
        @Param("excludeId") Long excludeId
    );

    @Query("select max(rule.priority) from DescriptionNormalizationRule rule where rule.user.id = :userId")
    Integer findMaxPriorityByUserId(@Param("userId") Long userId);

    List<DescriptionNormalizationRule> findByUserIdOrderByPriorityAscIdAsc(Long userId);

    @EntityGraph(attributePaths = { "user", "conditions" })
    @Query(
        "select distinct rule from DescriptionNormalizationRule rule " +
        "where rule.user.login = :login and rule.active = true " +
        "order by rule.priority asc, rule.id asc"
    )
    List<DescriptionNormalizationRule> findActiveRulesForEvaluationByUserLoginOrderByPriorityAscIdAsc(@Param("login") String login);
}
