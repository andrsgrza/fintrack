import { buildTransactionRuleConditionSummary } from './transaction-rule-condition-display';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

const translations = {
  'fintrackApp.TransactionRuleField.DESCRIPTION': 'Description',
  'fintrackApp.TransactionRuleField.AMOUNT': 'Amount',
  'fintrackApp.TransactionRuleField.FLOW': 'Flow',
  'fintrackApp.TransactionRuleField.ACCOUNT': 'Account',
  'fintrackApp.TransactionFlow.IN': 'Income',
  'fintrackApp.TransactionFlow.OUT': 'Expense',
  'fintrackApp.transactionRuleCondition.summary.unknownField': 'Condition',
  'fintrackApp.transactionRuleCondition.summary.missingValue': '—',
  'fintrackApp.transactionRuleCondition.summary.caseSensitiveSuffix': '(case-sensitive)',
  'fintrackApp.transactionRuleCondition.summary.equals': '{{field}} equals {{value}}',
  'fintrackApp.transactionRuleCondition.summary.notEquals': '{{field}} is not {{value}}',
  'fintrackApp.transactionRuleCondition.summary.between': '{{field}} between {{value}} and {{secondValue}}',
  'fintrackApp.transactionRuleCondition.summary.contains': '{{field}} contains {{value}}',
  'fintrackApp.transactionRuleCondition.summary.in': '{{field}} is one of {{value}}',
  'fintrackApp.transactionRuleCondition.summary.notIn': '{{field}} is not one of {{value}}',
};

const translate = (key: string, options?: Record<string, unknown>) =>
  Object.entries(options ?? {}).reduce(
    (label, [optionKey, optionValue]) => label.replace(`{{${optionKey}}}`, String(optionValue)),
    translations[key] ?? key,
  );

const condition = (overrides: Partial<ITransactionRuleCondition>): ITransactionRuleCondition => ({
  field: TransactionRuleField.DESCRIPTION,
  operator: RuleOperator.EQUALS,
  value: 'UBER',
  caseSensitive: false,
  ...overrides,
});

describe('TransactionRuleCondition display helper', () => {
  it('renders EQUALS as one sentence', () => {
    expect(buildTransactionRuleConditionSummary(condition({}), translate)).toBe('Description equals "UBER"');
  });

  it('renders BETWEEN with value and secondValue in one sentence', () => {
    expect(
      buildTransactionRuleConditionSummary(
        condition({
          field: TransactionRuleField.AMOUNT,
          operator: RuleOperator.BETWEEN,
          value: '20',
          secondValue: '500',
        }),
        translate,
      ),
    ).toBe('Amount between 20 and 500');
  });

  it('adds case-sensitive suffix for text conditions only when true', () => {
    expect(
      buildTransactionRuleConditionSummary(condition({ operator: RuleOperator.CONTAINS, value: 'Uber', caseSensitive: true }), translate),
    ).toBe('Description contains "Uber" (case-sensitive)');
  });

  it('does not render raw false when caseSensitive is false', () => {
    const summary = buildTransactionRuleConditionSummary(
      condition({ operator: RuleOperator.CONTAINS, value: 'Uber', caseSensitive: false }),
      translate,
    );

    expect(summary).toBe('Description contains "Uber"');
    expect(summary).not.toContain('false');
  });

  it('does not render case sensitivity for non-text fields', () => {
    expect(
      buildTransactionRuleConditionSummary(
        condition({
          field: TransactionRuleField.FLOW,
          operator: RuleOperator.NOT_EQUALS,
          value: 'IN',
          caseSensitive: true,
        }),
        translate,
      ),
    ).toBe('Flow is not Income');
  });

  it('renders FLOW list values with the shared TransactionFlow labels', () => {
    expect(
      buildTransactionRuleConditionSummary(
        condition({
          field: TransactionRuleField.FLOW,
          operator: RuleOperator.NOT_IN,
          value: 'IN,OUT',
        }),
        translate,
      ),
    ).toBe('Flow is not one of Income, Expense');
  });

  it('renders IN and NOT_IN as readable list phrases', () => {
    expect(buildTransactionRuleConditionSummary(condition({ operator: RuleOperator.IN, value: 'Uber,Lyft,Taxi' }), translate)).toBe(
      'Description is one of "Uber", "Lyft", "Taxi"',
    );
    expect(buildTransactionRuleConditionSummary(condition({ operator: RuleOperator.NOT_IN, value: 'Uber,Lyft' }), translate)).toBe(
      'Description is not one of "Uber", "Lyft"',
    );
  });

  it('falls back safely when BETWEEN secondValue is missing', () => {
    expect(
      buildTransactionRuleConditionSummary(
        condition({
          field: TransactionRuleField.AMOUNT,
          operator: RuleOperator.BETWEEN,
          value: '20',
          secondValue: null,
        }),
        translate,
      ),
    ).toBe('Amount between 20 and —');
  });

  it('can use external display values for account ids', () => {
    expect(
      buildTransactionRuleConditionSummary(
        condition({
          field: TransactionRuleField.ACCOUNT,
          operator: RuleOperator.EQUALS,
          value: '42',
        }),
        translate,
        { valueDisplay: value => (value === '42' ? 'Checking account' : undefined) },
      ),
    ).toBe('Account equals Checking account');
  });
});
