package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@WithMockUser
class TransactionRuleEvaluationServiceIT {

    private static final String CURRENT_USER_LOGIN = "user";

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionRuleEvaluationService service;

    @Test
    @Transactional
    void evaluatesPersistedRulesForCurrentUserOnlyWithFetchedOutputsAndConditions() {
        User currentUser = currentUser();
        User otherUser = otherUser();
        Category firstCategory = persistedCategory("Food", currentUser);
        Category laterCategory = persistedCategory("Transport", currentUser);
        Category foreignCategory = persistedCategory("Foreign", otherUser);
        Tag coffeeTag = persistedTag("Coffee", currentUser);
        Tag workTag = persistedTag("Work", currentUser);
        Tag foreignTag = persistedTag("Foreign Tag", otherUser);

        TransactionRule samePriorityLowerIdRule = persistedRule("Same priority lower id", 0, true, currentUser, null, Set.of(workTag));
        persistedCondition(samePriorityLowerIdRule, 0, TransactionRuleField.ORIGIN, RuleOperator.IN, "MANUAL,API");

        TransactionRule firstRule = persistedRule("First category", 0, true, currentUser, firstCategory, Set.of(coffeeTag));
        persistedCondition(firstRule, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee");

        TransactionRule laterRule = persistedRule("Later category", 1, true, currentUser, laterCategory, Set.of(workTag));
        persistedCondition(laterRule, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee");

        TransactionRule inactiveRule = persistedRule(
            "Inactive",
            2,
            false,
            currentUser,
            persistedCategory("Inactive", currentUser),
            Set.of()
        );
        persistedCondition(inactiveRule, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee");

        persistedRule("No conditions", 3, true, currentUser, persistedCategory("No Conditions", currentUser), Set.of());

        TransactionRule foreignRule = persistedRule("Foreign", 0, true, otherUser, foreignCategory, Set.of(foreignTag));
        persistedCondition(foreignRule, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee");

        em.flush();
        em.clear();

        TransactionRuleEvaluationResult result = service.evaluate(input());

        assertThat(result.matchedRules())
            .extracting(RuleMatchResult::ruleName)
            .containsExactly("Same priority lower id", "First category", "Later category");
        assertThat(result.matchedRules()).extracting(RuleMatchResult::ruleName).doesNotContain("Foreign", "Inactive", "No conditions");
        assertThat(result.suggestedCategory()).isNotNull();
        assertThat(result.suggestedCategory().categoryName()).isEqualTo("Food");
        assertThat(result.suggestedTags()).extracting(TagSuggestion::tagName).containsExactly("Work", "Coffee");
        assertThat(result.skippedOutputs()).anySatisfy(skip -> {
            assertThat(skip.field()).isEqualTo(RuleOutputField.CATEGORY);
            assertThat(skip.reason()).isEqualTo(RuleOutputSkipReason.CATEGORY_ALREADY_SUGGESTED_BY_HIGHER_PRIORITY_RULE);
            assertThat(skip.valueLabel()).isEqualTo("Transport");
        });
    }

    private TransactionRuleEvaluationInput input() {
        return new TransactionRuleEvaluationInput(
            CURRENT_USER_LOGIN,
            "coffee shop",
            new BigDecimal("25"),
            TransactionFlow.OUT,
            "external",
            TransactionOrigin.MANUAL,
            LocalDate.parse("2026-07-14"),
            LocalDate.parse("2026-07-14"),
            42L,
            null,
            null,
            Set.of(),
            java.util.Map.of()
        );
    }

    private User currentUser() {
        return TestUtil.findAll(em, User.class)
            .stream()
            .filter(user -> CURRENT_USER_LOGIN.equals(user.getLogin()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Current mock user not found"));
    }

    private User otherUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = new User();
        user.setLogin("other-" + suffix.substring(0, 20));
        user.setPassword("a".repeat(60));
        user.setActivated(true);
        user.setEmail("other-" + suffix + "@localhost");
        user.setFirstName("Other");
        user.setLastName("User");
        user.setLangKey("en");
        em.persist(user);
        return user;
    }

    private Category persistedCategory(String name, User user) {
        Category category = new Category()
            .name(name)
            .description(name + " description")
            .categoryType(CategoryType.BOTH)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user);
        em.persist(category);
        return category;
    }

    private Tag persistedTag(String name, User user) {
        Tag tag = new Tag()
            .name(name)
            .description(name + " description")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user);
        em.persist(tag);
        return tag;
    }

    private TransactionRule persistedRule(String name, Integer priority, Boolean active, User user, Category category, Set<Tag> tags) {
        TransactionRule rule = new TransactionRule()
            .name(name)
            .priority(priority)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(user)
            .resultingCategory(category);
        tags.forEach(rule::addResultingTags);
        em.persist(rule);
        return rule;
    }

    private void persistedCondition(
        TransactionRule rule,
        Integer position,
        TransactionRuleField field,
        RuleOperator operator,
        String value
    ) {
        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(field)
            .operator(operator)
            .value(value)
            .caseSensitive(false)
            .position(position)
            .transactionRule(rule);
        em.persist(condition);
    }
}
