import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';

export interface ITransactionRuleCondition {
  id?: number;
  field?: keyof typeof TransactionRuleField;
  operator?: keyof typeof RuleOperator;
  value?: string;
  secondValue?: string | null;
  caseSensitive?: boolean;
  position?: number;
  transactionRule?: ITransactionRule;
}

export const defaultValue: Readonly<ITransactionRuleCondition> = {
  caseSensitive: false,
};
