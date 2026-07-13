import React from 'react';
import { FormGroup, FormText, Input, Label } from 'reactstrap';
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
  values?: Record<string, string>;
  onFieldChange?: (fieldName: string, value: string) => void;
}

const getFieldValue = (values: Record<string, string> | undefined, fieldName: string, defaultValue: number | undefined | null) =>
  values?.[fieldName] ?? defaultValue ?? '';

const handleFieldChange =
  (fieldName: string, onFieldChange?: (fieldName: string, value: string) => void) => (event: React.ChangeEvent<HTMLInputElement>) =>
    onFieldChange?.(fieldName, event.target.value);

export const CreditCardDetailsFormSection = ({ details, values, onFieldChange }: CreditCardDetailsFormSectionProps) => (
  <div data-cy="creditCardDetailsFormSection">
    <h3>
      <Translate contentKey="fintrackApp.creditAccountDetails.detail.title">Credit card details</Translate>
    </h3>
    <FormGroup>
      <Label for="financial-account-credit-card-creditLimit">{translate('fintrackApp.creditAccountDetails.creditLimit')}</Label>
      <Input
        id="financial-account-credit-card-creditLimit"
        name={creditCardDetailsFieldNames.creditLimit}
        value={getFieldValue(values, creditCardDetailsFieldNames.creditLimit, details?.creditLimit)}
        onChange={handleFieldChange(creditCardDetailsFieldNames.creditLimit, onFieldChange)}
        data-cy="creditCardCreditLimit"
        type="text"
        required
        min={0}
      />
    </FormGroup>
    <FormText>
      <Translate contentKey="fintrackApp.creditAccountDetails.creditLimitHelp">Maximum approved credit line for this card.</Translate>
    </FormText>
    <FormGroup>
      <Label for="financial-account-credit-card-statementDay">{translate('fintrackApp.creditAccountDetails.statementDay')}</Label>
      <Input
        id="financial-account-credit-card-statementDay"
        name={creditCardDetailsFieldNames.statementDay}
        value={getFieldValue(values, creditCardDetailsFieldNames.statementDay, details?.statementDay)}
        onChange={handleFieldChange(creditCardDetailsFieldNames.statementDay, onFieldChange)}
        data-cy="creditCardStatementDay"
        type="text"
        required
        min={1}
        max={31}
      />
    </FormGroup>
    <FormText>
      <Translate contentKey="fintrackApp.creditAccountDetails.statementDayHelp">
        Day of the month when the statement period closes.
      </Translate>
    </FormText>
    <FormGroup>
      <Label for="financial-account-credit-card-paymentDueDay">{translate('fintrackApp.creditAccountDetails.paymentDueDay')}</Label>
      <Input
        id="financial-account-credit-card-paymentDueDay"
        name={creditCardDetailsFieldNames.paymentDueDay}
        value={getFieldValue(values, creditCardDetailsFieldNames.paymentDueDay, details?.paymentDueDay)}
        onChange={handleFieldChange(creditCardDetailsFieldNames.paymentDueDay, onFieldChange)}
        data-cy="creditCardPaymentDueDay"
        type="text"
        required
        min={1}
        max={31}
      />
    </FormGroup>
    <FormText>
      <Translate contentKey="fintrackApp.creditAccountDetails.paymentDueDayHelp">Day of the month when payment is due.</Translate>
    </FormText>
    <FormGroup>
      <Label for="financial-account-credit-card-annualInterestRate">
        {translate('fintrackApp.creditAccountDetails.annualInterestRate')}
      </Label>
      <Input
        id="financial-account-credit-card-annualInterestRate"
        name={creditCardDetailsFieldNames.annualInterestRate}
        value={getFieldValue(values, creditCardDetailsFieldNames.annualInterestRate, details?.annualInterestRate)}
        onChange={handleFieldChange(creditCardDetailsFieldNames.annualInterestRate, onFieldChange)}
        data-cy="creditCardAnnualInterestRate"
        type="text"
        min={0}
      />
    </FormGroup>
    <FormText>
      <Translate contentKey="fintrackApp.creditAccountDetails.annualInterestRateHelp">
        Annual percentage interest rate. Used later for interest calculations.
      </Translate>
    </FormText>
  </div>
);

export default CreditCardDetailsFormSection;
