import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter } from 'react-router';

import enGlobal from 'app/../i18n/en/global.json';
import esGlobal from 'app/../i18n/es/global.json';
import EntitiesMenu from './menu';

describe('Entities menu technical markers', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enGlobal);
    TranslatorContext.registerTranslations('es', esGlobal);
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
  });

  it('keeps Financial Accounts as the product entry and hides CreditAccountDetails from the normal menu', () => {
    render(
      <MemoryRouter>
        <EntitiesMenu />
      </MemoryRouter>,
    );

    expect(screen.getByRole('menuitem', { name: /financial account/i }).getAttribute('href')).toBe('/financial-account');
    expect(screen.queryByRole('menuitem', { name: /credit card details/i })).toBeNull();
  });

  it('uses the product Tag labels in English and Spanish', () => {
    const { unmount } = render(
      <MemoryRouter>
        <EntitiesMenu />
      </MemoryRouter>,
    );

    expect(screen.getByRole('menuitem', { name: 'Tags' }).getAttribute('href')).toBe('/tag');

    unmount();
    TranslatorContext.setLocale('es');
    render(
      <MemoryRouter>
        <EntitiesMenu />
      </MemoryRouter>,
    );

    expect(screen.getByRole('menuitem', { name: 'Etiquetas' }).getAttribute('href')).toBe('/tag');
  });
});
