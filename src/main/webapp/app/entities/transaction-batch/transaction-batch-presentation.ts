/**
 * Presentation helpers for the temporary batch workspace. Persistence continues
 * to use a signed amount; product input is a positive magnitude plus a flow.
 */
export const formatBatchAmount = (signedAmount?: number | null, currency = 'MXN'): string => {
  if (signedAmount === null || signedAmount === undefined || Number.isNaN(signedAmount)) return 'Pendiente';

  const formatted = new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(signedAmount));
  return signedAmount < 0 ? `-${formatted}` : formatted;
};

export const amountMagnitude = (signedAmount?: number | null): string =>
  signedAmount === null || signedAmount === undefined ? '' : Math.abs(signedAmount).toFixed(2);

export const signedAmountForFlow = (magnitude: string, flow: 'IN' | 'OUT'): number | null => {
  const numeric = Number(magnitude);
  if (!Number.isFinite(numeric) || numeric <= 0) return null;
  return flow === 'OUT' ? -numeric : numeric;
};
