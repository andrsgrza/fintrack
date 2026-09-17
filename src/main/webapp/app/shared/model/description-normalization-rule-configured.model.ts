import dayjs from 'dayjs';
import { DescriptionNormalizationRuleOperator } from './enumerations/description-normalization-rule-operator.model';
import { RuleConditionLogic } from './enumerations/rule-condition-logic.model';

export interface IDescriptionNormalizationRuleConfiguredCondition {
  operator?: keyof typeof DescriptionNormalizationRuleOperator;
  value?: string;
  caseSensitive?: boolean;
  position?: number;
}

export interface IDescriptionNormalizationRuleConfigured {
  id?: number;
  name?: string;
  description?: string | null;
  active?: boolean;
  priority?: number;
  conditionOperator?: keyof typeof RuleConditionLogic;
  resultingDescription?: string;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  conditions?: IDescriptionNormalizationRuleConfiguredCondition[];
}

export const defaultValue: Readonly<IDescriptionNormalizationRuleConfigured> = {
  active: false,
  conditionOperator: RuleConditionLogic.ALL,
  conditions: [],
};
