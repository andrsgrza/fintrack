import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enApiIngestion from 'app/../i18n/en/apiIngestion.json';
import { ApiIngestion } from './api-ingestion';
import { ApiIngestionDetail } from './api-ingestion-detail';
import { ApiIngestionWriteUnavailable } from './api-ingestion-write-unavailable';

const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'apiIngestion/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'apiIngestion/getEntity', payload: id }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./api-ingestion.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  getEntity: id => mockGetEntity(id),
}));

const apiIngestion = {
  id: 200,
  requestId: 'api-request-1',
  idempotencyKey: 'idem-1',
  sourceSystem: 'partner-api',
  apiVersion: 'v1',
  endpoint: '/transactions',
  clientReference: 'client-ref-1',
  receivedAt: '2026-07-13T16:00:00Z',
  createdAt: '2026-07-13T16:00:00Z',
  transactionIngestion: { id: 100 },
  apiTokenNameSnapshot: 'API token',
  apiTokenPrefixSnapshot: 'ftk_',
  apiTokenIdSnapshot: 300,
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enApiIngestion);
  TranslatorContext.setLocale('en');
};

const renderList = () => {
  mockState = {
    apiIngestion: {
      entities: [apiIngestion],
      entity: {},
      loading: false,
      updating: false,
      updateSuccess: false,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/api-ingestion']}>
      <Routes>
        <Route path="/api-ingestion" element={<ApiIngestion />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    apiIngestion: {
      entities: [],
      entity: apiIngestion,
      loading: false,
      updating: false,
      updateSuccess: false,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/api-ingestion/200']}>
      <Routes>
        <Route path="/api-ingestion/:id" element={<ApiIngestionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderWriteUnavailable = (path = '/api-ingestion/new') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/api-ingestion/new" element={<ApiIngestionWriteUnavailable />} />
        <Route path="/api-ingestion/:id/edit" element={<ApiIngestionWriteUnavailable />} />
        <Route path="/api-ingestion/:id/delete" element={<ApiIngestionWriteUnavailable />} />
      </Routes>
    </MemoryRouter>,
  );

describe('ApiIngestion technical UI actions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('marks the list as technical and keeps only View action', () => {
    renderList();

    expect(screen.getByText('Technical view — API ingestion product flow is deferred. This page is metadata/debug only.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /create a new api ingestion/i })).toBeNull();
    expect(screen.getByRole('link', { name: /view/i }).getAttribute('href')).toBe('/api-ingestion/200');
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });

  it('marks the detail as technical and hides Edit', () => {
    renderDetail();

    expect(screen.getByText('Technical view — API ingestion product flow is deferred. This page is metadata/debug only.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /edit/i })).toBeNull();
    expect(screen.getByRole('link', { name: /back/i }).getAttribute('href')).toBe('/api-ingestion');
  });

  it('renders a safe unavailable state for the new route instead of the generated create form', () => {
    renderWriteUnavailable('/api-ingestion/new');

    expect(screen.getByText('Api Ingestion technical view')).toBeTruthy();
    expect(
      screen.getByText(
        'Api Ingestion create, edit, and delete are not product actions yet. API ingestion metadata is managed by future API ingestion workflow commands.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
  });

  it('renders a safe unavailable state for edit and delete routes', () => {
    renderWriteUnavailable('/api-ingestion/200/edit');
    expect(screen.getByText('Api Ingestion technical view')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();

    renderWriteUnavailable('/api-ingestion/200/delete');
    expect(screen.getAllByText('Api Ingestion technical view')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });
});
