export const copy = (locale: string, spanish: string, english: string) => (locale.startsWith('es') ? spanish : english);

export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

export const browserToday = () => {
  const date = new Date();
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
};

const regionalLocale = (locale: string) => (locale.startsWith('es') ? 'es-MX' : 'en-US');

export const formatBudgetAmount = (value: number | null | undefined, currency: string, locale: string) => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(regionalLocale(locale), {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
};

export const formatBudgetDate = (value: string, locale: string) =>
  new Intl.DateTimeFormat(regionalLocale(locale), { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${value}T00:00:00Z`),
  );

export const dateRange = (start: string, endExclusive: string, locale: string) => {
  const end = new Date(`${endExclusive}T00:00:00Z`);
  end.setUTCDate(end.getUTCDate() - 1);
  const last = end.toISOString().slice(0, 10);
  return `${formatBudgetDate(start, locale)} – ${formatBudgetDate(last, locale)}`;
};

export const userFacingError = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    if (response?.data?.message) return response.data.message;
  }
  return error instanceof Error ? error.message : fallback;
};

/** Backend keeps stable validation text for REST callers; the product UI never displays that text untranslated. */
export const calendarValidationError = (error: unknown, locale: string, fallback: string) => {
  const originalMessage = userFacingError(error, '');
  const message = originalMessage.toLowerCase();
  if (message.includes('natural boundary')) {
    return copy(
      locale,
      'El inicio debe ser un límite válido del calendario natural seleccionado.',
      'The start must be a valid boundary for the selected natural calendar.',
    );
  }
  if (message.includes('custom calendar mode is not supported')) {
    return copy(
      locale,
      'El calendario personalizado no está disponible para intervalos de días.',
      'Custom calendar is not available for day intervals.',
    );
  }
  if (message.includes('custom calendar requires a day') || message.includes('custom day of month is allowed only')) {
    return copy(locale, 'Elige un día recurrente entre 1 y 31.', 'Choose a recurring day from 1 through 31.');
  }
  if (message.includes('custom weeks use the selected weekday')) {
    return copy(locale, 'Las semanas personalizadas usan el día de inicio elegido.', 'Custom weeks use the selected start weekday.');
  }
  if (message.includes('first custom calendar cycle')) {
    return copy(
      locale,
      'El primer ciclo debe comenzar en el límite configurado.',
      'The first cycle must begin at the configured boundary.',
    );
  }
  if (message.includes('complete period boundary')) {
    return copy(
      locale,
      'La fecha de finalización debe coincidir con un límite completo de período.',
      'The end date must be a complete period boundary.',
    );
  }
  if (message.includes('termination') || message.includes('recurrence end') || message.includes('recurrence mode')) {
    return copy(locale, 'Revisa la finalización de la recurrencia.', 'Review the recurrence ending.');
  }
  if (message.includes('interval'))
    return copy(locale, 'El intervalo debe ser un número entero mayor que cero.', 'The interval must be a positive whole number.');
  return originalMessage || fallback;
};
