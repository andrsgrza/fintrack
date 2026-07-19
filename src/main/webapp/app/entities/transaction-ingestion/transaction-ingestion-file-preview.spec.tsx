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
const mockAxiosPatch = axios.patch as jest.Mock;
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

const rowForRecord = (recordId: number) => screen.getByTestId(`filePreviewRowStatus-${recordId}`).closest('tr') as HTMLElement;

const expectRowStatus = (recordId: number, status: string) => {
  expect(screen.getByTestId(`filePreviewRowStatus-${recordId}`).textContent).toBe(status);
};

const successfulPreviewResponse = {
  data: {
    transactionIngestionId: 100,
    fileIngestionId: 200,
    status: 'READY',
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
    status: 'PARTIALLY_READY',
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
    mockAxiosPatch.mockReset();
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockDispatch.mockImplementation(action => action);
    registerTranslations();
  });

  it('adds New File Import action on TransactionIngestion list', () => {
    renderList();

    const newFileImport = screen.getByRole('link', { name: /new file import/i });
    expect(newFileImport.getAttribute('href')).toBe('/transaction-ingestion/new');
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
    expect(screen.getByText('Needs review')).toBeTruthy();
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
    expect(screen.getAllByRole('button', { name: /edit/i })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: /disable/i })).toHaveLength(2);
    expect(screen.getByRole('button', { name: /enable/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
    expect(within(rowForRecord(300)).getByRole('button', { name: /edit/i })).toBeTruthy();
    expect(within(rowForRecord(300)).getByRole('button', { name: /disable/i })).toBeTruthy();
    expect(within(rowForRecord(300)).queryByRole('button', { name: /enable/i })).toBeNull();
    expect(within(rowForRecord(301)).getByRole('button', { name: /edit/i })).toBeTruthy();
    expect(within(rowForRecord(301)).getByRole('button', { name: /disable/i })).toBeTruthy();
    expect(within(rowForRecord(301)).queryByRole('button', { name: /enable/i })).toBeNull();
    expectRowStatus(302, 'Disabled');
    expect(within(rowForRecord(302)).queryByText('signedAmount must be nonzero')).toBeNull();
    expect(within(rowForRecord(302)).queryByRole('button', { name: /edit/i })).toBeNull();
    expect(within(rowForRecord(302)).queryByRole('button', { name: /disable/i })).toBeNull();
    expect(within(rowForRecord(302)).getByRole('button', { name: /enable/i })).toBeTruthy();
  });

  it('renders ingestion readiness and import-result status labels', async () => {
    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'READY' } });
    const { unmount } = renderPersistedReview();
    expect(await screen.findByText('Ready to import')).toBeTruthy();
    unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PARTIALLY_READY' } });
    const partiallyReady = renderPersistedReview();
    expect(await screen.findByText('Needs review')).toBeTruthy();
    partiallyReady.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'COMPLETED' } });
    const completed = renderPersistedReview();
    expect(await screen.findByText('Import completed')).toBeTruthy();
    completed.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PARTIALLY_COMPLETED' } });
    renderPersistedReview();
    expect(await screen.findByText('Import partially completed')).toBeTruthy();
  });

  it('disable action updates row status and counts from response', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 2, recordsRejected: 1, validRows: 0, invalidRows: 1 },
        row: { ...persistedReviewResponse.data.rows[0], status: 'DISABLED' },
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    fireEvent.click(screen.getAllByRole('button', { name: /disable/i })[0]);

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/300/disable'));
    await waitFor(() => expect(within(screen.getByTestId('filePreviewCounts')).getByText('2')).toBeTruthy());
    expect(screen.getByText('Needs review')).toBeTruthy();
    expectRowStatus(300, 'Disabled');
    expect(within(rowForRecord(300)).getByRole('button', { name: /enable/i })).toBeTruthy();
    expect(within(rowForRecord(300)).queryByRole('button', { name: /disable/i })).toBeNull();
    expect(within(rowForRecord(300)).queryByRole('button', { name: /edit/i })).toBeNull();
  });

  it('disable last valid row updates displayed batch status to needs review', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    });
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        row: { ...persistedReviewResponse.data.rows[0], status: 'DISABLED' },
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Ready to import')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /disable/i }));

    await waitFor(() => expect(screen.getByText('Needs review')).toBeTruthy());
    expectRowStatus(300, 'Disabled');
  });

  it('enable action updates row status and counts from response', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 1, validRows: 2, invalidRows: 1 },
        row: { ...persistedReviewResponse.data.rows[2], status: 'VALID' },
      },
    });
    renderPersistedReview();

    await screen.findByText('Disabled row');
    fireEvent.click(screen.getByRole('button', { name: /enable/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/302/enable'));
    await waitFor(() => expect(within(screen.getByTestId('filePreviewCounts')).getByText('2')).toBeTruthy());
    expect(screen.getByText('Needs review')).toBeTruthy();
    expectRowStatus(302, 'Valid');
    expect(within(rowForRecord(302)).getByRole('button', { name: /disable/i })).toBeTruthy();
    expect(within(rowForRecord(302)).queryByRole('button', { name: /enable/i })).toBeNull();
  });

  it('enable valid disabled row updates displayed batch status to ready', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [{ ...persistedReviewResponse.data.rows[0], status: 'DISABLED' }],
      },
    });
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        row: persistedReviewResponse.data.rows[0],
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Needs review')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /enable/i }));

    await waitFor(() => expect(screen.getByText('Ready to import')).toBeTruthy());
    expectRowStatus(300, 'Valid');
  });

  it('enable action updates invalid disabled row status to rejected from response', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 2, validRows: 1, invalidRows: 2 },
        row: {
          ...persistedReviewResponse.data.rows[2],
          status: 'REJECTED',
          errorCode: 'DESCRIPTION_REQUIRED',
          errorMessage: 'description is required',
        },
      },
    });
    renderPersistedReview();

    await screen.findByText('Disabled row');
    fireEvent.click(screen.getByRole('button', { name: /enable/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/302/enable'));
    expect(screen.getByText('Needs review')).toBeTruthy();
    expectRowStatus(302, 'Rejected');
    expect(within(rowForRecord(302)).getByText('description is required')).toBeTruthy();
    expect(within(rowForRecord(302)).getByRole('button', { name: /disable/i })).toBeTruthy();
  });

  it('edits a rejected row with valid values and renders it as valid', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPatch.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        row: {
          ...persistedReviewResponse.data.rows[1],
          status: 'VALID',
          transactionDate: '2026-07-14',
          description: 'Corrected row',
          signedAmount: '50.00',
          amount: '50.00',
          flow: 'IN',
          errorCode: null,
          errorMessage: null,
        },
      },
    });
    renderPersistedReview();

    await screen.findByText('signedAmount must be nonzero');
    fireEvent.click(screen.getAllByRole('button', { name: /edit/i })[1]);
    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-14' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Corrected row' } });
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '50.00' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/records/301', {
        transactionDate: '2026-07-14',
        postingDate: null,
        description: 'Corrected row',
        signedAmount: '50.00',
        currency: 'MXN',
        externalReference: null,
        notes: null,
      }),
    );
    expect(await screen.findByText('Corrected row')).toBeTruthy();
    expect(screen.getByText('Ready to import')).toBeTruthy();
    expectRowStatus(301, 'Valid');
    expect(screen.getAllByText('50.00')).toHaveLength(2);
    expect(screen.queryByText('signedAmount must be nonzero')).toBeNull();
  });

  it('editing signedAmount sign refreshes derived amount and flow from response row', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPatch.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
        row: {
          ...persistedReviewResponse.data.rows[0],
          status: 'VALID',
          signedAmount: '-100.00',
          amount: '100.00',
          flow: 'OUT',
          errorCode: null,
          errorMessage: null,
        },
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    expect(within(rowForRecord(300)).getByText('Income')).toBeTruthy();

    fireEvent.click(within(rowForRecord(300)).getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '-100.00' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith(
        'api/transaction-ingestions/100/records/300',
        expect.objectContaining({ signedAmount: '-100.00' }),
      ),
    );
    expect(within(rowForRecord(300)).getByText('-100.00')).toBeTruthy();
    expect(within(rowForRecord(300)).getByText('100.00')).toBeTruthy();
    expect(within(rowForRecord(300)).getByText('Expense')).toBeTruthy();
    expect(within(rowForRecord(300)).queryByText('Income')).toBeNull();
  });

  it('does not preserve stale derived fields when response row omits them', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPatch.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
        row: {
          ingestionRecordId: 300,
          recordIndex: 1,
          status: 'VALID',
          transactionDate: '2026-07-13',
          postingDate: '2026-07-13',
          description: 'Salary',
          signedAmount: '-100.00',
          currency: 'MXN',
          errorCode: null,
          errorMessage: null,
        },
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    fireEvent.click(within(rowForRecord(300)).getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '-100.00' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(within(rowForRecord(300)).getByText('-100.00')).toBeTruthy());
    expect(within(rowForRecord(300)).queryByText('Income')).toBeNull();
  });

  it('edits a valid row with invalid values and renders row error', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    mockAxiosPatch.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 2, validRows: 0, invalidRows: 2 },
        row: {
          ...persistedReviewResponse.data.rows[0],
          status: 'REJECTED',
          signedAmount: '0',
          amount: null,
          flow: null,
          errorCode: 'ZERO_SIGNED_AMOUNT',
          errorMessage: 'signedAmount must be nonzero',
        },
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    fireEvent.click(screen.getAllByRole('button', { name: /edit/i })[0]);
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(mockAxiosPatch).toHaveBeenCalledWith(expect.stringContaining('/records/300'), expect.any(Object)));
    await waitFor(() => expectRowStatus(300, 'Rejected'));
    expect(screen.getByText('Needs review')).toBeTruthy();
    expect(screen.getAllByText('signedAmount must be nonzero')).toHaveLength(2);
  });

  it('edit valid row to invalid updates displayed batch status to needs review', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    });
    mockAxiosPatch.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 1, validRows: 0, invalidRows: 1 },
        row: {
          ...persistedReviewResponse.data.rows[0],
          status: 'REJECTED',
          signedAmount: '0',
          amount: null,
          flow: null,
          errorCode: 'ZERO_SIGNED_AMOUNT',
          errorMessage: 'signedAmount must be nonzero',
        },
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Ready to import')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(screen.getByText('Needs review')).toBeTruthy());
    expectRowStatus(300, 'Rejected');
  });

  it('does not show edit for disabled rows', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    await screen.findByText('Disabled row');

    expect(within(rowForRecord(302)).queryByRole('button', { name: /edit/i })).toBeNull();
    expect(within(rowForRecord(302)).getByRole('button', { name: /enable/i })).toBeTruthy();
  });

  it('shows Confirm Import when review is ready with valid rows', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Ready to import')).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
    expect(screen.getByRole('button', { name: /confirm import/i })).toBeTruthy();
  });

  it('does not allow confirm when review needs fixes', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect(await screen.findByText('Needs review')).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
    expect(screen.getByText('Fix or disable rejected rows before importing.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
  });

  it('does not allow confirm when no valid rows exist', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [{ ...persistedReviewResponse.data.rows[0], status: 'DISABLED' }],
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('There are no valid rows to import.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
  });

  it('confirm success marks imported rows, leaves disabled rows, and makes review read-only', async () => {
    const readyRows = [{ ...persistedReviewResponse.data.rows[0] }, { ...persistedReviewResponse.data.rows[2] }];
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: readyRows,
      },
    });
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        status: 'COMPLETED',
        createdNow: 1,
        alreadyImported: 0,
        skipped: 1,
        rejected: 0,
        failed: 0,
        counts: { recordsReceived: 2, recordsCreated: 1, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [
          { ...readyRows[0], status: 'IMPORTED', financialTransactionId: 9001 },
          { ...readyRows[1], status: 'DISABLED' },
        ],
      },
    });
    renderPersistedReview();

    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/confirm'));
    await waitFor(() => expect(screen.getByText('Import completed')).toBeTruthy());
    expectRowStatus(300, 'Imported');
    expectRowStatus(302, 'Disabled');
    expect(within(screen.getByTestId('filePreviewCounts')).getAllByText('0')).toHaveLength(3);
    expect(screen.queryByText('Preview only — no transactions were created.')).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Error' })).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /disable/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /enable/i })).toBeNull();
  });

  it('completed review loads as read-only', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'COMPLETED',
        counts: { recordsReceived: 2, recordsCreated: 1, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [
          { ...persistedReviewResponse.data.rows[0], status: 'IMPORTED', financialTransactionId: 9001 },
          { ...persistedReviewResponse.data.rows[2], status: 'DISABLED', transactionDate: '' },
        ],
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Import completed')).toBeTruthy();
    expect(screen.queryByText('Preview only — no transactions were created.')).toBeNull();
    expectRowStatus(300, 'Imported');
    expectRowStatus(302, 'Disabled');
    expect(rowForRecord(302).textContent).toContain('Disabled row');
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Error' })).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /disable/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /enable/i })).toBeNull();
    expect(rowForRecord(300).querySelector('input, select, textarea')).toBeNull();
    expect(rowForRecord(302).querySelector('input, select, textarea')).toBeNull();
  });

  it('confirm API error displays review error', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    });
    mockAxiosPost.mockRejectedValue(new Error('confirm failed'));
    renderPersistedReview();

    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    expect(await screen.findByText('Could not confirm import. Check the review status and try again.')).toBeTruthy();
    expect(screen.queryByText('Import completed')).toBeNull();
    expect(screen.getByText('Preview only — no transactions were created.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /confirm import/i })).toBeTruthy();
    expect(within(rowForRecord(300)).getByRole('button', { name: /edit/i })).toBeTruthy();
    expect(within(rowForRecord(300)).getByRole('button', { name: /disable/i })).toBeTruthy();
  });

  it('cancel leaves edited row unchanged', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    await screen.findByText('Salary');
    fireEvent.click(screen.getAllByRole('button', { name: /edit/i })[0]);
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Unsaved edit' } });
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.getByText('Salary')).toBeTruthy();
    expect(screen.queryByText('Unsaved edit')).toBeNull();
    expect(mockAxiosPatch).not.toHaveBeenCalled();
  });

  it('does not render confirm import on create page', () => {
    renderFilePreview();

    expect(screen.queryByRole('button', { name: /confirm/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /import/i })).toBeNull();
  });
});
