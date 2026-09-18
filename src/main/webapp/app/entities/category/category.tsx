import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, ButtonGroup, DropdownItem } from 'reactstrap';
import { Translate, getSortState, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faChevronDown, faChevronRight, faList, faSitemap, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './category.reducer';
import {
  CategoryStatusBadge,
  CategoryTypeLabel,
  CategoryPath,
  getCategoryColorAccentStyle,
  getCategoryHierarchyRows,
  getCategoryPathLabel,
} from './category-presentation';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

export const Category = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'name'), pageLocation.search));
  const [viewMode, setViewMode] = useState<'nested' | 'flat'>('nested');
  const [collapsedCategoryIds, setCollapsedCategoryIds] = useState<Set<number>>(() => new Set());

  const categoryList = useAppSelector(state => state.category.entities);
  const loading = useAppSelector(state => state.category.loading);
  const categoryHierarchyRows = getCategoryHierarchyRows(categoryList);
  const visibleNestedRows = categoryHierarchyRows.filter(row => row.ancestorIds.every(ancestorId => !collapsedCategoryIds.has(ancestorId)));
  const visibleRows =
    viewMode === 'nested' ? visibleNestedRows : categoryList.map(category => ({ category, depth: 0, hasChildren: false }));

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const toggleCategory = (categoryId: number) => {
    setCollapsedCategoryIds(current => {
      const next = new Set(current);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
      return next;
    });
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const order = sortState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="category-heading"
        dataCy="CategoryHeading"
        title={<Translate contentKey="fintrackApp.category.home.title">Categories</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.category.home.productSubtitle">Organize how your transactions are classified.</Translate>
        }
        actions={
          <>
            <Button color="secondary" outline size="sm" onClick={handleSyncList} disabled={loading}>
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.category.home.refreshListLabel">Refresh List</Translate>
            </Button>
            <Button tag={Link} to="/category/new" color="primary" size="sm" id="jh-create-entity" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.category.home.createLabel">Create category</Translate>
            </Button>
          </>
        }
      />
      {categoryList && categoryList.length > 0 ? (
        <>
          <div className="d-flex flex-wrap justify-content-end align-items-center gap-2 mb-3">
            <ButtonGroup size="sm" aria-label={translate('fintrackApp.category.home.viewMode')} data-cy="categoryViewModeToggle">
              <Button
                color={viewMode === 'nested' ? 'primary' : 'secondary'}
                outline={viewMode !== 'nested'}
                onClick={() => setViewMode('nested')}
                aria-pressed={viewMode === 'nested'}
                data-cy="categoryViewNested"
              >
                <FontAwesomeIcon icon={faSitemap} className="me-1" />
                <Translate contentKey="fintrackApp.category.home.nestedView">Nested</Translate>
              </Button>
              <Button
                color={viewMode === 'flat' ? 'primary' : 'secondary'}
                outline={viewMode !== 'flat'}
                onClick={() => setViewMode('flat')}
                aria-pressed={viewMode === 'flat'}
                data-cy="categoryViewFlat"
              >
                <FontAwesomeIcon icon={faList} className="me-1" />
                <Translate contentKey="fintrackApp.category.home.flatView">Flat</Translate>
              </Button>
            </ButtonGroup>
            <Button color="link" className="p-0 small text-decoration-none" onClick={sort('name')} data-cy="categorySortByName">
              <Translate contentKey="fintrackApp.category.home.sortByName">Sort by name</Translate>{' '}
              <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
            </Button>
          </div>
          <div className="vstack gap-2" data-cy="categoryProductList" data-testid="categoryProductList">
            {visibleRows.map(({ category, depth, hasChildren }) => {
              const isCollapsed = category.id !== undefined && collapsedCategoryIds.has(category.id);
              const pathLabel = viewMode === 'flat' ? getCategoryPathLabel(category, categoryList) : '';
              const isNestedChild = viewMode === 'nested' && depth > 0;

              return (
                <article
                  key={category.id}
                  className="card shadow-sm"
                  data-cy="entityTable"
                  data-category-depth={depth}
                  data-category-view={viewMode}
                  style={{
                    ...getCategoryColorAccentStyle(category),
                    marginInlineStart: isNestedChild ? `min(calc(${depth} * 1rem), 16vw)` : undefined,
                  }}
                >
                  <div className="card-body p-3">
                    <div className="d-flex align-items-start gap-3">
                      {viewMode === 'nested' ? (
                        <span
                          className="category-disclosure-slot d-inline-flex flex-shrink-0 align-items-center justify-content-center pt-1"
                          data-cy="categoryDisclosureSlot"
                        >
                          {hasChildren && category.id !== undefined ? (
                            <Button
                              color="link"
                              className="p-0 text-body"
                              onClick={() => toggleCategory(category.id)}
                              aria-expanded={!isCollapsed}
                              aria-label={translate(
                                isCollapsed ? 'fintrackApp.category.home.expandCategory' : 'fintrackApp.category.home.collapseCategory',
                                { name: category.name },
                              )}
                              title={translate(
                                isCollapsed ? 'fintrackApp.category.home.expandCategory' : 'fintrackApp.category.home.collapseCategory',
                                { name: category.name },
                              )}
                              data-cy="categoryDisclosure"
                            >
                              <FontAwesomeIcon icon={isCollapsed ? faChevronRight : faChevronDown} />
                            </Button>
                          ) : (
                            <span aria-hidden="true" data-cy="categoryDisclosurePlaceholder" />
                          )}
                        </span>
                      ) : null}
                      <div className="flex-grow-1 min-w-0" data-cy="categoryNameContent">
                        <Link
                          to={`/category/${category.id}`}
                          className="h5 d-inline-block mb-1 text-decoration-none"
                          data-cy="entityDetailsLink"
                        >
                          {category.name}
                        </Link>
                        {viewMode === 'flat' && pathLabel && pathLabel !== category.name ? (
                          <p className="text-muted small mb-1 text-break">
                            <CategoryPath category={category} categories={categoryList} dataCy="categoryFlatPath" />
                          </p>
                        ) : null}
                        {category.description ? <p className="text-muted small mb-2">{category.description}</p> : null}
                        <div className="d-flex flex-wrap align-items-center gap-2 small">
                          <CategoryTypeLabel categoryType={category.categoryType} />
                          <CategoryStatusBadge active={category.active} />
                        </div>
                      </div>
                      <div className="d-flex align-items-center gap-1">
                        <Button
                          tag={Link}
                          to={`/category/${category.id}/edit`}
                          color="primary"
                          outline
                          size="sm"
                          data-cy="entityEditButton"
                          aria-label={translate('entity.action.edit')}
                          title={translate('entity.action.edit')}
                        >
                          <FontAwesomeIcon icon="pencil-alt" />
                          <span className="visually-hidden">
                            <Translate contentKey="entity.action.edit">Edit</Translate>
                          </span>
                        </Button>
                        <ProductActionsMenu label={translate('fintrackApp.category.moreActions')}>
                          <DropdownItem tag={Link} to={`/category/${category.id}`} data-cy="entityDetailsButton">
                            <Translate contentKey="entity.action.view">View</Translate>
                          </DropdownItem>
                          <DropdownItem divider />
                          <DropdownItem
                            tag={Link}
                            to={`/category/${category.id}/delete`}
                            className="text-danger"
                            data-cy="entityDeleteButton"
                          >
                            <Translate contentKey="entity.action.delete">Delete</Translate>
                          </DropdownItem>
                        </ProductActionsMenu>
                      </div>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </>
      ) : (
        !loading && (
          <ProductSection
            title={<Translate contentKey="fintrackApp.category.home.title">Categories</Translate>}
            dataCy="categoryEmptyState"
          >
            <p className="text-muted mb-3">
              <Translate contentKey="fintrackApp.category.home.notFound">No categories found</Translate>
            </p>
            <Button tag={Link} to="/category/new" color="primary" size="sm">
              <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.category.home.createLabel">Create category</Translate>
            </Button>
          </ProductSection>
        )
      )}
    </ProductPage>
  );
};

export default Category;
