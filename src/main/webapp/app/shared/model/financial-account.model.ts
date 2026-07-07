import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { IBudget } from 'app/shared/model/budget.model';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

export interface IFinancialAccount {
  id?: number;
  name?: string;
  institutionName?: string | null;
  accountType?: keyof typeof AccountType;
  currency?: keyof typeof CurrencyCode;
  initialBalance?: number;
  initialBalanceDate?: dayjs.Dayjs;
  lastFourDigits?: string | null;
  description?: string | null;
  color?: string | null;
  icon?: string | null;
  active?: boolean;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  budgets?: IBudget[] | null;
  transactionIngestions?: ITransactionIngestion[] | null;
}

export const defaultValue: Readonly<IFinancialAccount> = {
  active: false,
};
