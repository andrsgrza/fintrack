import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionIngestion from 'app/../i18n/en/transactionIngestion.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enIngestionStatus from 'app/../i18n/en/ingestionStatus.json';
import enIngestionRecordStatus from 'app/../i18n/en/ingestionRecordStatus.json';
import enCategory from 'app/../i18n/en/category.json';
import enTag from 'app/../i18n/en/tag.json';
import enTransactionRule from 'app/../i18n/en/transactionRule.json';
import esTransactionIngestion from 'app/../i18n/es/transactionIngestion.json';
import esTransactionFlow from 'app/../i18n/es/transactionFlow.json';
import esIngestionStatus from 'app/../i18n/es/ingestionStatus.json';
import esIngestionRecordStatus from 'app/../i18n/es/ingestionRecordStatus.json';
import esCategory from 'app/../i18n/es/category.json';
import esTag from 'app/../i18n/es/tag.json';
import esTransactionRule from 'app/../i18n/es/transactionRule.json';
import { TransactionIngestion } from './transaction-ingestion';
import { TransactionIngestionWorkflowDetail } from './transaction-ingestion-workflow-detail';

jest.mock('axios');

const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPatch = axios.patch as jest.Mock;
const mockDispatch = jest.fn();
const mockGetFinancialAccounts = jest.fn(params => ({ type: 'financialAccount/getEntities', payload: params }));
const mockGetSelectableCategories = jest.fn();
const mockGetSelectableTags = jest.fn();
let mockState;

const confirmImportCalls = () => mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-ingestions/100/confirm');

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: params => mockGetFinancialAccounts(params),
}));

jest.mock('app/entities/category/category-selectable.service', () => ({
  getSelectableCategories: (...args) => mockGetSelectableCategories(...args),
}));

jest.mock('app/entities/tag/tag-selectable.service', () => ({
  getSelectableTags: (...args) => mockGetSelectableTags(...args),
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
  TranslatorContext.registerTranslations('en', enCategory);
  TranslatorContext.registerTranslations('en', enTag);
  TranslatorContext.registerTranslations('en', enTransactionRule);
  TranslatorContext.setLocale('en');
};

const registerSpanishTranslations = () => {
  TranslatorContext.registerTranslations('es', esTransactionIngestion);
  TranslatorContext.registerTranslations('es', esTransactionFlow);
  TranslatorContext.registerTranslations('es', esIngestionStatus);
  TranslatorContext.registerTranslations('es', esIngestionRecordStatus);
  TranslatorContext.registerTranslations('es', esCategory);
  TranslatorContext.registerTranslations('es', esTag);
  TranslatorContext.registerTranslations('es', esTransactionRule);
  TranslatorContext.setLocale('es');
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

const expandRowForRecord = async (recordId: number) => {
  const existingDetail = screen.queryByTestId(`workflowRowDetail-${recordId}`);
  if (existingDetail) {
    return existingDetail;
  }
  fireEvent.click(await screen.findByTestId(`workflowRowToggleDetails-${recordId}`));
  return screen.findByTestId(`workflowRowDetail-${recordId}`);
};

const expectRowStatus = (recordId: number, status: string) => {
  expect(screen.getByTestId(`workflowRowStatus-${recordId}`).textContent).toBe(status);
};

const selectedTagNames = (candidateId: number) =>
  within(screen.getByTestId(`classificationTags-${candidateId}`))
    .queryAllByTestId(`classificationTags-${candidateId}Chip`)
    .map(chip => chip.querySelector('span')?.textContent);

const expectSelectedTagNames = (candidateId: number, names: string[]) => {
  expect(selectedTagNames(candidateId)).toEqual(names);
};

const addCandidateTag = async (candidateId: number, name: string, search = name) => {
  const addButton = screen.getByTestId(`classificationTags-${candidateId}Add`);
  await waitFor(() => expect(addButton.disabled).toBe(false));
  fireEvent.click(addButton);
  const picker = await screen.findByTestId(`classificationTags-${candidateId}Picker`);
  fireEvent.change(within(picker).getByTestId(`classificationTags-${candidateId}Search`), { target: { value: search } });
  fireEvent.click(within(picker).getByRole('button', { name }));
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
    mockGetSelectableCategories.mockReset();
    mockGetSelectableTags.mockReset();
    mockGetSelectableCategories.mockResolvedValue(baseState.category.entities);
    mockGetSelectableTags.mockResolvedValue(baseState.tag.entities);
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

  it('renders the product review header, compact real summary, and collapsed file details without changing row actions', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('workflow.csv')).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: 'Review Import' })).toBeTruthy();
    expect(screen.getByTestId('workflowHeaderStatus').textContent).toBe('Needs review');
    expect(screen.getByRole('link', { name: 'Checking account' }).getAttribute('href')).toBe('/financial-account/10');
    expect(screen.getByRole('link', { name: /back to imports/i }).getAttribute('href')).toBe('/transaction-ingestion');
    expect(screen.queryByText('Parent summary')).toBeNull();
    expect(screen.getByText('Review summary')).toBeTruthy();
    expect(screen.getByText('File details')).toBeTruthy();
    const fileDetails = screen.getByTestId('workflowFileDetailsDisclosure');
    expect(fileDetails.open).toBe(false);
    expect(screen.getByText('Show file details')).toBeTruthy();
    fireEvent.click(screen.getByText('Show file details'));
    expect(fileDetails.open).toBe(true);
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
    expect(within(counts).getByText('3')).toBeTruthy();
    expect(within(counts).getAllByText('1')).toHaveLength(3);

    expect(screen.getByRole('columnheader', { name: 'Date' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Movement' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Category' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Tags' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Details' })).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.getByTestId('workflowRowToggleDetails-300').getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
  });

  it('does not render TransactionIngestion workflow detail Edit action', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('workflow.csv')).length).toBeGreaterThan(0);
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
    expect(screen.getByRole('heading', { name: 'Review Import' })).toBeTruthy();
    expect(screen.getByTestId('workflowHeaderStatus').textContent).toBe('Pending');
    expect(screen.queryByTestId('workflowCounts')).toBeNull();
  });

  it('uses Spanish product copy for the review header, status, and file details', async () => {
    registerSpanishTranslations();
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect(await screen.findByRole('heading', { name: 'Revisar importación' })).toBeTruthy();
    expect(screen.getByTestId('workflowHeaderStatus').textContent).toBe('Requiere revisión');
    expect(screen.getByRole('link', { name: 'Volver a importaciones' }).getAttribute('href')).toBe('/transaction-ingestion');
    expect(screen.getByText('Resumen de revisión')).toBeTruthy();
    expect(screen.getByText('Detalles del archivo')).toBeTruthy();
  });

  it('keeps a long filename in the product header and leaves file details collapsed initially', async () => {
    const longFilename = `statement-${'x'.repeat(180)}.csv`;
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        fileMetadata: { ...persistedReviewResponse.data.fileMetadata, originalFilename: longFilename },
      },
    });
    renderPersistedReview();

    const filenames = await screen.findAllByText(longFilename);
    expect(filenames.some(element => element.classList.contains('text-break'))).toBe(true);
    expect(screen.getByTestId('workflowFileDetailsDisclosure').open).toBe(false);
  });

  it('renders ingestion readiness and import-result status labels', async () => {
    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PENDING' } });
    const pending = renderPersistedReview();
    expect((await screen.findAllByText('Pending')).length).toBeGreaterThan(0);
    pending.unmount();

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
    expect(within(screen.getByTestId('workflowCounts')).getByText('Records Created')).toBeTruthy();
    expect(within(screen.getByTestId('workflowCounts')).getByText('Records Skipped')).toBeTruthy();
    expect(within(screen.getByTestId('workflowCounts')).getByText('Records rejected')).toBeTruthy();
    completed.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PARTIALLY_COMPLETED' } });
    const partiallyCompleted = renderPersistedReview();
    expect((await screen.findAllByText('Import partially completed')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('workflowPartiallyCompleted')).toBeTruthy();
    partiallyCompleted.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'PROCESSING' } });
    const processing = renderPersistedReview();
    expect((await screen.findAllByText('Processing')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('workflowProcessing')).toBeTruthy();
    processing.unmount();

    mockAxiosGet.mockResolvedValueOnce({ data: { ...persistedReviewResponse.data, status: 'FAILED' } });
    renderPersistedReview();
    expect((await screen.findAllByText('Failed')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('workflowFailed')).toBeTruthy();
  });

  it('renders deleted-after-import rows as completed historical records without review actions', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...persistedReviewResponse.data,
        status: 'COMPLETED',
        counts: { recordsReceived: 1, recordsCreated: 1, recordsSkipped: 0, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [
          {
            ...persistedReviewResponse.data.rows[3],
            status: 'DELETED_AFTER_IMPORT',
            financialTransactionId: null,
          },
        ],
      },
    });
    renderPersistedReview();

    expect(await screen.findByText('Transaction deleted after import')).toBeTruthy();
    expect(screen.getByText('Import completed')).toBeTruthy();
    expect(screen.queryByTestId('workflowRowEdit-303')).toBeNull();
    expect(screen.queryByTestId('workflowRowDisable-303')).toBeNull();
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
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowDisable-300'));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/300/disable'));
    await waitFor(() => expect(within(screen.getByTestId('workflowCounts')).getByText('2')).toBeTruthy());
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
    expectRowStatus(300, 'Disabled');
    expect(within(await expandRowForRecord(300)).getByRole('button', { name: /enable/i })).toBeTruthy();
    expect(within(await expandRowForRecord(300)).queryByRole('button', { name: /disable/i })).toBeNull();
    expect(within(await expandRowForRecord(300)).queryByRole('button', { name: /edit/i })).toBeNull();
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
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowDisable-300'));

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
    await expandRowForRecord(302);
    fireEvent.click(screen.getByTestId('workflowRowEnable-302'));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/302/enable'));
    await waitFor(() => expect(within(screen.getByTestId('workflowCounts')).getByText('2')).toBeTruthy());
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
    expectRowStatus(302, 'Valid');
    expect(within(await expandRowForRecord(302)).getByRole('button', { name: /disable/i })).toBeTruthy();
    expect(within(await expandRowForRecord(302)).queryByRole('button', { name: /enable/i })).toBeNull();
  });

  it('enable valid disabled row updates displayed batch status to ready', async () => {
    const disabledResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'PARTIALLY_READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 0, validRows: 0, invalidRows: 0 },
        rows: [{ ...persistedReviewResponse.data.rows[0], status: 'DISABLED' }],
      },
    };
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    mockAxiosGet.mockResolvedValueOnce(disabledResponse).mockResolvedValueOnce(withCandidates(readyResponse));
    mockAxiosPost
      .mockResolvedValueOnce({
        data: {
          transactionIngestionId: 100,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          row: persistedReviewResponse.data.rows[0],
        },
      })
      .mockResolvedValueOnce(prepareCandidatesResponse)
      .mockResolvedValueOnce(classificationPreviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('Needs review')).length).toBeGreaterThan(0);
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowEnable-300'));

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
    await expandRowForRecord(302);
    fireEvent.click(screen.getByTestId('workflowRowEnable-302'));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/records/302/enable'));
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
    expectRowStatus(302, 'Rejected');
    expect(within(rowForRecord(302)).getByText('description is required')).toBeTruthy();
    expect(within(await expandRowForRecord(302)).getByRole('button', { name: /disable/i })).toBeTruthy();
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
    await expandRowForRecord(301);
    fireEvent.click(screen.getByTestId('workflowRowEdit-301'));
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
    expect(within(await expandRowForRecord(301)).getByText('50.00')).toBeTruthy();
    expect(screen.queryByText('signedAmount must be nonzero')).toBeNull();
    expect(within(await expandRowForRecord(301)).getByText('Edited manually')).toBeTruthy();
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

    fireEvent.click(within(await expandRowForRecord(300)).getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '-100.00' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith(
        'api/transaction-ingestions/100/records/300',
        expect.objectContaining({ signedAmount: '-100.00' }),
      ),
    );
    expect(rowForRecord(300).textContent).toContain('-100.00');
    expect(within(await expandRowForRecord(300)).getByText('100.00')).toBeTruthy();
    expect(within(await expandRowForRecord(300)).getByText('Expense')).toBeTruthy();
    expect(within(await expandRowForRecord(300)).queryByText('Income')).toBeNull();
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
    fireEvent.click(within(await expandRowForRecord(300)).getByRole('button', { name: /edit/i }));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '-100.00' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(rowForRecord(300).textContent).toContain('-100.00'));
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
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowEdit-300'));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(mockAxiosPatch).toHaveBeenCalledWith(expect.stringContaining('/records/300'), expect.any(Object)));
    await waitFor(() => expectRowStatus(300, 'Rejected'));
    expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0);
    expect(screen.getAllByText('signedAmount must be nonzero').length).toBeGreaterThan(0);
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
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowEdit-300'));
    fireEvent.change(screen.getByLabelText('Signed amount'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save row/i }));

    await waitFor(() => expect(screen.getAllByText('Needs review').length).toBeGreaterThan(0));
    expectRowStatus(300, 'Rejected');
  });

  it('does not show edit for disabled rows', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    await screen.findByText('Disabled row');

    const disabledDetail = await expandRowForRecord(302);
    expect(within(disabledDetail).queryByRole('button', { name: /edit/i })).toBeNull();
    expect(within(disabledDetail).getByRole('button', { name: /enable/i })).toBeTruthy();
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

    await expandRowForRecord(300);
    await waitFor(() => expect(screen.getByTestId('descriptionReview-badge-300')).toBeTruthy());
    expect(within(await expandRowForRecord(300)).getByText('Auto-normalized')).toBeTruthy();
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

    await expandRowForRecord(300);
    await waitFor(() => expect(screen.getByTestId('descriptionReview-badge-300')).toBeTruthy());
    expect(within(await expandRowForRecord(300)).getByText('Edited manually')).toBeTruthy();
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

  it('automatically prepares candidate-backed classification in the same review table when review is ready', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValueOnce(readyResponse).mockResolvedValueOnce(candidateResponse);
    mockAxiosPost.mockResolvedValueOnce(prepareCandidatesResponse).mockResolvedValueOnce(classificationPreviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('Ready to import')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.queryByText(/translation-not-found/i)).toBeNull();
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/prepare'));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', undefined));
    expect(screen.getByRole('columnheader', { name: 'Category' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Tags' })).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: 'Rule details' })).toBeNull();
    expect(screen.queryByRole('button', { name: /continue to category\/tags/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /back to row review/i })).toBeNull();
    expect(mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-ingestions/100/candidates/prepare')).toHaveLength(1);
  });

  it("keeps a candidate's historical inactive category and tags selectable while hiding them from new selections", async () => {
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
        categoryId: 77,
        categoryName: 'Historical salary',
        tagIds: [66],
        tagNames: ['Historical payroll'],
      },
    });
    const historicalCategory = { id: 77, name: 'Historical salary', categoryType: 'INCOME', active: false };
    const historicalTag = { id: 66, name: 'Historical payroll', color: '#7a405d', active: false };
    mockGetSelectableCategories.mockImplementation(includeIds =>
      Promise.resolve(includeIds?.includes(77) ? [...baseState.category.entities, historicalCategory] : baseState.category.entities),
    );
    mockGetSelectableTags.mockImplementation(includeIds =>
      Promise.resolve(includeIds?.includes(66) ? [...baseState.tag.entities, historicalTag] : baseState.tag.entities),
    );
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    const categorySelect = await screen.findByTestId('classificationCategory-400');
    const tagsSelector = screen.getByTestId('classificationTags-400');
    expect(categorySelect.value).toBe('77');
    expect(Array.from(categorySelect.options).map(option => option.textContent)).toContain('Historical salary (Inactive)');
    expectSelectedTagNames(400, ['Historical payroll']);
    expect(within(tagsSelector).getByTestId('classificationTags-400Inactive')).toBeTruthy();
    expect(within(tagsSelector).getByTestId('classificationTags-400Chip').getAttribute('style')).toContain(
      'border-inline-start: 0.3rem solid #7a405d',
    );
    fireEvent.click(within(tagsSelector).getByTestId('classificationTags-400Add'));
    expect(screen.getByTestId('classificationTags-400Picker')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Historical payroll' })).toBeNull();
    expect(mockGetSelectableCategories).toHaveBeenCalledWith([77]);
    expect(mockGetSelectableTags).toHaveBeenCalledWith([66]);
  });

  it('renders distinct always-visible application controls and removes the ambiguous refresh action', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    expect(screen.getByRole('button', { name: /reevaluate descriptions/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /reevaluate categories/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /reevaluate tags/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /reevaluate all/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /apply all suggested descriptions/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /apply all suggested categories/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /apply all suggested tags/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /apply all suggested descriptions/i }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: /apply all suggested categories/i }).disabled).toBe(false);
    expect(screen.getByRole('button', { name: /apply all suggested tags/i }).disabled).toBe(false);
    expect(screen.queryByRole('button', { name: /^apply all classification suggestions$/i })).toBeNull();
    expect(await expandRowForRecord(300)).toBeTruthy();
    expect(screen.getByTestId('workflowRowReevaluate-300')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /refresh suggestions/i })).toBeNull();
  });

  it('filters the candidate review queue locally without narrowing global reevaluation scope', async () => {
    const reviewResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
      },
    };
    const candidateResponse = withCandidates(reviewResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationSuggestedCategory-400');
    expect(screen.getByTestId('candidateReviewVisibleCount').textContent).toContain('4 of 4');

    fireEvent.click(screen.getByTestId('candidateReviewFilter-SUGGESTIONS'));
    expect(screen.getByTestId('candidateReviewVisibleCount').textContent).toContain('1 of 4');
    expect(screen.getByTestId('workflowRow-300')).toBeTruthy();
    expect(screen.queryByTestId('workflowRow-301')).toBeNull();
    expect(screen.queryByTestId('workflowRow-302')).toBeNull();

    mockAxiosPost.mockClear();
    fireEvent.click(screen.getByRole('button', { name: /reevaluate categories/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', { scope: 'CATEGORY' }),
    );

    fireEvent.click(screen.getByTestId('candidateReviewFilter-ERRORS'));
    expect(screen.getByTestId('candidateReviewVisibleCount').textContent).toContain('1 of 4');
    expect(screen.queryByTestId('workflowRow-300')).toBeNull();
    expect(screen.getByTestId('workflowRow-301')).toBeTruthy();

    fireEvent.click(screen.getByTestId('candidateReviewFilter-DISABLED'));
    expect(screen.getByTestId('candidateReviewVisibleCount').textContent).toContain('1 of 4');
    expect(screen.getByTestId('workflowRow-302')).toBeTruthy();

    fireEvent.click(screen.getByTestId('candidateReviewFilter-ALL'));
    expect(screen.getByTestId('candidateReviewVisibleCount').textContent).toContain('4 of 4');
  });

  it('preserves an unsaved general row edit while a local filter hides it and blocks unrelated review writes', async () => {
    const reviewResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 3, recordsCreated: 0, recordsSkipped: 1, recordsRejected: 1, validRows: 1, invalidRows: 1 },
      },
    };
    const candidateResponse = withCandidates(reviewResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowEdit-300'));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Unsaved candidate edit' } });
    expect(screen.getByTestId('workflowRowToggleDetails-300').disabled).toBe(true);
    expect(screen.getByRole('button', { name: /reevaluate all/i }).disabled).toBe(true);

    fireEvent.click(screen.getByTestId('candidateReviewFilter-DISABLED'));
    expect(screen.getByTestId('candidateReviewHiddenEditNotice')).toBeTruthy();
    expect(screen.getByTestId('workflowRow-302')).toBeTruthy();
    expect(screen.queryByTestId('workflowRow-300')).toBeNull();

    fireEvent.click(screen.getByTestId('candidateReviewFilter-ALL'));
    expect(screen.getByLabelText('Description').getAttribute('value')).toBe('Unsaved candidate edit');
    expect(mockAxiosPatch).not.toHaveBeenCalled();
  });

  it('renders one mobile card presentation and exposes classification only after expansion', async () => {
    const previousMatchMedia = window.matchMedia;
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: jest.fn().mockImplementation(() => ({
        matches: true,
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
      })),
    });

    try {
      const readyResponse = {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [persistedReviewResponse.data.rows[0]],
        },
      };
      const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
      mockAxiosGet.mockResolvedValue(candidateResponse);
      mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
      renderPersistedReview();

      await screen.findByTestId('candidateReviewMobileCards');
      expect(screen.queryByTestId('workflowRow-300')).toBeNull();
      expect(screen.getByTestId('workflowMobileCard-300').textContent).toContain('Salary');
      expect(screen.queryByTestId('mobileclassificationCategory-400')).toBeNull();

      mockAxiosPatch.mockClear();
      mockAxiosPost.mockClear();
      fireEvent.click(screen.getByTestId('workflowMobileToggleDetails-300'));
      expect(screen.getByTestId('workflowMobileToggleDetails-300').getAttribute('aria-expanded')).toBe('true');
      expect(screen.getByTestId('mobileclassificationCategory-400')).toBeTruthy();
      expect(screen.getByTestId('mobileclassificationTags-400')).toBeTruthy();
      expect(screen.getByTestId('workflowRowEdit-300-mobile')).toBeTruthy();
      expect(mockAxiosPatch).not.toHaveBeenCalled();
      expect(mockAxiosPost).not.toHaveBeenCalled();
    } finally {
      Object.defineProperty(window, 'matchMedia', { configurable: true, value: previousMatchMedia });
    }
  });

  it('renders local automation defaults without automatically applying classification', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('workflowAutomationConfig');
    expect(screen.getByTestId('workflowAutoApplyDescriptions').checked).toBe(false);
    expect(screen.getByTestId('workflowAutoApplyCategories').checked).toBe(false);
    expect(screen.getByTestId('workflowAutoApplyTags').checked).toBe(false);
    expect(screen.getByTestId('workflowProtectManualChanges').checked).toBe(true);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/rule-preview'))).toHaveLength(1);
  });

  it('enabling category automation applies only the category scope once candidates are ready', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    const automaticallyAppliedCandidate = {
      ...candidateResponse.data.rows[0].candidate,
      categoryId: 9,
      categoryName: 'Salary',
      categorySource: 'AUTOMATIC',
    };
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'APPLIED',
            candidate: automaticallyAppliedCandidate,
            suggestedCategory: { id: 9, name: 'Salary', categoryType: 'INCOME' },
            suggestedTags: [{ id: 5, name: 'Business' }],
            hasSuggestions: true,
            categoryApplied: true,
            tagIdsApplied: [],
          },
        ],
      },
    });

    fireEvent.click(screen.getByTestId('workflowAutoApplyCategories'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', {
        scope: 'CATEGORY',
        automatic: true,
        protectManualChanges: true,
      }),
    );
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledTimes(2));
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(1);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/descriptions/reevaluate'))).toHaveLength(0);

    mockAxiosPost.mockClear();
    fireEvent.click(screen.getByRole('button', { name: /reevaluate categories/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', {
        scope: 'CATEGORY',
        automatic: true,
        protectManualChanges: true,
      }),
    );
  });

  it('queues consecutive automation changes so each newly enabled scope is applied once', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('workflowAutoApplyCategories');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'APPLIED',
            candidate: candidateResponse.data.rows[0].candidate,
            hasSuggestions: false,
          },
        ],
      },
    });

    fireEvent.click(screen.getByTestId('workflowAutoApplyCategories'));
    fireEvent.click(screen.getByTestId('workflowAutoApplyTags'));

    await waitFor(() => {
      const automaticCalls = mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'));
      expect(automaticCalls).toHaveLength(2);
      expect(automaticCalls[0]).toEqual([
        'api/transaction-ingestions/100/candidates/apply-rules',
        { scope: 'CATEGORY', automatic: true, protectManualChanges: true },
      ]);
      expect(automaticCalls[1]).toEqual([
        'api/transaction-ingestions/100/candidates/apply-rules',
        { scope: 'TAGS', automatic: true, protectManualChanges: true },
      ]);
    });
  });

  it('keeps category untouched when only tag automation is enabled and does not apply from the protection toggle alone', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    mockAxiosPost.mockClear();
    fireEvent.click(screen.getByTestId('workflowProtectManualChanges'));
    expect(mockAxiosPost).not.toHaveBeenCalled();

    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'APPLIED',
            candidate: {
              ...candidateResponse.data.rows[0].candidate,
              tagIds: [5],
              tagNames: ['Business'],
              selectedTags: [{ tagId: 5, tagName: 'Business', source: 'AUTOMATIC' }],
            },
            suggestedTags: [{ id: 5, name: 'Business' }],
            hasSuggestions: true,
            categoryApplied: false,
            tagIdsApplied: [5],
          },
        ],
      },
    });
    fireEvent.click(screen.getByTestId('workflowAutoApplyTags'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', {
        scope: 'TAGS',
        automatic: true,
        protectManualChanges: false,
      }),
    );
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/descriptions/reevaluate'))).toHaveLength(0);
  });

  it('reevaluates descriptions with apply only when description automation is enabled', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('workflowAutoApplyDescriptions');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValue({ data: { transactionIngestionId: 100, rows: [{ ingestionRecordId: 300, action: 'APPLIED' }] } });
    fireEvent.click(screen.getByTestId('workflowAutoApplyDescriptions'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        apply: true,
        protectManualChanges: true,
      }),
    );
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
  });

  it('keeps a protected manual category visible while retaining the automatic recommendation', async () => {
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
        categorySource: 'MANUAL',
      },
    });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'UNCHANGED',
            candidate: candidateResponse.data.rows[0].candidate,
            suggestedCategory: { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
            suggestedTags: [],
            hasSuggestions: true,
            categoryApplied: false,
            tagIdsApplied: [],
          },
        ],
      },
    });

    fireEvent.click(screen.getByTestId('workflowAutoApplyCategories'));

    await screen.findByTestId('classificationSuggestedCategory-400');
    expect(screen.getByTestId('classificationSuggestedCategory-400').textContent).toContain('Transport');
    expect(screen.getByTestId('classificationCategory-400').value).toBe('9');
  });

  it('applies all suggested categories without reevaluating, preserves manual selections, and retains tag suggestions', async () => {
    const manualRow = {
      ...persistedReviewResponse.data.rows[0],
      ingestionRecordId: 310,
      recordIndex: 1,
      description: 'Manual salary',
      signedAmount: '100.00',
      amount: '100.00',
      flow: 'IN',
    };
    const suggestedRow = {
      ...persistedReviewResponse.data.rows[0],
      ingestionRecordId: 311,
      recordIndex: 2,
      description: 'Uber trip',
      signedAmount: '-100.00',
      amount: '100.00',
      flow: 'OUT',
    };
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        rows: [manualRow, suggestedRow],
      },
    };
    const initialWorkflow = withCandidates(readyResponse, {
      310: {
        id: 410,
        classificationReviewStatus: 'USER_SELECTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [5],
        tagNames: ['Business'],
      },
      311: { id: 411, classificationReviewStatus: 'NOT_EVALUATED' },
    });
    const refreshedWorkflow = withCandidates(readyResponse, {
      310: {
        id: 410,
        classificationReviewStatus: 'USER_SELECTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [5],
        tagNames: ['Business'],
      },
      311: {
        id: 411,
        classificationReviewStatus: 'SUGGESTED',
        categoryId: 7,
        categoryName: 'Transport',
        tagIds: [],
        tagNames: [],
      },
    });
    const initialPreview = {
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 410,
            ingestionRecordId: 310,
            action: 'PREVIEWED',
            candidate: initialWorkflow.data.rows[0].candidate,
            suggestedCategory: null,
            suggestedTags: [],
            hasSuggestions: false,
          },
          {
            candidateId: 411,
            ingestionRecordId: 311,
            action: 'PREVIEWED',
            candidate: initialWorkflow.data.rows[1].candidate,
            suggestedCategory: { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
            suggestedTags: [{ id: 3, name: 'Ride share' }],
            matchedRules: [{ ruleId: 20, ruleName: 'Uber expense' }],
            hasSuggestions: true,
          },
        ],
      },
    };
    const batchApplyResponse = {
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 410,
            ingestionRecordId: 310,
            action: 'UNCHANGED',
            candidate: refreshedWorkflow.data.rows[0].candidate,
            hasSuggestions: false,
            categoryApplied: false,
            tagIdsApplied: [],
          },
          {
            candidateId: 411,
            ingestionRecordId: 311,
            action: 'APPLIED',
            candidate: refreshedWorkflow.data.rows[1].candidate,
            hasSuggestions: true,
            categoryApplied: true,
            tagIdsApplied: [],
          },
        ],
      },
    };
    let resolveBatchApply: (value: typeof batchApplyResponse) => void = () => undefined;

    mockAxiosGet.mockResolvedValueOnce(initialWorkflow).mockResolvedValueOnce(refreshedWorkflow);
    mockAxiosPost.mockResolvedValueOnce(initialPreview).mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveBatchApply = resolve;
        }),
    );
    renderPersistedReview();

    await screen.findByTestId('classificationSuggestedCategory-411');
    expect(screen.getByTestId('classificationCategory-410').value).toBe('9');
    expect(screen.getByTestId('classificationCategory-411').value).toBe('');

    const applyAllButton = screen.getByRole('button', { name: /apply all suggested categories/i });
    fireEvent.click(applyAllButton);
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', { scope: 'CATEGORY' }),
    );
    expect((applyAllButton as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(applyAllButton);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-ingestions/100/candidates/apply-rules')).toHaveLength(1);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/descriptions/reevaluate'))).toHaveLength(0);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/rule-preview'))).toHaveLength(1);
    expect(confirmImportCalls()).toHaveLength(0);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/financial-transactions'))).toHaveLength(0);

    resolveBatchApply(batchApplyResponse);

    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByTestId('classificationCategory-411').value).toBe('7'));
    expectSelectedTagNames(411, []);
    expect(screen.getByTestId('classificationCategory-410').value).toBe('9');
    expectSelectedTagNames(410, ['Business']);
    expect(screen.queryByTestId('classificationSuggestedCategory-411')).toBeNull();
    expect(screen.getByTestId('classificationSuggestedTags-411').textContent).toContain('Ride share');
  });

  it('applies all suggested tags without applying or clearing category suggestions', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [persistedReviewResponse.data.rows[0]],
      },
    };
    const initialWorkflow = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'NOT_EVALUATED' } });
    const refreshedWorkflow = withCandidates(readyResponse, {
      300: {
        id: 400,
        classificationReviewStatus: 'SUGGESTED',
        tagIds: [5],
        tagNames: ['Business'],
      },
    });
    const initialPreview = {
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'PREVIEWED',
            candidate: initialWorkflow.data.rows[0].candidate,
            suggestedCategory: { id: 9, name: 'Salary', categoryType: 'INCOME' },
            suggestedTags: [{ id: 5, name: 'Business' }],
            hasSuggestions: true,
          },
        ],
      },
    };
    const tagApplyResponse = {
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'APPLIED',
            candidate: refreshedWorkflow.data.rows[0].candidate,
            suggestedCategory: { id: 9, name: 'Salary', categoryType: 'INCOME' },
            suggestedTags: [{ id: 5, name: 'Business' }],
            hasSuggestions: true,
            categoryApplied: false,
            tagIdsApplied: [5],
          },
        ],
      },
    };
    mockAxiosGet.mockResolvedValueOnce(initialWorkflow).mockResolvedValueOnce(refreshedWorkflow);
    mockAxiosPost.mockResolvedValueOnce(initialPreview).mockResolvedValueOnce(tagApplyResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationSuggestedTags-400');
    fireEvent.click(screen.getByRole('button', { name: /apply all suggested tags/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', { scope: 'TAGS' }),
    );
    await waitFor(() => expectSelectedTagNames(400, ['Business']));
    expect(screen.getByTestId('classificationCategory-400').value).toBe('');
    expect(screen.getByTestId('classificationSuggestedCategory-400').textContent).toContain('Salary');
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('keeps current selections intact when applying all suggested categories fails', async () => {
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
        categoryId: null,
        categoryName: null,
        tagIds: [5],
        tagNames: ['Business'],
      },
    });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValueOnce(classificationPreviewResponse).mockRejectedValueOnce(new Error('apply failed'));
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    fireEvent.click(screen.getByRole('button', { name: /apply all suggested categories/i }));

    await screen.findByText('Could not apply all pending category or tag suggestions. Existing selections were preserved.');
    expect(screen.getByTestId('classificationCategory-400').value).toBe('');
    expectSelectedTagNames(400, ['Business']);
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('reports a partial batch result when the backend skips a classification candidate', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'NOT_EVALUATED' },
    });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValueOnce(classificationPreviewResponse).mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            action: 'SKIPPED',
            error: 'Transaction candidate must be linked to a valid ingestion record',
            candidate: candidateResponse.data.rows[0].candidate,
          },
        ],
      },
    });
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    fireEvent.click(screen.getByRole('button', { name: /apply all suggested categories/i }));

    await screen.findByTestId('workflowApplyAllWarning');
    expect(screen.getByText('Some pending category or tag suggestions could not be applied. Review the affected rows.')).toBeTruthy();
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('reevaluates each scope through preview-only endpoints without applying or confirming', async () => {
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
    const descriptionResponse = {
      data: {
        transactionIngestionId: 100,
        rows: [{ ingestionRecordId: 300, recordIndex: 1, action: 'NO_MATCH' }],
      },
    };
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValueOnce(descriptionResponse).mockResolvedValue(classificationPreviewResponse);

    fireEvent.click(screen.getByRole('button', { name: /reevaluate descriptions/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        apply: false,
        protectManualChanges: true,
      }),
    );
    await waitFor(() => expect(screen.getByRole('button', { name: /reevaluate categories/i }).disabled).toBe(false));

    fireEvent.click(screen.getByRole('button', { name: /reevaluate categories/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', { scope: 'CATEGORY' }),
    );
    await waitFor(() => expect(screen.getByRole('button', { name: /reevaluate tags/i }).disabled).toBe(false));

    fireEvent.click(screen.getByRole('button', { name: /reevaluate tags/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', { scope: 'TAGS' }),
    );
    await waitFor(() => expect(screen.getByRole('button', { name: /reevaluate all/i }).disabled).toBe(false));

    fireEvent.click(screen.getByRole('button', { name: /reevaluate all/i }));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        apply: false,
        protectManualChanges: true,
      }),
    );
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', undefined));
    expect(screen.getByTestId('classificationCategory-400').value).toBe('9');
    expectSelectedTagNames(400, ['Business']);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('reevaluates one eligible row only and keeps invalid and disabled rows unavailable', async () => {
    const readyRows = [
      persistedReviewResponse.data.rows[0],
      { ...persistedReviewResponse.data.rows[0], ingestionRecordId: 304, recordIndex: 5, description: 'Second valid row' },
    ];
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        rows: readyRows,
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'SUGGESTED' },
      304: { id: 404, classificationReviewStatus: 'SUGGESTED' },
    });
    const twoRowPreviewResponse = {
      data: {
        transactionIngestionId: 100,
        rows: [
          { ...classificationPreviewResponse.data.rows[0], hasSuggestions: true },
          {
            ...classificationPreviewResponse.data.rows[0],
            candidateId: 404,
            ingestionRecordId: 304,
            recordIndex: 5,
            candidate: candidateResponse.data.rows[1].candidate,
            description: 'Second valid row',
            suggestedCategory: null,
            suggestedTags: [],
            matchedRules: [],
            hasSuggestions: false,
          },
        ],
      },
    };
    const selectedRowPreviewResponse = {
      data: {
        ...twoRowPreviewResponse.data,
        rows: [twoRowPreviewResponse.data.rows[0]],
      },
    };
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(twoRowPreviewResponse);
    const firstRender = renderPersistedReview();

    await expandRowForRecord(300);
    await expandRowForRecord(304);
    await screen.findByTestId('workflowRowReevaluate-300');
    await screen.findByTestId('classificationConfirmNoSuggestions-404');
    mockAxiosPost.mockClear();
    mockAxiosPost
      .mockResolvedValueOnce({ data: { transactionIngestionId: 100, rows: [{ ingestionRecordId: 300, action: 'NO_MATCH' }] } })
      .mockResolvedValue(selectedRowPreviewResponse);
    fireEvent.click(screen.getByTestId('workflowRowReevaluate-300'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        recordIds: [300],
        apply: false,
        protectManualChanges: true,
      }),
    );
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', {
        candidateIds: [400],
        scope: 'ALL',
      }),
    );
    expect(screen.getByTestId('classificationConfirmNoSuggestions-404')).toBeTruthy();
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(confirmImportCalls()).toHaveLength(0);

    firstRender.unmount();
    const partialWithCandidates = withCandidates(persistedReviewResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(partialWithCandidates);
    const secondRender = renderPersistedReview();
    await screen.findByTestId('workflowRowStatus-300');
    await expandRowForRecord(300);
    expect(screen.queryByTestId('workflowRowReevaluate-301')).toBeNull();
    expect(screen.queryByTestId('workflowRowReevaluate-302')).toBeNull();
    secondRender.unmount();
  });

  it('preserves a manual description and displays its transient reevaluation suggestion', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [
          {
            ...persistedReviewResponse.data.rows[0],
            description: 'Manual salary',
            descriptionReview: {
              source: 'USER_EDIT',
              originalDescription: 'Salary raw',
              normalizedDescription: 'Manual salary',
              resultingDescription: 'Manual salary',
              editedBy: 'user',
            },
          },
        ],
      },
    };
    const candidateResponse = withCandidates(readyResponse, { 300: { id: 400, classificationReviewStatus: 'SUGGESTED' } });
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await expandRowForRecord(300);
    await screen.findByTestId('descriptionReview-badge-300');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValue({
      data: {
        transactionIngestionId: 100,
        rows: [{ ingestionRecordId: 300, action: 'MANUAL_PRESERVED', suggestedDescription: 'Salary normalized' }],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /reevaluate descriptions/i }));

    await waitFor(() =>
      expect(screen.getByTestId('descriptionReview-reevaluationSuggestion-300').textContent).toContain('Salary normalized'),
    );
    expect(screen.getByTestId('workflowApplyAllDescriptionSuggestions').disabled).toBe(true);
    expect(within(rowForRecord(300)).getAllByText('Manual salary').length).toBeGreaterThan(0);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('does not present no-op description, category, or tag previews as pending suggestions', async () => {
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
        classificationReviewStatus: 'SUGGESTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [5],
        tagNames: ['Business'],
      },
    });
    const noOpClassificationPreview = {
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 400,
            ingestionRecordId: 300,
            recordIndex: 1,
            action: 'PREVIEWED',
            candidate: candidateResponse.data.rows[0].candidate,
            suggestedCategory: { id: 9, name: 'Salary', categoryType: 'INCOME' },
            suggestedTags: [{ id: 5, name: 'Business' }],
            matchedRules: [{ ruleId: 20, ruleName: 'Salary rule' }],
            conflicts: [],
            skippedOutputs: [],
            hasSuggestions: true,
          },
        ],
      },
    };
    mockAxiosGet.mockResolvedValue(candidateResponse);
    mockAxiosPost.mockResolvedValue(noOpClassificationPreview);
    renderPersistedReview();

    await screen.findByTestId('classificationSuggestedCategory-400');
    expect(screen.queryByTestId('classificationApplySuggestedCategory-400')).toBeNull();
    expect(screen.queryByTestId('classificationApplySuggestedTags-400')).toBeNull();
    expect(screen.getByTestId('workflowApplyAllSuggestedCategories').disabled).toBe(true);
    expect(screen.getByTestId('workflowApplyAllSuggestedTags').disabled).toBe(true);
    expect(screen.getByTestId('classificationSuggestionCount').textContent).toBe('0');

    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [{ ingestionRecordId: 300, action: 'PREVIEWED', suggestedDescription: 'Salary' }],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /reevaluate descriptions/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        apply: false,
        protectManualChanges: true,
      }),
    );
    expect(screen.queryByTestId('descriptionReview-reevaluationSuggestion-300')).toBeNull();
    expect(screen.queryByTestId('workflowApplyDescriptionSuggestion-300')).toBeNull();
    expect(screen.getByTestId('workflowApplyAllDescriptionSuggestions').disabled).toBe(true);
  });

  it('explicitly applies one suggested description without applying classification or confirming import', async () => {
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
        rows: [{ ...persistedReviewResponse.data.rows[0], description: 'UBER UPM 200220LK5MX' }],
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
    const appliedResponse = withCandidates(
      {
        data: {
          ...readyResponse.data,
          rows: [
            {
              ...readyResponse.data.rows[0],
              description: 'UBER',
              descriptionReview: {
                source: 'DESCRIPTION_RULE',
                originalDescription: 'UBER UPM 200220LK5MX',
                normalizedDescription: 'UBER',
                resultingDescription: 'UBER',
                ruleName: 'Normalize UBER',
              },
            },
          ],
        },
      },
      {
        300: {
          id: 400,
          description: 'UBER',
          classificationReviewStatus: 'USER_SELECTED',
          categoryId: 9,
          categoryName: 'Salary',
          tagIds: [5],
          tagNames: ['Business'],
        },
      },
    );
    mockAxiosGet.mockResolvedValueOnce(candidateResponse).mockResolvedValue(appliedResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('classificationCategory-400');
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [{ ingestionRecordId: 300, action: 'PREVIEWED', suggestedDescription: 'UBER' }],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /reevaluate descriptions/i }));

    await expandRowForRecord(300);
    await screen.findByTestId('workflowApplyDescriptionSuggestion-300');
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [{ ingestionRecordId: 300, action: 'APPLIED' }],
      },
    });
    fireEvent.click(screen.getByTestId('workflowApplyDescriptionSuggestion-300'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        recordIds: [300],
        apply: true,
        protectManualChanges: false,
      }),
    );
    await screen.findByTestId('descriptionReview-badge-300');
    expect(within(rowForRecord(300)).getAllByText('UBER').length).toBeGreaterThan(0);
    expect(screen.getByTestId('classificationCategory-400').value).toBe('9');
    expectSelectedTagNames(400, ['Business']);
    expect(screen.getByTestId('classificationSuggestedCategory-400')).toBeTruthy();
    expect(screen.getByTestId('classificationSuggestedTags-400')).toBeTruthy();
    expect(screen.queryByTestId('workflowApplyDescriptionSuggestion-300')).toBeNull();
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('batch applies current description suggestions while preserving protected manual rows and other transient suggestions', async () => {
    const readyRows = [
      { ...persistedReviewResponse.data.rows[0], description: 'UBER raw' },
      {
        ...persistedReviewResponse.data.rows[0],
        ingestionRecordId: 304,
        recordIndex: 5,
        description: 'Manual taxi',
        descriptionReview: {
          source: 'USER_EDIT',
          originalDescription: 'TAXI raw',
          normalizedDescription: 'Manual taxi',
          resultingDescription: 'Manual taxi',
          editedBy: 'user',
        },
      },
    ];
    const readyResponse = {
      data: {
        ...persistedReviewResponse.data,
        status: 'READY',
        counts: { recordsReceived: 2, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 2, invalidRows: 0 },
        rows: readyRows,
      },
    };
    const candidateResponse = withCandidates(readyResponse, {
      300: { id: 400, classificationReviewStatus: 'SUGGESTED' },
      304: { id: 404, classificationReviewStatus: 'SUGGESTED' },
    });
    const appliedResponse = withCandidates(
      {
        data: {
          ...readyResponse.data,
          rows: [
            {
              ...readyRows[0],
              description: 'UBER',
              descriptionReview: {
                source: 'DESCRIPTION_RULE',
                originalDescription: 'UBER raw',
                normalizedDescription: 'UBER',
                resultingDescription: 'UBER',
                ruleName: 'Normalize UBER',
              },
            },
            readyRows[1],
          ],
        },
      },
      {
        300: { id: 400, description: 'UBER', classificationReviewStatus: 'STALE' },
        304: { id: 404, description: 'Manual taxi', classificationReviewStatus: 'SUGGESTED' },
      },
    );
    mockAxiosGet.mockResolvedValueOnce(candidateResponse).mockResolvedValue(appliedResponse);
    mockAxiosPost.mockResolvedValue(classificationPreviewResponse);
    renderPersistedReview();

    await screen.findByTestId('workflowRowStatus-300');
    await expandRowForRecord(300);
    await expandRowForRecord(304);
    mockAxiosPost.mockClear();
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          { ingestionRecordId: 300, action: 'PREVIEWED', suggestedDescription: 'UBER' },
          { ingestionRecordId: 304, action: 'MANUAL_PRESERVED', suggestedDescription: 'TAXI' },
        ],
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /reevaluate descriptions/i }));

    await screen.findByTestId('workflowApplyAllDescriptionSuggestions');
    expect(screen.getByTestId('workflowApplyAllDescriptionSuggestions').disabled).toBe(false);
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          { ingestionRecordId: 300, action: 'APPLIED' },
          { ingestionRecordId: 304, action: 'MANUAL_PRESERVED', suggestedDescription: 'TAXI' },
        ],
      },
    });
    fireEvent.click(screen.getByTestId('workflowApplyAllDescriptionSuggestions'));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
        recordIds: [300],
        apply: true,
        protectManualChanges: true,
      }),
    );
    await screen.findByTestId('descriptionReview-badge-300');
    expect(within(rowForRecord(300)).getAllByText('UBER').length).toBeGreaterThan(0);
    expect(screen.queryByTestId('workflowApplyDescriptionSuggestion-300')).toBeNull();
    expect(screen.getByTestId('workflowApplyDescriptionSuggestion-304')).toBeTruthy();
    expect(within(rowForRecord(304)).getAllByText('Manual taxi').length).toBeGreaterThan(0);
    expect(mockAxiosPost.mock.calls.filter(([url]) => url.includes('/candidates/apply-rules'))).toHaveLength(0);
    expect(confirmImportCalls()).toHaveLength(0);
  });

  it('does not allow confirm when review needs fixes', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    expect((await screen.findAllByText('Needs review')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
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
    const candidateAfterTagRemoval = withCandidates(readyResponse, {
      300: {
        id: 400,
        classificationReviewStatus: 'USER_SELECTED',
        categoryId: 9,
        categoryName: 'Salary',
        tagIds: [3],
        tagNames: ['Ride share'],
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

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/prepare'));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/rule-preview', undefined));
    expect(mockAxiosPost).not.toHaveBeenCalledWith('api/transaction-ingestions/100/classification-preview');
    expect(screen.getByText('Categories and tags are saved on candidates before confirming the import.')).toBeTruthy();
    expect(screen.getByTestId('classificationSuggestedCategory-400').textContent).toContain('Salary');
    expect(screen.getByTestId('classificationSuggestedTags-400').textContent).toContain('Business');
    await expandRowForRecord(300);
    expect(screen.getByTestId('classificationMatchedRules-400').textContent).toContain('Salary rule');
    expect(screen.getByTestId('classificationCategory-400').value).toBe('');

    fireEvent.change(screen.getByTestId('classificationCategory-400'), { target: { value: '9' } });
    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/400/classification', { categoryId: 9 }),
    );
    await waitFor(() => expect(screen.getByTestId('classificationCategory-400').value).toBe('9'));
    await waitFor(() => expectSelectedTagNames(400, ['Ride share', 'Cash']));
    expect(screen.getByTestId('classificationSuggestedTags-400').textContent).toContain('Business');
    expect(screen.getByTestId('classificationTags-400').querySelector('select[multiple]')).toBeNull();
    mockAxiosPatch.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 300,
        recordIndex: 1,
        candidate: candidateAfterTagRemoval.data.rows[0].candidate,
      },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Remove Cash' }));
    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/400/classification', { tagIds: [3] }),
    );
    await waitFor(() => expectSelectedTagNames(400, ['Ride share']));

    mockAxiosPatch.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 300,
        recordIndex: 1,
        candidate: selectedCandidateResponse.data.rows[0].candidate,
      },
    });
    await addCandidateTag(400, 'Cash');
    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/400/classification', { tagIds: [3, 6] }),
    );
    await waitFor(() => expectSelectedTagNames(400, ['Ride share', 'Cash']));

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

  it('candidate-backed classification applies category and tag suggestions from their own cells before import', async () => {
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
    const categoryAppliedWorkflow = withCandidates(readyResponse, {
      310: {
        id: 410,
        classificationReviewStatus: 'SUGGESTED',
        categoryId: 7,
        categoryName: 'Transport',
        tagIds: [],
        tagNames: [],
      },
      311: { id: 411, classificationReviewStatus: 'NOT_APPLICABLE' },
    });
    mockAxiosGet
      .mockResolvedValueOnce(readyResponse)
      .mockResolvedValueOnce(candidateResponse)
      .mockResolvedValueOnce(categoryAppliedWorkflow)
      .mockResolvedValue(reviewedResponse);
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

    await screen.findByTestId('classificationSuggestedCategory-410');
    expect(screen.getByTestId('classificationSuggestedCategory-410').textContent).toContain('Transport');
    expect(screen.getByTestId('classificationCategory-410').value).toBe('');
    expect(
      within(screen.getByTestId('classificationCategory-410').closest('td')).getByTestId('classificationApplySuggestedCategory-410'),
    ).toBeTruthy();
    expect(
      within(screen.getByTestId('classificationTags-410').closest('td')).getByTestId('classificationApplySuggestedTags-410'),
    ).toBeTruthy();
    expect(screen.queryByTestId('classificationApply-410')).toBeNull();
    expect(screen.queryByTestId('classificationSuggestedCategory-411')).toBeNull();
    expect(screen.queryByTestId('classificationMatchedRules-411')).toBeNull();
    expect(screen.getByTestId('classificationCategory-411').value).toBe('');
    expect(Array.from(screen.getByTestId('classificationCategory-411').options).map(option => option.textContent)).toEqual([
      'No category',
      'Salary',
    ]);

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        rows: [
          {
            candidateId: 410,
            ingestionRecordId: 310,
            recordIndex: 1,
            action: 'APPLIED',
            candidate: categoryAppliedWorkflow.data.rows[0].candidate,
            suggestedCategory: { id: 7, name: 'Transport', categoryType: 'EXPENSE' },
            suggestedTags: [],
            matchedRules: [{ ruleId: 20, ruleName: 'Uber gastos' }],
            conflicts: [],
            skippedOutputs: [],
            hasSuggestions: true,
            categoryApplied: true,
            tagIdsApplied: [],
          },
        ],
      },
    });
    fireEvent.click(screen.getByTestId('classificationApplySuggestedCategory-410'));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', {
        candidateIds: [410],
        scope: 'CATEGORY',
      }),
    );
    expect(screen.getByTestId('classificationCategory-410').value).toBe('7');
    expect(screen.getByTestId('classificationSuggestedTags-410').textContent).toContain('Ride share');

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
            categoryApplied: false,
            tagIdsApplied: [3],
          },
        ],
      },
    });
    fireEvent.click(screen.getByTestId('classificationApplySuggestedTags-410'));
    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/candidates/apply-rules', {
        candidateIds: [410],
        scope: 'TAGS',
      }),
    );
    expectSelectedTagNames(410, ['Ride share']);

    mockAxiosPost.mockResolvedValueOnce({
      data: {
        transactionIngestionId: 100,
        ingestionRecordId: 311,
        recordIndex: 2,
        candidate: reviewedResponse.data.rows[1].candidate,
      },
    });
    await expandRowForRecord(311);
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

    expect((await screen.findByTestId('classificationCategory-400')).value).toBe('9');
    expectSelectedTagNames(400, ['Business']);
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

    expect(await screen.findByText('Could not prepare transaction candidates for classification.')).toBeTruthy();
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

    expect(await screen.findByText('Review every valid row before confirming import.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /confirm import/i }).disabled).toBe(true);
    await expandRowForRecord(320);
    await expandRowForRecord(321);
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
            {
              ...readyRows[0],
              status: 'IMPORTED',
              financialTransactionId: 9001,
              financialTransaction: {
                id: 9001,
                category: { id: 9, name: 'Salary', color: '#0B6E4F' },
                tags: [{ id: 5, name: 'Business', color: '#3B82F6' }],
              },
            },
            { ...readyRows[1], status: 'DISABLED' },
          ],
        },
      });
    renderPersistedReview();

    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenLastCalledWith('api/transaction-ingestions/100/confirm'));
    await waitFor(() => expect(screen.getAllByText('Import completed').length).toBeGreaterThan(0));
    expectRowStatus(300, 'Imported');
    expectRowStatus(302, 'Disabled');
    expect(within(screen.getByTestId('workflowCounts')).getAllByText('0')).toHaveLength(1);
    expect(screen.queryByText('No transactions were created yet.')).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Actions' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Error' })).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm import/i })).toBeNull();
    expect(screen.queryByTestId('workflowRowEdit-300')).toBeNull();
    expect(screen.queryByTestId('workflowRowDisable-300')).toBeNull();
    expect(screen.queryByTestId('workflowRowEnable-302')).toBeNull();
    expect(screen.getByTestId('classificationCategoryFinal-9001').textContent).toBe('Salary');
    expect(screen.getByTestId('classificationTagsFinal-9001').textContent).toBe('Business');
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
            financialTransaction: {
              id: 9001,
              category: { id: 9, name: 'Salary', color: '#0B6E4F' },
              tags: [{ id: 5, name: 'Business', color: '#3B82F6' }],
            },
            candidate: null,
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
    expect(screen.queryByTestId('workflowRowEdit-300')).toBeNull();
    expect(screen.queryByTestId('workflowRowDisable-300')).toBeNull();
    expect(screen.queryByTestId('workflowRowEnable-302')).toBeNull();
    expect(rowForRecord(300).querySelector('input, select, textarea')).toBeNull();
    expect(rowForRecord(302).querySelector('input, select, textarea')).toBeNull();
    await expandRowForRecord(300);
    expect(within(await expandRowForRecord(300)).getByText('Auto-normalized')).toBeTruthy();
    expect(screen.getByTestId('descriptionReview-ruleName-300').textContent).toContain('Normalize Uber');
    expect(screen.getByTestId('classificationCategoryFinal-9001').textContent).toBe('Salary');
    expect(screen.getByTestId('classificationTagsFinal-9001').textContent).toBe('Business');
    expect(screen.queryByTestId('classificationCategoryReadOnly-400')).toBeNull();
    expect(screen.queryByTestId('classificationTagsReadOnly-400')).toBeNull();
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

    expect(await screen.findByRole('button', { name: /confirm import/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /confirm import/i }));

    expect(await screen.findByText('Could not confirm import. Check the review status and try again.')).toBeTruthy();
    expect(screen.queryByText('Import completed')).toBeNull();
    expect(screen.getByRole('button', { name: /confirm import/i })).toBeTruthy();
    expect(screen.getByText('Categories and tags are saved on candidates before confirming the import.')).toBeTruthy();
  });

  it('cancel leaves edited row unchanged', async () => {
    mockAxiosGet.mockResolvedValue(persistedReviewResponse);
    renderPersistedReview();

    await screen.findByTestId('workflowRowStatus-300');
    await expandRowForRecord(300);
    fireEvent.click(screen.getByTestId('workflowRowEdit-300'));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Unsaved edit' } });
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(within(rowForRecord(300)).getByText('Salary')).toBeTruthy();
    expect(screen.queryByText('Unsaved edit')).toBeNull();
    expect(mockAxiosPatch).not.toHaveBeenCalled();
  });

  it('renders global contextual rule actions and opens both scratch editors without navigating away from the persisted review', async () => {
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [persistedReviewResponse.data.rows[0]],
        },
      },
      { 300: { id: 400 } },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation(url =>
      url === 'api/transaction-ingestions/100/candidates/rule-preview'
        ? Promise.resolve(classificationPreviewResponse)
        : Promise.resolve({ data: {} }),
    );
    renderPersistedReview();

    expect(await screen.findByRole('button', { name: /new classification rule/i })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /new description rule/i }));
    expect((await screen.findAllByText('New Description Rule')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('transactionIngestionRuleCreationModal')).toBeTruthy();
    expect(screen.getByTestId('contextualNormalizationRuleExplanation')).toBeTruthy();
    fireEvent.click(within(screen.getByTestId('transactionIngestionRuleCreationModal')).getByRole('button', { name: /cancel/i }));
    fireEvent.click(screen.getByRole('button', { name: /new classification rule/i }));
    expect((await screen.findAllByText('New Classification Rule')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('contextualTransactionRuleExplanation')).toBeTruthy();
    expect(window.location.pathname).toBe('/');
  });

  it('prefills a row normalization rule from raw and normalized descriptions, and save does not reevaluate the row', async () => {
    const row = {
      ...persistedReviewResponse.data.rows[0],
      description: 'Uber',
      descriptionReview: {
        source: 'DESCRIPTION_RULE',
        originalDescription: 'UBER *TRIP 123',
        normalizedDescription: 'Uber',
        resultingDescription: 'Uber',
      },
    };
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [row],
        },
      },
      { 300: { id: 400, description: 'Uber' } },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation(url => {
      if (url === 'api/transaction-ingestions/100/candidates/rule-preview') {
        return Promise.resolve(classificationPreviewResponse);
      }
      if (url === 'api/description-normalization-rules/configured') {
        return Promise.resolve({ data: { id: 901, name: 'Normalize Uber', active: false, conditions: [] } });
      }
      return Promise.resolve({ data: {} });
    });
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(within(screen.getByTestId('workflowRowCreateRule-300')).getByRole('button', { name: /create rule/i }));
    fireEvent.click(await screen.findByTestId('workflowRowCreateNormalization-300'));

    expect(screen.getByTestId('contextualRuleSelectedRow').textContent).toContain('Selected row #1');
    expect(screen.getByTestId('contextualDescriptionRuleResult').value).toBe('Uber');
    expect(screen.getByTestId('contextualDescriptionRuleConditionValue-0').value).toBe('UBER *TRIP 123');

    fireEvent.click(within(screen.getByTestId('transactionIngestionRuleCreationModal')).getByRole('button', { name: /^save$/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/description-normalization-rules/configured',
        expect.objectContaining({
          active: true,
          resultingDescription: 'Uber',
          conditions: [expect.objectContaining({ operator: 'CONTAINS', value: 'UBER *TRIP 123', position: 0 })],
        }),
      ),
    );
    expect(mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-ingestions/100/descriptions/reevaluate')).toHaveLength(0);
    expect((await screen.findByTestId('workflowRuleCreationFeedback')).textContent).toContain('Rule saved.');
    expect(rowForRecord(300).textContent).toContain('Uber');
  });

  it('prefills a row transaction rule from the persisted candidate and reevaluates only that candidate on request', async () => {
    const row = { ...persistedReviewResponse.data.rows[0], description: 'Uber', signedAmount: '-12.00', amount: '12.00', flow: 'OUT' };
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [row],
        },
      },
      {
        300: { id: 400, description: 'Uber', flow: 'OUT', categoryId: 7, categoryName: 'Transport', tagIds: [3], tagNames: ['Ride share'] },
      },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation(url => {
      if (url === 'api/transaction-ingestions/100/candidates/rule-preview') {
        return Promise.resolve({ data: { ...classificationPreviewResponse.data, rows: [] } });
      }
      if (url === 'api/transaction-rules/configured') {
        return Promise.resolve({ data: { id: 902, name: 'Rule for Uber', conditions: [] } });
      }
      return Promise.resolve({ data: {} });
    });
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(within(screen.getByTestId('workflowRowCreateRule-300')).getByRole('button', { name: /create rule/i }));
    fireEvent.click(await screen.findByTestId('workflowRowCreateTransaction-300'));
    expect(screen.getByTestId('contextualRuleSelectedRow').textContent).toContain('Selected row #1');
    await waitFor(() => expect(screen.getByTestId('transactionRuleCategoryIdentity').textContent).toContain('Transport'));
    await waitFor(() => expect(screen.getByTestId('transactionRuleResultTagsChip').textContent).toContain('Ride share'));
    const reevaluateButton = within(screen.getByTestId('transactionIngestionRuleCreationModal')).getByRole('button', {
      name: /save and reevaluate this row/i,
    });
    await waitFor(() => expect(reevaluateButton.disabled).toBe(false));
    fireEvent.click(reevaluateButton);

    let configuredCall: [string, { conditions?: unknown[] }] | undefined;
    await waitFor(() => {
      configuredCall = mockAxiosPost.mock.calls.find(([url]) => url === 'api/transaction-rules/configured') as
        | [string, { conditions?: unknown[] }]
        | undefined;
      expect(configuredCall).toBeDefined();
      expect(configuredCall?.[1]).toEqual(
        expect.objectContaining({
          resultingCategory: { id: 7 },
          resultingTags: [{ id: '3' }],
          conditions: expect.arrayContaining([
            expect.objectContaining({ field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Uber' }),
            expect.objectContaining({ field: 'FLOW', operator: 'EQUALS', value: 'OUT' }),
          ]),
        }),
      );
    });
    expect(configuredCall?.[1].conditions).not.toEqual(expect.arrayContaining([expect.objectContaining({ field: 'ACCOUNT' })]));
    expect(
      mockAxiosPost.mock.calls.some(
        ([url, payload]) =>
          url === 'api/transaction-ingestions/100/candidates/rule-preview' &&
          payload?.candidateIds?.length === 1 &&
          payload.candidateIds[0] === 400,
      ),
    ).toBe(true);
    expect(mockAxiosPatch).not.toHaveBeenCalled();
  });

  it('creates a scratch normalization rule and reevaluates only applicable rows in the current ingestion', async () => {
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [persistedReviewResponse.data.rows[0]],
        },
      },
      { 300: { id: 400, description: 'Salary', flow: 'IN' } },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation((url: string) => {
      if (url === 'api/transaction-ingestions/100/candidates/rule-preview') {
        return Promise.resolve(classificationPreviewResponse);
      }
      if (url === 'api/description-normalization-rules/configured') {
        return Promise.resolve({ data: { id: 901, name: 'Normalize salary', active: false } });
      }
      if (url === 'api/transaction-ingestions/100/descriptions/reevaluate') {
        return Promise.resolve({ data: { transactionIngestionId: 100, rows: [{ ingestionRecordId: 300, action: 'NO_MATCH' }] } });
      }
      return Promise.resolve({ data: {} });
    });
    renderPersistedReview();

    fireEvent.click(await screen.findByRole('button', { name: /new description rule/i }));
    const modal = await screen.findByTestId('transactionIngestionRuleCreationModal');
    expect(within(modal).getByTestId('contextualNormalizationRuleExplanation').textContent).toContain('original file description');
    fireEvent.change(within(modal).getByLabelText('Name'), { target: { value: 'Normalize salary' } });
    fireEvent.change(within(modal).getByLabelText('Resulting description'), { target: { value: 'Salary' } });
    fireEvent.click(within(modal).getByRole('button', { name: /add condition/i }));
    fireEvent.change(within(modal).getByTestId('contextualDescriptionRuleConditionValue-0'), { target: { value: 'SALARY' } });
    expect(within(modal).getByTestId('active')).toHaveProperty('checked', true);
    fireEvent.click(within(modal).getByRole('button', { name: /save and reevaluate all applicable rows/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/description-normalization-rules/configured',
        expect.objectContaining({ name: 'Normalize salary', active: true }),
      ),
    );
    expect(mockAxiosPost).toHaveBeenCalledWith('api/transaction-ingestions/100/descriptions/reevaluate', {
      apply: false,
      protectManualChanges: true,
    });
    expect((await screen.findByTestId('workflowRuleCreationFeedback')).textContent).toContain(
      'applicable rows in this import were reevaluated',
    );
  });

  it('does not invent an original description when a selected normalization-rule row is stale', async () => {
    const row = { ...persistedReviewResponse.data.rows[0], description: 'Normalized value', descriptionReview: null };
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [row],
        },
      },
      { 300: { id: 400, description: 'Normalized value', flow: 'IN' } },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation((url: string) =>
      url === 'api/transaction-ingestions/100/candidates/rule-preview'
        ? Promise.resolve(classificationPreviewResponse)
        : Promise.resolve({ data: {} }),
    );
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(within(screen.getByTestId('workflowRowCreateRule-300')).getByRole('button', { name: /create rule/i }));
    fireEvent.click(await screen.findByTestId('workflowRowCreateNormalization-300'));

    expect(await screen.findByTestId('contextualRuleSourceUnavailable')).toBeTruthy();
    expect(screen.queryByTestId('descriptionNormalizationRuleContextualForm')).toBeNull();
    expect(mockAxiosPost.mock.calls.filter(([url]) => url === 'api/description-normalization-rules/configured')).toHaveLength(0);
  });

  it('blocks an inactive row output from becoming a new transaction-rule result', async () => {
    const row = { ...persistedReviewResponse.data.rows[0], description: 'Taxi', flow: 'OUT' };
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [row],
        },
      },
      { 300: { id: 400, description: 'Taxi', flow: 'OUT', categoryId: 7, categoryName: 'Former transport' } },
    );
    mockGetSelectableCategories.mockResolvedValue([
      { id: 7, name: 'Former transport', categoryType: 'EXPENSE', active: false },
      { id: 8, name: 'Restaurants', categoryType: 'EXPENSE', active: true },
    ]);
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation((url: string) =>
      url === 'api/transaction-ingestions/100/candidates/rule-preview'
        ? Promise.resolve(classificationPreviewResponse)
        : Promise.resolve({ data: {} }),
    );
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(within(screen.getByTestId('workflowRowCreateRule-300')).getByRole('button', { name: /create rule/i }));
    fireEvent.click(await screen.findByTestId('workflowRowCreateTransaction-300'));

    const modal = screen.getByTestId('transactionIngestionRuleCreationModal');
    await waitFor(() => expect(within(modal).getByRole('button', { name: /^save$/i }).disabled).toBe(false));
    const saveButton = within(modal).getByRole('button', { name: /^save$/i });
    fireEvent.click(saveButton);
    expect(await screen.findByText(/inactive category or tag cannot be used/i)).toBeTruthy();
  });

  it('reports reevaluation failure after saving a contextual rule without creating it twice', async () => {
    const row = { ...persistedReviewResponse.data.rows[0], description: 'Taxi', flow: 'OUT' };
    const readyResponse = withCandidates(
      {
        data: {
          ...persistedReviewResponse.data,
          status: 'READY',
          counts: { recordsReceived: 1, recordsCreated: 0, recordsSkipped: 0, recordsRejected: 0, validRows: 1, invalidRows: 0 },
          rows: [row],
        },
      },
      { 300: { id: 400, description: 'Taxi', flow: 'OUT', categoryId: 7, categoryName: 'Transport' } },
    );
    mockAxiosGet.mockResolvedValue(readyResponse);
    mockAxiosPost.mockImplementation((url: string, payload?: { candidateIds?: number[] }) => {
      if (url === 'api/transaction-ingestions/100/candidates/rule-preview') {
        return payload?.candidateIds?.length ? Promise.reject(new Error('preview failed')) : Promise.resolve(classificationPreviewResponse);
      }
      if (url === 'api/transaction-rules/configured') {
        return Promise.resolve({ data: { id: 902, name: 'Rule for Taxi' } });
      }
      return Promise.resolve({ data: {} });
    });
    renderPersistedReview();

    await expandRowForRecord(300);
    fireEvent.click(within(screen.getByTestId('workflowRowCreateRule-300')).getByRole('button', { name: /create rule/i }));
    fireEvent.click(await screen.findByTestId('workflowRowCreateTransaction-300'));
    await waitFor(() => expect(screen.getByTestId('transactionRuleCategoryIdentity').textContent).toContain('Transport'));
    const reevaluateButton = within(screen.getByTestId('transactionIngestionRuleCreationModal')).getByRole('button', {
      name: /save and reevaluate this row/i,
    });
    await waitFor(() => expect(reevaluateButton.disabled).toBe(false));
    fireEvent.click(reevaluateButton);

    await waitFor(() => expect(mockAxiosPost.mock.calls.filter(([url]) => url === 'api/transaction-rules/configured')).toHaveLength(1));
    expect((await screen.findByTestId('workflowRuleCreationFeedback')).textContent).toContain(
      'Rule saved, but reevaluation could not be completed.',
    );
  });
});
