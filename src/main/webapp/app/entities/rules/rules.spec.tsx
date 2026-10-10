import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import enRules from 'app/../i18n/en/rules.json';
import esRules from 'app/../i18n/es/rules.json';
import RulesRoutes from './index';

const renderRules = (locale = 'en') => {
  TranslatorContext.setLocale(locale);

  return render(
    <MemoryRouter initialEntries={['/rules']}>
      <Routes>
        <Route path="/rules/*" element={<RulesRoutes />} />
        <Route path="/description-normalization-rule" element={<div data-testid="descriptionManager">Description manager</div>} />
        <Route path="/transaction-rule" element={<div data-testid="classificationManager">Classification manager</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('Rules product landing', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enRules);
    TranslatorContext.registerTranslations('es', esRules);
  });

  it('renders both rule engines with links to their preserved manager routes', () => {
    renderRules();

    expect(screen.getByRole('heading', { name: 'Rules' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Description Rules' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Classification Rules' })).toBeTruthy();
    expect(screen.getByText('The first matching rule determines the normalized description.')).toBeTruthy();
    expect(screen.getByText('Multiple rules can match; priority determines which category takes precedence.')).toBeTruthy();

    const descriptionLink = screen.getByRole('link', { name: 'Manage description rules' });
    const classificationLink = screen.getByRole('link', { name: 'Manage classification rules' });
    expect(descriptionLink.getAttribute('href')).toBe('/description-normalization-rule');
    expect(classificationLink.getAttribute('href')).toBe('/transaction-rule');

    fireEvent.click(descriptionLink);
    expect(screen.getByTestId('descriptionManager')).toBeTruthy();
  });

  it('uses Spanish product copy and preserves the classification manager destination', () => {
    renderRules('es');

    expect(screen.getByRole('heading', { name: 'Reglas' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Reglas de descripción' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Reglas de clasificación' })).toBeTruthy();
    expect(screen.getByText('La primera coincidencia determina la descripción normalizada.')).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: 'Administrar reglas de clasificación' }));
    expect(screen.getByTestId('classificationManager')).toBeTruthy();
  });
});
