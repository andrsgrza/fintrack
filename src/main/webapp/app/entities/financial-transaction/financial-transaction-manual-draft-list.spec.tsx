import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enAccountType from 'app/../i18n/en/accountType.json';
import FinancialTransactionManualDraftList from './financial-transaction-manual-draft-list';
import { cancelManualDraft, getManualDrafts } from './services/manual-transaction-candidate.service';

jest.mock('./services/manual-transaction-candidate.service');

const mockGetManualDrafts = getManualDrafts as jest.Mock;
const mockCancelManualDraft = cancelManualDraft as jest.Mock;

const renderDraftList = () =>
  render(
    <MemoryRouter initialEntries={['/financial-transaction/drafts']}>
      <Routes>
        <Route path="/financial-transaction/drafts" element={<FinancialTransactionManualDraftList />} />
      </Routes>
    </MemoryRouter>,
  );

const findDraftRow = async () => {
  await screen.findByText('Coffee');
  const row = document.querySelector('[data-cy="manualDraftRow"]') as HTMLElement | null;
  expect(row).not.toBeNull();
  return row!;
};

const draftSummary = {
  id: 77,
  status: 'READY_TO_POST',
  classificationReviewStatus: 'USER_SELECTED',
  accountId: 1,
  accountName: 'Checking',
  accountType: 'DEBIT',
  accountLastFourDigits: '1234',
  accountActive: true,
  transactionDate: '2026-07-13',
  description: 'Coffee',
  amount: 12,
  flow: 'OUT',
  currencySnapshot: 'MXN',
  createdAt: '2026-07-13T16:00:00Z',
  updatedAt: '2026-07-13T17:00:00Z',
  categoryName: 'Transport',
  categoryId: 9,
  categoryParentName: 'Expenses',
  categoryColor: '#2463A5',
  categoryActive: true,
  tagNames: ['Business', 'Personal'],
  tags: [
    { id: 31, name: 'Business', color: '#E31B23', active: true },
    { id: 32, name: 'Personal', color: '#2463A5', active: true },
  ],
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enAccountType);
  TranslatorContext.setLocale('en');
};

describe('FinancialTransaction manual draft recovery list', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockGetManualDrafts.mockReset();
    mockCancelManualDraft.mockReset();
    mockCancelManualDraft.mockResolvedValue({ data: { ...draftSummary, status: 'CANCELLED' } });
  });

  it('loads and renders manual drafts with compact summary data', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();

    expect(screen.getByText('Loading drafts…')).toBeTruthy();
    expect(await screen.findByText('Transaction drafts')).toBeTruthy();
    expect(screen.getByRole('link', { name: /view transactions/i }).getAttribute('href')).toBe('/financial-transaction');
    const row = await findDraftRow();
    expect(within(row).getByText('Coffee')).toBeTruthy();
    expect(within(row).getByText('Checking · Debit account · MXN · ••••1234')).toBeTruthy();
    expect(within(row).getByText('13/07/2026')).toBeTruthy();
    expect(within(row).getByText('−12.00 MXN')).toBeTruthy();
    expect(within(row).getByText('Expense')).toBeTruthy();
    expect(row.textContent).toContain('MXN');
    expect(within(row).getByText('Ready to post')).toBeTruthy();
    expect(within(row).getByText('Manually selected')).toBeTruthy();
    expect(within(row).getByText('Expenses › Transport')).toBeTruthy();
    expect(within(row).getByText('Business')).toBeTruthy();
    expect(within(row).getByText('Personal')).toBeTruthy();
    const category = row.querySelector('[data-cy="transactionCategory"]') as HTMLElement;
    expect(category.className).toContain('rounded-1');
    expect(category.getAttribute('data-color-treatment')).toBe('category');
    expect(category.getAttribute('style')).toContain('background-color');
    const tagChip = within(row).getByText('Business');
    expect(tagChip.className).toContain('rounded-pill');
    expect(tagChip.getAttribute('data-color-treatment')).toBe('tag');
    expect(tagChip.getAttribute('style')).toContain('background-color');
    expect(within(row).queryByText('77')).toBeNull();
  });

  it('shows empty state with create CTA', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [] });

    renderDraftList();

    expect(await screen.findByText('No transaction drafts are waiting for you.')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /new transaction/i })[1].getAttribute('href')).toBe('/financial-transaction/new');
  });

  it('shows safe fallback labels for incomplete or unknown draft summaries', async () => {
    mockGetManualDrafts.mockResolvedValue({
      data: [
        {
          id: 88,
          status: 'SOMETHING_NEW',
          classificationReviewStatus: 'CLASSIFICATION_UNKNOWN',
          tagNames: [],
        },
      ],
    });

    renderDraftList();

    await screen.findByText('No description');
    const row = document.querySelector('[data-cy="manualDraftRow"]') as HTMLElement;
    expect(within(row).getByText('No description')).toBeTruthy();
    expect(within(row).getByText('No account')).toBeTruthy();
    expect(within(row).getByText('No date')).toBeTruthy();
    expect(within(row).getByText('No amount')).toBeTruthy();
    expect(within(row).getAllByText('Unknown')).toHaveLength(2);
    expect(within(row).queryByText('SOMETHING_NEW')).toBeNull();
    expect(within(row).queryByText('CLASSIFICATION_UNKNOWN')).toBeNull();
    expect(within(row).getByText('No category')).toBeTruthy();
    expect(within(row).getByText('No tags')).toBeTruthy();
  });

  it('links resume action to the manual draft route', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();

    const row = await findDraftRow();
    expect(
      within(row)
        .getByRole('link', { name: /resume/i })
        .getAttribute('href'),
    ).toBe('/financial-transaction/drafts/77');
  });

  it('cancels a draft through the command endpoint and removes it from the list', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();
    const row = await findDraftRow();
    fireEvent.click(within(row).getByRole('button', { name: /more draft actions/i }));
    fireEvent.click(within(row).getByRole('menuitem', { name: /cancel draft/i }));

    await waitFor(() => expect(mockCancelManualDraft).toHaveBeenCalledWith(77));
    await waitFor(() => expect(screen.queryByText('Coffee')).toBeNull());
    expect(screen.getByText('No transaction drafts are waiting for you.')).toBeTruthy();
    confirmSpy.mockRestore();
  });

  it('keeps the draft visible and shows an error when cancel fails', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });
    mockCancelManualDraft.mockRejectedValue(new Error('boom'));

    renderDraftList();
    const row = await findDraftRow();
    fireEvent.click(within(row).getByRole('button', { name: /more draft actions/i }));
    fireEvent.click(within(row).getByRole('menuitem', { name: /cancel draft/i }));

    expect(await screen.findByText('Could not cancel the draft.')).toBeTruthy();
    expect(screen.getByText('Coffee')).toBeTruthy();
    confirmSpy.mockRestore();
  });

  it('shows a clear load error if manual drafts cannot be fetched', async () => {
    mockGetManualDrafts.mockRejectedValue(new Error('boom'));

    renderDraftList();

    expect(await screen.findByText('Could not load drafts.')).toBeTruthy();
    expect(screen.queryByText('No transaction drafts are waiting for you.')).toBeNull();
  });
});
