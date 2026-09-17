import React from 'react';
import { Alert, Badge } from 'reactstrap';
import { Translate } from 'react-jhipster';

interface CreditAccountDetailsTechnicalNoticeProps {
  writeUnavailable?: boolean;
}

/**
 * Direct CreditAccountDetails routes are retained for compatibility only.
 * Product users manage these fields from the parent FinancialAccount workflow.
 */
export const CreditAccountDetailsTechnicalNotice = ({ writeUnavailable = false }: CreditAccountDetailsTechnicalNoticeProps) => (
  <div data-cy="creditAccountDetailsTechnicalNotice" data-testid="creditAccountDetailsTechnicalNotice">
    <Badge color="secondary" pill>
      <Translate contentKey="fintrackApp.creditAccountDetails.technical.badge">Technical</Translate>
    </Badge>
    <Alert color="secondary" fade={false} className="mt-2">
      <Translate
        contentKey={
          writeUnavailable
            ? 'fintrackApp.creditAccountDetails.technical.writeUnavailable'
            : 'fintrackApp.creditAccountDetails.technical.description'
        }
      >
        Credit card details are normally managed from the parent account.
      </Translate>
    </Alert>
  </div>
);

export default CreditAccountDetailsTechnicalNotice;
