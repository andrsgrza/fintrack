import React from 'react';
import { Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';

import { ICreditAccountDetails } from 'app/shared/model/credit-account-details.model';

interface CreditCardDetailsViewSectionProps {
  details?: ICreditAccountDetails;
}

const CreditMetric = ({ labelKey, fallback, value }: { labelKey: string; fallback: string; value: React.ReactNode }) => (
  <div className="border rounded-3 p-3 h-100">
    <div className="text-muted small mb-1">
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </div>
    <div className="fw-semibold">{value ?? '—'}</div>
  </div>
);

export const CreditCardDetailsViewSection = ({ details }: CreditCardDetailsViewSectionProps) => (
  <div data-cy="creditCardDetailsViewSection" data-testid="creditCardDetailsViewSection">
    {details?.id ? (
      <Row className="g-3 mt-0" data-cy="financialAccountCreditTerms">
        <Col sm="6" lg="4">
          <CreditMetric
            labelKey="fintrackApp.financialAccount.statementDay"
            fallback="Statement closing day"
            value={details.statementDay}
          />
        </Col>
        <Col sm="6" lg="4">
          <CreditMetric labelKey="fintrackApp.financialAccount.paymentDueDay" fallback="Payment due day" value={details.paymentDueDay} />
        </Col>
        <Col sm="6" lg="4">
          <CreditMetric
            labelKey="fintrackApp.financialAccount.annualInterestRate"
            fallback="Annual interest rate"
            value={
              details.annualInterestRate === undefined || details.annualInterestRate === null ? null : `${details.annualInterestRate}%`
            }
          />
        </Col>
      </Row>
    ) : null}
  </div>
);

export default CreditCardDetailsViewSection;
