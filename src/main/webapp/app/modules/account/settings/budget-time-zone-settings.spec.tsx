import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import axios from 'axios';

import { BudgetTimeZoneSettings, detectedBrowserTimeZone } from './budget-time-zone-settings';

jest.mock('axios');

const mockedAxios = axios as jest.Mocked<typeof axios>;

describe('BudgetTimeZoneSettings', () => {
  beforeEach(() => {
    mockedAxios.get.mockReset();
    mockedAxios.put.mockReset();
    jest.spyOn(Intl, 'DateTimeFormat').mockImplementation(
      ((_: string, options?: Intl.DateTimeFormatOptions) =>
        ({
          resolvedOptions: () => ({ timeZone: options?.timeZone ?? 'America/New_York' }),
          format: () => 'Eastern Daylight Time',
        }) as unknown as Intl.DateTimeFormat) as typeof Intl.DateTimeFormat,
    );
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('detects a browser suggestion but never persists it before an explicit confirmation', async () => {
    mockedAxios.get.mockResolvedValue({ data: { timeZone: null, configurationRequired: true, effectiveChanges: [] } });

    render(<BudgetTimeZoneSettings locale="es" />);

    expect(detectedBrowserTimeZone()).toBe('America/New_York');
    expect((await screen.findByText(/Sugerimos la zona horaria/)).textContent).toContain('America/New_York');
    expect(mockedAxios.put).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar zona horaria' }));
    await waitFor(() => expect(mockedAxios.put).toHaveBeenCalledWith('api/account/budget-time-zone', { timeZone: 'America/New_York' }));
  });

  it('keeps the persisted profile timezone selected even when the browser reports another zone', async () => {
    mockedAxios.get.mockResolvedValue({
      data: { timeZone: 'America/Mexico_City', configurationRequired: false, effectiveChanges: [] },
    });

    render(<BudgetTimeZoneSettings locale="en" />);

    const input = await screen.findByLabelText('IANA time zone');
    expect(input).toHaveProperty('value', 'America/Mexico_City');
    expect(screen.queryByText(/This browser suggests/)).toBeNull();
    expect(mockedAxios.put).not.toHaveBeenCalled();
  });

  it('only sends the manually selected value and restores the authoritative value on cancel', async () => {
    mockedAxios.get.mockResolvedValue({ data: { timeZone: 'UTC', configurationRequired: false, effectiveChanges: [] } });
    mockedAxios.put.mockResolvedValue({
      data: {
        timeZone: 'Asia/Tokyo',
        configurationRequired: false,
        effectiveChanges: [{ budgetId: 7, budgetName: 'Food', effectivePeriodStart: '2026-04-01' }],
      },
    });

    render(<BudgetTimeZoneSettings locale="en" />);

    const input = await screen.findByLabelText('IANA time zone');
    fireEvent.change(input, { target: { value: 'Asia/Tokyo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(input).toHaveProperty('value', 'UTC');

    fireEvent.change(input, { target: { value: 'Asia/Tokyo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm time zone' }));
    await waitFor(() => expect(mockedAxios.put).toHaveBeenCalledWith('api/account/budget-time-zone', { timeZone: 'Asia/Tokyo' }));
    expect((await screen.findByRole('alert')).textContent).toContain('2026-04-01');
  });
});
