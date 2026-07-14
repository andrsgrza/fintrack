import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

export type ValueInputKind = 'text' | 'number' | 'date' | 'flow-select' | 'origin-select' | 'account-select';
export type SecondValueInputKind = 'text' | 'number' | 'date';

const TEXT_OPERATORS = [
  RuleOperator.EQUALS,
  RuleOperator.NOT_EQUALS,
  RuleOperator.CONTAINS,
  RuleOperator.NOT_CONTAINS,
  RuleOperator.STARTS_WITH,
  RuleOperator.ENDS_WITH,
  RuleOperator.REGEX,
  RuleOperator.IN,
  RuleOperator.NOT_IN,
];

const ENUM_OPERATORS = [RuleOperator.EQUALS, RuleOperator.NOT_EQUALS, RuleOperator.IN, RuleOperator.NOT_IN];

const AMOUNT_OPERATORS = [
  RuleOperator.EQUALS,
  RuleOperator.NOT_EQUALS,
  RuleOperator.GREATER_THAN,
  RuleOperator.GREATER_THAN_OR_EQUAL,
  RuleOperator.LESS_THAN,
  RuleOperator.LESS_THAN_OR_EQUAL,
  RuleOperator.BETWEEN,
  RuleOperator.IN,
  RuleOperator.NOT_IN,
];

const DATE_OPERATORS = [
  RuleOperator.EQUALS,
  RuleOperator.NOT_EQUALS,
  RuleOperator.BEFORE,
  RuleOperator.AFTER,
  RuleOperator.BETWEEN,
  RuleOperator.IN,
  RuleOperator.NOT_IN,
];

const ACCOUNT_OPERATORS = [RuleOperator.EQUALS, RuleOperator.NOT_EQUALS, RuleOperator.IN, RuleOperator.NOT_IN];

export const isTextField = (field?: TransactionRuleField) =>
  field === TransactionRuleField.DESCRIPTION || field === TransactionRuleField.EXTERNAL_REFERENCE;

export const isAmountField = (field?: TransactionRuleField) => field === TransactionRuleField.AMOUNT;

export const isDateField = (field?: TransactionRuleField) =>
  field === TransactionRuleField.TRANSACTION_DATE || field === TransactionRuleField.POSTING_DATE;

export const isEnumField = (field?: TransactionRuleField) => field === TransactionRuleField.FLOW || field === TransactionRuleField.ORIGIN;

export const isAccountField = (field?: TransactionRuleField) => field === TransactionRuleField.ACCOUNT;

export const isListOperator = (operator?: RuleOperator) => operator === RuleOperator.IN || operator === RuleOperator.NOT_IN;

export const getAllowedOperators = (field?: TransactionRuleField): RuleOperator[] => {
  if (isTextField(field)) {
    return TEXT_OPERATORS;
  }
  if (isAmountField(field)) {
    return AMOUNT_OPERATORS;
  }
  if (isDateField(field)) {
    return DATE_OPERATORS;
  }
  if (isEnumField(field)) {
    return ENUM_OPERATORS;
  }
  if (isAccountField(field)) {
    return ACCOUNT_OPERATORS;
  }
  return TEXT_OPERATORS;
};

export const requiresSecondValue = (operator?: RuleOperator) => operator === RuleOperator.BETWEEN;

export const supportsCaseSensitive = (field?: TransactionRuleField) => isTextField(field);

export const getValueInputKind = (field?: TransactionRuleField, operator?: RuleOperator): ValueInputKind => {
  if (isListOperator(operator)) {
    return 'text';
  }
  if (isAmountField(field)) {
    return 'number';
  }
  if (isDateField(field)) {
    return 'date';
  }
  if (field === TransactionRuleField.FLOW) {
    return 'flow-select';
  }
  if (field === TransactionRuleField.ORIGIN) {
    return 'origin-select';
  }
  if (isAccountField(field)) {
    return 'account-select';
  }
  return 'text';
};

export const getSecondValueInputKind = (field?: TransactionRuleField): SecondValueInputKind => {
  if (isAmountField(field)) {
    return 'number';
  }
  if (isDateField(field)) {
    return 'date';
  }
  return 'text';
};
