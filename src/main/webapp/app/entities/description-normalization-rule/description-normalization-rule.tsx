import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { ASC } from 'app/shared/util/pagination.constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities } from './description-normalization-rule.reducer';

export const DescriptionNormalizationRule = () => {
  const dispatch = useAppDispatch();
  const [reordering, setReordering] = useState(false);
  const rules = useAppSelector(state => state.descriptionNormalizationRule.entities);
  const loading = useAppSelector(state => state.descriptionNormalizationRule.loading);
  const orderedRules = [...rules].sort((a, b) => (a.priority ?? 999999) - (b.priority ?? 999999) || (a.id ?? 999999) - (b.id ?? 999999));

  const load = () => dispatch(getEntities({ sort: `priority,${ASC}&sort=id,${ASC}` }));

  useEffect(() => {
    load();
  }, []);

  const move = async (fromIndex: number, toIndex: number) => {
    const nextRules = [...orderedRules];
    const [moved] = nextRules.splice(fromIndex, 1);
    nextRules.splice(toIndex, 0, moved);
    setReordering(true);
    try {
      await axios.put('api/description-normalization-rules/reorder', { orderedIds: nextRules.map(rule => rule.id) });
      load();
    } finally {
      setReordering(false);
    }
  };

  return (
    <div>
      <h2 id="description-normalization-rule-heading" data-cy="DescriptionNormalizationRuleHeading">
        <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.title">Description Normalization Rules</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={load} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.refreshListLabel">Refresh list</Translate>
          </Button>
          <Link to="/description-normalization-rule/new" className="btn btn-primary jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.createLabel">Create rule</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {orderedRules.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.order">Order</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.name">Name</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.active">Active</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditionOperator">Condition Logic</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.resultingDescription">Resulting Description</Translate>
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orderedRules.map((rule, index) => (
                <tr key={`entity-${rule.id}`} data-cy="entityTable">
                  <td>#{(rule.priority ?? index) + 1}</td>
                  <td>
                    <Link to={`/description-normalization-rule/${rule.id}`}>{rule.name}</Link>
                  </td>
                  <td>{rule.active ? 'Active' : 'Inactive'}</td>
                  <td>{rule.conditionOperator}</td>
                  <td>{rule.resultingDescription}</td>
                  <td className="text-end">
                    {index > 0 ? (
                      <Button size="sm" color="secondary" className="me-1" disabled={reordering} onClick={() => move(index, index - 1)}>
                        <Translate contentKey="fintrackApp.descriptionNormalizationRule.moveUp">Move up</Translate>
                      </Button>
                    ) : null}
                    {index < orderedRules.length - 1 ? (
                      <Button size="sm" color="secondary" className="me-1" disabled={reordering} onClick={() => move(index, index + 1)}>
                        <Translate contentKey="fintrackApp.descriptionNormalizationRule.moveDown">Move down</Translate>
                      </Button>
                    ) : null}
                    <Button tag={Link} to={`/description-normalization-rule/${rule.id}`} color="info" size="sm" className="me-1">
                      <FontAwesomeIcon icon="eye" />
                    </Button>
                    <Button tag={Link} to={`/description-normalization-rule/${rule.id}/edit`} color="primary" size="sm" className="me-1">
                      <FontAwesomeIcon icon="pencil-alt" />
                    </Button>
                    <Button tag={Link} to={`/description-normalization-rule/${rule.id}/delete`} color="danger" size="sm">
                      <FontAwesomeIcon icon="trash" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.home.notFound">No rules found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default DescriptionNormalizationRule;
