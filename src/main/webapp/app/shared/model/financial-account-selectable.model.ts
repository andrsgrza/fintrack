import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

/** Product-safe account shape used by account assignment controls. */
export interface IFinancialAccountSelectable {
  id?: number;
  name?: string;
  accountType?: keyof typeof AccountType;
  currency?: keyof typeof CurrencyCode;
  lastFourDigits?: string | null;
  active?: boolean;
}
