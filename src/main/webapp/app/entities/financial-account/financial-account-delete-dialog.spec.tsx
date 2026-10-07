import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TranslatorContext } from 'react-jhipster';

import enAccountType from 'app/../i18n/en/accountType.json';
import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enGlobal from 'app/../i18n/en/global.json';
import {
  IFinancialAccountDeletionBlocker,
  IFinancialAccountDeletionPreview,
} from 'app/shared/model/financial-account-deletion-preview.model';

const mockGetFinancialAccountForDeletion = jest.fn();
const mockGetFinancialAccountDeletionPreview = jest.fn();
const mockHardDeleteFinancialAccount = jest.fn();

jest.mock('./financial-account-hard-delete.service', () => ({
  getFinancialAccountForDeletion: (...args: unknown[]) => mockGetFinancialAccountForDeletion(...args),
  getFinancialAccountDeletionPreview: (...args: unknown[]) => mockGetFinancialAccountDeletionPreview(...args),
  hardDeleteFinancialAccount: (...args: unknown[]) => mockHardDeleteFinancialAccount(...args),
}));

const { FinancialAccountDeleteDialog } = require('./financial-account-delete-dialog');

const safePreview = (overrides: Partial<IFinancialAccountDeletionPreview> = {}): IFinancialAccountDeletionPreview => ({
  accountId: 42,
  accountName: 'Travel card',
  canHardDelete: true,
  blockers: [],
  counts: {
    financialTransactions: 2,
    manualCandidates: 3,
    manualDraftCandidates: 2,
    manualPostedCandidates: 1,
    transactionIngestions: 1,
    ingestionRecords: 4,
    fileImportCandidates: 1,
    creditAccountDetails: 1,
    budgetLinks: 0,
    subscriptions: 0,
    crossAccountTransfers: 0,
    ruleAccountReferences: 0,
  },
  ...overrides,
});

const DeleteDialogRoutes = () => (
  <Routes>
    <Route path="/financial-account/:id/delete" element={<FinancialAccountDeleteDialog />} />
    <Route path="/financial-account" element={<div data-testid="financialAccountOverview">Account overview</div>} />
    <Route path="/financial-account/:id/edit" element={<div data-testid="financialAccountEdit">Account edit</div>} />
  </Routes>
);

const renderDialog = () =>
  render(
    <MemoryRouter initialEntries={['/financial-account/42/delete']}>
      <DeleteDialogRoutes />
    </MemoryRouter>,
  );

const blockerCases: Array<[IFinancialAccountDeletionBlocker['code'], string]> = [
  ['CROSS_ACCOUNT_TRANSFER', 'This account participates in 2 transfers with other accounts.'],
  ['BUDGET_SCOPE_WOULD_BROADEN', 'A budget uses this account as its only specific account.'],
  ['RULE_ACCOUNT_CONDITION_REFERENCE', 'A rule uses this account as a condition.'],
  ['CORRUPT_CANDIDATE', 'Fintrack detected inconsistent transaction data associated with this account.'],
  ['CORRUPT_INGESTION_GRAPH', 'Fintrack detected inconsistent import data associated with this account.'],
  ['UNSUPPORTED_API_IMPORT_CANDIDATE', 'This account contains API import data that does not yet support permanent deletion.'],
];

describe('FinancialAccountDeleteDialog', () => {
  beforeEach(() => {
    TranslatorContext.registerTranslations('en', enFinancialAccount);
    TranslatorContext.registerTranslations('en', enAccountType);
    TranslatorContext.registerTranslations('en', enGlobal);
    TranslatorContext.setLocale('en');
    mockGetFinancialAccountForDeletion.mockReset();
    mockGetFinancialAccountDeletionPreview.mockReset();
    mockHardDeleteFinancialAccount.mockReset();
    mockGetFinancialAccountForDeletion.mockResolvedValue({
      data: { id: 42, name: 'Travel card', institutionName: 'Fintrack Bank', accountType: 'CREDIT_CARD', currency: 'MXN' },
    });
    mockGetFinancialAccountDeletionPreview.mockResolvedValue({ data: safePreview() });
  });

  it('keeps permanent delete disabled while deletion information loads', () => {
    mockGetFinancialAccountForDeletion.mockReturnValue(new Promise(() => undefined));
    mockGetFinancialAccountDeletionPreview.mockReturnValue(new Promise(() => undefined));
    renderDialog();

    expect(screen.getByTestId('financialAccountDeletePreviewLoading')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Permanently delete' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders meaningful safe-preview consequences and omits zero counts', async () => {
    renderDialog();

    await waitFor(() => expect(screen.getByTestId('financialAccountDeleteSummary')).toBeTruthy());
    expect(screen.getByRole('dialog').getAttribute('aria-labelledby')).toBe('financial-account-delete-title');
    expect(screen.getByTestId('financialAccountDeleteDialogHeading').getAttribute('id')).toBe('financial-account-delete-title');
    expect(screen.getByText('2 transactions')).toBeTruthy();
    expect(screen.getByText('2 manual drafts')).toBeTruthy();
    expect(screen.getByText('1 import')).toBeTruthy();
    expect(screen.getByText('4 import records')).toBeTruthy();
    expect(screen.getByText("This account's card details")).toBeTruthy();
    expect(screen.queryByText('0 imports')).toBeNull();
    expect(screen.getByText('Will be preserved')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Permanently delete' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it.each(blockerCases)('renders product copy for the %s blocker and disables delete', async (code, expectedCopy) => {
    mockGetFinancialAccountDeletionPreview.mockResolvedValue({
      data: safePreview({ canHardDelete: false, blockers: [{ code, count: 2, relatedIds: [7, 8] }] }),
    });
    renderDialog();

    expect((await screen.findByTestId(`financialAccountDeleteBlocker-${code}`)).textContent).toContain(expectedCopy);
    expect((screen.getByRole('button', { name: 'Permanently delete' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('calls only the hard-delete command and returns to the overview on success', async () => {
    mockHardDeleteFinancialAccount.mockResolvedValue({ status: 204 });
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Permanently delete' }));

    await waitFor(() => expect(mockHardDeleteFinancialAccount).toHaveBeenCalledWith('42'));
    expect(await screen.findByTestId('financialAccountOverview')).toBeTruthy();
  });

  it('keeps the modal open and replaces the preview with blockers after a 409 race', async () => {
    mockHardDeleteFinancialAccount.mockRejectedValue({
      response: {
        status: 409,
        data: { code: 'ACCOUNT_HARD_DELETE_BLOCKED', blockers: [{ code: 'RULE_ACCOUNT_CONDITION_REFERENCE', count: 1, relatedIds: [9] }] },
      },
    });
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Permanently delete' }));

    expect((await screen.findByTestId('financialAccountDeleteBlocker-RULE_ACCOUNT_CONDITION_REFERENCE')).textContent).toContain(
      'A rule uses this account as a condition.',
    );
    expect((screen.getByRole('button', { name: 'Permanently delete' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.queryByTestId('financialAccountDeleteError')).toBeNull();
  });

  it('shows a local safe error and keeps permanent delete disabled when preview loading fails', async () => {
    mockGetFinancialAccountDeletionPreview.mockRejectedValue(new Error('preview unavailable'));
    renderDialog();

    expect((await screen.findByTestId('financialAccountDeletePreviewError')).textContent).toContain(
      'Could not load deletion information. Close this dialog and try again.',
    );
    expect((screen.getByRole('button', { name: 'Permanently delete' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('takes the user to account edit when choosing to deactivate instead', async () => {
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Deactivate instead' }));

    expect(await screen.findByTestId('financialAccountEdit')).toBeTruthy();
  });
});
