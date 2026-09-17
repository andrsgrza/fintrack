import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enCreditAccountDetails from 'app/../i18n/en/creditAccountDetails.json';
import CreditAccountDetailsRoutes from './index';

const renderTechnicalWriteRoute = (route: string) =>
  render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/credit-account-details/*" element={<CreditAccountDetailsRoutes />} />
      </Routes>
    </MemoryRouter>,
  );

describe('CreditAccountDetails technical write routes', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enCreditAccountDetails);
    TranslatorContext.setLocale('en');
  });

  it('keeps the direct create route resolvable but points users to Financial Accounts', () => {
    renderTechnicalWriteRoute('/credit-account-details/new');

    expect(screen.getByTestId('creditAccountDetailsTechnicalNotice').textContent).toContain('Direct creation and editing are unavailable');
    expect(screen.getByRole('link', { name: /go to accounts/i }).getAttribute('href')).toBe('/financial-account');
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
    expect(screen.queryByLabelText(/credit limit/i)).toBeNull();
  });

  it('keeps the direct edit route resolvable without exposing a child write form', () => {
    renderTechnicalWriteRoute('/credit-account-details/10/edit');

    expect(screen.getByRole('heading', { name: /direct editing unavailable/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
    expect(screen.queryByRole('combobox')).toBeNull();
  });
});
