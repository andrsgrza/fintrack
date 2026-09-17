import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enGlobal from 'app/../i18n/en/global.json';
const mockDispatch = jest.fn();
let mockState: any;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./financial-account.reducer', () => ({
  deleteEntity: Object.assign(jest.fn(), {
    rejected: {
      match: (action: { type?: string }) => action.type === 'financialAccount/delete_entity/rejected',
    },
  }),
  getEntity: jest.fn(id => ({ type: 'financialAccount/get_entity', payload: id })),
}));

const { deleteEntity: mockDeleteEntity, getEntity: mockGetEntity } = jest.requireMock('./financial-account.reducer');
const { FinancialAccountDeleteDialog } = require('./financial-account-delete-dialog');

const DeleteDialogRoutes = () => (
  <Routes>
    <Route path="/financial-account/:id/delete" element={<FinancialAccountDeleteDialog />} />
    <Route path="/financial-account" element={<div data-testid="financialAccountOverview">Account overview</div>} />
  </Routes>
);

const renderDialog = () =>
  render(
    <MemoryRouter initialEntries={['/financial-account/42/delete']}>
      <DeleteDialogRoutes />
    </MemoryRouter>,
  );

describe('FinancialAccountDeleteDialog', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enFinancialAccount);
    TranslatorContext.registerTranslations('en', enGlobal);
    TranslatorContext.setLocale('en');
    mockGetEntity.mockClear();
    mockDeleteEntity.mockReset();
    mockDispatch.mockReset();
    mockState = {
      financialAccount: {
        entity: { id: 42, name: 'Travel card', accountType: 'CREDIT_CARD', currency: 'MXN', active: true },
        updateSuccess: false,
      },
    };
    mockDispatch.mockImplementation(action => Promise.resolve(action));
  });

  it('uses product account wording instead of a generated ID confirmation', () => {
    renderDialog();

    expect(screen.getByText('Delete account')).toBeTruthy();
    expect(screen.getByText('Are you sure you want to permanently delete Travel card?')).toBeTruthy();
    expect(
      screen.getByText(
        'Related imported or workflow data is removed only when it can be cleaned up safely. Deletion can be blocked by protected transaction data.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Financial Account 42')).toBeNull();
  });

  it('shows a contextual error for a protected-reference delete rejection without exposing the raw backend message', async () => {
    mockDeleteEntity.mockReturnValue({
      type: 'financialAccount/delete_entity/rejected',
      error: { response: { status: 400 }, message: 'Account cannot be deleted because it is used by transaction candidates.' },
    });
    mockDispatch.mockImplementation(action => Promise.resolve(action));
    renderDialog();

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect((await screen.findByTestId('financialAccountDeleteError')).textContent).toContain(
      'This account cannot be deleted because it is still referenced by protected transaction data. Resolve those references first.',
    );
    expect(screen.queryByText('Account cannot be deleted because it is used by transaction candidates.')).toBeNull();
  });

  it('returns to the account overview after a successful delete state', async () => {
    const view = renderDialog();
    mockState.financialAccount.updateSuccess = true;
    view.rerender(
      <MemoryRouter initialEntries={['/financial-account/42/delete']}>
        <DeleteDialogRoutes />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByTestId('financialAccountOverview')).toBeTruthy());
  });
});
