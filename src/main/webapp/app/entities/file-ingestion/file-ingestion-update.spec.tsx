import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFileIngestion from 'app/../i18n/en/fileIngestion.json';
import FileIngestionRoutes from './index';

jest.mock('app/config/store', () => ({
  useAppDispatch: () => jest.fn(),
  useAppSelector: selector =>
    selector({
      fileIngestion: {
        entities: [],
        entity: {},
        loading: false,
        updating: false,
        updateSuccess: false,
      },
    }),
}));

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFileIngestion);
  TranslatorContext.setLocale('en');
};

const renderRoute = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/file-ingestion/*" element={<FileIngestionRoutes />} />
      </Routes>
    </MemoryRouter>,
  );

describe('FileIngestion generated write routes', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it.each(['/file-ingestion/new', '/file-ingestion/200/edit', '/file-ingestion/200/delete'])('shows write unavailable for %s', path => {
    renderRoute(path);

    expect(screen.getByText('File Ingestion technical view')).toBeTruthy();
    expect(screen.queryByLabelText('CSV file')).toBeNull();
    expect(screen.queryByLabelText('Original Filename')).toBeNull();
    expect(screen.queryByRole('button', { name: /save|upload csv|delete/i })).toBeNull();
  });
});
