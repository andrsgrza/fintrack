import dayjs from 'dayjs';
import { IDescriptionNormalizationRule } from 'app/shared/model/description-normalization-rule.model';
import { DescriptionNormalizationRuleOperator } from 'app/shared/model/enumerations/description-normalization-rule-operator.model';

export interface IDescriptionNormalizationRuleCondition {
  id?: number;
  operator?: keyof typeof DescriptionNormalizationRuleOperator;
  value?: string;
  caseSensitive?: boolean;
  position?: number;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  descriptionNormalizationRule?: IDescriptionNormalizationRule;
}

export const defaultValue: Readonly<IDescriptionNormalizationRuleCondition> = {
  caseSensitive: false,
};
