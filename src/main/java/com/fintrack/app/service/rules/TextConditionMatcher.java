package com.fintrack.app.service.rules;

import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
public class TextConditionMatcher {

    public boolean matches(String actualValue, DescriptionNormalizationRuleOperator operator, String expectedValue, boolean caseSensitive) {
        String actual = trimToNull(actualValue);
        String expected = trimToNull(expectedValue);
        if (actual == null || expected == null || operator == null) {
            return false;
        }
        String comparableActual = caseSensitive ? actual : actual.toLowerCase();
        String comparableExpected = caseSensitive ? expected : expected.toLowerCase();
        return switch (operator) {
            case EXACT -> comparableActual.equals(comparableExpected);
            case NOT_EQUALS -> !comparableActual.equals(comparableExpected);
            case CONTAINS -> comparableActual.contains(comparableExpected);
            case NOT_CONTAINS -> !comparableActual.contains(comparableExpected);
            case STARTS_WITH -> comparableActual.startsWith(comparableExpected);
            case ENDS_WITH -> comparableActual.endsWith(comparableExpected);
            case REGEX -> regexMatches(actual, expected, caseSensitive);
        };
    }

    public void validatePattern(DescriptionNormalizationRuleOperator operator, String value) {
        if (operator == DescriptionNormalizationRuleOperator.REGEX) {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Regex pattern is invalid", e);
            }
        }
    }

    private boolean regexMatches(String actual, String pattern, boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return Pattern.compile(pattern, flags).matcher(actual).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
