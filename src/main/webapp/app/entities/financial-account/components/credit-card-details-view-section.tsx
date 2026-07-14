import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { ICreditAccountDetails } from 'app/shared/model/credit-account-details.model';

interface CreditCardDetailsViewSectionProps {
  details?: ICreditAccountDetails;
  accountId?: number;
}

export const CreditCardDetailsViewSection = ({ details, accountId }: CreditCardDetailsViewSectionProps) => (
  <div data-cy="creditCardDetailsViewSection">
    <h3>
      <Translate contentKey="fintrackApp.creditAccountDetails.detail.title">Credit card details</Translate>
    </h3>
    {details?.id ? (
      <dl className="jh-entity-details">
        <dt>
          <Translate contentKey="fintrackApp.creditAccountDetails.creditLimit">Credit limit</Translate>
        </dt>
        <dd>{details.creditLimit}</dd>
        <dt>
          <Translate contentKey="fintrackApp.creditAccountDetails.statementDay">Statement day</Translate>
        </dt>
        <dd>{details.statementDay}</dd>
        <dt>
          <Translate contentKey="fintrackApp.creditAccountDetails.paymentDueDay">Payment due day</Translate>
        </dt>
        <dd>{details.paymentDueDay}</dd>
        <dt>
          <Translate contentKey="fintrackApp.creditAccountDetails.annualInterestRate">Annual interest rate</Translate>
        </dt>
        <dd>{details.annualInterestRate}</dd>
      </dl>
    ) : (
      <>
        <p>
          <Translate contentKey="fintrackApp.creditAccountDetails.composition.notConfigured">
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
