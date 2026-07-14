package com.fintrack.app.service.rules;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionRuleEvaluationService {

    private final TransactionRuleRepository transactionRuleRepository;

    public TransactionRuleEvaluationService(TransactionRuleRepository transactionRuleRepository) {
        this.transactionRuleRepository = transactionRuleRepository;
    }

    public TransactionRuleEvaluationResult evaluate(TransactionRuleEvaluationInput input) {
        Objects.requireNonNull(input, "input must not be null");

        List<TransactionRule> rules = transactionRuleRepository.findActiveRulesForEvaluationByUserLoginOrderByPriorityAscIdAsc(
            input.userLogin()
        );

        List<RuleMatchResult> matchedRules = new ArrayList<>();
        List<TagSuggestion> suggestedTags = new ArrayList<>();
        List<RuleOutputConflict> conflicts = new ArrayList<>();
        List<SkippedRuleOutput> skippedOutputs = new ArrayList<>();
        Set<Long> suggestedTagIds = new LinkedHashSet<>();
        CategorySuggestion suggestedCategory = null;

        for (TransactionRule rule : sortedRules(rules)) {
            List<TransactionRuleCondition> conditions = sortedConditions(rule);
            Set<RuleOutputField> proposedOutputs = proposedOutputs(rule);

            if (
                !Boolean.TRUE.equals(rule.getActive()) ||
                conditions.isEmpty() ||
                proposedOutputs.isEmpty() ||
                !matches(rule, conditions, input)
            ) {
                continue;
            }

            matchedRules.add(
                new RuleMatchResult(rule.getId(), rule.getName(), rule.getPriority(), rule.getConditionLogic(), proposedOutputs)
            );

            if (rule.getResultingCategory() != null) {
                Category category = rule.getResultingCategory();
                if (suggestedCategory == null) {
                    boolean conflictsWithCurrentValue =
                        input.currentCategoryId() != null && !input.currentCategoryId().equals(category.getId());
                    suggestedCategory = new CategorySuggestion(
                        category.getId(),
                        category.getName(),
                        rule.getId(),
                        rule.getName(),
                        conflictsWithCurrentValue,
                        input.currentCategoryId(),
                        input.currentCategoryName()
                    );
                    if (conflictsWithCurrentValue) {
                        conflicts.add(
                            new RuleOutputConflict(
                                RuleOutputField.CATEGORY,
                                input.currentCategoryId(),
                                input.currentCategoryName(),
                                category.getId(),
                                category.getName(),
                                rule.getId(),
                                rule.getName(),
                                RuleOutputConflictReason.CATEGORY_CONFLICTS_WITH_EXPLICIT_VALUE
                            )
                        );
                        skippedOutputs.add(
                            new SkippedRuleOutput(
                                RuleOutputField.CATEGORY,
                                rule.getId(),
                                rule.getName(),
                                RuleOutputSkipReason.CATEGORY_CONFLICTS_WITH_EXPLICIT_VALUE,
                                category.getId(),
                                category.getName()
                            )
                        );
                    }
                } else {
                    skippedOutputs.add(
                        new SkippedRuleOutput(
                            RuleOutputField.CATEGORY,
                            rule.getId(),
                            rule.getName(),
                            RuleOutputSkipReason.CATEGORY_ALREADY_SUGGESTED_BY_HIGHER_PRIORITY_RULE,
                            category.getId(),
                            category.getName()
                        )
                    );
                }
            }

            for (Tag tag : sortedTags(rule)) {
                if (tag.getId() == null) {
                    skippedOutputs.add(
                        new SkippedRuleOutput(
                            RuleOutputField.TAGS,
                            rule.getId(),
                            rule.getName(),
                            RuleOutputSkipReason.OUTPUT_INVALID_FOR_TRANSACTION,
                            null,
                            tag.getName()
                        )
                    );
                    continue;
                }

                if (input.currentTagIds().contains(tag.getId())) {
                    suggestedTags.add(new TagSuggestion(tag.getId(), tag.getName(), rule.getId(), rule.getName(), true, false));
                    skippedOutputs.add(
                        new SkippedRuleOutput(
                            RuleOutputField.TAGS,
                            rule.getId(),
                            rule.getName(),
                            RuleOutputSkipReason.TAG_ALREADY_PRESENT,
                            tag.getId(),
                            tagName(input, tag)
                        )
                    );
                    continue;
                }

                if (!suggestedTagIds.add(tag.getId())) {
                    skippedOutputs.add(
                        new SkippedRuleOutput(
                            RuleOutputField.TAGS,
                            rule.getId(),
                            rule.getName(),
                            RuleOutputSkipReason.TAG_ALREADY_SUGGESTED,
                            tag.getId(),
                            tag.getName()
                        )
                    );
                    continue;
                }

                suggestedTags.add(new TagSuggestion(tag.getId(), tag.getName(), rule.getId(), rule.getName(), false, false));
            }
        }

        return new TransactionRuleEvaluationResult(matchedRules, suggestedCategory, suggestedTags, conflicts, skippedOutputs);
    }

    private List<TransactionRule> sortedRules(List<TransactionRule> rules) {
        return rules
            .stream()
            .sorted(
                Comparator.comparing(TransactionRule::getPriority, Comparator.nullsLast(Integer::compareTo)).thenComparing(
                    TransactionRule::getId,
                    Comparator.nullsLast(Long::compareTo)
                )
            )
            .toList();
    }

    private List<TransactionRuleCondition> sortedConditions(TransactionRule rule) {
        if (rule.getConditions() == null) {
            return List.of();
        }
        return rule
            .getConditions()
            .stream()
            .sorted(
                Comparator.comparing(TransactionRuleCondition::getPosition, Comparator.nullsLast(Integer::compareTo)).thenComparing(
                    TransactionRuleCondition::getId,
                    Comparator.nullsLast(Long::compareTo)
                )
            )
            .toList();
    }

    private List<Tag> sortedTags(TransactionRule rule) {
        if (rule.getResultingTags() == null) {
            return List.of();
        }
        return rule.getResultingTags().stream().sorted(Comparator.comparing(Tag::getId, Comparator.nullsLast(Long::compareTo))).toList();
    }

    private Set<RuleOutputField> proposedOutputs(TransactionRule rule) {
        Set<RuleOutputField> outputs = EnumSet.noneOf(RuleOutputField.class);
        if (rule.getResultingCategory() != null) {
            outputs.add(RuleOutputField.CATEGORY);
        }
        if (rule.getResultingTags() != null && !rule.getResultingTags().isEmpty()) {
            outputs.add(RuleOutputField.TAGS);
        }
        return outputs;
    }

    private boolean matches(TransactionRule rule, List<TransactionRuleCondition> conditions, TransactionRuleEvaluationInput input) {
        if (rule.getConditionLogic() == RuleConditionLogic.ANY) {
            return conditions.stream().anyMatch(condition -> matches(condition, input));
        }
        return conditions.stream().allMatch(condition -> matches(condition, input));
    }

    private boolean matches(TransactionRuleCondition condition, TransactionRuleEvaluationInput input) {
        return switch (condition.getField()) {
            case DESCRIPTION -> matchesText(input.description(), condition);
            case EXTERNAL_REFERENCE -> matchesText(input.externalReference(), condition);
            case AMOUNT -> matchesAmount(input.amount(), condition);
            case FLOW -> matchesEnum(input.flow(), condition);
            case ORIGIN -> matchesEnum(input.origin(), condition);
            case TRANSACTION_DATE -> matchesDate(input.transactionDate(), condition);
            case POSTING_DATE -> matchesDate(input.postingDate(), condition);
            case ACCOUNT -> matchesAccount(input.accountId(), condition);
        };
    }

    private boolean matchesText(String actual, TransactionRuleCondition condition) {
        if (isBlank(actual)) {
            return false;
        }

        String expected = trimRequired(condition.getValue());
        String actualValue = actual.trim();
        boolean caseSensitive = Boolean.TRUE.equals(condition.getCaseSensitive());

        return switch (condition.getOperator()) {
            case EQUALS -> normalize(actualValue, caseSensitive).equals(normalize(expected, caseSensitive));
            case NOT_EQUALS -> !normalize(actualValue, caseSensitive).equals(normalize(expected, caseSensitive));
            case CONTAINS -> normalize(actualValue, caseSensitive).contains(normalize(expected, caseSensitive));
            case NOT_CONTAINS -> !normalize(actualValue, caseSensitive).contains(normalize(expected, caseSensitive));
            case STARTS_WITH -> normalize(actualValue, caseSensitive).startsWith(normalize(expected, caseSensitive));
            case ENDS_WITH -> normalize(actualValue, caseSensitive).endsWith(normalize(expected, caseSensitive));
            case REGEX -> regexMatches(actualValue, expected, caseSensitive);
            case IN -> tokens(condition.getValue())
                .stream()
                .anyMatch(token -> normalize(actualValue, caseSensitive).equals(normalize(token, caseSensitive)));
            case NOT_IN -> tokens(condition.getValue())
                .stream()
                .noneMatch(token -> normalize(actualValue, caseSensitive).equals(normalize(token, caseSensitive)));
            default -> false;
        };
    }

    private boolean regexMatches(String actual, String regex, boolean caseSensitive) {
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(regex, flags).matcher(actual).find();
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid stored transaction rule regex", e);
        }
    }

    private boolean matchesAmount(BigDecimal actual, TransactionRuleCondition condition) {
        if (actual == null) {
            return false;
        }
        BigDecimal expected = new BigDecimal(trimRequired(condition.getValue()));
        return switch (condition.getOperator()) {
            case EQUALS -> actual.compareTo(expected) == 0;
            case NOT_EQUALS -> actual.compareTo(expected) != 0;
            case GREATER_THAN -> actual.compareTo(expected) > 0;
            case GREATER_THAN_OR_EQUAL -> actual.compareTo(expected) >= 0;
            case LESS_THAN -> actual.compareTo(expected) < 0;
            case LESS_THAN_OR_EQUAL -> actual.compareTo(expected) <= 0;
            case BETWEEN -> {
                BigDecimal upper = new BigDecimal(trimRequired(condition.getSecondValue()));
                yield actual.compareTo(expected) >= 0 && actual.compareTo(upper) <= 0;
            }
            case IN -> tokens(condition.getValue()).stream().map(BigDecimal::new).anyMatch(token -> actual.compareTo(token) == 0);
            case NOT_IN -> tokens(condition.getValue()).stream().map(BigDecimal::new).noneMatch(token -> actual.compareTo(token) == 0);
            default -> false;
        };
    }

    private boolean matchesDate(LocalDate actual, TransactionRuleCondition condition) {
        if (actual == null) {
            return false;
        }
        LocalDate expected = LocalDate.parse(trimRequired(condition.getValue()));
        return switch (condition.getOperator()) {
            case EQUALS -> actual.isEqual(expected);
            case NOT_EQUALS -> !actual.isEqual(expected);
            case BEFORE -> actual.isBefore(expected);
            case AFTER -> actual.isAfter(expected);
            case BETWEEN -> {
                LocalDate upper = LocalDate.parse(trimRequired(condition.getSecondValue()));
                yield !actual.isBefore(expected) && !actual.isAfter(upper);
            }
            case IN -> tokens(condition.getValue()).stream().map(LocalDate::parse).anyMatch(actual::isEqual);
            case NOT_IN -> tokens(condition.getValue()).stream().map(LocalDate::parse).noneMatch(actual::isEqual);
            default -> false;
        };
    }

    private boolean matchesEnum(Enum<?> actual, TransactionRuleCondition condition) {
        if (actual == null) {
            return false;
        }
        String actualName = actual.name();
        return switch (condition.getOperator()) {
            case EQUALS -> actualName.equals(trimRequired(condition.getValue()));
            case NOT_EQUALS -> !actualName.equals(trimRequired(condition.getValue()));
            case IN -> tokens(condition.getValue()).contains(actualName);
            case NOT_IN -> !tokens(condition.getValue()).contains(actualName);
            default -> false;
        };
    }

    private boolean matchesEnum(TransactionFlow actual, TransactionRuleCondition condition) {
        return matchesEnum((Enum<?>) actual, condition);
    }

    private boolean matchesEnum(TransactionOrigin actual, TransactionRuleCondition condition) {
        return matchesEnum((Enum<?>) actual, condition);
    }

    private boolean matchesAccount(Long actual, TransactionRuleCondition condition) {
        if (actual == null) {
            return false;
        }
        Long expected = Long.valueOf(trimRequired(condition.getValue()));
        return switch (condition.getOperator()) {
            case EQUALS -> actual.equals(expected);
            case NOT_EQUALS -> !actual.equals(expected);
            case IN -> tokens(condition.getValue()).stream().map(Long::valueOf).anyMatch(actual::equals);
            case NOT_IN -> tokens(condition.getValue()).stream().map(Long::valueOf).noneMatch(actual::equals);
            default -> false;
        };
    }

    private String tagName(TransactionRuleEvaluationInput input, Tag tag) {
        return input.currentTagNames().getOrDefault(tag.getId(), tag.getName());
    }

    private List<String> tokens(String value) {
        if (value == null) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.split(",")) {
            if (!isBlank(token)) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private String trimRequired(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Stored transaction rule value must not be null");
        }
        return value.trim();
    }

    private String normalize(String value, boolean caseSensitive) {
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
