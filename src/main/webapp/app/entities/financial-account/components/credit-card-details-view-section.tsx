import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { ICreditAccountDetails } from 'app/shared/model/credit-account-details.model';

interface CreditCardDetailsViewSectionProps {
  details?: ICreditAccountDetails;
  accountId?: number;
  currency?: string;
}

const formatMoney = (value: number | undefined | null, currency: string | undefined) =>
  value === undefined || value === null ? '' : `${value} ${currency ?? ''}`.trim();

export const CreditCardDetailsViewSection = ({ details, accountId, currency }: CreditCardDetailsViewSectionProps) => (
  <div data-cy="creditCardDetailsViewSection">
    <h3>
      <Translate contentKey="fintrackApp.financialAccount.detail.creditDetails">Credit details</Translate>
    </h3>
    {details?.id ? (
      <dl className="jh-entity-details">
        <dt>
          <Translate contentKey="fintrackApp.financialAccount.creditLimit">Credit limit</Translate>
        </dt>
        <dd>{formatMoney(details.creditLimit, currency)}</dd>
        <dt>
          <Translate contentKey="fintrackApp.financialAccount.statementDay">Statement closing day</Translate>
        </dt>
        <dd>{details.statementDay}</dd>
        <dt>
          <Translate contentKey="fintrackApp.financialAccount.paymentDueDay">Payment due day</Translate>
        </dt>
        <dd>{details.paymentDueDay}</dd>
        <dt>
          <Translate contentKey="fintrackApp.financialAccount.annualInterestRate">Annual interest rate</Translate>
        </dt>
        <dd>{details.annualInterestRate}</dd>
      </dl>
    ) : (
      <>
        <p>
          <Translate contentKey="fintrackApp.financialAccount.creditDetailsMissing">
            Credit card details have not been configured yet.
          </Translate>
        </p>
        {accountId ? (
          <Button tag={Link} to={`/financial-account/${accountId}/edit`} color="primary" size="sm">
            <FontAwesomeIcon icon="pencil-alt" />{' '}
            <span className="d-none d-md-inline">
              <Translate contentKey="entity.action.edit">Edit</Translate>
            </span>
          </Button>
        ) : null}
      </>
    )}
  </div>
);

export default CreditCardDetailsViewSection;
