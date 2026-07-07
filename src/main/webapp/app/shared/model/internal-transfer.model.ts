import dayjs from 'dayjs';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';

export interface IInternalTransfer {
  id?: number;
  notes?: string | null;
  createdAt?: dayjs.Dayjs;
  outgoingTransaction?: IFinancialTransaction;
  incomingTransaction?: IFinancialTransaction;
}

export const defaultValue: Readonly<IInternalTransfer> = {};
