import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

type TranslateFn = (key: string, options?: Record<string, unknown>) => string;

interface SummaryOptions {
  valueDisplay?: (value: string, condition: ITransactionRuleCondition) => string | undefined;
}

const textFields = new Set<string>([TransactionRuleField.DESCRIPTION, TransactionRuleField.EXTERNAL_REFERENCE]);

const fieldLabel = (condition: ITransactionRuleCondition, translate: TranslateFn) =>
  condition.field
    ? translate(`fintrackApp.TransactionRuleField.${condition.field}`)
    : translate('fintrackApp.transactionRuleCondition.summary.unknownField');

const isTextField = (condition: ITransactionRuleCondition) => Boolean(condition.field && textFields.has(condition.field));

const isBlank = (value?: string | null) => value === undefined || value === null || value.trim() === '';

const missingValue = (translate: TranslateFn) => translate('fintrackApp.transactionRuleCondition.summary.missingValue');

const rawValue = (value: string | null | undefined, translate: TranslateFn) => (isBlank(value) ? missingValue(translate) : value.trim());

const quote = (value: string) => `"${value}"`;

const fieldValueTranslationKey = (condition: ITransactionRuleCondition, value: string) => {
  if (condition.field === TransactionRuleField.FLOW) {
    return `fintrackApp.TransactionFlow.${value}`;
  }
  return undefined;
};

const translatedFieldValue = (condition: ITransactionRuleCondition, value: string, translate: TranslateFn) => {
  const key = fieldValueTranslationKey(condition, value);
  if (!key) {
    return undefined;
  }
  const translated = translate(key);
  return translated === key ? undefined : translated;
};

const displayValue = (
  condition: ITransactionRuleCondition,
  translate: TranslateFn,
  options: SummaryOptions,
  value: string | null | undefined = condition.value,
) => {
  const normalizedValue = rawValue(value, translate);
  if (normalizedValue === missingValue(translate)) {
    return normalizedValue;
  }
  const customDisplay = options.valueDisplay?.(normalizedValue, condition);
  if (customDisplay) {
    return customDisplay;
  }
  const translatedValue = translatedFieldValue(condition, normalizedValue, translate);
  if (translatedValue) {
    return translatedValue;
  }
  return isTextField(condition) ? quote(normalizedValue) : normalizedValue;
};

const displayListValue = (condition: ITransactionRuleCondition, translate: TranslateFn, options: SummaryOptions) => {
  if (isBlank(condition.value)) {
    return missingValue(translate);
  }
  return condition.value
    .split(',')
    .map(value => displayValue(condition, translate, options, value))
    .join(', ');
};

const withCaseSensitivitySuffix = (summary: string, condition: ITransactionRuleCondition, translate: TranslateFn) => {
  if (isTextField(condition) && condition.caseSensitive) {
    return `${summary} ${translate('fintrackApp.transactionRuleCondition.summary.caseSensitiveSuffix')}`;
  }
  return summary;
};

export const buildTransactionRuleConditionSummary = (
  condition: ITransactionRuleCondition,
  translate: TranslateFn,
  options: SummaryOptions = {},
) => {
  const field = fieldLabel(condition, translate);
  const value = displayValue(condition, translate, options);
  const secondValue = displayValue(condition, translate, options, condition.secondValue);
  const listValue = displayListValue(condition, translate, options);

  const summaryKey = (() => {
    switch (condition.operator) {
      case RuleOperator.EQUALS:
        return 'equals';
      case RuleOperator.NOT_EQUALS:
        return 'notEquals';
      case RuleOperator.CONTAINS:
        return 'contains';
      case RuleOperator.NOT_CONTAINS:
        return 'notContains';
      case RuleOperator.STARTS_WITH:
        return 'startsWith';
      case RuleOperator.ENDS_WITH:
        return 'endsWith';
      case RuleOperator.REGEX:
        return 'regex';
      case RuleOperator.GREATER_THAN:
        return 'greaterThan';
      case RuleOperator.GREATER_THAN_OR_EQUAL:
        return 'greaterThanOrEqual';
      case RuleOperator.LESS_THAN:
        return 'lessThan';
      case RuleOperator.LESS_THAN_OR_EQUAL:
        return 'lessThanOrEqual';
      case RuleOperator.BEFORE:
        return 'before';
      case RuleOperator.AFTER:
        return 'after';
      case RuleOperator.BETWEEN:
        return 'between';
      case RuleOperator.IN:
        return 'in';
      case RuleOperator.NOT_IN:
        return 'notIn';
      default:
        return 'fallback';
    }
  })();

  const renderedValue = condition.operator === RuleOperator.IN || condition.operator === RuleOperator.NOT_IN ? listValue : value;
  const summary = translate(`fintrackApp.transactionRuleCondition.summary.${summaryKey}`, {
    field,
    value: renderedValue,
    secondValue,
  });

  return withCaseSensitivitySuffix(summary, condition, translate);
};
