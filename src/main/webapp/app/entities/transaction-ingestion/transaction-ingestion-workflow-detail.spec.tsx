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
const mockGetCategories = jest.fn(params => ({ type: 'category/getEntities', payload: params }));
const mockGetTags = jest.fn(params => ({ type: 'tag/getEntities', payload: params }));
let mockState;

const confirmImportCalls = () => mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-ingestions/100/confirm');

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: params => mockGetFinancialAccounts(params),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  getEntities: params => mockGetCategories(params),
}));

jest.mock('app/entities/tag/tag.reducer', () => ({
  getEntities: params => mockGetTags(params),
}));

const baseState = {
  financialAccount: {
    entities: [
      { id: 10, name: 'Checking account', currency: 'MXN' },
      { id: 11, name: 'Cash account', currency: 'USD' },
    ],
    loading: false,
  },
  category: {
    entities: [
      { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
      { id: 8, name: 'Restaurants', categoryType: 'EXPENSE' },
      { id: 9, name: 'Salary', categoryType: 'INCOME' },
    ],
    loading: false,
  },
  tag: {
    entities: [
      { id: 3, name: 'Ride share' },
      { id: 5, name: 'Business' },
      { id: 6, name: 'Cash' },
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

const classificationPreviewResponse = {
  data: {
    transactionIngestionId: 100,
    rows: [
      {
        candidateId: 400,
        ingestionRecordId: 300,
        recordIndex: 1,
        action: 'PREVIEWED',
        candidate: {
          id: 400,
          source: 'FILE_IMPORT',
          status: 'READY_TO_POST',
          validationStatus: 'VALID',
          classificationReviewStatus: 'NOT_EVALUATED',
          transactionDate: '2026-07-13',
          description: 'Salary',
          signedAmount: '100.00',
          amount: '100.00',
          flow: 'IN',
          currencySnapshot: 'MXN',
          accountId: 10,
          accountName: 'Checking account',
          categoryId: null,
          categoryName: null,
          tagIds: [],
          tagNames: [],
        },
        transactionDate: '2026-07-13',
        description: 'Salary',
        signedAmount: '100.00',
        amount: '100.00',
        flow: 'IN',
        suggestedCategory: { id: 9, name: 'Salary', categoryType: 'INCOME' },
        suggestedTags: [{ id: 5, name: 'Business' }],
        matchedRules: [{ ruleId: 20, ruleName: 'Salary rule' }],
        conflicts: [],
        skippedOutputs: [],
      },
    ],
  },
};

const candidateForRow = (row, overrides = {}) => ({
  id: (row.ingestionRecordId ?? row.recordIndex) + 100,
  source: 'FILE_IMPORT',
  status: 'READY_TO_POST',
  validationStatus: 'VALID',
  classificationReviewStatus: 'NOT_EVALUATED',
  transactionDate: row.transactionDate,
  postingDate: row.postingDate,
  description: row.description,
  signedAmount: row.signedAmount,
  amount: row.amount,
  flow: row.flow,
  currencySnapshot: row.currency,
  externalReference: row.externalReference,
  notes: row.notes,
  accountId: 10,
  accountName: 'Checking account',
  categoryId: null,
  categoryName: null,
  tagIds: [],
  tagNames: [],
  ...overrides,
});

const withCandidates = (response, candidateOverridesByRecordId = {}) => ({
  data: {
    ...response.data,
    rows: response.data.rows.map(row =>
      row.status === 'VALID'
        ? {
            ...row,
            candidate: candidateForRow(row, candidateOverridesByRecordId[row.ingestionRecordId] ?? {}),
          }
        : row,
    ),
  },
});

const prepareCandidatesResponse = {
  data: {
    transactionIngestionId: 100,
    created: 1,
    updated: 0,
    unchanged: 0,
    skipped: 0,
    errors: 0,
    rows: [{ ingestionRecordId: 300, recordIndex: 1, candidateId: 400, action: 'CREATED' }],
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

  it('adds one clear New File Import action on TransactionIngestion list', () => {
    renderList();

    const newFileImport = screen.getByRole('link', { name: /new file import/i });
    expect(newFileImport.getAttribute('href')).toBe('/transaction-ingestion/new');
    expect(screen.getAllByRole('link', { name: /new file import/i })).toHaveLength(1);
    expect(screen.queryByRole('link', { name: /create new transaction ingestion/i })).toBeNull();
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
          descriptionReview: {
            source: 'USER_EDIT',
            originalDescription: '',
            normalizedDescription: 'Corrected row',
            ruleName: 'Normalize Old Value',
            editedBy: 'user',
          },
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
    await waitFor(() => expect(screen.getByTestId('descriptionReview-badge-301')).toBeTruthy());
    expect(screen.getAllByText('Ready to import').length).toBeGreaterThan(0);
    expectRowStatus(301, 'Valid');
    expect(screen.getAllByText('50.00')).toHaveLength(2);
    expect(screen.queryByText('signedAmount must be nonzero')).toBeNull();
    expect(within(rowForRecord(301)).getByText('Edited manually')).toBeTruthy();
    expect(screen.getByTestId('descriptionReview-manualNote-301').textContent).toContain(
      'Originally auto-normalized by Normalize Old Value, then edited manually',
    );
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

  it('renders description rule metadata compactly', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        rows: [
          {
            ...persistedReviewResponse.data.rows[0],
            description: 'Uber',
            descriptionReview: {
              source: 'DESCRIPTION_RULE',
              originalDescription: 'Uber, Trip',
              normalizedDescription: 'Uber',
              ruleName: 'Normalize Uber',
            },
          },
        ],
      },
    });
    renderPersistedReview();

    await waitFor(() => expect(screen.getByTestId('descriptionReview-badge-300')).toBeTruthy());
    expect(within(rowForRecord(300)).getByText('Auto-normalized')).toBeTruthy();
    expect(screen.getByTestId('descriptionReview-ruleName-300').textContent).toContain('Normalize Uber');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Original description');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Uber, Trip');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Normalized description');
  });

  it('renders manual edit metadata and previous rule note', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        rows: [
          {
            ...persistedReviewResponse.data.rows[0],
            description: 'Manual Uber',
            descriptionReview: {
              source: 'USER_EDIT',
              originalDescription: 'Uber, Trip',
              normalizedDescription: 'Manual Uber',
              ruleName: 'Normalize Uber',
              editedAt: '2026-07-13T16:05:00Z',
              editedBy: 'user',
            },
          },
        ],
      },
    });
    renderPersistedReview();

    await waitFor(() => expect(screen.getByTestId('descriptionReview-badge-300')).toBeTruthy());
    expect(within(rowForRecord(300)).getByText('Edited manually')).toBeTruthy();
    expect(screen.getByTestId('descriptionReview-manualNote-300').textContent).toContain(
      'Originally auto-normalized by Normalize Uber, then edited manually',
    );
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Original description');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Uber, Trip');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('Edited by');
    expect(screen.getByTestId('descriptionReview-details-300').textContent).toContain('user');
  });

  it('does not render description metadata badge when no source is present', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        rows: [
          {
            ...persistedReviewResponse.data.rows[0],
            descriptionReview: {
              originalDescription: 'Salary raw',
              normalizedDescription: 'Salary',
            },
          },
        ],
      },
    });
    renderPersistedReview();

    await screen.findByText('Salary');
    expect(within(rowForRecord(300)).queryByText('Auto-normalized')).toBeNull();
    expect(within(rowForRecord(300)).queryByText('Edited manually')).toBeNull();
  });

  it('shows Continue to category/tags when review is ready with valid rows', async () => {
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
    expect(screen.getByRole('button', { name: /continue to category\/tags/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
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

  it('candidate-backed classification prepares candidates, previews suggestions, persists manual selections, and confirms from reloaded candidates', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'NOT_EVALUATED' },
    });
    const selectedCandidateResponse = withCandidates(readyResponse, {
      300: {
        id: 400,
        classificationReviewStatus: 'USER_SELECTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [3, 6],
        tagNames: ['Ride share', 'Cash'],
      },
    });
    mockAxiosGet
      .mockResolvedValueOnce(readyResponse)
      .mockResolvedValueOnce(candidateResponse)
      .mockResolvedValueOnce(selectedCandidateResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce(classificationPreviewResponse);
    mockAxiosPatch.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 300,
        recordIndex: 1,
        candidate: selectedCandidateResponse.data.rows[0].candidate,
      },
    });
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/prepare'));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', undefined));
    expect(mockAxiosPost).not.toHaveBeenCalledWith('api/transaction-ingestions/100/classification-preview');
    expect(await screen.findByText('Review categories and tags')).toBeTruthy();
    expect(screen.getByText('Categories and tags are saved on candidates before confirming the import.')).toBeTruthy();
    expect(screen.getByTestId('classificationSuggestedCategory-400').textContent).toContain('Salary');
    expect(screen.getByTestId('classificationSuggestedTags-400').textContent).toContain('Business');
    expect(screen.getByTestId('classificationMatchedRules-400').textContent).toContain('Salary rule');
    expect((screen.getByTestId('classificationCategory-400') as HTMLSelectElement).value).toBe('');

    fireEvent.change(screen.getByTestId('classificationCategory-400'), { target: { value: '9' } });
    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/400/classification', { categoryId: 9 }),
    );
    expect((screen.getByTestId('classificationCategory-400') as HTMLSelectElement).value).toBe('9');
    const tagSelect = screen.getByTestId('classificationTags-400') as HTMLSelectElement;
    Array.from(tagSelect.options).forEach(option => {
      option.selected = ['3', '6'].includes(option.value);
    });
    mockAxiosPatch.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 300,
        recordIndex: 1,
        candidate: selectedCandidateResponse.data.rows[0].candidate,
      },
    });
    fireEvent.change(tagSelect);
    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/400/classification', { tagIds: [3, 6] }),
    );

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        status: 'COMPLETED',
        createdNow: 1,
        alreadyImported: 0,
        skipped: 0,
        rejected: 0,
        failed: 0,
        counts: { recordsReceived: 1, recordsCreated: 1, recordsSkipped: 0, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [{ ...persistedReviewResponse.data.rows[0], status: 'IMPORTED', financialTransactionId: 9001 }],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenLastCalledWith('api/transaction-ingestions/100/confirm'));
  });

  it('candidate-backed classification applies suggestions per row and confirms no-suggestion rows before import', async () => {
    const outRow = {
      ...persistedReviewResponse.data.rows[0],
      ingestionRecordId: 310,
      recordIndex: 1,
      description: 'Uber trip',
      signedAmount: '-100.00',
      amount: '100.00',
      flow: 'OUT',
    };
    const inRow = {
      ...persistedReviewResponse.data.rows[0],
      ingestionRecordId: 311,
      recordIndex: 2,
      description: 'Uber refund',
      signedAmount: '100.00',
      amount: '100.00',
      flow: 'IN',
    };
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        rows: [outRow, inRow],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      310: { id: 410 },
      311: { id: 411 },
    });
    const reviewedResponse = withCandidates(readyResponse, {
      310: {
        id: 410,
        classificationReviewStatus: 'SUGGESTED',
        categoryId: 7,
        categoryName: 'Transport',
        tagIds: [3],
        tagNames: ['Ride share'],
      },
      311: { id: 411, classificationReviewStatus: 'NOT_APPLICABLE' },
    });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse).mockResolvedValueOnce(reviewedResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 410,
            ingestionRecordId: 310,
            recordIndex: 1,
            action: 'PREVIEWED',
            candidate: candidateResponse.data.rows[0].candidate,
            transactionDate: '2026-07-13',
            description: 'Uber trip',
            signedAmount: '-100.00',
            amount: '100.00',
            flow: 'OUT',
            suggestedCategory: { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
            suggestedTags: [{ id: 3, name: 'Ride share' }],
            matchedRules: [{ ruleId: 20, ruleName: 'Uber gastos' }],
            conflicts: [],
            skippedOutputs: [],
            hasSuggestions: true,
          },
          {
            candidateId: 411,
            ingestionRecordId: 311,
            recordIndex: 2,
            action: 'PREVIEWED',
            candidate: candidateResponse.data.rows[1].candidate,
            transactionDate: '2026-07-14',
            description: 'Uber refund',
            signedAmount: '100.00',
            amount: '100.00',
            flow: 'IN',
            suggestedCategory: null,
            suggestedTags: [],
            matchedRules: [],
            conflicts: [],
            skippedOutputs: [],
            hasSuggestions: false,
          },
        ],
      },
    });
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));

    expect(await screen.findByText('Review categories and tags')).toBeTruthy();
    expect(screen.getByTestId('classificationSuggestedCategory-410').textContent).toContain('Transport');
    expect((screen.getByTestId('classificationCategory-410') as HTMLSelectElement).value).toBe('');
    expect(screen.queryByTestId('classificationSuggestedCategory-411')).toBeNull();
    expect(screen.queryByTestId('classificationMatchedRules-411')).toBeNull();
    expect((screen.getByTestId('classificationCategory-411') as HTMLSelectElement).value).toBe('');
    expect(
      Array.from((screen.getByTestId('classificationCategory-411') as HTMLSelectElement).options).map(option => option.textContent),
    ).toEqual(['No category', 'Salary']);

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 410,
            ingestionRecordId: 310,
            recordIndex: 1,
            action: 'APPLIED',
            candidate: reviewedResponse.data.rows[0].candidate,
            suggestedCategory: { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
            suggestedTags: [{ id: 3, name: 'Ride share' }],
            matchedRules: [{ ruleId: 20, ruleName: 'Uber gastos' }],
            conflicts: [],
            skippedOutputs: [],
            hasSuggestions: true,
            categoryApplied: true,
            tagIdsApplied: [3],
          },
        ],
      },
    });
    fireEvent.click(screen.getByTestId('classificationApply-410'));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', { candidateIds: [410] }),
    );
    expect((screen.getByTestId('classificationCategory-410') as HTMLSelectElement).value).toBe('7');

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 311,
        recordIndex: 2,
        candidate: reviewedResponse.data.rows[1].candidate,
      },
    });
    fireEvent.click(screen.getByTestId('classificationConfirmNoSuggestions-411'));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/411/confirm-no-suggestions'));

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        status: 'COMPLETED',
        createdNow: 2,
        alreadyImported: 0,
        skipped: 0,
        rejected: 0,
        failed: 0,
        counts: { recordsReceived: 2, recordsCreated: 2, recordsSkipped: 0, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [
          { ...outRow, status: 'IMPORTED', financialTransactionId: 9001 },
          { ...inRow, status: 'IMPORTED', financialTransactionId: 9002 },
        ],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenLastCalledWith('api/transaction-ingestions/100/confirm'));
  });

  it('reload keeps persisted candidate category and tags as classification source of truth', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: {
        id: 400,
        classificationReviewStatus: 'USER_SELECTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [5],
        tagNames: ['Business'],
      },
    });
    mockAxiosGet.mockResolvedValueOnce(candidateResponse).mockResolvedValueOnce(candidateResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce(classificationPreviewResponse);
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));
    await screen.findByText('Review categories and tags');

    expect(((await screen.findByTestId('classificationCategory-400')) as HTMLSelectElement).value).toBe('9');
    expect(
      Array.from((screen.getByTestId('classificationTags-400') as HTMLSelectElement).selectedOptions).map(option => option.value),
    ).toEqual(['5']);
  });

  it('candidate-backed classification blocks VALID rows without candidates', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(readyResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse);
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));

    expect(await screen.findByText('Could not prepare transaction candidates for classification.')).toBeTruthy();
    expect(screen.queryByText('Review categories and tags')).toBeNull();
    expect(screen.getByText('Ingestion records')).toBeTruthy();
  });

  it('candidate-backed classification blocks confirm for NOT_EVALUATED and STALE candidates', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        rows: [
          { ...persistedReviewResponse.data.rows[0], ingestionRecordId: 320, recordIndex: 1 },
          {
            ...persistedReviewResponse.data.rows[0],
            ingestionRecordId: 321,
            recordIndex: 2,
            description: 'Stale row',
            signedAmount: '-20.00',
            amount: '20.00',
            flow: 'OUT',
          },
        ],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      320: { id: 420, classificationReviewStatus: 'NOT_EVALUATED' },
      321: { id: 421, classificationReviewStatus: 'STALE' },
    });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          { candidateId: 420, ingestionRecordId: 320, recordIndex: 1, action: 'PREVIEWED', hasSuggestions: false },
          { candidateId: 421, ingestionRecordId: 321, recordIndex: 2, action: 'PREVIEWED', hasSuggestions: false },
        ],
      },
    });
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));

    expect(await screen.findByText('Review every valid row before confirming import.')).toBeTruthy();
    expect((screen.getByRole('button', { name: /confirm import/i }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId('classificationStatus-420').textContent).toContain('Not evaluated');
    expect(screen.getByTestId('classificationStatus-421').textContent).toContain('Stale');
  });

  it.each([
    ['non-FILE_IMPORT source', { source: 'MANUAL' }],
    ['non-ready status', { status: 'DRAFT' }],
    ['final status', { status: 'CANCELLED' }],
  ])('candidate-backed confirm blocks %s from freshly reloaded candidates', async (_caseName, blockedOverrides) => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'SUGGESTED', categoryId: 9, categoryName: 'Salary', tagIds: [5], tagNames: ['Business'] },
    });
    const blockedReloadResponse = withCandidates(readyResponse, {
      300: {
        id: 400,
        classificationReviewStatus: 'SUGGESTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [5],
        tagNames: ['Business'],
        ...blockedOverrides,
      },
    });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse).mockResolvedValueOnce(blockedReloadResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce(classificationPreviewResponse);
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));
    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    expect(await screen.findByText('Review every valid row before confirming import.')).toBeTruthy();
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('confirm success marks imported rows, leaves disabled rows, and makes review read-only', async () => {
    const readyRows = [{ ...persistedReviewResponse.data.rows[0] }, { ...persistedReviewResponse.data.rows[2] }];
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: readyRows,
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'SUGGESTED', categoryId: 9, categoryName: 'Salary', tagIds: [5], tagNames: ['Business'] },
    });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse).mockResolvedValueOnce(candidateResponse);
    mockAxiosPost
      .mockResolvedValueOnce(prepareCandidatesResponse)
      .mockResolvedValueOnce(classificationPreviewResponse)
      .mockResolvedValueOnce({
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

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));
    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenLastCalledWith('api/transaction-ingestions/100/confirm'));
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
          {
            ...persistedReviewResponse.data.rows[0],
            status: 'IMPORTED',
            financialTransactionId: 9001,
            description: 'Uber',
            descriptionReview: {
              source: 'DESCRIPTION_RULE',
              originalDescription: 'Uber, Trip',
              normalizedDescription: 'Uber',
              ruleName: 'Normalize Uber',
            },
          },
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
    expect(within(rowForRecord(300)).getByText('Auto-normalized')).toBeTruthy();
    expect(screen.getByTestId('descriptionReview-ruleName-300').textContent).toContain('Normalize Uber');
  });

  it('confirm API error displays review error', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'SUGGESTED', categoryId: 9, categoryName: 'Salary', tagIds: [5], tagNames: ['Business'] },
    });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse).mockResolvedValueOnce(candidateResponse);
    mockAxiosPost
      .mockResolvedValueOnce(prepareCandidatesResponse)
      .mockResolvedValueOnce(classificationPreviewResponse)
      .mockRejectedValueOnce(new Error('confirm failed'));
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /continue to category\/tags/i }));
    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    expect(await screen.findByText('Could not confirm import. Check the review status and try again.')).toBeTruthy();
    expect(screen.queryByText('Import completed')).toBeNull();
    expect(screen.getByRole('button', { name: /confirm import/i })).toBeTruthy();
    expect(screen.getByText('Review categories and tags')).toBeTruthy();
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
