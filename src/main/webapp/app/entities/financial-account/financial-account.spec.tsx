import React from 'react';
import axios from 'axios';
import { render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter } from 'react-router-dom';

import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enAccountType from 'app/../i18n/en/accountType.json';
import { FinancialAccount } from './financial-account';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;

const renderOverview = () => render(<FinancialAccount />, { wrapper: MemoryRouter });

describe('FinancialAccount overview', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enFinancialAccount);
    TranslatorContext.registerTranslations('en', enAccountType);
    TranslatorContext.setLocale('en');
    mockAxiosGet.mockReset();
  });

  it('renders product account summaries rather than generated fields', async () => {
    mockAxiosGet.mockResolvedValue({
      data: [
        {
          id: 12,
          name: 'Daily account',
          accountType: 'DEBIT',
          currency: 'MXN',
          active: true,
          lastFourDigits: '1234',
          color: '#2463A5',
          currentBalance: 1250.5,
        },
        {
          id: 13,
          name: 'Travel card',
          accountType: 'CREDIT_CARD',
          currency: 'MXN',
          active: false,
          lastFourDigits: '9876',
          color: '#8B3D88',
          currentDebt: 1300,
          creditLimit: 5000,
          availableCredit: 3700,
          statementDay: 15,
          paymentDueDay: 5,
          annualInterestRate: 65,
          missingCreditDetails: false,
        },
      ],
    });

    const { container } = renderOverview();

    await waitFor(() => expect(screen.getByText('Daily account')).toBeTruthy());
    expect(mockAxiosGet).toHaveBeenCalledWith('api/financial-accounts/overview');
    expect(screen.getByText('Debit account')).toBeTruthy();
    expect(screen.getByText('Credit card')).toBeTruthy();
    expect(screen.getByText('Current balance')).toBeTruthy();
    expect(screen.getByText('1250.5 MXN')).toBeTruthy();
    expect(screen.getByText('Current debt')).toBeTruthy();
    expect(screen.getByText('1300 MXN')).toBeTruthy();
    expect(screen.getByText('Credit limit')).toBeTruthy();
    expect(screen.getByText('Available credit')).toBeTruthy();
    expect(screen.getByText('Statement closing day')).toBeTruthy();
    expect(screen.getByText('Payment due day')).toBeTruthy();
    expect(screen.getByText('Annual interest rate')).toBeTruthy();
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getAllByText(/Ends in/)[1].parentElement?.textContent).toContain('9876');
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('Transaction Ingestions')).toBeNull();
    expect(screen.queryByText('12')).toBeNull();

    expect(screen.getByRole('link', { name: 'Daily account' }).getAttribute('href')).toBe('/financial-account/12');
    expect(screen.getByRole('link', { name: 'Travel card' }).getAttribute('href')).toBe('/financial-account/13');
    expect(screen.getAllByTestId('financialAccountOverviewCard')[0].style.borderInlineStart).toContain('#2463A5');
    expect(screen.getAllByTestId('financialAccountOverviewCard')[1].style.borderInlineStart).toContain('#8B3D88');
    expect(screen.queryByText('#2463A5')).toBeNull();
    expect(container.querySelector('.flex-btn-group-container')).toBeNull();
    expect(screen.queryByRole('link', { name: /View/ })).toBeNull();
    expect(screen.getAllByTestId('financialAccountOverviewCard')).toHaveLength(2);
    expect(container.querySelectorAll('[data-cy="entityEditButton"]')[1].getAttribute('href')).toBe('/financial-account/13/edit');
    expect(container.querySelectorAll('[data-cy="financialAccountActionsMenuToggle"]')).toHaveLength(2);
  });

  it('renders a safe incomplete-card state', async () => {
    mockAxiosGet.mockResolvedValue({
      data: [
        {
          id: 14,
          name: 'Legacy card',
          accountType: 'CREDIT_CARD',
          currency: 'MXN',
          active: true,
          currentDebt: 250,
          missingCreditDetails: true,
        },
      ],
    });

    renderOverview();

    await waitFor(() => expect(screen.getByText('Legacy card')).toBeTruthy());
    expect(screen.getByText('250 MXN')).toBeTruthy();
    expect(screen.getByText('Credit card details have not been configured yet.')).toBeTruthy();
    expect(screen.queryByText('Credit limit')).toBeNull();
  });

  it('renders clear empty and error states', async () => {
    mockAxiosGet.mockResolvedValueOnce({ data: [] });
    const { unmount } = renderOverview();

    await waitFor(() => expect(screen.getByText('No accounts yet')).toBeTruthy());
    expect(screen.getAllByRole('link', { name: /Create a new Financial Account/ })[1].getAttribute('href')).toBe('/financial-account/new');
    unmount();

    mockAxiosGet.mockRejectedValueOnce(new Error('overview unavailable'));
    renderOverview();
    await waitFor(() => expect(screen.getByText('Accounts could not be loaded.')).toBeTruthy());
  });
});
