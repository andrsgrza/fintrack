import React from 'react';
import { Col, FormFeedback, FormGroup, FormText, Input, Label, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

import { ICreditAccountDetails } from 'app/shared/model/credit-account-details.model';

export const creditCardDetailsFieldNames = {
  creditLimit: 'creditCardCreditLimit',
  statementDay: 'creditCardStatementDay',
  paymentDueDay: 'creditCardPaymentDueDay',
  annualInterestRate: 'creditCardAnnualInterestRate',
};

interface CreditCardDetailsFormSectionProps {
  details?: ICreditAccountDetails;
  values: Record<string, string>;
  validationErrors?: Record<string, string>;
  onFieldChange: (fieldName: string, value: string) => void;
}

const getFieldValue = (values: Record<string, string>, fieldName: string, defaultValue: number | undefined | null) =>
  values[fieldName] ?? defaultValue ?? '';

const handleFieldChange =
  (fieldName: string, onFieldChange: (fieldName: string, value: string) => void) => (event: React.ChangeEvent<HTMLInputElement>) =>
    onFieldChange(fieldName, event.target.value);

/**
 * Contextual child fields deliberately keep local controlled values. This lets the configured parent command remain a single atomic save.
 */
export const CreditCardDetailsFormSection = ({
  details,
  values,
  validationErrors = {},
  onFieldChange,
}: CreditCardDetailsFormSectionProps) => (
  <div data-cy="creditCardDetailsFormSection" data-testid="creditCardDetailsFormSection">
    <Row className="g-3">
      <Col md="6">
        <FormGroup className="mb-0">
          <Label for="financial-account-credit-card-creditLimit">{translate('fintrackApp.financialAccount.creditLimit')}</Label>
          <Input
            id="financial-account-credit-card-creditLimit"
            name={creditCardDetailsFieldNames.creditLimit}
            value={getFieldValue(values, creditCardDetailsFieldNames.creditLimit, details?.creditLimit)}
            onChange={handleFieldChange(creditCardDetailsFieldNames.creditLimit, onFieldChange)}
            invalid={Boolean(validationErrors[creditCardDetailsFieldNames.creditLimit])}
            data-cy="creditCardCreditLimit"
            type="number"
            step="0.01"
          />
          {validationErrors[creditCardDetailsFieldNames.creditLimit] ? (
            <FormFeedback>{validationErrors[creditCardDetailsFieldNames.creditLimit]}</FormFeedback>
          ) : null}
          <FormText>
            <Translate contentKey="fintrackApp.financialAccount.creditLimitHelp">Maximum approved credit line for this card.</Translate>
          </FormText>
        </FormGroup>
      </Col>
      <Col md="6">
        <FormGroup className="mb-0">
          <Label for="financial-account-credit-card-statementDay">{translate('fintrackApp.financialAccount.statementDay')}</Label>
          <Input
            id="financial-account-credit-card-statementDay"
            name={creditCardDetailsFieldNames.statementDay}
            value={getFieldValue(values, creditCardDetailsFieldNames.statementDay, details?.statementDay)}
            onChange={handleFieldChange(creditCardDetailsFieldNames.statementDay, onFieldChange)}
            invalid={Boolean(validationErrors[creditCardDetailsFieldNames.statementDay])}
            data-cy="creditCardStatementDay"
            type="number"
          />
          {validationErrors[creditCardDetailsFieldNames.statementDay] ? (
            <FormFeedback>{validationErrors[creditCardDetailsFieldNames.statementDay]}</FormFeedback>
          ) : null}
          <FormText>
            <Translate contentKey="fintrackApp.financialAccount.statementDayHelp">
              Day of the month when the statement period closes.
            </Translate>
          </FormText>
        </FormGroup>
      </Col>
      <Col md="6">
        <FormGroup className="mb-0">
          <Label for="financial-account-credit-card-paymentDueDay">{translate('fintrackApp.financialAccount.paymentDueDay')}</Label>
          <Input
            id="financial-account-credit-card-paymentDueDay"
            name={creditCardDetailsFieldNames.paymentDueDay}
            value={getFieldValue(values, creditCardDetailsFieldNames.paymentDueDay, details?.paymentDueDay)}
            onChange={handleFieldChange(creditCardDetailsFieldNames.paymentDueDay, onFieldChange)}
            invalid={Boolean(validationErrors[creditCardDetailsFieldNames.paymentDueDay])}
            data-cy="creditCardPaymentDueDay"
            type="number"
          />
          {validationErrors[creditCardDetailsFieldNames.paymentDueDay] ? (
            <FormFeedback>{validationErrors[creditCardDetailsFieldNames.paymentDueDay]}</FormFeedback>
          ) : null}
          <FormText>
            <Translate contentKey="fintrackApp.financialAccount.paymentDueDayHelp">Day of the month when payment is due.</Translate>
          </FormText>
        </FormGroup>
      </Col>
      <Col md="6">
        <FormGroup className="mb-0">
          <Label for="financial-account-credit-card-annualInterestRate">
            {translate('fintrackApp.financialAccount.annualInterestRate')}
          </Label>
          <Input
            id="financial-account-credit-card-annualInterestRate"
            name={creditCardDetailsFieldNames.annualInterestRate}
            value={getFieldValue(values, creditCardDetailsFieldNames.annualInterestRate, details?.annualInterestRate)}
            onChange={handleFieldChange(creditCardDetailsFieldNames.annualInterestRate, onFieldChange)}
            invalid={Boolean(validationErrors[creditCardDetailsFieldNames.annualInterestRate])}
            data-cy="creditCardAnnualInterestRate"
            type="number"
            step="0.01"
          />
          {validationErrors[creditCardDetailsFieldNames.annualInterestRate] ? (
            <FormFeedback>{validationErrors[creditCardDetailsFieldNames.annualInterestRate]}</FormFeedback>
          ) : null}
          <FormText>
            <Translate contentKey="fintrackApp.financialAccount.annualInterestRateHelp">
              Annual percentage interest rate. Used later for interest calculations.
            </Translate>
          </FormText>
        </FormGroup>
      </Col>
    </Row>
  </div>
);

export default CreditCardDetailsFormSection;
