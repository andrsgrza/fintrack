package com.fintrack.app.repository;

import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DescriptionNormalizationRuleConditionRepository
    extends JpaRepository<DescriptionNormalizationRuleCondition, Long>, JpaSpecificationExecutor<DescriptionNormalizationRuleCondition> {
    @Query(
        "select condition from DescriptionNormalizationRuleCondition condition " +
        "left join fetch condition.descriptionNormalizationRule rule " +
        "left join fetch rule.user " +
        "where condition.id = :id"
    )
    Optional<DescriptionNormalizationRuleCondition> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        "select condition from DescriptionNormalizationRuleCondition condition " +
        "left join fetch condition.descriptionNormalizationRule rule " +
        "left join fetch rule.user " +
        "where condition.id = :id and rule.user.login = :login"
    )
    Optional<DescriptionNormalizationRuleCondition> findOneWithToOneRelationshipsByIdAndRuleUserLogin(
        @Param("id") Long id,
        @Param("login") String login
    );

    @Query(
        "select condition from DescriptionNormalizationRuleCondition condition " +
        "left join fetch condition.descriptionNormalizationRule rule " +
        "left join fetch rule.user"
    )
    List<DescriptionNormalizationRuleCondition> findAllWithToOneRelationships();

    @Query(
        "select condition from DescriptionNormalizationRuleCondition condition " +
        "left join fetch condition.descriptionNormalizationRule rule " +
        "left join fetch rule.user " +
        "where rule.user.login = :login"
    )
    List<DescriptionNormalizationRuleCondition> findAllWithToOneRelationshipsByRuleUserLogin(@Param("login") String login);

    List<DescriptionNormalizationRuleCondition> findByDescriptionNormalizationRuleIdOrderByPositionAscIdAsc(Long ruleId);

    long countByDescriptionNormalizationRuleId(Long ruleId);

    @Query(
        "select max(condition.position) from DescriptionNormalizationRuleCondition condition " +
        "where condition.descriptionNormalizationRule.id = :ruleId"
    )
    Integer findMaxPositionByDescriptionNormalizationRuleId(@Param("ruleId") Long ruleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DescriptionNormalizationRuleCondition condition where condition.descriptionNormalizationRule.id = :ruleId")
    void deleteByDescriptionNormalizationRuleId(@Param("ruleId") Long ruleId);

    @Query(
        "select condition from DescriptionNormalizationRuleCondition condition " +
        "where condition.descriptionNormalizationRule.id = :ruleId " +
        "and condition.operator = :operator " +
        "and condition.caseSensitive = :caseSensitive " +
        "and lower(trim(condition.value)) = lower(trim(:value)) " +
        "and (:excludeId is null or condition.id <> :excludeId)"
    )
    List<DescriptionNormalizationRuleCondition> findPotentialDuplicates(
        @Param("ruleId") Long ruleId,
        @Param("operator") DescriptionNormalizationRuleOperator operator,
        @Param("caseSensitive") Boolean caseSensitive,
        @Param("value") String value,
        @Param("excludeId") Long excludeId
    );
}
