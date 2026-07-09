import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './financial-subscription.reducer';

export const FinancialSubscriptionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const financialSubscriptionEntity = useAppSelector(state => state.financialSubscription.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="financialSubscriptionDetailsHeading">
          <Translate contentKey="fintrackApp.financialSubscription.detail.title">FinancialSubscription</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.financialSubscription.name">Name</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.name}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.financialSubscription.description">Description</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.description}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="fintrackApp.financialSubscription.status">Status</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.status}</dd>
          <dt>
            <span id="expectedAmount">
              <Translate contentKey="fintrackApp.financialSubscription.expectedAmount">Expected Amount</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.expectedAmount}</dd>
          <dt>
            <span id="amountTolerancePercentage">
              <Translate contentKey="fintrackApp.financialSubscription.amountTolerancePercentage">Amount Tolerance Percentage</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.amountTolerancePercentage}</dd>
          <dt>
            <span id="currency">
              <Translate contentKey="fintrackApp.financialSubscription.currency">Currency</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.currency}</dd>
          <dt>
            <span id="recurrenceUnit">
              <Translate contentKey="fintrackApp.financialSubscription.recurrenceUnit">Recurrence Unit</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.recurrenceUnit}</dd>
          <dt>
            <span id="intervalCount">
              <Translate contentKey="fintrackApp.financialSubscription.intervalCount">Interval Count</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.intervalCount}</dd>
          <dt>
            <span id="startDate">
              <Translate contentKey="fintrackApp.financialSubscription.startDate">Start Date</Translate>
            </span>
          </dt>
          <dd>
            {financialSubscriptionEntity.startDate ? (
              <TextFormat value={financialSubscriptionEntity.startDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="nextExpectedDate">
              <Translate contentKey="fintrackApp.financialSubscription.nextExpectedDate">Next Expected Date</Translate>
            </span>
          </dt>
          <dd>
            {financialSubscriptionEntity.nextExpectedDate ? (
              <TextFormat value={financialSubscriptionEntity.nextExpectedDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="endDate">
              <Translate contentKey="fintrackApp.financialSubscription.endDate">End Date</Translate>
            </span>
          </dt>
          <dd>
            {financialSubscriptionEntity.endDate ? (
              <TextFormat value={financialSubscriptionEntity.endDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="automaticPayment">
              <Translate contentKey="fintrackApp.financialSubscription.automaticPayment">Automatic Payment</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.automaticPayment ? 'true' : 'false'}</dd>
          <dt>
            <span id="notes">
              <Translate contentKey="fintrackApp.financialSubscription.notes">Notes</Translate>
            </span>
          </dt>
          <dd>{financialSubscriptionEntity.notes}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.financialSubscription.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {financialSubscriptionEntity.createdAt ? (
              <TextFormat value={financialSubscriptionEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.financialSubscription.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {financialSubscriptionEntity.updatedAt ? (
              <TextFormat value={financialSubscriptionEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.financialSubscription.account">Account</Translate>
          </dt>
          <dd>{financialSubscriptionEntity.account ? financialSubscriptionEntity.account.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialSubscription.category">Category</Translate>
          </dt>
          <dd>{financialSubscriptionEntity.category ? financialSubscriptionEntity.category.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.financialSubscription.tags">Tags</Translate>
          </dt>
          <dd>
            {financialSubscriptionEntity.tags
              ? financialSubscriptionEntity.tags.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {financialSubscriptionEntity.tags && i === financialSubscriptionEntity.tags.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/financial-subscription" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/financial-subscription/${financialSubscriptionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default FinancialSubscriptionDetail;
