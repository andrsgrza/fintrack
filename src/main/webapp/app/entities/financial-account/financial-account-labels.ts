import { AccountType } from 'app/shared/model/enumerations/account-type.model';

export const getInitialBalanceLabelKey = (accountType?: keyof typeof AccountType | null) =>
  `fintrackApp.financialAccount.initialBalanceByAccountType.${accountType ?? 'DEBIT'}`;

export const getInitialBalanceHelpKey = (accountType?: keyof typeof AccountType | null) =>
  `fintrackApp.financialAccount.initialBalanceHelpByAccountType.${accountType ?? 'DEBIT'}`;
