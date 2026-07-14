import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import {
  getAllowedOperators,
  getValueInputKind,
  requiresSecondValue,
  supportsCaseSensitive,
} from './transaction-rule-condition-form-helpers';

describe('transaction rule condition form helpers', () => {
  it('returns text operators for description and external reference', () => {
    expect(getAllowedOperators(TransactionRuleField.DESCRIPTION)).toEqual([
      RuleOperator.EQUALS,
      RuleOperator.NOT_EQUALS,
      RuleOperator.CONTAINS,
      RuleOperator.NOT_CONTAINS,
      RuleOperator.STARTS_WITH,
      RuleOperator.ENDS_WITH,
      RuleOperator.REGEX,
      RuleOperator.IN,
      RuleOperator.NOT_IN,
    ]);
    expect(getAllowedOperators(TransactionRuleField.EXTERNAL_REFERENCE)).toContain(RuleOperator.REGEX);
  });

  it('returns numeric operators for amount', () => {
    expect(getAllowedOperators(TransactionRuleField.AMOUNT)).toEqual([
      RuleOperator.EQUALS,
      RuleOperator.NOT_EQUALS,
      RuleOperator.GREATER_THAN,
      RuleOperator.GREATER_THAN_OR_EQUAL,
      RuleOperator.LESS_THAN,
      RuleOperator.LESS_THAN_OR_EQUAL,
      RuleOperator.BETWEEN,
      RuleOperator.IN,
      RuleOperator.NOT_IN,
    ]);
  });

  it('returns date operators for transaction and posting date', () => {
    expect(getAllowedOperators(TransactionRuleField.TRANSACTION_DATE)).toEqual([
      RuleOperator.EQUALS,
      RuleOperator.NOT_EQUALS,
      RuleOperator.BEFORE,
      RuleOperator.AFTER,
      RuleOperator.BETWEEN,
      RuleOperator.IN,
      RuleOperator.NOT_IN,
    ]);
    expect(getAllowedOperators(TransactionRuleField.POSTING_DATE)).toContain(RuleOperator.BEFORE);
  });

  it('returns enum and account operators', () => {
    expect(getAllowedOperators(TransactionRuleField.FLOW)).toEqual([
      RuleOperator.EQUALS,
      RuleOperator.NOT_EQUALS,
      RuleOperator.IN,
      RuleOperator.NOT_IN,
    ]);
    expect(getAllowedOperators(TransactionRuleField.ORIGIN)).toEqual(getAllowedOperators(TransactionRuleField.FLOW));
    expect(getAllowedOperators(TransactionRuleField.ACCOUNT)).toEqual(getAllowedOperators(TransactionRuleField.FLOW));
  });

  it('detects second value and case sensitivity support', () => {
    expect(requiresSecondValue(RuleOperator.BETWEEN)).toBe(true);
    expect(requiresSecondValue(RuleOperator.EQUALS)).toBe(false);
    expect(supportsCaseSensitive(TransactionRuleField.DESCRIPTION)).toBe(true);
    expect(supportsCaseSensitive(TransactionRuleField.EXTERNAL_REFERENCE)).toBe(true);
    expect(supportsCaseSensitive(TransactionRuleField.AMOUNT)).toBe(false);
  });

  it('returns value input kind by field and operator', () => {
    expect(getValueInputKind(TransactionRuleField.AMOUNT, RuleOperator.EQUALS)).toBe('number');
    expect(getValueInputKind(TransactionRuleField.AMOUNT, RuleOperator.IN)).toBe('text');
    expect(getValueInputKind(TransactionRuleField.TRANSACTION_DATE, RuleOperator.EQUALS)).toBe('date');
    expect(getValueInputKind(TransactionRuleField.FLOW, RuleOperator.EQUALS)).toBe('flow-select');
    expect(getValueInputKind(TransactionRuleField.ORIGIN, RuleOperator.EQUALS)).toBe('origin-select');
    expect(getValueInputKind(TransactionRuleField.ACCOUNT, RuleOperator.EQUALS)).toBe('account-select');
    expect(getValueInputKind(TransactionRuleField.ACCOUNT, RuleOperator.IN)).toBe('text');
  });
});
