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
import { TransactionIngestionWorkflowDetail } from './transaction-ingestion-workflow-detail';

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
    entity: {
      id: 100,
      ingestionType: 'FILE',
      status: 'PARTIALLY_READY',
      sourceLabel: 'Canonical CSV: workflow.csv',
      startedAt: '2026-07-13T16:00:00Z',
      completedAt: '2026-07-13T16:01:00Z',
      recordsReceived: 3,
      recordsCreated: 0,
      recordsSkipped: 1,
      recordsRejected: 1,
      errorMessage: null,
      createdAt: '2026-07-13T15:59:00Z',
      account: { id: 10, name: 'Checking account' },
    },
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

const renderPersistedReview = (path = '/transaction-ingestion/100', entity = baseState.transactionIngestion.entity) => {
  mockState = {
    ...baseState,
    transactionIngestion: {
      ...baseState.transactionIngestion,
      entity,
    },
  };

  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/transaction-ingestion/:id" element={<TransactionIngestionWorkflowDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderList = () => {
  mockState = {
    ...baseState,
    transactionIngestion: {
      ...baseState.transactionIngestion,
      entities: [baseState.transactionIngestion.entity],
      totalItems: 1,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/transaction-ingestion']}>
      <Routes>
        <Route path="/transaction-ingestion" element={<TransactionIngestion />} />
      </Routes>
    </MemoryRouter>,
  );
};

const rowForRecord = (recordId: number) => screen.getByTestId(`workflowRowStatus-${recordId}`).closest('tr') as HTMLElement;

const expectRowStatus = (recordId: number, status: string) => {
  expect(screen.getByTestId(`workflowRowStatus-${recordId}`).textContent).toBe(status);
};

const persistedReviewResponse = {
  data: {
    transactionIngestionId: 100,
    fileIngestionId: 200,
    status: 'PARTIALLY_READY',
    sourceLabel: 'Canonical CSV: workflow.csv',
    counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
    warnings: [],
    fileMetadata: {
      originalFilename: 'workflow.csv',
      fileType: 'CSV',
      contentType: 'text/csv',
      fileSizeBytes: 123,
      checksum: 'abc123',
      storageKey: null,
      parserName: 'fintrack-canonical-csv',
      parserVersion: '1.0',
      statementStartDate: '2026-07-13',
      statementEndDate: '2026-07-14',
      createdAt: '2026-07-13T16:00:00Z',
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

describe('TransactionIngestion file workflow', () => {
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

  it('does not render TransactionIngestion list Edit action', () => {
    renderList();

    expect(screen.getByRole('link', { name: /view/i }).getAttribute('href')).toBe('/transaction-ingestion/100');
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.getByRole('button', { name: /delete/i })).toBeTruthy();
  });

  it('canonical detail page loads parent summary, file metadata, counts, statuses, and actions', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect(await screen.findByText('workflow.csv')).toBeTruthy();
    expect(screen.getByText('Parent summary')).toBeTruthy();
    expect(screen.getByText('Checking account')).toBeTruthy();
    expect(screen.getByText('FILE')).toBeTruthy();
    expect(screen.getByText('Parser name')).toBeTruthy();
    expect(screen.getByText('fintrack-canonical-csv')).toBeTruthy();
    expect(screen.getByText('Parser version')).toBeTruthy();
    expect(screen.getByText('1.0')).toBeTruthy();
    expect(screen.getByText('Statement start date')).toBeTruthy();
    expect(screen.getAllByText('2026-07-13').length).toBeGreaterThan(0);
    expect(screen.getByText('Statement end date')).toBeTruthy();
    expect(screen.getByText('2026-07-14')).toBeTruthy();
    expect(screen.getByText('Ingestion records')).toBeTruthy();
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
    const counts = screen.getByTestId('workflowCounts');
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

  it('does not render TransactionIngestion workflow detail Edit action', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect(await screen.findByText('workflow.csv')).toBeTruthy();
    expect(screen.getByRole('link', { name: /back/i }).getAttribute('href')).toBe('/transaction-ingestion');
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
  });

  it('API ingestion detail shows TBD placeholder and does not load file workflow', async () => {
    renderPersistedReview('/transaction-ingestion/100', {
      ...baseState.transactionIngestion.entity,
      ingestionType: 'API',
      status: 'PENDING',
      sourceLabel: 'API ingestion',
    });

    expect(await screen.findByText('API ingestion detail is not implemented yet.')).toBeTruthy();
    expect(mockAxiosGet).not.toHaveBeenCalled();
    expect(screen.queryByTestId('workflowCounts')).toBeNull();
  });

  it('PENDING FILE ingestion without file metadata shows unavailable state without crashing', async () => {
    mockAxiosGet.mockRejectedValue(new Error('not available'));
    renderPersistedReview('/transaction-ingestion/100', {
      ...baseState.transactionIngestion.entity,
      status: 'PENDING',
      sourceLabel: null,
      recordsReceived: 0,
      recordsSkipped: 0,
      recordsRejected: 0,
    });

    expect(await screen.findByText('No file workflow metadata or review rows are available yet.')).toBeTruthy();
    expect(screen.getByText('Parent summary')).toBeTruthy();
    expect(screen.queryByTestId('workflowCounts')).toBeNull();
  });

  it('renders ingestion readiness and import-result status labels', async () => {
    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'READY' } });
    const { unmount } = renderPersistedReview();
    expect((await screen.findAllByText('Ready to import')).length).toBeGreaterThan(0);
    unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PARTIALLY_READY' } });
    const partiallyReady = renderPersistedReview();
    expect((await screen.findAllByText('Needs review')).length).toBeGreaterThan(0);
    partiallyReady.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'COMPLETED' } });
    const completed = renderPersistedReview();
    expect((await screen.findAllByText('Import completed')).length).toBeGreaterThan(0);
    completed.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PARTIALLY_COMPLETED' } });
    renderPersistedReview();
    expect((await screen.findAllByText('Import partially completed')).length).toBeGreaterThan(0);
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
    await waitFor(() => expect(within(screen.getByTestId('workflowCounts')).getByText('2')).toBeTruthy());
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
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

    expect((await screen.findAllByText('Ready to import')).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /disable/i }));

    await waitFor(() => expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0));
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
    await waitFor(() => expect(within(screen.getByTestId('workflowCounts')).getByText('2')).toBeTruthy());
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
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

    expect((await screen.findAllByText('Needs review')).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /enable/i }));

    await waitFor(() => expect(screen.getAllByText('Ready to import').length).toBeGreaterThan(0));
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
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
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
    expect(screen.getAllByText('Ready to import').length).toBeGreaterThan(0);
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
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
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

    expect((await screen.findAllByText('Ready to import')).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0));
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

    expect((await screen.findAllByText('Ready to import')).length).toBeGreaterThan(0);
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeTruthy();
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
    expect(screen.getByRole('button', { name: /confirm import/i })).toBeTruthy();
  });

  it('does not allow confirm when review needs fixes', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('Needs review')).length).toBeGreaterThan(0);
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
    await waitFor(() => expect(screen.getAllByText('Import completed').length).toBeGreaterThan(0));
    expectRowStatus(300, 'Imported');
    expectRowStatus(302, 'Disabled');
    expect(within(screen.getByTestId('workflowCounts')).getAllByText('0')).toHaveLength(3);
    expect(screen.queryByText('No transactions were created yet.')).toBeNull();
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

    expect((await screen.findAllByText('Import completed')).length).toBeGreaterThan(0);
    expect(screen.queryByText('No transactions were created yet.')).toBeNull();
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
    expect(screen.getByText('No transactions were created yet.')).toBeTruthy();
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
});
