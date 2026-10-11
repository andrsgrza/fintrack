import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { buildTransactionRuleConditionSummary } from 'app/entities/transaction-rule-condition/transaction-rule-condition-display';

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

/**
 * Formats persisted condition values for the product manager without attempting to evaluate a rule in the browser.
 * The condition summary itself remains shared with the configured editor and technical condition surfaces.
 */
/**
 * Adds presentation-only catalog context to the typed condition formatter. Both the manager and product detail
 * use this so ACCOUNT conditions never fall back to raw ids and date-only values retain their calendar day.
 */
export const buildTransactionRuleConditionSummaryWithContext = (
  condition: ITransactionRuleCondition,
  translate: TranslateFn,
  accountsById: ReadonlyMap<number, IFinancialAccount>,
  locale: string,
) =>
  buildTransactionRuleConditionSummary(condition, translate, {
    valueDisplay(value, persistedCondition) {
      if (persistedCondition.field === TransactionRuleField.ACCOUNT) {
        const account = /^\d+$/.test(value) ? accountsById.get(Number(value)) : undefined;
        return account?.name ?? translate('fintrackApp.transactionRule.summary.accountUnavailable');
      }

      if (
        persistedCondition.field === TransactionRuleField.TRANSACTION_DATE ||
        persistedCondition.field === TransactionRuleField.POSTING_DATE
      ) {
        const parsedDate = new Date(`${value}T00:00:00`);
        if (!Number.isNaN(parsedDate.getTime())) {
          return new Intl.DateTimeFormat(locale === 'es' ? 'es-MX' : 'en-US', { dateStyle: 'medium' }).format(parsedDate);
        }
      }

      if (persistedCondition.field === TransactionRuleField.AMOUNT) {
        const parsedAmount = Number(value);
        if (Number.isFinite(parsedAmount)) {
          return new Intl.NumberFormat(locale === 'es' ? 'es-MX' : 'en-US', { maximumFractionDigits: 2 }).format(parsedAmount);
        }
      }

      return undefined;
    },
  });

/** @deprecated Kept as a narrow alias while the manager uses the shared product formatter. */
export const buildTransactionRuleManagerConditionSummary = buildTransactionRuleConditionSummaryWithContext;
