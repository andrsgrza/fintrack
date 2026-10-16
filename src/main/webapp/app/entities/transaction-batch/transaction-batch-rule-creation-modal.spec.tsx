import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import TransactionBatchRuleCreationModal from './transaction-batch-rule-creation-modal';

jest.mock('app/entities/description-normalization-rule/components/description-normalization-rule-configured-form', () => ({
  __esModule: true,
  default: ({ initialValue, saveActions, onSaved }) => (
    <div data-testid="mockDnrEditor">
      <span data-testid="mockDnrCondition">{initialValue.conditions?.[0]?.value ?? ''}</span>
      <span data-testid="mockDnrResult">{initialValue.resultingDescription ?? ''}</span>
      {saveActions.map(action => (
        <button
          key={action.action}
          type="button"
          data-testid={`mockDnr-${action.action}`}
          onClick={() => onSaved({ id: 91, name: 'CSV rule' }, action.action)}
        >
          {action.action}
        </button>
      ))}
    </div>
  ),
}));

jest.mock('app/entities/transaction-rule/components/transaction-rule-configured-form', () => ({
  __esModule: true,
  default: ({ initialValue, saveActions, onSaved }) => (
    <div data-testid="mockTransactionEditor">
      <span data-testid="mockTransactionConditions">{initialValue.conditions?.map(condition => condition.field).join(',')}</span>
      {saveActions.map(action => (
        <button
          key={action.action}
          type="button"
          data-testid={`mockTransaction-${action.action}`}
          onClick={() => onSaved({ id: 92, name: 'Classification rule' }, action.action)}
        >
          {action.action}
        </button>
      ))}
    </div>
  ),
}));

describe('TransactionBatchRuleCreationModal', () => {
  const csvRow = {
    candidateId: 8,
    rowPosition: 3,
    source: 'FILE_IMPORT' as const,
    sourceDescription: 'RAW UBER 123',
    currentDescription: 'Uber',
    flow: 'OUT',
    accountName: 'Cuenta de prueba',
    categoryId: 21,
    categoryName: 'Transporte',
    tagIds: [31],
    tagNames: ['Trabajo'],
  };

  it('prefills DNR only from the temporary CSV baseline and keeps account out of TransactionRule conditions', () => {
    const onClose = jest.fn();
    const { rerender } = render(
      <TransactionBatchRuleCreationModal
        context={{ kind: 'NORMALIZATION', row: csvRow }}
        onClose={onClose}
        onCreated={jest.fn()}
        onReevaluate={jest.fn()}
      />,
    );

    expect(screen.getByTestId('mockDnrCondition').textContent).toBe('RAW UBER 123');
    expect(screen.getByTestId('mockDnrResult').textContent).toBe('Uber');

    rerender(
      <TransactionBatchRuleCreationModal
        context={{ kind: 'TRANSACTION', row: { ...csvRow, source: 'MANUAL', sourceDescription: null } }}
        onClose={onClose}
        onCreated={jest.fn()}
        onReevaluate={jest.fn()}
      />,
    );
    expect(screen.getByTestId('mockTransactionConditions').textContent).toBe('DESCRIPTION,FLOW');
    expect(screen.getByText(/cuenta se muestra sólo como contexto/i)).toBeTruthy();
  });

  it('keeps a real creation as partial success when follow-up reevaluation fails, then retries without another create', async () => {
    const onCreated = jest.fn();
    const onClose = jest.fn();
    const onReevaluate = jest.fn().mockRejectedValueOnce(new Error('unavailable')).mockResolvedValueOnce(undefined);
    render(
      <TransactionBatchRuleCreationModal
        context={{ kind: 'NORMALIZATION', row: csvRow }}
        onClose={onClose}
        onCreated={onCreated}
        onReevaluate={onReevaluate}
      />,
    );

    fireEvent.click(screen.getByTestId('mockDnr-REEVALUATE_ROW'));

    await screen.findByTestId('batchContextualRulePartialSuccess');
    expect(onCreated).toHaveBeenCalledTimes(1);
    expect(onReevaluate).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByTestId('batchContextualRuleRetryReevaluation'));
    await waitFor(() => expect(onReevaluate).toHaveBeenCalledTimes(2));
    expect(onCreated).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
