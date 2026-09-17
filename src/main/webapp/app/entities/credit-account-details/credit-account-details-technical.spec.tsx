import React from 'react';
import { render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enCreditAccountDetails from 'app/../i18n/en/creditAccountDetails.json';
import { CreditAccountDetails } from './credit-account-details';
import { CreditAccountDetailsDetail } from './credit-account-details-detail';

const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'creditAccountDetails/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'creditAccountDetails/getEntity', payload: id }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./credit-account-details.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  getEntity: id => mockGetEntity(id),
}));

const details = {
  id: 10,
  creditLimit: 5000,
  statementDay: 15,
  paymentDueDay: 5,
  annualInterestRate: 65,
  account: { id: 42, name: 'Travel card', accountType: 'CREDIT_CARD' },
};

const renderList = () => {
  mockState = { creditAccountDetails: { entities: [details], loading: false } };
  return render(
    <MemoryRouter>
      <CreditAccountDetails />
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = { creditAccountDetails: { entity: details } };
  return render(
    <MemoryRouter initialEntries={['/credit-account-details/10']}>
      <Routes>
        <Route path="/credit-account-details/:id" element={<CreditAccountDetailsDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('CreditAccountDetails technical surfaces', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enCreditAccountDetails);
    TranslatorContext.setLocale('en');
  });

  beforeEach(() => {
    mockDispatch.mockClear();
    mockGetEntities.mockClear();
    mockGetEntity.mockClear();
  });

  it('renders the direct list as technical read-only and links to the parent account', () => {
    renderList();

    expect(screen.getByTestId('creditAccountDetailsTechnicalNotice').textContent).toContain('Technical');
    expect(screen.getByTestId('creditAccountDetailsTechnicalNotice').textContent).toContain(
      'Credit card details are normally managed from the parent account.',
    );
    expect(screen.getByRole('link', { name: 'Travel card' }).getAttribute('href')).toBe('/financial-account/42');
    expect(screen.queryByTestId('entityCreateButton')).toBeNull();
    expect(screen.queryByTestId('entityEditButton')).toBeNull();
    expect(screen.queryByTestId('entityDeleteButton')).toBeNull();
  });

  it('renders direct detail as technical and offers parent-account management only', () => {
    renderDetail();

    expect(screen.getByTestId('creditAccountDetailsTechnicalNotice').textContent).toContain('Technical');
    expect(screen.getByRole('link', { name: 'Travel card' }).getAttribute('href')).toBe('/financial-account/42');
    expect(screen.getByTestId('creditAccountDetailsManageParentAccountButton').getAttribute('href')).toBe('/financial-account/42/edit');
    expect(screen.queryByTestId('entityEditButton')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
  });
});
