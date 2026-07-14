package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.TransactionRuleRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRuleEvaluationServiceTest {

    private static final String USER_LOGIN = "user";

    private TransactionRuleRepository transactionRuleRepository;

    private TransactionRuleEvaluationService service;
    private List<TransactionRule> activeRules;

    @BeforeEach
    void setUp() {
        activeRules = List.of();
        transactionRuleRepository = repositoryProxy();
        service = new TransactionRuleEvaluationService(transactionRuleRepository);
    }

    @Test
    void ignoresInactiveRules() {
        TransactionRule rule = rule(1L, 0, false, RuleConditionLogic.ALL, category(10L, "Food"));
        rule.setConditions(Set.of(condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee")));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee shop").build());

        assertThat(result.matchedRules()).isEmpty();
        assertThat(result.suggestedCategory()).isNull();
    }

    @Test
    void ignoresRulesWithoutConditions() {
        TransactionRule rule = rule(1L, 0, true, RuleConditionLogic.ALL, category(10L, "Food"));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee shop").build());

        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void ignoresRulesWithoutOutputsDefensively() {
        TransactionRule rule = rule(1L, 0, true, RuleConditionLogic.ALL, null);
        rule.setConditions(Set.of(condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee")));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee shop").build());

        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void evaluatesRulesByPriorityAscThenIdAsc() {
        TransactionRule firstById = matchingCategoryRule(2L, 0, 20L, "B");
        TransactionRule secondById = matchingCategoryRule(1L, 0, 10L, "A");
        TransactionRule laterPriority = matchingCategoryRule(3L, 1, 30L, "C");
        givenRules(laterPriority, firstById, secondById);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.matchedRules()).extracting(RuleMatchResult::ruleId).containsExactly(1L, 2L, 3L);
        assertThat(result.suggestedCategory().categoryId()).isEqualTo(10L);
    }

    @Test
    void allRequiresAllConditions() {
        TransactionRule rule = rule(1L, 0, true, RuleConditionLogic.ALL, category(10L, "Food"));
        rule.setConditions(
            Set.of(
                condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee"),
                condition(2L, 1, TransactionRuleField.AMOUNT, RuleOperator.GREATER_THAN, "100")
            )
        );
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").amount(new BigDecimal("20")).build());

        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void anyRequiresAtLeastOneCondition() {
        TransactionRule rule = rule(1L, 0, true, RuleConditionLogic.ANY, category(10L, "Food"));
        rule.setConditions(
            Set.of(
                condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee"),
                condition(2L, 1, TransactionRuleField.AMOUNT, RuleOperator.GREATER_THAN, "100")
            )
        );
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").amount(new BigDecimal("20")).build());

        assertThat(result.matchedRules()).hasSize(1);
    }

    @Test
    void descriptionContainsMatches() {
        assertSingleConditionMatches(
            TransactionRuleField.DESCRIPTION,
            RuleOperator.CONTAINS,
            "coffee",
            input().description("morning coffee").build()
        );
    }

    @Test
    void descriptionCaseInsensitiveMatchingWorks() {
        TransactionRuleCondition condition = condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.EQUALS, "Coffee");
        condition.setCaseSensitive(false);
        TransactionRule rule = ruleWithCondition(condition);
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.matchedRules()).hasSize(1);
    }

    @Test
    void descriptionCaseSensitiveMismatchFails() {
        TransactionRuleCondition condition = condition(1L, 0, TransactionRuleField.DESCRIPTION, RuleOperator.EQUALS, "Coffee");
        condition.setCaseSensitive(true);
        TransactionRule rule = ruleWithCondition(condition);
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void amountGreaterThanMatches() {
        assertSingleConditionMatches(
            TransactionRuleField.AMOUNT,
            RuleOperator.GREATER_THAN,
            "10",
            input().amount(new BigDecimal("10.01")).build()
        );
    }

    @Test
    void amountBetweenMatches() {
        TransactionRuleCondition condition = condition(1L, 0, TransactionRuleField.AMOUNT, RuleOperator.BETWEEN, "10");
        condition.setSecondValue("20");
        TransactionRule rule = ruleWithCondition(condition);
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().amount(new BigDecimal("15")).build());

        assertThat(result.matchedRules()).hasSize(1);
    }

    @Test
    void flowEqualsMatches() {
        assertSingleConditionMatches(TransactionRuleField.FLOW, RuleOperator.EQUALS, "OUT", input().flow(TransactionFlow.OUT).build());
    }

    @Test
    void originInMatches() {
        assertSingleConditionMatches(
            TransactionRuleField.ORIGIN,
            RuleOperator.IN,
            "MANUAL,API",
            input().origin(TransactionOrigin.API).build()
        );
    }

    @Test
    void accountEqualsMatches() {
        assertSingleConditionMatches(TransactionRuleField.ACCOUNT, RuleOperator.EQUALS, "42", input().accountId(42L).build());
    }

    @Test
    void transactionDateBeforeAfterBetweenWorks() {
        assertSingleConditionMatches(
            TransactionRuleField.TRANSACTION_DATE,
            RuleOperator.BEFORE,
            "2026-07-15",
            input().transactionDate(LocalDate.parse("2026-07-14")).build()
        );
        assertSingleConditionMatches(
            TransactionRuleField.TRANSACTION_DATE,
            RuleOperator.AFTER,
            "2026-07-13",
            input().transactionDate(LocalDate.parse("2026-07-14")).build()
        );

        TransactionRuleCondition condition = condition(1L, 0, TransactionRuleField.TRANSACTION_DATE, RuleOperator.BETWEEN, "2026-07-01");
        condition.setSecondValue("2026-07-31");
        TransactionRule rule = ruleWithCondition(condition);
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().transactionDate(LocalDate.parse("2026-07-14")).build());

        assertThat(result.matchedRules()).hasSize(1);
    }

    @Test
    void nullOptionalFieldDoesNotMatchIncludingNegativeOperators() {
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.DESCRIPTION,
            RuleOperator.NOT_EQUALS,
            "coffee",
            input().description(null).build()
        );
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.EXTERNAL_REFERENCE,
            RuleOperator.NOT_IN,
            "abc,def",
            input().externalReference(null).build()
        );
    }

    @Test
    void externalReferenceNullDoesNotMatchIncludingNotEqualsAndNotIn() {
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.EXTERNAL_REFERENCE,
            RuleOperator.NOT_EQUALS,
            "abc",
            input().externalReference(null).build()
        );
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.EXTERNAL_REFERENCE,
            RuleOperator.NOT_IN,
            "abc,def",
            input().externalReference(null).build()
        );
    }

    @Test
    void postingDateNullDoesNotMatchDateOperators() {
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.POSTING_DATE,
            RuleOperator.BEFORE,
            "2026-07-15",
            input().postingDate(null).build()
        );
        assertSingleConditionDoesNotMatch(
            TransactionRuleField.POSTING_DATE,
            RuleOperator.AFTER,
            "2026-07-13",
            input().postingDate(null).build()
        );

        TransactionRuleCondition condition = condition(1L, 0, TransactionRuleField.POSTING_DATE, RuleOperator.BETWEEN, "2026-07-01");
        condition.setSecondValue("2026-07-31");
        TransactionRule rule = ruleWithCondition(condition);
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().postingDate(null).build());

        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void categoryFirstMatchingOutputWinsAndLaterCategoryIsSkipped() {
        TransactionRule first = matchingCategoryRule(1L, 0, 10L, "Food");
        TransactionRule later = matchingCategoryRule(2L, 1, 20L, "Transport");
        givenRules(later, first);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.suggestedCategory().categoryId()).isEqualTo(10L);
        assertThat(result.skippedOutputs()).anySatisfy(skip -> {
            assertThat(skip.field()).isEqualTo(RuleOutputField.CATEGORY);
            assertThat(skip.reason()).isEqualTo(RuleOutputSkipReason.CATEGORY_ALREADY_SUGGESTED_BY_HIGHER_PRIORITY_RULE);
            assertThat(skip.valueId()).isEqualTo(20L);
        });
    }

    @Test
    void currentCategorySameIdCreatesNoConflict() {
        TransactionRule rule = matchingCategoryRule(1L, 0, 10L, "Food");
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(
            input().description("coffee").currentCategoryId(10L).currentCategoryName("Food").build()
        );

        assertThat(result.suggestedCategory().conflictsWithCurrentValue()).isFalse();
        assertThat(result.conflicts()).isEmpty();
        assertThat(result.hasConflicts()).isFalse();
    }

    @Test
    void currentCategoryDifferentIdCreatesConflictAndDoesNotOverride() {
        TransactionRule rule = matchingCategoryRule(1L, 0, 10L, "Food");
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(
            input().description("coffee").currentCategoryId(99L).currentCategoryName("Manual").build()
        );

        assertThat(result.suggestedCategory().categoryId()).isEqualTo(10L);
        assertThat(result.suggestedCategory().conflictsWithCurrentValue()).isTrue();
        assertThat(result.conflicts())
            .singleElement()
            .satisfies(conflict -> {
                assertThat(conflict.field()).isEqualTo(RuleOutputField.CATEGORY);
                assertThat(conflict.reason()).isEqualTo(RuleOutputConflictReason.CATEGORY_CONFLICTS_WITH_EXPLICIT_VALUE);
                assertThat(conflict.currentValueId()).isEqualTo(99L);
                assertThat(conflict.suggestedValueId()).isEqualTo(10L);
            });
        assertThat(result.hasConflicts()).isTrue();
    }

    @Test
    void tagsAccumulateAcrossMultipleMatchingRules() {
        TransactionRule first = matchingTagRule(1L, 0, tag(10L, "Coffee"));
        TransactionRule second = matchingTagRule(2L, 1, tag(20L, "Work"));
        givenRules(first, second);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.suggestedTags()).extracting(TagSuggestion::tagId).containsExactly(10L, 20L);
        assertThat(result.hasSuggestions()).isTrue();
    }

    @Test
    void duplicateTagFromLaterMatchingRuleIsSkipped() {
        TransactionRule first = matchingTagRule(1L, 0, tag(10L, "Coffee"));
        TransactionRule second = matchingTagRule(2L, 1, tag(10L, "Coffee"));
        givenRules(first, second);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").build());

        assertThat(result.suggestedTags()).extracting(TagSuggestion::tagId).containsExactly(10L);
        assertThat(result.skippedOutputs()).anySatisfy(skip -> {
            assertThat(skip.field()).isEqualTo(RuleOutputField.TAGS);
            assertThat(skip.reason()).isEqualTo(RuleOutputSkipReason.TAG_ALREADY_SUGGESTED);
            assertThat(skip.valueId()).isEqualTo(10L);
        });
    }

    @Test
    void existingTagIsReturnedWithAlreadyPresentTrueAndSkipped() {
        TransactionRule rule = matchingTagRule(1L, 0, tag(10L, "Coffee"));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(
            input().description("coffee").currentTagIds(Set.of(10L)).currentTagNames(Map.of(10L, "Existing Coffee")).build()
        );

        assertThat(result.suggestedTags())
            .singleElement()
            .satisfies(tag -> {
                assertThat(tag.tagId()).isEqualTo(10L);
                assertThat(tag.alreadyPresent()).isTrue();
                assertThat(tag.duplicateOfEarlierSuggestion()).isFalse();
            });
        assertThat(result.skippedOutputs()).anySatisfy(skip -> {
            assertThat(skip.reason()).isEqualTo(RuleOutputSkipReason.TAG_ALREADY_PRESENT);
            assertThat(skip.valueLabel()).isEqualTo("Existing Coffee");
        });
    }

    @Test
    void alreadyPresentTagIsNotTreatedAsNewTagToAdd() {
        TransactionRule rule = matchingTagRule(1L, 0, tag(10L, "Coffee"));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input().description("coffee").currentTagIds(Set.of(10L)).build());

        assertThat(result.hasSuggestions()).isFalse();
        assertThat(result.suggestedTags()).singleElement().satisfies(tag -> assertThat(tag.alreadyPresent()).isTrue());
    }

    private void assertSingleConditionMatches(
        TransactionRuleField field,
        RuleOperator operator,
        String value,
        TransactionRuleEvaluationInput input
    ) {
        TransactionRule rule = ruleWithCondition(condition(1L, 0, field, operator, value));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input);

        assertThat(result.matchedRules()).hasSize(1);
    }

    private void assertSingleConditionDoesNotMatch(
        TransactionRuleField field,
        RuleOperator operator,
        String value,
        TransactionRuleEvaluationInput input
    ) {
        TransactionRule rule = ruleWithCondition(condition(1L, 0, field, operator, value));
        givenRules(rule);

        TransactionRuleEvaluationResult result = service.evaluate(input);

        assertThat(result.matchedRules()).isEmpty();
    }

    private TransactionRule ruleWithCondition(TransactionRuleCondition condition) {
        TransactionRule rule = rule(1L, 0, true, RuleConditionLogic.ALL, category(10L, "Matched"));
        rule.setConditions(Set.of(condition));
        return rule;
    }

    private TransactionRule matchingCategoryRule(Long id, Integer priority, Long categoryId, String categoryName) {
        TransactionRule rule = rule(id, priority, true, RuleConditionLogic.ALL, category(categoryId, categoryName));
        rule.setConditions(Set.of(condition(id * 10, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee")));
        return rule;
    }

    private TransactionRule matchingTagRule(Long id, Integer priority, Tag tag) {
        TransactionRule rule = rule(id, priority, true, RuleConditionLogic.ALL, null);
        rule.setResultingTags(new HashSet<>(Set.of(tag)));
        rule.setConditions(Set.of(condition(id * 10, 0, TransactionRuleField.DESCRIPTION, RuleOperator.CONTAINS, "coffee")));
        return rule;
    }

    private TransactionRule rule(Long id, Integer priority, Boolean active, RuleConditionLogic logic, Category category) {
        TransactionRule rule = new TransactionRule();
        rule.setId(id);
        rule.setName("Rule " + id);
        rule.setPriority(priority);
        rule.setActive(active);
        rule.setConditionLogic(logic);
        rule.setResultingCategory(category);
        return rule;
    }

    private TransactionRuleCondition condition(Long id, Integer position, TransactionRuleField field, RuleOperator operator, String value) {
        TransactionRuleCondition condition = new TransactionRuleCondition();
        condition.setId(id);
        condition.setPosition(position);
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        condition.setCaseSensitive(false);
        return condition;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private InputBuilder input() {
        return new InputBuilder();
    }

    private void givenRules(TransactionRule... rules) {
        activeRules = List.of(rules);
    }

    private TransactionRuleRepository repositoryProxy() {
        return (TransactionRuleRepository) Proxy.newProxyInstance(
            TransactionRuleRepository.class.getClassLoader(),
            new Class<?>[] { TransactionRuleRepository.class },
            (proxy, method, args) -> {
                if ("findActiveRulesForEvaluationByUserLoginOrderByPriorityAscIdAsc".equals(method.getName())) {
                    assertThat(args).containsExactly(USER_LOGIN);
                    return activeRules;
                }
                throw new UnsupportedOperationException("Unexpected repository method in pure evaluator test: " + method.getName());
            }
        );
    }

    private static final class InputBuilder {

        private String description = "coffee";
        private BigDecimal amount = new BigDecimal("25");
        private TransactionFlow flow = TransactionFlow.OUT;
        private String externalReference = "abc";
        private TransactionOrigin origin = TransactionOrigin.MANUAL;
        private LocalDate transactionDate = LocalDate.parse("2026-07-14");
        private LocalDate postingDate = LocalDate.parse("2026-07-14");
        private Long accountId = 1L;
        private Long currentCategoryId;
        private String currentCategoryName;
        private Set<Long> currentTagIds = Set.of();
        private Map<Long, String> currentTagNames = Map.of();

        private InputBuilder description(String description) {
            this.description = description;
            return this;
        }

        private InputBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        private InputBuilder flow(TransactionFlow flow) {
            this.flow = flow;
            return this;
        }

        private InputBuilder origin(TransactionOrigin origin) {
            this.origin = origin;
            return this;
        }

        private InputBuilder transactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        private InputBuilder postingDate(LocalDate postingDate) {
            this.postingDate = postingDate;
            return this;
        }

        private InputBuilder accountId(Long accountId) {
            this.accountId = accountId;
            return this;
        }

        private InputBuilder externalReference(String externalReference) {
            this.externalReference = externalReference;
            return this;
        }

        private InputBuilder currentCategoryId(Long currentCategoryId) {
            this.currentCategoryId = currentCategoryId;
            return this;
        }

        private InputBuilder currentCategoryName(String currentCategoryName) {
            this.currentCategoryName = currentCategoryName;
            return this;
        }

        private InputBuilder currentTagIds(Set<Long> currentTagIds) {
            this.currentTagIds = currentTagIds;
            return this;
        }

        private InputBuilder currentTagNames(Map<Long, String> currentTagNames) {
            this.currentTagNames = currentTagNames;
            return this;
        }

        private TransactionRuleEvaluationInput build() {
            return new TransactionRuleEvaluationInput(
                USER_LOGIN,
                description,
                amount,
                flow,
                externalReference,
                origin,
                transactionDate,
                postingDate,
                accountId,
                currentCategoryId,
                currentCategoryName,
                currentTagIds,
                currentTagNames
            );
        }
    }
}
