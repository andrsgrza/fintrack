import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

type TranslateFn = (key: string, options?: Record<string, unknown>) => string;

const tagNames = (rule: ITransactionRule) => rule.resultingTags?.map(tag => tag.name).filter(Boolean) ?? [];

export const formatTransactionRuleStatus = (rule: ITransactionRule, translate: TranslateFn) =>
  rule.active ? translate('fintrackApp.transactionRule.status.active') : translate('fintrackApp.transactionRule.status.inactive');

export const formatTransactionRuleConditionLogic = (logic: ITransactionRule['conditionLogic'], translate: TranslateFn) => {
  if (logic === RuleConditionLogic.ANY) {
    return translate('fintrackApp.transactionRule.conditionSummary.any');
  }
  return translate('fintrackApp.transactionRule.conditionSummary.all');
};

export const formatTransactionRuleWhenSentence = (logic: ITransactionRule['conditionLogic'], translate: TranslateFn) => {
  if (logic === RuleConditionLogic.ANY) {
    return translate('fintrackApp.transactionRule.when.any');
  }
  return translate('fintrackApp.transactionRule.when.all');
};

export const buildTransactionRuleResultSummary = (rule: ITransactionRule, translate: TranslateFn) => {
  const results: string[] = [];

  if (rule.resultingCategory?.name) {
    results.push(`${translate('fintrackApp.transactionRule.result.category')}: ${rule.resultingCategory.name}`);
  }
  const names = tagNames(rule);
  if (names.length > 0) {
    results.push(`${translate('fintrackApp.transactionRule.result.tags')}: ${names.join(', ')}`);
  }

  return results;
};

export const buildTransactionRuleResultActions = (rule: ITransactionRule, translate: TranslateFn) => {
  const results: string[] = [];

  if (rule.resultingCategory?.name) {
    results.push(`${translate('fintrackApp.transactionRule.then.assignCategory')}: ${rule.resultingCategory.name}`);
  }
  const names = tagNames(rule);
  if (names.length > 0) {
    results.push(`${translate('fintrackApp.transactionRule.then.addTags')}: ${names.join(', ')}`);
  }

  return results;
};
