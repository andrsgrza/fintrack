export const copy = (locale: string, spanish: string, english: string) => (locale.startsWith('es') ? spanish : english);

export const BUDGET_TARGET_MAX_INTEGER_DIGITS = 19;
const PLAIN_DECIMAL = /^-?\d+(?:\.\d+)?$/;

export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

export const browserToday = () => {
  const date = new Date();
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
};

export const regionalLocale = (locale: string) => (locale.startsWith('es') ? 'es-MX' : 'en-US');

type DecimalParts = { negative: boolean; integer: string; fraction: string };

const decimalParts = (value: string): DecimalParts | null => {
  if (!PLAIN_DECIMAL.test(value)) return null;
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [rawInteger, fraction = ''] = unsigned.split('.');
  return { negative, integer: rawInteger.replace(/^0+(?=\d)/, ''), fraction };
};

export const isBudgetDecimal = (value: unknown): value is string => typeof value === 'string' && decimalParts(value) !== null;

export const isBudgetDecimalZero = (value: string) => {
  const parts = decimalParts(value);
  return parts !== null && /^0+$/.test(parts.integer) && (!parts.fraction || /^0+$/.test(parts.fraction));
};

export const isBudgetDecimalNegative = (value: string) => {
  const parts = decimalParts(value);
  return parts?.negative === true && !isBudgetDecimalZero(value);
};

export const absoluteBudgetDecimal = (value: string) => (isBudgetDecimalNegative(value) ? value.slice(1) : value);

/** Exact client validation for the server's DECIMAL(21,2) Budget target contract. */
export const budgetTargetValidationError = (value: string, locale: string): string | null => {
  const parts = decimalParts(value);
  if (!parts) return copy(locale, 'Escribe un importe decimal válido.', 'Enter a valid decimal amount.');
  if (parts.fraction.length > 2)
    return copy(locale, 'El importe objetivo admite como máximo dos decimales.', 'The target amount supports at most two decimal places.');
  if (parts.integer.replace(/^0+/, '').length > BUDGET_TARGET_MAX_INTEGER_DIGITS)
    return copy(locale, 'El importe objetivo excede la precisión permitida.', 'The target amount exceeds the supported precision.');
  if (parts.negative || isBudgetDecimalZero(value))
    return copy(locale, 'Escribe un importe objetivo mayor que cero.', 'Enter a target amount greater than zero.');
  return null;
};

const groupedInteger = (integer: string, locale: string) =>
  new Intl.NumberFormat(regionalLocale(locale), { maximumFractionDigits: 0 }).format(BigInt(integer));

const localizedDecimal = (value: string, locale: string, minimumFractionDigits = 2): string | null => {
  const parts = decimalParts(value);
  if (!parts) return null;
  const integer = groupedInteger(parts.integer, locale);
  const decimalSeparator =
    new Intl.NumberFormat(regionalLocale(locale), { minimumFractionDigits: 1, maximumFractionDigits: 1 })
      .formatToParts(0.1)
      .find(part => part.type === 'decimal')?.value ?? '.';
  // Exact decimal transport can preserve storage scale (for example, a threshold
  // arrives as "50.0000"). Trailing zeroes add no financial meaning in the UI;
  // retain at least the requested display precision without rounding a value.
  const fraction = parts.fraction.replace(/0+$/, '').padEnd(minimumFractionDigits, '0');
  return `${parts.negative && !isBudgetDecimalZero(value) ? '-' : ''}${integer}${fraction ? `${decimalSeparator}${fraction}` : ''}`;
};

/** Formats an exact Budget decimal without ever constructing a JavaScript floating-point number. */
export const formatBudgetAmount = (value: string | null | undefined, currency: string, locale: string) => {
  if (value === null || value === undefined) return '—';
  const decimal = localizedDecimal(value, locale);
  if (decimal === null) return '—';
  const parts = new Intl.NumberFormat(regionalLocale(locale), {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).formatToParts(0);
  let inserted = false;
  return parts
    .filter(part => part.type !== 'group' && part.type !== 'decimal' && part.type !== 'fraction')
    .map(part => {
      if (part.type !== 'integer' || inserted) return part.value;
      inserted = true;
      return decimal;
    })
    .join('');
};

export const formatBudgetPercentage = (value: string | null | undefined, locale: string) => {
  if (value === null || value === undefined) return '—';
  const decimal = localizedDecimal(value, locale);
  return decimal === null ? '—' : `${decimal}%`;
};

/** Displays a configured alert threshold without inventing insignificant decimal places. */
export const formatBudgetThresholdPercentage = (value: string | null | undefined, locale: string) => {
  if (value === null || value === undefined) return '—';
  const decimal = localizedDecimal(value, locale, 0);
  return decimal === null ? '—' : `${decimal}%`;
};

/** The bar is bounded visually, while aria-valuetext and visible copy retain the actual server-calculated percentage. */
export const boundedBudgetProgress = (value: string | null | undefined): number | null => {
  if (!value || !isBudgetDecimal(value)) return null;
  if (isBudgetDecimalNegative(value)) return 0;
  const [integer] = value.split('.');
  const normalizedInteger = integer.replace(/^0+/, '') || '0';
  if (normalizedInteger.length > 3 || (normalizedInteger.length === 3 && normalizedInteger > '100')) return 100;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.min(100, parsed)) : 100;
};

export const exceedsBudgetProgress = (value: string | null | undefined) => {
  if (!value || !isBudgetDecimal(value) || isBudgetDecimalNegative(value)) return false;
  const [integer, fraction = ''] = value.split('.');
  const normalizedInteger = integer.replace(/^0+/, '') || '0';
  return (
    normalizedInteger.length > 3 ||
    (normalizedInteger.length === 3 && (normalizedInteger > '100' || (normalizedInteger === '100' && /[1-9]/.test(fraction))))
  );
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
  if (message.includes('target amount supports at most two decimal places')) {
    return copy(locale, 'El importe objetivo admite como máximo dos decimales.', 'The target amount supports at most two decimal places.');
  }
  if (message.includes('target amount exceeds the supported precision')) {
    return copy(locale, 'El importe objetivo excede la precisión permitida.', 'The target amount exceeds the supported precision.');
  }
  if (message.includes('target amount must be greater than zero')) {
    return copy(locale, 'Escribe un importe objetivo mayor que cero.', 'Enter a target amount greater than zero.');
  }
  if (message.includes('target amount must be a plain decimal')) {
    return copy(locale, 'Escribe un importe decimal válido.', 'Enter a valid decimal amount.');
  }
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
