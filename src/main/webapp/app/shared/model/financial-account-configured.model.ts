import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

export interface IFinancialAccountConfiguredInput {
  name?: string;
  institutionName?: string | null;
  accountType?: keyof typeof AccountType;
  currency?: keyof typeof CurrencyCode;
  initialBalance?: number;
  initialBalanceDate?: string;
  lastFourDigits?: string | null;
  description?: string | null;
  color?: string | null;
  icon?: string | null;
  active?: boolean;
}

export interface ICreditAccountDetailsConfiguredInput {
  creditLimit?: number;
  statementDay?: number;
  paymentDueDay?: number;
  annualInterestRate?: number | null;
}

export interface IFinancialAccountConfiguredRequest {
  financialAccount: IFinancialAccountConfiguredInput;
  creditAccountDetails?: ICreditAccountDetailsConfiguredInput;
}

export interface IFinancialAccountConfiguredResponse {
  financialAccount: IFinancialAccountConfiguredInput & { id: number };
  creditAccountDetails?: ICreditAccountDetailsConfiguredInput & { id: number };
}
