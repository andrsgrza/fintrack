import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './tag.reducer';

export const TagDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const tagEntity = useAppSelector(state => state.tag.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="tagDetailsHeading">
          <Translate contentKey="fintrackApp.tag.detail.title">Tag</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.tag.name">Name</Translate>
            </span>
          </dt>
          <dd>{tagEntity.name}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.tag.description">Description</Translate>
            </span>
          </dt>
          <dd>{tagEntity.description}</dd>
          <dt>
            <span id="color">
              <Translate contentKey="fintrackApp.tag.color">Color</Translate>
            </span>
          </dt>
          <dd>{tagEntity.color}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.tag.active">Active</Translate>
            </span>
          </dt>
          <dd>{tagEntity.active ? 'true' : 'false'}</dd>
        </dl>
        <Button tag={Link} to="/tag" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/tag/${tagEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default TagDetail;
