import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Badge, Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './financial-account.reducer';
import { getInitialBalanceLabelKey } from './financial-account-labels';
import {
  getEntityByAccountId as getCreditAccountDetailsByAccountId,
  reset as resetCreditAccountDetails,
} from 'app/entities/credit-account-details/credit-account-details.reducer';
import CreditCardDetailsViewSection from './components/credit-card-details-view-section';
import FinancialAccountBalanceSection from './components/financial-account-balance-section';
import AccountRecentTransactionsSection from './components/account-recent-transactions-section';

export const FinancialAccountDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
    dispatch(resetCreditAccountDetails());
  }, []);

  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  const creditAccountDetailsEntity = useAppSelector(state => state.creditAccountDetails.entity);
  const isInactive = financialAccountEntity.active === false;

  useEffect(() => {
    if (financialAccountEntity.id && financialAccountEntity.accountType === 'CREDIT_CARD') {
      dispatch(getCreditAccountDetailsByAccountId(financialAccountEntity.id));
    }
  }, [financialAccountEntity.id, financialAccountEntity.accountType]);

  return (
    <Row>
      <Col md="8">
        <h2 data-cy="financialAccountDetailsHeading">
          <Translate contentKey="fintrackApp.financialAccount.detail.title">Account details</Translate>
        </h2>
        <section data-cy="financialAccountDetailAccountSection" data-testid="financialAccountDetailAccountSection" className="mb-4">
          <h3>
            <Translate contentKey="fintrackApp.financialAccount.detail.account">Account</Translate>
          </h3>
          <dl className="jh-entity-details mb-0">
            <dt>
              <span id="name">
                <Translate contentKey="fintrackApp.financialAccount.name">Name</Translate>
              </span>
            </dt>
            <dd>{financialAccountEntity.name}</dd>
            {financialAccountEntity.institutionName ? (
              <>
                <dt>
                  <span id="institutionName">
                    <Translate contentKey="fintrackApp.financialAccount.institutionName">Institution Name</Translate>
                  </span>
                </dt>
                <dd>{financialAccountEntity.institutionName}</dd>
              </>
            ) : null}
            <dt>
              <span id="accountType">
                <Translate contentKey="fintrackApp.financialAccount.accountType">Account Type</Translate>
              </span>
            </dt>
            <dd>
              {financialAccountEntity.accountType ? (
                <Translate contentKey={`fintrackApp.AccountType.${financialAccountEntity.accountType}`} />
              ) : null}
            </dd>
            <dt>
              <span id="currency">
                <Translate contentKey="fintrackApp.financialAccount.currency">Currency</Translate>
              </span>
            </dt>
            <dd>{financialAccountEntity.currency}</dd>
            <dt>
              <span id="initialBalance">
                <Translate contentKey={getInitialBalanceLabelKey(financialAccountEntity.accountType)}>Opening position</Translate>
              </span>
            </dt>
            <dd>{financialAccountEntity.initialBalance}</dd>
            <dt>
              <span id="initialBalanceDate">
                <Translate contentKey="fintrackApp.financialAccount.initialBalanceDate">Initial Balance Date</Translate>
              </span>
            </dt>
            <dd>
              {financialAccountEntity.initialBalanceDate ? (
                <TextFormat value={financialAccountEntity.initialBalanceDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
              ) : null}
            </dd>
            {financialAccountEntity.lastFourDigits ? (
              <>
                <dt>
                  <span id="lastFourDigits">
                    <Translate contentKey="fintrackApp.financialAccount.lastFourDigits">Last Four Digits</Translate>
                  </span>
                </dt>
                <dd>{`••••${financialAccountEntity.lastFourDigits}`}</dd>
              </>
            ) : null}
            {financialAccountEntity.description ? (
              <>
                <dt>
                  <span id="description">
                    <Translate contentKey="fintrackApp.financialAccount.description">Description</Translate>
                  </span>
                </dt>
                <dd>{financialAccountEntity.description}</dd>
              </>
            ) : null}
          </dl>
        </section>
        <section data-cy="financialAccountDetailStatusSection" data-testid="financialAccountDetailStatusSection" className="mb-4">
          <h3>
            <Translate contentKey="fintrackApp.financialAccount.detail.status">Status</Translate>
          </h3>
          {financialAccountEntity.id ? (
            <Badge color={isInactive ? 'secondary' : 'success'} pill data-cy="financialAccountStatus" data-testid="financialAccountStatus">
              <Translate
                contentKey={isInactive ? 'fintrackApp.financialAccount.status.inactive' : 'fintrackApp.financialAccount.status.active'}
              >
                {isInactive ? 'Inactive' : 'Active'}
              </Translate>
            </Badge>
          ) : null}
          {isInactive ? (
            <Alert
              color="secondary"
              fade={false}
              className="mt-3 mb-0"
              data-cy="financialAccountInactiveExplanation"
              data-testid="financialAccountInactiveExplanation"
            >
              <Translate contentKey="fintrackApp.financialAccount.status.inactiveExplanation">
                This account is inactive. Its history and balance remain available, and existing work can finish, but it cannot be selected
                for new transactions, imports, or rules until it is reactivated.
              </Translate>
            </Alert>
          ) : null}
        </section>
        {financialAccountEntity.accountType === 'CREDIT_CARD' ? (
          <div className="mt-3 mb-3">
            <CreditCardDetailsViewSection
              details={creditAccountDetailsEntity}
              accountId={financialAccountEntity.id}
              currency={financialAccountEntity.currency}
            />
          </div>
        ) : null}
        {financialAccountEntity.id ? (
          <div className="mt-3 mb-3">
            <FinancialAccountBalanceSection accountId={financialAccountEntity.id} />
          </div>
        ) : null}
        {financialAccountEntity.id ? (
          <div className="mt-3 mb-3">
            <AccountRecentTransactionsSection accountId={financialAccountEntity.id} currency={financialAccountEntity.currency} />
          </div>
        ) : null}
        <Button tag={Link} to="/financial-account" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/financial-account/${financialAccountEntity.id}/edit`} replace color="primary" data-cy="entityEditButton">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default FinancialAccountDetail;
