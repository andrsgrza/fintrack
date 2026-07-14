import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './category.reducer';

export const CategoryDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const categoryEntity = useAppSelector(state => state.category.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="categoryDetailsHeading">
          <Translate contentKey="fintrackApp.category.detail.title">Category</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.category.name">Name</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.name}</dd>
          <dt>
            <span id="categoryType">
              <Translate contentKey="fintrackApp.category.categoryType">Type</Translate>
            </span>
          </dt>
          <dd>
            {categoryEntity.categoryType ? <Translate contentKey={`fintrackApp.CategoryType.${categoryEntity.categoryType}`} /> : null}
          </dd>
          {categoryEntity.parentCategory ? (
            <>
              <dt>
                <Translate contentKey="fintrackApp.category.parentCategory">Parent category</Translate>
              </dt>
              <dd>{categoryEntity.parentCategory.name}</dd>
            </>
          ) : null}
          <dt>
            <span id="color">
              <Translate contentKey="fintrackApp.category.color">Color</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.color}</dd>
          <dt>
            <span id="icon">
              <Translate contentKey="fintrackApp.category.icon">Icon</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.icon}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.category.active">Active</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.active ? 'true' : 'false'}</dd>
        </dl>
        <Button tag={Link} to="/category" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/category/${categoryEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default CategoryDetail;
