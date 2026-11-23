import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import {
  BudgetNotificationPreferencesEditor,
  BudgetNotificationPreferencesDraft,
  compareThresholdPercentages,
  normalizeThresholdPercentage,
  validateNotificationPreferences,
} from './budget-notification-preferences';
import { formatBudgetThresholdPercentage } from './budget-utils';

const byCy = (value: string) => {
  const element = document.querySelector<HTMLElement>(`[data-cy="${value}"]`);
  if (!element) throw new Error(`Missing ${value}`);
  return element;
};

const EditorHarness = ({ initial }: { initial: BudgetNotificationPreferencesDraft }) => {
  const [draft, setDraft] = useState(initial);
  return (
    <>
      <BudgetNotificationPreferencesEditor locale="en" type="EXPENSE" value={draft} onChange={setDraft} idPrefix="notification" />
      <output data-testid="draft">{JSON.stringify(draft)}</output>
    </>
  );
};

describe('BudgetNotificationPreferencesEditor', () => {
  it('keeps the selector out of the tab order while threshold alerts are off and preserves configured values through toggles', () => {
    render(<EditorHarness initial={{ percentageAlertsEnabled: false, thresholdPercentages: ['87.1250'], periodCloseEnabled: false }} />);

    expect(document.querySelector('[data-cy="notificationThresholdSelector"]')).toBeNull();
    expect(byCy('notificationPeriodCloseEnabled')).toBeTruthy();
    fireEvent.click(byCy('notificationThresholdEnabled'));
    expect(byCy('notificationThresholdSelector')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove 87.125%' })).toBeTruthy();
    fireEvent.click(byCy('notificationThresholdEnabled'));
    expect(document.querySelector('[data-cy="notificationThresholdSelector"]')).toBeNull();
    fireEvent.click(byCy('notificationThresholdEnabled'));
    expect(screen.getByRole('button', { name: 'Remove 87.125%' })).toBeTruthy();
    expect(screen.getByTestId('draft').textContent).toContain('87.1250');
  });

  it('uses accessible suggestion toggles and adds an exact custom decimal without Number coercion', () => {
    render(<EditorHarness initial={{ percentageAlertsEnabled: true, thresholdPercentages: [], periodCloseEnabled: false }} />);

    const fifty = byCy('notificationSuggestion50');
    expect(fifty.getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(fifty);
    expect(fifty.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(fifty);
    expect(fifty.getAttribute('aria-pressed')).toBe('false');

    fireEvent.click(byCy('notificationOpenCustomThreshold'));
    fireEvent.change(byCy('notificationCustomThreshold'), { target: { value: '87.1250' } });
    fireEvent.click(byCy('notificationAddThreshold'));

    expect(screen.getByTestId('draft').textContent).toContain('87.1250');
    expect(screen.getByRole('button', { name: 'Remove 87.125%' })).toBeTruthy();
  });

  it.each(['', '0', '50.12345', '1000000000000000', '1e2', '-1'])('rejects invalid custom percentage %s', invalid => {
    render(<EditorHarness initial={{ percentageAlertsEnabled: true, thresholdPercentages: [], periodCloseEnabled: false }} />);
    fireEvent.click(byCy('notificationOpenCustomThreshold'));
    fireEvent.change(byCy('notificationCustomThreshold'), { target: { value: invalid } });
    fireEvent.click(byCy('notificationAddThreshold'));
    expect(screen.getByRole('alert').textContent).toMatch(/positive percentage/i);
    expect(screen.getByTestId('draft').textContent).toContain('"thresholdPercentages":[]');
  });

  it('rejects numerically equivalent threshold values and enforces the twenty-value maximum', () => {
    const maximum = Array.from({ length: 20 }, (_, index) => String(index + 1));
    expect(
      validateNotificationPreferences(
        { percentageAlertsEnabled: true, thresholdPercentages: ['50', '50.0'], periodCloseEnabled: false },
        'en',
      ),
    ).toBe('Percentages must be unique.');
    render(<EditorHarness initial={{ percentageAlertsEnabled: true, thresholdPercentages: maximum, periodCloseEnabled: false }} />);
    expect(byCy('notificationThresholdCount').textContent).toContain('20 of 20');
    expect((byCy('notificationOpenCustomThreshold') as HTMLButtonElement).disabled).toBe(true);
  });

  it('compares and formats threshold strings exactly without floating-point conversion', () => {
    expect(normalizeThresholdPercentage('00087.1250')).toBe('87.125');
    expect(normalizeThresholdPercentage('0.0000')).toBeNull();
    expect(['100', '87.1250', '50.5000'].sort(compareThresholdPercentages)).toEqual(['50.5000', '87.1250', '100']);
    expect(formatBudgetThresholdPercentage('87.1250', 'en')).toBe('87.125%');
  });
});
