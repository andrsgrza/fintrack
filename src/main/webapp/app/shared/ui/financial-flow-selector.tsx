import React from 'react';
import { Button, ButtonGroup } from 'reactstrap';

export type FinancialFlowOption = 'IN' | 'OUT';

/** Shared product control for selecting whether a monetary value is income or expense. */
export const FinancialFlowSelector = ({
  flow,
  onChange,
  incomeLabel,
  expenseLabel,
  disabled = false,
  ariaLabelledBy,
  dataCy = 'financialFlow',
}: {
  flow: FinancialFlowOption;
  onChange: (flow: FinancialFlowOption) => void;
  incomeLabel: React.ReactNode;
  expenseLabel: React.ReactNode;
  disabled?: boolean;
  ariaLabelledBy?: string;
  dataCy?: string;
}) => (
  <div className="financial-flow-selector" data-cy={dataCy} data-testid={dataCy}>
    <ButtonGroup className="d-flex" role="group" aria-labelledby={ariaLabelledBy}>
      <Button
        type="button"
        color="light"
        className={`financial-flow-selector-option financial-flow-selector-option--income${flow === 'IN' ? ' is-active' : ''}`}
        aria-pressed={flow === 'IN'}
        onClick={() => onChange('IN')}
        disabled={disabled}
        data-cy={`${dataCy}-IN`}
        data-testid={`${dataCy}-IN`}
      >
        {incomeLabel}
      </Button>
      <Button
        type="button"
        color="light"
        className={`financial-flow-selector-option financial-flow-selector-option--expense${flow === 'OUT' ? ' is-active' : ''}`}
        aria-pressed={flow === 'OUT'}
        onClick={() => onChange('OUT')}
        disabled={disabled}
        data-cy={`${dataCy}-OUT`}
        data-testid={`${dataCy}-OUT`}
      >
        {expenseLabel}
      </Button>
    </ButtonGroup>
  </div>
);
