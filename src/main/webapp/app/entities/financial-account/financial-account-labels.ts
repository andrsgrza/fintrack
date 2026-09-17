import { AccountType } from 'app/shared/model/enumerations/account-type.model';
import { translate } from 'react-jhipster';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';

export const getInitialBalanceLabelKey = (accountType?: keyof typeof AccountType | null) =>
  `fintrackApp.financialAccount.initialBalanceByAccountType.${accountType ?? 'DEBIT'}`;

export const getInitialBalanceHelpKey = (accountType?: keyof typeof AccountType | null) =>
  `fintrackApp.financialAccount.initialBalanceHelpByAccountType.${accountType ?? 'DEBIT'}`;

/** Consistent product label for selectable and existing FinancialAccount references. */
export const formatFinancialAccountLabel = (account?: IFinancialAccountSelectable | null) => {
  if (!account) {
    return '';
  }
  const parts = [
    account.name,
    account.accountType ? translate(`fintrackApp.AccountType.${account.accountType}`) : undefined,
    account.currency,
    account.lastFourDigits ? `••••${account.lastFourDigits}` : undefined,
    account.active === false ? translate('fintrackApp.financialAccount.status.inactive') : undefined,
  ];
  return parts.filter(Boolean).join(' · ');
};
