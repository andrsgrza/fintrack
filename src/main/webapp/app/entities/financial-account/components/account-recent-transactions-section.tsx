import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';
import { TransactionAmount, TransactionClassification } from 'app/entities/financial-transaction/transaction-presentation';

interface AccountRecentTransactionsSectionProps {
  accountId?: number;
  currency?: string;
}

export const getRecentTransactionsForAccount = (accountId: string | number) =>
  axios.get<IFinancialTransaction[]>(
    `api/financial-transactions?accountId.equals=${accountId}&sort=transactionDate,desc&sort=id,desc&size=5`,
  );

export const getAccountTransactionsListUrl = (accountId: string | number) => {
  const params = new URLSearchParams();
  params.set('accountId.equals', String(accountId));
  params.set('page', '1');
  params.set('sort', 'transactionDate,desc');
  return `/financial-transaction?${params.toString()}`;
};

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
      {loading ? (
        <p className="mb-0">
          <Translate contentKey="fintrackApp.financialAccount.loadingTransactions">Loading transactions...</Translate>
        </p>
      ) : null}
      {error ? (
        <p className="mb-0">
          <Translate contentKey="fintrackApp.financialAccount.transactionsUnavailable">Transactions are not available.</Translate>
        </p>
      ) : null}
      {!loading && !error && transactions.length === 0 ? (
        <p className="text-muted mb-0">
          <Translate contentKey="fintrackApp.financialAccount.noTransactionsYet">No transactions yet.</Translate>
        </p>
      ) : null}
      {!loading && !error && transactions.length > 0 ? (
        <Table responsive size="sm" className="mb-3 align-middle">
          <thead>
            <tr>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction Date</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.financialTransaction.product.classification">Classification</Translate>
              </th>
              <th className="text-end">
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
                <td>
                  {transaction.id ? (
                    <Link
                      to={`/financial-transaction/${transaction.id}`}
                      className="account-recent-transaction-link"
                      data-cy="accountRecentTransactionLink"
                    >
                      {transaction.description}
                    </Link>
                  ) : (
                    transaction.description
                  )}
                </td>
                <td>
                  <TransactionClassification category={transaction.category} tags={transaction.tags} />
                </td>
                <td>
                  <TransactionAmount amount={transaction.amount} currency={currency} flow={transaction.flow} compact />
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      ) : null}
      <Button
        tag={Link}
        to={accountId ? getAccountTransactionsListUrl(accountId) : '/financial-transaction'}
        color="link"
        size="sm"
        className="p-0 text-decoration-none"
        data-cy="accountViewAllTransactions"
      >
        <FontAwesomeIcon icon="list" />{' '}
        <Translate contentKey="fintrackApp.financialAccount.viewAllTransactions">View all transactions</Translate>
      </Button>
    </div>
  );
};

export default AccountRecentTransactionsSection;
