import dayjs from 'dayjs';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';

export interface ICreditAccountDetails {
  id?: number;
  creditLimit?: number;
  statementDay?: number;
  paymentDueDay?: number;
  annualInterestRate?: number | null;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  account?: IFinancialAccount;
}

export const defaultValue: Readonly<ICreditAccountDetails> = {};
