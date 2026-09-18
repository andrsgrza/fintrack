import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, DropdownItem, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities, getEntity } from './category.reducer';
import { CategoryPath, CategoryStatusBadge, CategoryTypeLabel, getImmediateChildren } from './category-presentation';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductStatusHelp } from 'app/shared/ui/product-status-control';

export const CategoryDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
    dispatch(getEntities({ sort: 'name,asc' }));
  }, [id]);

  const categoryEntity = useAppSelector(state => state.category.entity);
  const categories = useAppSelector(state => state.category.entities);
  const children = getImmediateChildren(categoryEntity.id, categories);
  return (
    <ProductPage>
      <ProductPageHeader
        headingId="category-details-heading"
        dataCy="categoryDetailsHeading"
        accentColor={categoryEntity.color}
        accentDataCy="categoryDetailColorAccent"
        title={categoryEntity.name ?? <Translate contentKey="fintrackApp.category.detail.title">Category</Translate>}
        metadata={
          <>
            <CategoryTypeLabel categoryType={categoryEntity.categoryType} />
            <CategoryStatusBadge active={categoryEntity.active} />
            <ProductStatusHelp
              id="category-detail-status-help"
              label={translate('fintrackApp.category.status.label')}
              help={
                <Translate contentKey="fintrackApp.category.status.help">
                  Inactive categories are kept for history and cannot be newly assigned until reactivated.
                </Translate>
              }
              dataCyPrefix="categoryDetail"
            />
          </>
        }
        actions={
          <>
            <Button tag={Link} to="/category" replace color="secondary" outline size="sm" data-cy="entityDetailsBackButton">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            <Button
              tag={Link}
              to={`/category/${categoryEntity.id}/edit`}
              replace
              color="primary"
              size="sm"
              data-cy="entityDetailsEditButton"
            >
              <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
            </Button>
            <ProductActionsMenu label={translate('fintrackApp.category.moreActions')} dataCy="categoryDetailActionsMenu">
              <DropdownItem tag={Link} to={`/category/${categoryEntity.id}/delete`} className="text-danger" data-cy="entityDeleteButton">
                <Translate contentKey="entity.action.delete">Delete</Translate>
              </DropdownItem>
            </ProductActionsMenu>
          </>
        }
      />
      <Row className="g-3">
        <Col xs="12">
          <ProductSection
            title={<Translate contentKey="fintrackApp.category.description">Description</Translate>}
            dataCy="categoryDetailDescription"
          >
            <p className="mb-0">
              {categoryEntity.description || (
                <Translate contentKey="fintrackApp.category.noDescription">No description provided.</Translate>
              )}
            </p>
          </ProductSection>
        </Col>
        <Col md="5">
          <ProductSection
            title={<Translate contentKey="fintrackApp.category.classification">Classification</Translate>}
            dataCy="categoryDetailClassification"
          >
            <div data-cy="categoryTypeValue">
              <CategoryTypeLabel categoryType={categoryEntity.categoryType} />
            </div>
          </ProductSection>
        </Col>
        <Col md="7">
          <ProductSection
            title={<Translate contentKey="fintrackApp.category.hierarchy">Hierarchy</Translate>}
            dataCy="categoryDetailHierarchy"
          >
            <CategoryPath category={categoryEntity} categories={categories} />
          </ProductSection>
        </Col>
        <Col xs="12">
          <ProductSection
            title={
              <>
                <Translate contentKey="fintrackApp.category.children">Subcategories</Translate> ({children.length})
              </>
            }
            dataCy="categoryChildrenSummary"
          >
            {children.length > 0 ? (
              <div className="list-group list-group-flush" data-cy="categoryChildren">
                {children.map(child => (
                  <div
                    key={child.id}
                    className="list-group-item px-0 d-flex align-items-center justify-content-between gap-3"
                    data-cy="categoryChildRow"
                  >
                    <Link
                      to={`/category/${child.id}`}
                      className="category-child-link"
                      data-cy="categoryChildLink"
                      onClick={event => event.stopPropagation()}
                    >
                      {child.name}
                    </Link>
                    <CategoryStatusBadge active={child.active} />
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted mb-0">
                <Translate contentKey="fintrackApp.category.noChildren">No subcategories.</Translate>
              </p>
            )}
          </ProductSection>
        </Col>
      </Row>
    </ProductPage>
  );
};

export default CategoryDetail;
