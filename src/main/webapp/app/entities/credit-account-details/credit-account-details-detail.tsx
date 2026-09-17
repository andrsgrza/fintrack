import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './credit-account-details.reducer';
import CreditAccountDetailsTechnicalNotice from './credit-account-details-technical-notice';

export const CreditAccountDetailsDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const creditAccountDetailsEntity = useAppSelector(state => state.creditAccountDetails.entity);
  return (
    <Row>
      <Col md="8">
        <CreditAccountDetailsTechnicalNotice />
        <h2 data-cy="creditAccountDetailsDetailsHeading">
          <Translate contentKey="fintrackApp.creditAccountDetails.detail.title">Technical credit card details</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="creditLimit">
              <Translate contentKey="fintrackApp.creditAccountDetails.creditLimit">Credit Limit</Translate>
            </span>
          </dt>
          <dd>{creditAccountDetailsEntity.creditLimit}</dd>
          <dt>
            <span id="statementDay">
              <Translate contentKey="fintrackApp.creditAccountDetails.statementDay">Statement Day</Translate>
            </span>
          </dt>
          <dd>{creditAccountDetailsEntity.statementDay}</dd>
          <dt>
            <span id="paymentDueDay">
              <Translate contentKey="fintrackApp.creditAccountDetails.paymentDueDay">Payment Due Day</Translate>
            </span>
          </dt>
          <dd>{creditAccountDetailsEntity.paymentDueDay}</dd>
          <dt>
            <span id="annualInterestRate">
              <Translate contentKey="fintrackApp.creditAccountDetails.annualInterestRate">Annual Interest Rate</Translate>
            </span>
          </dt>
          <dd>{creditAccountDetailsEntity.annualInterestRate}</dd>
          <dt>
            <Translate contentKey="fintrackApp.creditAccountDetails.account">Account</Translate>
          </dt>
          <dd>
            {creditAccountDetailsEntity.account ? (
              <Link to={`/financial-account/${creditAccountDetailsEntity.account.id}`}>{creditAccountDetailsEntity.account.name}</Link>
            ) : null}
          </dd>
        </dl>
        <Button tag={Link} to="/financial-account" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="fintrackApp.creditAccountDetails.technical.backToAccounts">Back to accounts</Translate>
          </span>
        </Button>
        {creditAccountDetailsEntity.account?.id ? (
          <>
            &nbsp;
            <Button
              tag={Link}
              to={`/financial-account/${creditAccountDetailsEntity.account.id}/edit`}
              replace
              color="primary"
              data-cy="creditAccountDetailsManageParentAccountButton"
              data-testid="creditAccountDetailsManageParentAccountButton"
            >
              <FontAwesomeIcon icon="pencil-alt" />{' '}
              <span className="d-none d-md-inline">
                <Translate contentKey="fintrackApp.creditAccountDetails.technical.manageParentAccount">Edit parent account</Translate>
              </span>
            </Button>
          </>
        ) : null}
      </Col>
    </Row>
  );
};

export default CreditAccountDetailsDetail;
