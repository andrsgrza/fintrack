import React, { useEffect } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getTransactionRules } from 'app/entities/transaction-rule/transaction-rule.reducer';
import { createEntity, getEntity, partialUpdateEntity, reset } from './transaction-rule-condition.reducer';
import TransactionRuleConditionFormSection from './components/transaction-rule-condition-form-section';

export const TransactionRuleConditionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const [searchParams] = useSearchParams();
  const isNew = id === undefined;
  const requestedTransactionRuleId = searchParams.get('transactionRuleId');

  const transactionRuleConditionEntity = useAppSelector(state => state.transactionRuleCondition.entity);
  const loading = useAppSelector(state => state.transactionRuleCondition.loading);
  const updating = useAppSelector(state => state.transactionRuleCondition.updating);
  const updateSuccess = useAppSelector(state => state.transactionRuleCondition.updateSuccess);

  const handleClose = () => {
    navigate('/transaction-rule-condition');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getTransactionRules({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = entity => {
    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(partialUpdateEntity(entity));
    }
  };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRuleCondition.home.createOrEditLabel" data-cy="TransactionRuleConditionCreateUpdateHeading">
            <Translate
              contentKey={isNew ? 'fintrackApp.transactionRuleCondition.createTitle' : 'fintrackApp.transactionRuleCondition.editTitle'}
            >
              {isNew ? 'Create Transaction Rule Condition' : 'Edit Transaction Rule Condition'}
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <>
              <TransactionRuleConditionFormSection
                initialCondition={isNew ? undefined : transactionRuleConditionEntity}
                isNew={isNew}
                requestedTransactionRuleId={requestedTransactionRuleId ?? undefined}
                parentDisabled={!isNew}
                submitting={updating}
                onSubmit={saveEntity}
              />
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-rule-condition" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
            </>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionRuleConditionUpdate;
