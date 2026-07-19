import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionIngestion from 'app/../i18n/en/transactionIngestion.json';
import enIngestionType from 'app/../i18n/en/ingestionType.json';
import { TransactionIngestionUpdate } from './transaction-ingestion-update';

jest.mock('axios');

const mockAxiosPost = axios.post as jest.Mock;
const mockDispatch = jest.fn();
const mockGetFinancialAccounts = jest.fn(params => ({ type: 'financialAccount/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'transactionIngestion/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'transactionIngestion/reset' }));
const mockUpdateEntity = jest.fn(entity => ({ type: 'transactionIngestion/updateEntity', payload: entity }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: params => mockGetFinancialAccounts(params),
}));

jest.mock('./transaction-ingestion.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  updateEntity: entity => mockUpdateEntity(entity),
}));

const baseState = {
  financialAccount: {
    entities: [
      { id: 10, name: 'Checking account' },
      { id: 11, name: 'Cash account' },
    ],
  },
  transactionIngestion: {
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enTransactionIngestion);
  TranslatorContext.registerTranslations('en', enIngestionType);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-ingestion/new']}>
      <Routes>
        <Route path="/transaction-ingestion/new" element={<TransactionIngestionUpdate />} />
        <Route path="/transaction-ingestion/:id" element={<div>Review route</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const selectAccount = () => {
  const account = screen.getByLabelText('Account') as HTMLSelectElement;
  fireEvent.change(account, { target: { value: '10' } });
  return account;
};

const uploadFile = () => {
  const file = new File(['transactionDate,postingDate,description,signedAmount,currency,externalReference,notes'], 'upload.csv', {
    type: 'text/csv',
  });
  const input = screen.getByLabelText('CSV file') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
  return input;
};

describe('TransactionIngestion create workflow form', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('shows only Account and Ingestion Type parent fields in create mode', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Account')).not.toBeNull();
    expect(screen.getByLabelText('Ingestion Type')).not.toBeNull();
    expect(screen.getByLabelText('CSV file')).not.toBeNull();

    expect(screen.queryByLabelText('Status')).toBeNull();
    expect(screen.queryByLabelText('Source Label')).toBeNull();
    expect(screen.queryByLabelText('Started At')).toBeNull();
    expect(screen.queryByLabelText('Completed At')).toBeNull();
    expect(screen.queryByLabelText('Records Received')).toBeNull();
    expect(screen.queryByLabelText('Records Created')).toBeNull();
    expect(screen.queryByLabelText('Records Skipped')).toBeNull();
    expect(screen.queryByLabelText('Records Rejected')).toBeNull();
    expect(screen.queryByLabelText('Error Message')).toBeNull();
    expect(screen.queryByLabelText('Created At')).toBeNull();
  });

  it('shows API TBD placeholder and does not submit unsupported API flow', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Ingestion Type'), { target: { value: 'API' } });

    expect(screen.getByText('API ingestion is coming soon.')).not.toBeNull();
    expect(screen.queryByLabelText('CSV file')).toBeNull();
    expect(screen.getByRole('button', { name: /Create workflow/ })).toHaveProperty('disabled', true);
  });

  it('submits FILE create as multipart to canonical workflow endpoint and redirects to review route', async () => {
    mockAxiosPost.mockResolvedValue({ data: { transactionIngestionId: 100 } });
    renderCreateForm();

    selectAccount();
    uploadFile();
    fireEvent.click(screen.getByRole('button', { name: /Create workflow/ }));

    await waitFor(() => {
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/transaction-ingestions/file',
        expect.any(FormData),
        expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
      );
    });
    await waitFor(() => expect(screen.getByText('Review route')).not.toBeNull());
  });

  it('shows backend errors and clears the file input after failed create', async () => {
    mockAxiosPost.mockRejectedValue({ response: { data: { detail: 'CSV file is required' } } });
    renderCreateForm();

    selectAccount();
    const input = uploadFile();
    fireEvent.click(screen.getByRole('button', { name: /Create workflow/ }));

    await waitFor(() => expect(screen.getByText('CSV file is required')).not.toBeNull());
    expect(input.value).toBe('');
  });
});
