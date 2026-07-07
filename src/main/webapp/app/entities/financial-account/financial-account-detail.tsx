import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './financial-account.reducer';

export const FinancialAccountDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="financialAccountDetailsHeading">
          <Translate contentKey="fintrackApp.financialAccount.detail.title">FinancialAccount</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.financialAccount.name">Name</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.name}</dd>
          <dt>
            <span id="institutionName">
              <Translate contentKey="fintrackApp.financialAccount.institutionName">Institution Name</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.institutionName}</dd>
          <dt>
            <span id="accountType">
              <Translate contentKey="fintrackApp.financialAccount.accountType">Account Type</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.accountType}</dd>
          <dt>
            <span id="currency">
              <Translate contentKey="fintrackApp.financialAccount.currency">Currency</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.currency}</dd>
          <dt>
            <span id="initialBalance">
              <Translate contentKey="fintrackApp.financialAccount.initialBalance">Initial Balance</Translate>
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
          <dt>
            <span id="lastFourDigits">
              <Translate contentKey="fintrackApp.financialAccount.lastFourDigits">Last Four Digits</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.lastFourDigits}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.financialAccount.description">Description</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.description}</dd>
          <dt>
            <span id="color">
              <Translate contentKey="fintrackApp.financialAccount.color">Color</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.color}</dd>
          <dt>
            <span id="icon">
              <Translate contentKey="fintrackApp.financialAccount.icon">Icon</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.icon}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.financialAccount.active">Active</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.active ? 'true' : 'false'}</dd>
          <dt>
            <span id="includeInNetWorth">
              <Translate contentKey="fintrackApp.financialAccount.includeInNetWorth">Include In Net Worth</Translate>
            </span>
          </dt>
          <dd>{financialAccountEntity.includeInNetWorth ? 'true' : 'false'}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.financialAccount.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {financialAccountEntity.createdAt ? (
              <TextFormat value={financialAccountEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.financialAccount.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {financialAccountEntity.updatedAt ? (
              <TextFormat value={financialAccountEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.financialAccount.user">User</Translate>
          </dt>
          <dd>{financialAccountEntity.user ? financialAccountEntity.user.login : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialAccount.budgets">Budgets</Translate>
          </dt>
          <dd>
            {financialAccountEntity.budgets
              ? financialAccountEntity.budgets.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {financialAccountEntity.budgets && i === financialAccountEntity.budgets.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.financialAccount.transactionIngestions">Transaction Ingestions</Translate>
          </dt>
          <dd>
            {financialAccountEntity.transactionIngestions
              ? financialAccountEntity.transactionIngestions.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {financialAccountEntity.transactionIngestions && i === financialAccountEntity.transactionIngestions.length - 1
                      ? ''
                      : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.financialAccount.apiAccessTokens">Api Access Tokens</Translate>
          </dt>
          <dd>
            {financialAccountEntity.apiAccessTokens
              ? financialAccountEntity.apiAccessTokens.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {financialAccountEntity.apiAccessTokens && i === financialAccountEntity.apiAccessTokens.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/financial-account" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/financial-account/${financialAccountEntity.id}/edit`} replace color="primary">
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
