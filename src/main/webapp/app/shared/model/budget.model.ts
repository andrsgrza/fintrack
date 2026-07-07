import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { ITag } from 'app/shared/model/tag.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';
import { BudgetPeriod } from 'app/shared/model/enumerations/budget-period.model';
import { BudgetStatus } from 'app/shared/model/enumerations/budget-status.model';
import { TagMatchMode } from 'app/shared/model/enumerations/tag-match-mode.model';

export interface IBudget {
  id?: number;
  name?: string;
  amount?: number;
  currency?: keyof typeof CurrencyCode;
  period?: keyof typeof BudgetPeriod;
  startDate?: dayjs.Dayjs;
  endDate?: dayjs.Dayjs | null;
  status?: keyof typeof BudgetStatus;
  tagMatchMode?: keyof typeof TagMatchMode;
  warningPercentage?: number | null;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  accounts?: IFinancialAccount[] | null;
  categories?: ICategory[] | null;
  tags?: ITag[] | null;
}

export const defaultValue: Readonly<IBudget> = {};
