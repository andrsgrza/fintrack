package com.fintrack.app.service.rules;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class ConditionGroupEvaluator {

    public <T> boolean matches(List<T> conditions, RuleConditionLogic logic, Predicate<T> predicate) {
        if (conditions == null || conditions.isEmpty() || logic == null) {
            return false;
        }
        return switch (logic) {
            case ALL -> conditions.stream().allMatch(predicate);
            case ANY -> conditions.stream().anyMatch(predicate);
        };
    }
}
