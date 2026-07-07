import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { ICategory } from 'app/shared/model/category.model';
import { IFinancialSubscription } from 'app/shared/model/financial-subscription.model';
import { ITag } from 'app/shared/model/tag.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

export interface ITransactionRule {
  id?: number;
  name?: string;
  description?: string | null;
  priority?: number;
  conditionLogic?: keyof typeof RuleConditionLogic;
  resultingDescription?: string | null;
  active?: boolean;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  resultingCategory?: ICategory | null;
  resultingFinancialSubscription?: IFinancialSubscription | null;
  resultingTags?: ITag[] | null;
}

export const defaultValue: Readonly<ITransactionRule> = {
  active: false,
};
