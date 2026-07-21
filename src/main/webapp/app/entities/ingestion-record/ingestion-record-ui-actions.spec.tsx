import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enIngestionRecord from 'app/../i18n/en/ingestionRecord.json';
import { IngestionRecord } from './ingestion-record';
import { IngestionRecordDetail } from './ingestion-record-detail';

const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'ingestionRecord/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'ingestionRecord/getEntity', payload: id }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./ingestion-record.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  getEntity: id => mockGetEntity(id),
}));

const ingestionRecord = {
  id: 300,
  recordIndex: 1,
  externalRecordId: 'csv-row-1',
  status: 'VALID',
  rawData: '{"normalized":{"description":"Salary"}}',
  errorCode: null,
  errorMessage: null,
  createdAt: '2026-07-13T16:00:00Z',
  financialTransaction: null,
  transactionIngestion: { id: 100 },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enIngestionRecord);
  TranslatorContext.setLocale('en');
};

const renderList = () => {
  mockState = {
    ingestionRecord: {
      entities: [ingestionRecord],
      entity: {},
      loading: false,
      updating: false,
      updateSuccess: false,
      totalItems: 1,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/ingestion-record']}>
      <Routes>
        <Route path="/ingestion-record" element={<IngestionRecord />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    ingestionRecord: {
      entities: [],
      entity: ingestionRecord,
      loading: false,
      updating: false,
      updateSuccess: false,
      totalItems: 0,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/ingestion-record/300']}>
      <Routes>
        <Route path="/ingestion-record/:id" element={<IngestionRecordDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('IngestionRecord technical UI actions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('marks the list as technical and keeps only View action', () => {
    renderList();

    expect(screen.getByText('Technical view — ingestion rows are managed from the Transaction Ingestion workflow.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /create a new ingestion record/i })).toBeNull();
    expect(screen.getByRole('link', { name: /view/i }).getAttribute('href')).toBe('/ingestion-record/300');
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });

  it('marks the detail as technical, hides Edit, and links back to the parent workflow', () => {
    renderDetail();

    expect(screen.getByText('Technical view — ingestion rows are managed from the Transaction Ingestion workflow.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.getByRole('link', { name: /open workflow/i }).getAttribute('href')).toBe('/transaction-ingestion/100');
  });
});
