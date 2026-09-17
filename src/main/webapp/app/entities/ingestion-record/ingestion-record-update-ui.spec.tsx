import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enIngestionRecord from 'app/../i18n/en/ingestionRecord.json';
import IngestionRecordRoutes from './index';

jest.mock('app/config/store', () => ({
  useAppDispatch: () => jest.fn(),
  useAppSelector: selector =>
    selector({
      ingestionRecord: {
        entities: [],
        entity: {},
        loading: false,
        updating: false,
        updateSuccess: false,
        totalItems: 0,
      },
    }),
}));

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enIngestionRecord);
  TranslatorContext.setLocale('en');
};

const renderRoute = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/ingestion-record/*" element={<IngestionRecordRoutes />} />
      </Routes>
    </MemoryRouter>,
  );

describe('IngestionRecord generated write routes', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it.each(['/ingestion-record/new', '/ingestion-record/300/edit', '/ingestion-record/300/delete'])(
    'shows write unavailable for %s',
    path => {
      renderRoute(path);

      expect(screen.getByText('Ingestion Record technical view')).toBeTruthy();
      expect(screen.queryByLabelText('Raw Data')).toBeNull();
      expect(screen.queryByLabelText('Status')).toBeNull();
      expect(screen.queryByRole('button', { name: /save|delete/i })).toBeNull();
    },
  );
});
