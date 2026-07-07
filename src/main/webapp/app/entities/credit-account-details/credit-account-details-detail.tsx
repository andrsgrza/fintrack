import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './credit-account-details.reducer';

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
        <h2 data-cy="creditAccountDetailsDetailsHeading">
          <Translate contentKey="fintrackApp.creditAccountDetails.detail.title">CreditAccountDetails</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{creditAccountDetailsEntity.id}</dd>
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
            <span id="createdAt">
              <Translate contentKey="fintrackApp.creditAccountDetails.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {creditAccountDetailsEntity.createdAt ? (
              <TextFormat value={creditAccountDetailsEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.creditAccountDetails.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {creditAccountDetailsEntity.updatedAt ? (
              <TextFormat value={creditAccountDetailsEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.creditAccountDetails.account">Account</Translate>
          </dt>
          <dd>{creditAccountDetailsEntity.account ? creditAccountDetailsEntity.account.name : ''}</dd>
        </dl>
        <Button tag={Link} to="/credit-account-details" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/credit-account-details/${creditAccountDetailsEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default CreditAccountDetailsDetail;
