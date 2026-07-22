import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enIngestionRecord from 'app/../i18n/en/ingestionRecord.json';
import { IngestionRecordUpdate } from './ingestion-record-update';

const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'ingestionRecord/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'ingestionRecord/reset' }));
const mockCreateEntity = jest.fn(entity => ({ type: 'ingestionRecord/createEntity', payload: entity }));
const mockUpdateEntity = jest.fn(entity => ({ type: 'ingestionRecord/updateEntity', payload: entity }));
const mockGetFinancialTransactionCandidates = jest.fn(() => ({ type: 'financialTransaction/fetch_ingestion_record_candidates' }));
const mockGetTransactionIngestions = jest.fn(params => ({ type: 'transactionIngestion/getEntities', payload: params }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./ingestion-record.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  updateEntity: entity => mockUpdateEntity(entity),
}));

jest.mock('app/entities/financial-transaction/financial-transaction.reducer', () => ({
  getEntitiesWhereIngestionRecordIsNull: () => mockGetFinancialTransactionCandidates(),
}));

jest.mock('app/entities/transaction-ingestion/transaction-ingestion.reducer', () => ({
  getEntities: params => mockGetTransactionIngestions(params),
}));

const baseState = {
  financialTransaction: {
    ingestionRecordParentCandidates: [],
  },
  transactionIngestion: {
    entities: [{ id: 100 }],
  },
  ingestionRecord: {
    entity: {
      id: 300,
      recordIndex: 1,
      externalRecordId: 'csv-row-1',
      status: 'VALID',
      rawData: '{}',
      errorCode: null,
      errorMessage: null,
      createdAt: '2026-07-13T16:00:00Z',
      financialTransaction: null,
      transactionIngestion: { id: 100 },
    },
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enIngestionRecord);
  TranslatorContext.setLocale('en');
};

describe('IngestionRecord technical create/edit marker', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
    mockState = baseState;
  });

  it('marks standalone create as technical', () => {
    render(
      <MemoryRouter initialEntries={['/ingestion-record/new']}>
        <Routes>
          <Route path="/ingestion-record/new" element={<IngestionRecordUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Technical view — ingestion rows are managed from the Transaction Ingestion workflow.')).toBeTruthy();
  });

  it('marks standalone edit as technical', () => {
    render(
      <MemoryRouter initialEntries={['/ingestion-record/300/edit']}>
        <Routes>
          <Route path="/ingestion-record/:id/edit" element={<IngestionRecordUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Technical view — ingestion rows are managed from the Transaction Ingestion workflow.')).toBeTruthy();
  });
});
