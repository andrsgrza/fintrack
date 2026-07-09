import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './transaction-ingestion.reducer';

export const TransactionIngestionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const transactionIngestionEntity = useAppSelector(state => state.transactionIngestion.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="transactionIngestionDetailsHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.detail.title">TransactionIngestion</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.id}</dd>
          <dt>
            <span id="ingestionType">
              <Translate contentKey="fintrackApp.transactionIngestion.ingestionType">Ingestion Type</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.ingestionType}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.status}</dd>
          <dt>
            <span id="sourceLabel">
              <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source Label</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.sourceLabel}</dd>
          <dt>
            <span id="startedAt">
              <Translate contentKey="fintrackApp.transactionIngestion.startedAt">Started At</Translate>
            </span>
          </dt>
          <dd>
            {transactionIngestionEntity.startedAt ? (
              <TextFormat value={transactionIngestionEntity.startedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="completedAt">
              <Translate contentKey="fintrackApp.transactionIngestion.completedAt">Completed At</Translate>
            </span>
          </dt>
          <dd>
            {transactionIngestionEntity.completedAt ? (
              <TextFormat value={transactionIngestionEntity.completedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="recordsReceived">
              <Translate contentKey="fintrackApp.transactionIngestion.recordsReceived">Records Received</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.recordsReceived}</dd>
          <dt>
            <span id="recordsCreated">
              <Translate contentKey="fintrackApp.transactionIngestion.recordsCreated">Records Created</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.recordsCreated}</dd>
          <dt>
            <span id="recordsSkipped">
              <Translate contentKey="fintrackApp.transactionIngestion.recordsSkipped">Records Skipped</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.recordsSkipped}</dd>
          <dt>
            <span id="recordsRejected">
              <Translate contentKey="fintrackApp.transactionIngestion.recordsRejected">Records Rejected</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.recordsRejected}</dd>
          <dt>
            <span id="errorMessage">
              <Translate contentKey="fintrackApp.transactionIngestion.errorMessage">Error Message</Translate>
            </span>
          </dt>
          <dd>{transactionIngestionEntity.errorMessage}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.transactionIngestion.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {transactionIngestionEntity.createdAt ? (
              <TextFormat value={transactionIngestionEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.account">Account</Translate>
          </dt>
          <dd>{transactionIngestionEntity.account ? transactionIngestionEntity.account.name : ''}</dd>
        </dl>
        <Button tag={Link} to="/transaction-ingestion" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/transaction-ingestion/${transactionIngestionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default TransactionIngestionDetail;
