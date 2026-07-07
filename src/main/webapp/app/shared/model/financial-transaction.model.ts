import dayjs from 'dayjs';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { IFinancialSubscription } from 'app/shared/model/financial-subscription.model';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { ITag } from 'app/shared/model/tag.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionOrigin } from 'app/shared/model/enumerations/transaction-origin.model';

export interface IFinancialTransaction {
  id?: number;
  transactionDate?: dayjs.Dayjs;
  postingDate?: dayjs.Dayjs | null;
  description?: string;
  amount?: number;
  flow?: keyof typeof TransactionFlow;
  origin?: keyof typeof TransactionOrigin;
  externalReference?: string | null;
  notes?: string | null;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  account?: IFinancialAccount;
  category?: ICategory | null;
  financialSubscription?: IFinancialSubscription | null;
  transactionIngestion?: ITransactionIngestion | null;
  tags?: ITag[] | null;
}

export const defaultValue: Readonly<IFinancialTransaction> = {};
