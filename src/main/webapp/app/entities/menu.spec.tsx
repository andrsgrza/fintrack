import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter } from 'react-router';

import enGlobal from 'app/../i18n/en/global.json';
import EntitiesMenu from './menu';

describe('Entities menu technical markers', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enGlobal);
    TranslatorContext.setLocale('en');
  });

  it('keeps FileIngestion and IngestionRecord visible and marks both as Technical', () => {
    render(
      <MemoryRouter>
        <EntitiesMenu />
      </MemoryRouter>,
    );

    expect(screen.getByRole('menuitem', { name: /file ingestion technical/i }).getAttribute('href')).toBe('/file-ingestion');
    expect(screen.getByRole('menuitem', { name: /ingestion record technical/i }).getAttribute('href')).toBe('/ingestion-record');
    expect(screen.getAllByText('Technical')).toHaveLength(2);
  });
});
