import React from 'react';

type FinancialFlow = string | null | undefined;

export const formatFinancialAmount = (amount?: number | null, currency?: string | null): string | null => {
  if (amount === undefined || amount === null || Number.isNaN(amount)) return null;
  const formatted = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(amount));
  return `${formatted}${currency ? ` ${currency}` : ''}`;
};

/** Shared visual treatment for signed financial values across transaction surfaces. */
export const FinancialAmount = ({
  amount,
  currency,
  flow,
  compact = false,
  dataCy = 'financialAmount',
}: {
  amount?: number | null;
  currency?: string | null;
  flow?: FinancialFlow;
  compact?: boolean;
  dataCy?: string;
}) => {
  const formatted = formatFinancialAmount(amount, currency);
  if (!formatted) {
    return (
      <span className="text-muted small" data-cy={`${dataCy}Missing`}>
        —
      </span>
    );
  }

  const tone = flow === 'IN' ? 'income' : flow === 'OUT' ? 'expense' : 'neutral';
  const sign = tone === 'income' ? '+' : tone === 'expense' ? '−' : '';
  return (
    <span className={`financial-amount financial-amount--${tone}${compact ? ' financial-amount--compact' : ''}`} data-cy={dataCy}>
      <span className="visually-hidden">{tone === 'income' ? 'Ingreso' : tone === 'expense' ? 'Gasto' : 'Monto'}: </span>
      {sign}
      {formatted}
    </span>
  );
};
