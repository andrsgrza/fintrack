import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

export interface IDescriptionNormalizationRule {
  id?: number;
  name?: string;
  description?: string | null;
  active?: boolean;
  priority?: number;
  conditionOperator?: keyof typeof RuleConditionLogic;
  resultingDescription?: string;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
}

export const defaultValue: Readonly<IDescriptionNormalizationRule> = {
  active: false,
};
