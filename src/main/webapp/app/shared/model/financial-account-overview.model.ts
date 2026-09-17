import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

export interface IFinancialAccountOverview {
  id: number;
  name: string;
  accountType: keyof typeof AccountType;
  currency: keyof typeof CurrencyCode;
  active: boolean;
  lastFourDigits?: string | null;
  currentBalance?: number | null;
  currentDebt?: number | null;
  creditLimit?: number | null;
  availableCredit?: number | null;
  statementDay?: number | null;
  paymentDueDay?: number | null;
  annualInterestRate?: number | null;
  missingCreditDetails?: boolean | null;
}
