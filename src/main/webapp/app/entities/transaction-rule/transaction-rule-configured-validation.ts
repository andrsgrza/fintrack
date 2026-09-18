import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { CategoryType } from 'app/shared/model/enumerations/category-type.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

const ALL_FLOWS = new Set<string>([TransactionFlow.IN, TransactionFlow.OUT]);

type CategoryWithType = { categoryType?: keyof typeof CategoryType | null };

export const requiredFlowForCategory = (category?: CategoryWithType | null): keyof typeof TransactionFlow | null => {
  if (category?.categoryType === CategoryType.EXPENSE) {
    return TransactionFlow.OUT;
  }
  if (category?.categoryType === CategoryType.INCOME) {
    return TransactionFlow.IN;
  }
  return null;
};

export const isExactFlowEqualsCondition = (condition: ITransactionRuleCondition, flow: keyof typeof TransactionFlow) =>
  condition.field === TransactionRuleField.FLOW && condition.operator === RuleOperator.EQUALS && condition.value === flow;

const parseFlowTokens = (value?: string | null) =>
  (value ?? '')
    .split(',')
    .map(token => token.trim())
    .filter(Boolean);

export const getEffectiveFlows = (conditions: ITransactionRuleCondition[]) => {
  const effectiveFlows = new Set<string>(ALL_FLOWS);

  conditions
    .filter(condition => condition.field === TransactionRuleField.FLOW)
    .forEach(condition => {
      const tokens = parseFlowTokens(condition.value);
      if (condition.operator === RuleOperator.EQUALS || condition.operator === RuleOperator.IN) {
        for (const flow of Array.from(effectiveFlows)) {
          if (!tokens.includes(flow)) {
            effectiveFlows.delete(flow);
          }
        }
      }
      if (condition.operator === RuleOperator.NOT_EQUALS || condition.operator === RuleOperator.NOT_IN) {
        tokens.forEach(flow => effectiveFlows.delete(flow));
      }
    });

  return effectiveFlows;
};

export const isCategoryFlowCompatible = (
  category: CategoryWithType | null | undefined,
  conditionLogic: keyof typeof RuleConditionLogic | undefined,
  conditions: ITransactionRuleCondition[],
) => {
  const requiredFlow = requiredFlowForCategory(category);
  if (!requiredFlow) {
    return true;
  }
  if (conditionLogic !== RuleConditionLogic.ALL) {
    return false;
  }
  const effectiveFlows = getEffectiveFlows(conditions);
  return effectiveFlows.size === 1 && effectiveFlows.has(requiredFlow);
};
