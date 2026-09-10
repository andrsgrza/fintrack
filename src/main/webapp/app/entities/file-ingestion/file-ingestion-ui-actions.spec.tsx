import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFileIngestion from 'app/../i18n/en/fileIngestion.json';
import FileIngestionRoutes from './index';
import { FileIngestion } from './file-ingestion';
import { FileIngestionDetail } from './file-ingestion-detail';

const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'fileIngestion/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'fileIngestion/getEntity', payload: id }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./file-ingestion.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  getEntity: id => mockGetEntity(id),
}));

const fileIngestion = {
  id: 200,
  originalFilename: 'statement.csv',
  fileType: 'CSV',
  contentType: 'text/csv',
  fileSizeBytes: 123,
  checksum: 'abc123',
  storageKey: null,
  parserName: 'fintrack-canonical-csv',
  parserVersion: '1.0',
  statementStartDate: '2026-07-01',
  statementEndDate: '2026-07-31',
  createdAt: '2026-07-13T16:00:00Z',
  transactionIngestion: { id: 100 },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFileIngestion);
  TranslatorContext.setLocale('en');
};

const renderList = () => {
  mockState = {
    fileIngestion: {
      entities: [fileIngestion],
      entity: {},
      loading: false,
      updating: false,
      updateSuccess: false,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/file-ingestion']}>
      <Routes>
        <Route path="/file-ingestion" element={<FileIngestion />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    fileIngestion: {
      entities: [],
      entity: fileIngestion,
      loading: false,
      updating: false,
      updateSuccess: false,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/file-ingestion/200']}>
      <Routes>
        <Route path="/file-ingestion/:id" element={<FileIngestionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderRoute = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/file-ingestion/*" element={<FileIngestionRoutes />} />
      </Routes>
    </MemoryRouter>,
  );

describe('FileIngestion technical UI actions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('marks the list as technical and keeps only View action', () => {
    renderList();

    expect(screen.getByText('Technical view — File metadata is managed by the Transaction Ingestion workflow.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /create a new file ingestion/i })).toBeNull();
    expect(screen.getByRole('link', { name: /view/i }).getAttribute('href')).toBe('/file-ingestion/200');
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });

  it('marks the detail as technical, hides Edit, and links back to the parent workflow', () => {
    renderDetail();

    expect(screen.getByText('Technical view — File metadata is managed by the Transaction Ingestion workflow.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.getByRole('link', { name: /open workflow/i }).getAttribute('href')).toBe('/transaction-ingestion/100');
  });

  it('renders unavailable state for direct generated write routes', () => {
    renderRoute('/file-ingestion/new');
    expect(screen.getByText('File Ingestion technical view')).toBeTruthy();
    expect(
      screen.getByText(
        'File Ingestion create, edit, and delete are not product actions. File metadata is created and managed by the Transaction Ingestion workflow.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();

    renderRoute('/file-ingestion/200/edit');
    expect(screen.getAllByText('File Ingestion technical view')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();

    renderRoute('/file-ingestion/200/delete');
    expect(screen.getAllByText('File Ingestion technical view')).toHaveLength(3);
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });
});
