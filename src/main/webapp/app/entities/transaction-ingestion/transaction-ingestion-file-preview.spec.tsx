import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionIngestion from 'app/../i18n/en/transactionIngestion.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enIngestionStatus from 'app/../i18n/en/ingestionStatus.json';
import enIngestionRecordStatus from 'app/../i18n/en/ingestionRecordStatus.json';
import { TransactionIngestion } from './transaction-ingestion';
import { TransactionIngestionFilePreview } from './transaction-ingestion-file-preview';

jest.mock('axios');

const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosGet = axios.get as jest.Mock;
const mockDispatch = jest.fn();
const mockGetFinancialAccounts = jest.fn(params => ({ type: 'financialAccount/getEntities', payload: params }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: params => mockGetFinancialAccounts(params),
}));

const baseState = {
  financialAccount: {
    entities: [
      { id: 10, name: 'Checking account', currency: 'MXN' },
      { id: 11, name: 'Cash account', currency: 'USD' },
    ],
    loading: false,
  },
  transactionIngestion: {
    entities: [],
    loading: false,
    totalItems: 0,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enTransactionIngestion);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enIngestionStatus);
  TranslatorContext.registerTranslations('en', enIngestionRecordStatus);
  TranslatorContext.setLocale('en');
};

const renderFilePreview = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-ingestion/file-preview/new']}>
      <Routes>
        <Route path="/transaction-ingestion/file-preview/new" element={<TransactionIngestionFilePreview />} />
        <Route path="/transaction-ingestion/:id/file-preview" element={<TransactionIngestionFilePreview />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderPersistedReview = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-ingestion/100/file-preview']}>
      <Routes>
        <Route path="/transaction-ingestion/:id/file-preview" element={<TransactionIngestionFilePreview />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderList = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-ingestion']}>
      <Routes>
        <Route path="/transaction-ingestion" element={<TransactionIngestion />} />
      </Routes>
    </MemoryRouter>,
  );
};

const uploadCsv = () => {
  const file = new File(['transactionDate,postingDate,description,signedAmount,currency,externalReference,notes'], 'preview.csv', {
    type: 'text/csv',
  });
  const input = screen.getByLabelText('CSV file') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
  return input;
};

const selectAccount = () => {
  const input = screen.getByLabelText('Account') as HTMLSelectElement;
  fireEvent.change(input, { target: { value: '10' } });
  return input;
};

const successfulPreviewResponse = {
  data: {
    transactionIngestionId: 100,
    fileIngestionId: 200,
    status: 'COMPLETED',
    sourceLabel: 'Canonical CSV: preview.csv',
    counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
    warnings: [],
    rows: [],
  },
};

const persistedReviewResponse = {
  data: {
    transactionIngestionId: 100,
    fileIngestionId: 200,
    status: 'PARTIALLY_COMPLETED',
    sourceLabel: 'Canonical CSV: preview.csv',
    counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
    warnings: [],
    fileMetadata: {
      originalFilename: 'preview.csv',
      fileType: 'CSV',
      contentType: 'text/csv',
      fileSizeBytes: 123,
      checksum: 'abc123',
      parserName: 'fintrack-canonical-csv',
      parserVersion: '1.0',
      statementStartDate: '2026-07-13',
      statementEndDate: '2026-07-14',
    },
    rows: [
      {
        ingestionRecordId: 300,
        recordIndex: 1,
        status: 'VALID',
        transactionDate: '2026-07-13',
        postingDate: '2026-07-13',
        description: 'Salary',
        signedAmount: '100.00',
        amount: '100.00',
        flow: 'IN',
        currency: 'MXN',
        externalReference: 'abc',
      },
      {
        ingestionRecordId: 301,
        recordIndex: 2,
        status: 'REJECTED',
        description: '',
        signedAmount: '0',
        currency: 'MXN',
        errorCode: 'ZERO_SIGNED_AMOUNT',
        errorMessage: 'signedAmount must be nonzero',
      },
      {
        ingestionRecordId: 302,
        recordIndex: 3,
        status: 'DISABLED',
        description: 'Disabled row',
        signedAmount: '-12.00',
        amount: '12.00',
        flow: 'OUT',
        currency: 'MXN',
      },
      {
        ingestionRecordId: 303,
        recordIndex: 4,
        status: 'IMPORTED',
        description: 'Imported row',
        signedAmount: '-10.00',
        amount: '10.00',
        flow: 'OUT',
        currency: 'MXN',
      },
    ],
  },
};

describe('TransactionIngestion file preview workflow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAxiosPost.mockReset();
    mockAxiosGet.mockReset();
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockDispatch.mockImplementation(action => action);
    registerTranslations();
  });

  it('adds New File Import action on TransactionIngestion list', () => {
    renderList();

    const newFileImport = screen.getByRole('link', { name: /new file import/i });
    expect(newFileImport.getAttribute('href')).toBe('/transaction-ingestion/file-preview/new');
  });

  it('renders account selector, file input, and Preview button', () => {
    renderFilePreview();

    expect(mockGetFinancialAccounts).toHaveBeenCalledWith({ sort: 'name,asc' });
    expect(screen.getByRole('heading', { name: /file import preview/i })).toBeTruthy();
    expect(screen.getByLabelText('Account')).toBeTruthy();
    expect(screen.getByText('Checking account (MXN)')).toBeTruthy();
    expect(screen.getByLabelText('CSV file')).toBeTruthy();
    expect(screen.getByRole('button', { name: /preview/i })).toBeTruthy();
    expect(screen.getAllByText('Preview only — no transactions were created.')).toHaveLength(1);
  });

  it('requires account before submit', async () => {
    renderFilePreview();
    uploadCsv();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    expect(await screen.findByText('Account is required.')).toBeTruthy();
    expect(mockAxiosPost).not.toHaveBeenCalled();
  });

  it('requires file before submit', async () => {
    renderFilePreview();
    selectAccount();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    expect(await screen.findByText('CSV file is required.')).toBeTruthy();
    expect(mockAxiosPost).not.toHaveBeenCalled();
  });

  it('submits multipart request to file-preview endpoint', async () => {
    mockAxiosPost.mockResolvedValue(successfulPreviewResponse);
    renderFilePreview();
    selectAccount();
    uploadCsv();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledTimes(1));
    expect(mockAxiosPost).toHaveBeenCalledWith(
      'api/transaction-ingestions/file-preview',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
    );

    const formData = mockAxiosPost.mock.calls[0][1] as FormData;
    expect(formData.get('accountId')).toBe('10');
    expect(formData.get('file')).toBeTruthy();
  });

  it('disables Preview while upload is in progress', async () => {
    let resolvePreview;
    mockAxiosPost.mockReturnValue(
      new Promise(resolve => {
        resolvePreview = resolve;
      }),
    );
    renderFilePreview();
    selectAccount();
    uploadCsv();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    await waitFor(() => expect(screen.getByRole('button', { name: /preview/i }).hasAttribute('disabled')).toBe(true));

    resolvePreview(successfulPreviewResponse);
    await screen.findByRole('heading', { name: /review import|file import preview/i });
  });

  it('redirects to persisted review route after successful preview', async () => {
    mockAxiosPost.mockResolvedValue(successfulPreviewResponse);
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderFilePreview();
    const account = selectAccount();
    const fileInput = uploadCsv();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    expect(await screen.findByText('preview.csv')).toBeTruthy();
    expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-ingestions/100/file-preview');
    expect(account.value).toBe('10');
    expect(fileInput.value).toBe('');
  });

  it('clears selected file after failed preview while preserving account', async () => {
    mockAxiosPost.mockRejectedValue(new Error('upload failed'));
    renderFilePreview();
    const account = selectAccount();
    const fileInput = uploadCsv();

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    expect(await screen.findByText('Could not create file preview. Check the file and try again.')).toBeTruthy();
    expect(account.value).toBe('10');
    expect(fileInput.value).toBe('');

    fireEvent.click(screen.getByRole('button', { name: /preview/i }));

    expect(await screen.findByText('CSV file is required.')).toBeTruthy();
    expect(mockAxiosPost).toHaveBeenCalledTimes(1);
  });

  it('review page loads persisted preview metadata, counts, statuses, and actions', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect(await screen.findByText('preview.csv')).toBeTruthy();
    expect(screen.getByText('fintrack-canonical-csv 1.0')).toBeTruthy();
    expect(screen.getByText('Preview completed with errors')).toBeTruthy();
    const counts = screen.getByTestId('filePreviewCounts');
    expect(within(counts).getByText('Records received')).toBeTruthy();
    expect(within(counts).getByText('Valid rows')).toBeTruthy();
    expect(within(counts).getByText('Invalid rows')).toBeTruthy();
    expect(within(counts).getByText('Disabled rows')).toBeTruthy();
    expect(within(counts).getByText('Records rejected')).toBeTruthy();
    expect(within(counts).getByText('3')).toBeTruthy();
    expect(within(counts).getAllByText('1')).toHaveLength(4);

    expect(screen.getByText('Valid')).toBeTruthy();
    expect(screen.getByText('Rejected')).toBeTruthy();
    expect(screen.getByText('Disabled')).toBeTruthy();
    expect(screen.getByText('Imported')).toBeTruthy();
    expect(screen.getByText('Income')).toBeTruthy();
    expect(screen.getByText('signedAmount must be nonzero')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: /disable/i })).toHaveLength(2);
    expect(screen.getByRole('button', { name: /enable/i })).toBeTruthy();
  });

  it('disable action updates row status and counts from response', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 2, recordsRejected: 1, validRows: 0, invalidRows: 1 },
        row: { ...persistedReviewResponse.data.rows[0], status: 'DISABLED' },
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    fireEvent.click(screen.getAllByRole('button', { name: /disable/i })[0]);

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/300/disable'));
    await waitFor(() => expect(within(screen.getByTestId('filePreviewCounts')).getByText('2')).toBeTruthy());
    expect(screen.getByText('Disabled')).toBeTruthy();
  });

  it('enable action updates row status and counts from response', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 1, validRows: 2, invalidRows: 1 },
        row: { ...persistedReviewResponse.data.rows[2], status: 'VALID' },
      },
    });
    renderPersistedReview();

    await screen.findByText('Disabled row');
    fireEvent.click(screen.getByRole('button', { name: /enable/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/302/enable'));
    await waitFor(() => expect(within(screen.getByTestId('filePreviewCounts')).getByText('2')).toBeTruthy());
    expect(screen.getByText('Valid')).toBeTruthy();
  });

  it('does not render a confirm/import action', () => {
    renderFilePreview();

    expect(screen.queryByRole('button', { name: /confirm/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /import/i })).toBeNull();
  });
});
