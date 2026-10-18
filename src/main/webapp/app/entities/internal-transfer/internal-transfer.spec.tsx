import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import enAccountType from 'app/../i18n/en/accountType.json';
import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enInternalTransfer from 'app/../i18n/en/internalTransfer.json';
import { InternalTransfer } from './internal-transfer';
import { InternalTransferUpdate } from './internal-transfer-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPost = axios.post as jest.Mock;

const accounts = [
  { id: 10, name: 'Everyday debit', accountType: 'DEBIT', currency: 'MXN', active: true },
  { id: 11, name: 'Savings', accountType: 'DEBIT', currency: 'MXN', active: true },
];

const transfer = {
  transferId: 70,
  version: 0,
  transactionDate: '2026-10-17',
  amount: 125.5,
  currency: 'MXN',
  notes: 'Move to savings',
  source: {
    transactionId: 701,
    accountId: 10,
    accountName: 'Everyday debit',
    accountType: 'DEBIT',
    currency: 'MXN',
    transactionDate: '2026-10-17',
    postingDate: '2026-10-18',
    description: 'Monthly saving',
    amount: 125.5,
    flow: 'OUT',
    origin: 'MANUAL',
  },
  destination: {
    transactionId: 702,
    accountId: 11,
    accountName: 'Savings',
    accountType: 'DEBIT',
    currency: 'MXN',
    transactionDate: '2026-10-17',
    postingDate: '2026-10-19',
    description: 'Monthly saving',
    amount: 125.5,
    flow: 'IN',
    origin: 'MANUAL',
  },
};

describe('InternalTransfer product UI', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enInternalTransfer);
    TranslatorContext.registerTranslations('en', enAccountType);
    TranslatorContext.registerTranslations('en', enFinancialTransaction);
    TranslatorContext.setLocale('en');
    mockAxiosGet.mockReset();
    mockAxiosPost.mockReset();
  });

  it('renders a human-readable transfer list without raw identifiers', async () => {
    mockAxiosGet.mockImplementation(url => {
      if (url === 'api/financial-accounts/selectable') return Promise.resolve({ data: accounts });
      if (url === 'api/internal-transfers/product')
        return Promise.resolve({ data: { content: [transfer], totalElements: 1, totalPages: 1 } });
      return Promise.reject(new Error(`unexpected ${url}`));
    });

    const { container } = render(<InternalTransfer />, { wrapper: MemoryRouter });

    await waitFor(() => expect(container.querySelector('[data-cy="internalTransferListItem"]')).toBeTruthy());
    expect(container.querySelector('[data-cy="internalTransferSourceLeg"]')?.textContent).toContain('Everyday debit');
    expect(container.querySelector('[data-cy="internalTransferDestinationLeg"]')?.textContent).toContain('Savings');
    expect(screen.getByText('125.50 MXN')).toBeTruthy();
    expect(screen.getAllByText('Monthly saving')).toHaveLength(2);
    expect(screen.queryByText('70')).toBeNull();
    expect(screen.queryByText('701')).toBeNull();
    expect(container.querySelector('[data-cy="internalTransferSourceLeg"]')).toBeTruthy();
    expect(container.querySelector('[data-cy="internalTransferDestinationLeg"]')).toBeTruthy();
    expect(container.querySelector('[data-cy="internalTransferEdit"]')?.getAttribute('href')).toBe('/internal-transfers/70/edit');
    expect(mockAxiosGet).toHaveBeenCalledWith('api/internal-transfers/product', {
      params: expect.objectContaining({ sort: 'outgoingTransaction.transactionDate,DESC' }),
    });
  });

  it('renders the product empty state and its create CTA', async () => {
    mockAxiosGet.mockImplementation(url => {
      if (url === 'api/financial-accounts/selectable') return Promise.resolve({ data: accounts });
      return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } });
    });

    render(<InternalTransfer />, { wrapper: MemoryRouter });

    await waitFor(() => expect(screen.getByText('No internal transfers')).toBeTruthy());
    expect(screen.getAllByRole('link', { name: 'New transfer' })[0].getAttribute('href')).toBe('/internal-transfers/new');
  });

  it('creates through the atomic command with shared transaction date', async () => {
    mockAxiosGet.mockResolvedValue({ data: accounts });
    mockAxiosPost.mockResolvedValue({ data: transfer });

    render(
      <MemoryRouter initialEntries={['/internal-transfers/new']}>
        <Routes>
          <Route path="/internal-transfers/new" element={<InternalTransferUpdate />} />
          <Route path="/internal-transfers/:id" element={<div>created detail</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByLabelText('From').querySelector('option[value="10"]')).toBeTruthy());
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '10' } });
    await waitFor(() => expect(screen.getByLabelText('To').querySelector('option[value="10"]')).toBeNull());
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '11' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '125.50' } });
    fireEvent.change(screen.getByLabelText('Source description'), { target: { value: 'Transfer out' } });
    fireEvent.change(screen.getByLabelText('Destination description'), { target: { value: 'Transfer in' } });
    const saveButton = document.querySelector('[data-cy="saveInternalTransfer"]');
    if (!saveButton) throw new Error('Expected the transfer save button');
    fireEvent.click(saveButton);

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalled());
    expect(mockAxiosPost).toHaveBeenCalledWith(
      'api/internal-transfers/commands/create',
      expect.objectContaining({
        sourceAccountId: 10,
        destinationAccountId: 11,
        amount: 125.5,
        transactionDate: expect.any(String),
        sourcePostingDate: expect.any(String),
        destinationPostingDate: expect.any(String),
        sourceDescription: 'Transfer out',
        destinationDescription: 'Transfer in',
      }),
    );
  });

  it('prevents using the same account for both legs', async () => {
    mockAxiosGet.mockResolvedValue({ data: accounts });
    render(
      <MemoryRouter>
        <InternalTransferUpdate />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByLabelText('From').querySelector('option[value="10"]')).toBeTruthy());
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '10' } });
    await waitFor(() => expect(screen.getByLabelText('To').querySelector('option[value="10"]')).toBeNull());
  });
});
