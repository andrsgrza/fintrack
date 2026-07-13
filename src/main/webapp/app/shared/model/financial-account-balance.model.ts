import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

export interface IFinancialAccountBalance {
  accountId?: number;
  accountName?: string;
  accountType?: keyof typeof AccountType;
  currency?: keyof typeof CurrencyCode;
  initialBalance?: number;
  initialBalanceDate?: string;
  asOfDate?: string;
  inflowTotal?: number;
  outflowTotal?: number;
  currentBalance?: number;
  currentDebt?: number;
  creditLimit?: number;
  availableCredit?: number;
  missingCreditDetails?: boolean;
}
