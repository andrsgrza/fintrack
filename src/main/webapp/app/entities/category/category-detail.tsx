import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
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
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.category.name">Name</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.name}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.category.description">Description</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.description}</dd>
          <dt>
            <span id="categoryType">
              <Translate contentKey="fintrackApp.category.categoryType">Category Type</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.categoryType}</dd>
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
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.category.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.createdAt ? <TextFormat value={categoryEntity.createdAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.category.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>{categoryEntity.updatedAt ? <TextFormat value={categoryEntity.updatedAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <Translate contentKey="fintrackApp.category.parentCategory">Parent Category</Translate>
          </dt>
          <dd>{categoryEntity.parentCategory ? categoryEntity.parentCategory.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.category.budgets">Budgets</Translate>
          </dt>
          <dd>
            {categoryEntity.budgets
              ? categoryEntity.budgets.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {categoryEntity.budgets && i === categoryEntity.budgets.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
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
