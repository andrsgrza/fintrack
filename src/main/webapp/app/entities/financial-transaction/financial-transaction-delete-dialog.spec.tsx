import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { TranslatorContext } from 'react-jhipster';
import axios from 'axios';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enGlobal from 'app/../i18n/en/global.json';

import { FinancialTransactionDeleteDialog } from './financial-transaction-delete-dialog';

jest.mock('axios');

const mockedAxios = axios as jest.Mocked<typeof axios>;

const DeleteDialogRoutes = () => (
  <Routes>
    <Route path="/financial-transaction/:id/delete" element={<FinancialTransactionDeleteDialog />} />
    <Route path="/financial-transaction" element={<DeleteReturnLocation />} />
  </Routes>
);

const DeleteReturnLocation = () => {
  const location = useLocation();
  return <div data-testid="financialTransactionReturnLocation">{`${location.pathname}${location.search}`}</div>;
};

const renderDialog = (entry = '/financial-transaction/42/delete?page=3&sort=transactionDate,asc') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <DeleteDialogRoutes />
    </MemoryRouter>,
  );

describe('FinancialTransactionDeleteDialog', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enFinancialTransaction);
    TranslatorContext.registerTranslations('en', enGlobal);
    TranslatorContext.setLocale('en');
  });

  beforeEach(() => {
    mockedAxios.delete.mockReset();
  });

  it('shows a direct product confirmation without a deletion preview', () => {
    renderDialog();

    expect(screen.getByTestId('financialTransactionDeleteConfirmation')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete' }).disabled).toBe(false);
    expect(mockedAxios.delete).not.toHaveBeenCalled();
  });

  it('deletes directly, then returns to the exact list query', async () => {
    mockedAxios.delete.mockResolvedValue({ status: 204 });
    renderDialog();

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(mockedAxios.delete).toHaveBeenCalledWith('api/financial-transactions/42'));
    expect((await screen.findByTestId('financialTransactionReturnLocation')).textContent).toBe(
      '/financial-transaction?page=3&sort=transactionDate,asc',
    );
  });

  it('keeps the dialog open and reports a direct delete failure', async () => {
    mockedAxios.delete.mockRejectedValue(new Error('delete failed'));
    renderDialog('/financial-transaction/42/delete');

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect((await screen.findByTestId('financialTransactionDeleteError')).textContent).toContain(
      'We could not delete this transaction. Try again.',
    );
    await waitFor(() => expect(screen.getByRole('button', { name: 'Delete' }).disabled).toBe(false));
  });
});
