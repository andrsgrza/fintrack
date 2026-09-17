import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, Card, CardBody, CardFooter, CardHeader, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IFinancialAccountOverview } from 'app/shared/model/financial-account-overview.model';

export const getFinancialAccountOverview = () => axios.get<IFinancialAccountOverview[]>('api/financial-accounts/overview');

const formatMoney = (value: number | null | undefined, currency: string) =>
  value === undefined || value === null ? null : `${value} ${currency}`;

const BalanceItem = ({ labelKey, fallback, value }: { labelKey: string; fallback: string; value: React.ReactNode }) => (
  <div className="mb-2">
    <div className="text-muted small">
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </div>
    <div className="fw-semibold">{value}</div>
  </div>
);

export const FinancialAccount = () => {
  const [accounts, setAccounts] = useState<IFinancialAccountOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const loadOverview = useCallback(() => {
    let active = true;
    setLoading(true);
    setError(false);
    getFinancialAccountOverview()
      .then(response => {
        if (active) {
          setAccounts(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setAccounts([]);
          setError(true);
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
  }, []);

  useEffect(() => loadOverview(), [loadOverview]);

  return (
    <div>
      <h2 id="financial-account-heading" data-cy="FinancialAccountHeading">
        <Translate contentKey="fintrackApp.financialAccount.home.title">Financial Accounts</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={loadOverview} disabled={loading} data-cy="financialAccountRefreshButton">
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.financialAccount.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/financial-account/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialAccount.home.createLabel">Create new Financial Account</Translate>
          </Link>
        </div>
      </h2>
      {loading ? (
        <p data-cy="financialAccountOverviewLoading">
          <Translate contentKey="fintrackApp.financialAccount.overview.loading">Loading accounts...</Translate>
        </p>
      ) : null}
      {error ? (
        <Alert color="danger" fade={false} data-cy="financialAccountOverviewError">
          <Translate contentKey="fintrackApp.financialAccount.overview.loadError">Accounts could not be loaded.</Translate>{' '}
          <Button color="link" className="p-0 align-baseline" onClick={loadOverview}>
            <Translate contentKey="fintrackApp.financialAccount.overview.retry">Try again</Translate>
          </Button>
        </Alert>
      ) : null}
      {!loading && !error && accounts.length === 0 ? (
        <Alert color="info" fade={false} data-cy="financialAccountOverviewEmpty">
          <h3 className="h5">
            <Translate contentKey="fintrackApp.financialAccount.overview.emptyTitle">No accounts yet</Translate>
          </h3>
          <p>
            <Translate contentKey="fintrackApp.financialAccount.overview.emptyDescription">
              Create an account to start tracking your finances.
            </Translate>
          </p>
          <Button tag={Link} to="/financial-account/new" color="primary">
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.financialAccount.home.createLabel">Create a new Financial Account</Translate>
          </Button>
        </Alert>
      ) : null}
      {!loading && !error && accounts.length > 0 ? (
        <Row xs="1" md="2" xl="3" className="g-3" data-cy="financialAccountOverview">
          {accounts.map(account => {
            const isCreditCard = account.accountType === 'CREDIT_CARD';
            return (
              <Col key={account.id} data-cy="financialAccountOverviewCard">
                <Card className="h-100">
                  <CardHeader className="d-flex justify-content-between align-items-start gap-2">
                    <div>
                      <h3 className="h5 mb-1">{account.name}</h3>
                      <div className="text-muted small">
                        <Translate contentKey={`fintrackApp.AccountType.${account.accountType}`} /> · {account.currency}
                      </div>
                    </div>
                    <Badge color={account.active ? 'success' : 'secondary'} pill data-cy="financialAccountStatus">
                      <Translate
                        contentKey={
                          account.active ? 'fintrackApp.financialAccount.status.active' : 'fintrackApp.financialAccount.status.inactive'
                        }
                      >
                        {account.active ? 'Active' : 'Inactive'}
                      </Translate>
                    </Badge>
                  </CardHeader>
                  <CardBody>
                    {account.lastFourDigits ? (
                      <p className="text-muted mb-3" data-cy="financialAccountLastFourDigits">
                        <Translate contentKey="fintrackApp.financialAccount.overview.endsIn">Ends in</Translate> ••••{' '}
                        {account.lastFourDigits}
                      </p>
                    ) : null}
                    {isCreditCard ? (
                      <>
                        <BalanceItem
                          labelKey="fintrackApp.financialAccount.currentDebt"
                          fallback="Current debt"
                          value={formatMoney(account.currentDebt, account.currency)}
                        />
                        {account.missingCreditDetails ? (
                          <Alert color="warning" fade={false} className="mb-0" data-cy="financialAccountMissingCreditDetails">
                            <Translate contentKey="fintrackApp.financialAccount.creditDetailsMissing">
                              Credit card details have not been configured yet.
                            </Translate>
                          </Alert>
                        ) : (
                          <Row>
                            <Col xs="6">
                              <BalanceItem
                                labelKey="fintrackApp.financialAccount.creditLimit"
                                fallback="Credit limit"
                                value={formatMoney(account.creditLimit, account.currency)}
                              />
                            </Col>
                            <Col xs="6">
                              <BalanceItem
                                labelKey="fintrackApp.financialAccount.availableCredit"
                                fallback="Available credit"
                                value={formatMoney(account.availableCredit, account.currency)}
                              />
                            </Col>
                            {account.statementDay ? (
                              <Col xs="6">
                                <BalanceItem
                                  labelKey="fintrackApp.financialAccount.statementDay"
                                  fallback="Statement day"
                                  value={account.statementDay}
                                />
                              </Col>
                            ) : null}
                            {account.paymentDueDay ? (
                              <Col xs="6">
                                <BalanceItem
                                  labelKey="fintrackApp.financialAccount.paymentDueDay"
                                  fallback="Payment due day"
                                  value={account.paymentDueDay}
                                />
                              </Col>
                            ) : null}
                            {account.annualInterestRate !== null && account.annualInterestRate !== undefined ? (
                              <Col xs="12">
                                <BalanceItem
                                  labelKey="fintrackApp.financialAccount.annualInterestRate"
                                  fallback="Annual interest rate"
                                  value={`${account.annualInterestRate}%`}
                                />
                              </Col>
                            ) : null}
                          </Row>
                        )}
                      </>
                    ) : (
                      <BalanceItem
                        labelKey="fintrackApp.financialAccount.currentBalance"
                        fallback="Current balance"
                        value={formatMoney(account.currentBalance, account.currency)}
                      />
                    )}
                  </CardBody>
                  <CardFooter className="bg-transparent border-top-0 pt-0">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/financial-account/${account.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" /> <Translate contentKey="entity.action.view">View</Translate>
                      </Button>
                      <Button tag={Link} to={`/financial-account/${account.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/financial-account/${account.id}/delete`)}
                        color="danger"
                        size="sm"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" /> <Translate contentKey="entity.action.delete">Delete</Translate>
                      </Button>
                    </div>
                  </CardFooter>
                </Card>
              </Col>
            );
          })}
        </Row>
      ) : null}
    </div>
  );
};

export default FinancialAccount;
