import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './transaction-rule.reducer';

export const TransactionRuleUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const transactionRuleEntity = useAppSelector(state => state.transactionRule.entity);
  const loading = useAppSelector(state => state.transactionRule.loading);
  const updating = useAppSelector(state => state.transactionRule.updating);
  const updateSuccess = useAppSelector(state => state.transactionRule.updateSuccess);
  const ruleConditionLogicValues = Object.keys(RuleConditionLogic);
  const [conditionsState, setConditionsState] = useState({ count: 0, loaded: false, failed: false });

  const activeDisabled = !isNew && (!conditionsState.loaded || conditionsState.failed || conditionsState.count === 0);
  const isEntityLoaded = isNew || transactionRuleEntity?.id?.toString() === id;
  const formKey = isNew ? 'new' : `transaction-rule-${transactionRuleEntity?.id ?? 'loading'}`;

  const handleClose = () => {
    navigate('/transaction-rule');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getCategories({}));
    dispatch(getTags({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      if (isNew && transactionRuleEntity?.id) {
        navigate(`/transaction-rule/${transactionRuleEntity.id}`);
      } else {
        handleClose();
      }
    }
  }, [updateSuccess, isNew, transactionRuleEntity?.id]);

  useEffect(() => {
    if (isNew || !id) {
      return;
    }

    axios
      .get<ITransactionRuleCondition[]>(`api/transaction-rules/${id}/conditions`)
      .then(response => {
        setConditionsState({ count: response.data.length, loaded: true, failed: false });
      })
      .catch(() => {
        setConditionsState({ count: 0, loaded: false, failed: true });
      });
  }, [isNew, id]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    const { priority, ...submittedValues } = values;
    const entity = {
      ...submittedValues,
      id: isNew ? undefined : transactionRuleEntity.id,
      active: isNew ? false : values.active,
      resultingCategory: values.resultingCategory ? categories.find(it => it.id.toString() === values.resultingCategory?.toString()) : null,
      resultingTags: mapIdList(values.resultingTags),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(partialUpdateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          active: false,
          conditionLogic: 'ALL',
        }
      : {
          conditionLogic: 'ALL',
          ...transactionRuleEntity,
          resultingCategory: transactionRuleEntity?.resultingCategory?.id,
          resultingTags: transactionRuleEntity?.resultingTags?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRule.home.createOrEditLabel" data-cy="TransactionRuleCreateUpdateHeading">
            <Translate contentKey={isNew ? 'fintrackApp.transactionRule.createTitle' : 'fintrackApp.transactionRule.editTitle'}>
              {isNew ? 'Create Transaction Rule' : 'Edit Transaction Rule'}
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading || !isEntityLoaded ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm key={formKey} defaultValues={defaultValues()} onSubmit={saveEntity}>
              {isNew ? (
                <FormText className="d-block mb-3">
                  <Translate contentKey="fintrackApp.transactionRule.createInactiveHelp">
                    Rules are saved inactive first. Add conditions from the rule detail page, then activate the rule when ready.
                  </Translate>
                </FormText>
              ) : null}
              <FormText className="d-block mb-3">
                <Translate contentKey="fintrackApp.transactionRule.priorityServerManagedHelp">
                  Rule order is managed from the rules list. New rules are added last.
                </Translate>
              </FormText>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="transaction-rule-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <h3 id="transaction-rule-identity-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.identity">Identity</Translate>
              </h3>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.name')}
                id="transaction-rule-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionRule.description')}
                id="transaction-rule-description"
                name="description"
                data-cy="description"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <h3 id="transaction-rule-matching-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.matching">Matching logic</Translate>
              </h3>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.conditionLogic')}
                id="transaction-rule-conditionLogic"
                name="conditionLogic"
                data-cy="conditionLogic"
                type="select"
              >
                {ruleConditionLogicValues.map(ruleConditionLogic => (
                  <option value={ruleConditionLogic} key={ruleConditionLogic}>
                    {translate(`fintrackApp.RuleConditionLogic.${ruleConditionLogic}`)}
                  </option>
                ))}
              </ValidatedField>
              {!isNew ? (
                <Button tag={Link} to={`/transaction-rule/${transactionRuleEntity.id}`} color="secondary" data-cy="manageConditionsButton">
                  <FontAwesomeIcon icon="list" />
                  &nbsp;
                  <Translate contentKey="fintrackApp.transactionRule.manageConditions">Manage conditions</Translate>
                </Button>
              ) : null}
              <h3 id="transaction-rule-result-heading" className="mt-4">
                <Translate contentKey="fintrackApp.transactionRule.sections.result">Result</Translate>
              </h3>
              <ValidatedField
                id="transaction-rule-resultingCategory"
                name="resultingCategory"
                data-cy="resultingCategory"
                label={translate('fintrackApp.transactionRule.resultingCategory')}
                type="select"
              >
                <option value="" key="0" />
                {categories
                  ? categories.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.transactionRule.resultingTags')}
                id="transaction-rule-resultingTags"
                data-cy="resultingTags"
                type="select"
                multiple
                name="resultingTags"
              >
                <option value="" key="0" />
                {tags
                  ? tags.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              {!isNew ? (
                <h3 id="transaction-rule-status-heading" className="mt-4">
                  <Translate contentKey="fintrackApp.transactionRule.sections.status">Status</Translate>
                </h3>
              ) : null}
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.transactionRule.active')}
                  id="transaction-rule-active"
                  name="active"
                  data-cy="active"
                  check
                  type="checkbox"
                  disabled={activeDisabled}
                />
              ) : null}
              {!isNew ? (
                <FormText>
                  <Translate contentKey="fintrackApp.transactionRule.activeRequiresCondition">
                    Active rules require at least one condition.
                  </Translate>
                </FormText>
              ) : null}
              {!isNew && activeDisabled ? (
                <FormText>
                  <Translate contentKey="fintrackApp.transactionRule.activeDisabledNoConditions">
                    Add at least one condition before activating this rule.
                  </Translate>
                </FormText>
              ) : null}
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-rule" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey={isNew ? 'fintrackApp.transactionRule.saveAndAddConditions' : 'entity.action.save'}>
                  {isNew ? 'Save and add conditions' : 'Save'}
                </Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionRuleUpdate;
