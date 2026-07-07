import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';
import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { IFinancialSubscription } from 'app/shared/model/financial-subscription.model';
import { IBudget } from 'app/shared/model/budget.model';

export interface ITag {
  id?: number;
  name?: string;
  description?: string | null;
  color?: string | null;
  active?: boolean;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  financialTransactions?: IFinancialTransaction[] | null;
  transactionRules?: ITransactionRule[] | null;
  subscriptions?: IFinancialSubscription[] | null;
  budgets?: IBudget[] | null;
}

export const defaultValue: Readonly<ITag> = {
  active: false,
};
