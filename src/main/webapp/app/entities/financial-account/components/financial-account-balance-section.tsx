import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { IFinancialAccountBalance } from 'app/shared/model/financial-account-balance.model';

interface FinancialAccountBalanceSectionProps {
  accountId?: number;
}

const formatMoney = (value: number | undefined | null, currency: string | undefined | null) =>
  value === undefined || value === null ? '—' : `${value} ${currency ?? ''}`.trim();

const Metric = ({
  labelKey,
  fallback,
  value,
  primary = false,
}: {
  labelKey: string;
  fallback: string;
  value: React.ReactNode;
  primary?: boolean;
}) => (
  <div className={`border rounded-3 p-3 h-100 ${primary ? 'bg-body-tertiary' : ''}`}>
    <div className="text-muted small mb-1">
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </div>
    <div className={primary ? 'fs-3 fw-semibold' : 'fw-semibold'}>{value}</div>
  </div>
);

const DateValue = ({ value }: { value?: string }) =>
  value ? <TextFormat value={value} type="date" format={APP_LOCAL_DATE_FORMAT} /> : <>—</>;

export const getFinancialAccountBalance = (accountId: string | number) =>
  axios.get<IFinancialAccountBalance>(`api/financial-accounts/${accountId}/balance`);

export const FinancialAccountBalanceSection = ({ accountId }: FinancialAccountBalanceSectionProps) => {
  const [balance, setBalance] = useState<IFinancialAccountBalance | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!accountId) return;

    let active = true;
    setLoading(true);
    setError(false);
    getFinancialAccountBalance(accountId)
      .then(response => {
        if (active) setBalance(response.data);
      })
      .catch(() => {
        if (active) {
          setError(true);
          setBalance(null);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [accountId]);

  return (
    <div data-cy="financialAccountBalanceSection" data-testid="financialAccountBalanceSection">
      {loading ? (
        <p className="mb-0">
          <Translate contentKey="fintrackApp.financialAccount.loadingBalance">Loading balance...</Translate>
        </p>
      ) : null}
      {error ? (
        <p className="mb-0">
          <Translate contentKey="fintrackApp.financialAccount.balanceUnavailable">Balance is not available.</Translate>
        </p>
      ) : null}
      {!loading && !error && balance ? (
        <>
          {balance.accountType === 'CREDIT_CARD' && balance.missingCreditDetails ? (
            <Alert color="warning" fade={false} className="small mb-3">
              <Translate contentKey="fintrackApp.financialAccount.creditDetailsMissing">
                Credit card details have not been configured yet.
              </Translate>
            </Alert>
          ) : null}
          {balance.accountType === 'CREDIT_CARD' ? (
            <Row className="g-3" data-cy="financialAccountCreditMetrics" data-testid="financialAccountCreditMetrics">
              <Col sm="6">
                <Metric
                  primary
                  labelKey="fintrackApp.financialAccount.currentDebt"
                  fallback="Current debt"
                  value={formatMoney(balance.currentDebt, balance.currency)}
                />
              </Col>
              <Col sm="6">
                <Metric
                  primary
                  labelKey="fintrackApp.financialAccount.availableCredit"
                  fallback="Available credit"
                  value={formatMoney(balance.availableCredit, balance.currency)}
                />
              </Col>
              <Col sm="6" lg="4">
                <Metric
                  labelKey="fintrackApp.financialAccount.creditLimit"
                  fallback="Credit limit"
                  value={formatMoney(balance.creditLimit, balance.currency)}
                />
              </Col>
              <Col sm="6" lg="4">
                <Metric
                  labelKey="fintrackApp.financialAccount.initialBalance"
                  fallback="Opening position"
                  value={formatMoney(balance.initialBalance, balance.currency)}
                />
              </Col>
              <Col sm="6" lg="4">
                <Metric
                  labelKey="fintrackApp.financialAccount.initialBalanceDate"
                  fallback="Tracking start date"
                  value={<DateValue value={balance.initialBalanceDate} />}
                />
              </Col>
            </Row>
          ) : (
            <Row className="g-3" data-cy="financialAccountDebitMetrics" data-testid="financialAccountDebitMetrics">
              <Col lg="5">
                <Metric
                  primary
                  labelKey="fintrackApp.financialAccount.currentBalance"
                  fallback="Current balance"
                  value={formatMoney(balance.currentBalance, balance.currency)}
                />
              </Col>
              <Col sm="6" lg="7">
                <Row className="g-3">
                  <Col sm="6">
                    <Metric
                      labelKey="fintrackApp.financialAccount.initialBalance"
                      fallback="Opening position"
                      value={formatMoney(balance.initialBalance, balance.currency)}
                    />
                  </Col>
                  <Col sm="6">
                    <Metric
                      labelKey="fintrackApp.financialAccount.initialBalanceDate"
                      fallback="Tracking start date"
                      value={<DateValue value={balance.initialBalanceDate} />}
                    />
                  </Col>
                  <Col sm="6">
                    <Metric
                      labelKey="fintrackApp.financialAccount.inflowTotal"
                      fallback="Inflow total"
                      value={formatMoney(balance.inflowTotal, balance.currency)}
                    />
                  </Col>
                  <Col sm="6">
                    <Metric
                      labelKey="fintrackApp.financialAccount.outflowTotal"
                      fallback="Outflow total"
                      value={formatMoney(balance.outflowTotal, balance.currency)}
                    />
                  </Col>
                </Row>
              </Col>
            </Row>
          )}
        </>
      ) : null}
    </div>
  );
};

export default FinancialAccountBalanceSection;
