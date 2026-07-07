import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './financial-transaction.reducer';

export const FinancialTransactionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="financialTransactionDetailsHeading">
          <Translate contentKey="fintrackApp.financialTransaction.detail.title">FinancialTransaction</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.id}</dd>
          <dt>
            <span id="transactionDate">
              <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction Date</Translate>
            </span>
          </dt>
          <dd>
            {financialTransactionEntity.transactionDate ? (
              <TextFormat value={financialTransactionEntity.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="postingDate">
              <Translate contentKey="fintrackApp.financialTransaction.postingDate">Posting Date</Translate>
            </span>
          </dt>
          <dd>
            {financialTransactionEntity.postingDate ? (
              <TextFormat value={financialTransactionEntity.postingDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.description}</dd>
          <dt>
            <span id="amount">
              <Translate contentKey="fintrackApp.financialTransaction.amount">Amount</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.amount}</dd>
          <dt>
            <span id="flow">
              <Translate contentKey="fintrackApp.financialTransaction.flow">Flow</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.flow}</dd>
          <dt>
            <span id="origin">
              <Translate contentKey="fintrackApp.financialTransaction.origin">Origin</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.origin}</dd>
          <dt>
            <span id="externalReference">
              <Translate contentKey="fintrackApp.financialTransaction.externalReference">External Reference</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.externalReference}</dd>
          <dt>
            <span id="notes">
              <Translate contentKey="fintrackApp.financialTransaction.notes">Notes</Translate>
            </span>
          </dt>
          <dd>{financialTransactionEntity.notes}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.financialTransaction.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {financialTransactionEntity.createdAt ? (
              <TextFormat value={financialTransactionEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.financialTransaction.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {financialTransactionEntity.updatedAt ? (
              <TextFormat value={financialTransactionEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>
          </dt>
          <dd>{financialTransactionEntity.account ? financialTransactionEntity.account.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate>
          </dt>
          <dd>{financialTransactionEntity.category ? financialTransactionEntity.category.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialTransaction.financialSubscription">Financial Subscription</Translate>
          </dt>
          <dd>{financialTransactionEntity.financialSubscription ? financialTransactionEntity.financialSubscription.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialTransaction.transactionIngestion">Transaction Ingestion</Translate>
          </dt>
          <dd>{financialTransactionEntity.transactionIngestion ? financialTransactionEntity.transactionIngestion.id : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialTransaction.tags">Tags</Translate>
          </dt>
          <dd>
            {financialTransactionEntity.tags
              ? financialTransactionEntity.tags.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {financialTransactionEntity.tags && i === financialTransactionEntity.tags.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/financial-transaction" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/financial-transaction/${financialTransactionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default FinancialTransactionDetail;
