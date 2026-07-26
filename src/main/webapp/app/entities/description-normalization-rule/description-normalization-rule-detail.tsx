import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row, Table } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IDescriptionNormalizationRuleCondition } from 'app/shared/model/description-normalization-rule-condition.model';
import { DescriptionNormalizationRuleOperator } from 'app/shared/model/enumerations/description-normalization-rule-operator.model';
import { getEntity } from './description-normalization-rule.reducer';

export const DescriptionNormalizationRuleDetail = () => {
  const dispatch = useAppDispatch();
  const { id } = useParams<'id'>();
  const rule = useAppSelector(state => state.descriptionNormalizationRule.entity);
  const [conditions, setConditions] = useState<IDescriptionNormalizationRuleCondition[]>([]);
  const [editing, setEditing] = useState<IDescriptionNormalizationRuleCondition | null>(null);
  const [form, setForm] = useState({ operator: 'CONTAINS', value: '', caseSensitive: false });

  const loadConditions = () =>
    axios.get<IDescriptionNormalizationRuleCondition[]>(`api/description-normalization-rules/${id}/conditions`).then(response => {
      setConditions(response.data);
    });

  useEffect(() => {
    dispatch(getEntity(id));
    loadConditions();
  }, []);

  const startAdd = () => {
    setEditing({ descriptionNormalizationRule: { id: Number(id) } });
    setForm({ operator: 'CONTAINS', value: '', caseSensitive: false });
  };

  const startEdit = (condition: IDescriptionNormalizationRuleCondition) => {
    setEditing(condition);
    setForm({
      operator: condition.operator ?? 'CONTAINS',
      value: condition.value ?? '',
      caseSensitive: Boolean(condition.caseSensitive),
    });
  };

  const saveCondition = async () => {
    const payload = {
      ...editing,
      operator: form.operator,
      value: form.value,
      caseSensitive: form.caseSensitive,
      descriptionNormalizationRule: { id: Number(id) },
    };
    if (editing?.id) {
      await axios.patch(`api/description-normalization-rule-conditions/${editing.id}`, payload);
    } else {
      await axios.post('api/description-normalization-rule-conditions', payload);
    }
    setEditing(null);
    await loadConditions();
  };

  const deleteCondition = async (conditionId: number) => {
    if (window.confirm('Delete condition?')) {
      await axios.delete(`api/description-normalization-rule-conditions/${conditionId}`);
      await loadConditions();
    }
  };

  return (
    <Row>
      <Col md="8">
        <h2 data-cy="descriptionNormalizationRuleDetailsHeading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.title">Description Normalization Rule</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.name">Name</Translate>
          </dt>
          <dd>{rule.name}</dd>
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.description">Description</Translate>
          </dt>
          <dd>{rule.description}</dd>
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.order">Order</Translate>
          </dt>
          <dd>#{(rule.priority ?? 0) + 1}</dd>
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditionOperator">Condition Logic</Translate>
          </dt>
          <dd>{rule.conditionOperator}</dd>
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.resultingDescription">Resulting Description</Translate>
          </dt>
          <dd>{rule.resultingDescription}</dd>
          <dt>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.active">Active</Translate>
          </dt>
          <dd>{rule.active ? 'Active' : 'Inactive'}</dd>
        </dl>
        <h3>
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditions">Conditions</Translate>
        </h3>
        <p className="text-muted">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.activeRequiresCondition">
            Active rules require at least one condition.
          </Translate>
        </p>
        <Button color="primary" size="sm" className="mb-2" onClick={startAdd}>
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.addCondition">Add condition</Translate>
        </Button>
        {editing ? (
          <div className="border rounded p-3 mb-3">
            <label htmlFor="dnr-condition-operator">
              <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.operator">Operator</Translate>
            </label>
            <select
              id="dnr-condition-operator"
              className="form-control mb-2"
              value={form.operator}
              onChange={event => setForm({ ...form, operator: event.target.value })}
            >
              {Object.keys(DescriptionNormalizationRuleOperator).map(operator => (
                <option value={operator} key={operator}>
                  {operator}
                </option>
              ))}
            </select>
            <label htmlFor="dnr-condition-value">
              <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.value">Value</Translate>
            </label>
            <input
              id="dnr-condition-value"
              className="form-control mb-2"
              value={form.value}
              onChange={event => setForm({ ...form, value: event.target.value })}
            />
            <label className="d-block mb-2">
              <input
                type="checkbox"
                checked={form.caseSensitive}
                onChange={event => setForm({ ...form, caseSensitive: event.target.checked })}
              />{' '}
              <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.caseSensitive">Case sensitive</Translate>
            </label>
            <Button color="primary" size="sm" onClick={saveCondition}>
              <Translate contentKey="entity.action.save">Save</Translate>
            </Button>{' '}
            <Button color="secondary" size="sm" onClick={() => setEditing(null)}>
              <Translate contentKey="entity.action.cancel">Cancel</Translate>
            </Button>
          </div>
        ) : null}
        <Table responsive>
          <thead>
            <tr>
              <th>#</th>
              <th>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.operator">Operator</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.value">Value</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.caseSensitive">Case sensitive</Translate>
              </th>
              <th />
            </tr>
          </thead>
          <tbody>
            {conditions.map(condition => (
              <tr key={condition.id}>
                <td>{(condition.position ?? 0) + 1}</td>
                <td>{condition.operator}</td>
                <td>{condition.value}</td>
                <td>{condition.caseSensitive ? 'Yes' : 'No'}</td>
                <td className="text-end">
                  <Button color="primary" size="sm" className="me-1" onClick={() => startEdit(condition)}>
                    <Translate contentKey="entity.action.edit">Edit</Translate>
                  </Button>
                  <Button color="danger" size="sm" onClick={() => deleteCondition(condition.id)}>
                    <Translate contentKey="entity.action.delete">Delete</Translate>
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
        <Button tag={Link} to="/description-normalization-rule" replace color="info">
          <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
        </Button>{' '}
        <Button tag={Link} to={`/description-normalization-rule/${rule.id}/edit`} color="primary">
          <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
        </Button>
      </Col>
    </Row>
  );
};

export default DescriptionNormalizationRuleDetail;
