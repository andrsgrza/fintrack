import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities, getEntity } from './category.reducer';
import { CategoryAppearance, CategoryPath, CategoryStatusBadge, CategoryTypeLabel, getImmediateChildren } from './category-presentation';

export const CategoryDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
    dispatch(getEntities({ sort: 'name,asc' }));
  }, []);

  const categoryEntity = useAppSelector(state => state.category.entity);
  const categories = useAppSelector(state => state.category.entities);
  const children = getImmediateChildren(categoryEntity.id, categories);
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
          {categoryEntity.description ? (
            <>
              <dt>
                <Translate contentKey="fintrackApp.category.description">Description</Translate>
              </dt>
              <dd>{categoryEntity.description}</dd>
            </>
          ) : null}
          <dt>
            <span id="categoryType">
              <Translate contentKey="fintrackApp.category.categoryType">Type</Translate>
            </span>
          </dt>
          <dd data-cy="categoryTypeValue">
            <CategoryTypeLabel categoryType={categoryEntity.categoryType} />
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.category.hierarchy">Hierarchy</Translate>
          </dt>
          <dd>
            <CategoryPath category={categoryEntity} categories={categories} />
          </dd>
          <dt>
            <span id="color">
              <Translate contentKey="fintrackApp.category.color">Color</Translate>
            </span>
          </dt>
          <dd>
            <CategoryAppearance category={categoryEntity} />
          </dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.category.active">Active</Translate>
            </span>
          </dt>
          <dd>
            <CategoryStatusBadge active={categoryEntity.active} />
          </dd>
        </dl>
        {categoryEntity.active === false ? (
          <Alert color="secondary" fade={false} data-cy="categoryInactiveExplanation" data-testid="categoryInactiveExplanation">
            <Translate contentKey="fintrackApp.category.inactiveExplanation">
              This category is kept for historical records and cannot be newly assigned until it is reactivated.
            </Translate>
          </Alert>
        ) : null}
        <section aria-labelledby="category-children-heading" className="mb-4" data-cy="categoryChildrenSummary">
          <h3 id="category-children-heading" className="h5">
            <Translate contentKey="fintrackApp.category.children">Subcategories</Translate> ({children.length})
          </h3>
          {children.length > 0 ? (
            <ul className="mb-0" data-cy="categoryChildren">
              {children.map(child => (
                <li key={child.id}>
                  <Link to={`/category/${child.id}`}>{child.name}</Link> <CategoryStatusBadge active={child.active} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-muted mb-0">
              <Translate contentKey="fintrackApp.category.noChildren">No subcategories.</Translate>
            </p>
          )}
        </section>
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
