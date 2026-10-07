import React from 'react';
import { Alert } from 'reactstrap';
import { Translate } from 'react-jhipster';

import { IFinancialAccountDeletionBlocker } from 'app/shared/model/financial-account-deletion-preview.model';

const blockerKeyByCode: Record<Exclude<IFinancialAccountDeletionBlocker['code'], 'CROSS_ACCOUNT_TRANSFER'>, string> = {
  BUDGET_SCOPE_WOULD_BROADEN: 'fintrackApp.financialAccount.delete.blockers.budgetScopeWouldBroaden',
  RULE_ACCOUNT_CONDITION_REFERENCE: 'fintrackApp.financialAccount.delete.blockers.ruleAccountConditionReference',
  CORRUPT_CANDIDATE: 'fintrackApp.financialAccount.delete.blockers.corruptCandidate',
  CORRUPT_INGESTION_GRAPH: 'fintrackApp.financialAccount.delete.blockers.corruptIngestionGraph',
  UNSUPPORTED_API_IMPORT_CANDIDATE: 'fintrackApp.financialAccount.delete.blockers.unsupportedApiImportCandidate',
};

const blockerFallbackByCode: Record<IFinancialAccountDeletionBlocker['code'], string> = {
  CROSS_ACCOUNT_TRANSFER: 'This account participates in a transfer with another account. Resolve that transfer before deleting it.',
  BUDGET_SCOPE_WOULD_BROADEN:
    'A budget uses this account as its only specific account. Deleting it would broaden that budget to all active accounts. Edit the budget first.',
  RULE_ACCOUNT_CONDITION_REFERENCE: 'A rule uses this account as a condition. Edit or remove that condition before deleting the account.',
  CORRUPT_CANDIDATE: 'Fintrack detected inconsistent transaction data associated with this account. It cannot be deleted safely.',
  CORRUPT_INGESTION_GRAPH: 'Fintrack detected inconsistent import data associated with this account. It cannot be deleted safely.',
  UNSUPPORTED_API_IMPORT_CANDIDATE: 'This account contains API import data that does not yet support permanent deletion.',
};

interface AccountDeletionBlockersProps {
  blockers: IFinancialAccountDeletionBlocker[];
}

const blockerKey = (blocker: IFinancialAccountDeletionBlocker) =>
  blocker.code === 'CROSS_ACCOUNT_TRANSFER'
    ? `fintrackApp.financialAccount.delete.blockers.crossAccountTransfer.${blocker.count === 1 ? 'one' : 'other'}`
    : blockerKeyByCode[blocker.code];

export const AccountDeletionBlockers = ({ blockers }: AccountDeletionBlockersProps) => {
  if (blockers.length === 0) {
    return null;
  }

  return (
    <Alert
      color="warning"
      fade={false}
      className="mb-3"
      data-cy="financialAccountDeleteBlockers"
      data-testid="financialAccountDeleteBlockers"
      aria-live="polite"
    >
      <h3 className="h6 mb-2">
        <Translate contentKey="fintrackApp.financialAccount.delete.blockers.title">This account cannot be deleted yet</Translate>
      </h3>
      <ul className="mb-0 ps-3">
        {blockers.map(blocker => (
          <li
            key={blocker.code}
            data-cy={`financialAccountDeleteBlocker-${blocker.code}`}
            data-testid={`financialAccountDeleteBlocker-${blocker.code}`}
          >
            <Translate contentKey={blockerKey(blocker)} interpolate={{ count: blocker.count }}>
              {blockerFallbackByCode[blocker.code]}
            </Translate>
          </li>
        ))}
      </ul>
    </Alert>
  );
};

export default AccountDeletionBlockers;
