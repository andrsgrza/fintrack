import dayjs from 'dayjs';
import { ICategory } from 'app/shared/model/category.model';
import { ITag } from 'app/shared/model/tag.model';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

export interface ITransactionRuleConfigured {
  id?: number;
  name?: string;
  description?: string | null;
  priority?: number;
  conditionLogic?: keyof typeof RuleConditionLogic;
  active?: boolean;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  resultingCategory?: ICategory | null;
  resultingTags?: ITag[] | null;
  conditions?: ITransactionRuleCondition[];
}

export const defaultValue: Readonly<ITransactionRuleConfigured> = {
  active: true,
  conditionLogic: 'ALL',
  conditions: [],
  resultingTags: [],
};
