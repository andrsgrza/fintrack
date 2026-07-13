import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { IFinancialAccountBalance } from 'app/shared/model/financial-account-balance.model';

interface FinancialAccountBalanceSectionProps {
  accountId?: number;
}

const formatMoney = (value: number | undefined | null, currency: string | undefined | null) =>
  value === undefined || value === null ? '' : `${value} ${currency ?? ''}`.trim();

const BalanceRow = ({ labelKey, fallback, value }: { labelKey: string; fallback: string; value: React.ReactNode }) => (
  <>
    <dt>
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </dt>
    <dd>{value}</dd>
  </>
);

const DateValue = ({ value }: { value?: string }) =>
  value ? <TextFormat value={value} type="date" format={APP_LOCAL_DATE_FORMAT} /> : null;

export const getFinancialAccountBalance = (accountId: string | number) =>
  axios.get<IFinancialAccountBalance>(`api/financial-accounts/${accountId}/balance`);

export const FinancialAccountBalanceSection = ({ accountId }: FinancialAccountBalanceSectionProps) => {
  const [balance, setBalance] = useState<IFinancialAccountBalance | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!accountId) {
      return;
    }

    let active = true;

    setLoading(true);
    setError(false);
    getFinancialAccountBalance(accountId)
      .then(response => {
        if (active) {
          setBalance(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setError(true);
          setBalance(null);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [accountId]);

  return (
    <div data-cy="financialAccountBalanceSection" data-testid="financialAccountBalanceSection">
      <h3>
        <Translate contentKey="fintrackApp.financialAccount.balanceSnapshot">Balance snapshot</Translate>
      </h3>
      {loading ? (
        <p>
          <Translate contentKey="fintrackApp.financialAccount.loadingBalance">Loading balance...</Translate>
        </p>
      ) : null}
      {error ? (
        <p>
          <Translate contentKey="fintrackApp.financialAccount.balanceUnavailable">Balance is not available.</Translate>
        </p>
      ) : null}
      {!loading && !error && balance ? (
        <>
          {balance.accountType === 'CREDIT_CARD' && balance.missingCreditDetails ? (
            <Alert color="warning" fade={false}>
              <Translate contentKey="fintrackApp.financialAccount.creditDetailsMissing">
                Credit card details have not been configured yet.
              </Translate>
            </Alert>
          ) : null}
          <dl className="jh-entity-details">
            {balance.accountType === 'CREDIT_CARD' ? (
              <BalanceRow
                labelKey="fintrackApp.financialAccount.currentDebt"
                fallback="Current debt"
                value={formatMoney(balance.currentDebt, balance.currency)}
              />
            ) : (
              <BalanceRow
                labelKey="fintrackApp.financialAccount.currentBalance"
                fallback="Current balance"
                value={formatMoney(balance.currentBalance, balance.currency)}
              />
            )}
            <BalanceRow
              labelKey="fintrackApp.financialAccount.inflowTotal"
              fallback="Inflow total"
              value={formatMoney(balance.inflowTotal, balance.currency)}
            />
            <BalanceRow
              labelKey="fintrackApp.financialAccount.outflowTotal"
              fallback="Outflow total"
              value={formatMoney(balance.outflowTotal, balance.currency)}
            />
            <BalanceRow
              labelKey="fintrackApp.financialAccount.initialBalance"
              fallback="Initial balance"
              value={formatMoney(balance.initialBalance, balance.currency)}
            />
            <BalanceRow
              labelKey="fintrackApp.financialAccount.initialBalanceDate"
              fallback="Initial balance date"
              value={<DateValue value={balance.initialBalanceDate} />}
            />
            <BalanceRow
              labelKey="fintrackApp.financialAccount.asOfDate"
              fallback="As of date"
              value={<DateValue value={balance.asOfDate} />}
            />
            {balance.accountType === 'CREDIT_CARD' && balance.creditLimit !== undefined && balance.creditLimit !== null ? (
              <BalanceRow
                labelKey="fintrackApp.financialAccount.creditLimit"
                fallback="Credit limit"
                value={formatMoney(balance.creditLimit, balance.currency)}
              />
            ) : null}
            {balance.accountType === 'CREDIT_CARD' && balance.availableCredit !== undefined && balance.availableCredit !== null ? (
              <BalanceRow
                labelKey="fintrackApp.financialAccount.availableCredit"
                fallback="Available credit"
                value={formatMoney(balance.availableCredit, balance.currency)}
              />
            ) : null}
          </dl>
        </>
      ) : null}
    </div>
  );
};

export default FinancialAccountBalanceSection;
