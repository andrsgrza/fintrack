import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './transaction-rule-condition.reducer';

export const TransactionRuleConditionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const transactionRuleConditionEntity = useAppSelector(state => state.transactionRuleCondition.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="transactionRuleConditionDetailsHeading">
          <Translate contentKey="fintrackApp.transactionRuleCondition.detail.title">TransactionRuleCondition</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.id}</dd>
          <dt>
            <span id="field">
              <Translate contentKey="fintrackApp.transactionRuleCondition.field">Field</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.field}</dd>
          <dt>
            <span id="operator">
              <Translate contentKey="fintrackApp.transactionRuleCondition.operator">Operator</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.operator}</dd>
          <dt>
            <span id="value">
              <Translate contentKey="fintrackApp.transactionRuleCondition.value">Value</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.value}</dd>
          <dt>
            <span id="secondValue">
              <Translate contentKey="fintrackApp.transactionRuleCondition.secondValue">Second Value</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.secondValue}</dd>
          <dt>
            <span id="caseSensitive">
              <Translate contentKey="fintrackApp.transactionRuleCondition.caseSensitive">Case Sensitive</Translate>
            </span>
          </dt>
          <dd>{transactionRuleConditionEntity.caseSensitive ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionRuleCondition.transactionRule">Transaction Rule</Translate>
          </dt>
          <dd>{transactionRuleConditionEntity.transactionRule ? transactionRuleConditionEntity.transactionRule.name : ''}</dd>
        </dl>
        <Button tag={Link} to="/transaction-rule-condition" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/transaction-rule-condition/${transactionRuleConditionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default TransactionRuleConditionDetail;
