import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';

interface AccountRecentTransactionsSectionProps {
  accountId?: number;
  currency?: string;
}

const formatMoney = (value: number | undefined | null, currency: string | undefined | null) =>
  value === undefined || value === null ? '' : `${value} ${currency ?? ''}`.trim();

export const getRecentTransactionsForAccount = (accountId: string | number) =>
  axios.get<IFinancialTransaction[]>(
    `api/financial-transactions?accountId.equals=${accountId}&sort=transactionDate,desc&sort=id,desc&size=5`,
  );

export const AccountRecentTransactionsSection = ({ accountId, currency }: AccountRecentTransactionsSectionProps) => {
  const [transactions, setTransactions] = useState<IFinancialTransaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!accountId) {
      return;
    }

    let active = true;

    setLoading(true);
    setError(false);
    getRecentTransactionsForAccount(accountId)
      .then(response => {
        if (active) {
          setTransactions(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setError(true);
          setTransactions([]);
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
    <div data-cy="accountRecentTransactionsSection" data-testid="accountRecentTransactionsSection">
      <h3>
        <Translate contentKey="fintrackApp.financialAccount.recentTransactions">Recent transactions</Translate>
      </h3>
      {loading ? (
        <p>
          <Translate contentKey="fintrackApp.financialAccount.loadingTransactions">Loading transactions...</Translate>
        </p>
      ) : null}
      {error ? (
        <p>
          <Translate contentKey="fintrackApp.financialAccount.transactionsUnavailable">Transactions are not available.</Translate>
        </p>
      ) : null}
      {!loading && !error && transactions.length === 0 ? (
        <p>
          <Translate contentKey="fintrackApp.financialAccount.noTransactionsYet">No transactions yet.</Translate>
        </p>
      ) : null}
      {!loading && !error && transactions.length > 0 ? (
        <Table responsive size="sm">
          <thead>
            <tr>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction Date</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.flow">Flow</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.amount">Amount</Translate>
              </th>
            </tr>
          </thead>
          <tbody>
            {transactions.map(transaction => (
              <tr key={transaction.id}>
                <td>
                  {transaction.transactionDate ? (
                    <TextFormat value={transaction.transactionDate as unknown as string} type="date" format={APP_LOCAL_DATE_FORMAT} />
                  ) : null}
                </td>
                <td>{transaction.description}</td>
                <td>
                  <Translate contentKey={`fintrackApp.TransactionFlow.${transaction.flow}`} />
                </td>
                <td>{formatMoney(transaction.amount, currency)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      ) : null}
      <Button tag={Link} to="/financial-transaction" color="link" size="sm" className="p-0">
        <FontAwesomeIcon icon="list" />{' '}
        <Translate contentKey="fintrackApp.financialAccount.viewAllTransactions">View all transactions</Translate>
      </Button>
    </div>
  );
};

export default AccountRecentTransactionsSection;
