import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { createEntity, getEntity, partialUpdateEntity, reset } from './description-normalization-rule.reducer';

export const DescriptionNormalizationRuleUpdate = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;
  const rule = useAppSelector(state => state.descriptionNormalizationRule.entity);
  const loading = useAppSelector(state => state.descriptionNormalizationRule.loading);
  const updating = useAppSelector(state => state.descriptionNormalizationRule.updating);
  const updateSuccess = useAppSelector(state => state.descriptionNormalizationRule.updateSuccess);
  const [conditionCount, setConditionCount] = useState<number | null>(null);

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
      axios
        .get(`api/description-normalization-rules/${id}/conditions`)
        .then(response => setConditionCount(response.data.length))
        .catch(() => setConditionCount(null));
    }
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      if (isNew && rule.id) {
        navigate(`/description-normalization-rule/${rule.id}`);
      } else {
        navigate('/description-normalization-rule');
      }
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (isNew) {
      dispatch(
        createEntity({
          ...values,
          active: false,
          priority: undefined,
          createdAt: undefined,
          updatedAt: undefined,
        }),
      );
    } else {
      dispatch(partialUpdateEntity({ ...rule, ...values, priority: undefined, createdAt: undefined, updatedAt: undefined }));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          active: false,
          conditionOperator: 'ALL',
        }
      : {
          ...rule,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 data-cy="DescriptionNormalizationRuleCreateUpdateHeading">
            <Translate
              contentKey={
                isNew ? 'fintrackApp.descriptionNormalizationRule.createTitle' : 'fintrackApp.descriptionNormalizationRule.editTitle'
              }
            >
              Create or edit Description Normalization Rule
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? <ValidatedField name="id" required readOnly id="description-normalization-rule-id" label="ID" /> : null}
              <ValidatedField
                label={translate('fintrackApp.descriptionNormalizationRule.name')}
                id="description-normalization-rule-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{ required: { value: true, message: translate('entity.validation.required') } }}
              />
              <ValidatedField
                label={translate('fintrackApp.descriptionNormalizationRule.description')}
                id="description-normalization-rule-description"
                name="description"
                data-cy="description"
                type="textarea"
              />
              <ValidatedField
                label={translate('fintrackApp.descriptionNormalizationRule.conditionOperator')}
                id="description-normalization-rule-conditionOperator"
                name="conditionOperator"
                data-cy="conditionOperator"
                type="select"
              >
                {Object.keys(RuleConditionLogic).map(key => (
                  <option value={key} key={key}>
                    {key}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.descriptionNormalizationRule.resultingDescription')}
                id="description-normalization-rule-resultingDescription"
                name="resultingDescription"
                data-cy="resultingDescription"
                type="text"
                validate={{ required: { value: true, message: translate('entity.validation.required') } }}
              />
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.descriptionNormalizationRule.active')}
                  id="description-normalization-rule-active"
                  name="active"
                  data-cy="active"
                  check
                  type="checkbox"
                  disabled={conditionCount === 0}
                />
              ) : null}
              {!isNew && conditionCount === 0 ? (
                <small className="text-muted d-block mb-3">
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.activeDisabledNoConditions">
                    Add at least one condition before activating this rule.
                  </Translate>
                </small>
              ) : null}
              {!isNew ? (
                <Button tag={Link} to={`/description-normalization-rule/${rule.id}`} color="secondary" className="mb-3">
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.manageConditions">Manage conditions</Translate>
                </Button>
              ) : (
                <p className="text-muted">
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.createInactiveHelp">
                    Rules are saved inactive first. Add conditions from the detail page, then activate the rule.
                  </Translate>
                </p>
              )}
              <div>
                <Button tag={Link} id="cancel-save" to="/description-normalization-rule" replace color="info">
                  <FontAwesomeIcon icon="arrow-left" />
                  &nbsp;
                  <Translate contentKey="entity.action.back">Back</Translate>
                </Button>
                &nbsp;
                <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                  <FontAwesomeIcon icon="save" />
                  &nbsp;
                  <Translate contentKey="entity.action.save">Save</Translate>
                </Button>
              </div>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default DescriptionNormalizationRuleUpdate;
