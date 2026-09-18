import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Badge, Button, Card, CardBody, CardHeader, Col, DropdownItem, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IFinancialAccountOverview } from 'app/shared/model/financial-account-overview.model';
import { isHexColor } from 'app/shared/ui/hex-color-control';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

export const getFinancialAccountOverview = () => axios.get<IFinancialAccountOverview[]>('api/financial-accounts/overview');

const formatMoney = (value: number | null | undefined, currency: string) =>
  value === undefined || value === null ? '—' : `${value} ${currency}`;

const AccountMetric = ({
  labelKey,
  fallback,
  value,
  prominent = false,
}: {
  labelKey: string;
  fallback: string;
  value: React.ReactNode;
  prominent?: boolean;
}) => (
  <div className={prominent ? 'mb-3' : 'mb-2'}>
    <div className="text-muted small">
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </div>
    <div className={prominent ? 'fs-4 fw-semibold' : 'fw-semibold'}>{value}</div>
  </div>
);

export const FinancialAccount = () => {
  const [accounts, setAccounts] = useState<IFinancialAccountOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const navigate = useNavigate();

  const loadOverview = useCallback(() => {
    let active = true;
    setLoading(true);
    setError(false);
    getFinancialAccountOverview()
      .then(response => {
        if (active) setAccounts(response.data);
      })
      .catch(() => {
        if (active) {
          setAccounts([]);
          setError(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => loadOverview(), [loadOverview]);

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="financial-account-heading"
        dataCy="FinancialAccountHeading"
        title={<Translate contentKey="fintrackApp.financialAccount.home.title">Financial Accounts</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.financialAccount.overview.productSubtitle">
            Track balances, available credit, and recent account activity.
          </Translate>
        }
        actions={
          <>
            <Button color="secondary" outline size="sm" onClick={loadOverview} disabled={loading} data-cy="financialAccountRefreshButton">
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.financialAccount.home.refreshListLabel">Refresh List</Translate>
            </Button>
            <Button tag={Link} to="/financial-account/new" color="primary" size="sm" id="jh-create-entity" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" />{' '}
              <Translate contentKey="fintrackApp.financialAccount.home.createLabel">Create a new Financial Account</Translate>
            </Button>
          </>
        }
      />

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
        <ProductSection
          title={<Translate contentKey="fintrackApp.financialAccount.home.title">Financial Accounts</Translate>}
          dataCy="financialAccountOverviewEmpty"
        >
          <h3 className="h5">
            <Translate contentKey="fintrackApp.financialAccount.overview.emptyTitle">No accounts yet</Translate>
          </h3>
          <p className="text-muted">
            <Translate contentKey="fintrackApp.financialAccount.overview.emptyDescription">
              Create an account to start tracking your finances.
            </Translate>
          </p>
          <Button tag={Link} to="/financial-account/new" color="primary" size="sm">
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.financialAccount.home.createLabel">Create a new Financial Account</Translate>
          </Button>
        </ProductSection>
      ) : null}
      {!loading && !error && accounts.length > 0 ? (
        <Row xs="1" md="2" xl="3" className="g-3" data-cy="financialAccountOverview">
          {accounts.map(account => {
            const isCreditCard = account.accountType === 'CREDIT_CARD';
            const accentColor = isHexColor(account.color) ? account.color : undefined;
            const openDetail = () => navigate(`/financial-account/${account.id}`);
            const preventCardNavigation = (event: React.SyntheticEvent) => event.stopPropagation();

            return (
              <Col key={account.id} data-cy="financialAccountOverviewCardColumn">
                <Card
                  className="h-100 shadow-sm cursor-pointer"
                  data-cy="financialAccountOverviewCard"
                  data-testid="financialAccountOverviewCard"
                  data-account-id={account.id}
                  role="link"
                  tabIndex={0}
                  onClick={openDetail}
                  onKeyDown={event => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      openDetail();
                    }
                  }}
                  style={accentColor ? { borderInlineStart: `0.35rem solid ${accentColor}` } : undefined}
                >
                  <CardHeader className="bg-transparent d-flex justify-content-between align-items-start gap-2">
                    <div className="min-w-0">
                      <Link
                        to={`/financial-account/${account.id}`}
                        className="h5 d-inline-block mb-1 text-decoration-none text-body"
                        data-cy="entityDetailsLink"
                        onClick={preventCardNavigation}
                      >
                        {account.name}
                      </Link>
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
                  <CardBody className="d-flex flex-column">
                    {account.lastFourDigits ? (
                      <p className="text-muted small mb-3" data-cy="financialAccountLastFourDigits">
                        <Translate contentKey="fintrackApp.financialAccount.overview.endsIn">Ends in</Translate> ••••
                        {account.lastFourDigits}
                      </p>
                    ) : null}
                    {isCreditCard ? (
                      <>
                        <Row className="g-3">
                          <Col xs="6">
                            <AccountMetric
                              prominent
                              labelKey="fintrackApp.financialAccount.currentDebt"
                              fallback="Current debt"
                              value={formatMoney(account.currentDebt, account.currency)}
                            />
                          </Col>
                          <Col xs="6">
                            <AccountMetric
                              prominent
                              labelKey="fintrackApp.financialAccount.availableCredit"
                              fallback="Available credit"
                              value={formatMoney(account.availableCredit, account.currency)}
                            />
                          </Col>
                        </Row>
                        {account.missingCreditDetails ? (
                          <Alert color="warning" fade={false} className="small mt-2 mb-0" data-cy="financialAccountMissingCreditDetails">
                            <Translate contentKey="fintrackApp.financialAccount.creditDetailsMissing">
                              Credit card details have not been configured yet.
                            </Translate>
                          </Alert>
                        ) : (
                          <Row className="g-2 mt-0">
                            <Col xs="6">
                              <AccountMetric
                                labelKey="fintrackApp.financialAccount.creditLimit"
                                fallback="Credit limit"
                                value={formatMoney(account.creditLimit, account.currency)}
                              />
                            </Col>
                            {account.statementDay ? (
                              <Col xs="6">
                                <AccountMetric
                                  labelKey="fintrackApp.financialAccount.statementDay"
                                  fallback="Statement closing day"
                                  value={account.statementDay}
                                />
                              </Col>
                            ) : null}
                            {account.paymentDueDay ? (
                              <Col xs="6">
                                <AccountMetric
                                  labelKey="fintrackApp.financialAccount.paymentDueDay"
                                  fallback="Payment due day"
                                  value={account.paymentDueDay}
                                />
                              </Col>
                            ) : null}
                            {account.annualInterestRate !== null && account.annualInterestRate !== undefined ? (
                              <Col xs="6">
                                <AccountMetric
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
                      <AccountMetric
                        prominent
                        labelKey="fintrackApp.financialAccount.currentBalance"
                        fallback="Current balance"
                        value={formatMoney(account.currentBalance, account.currency)}
                      />
                    )}
                    <div className="mt-auto pt-2 d-flex justify-content-end align-items-center gap-1" onClick={preventCardNavigation}>
                      <Button
                        tag={Link}
                        to={`/financial-account/${account.id}/edit`}
                        color="primary"
                        outline
                        size="sm"
                        data-cy="entityEditButton"
                        aria-label={translate('entity.action.edit')}
                        title={translate('entity.action.edit')}
                      >
                        <FontAwesomeIcon icon="pencil-alt" />
                        <span className="visually-hidden">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <ProductActionsMenu
                        label={translate('fintrackApp.financialAccount.moreActions')}
                        dataCy="financialAccountActionsMenu"
                      >
                        <DropdownItem
                          tag={Link}
                          to={`/financial-account/${account.id}/delete`}
                          className="text-danger"
                          data-cy="entityDeleteButton"
                        >
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </DropdownItem>
                      </ProductActionsMenu>
                    </div>
                  </CardBody>
                </Card>
              </Col>
            );
          })}
        </Row>
      ) : null}
    </ProductPage>
  );
};

export default FinancialAccount;
