import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter } from 'react-router-dom';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enInternalTransfer from 'app/../i18n/en/internalTransfer.json';
import enAccountType from 'app/../i18n/en/accountType.json';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import {
  createCounterpartForTransaction,
  findEligibleCounterparts,
  getInternalTransferForTransaction,
} from 'app/entities/internal-transfer/internal-transfer-product.service';
import { FinancialTransactionInternalTransfer } from './financial-transaction-internal-transfer';

jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));

jest.mock('app/entities/internal-transfer/internal-transfer-product.service', () => ({
  createCounterpartForTransaction: jest.fn(),
  findEligibleCounterparts: jest.fn(),
  getInternalTransferForTransaction: jest.fn(),
  internalTransferErrorKey: jest.fn(() => 'unexpectedError'),
  linkExistingTransactions: jest.fn(),
}));

jest.mock('app/entities/internal-transfer/internal-transfer-unlink-modal', () => ({
  InternalTransferUnlinkModal: () => null,
}));

const mockGetSelectableFinancialAccounts = getSelectableFinancialAccounts as jest.Mock;
const mockGetInternalTransferForTransaction = getInternalTransferForTransaction as jest.Mock;
const mockCreateCounterpartForTransaction = createCounterpartForTransaction as jest.Mock;
const mockFindEligibleCounterparts = findEligibleCounterparts as jest.Mock;

const transaction = {
  id: 12,
  account: { id: 1, name: 'Checking', accountType: 'DEBIT', currency: 'MXN', lastFourDigits: '1234', active: true },
  transactionDate: '2026-10-18',
  postingDate: '2026-10-19',
  description: 'Transfer out',
  externalReference: 'ref-12',
  notes: 'month end',
  amount: 100,
  flow: 'OUT' as const,
};

const linkedTransfer = {
  transferId: 30,
  version: 2,
  source: {
    transactionId: 12,
    accountId: 1,
    accountName: 'Checking',
    description: 'Transfer out',
    amount: 100,
    flow: 'OUT',
  },
  destination: {
    transactionId: 13,
    accountId: 2,
    accountName: 'Savings',
    description: 'Transfer in',
    amount: 100,
    flow: 'IN',
  },
};

const renderComponent = (nextTransaction = transaction) =>
  render(
    <MemoryRouter>
      <FinancialTransactionInternalTransfer transaction={nextTransaction} />
    </MemoryRouter>,
  );

describe('FinancialTransactionInternalTransfer', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enFinancialTransaction);
    TranslatorContext.registerTranslations('en', enInternalTransfer);
    TranslatorContext.registerTranslations('en', enAccountType);
    TranslatorContext.setLocale('en');
  });

  beforeEach(() => {
    mockGetSelectableFinancialAccounts.mockReset();
    mockGetInternalTransferForTransaction.mockReset();
    mockCreateCounterpartForTransaction.mockReset();
    mockFindEligibleCounterparts.mockReset();
  });

  it('shows a compact linked state with navigation to the aggregate and counterpart, without exposing an id as product text', async () => {
    mockGetInternalTransferForTransaction.mockResolvedValue(linkedTransfer);
    renderComponent();

    await waitFor(() =>
      expect(document.querySelector('[data-cy="financialTransactionInternalTransferCounterpart"]')?.textContent).toContain(
        'Savings · Transfer in',
      ),
    );
    expect(screen.getByRole('link', { name: 'View internal transfer' }).getAttribute('href')).toBe('/internal-transfers/30');
    expect(screen.getByRole('link', { name: 'View counterpart transaction' }).getAttribute('href')).toBe('/financial-transaction/13');
    expect(document.querySelector('[data-cy="financialTransactionInternalTransferCounterpart"]')?.textContent).not.toContain('13');
  });

  it('lets an unlinked posted transaction create a counterpart without duplicating the current leg', async () => {
    mockGetInternalTransferForTransaction.mockRejectedValue({ response: { status: 404 } });
    mockGetSelectableFinancialAccounts.mockResolvedValue([
      transaction.account,
      { id: 2, name: 'Savings', accountType: 'DEBIT', currency: 'MXN', lastFourDigits: '9876', active: true },
    ]);
    mockCreateCounterpartForTransaction.mockResolvedValue(linkedTransfer);
    renderComponent();

    fireEvent.click(await screen.findByRole('button', { name: 'Mark as internal transfer' }));
    expect(screen.getByLabelText('Counterpart posting date').value).toBe('2026-10-19');
    expect(screen.getByLabelText('Counterpart description').value).toBe('Transfer out');
    expect(screen.getByLabelText('Counterpart reference').value).toBe('ref-12');
    expect(screen.getByLabelText('Counterpart notes').value).toBe('month end');
    fireEvent.change(await screen.findByLabelText('Counterpart account'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Counterpart posting date'), { target: { value: '2026-10-19' } });
    fireEvent.change(screen.getByLabelText('Counterpart description'), { target: { value: 'Transfer in' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Create counterpart' }).at(-1));

    await waitFor(() =>
      expect(mockCreateCounterpartForTransaction).toHaveBeenCalledWith(12, {
        targetAccountId: 2,
        postingDate: '2026-10-19',
        description: 'Transfer in',
        externalReference: 'ref-12',
        notes: 'month end',
      }),
    );
    await waitFor(() =>
      expect(document.querySelector('[data-cy="financialTransactionInternalTransferCounterpart"]')?.textContent).toContain(
        'Savings · Transfer in',
      ),
    );
  });

  it('loads eligible existing counterparts on entry, filters them server-side, and renders a readable selected state', async () => {
    mockGetInternalTransferForTransaction.mockRejectedValue({ response: { status: 404 } });
    mockGetSelectableFinancialAccounts.mockResolvedValue([
      transaction.account,
      { id: 2, name: 'Savings', accountType: 'DEBIT', currency: 'MXN', lastFourDigits: '9876', active: true },
    ]);
    mockFindEligibleCounterparts.mockResolvedValue({
      content: [
        {
          transactionId: 13,
          accountId: 2,
          accountName: 'Savings',
          transactionDate: '2026-10-18',
          postingDate: '2026-10-19',
          description: 'Transfer in',
          amount: 100,
          currency: 'MXN',
          flow: 'IN',
        },
      ],
      totalElements: 1,
      totalPages: 1,
    });
    renderComponent();

    fireEvent.click(await screen.findByRole('button', { name: 'Mark as internal transfer' }));
    fireEvent.click(screen.getByRole('button', { name: 'Link existing counterpart' }));

    await waitFor(() => expect(mockFindEligibleCounterparts).toHaveBeenCalledWith(12, { search: '', accountId: undefined, page: 0 }));
    const result = await screen.findByRole('option', { name: /transfer in.*savings.*2026-10-18.*100 mxn/i });
    fireEvent.click(result);
    expect(result.getAttribute('aria-selected')).toBe('true');
    expect(document.querySelector('[data-cy="postedTransferSelectedCounterpart"]')?.textContent).toContain('Transfer in · Savings');

    fireEvent.change(screen.getByLabelText('Counterpart account filter'), { target: { value: '2' } });
    await waitFor(() => expect(mockFindEligibleCounterparts).toHaveBeenLastCalledWith(12, { search: '', accountId: 2, page: 0 }));
  });
});
