import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './budget.reducer';

export const BudgetDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const budgetEntity = useAppSelector(state => state.budget.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="budgetDetailsHeading">
          <Translate contentKey="fintrackApp.budget.detail.title">Budget</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.budget.name">Name</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.name}</dd>
          <dt>
            <span id="amount">
              <Translate contentKey="fintrackApp.budget.amount">Amount</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.amount}</dd>
          <dt>
            <span id="currency">
              <Translate contentKey="fintrackApp.budget.currency">Currency</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.currency}</dd>
          <dt>
            <span id="period">
              <Translate contentKey="fintrackApp.budget.period">Period</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.period}</dd>
          <dt>
            <span id="startDate">
              <Translate contentKey="fintrackApp.budget.startDate">Start Date</Translate>
            </span>
          </dt>
          <dd>
            {budgetEntity.startDate ? <TextFormat value={budgetEntity.startDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : null}
          </dd>
          <dt>
            <span id="endDate">
              <Translate contentKey="fintrackApp.budget.endDate">End Date</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.endDate ? <TextFormat value={budgetEntity.endDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : null}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="fintrackApp.budget.status">Status</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.status}</dd>
          <dt>
            <span id="tagMatchMode">
              <Translate contentKey="fintrackApp.budget.tagMatchMode">Tag Match Mode</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.tagMatchMode}</dd>
          <dt>
            <span id="warningPercentage">
              <Translate contentKey="fintrackApp.budget.warningPercentage">Warning Percentage</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.warningPercentage}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.budget.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.createdAt ? <TextFormat value={budgetEntity.createdAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.budget.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>{budgetEntity.updatedAt ? <TextFormat value={budgetEntity.updatedAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <Translate contentKey="fintrackApp.budget.user">User</Translate>
          </dt>
          <dd>{budgetEntity.user ? budgetEntity.user.login : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.budget.accounts">Accounts</Translate>
          </dt>
          <dd>
            {budgetEntity.accounts
              ? budgetEntity.accounts.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {budgetEntity.accounts && i === budgetEntity.accounts.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.budget.categories">Categories</Translate>
          </dt>
          <dd>
            {budgetEntity.categories
              ? budgetEntity.categories.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {budgetEntity.categories && i === budgetEntity.categories.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.budget.tags">Tags</Translate>
          </dt>
          <dd>
            {budgetEntity.tags
              ? budgetEntity.tags.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {budgetEntity.tags && i === budgetEntity.tags.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/budget" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/budget/${budgetEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default BudgetDetail;
