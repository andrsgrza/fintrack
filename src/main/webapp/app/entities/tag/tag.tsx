import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, DropdownItem } from 'reactstrap';
import { Translate, getSortState, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './tag.reducer';
import { TagColorChip, TagStatusBadge } from './tag-presentation';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

export const Tag = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'name'), pageLocation.search));

  const tagList = useAppSelector(state => state.tag.entities);
  const loading = useAppSelector(state => state.tag.loading);

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
        headingId="tag-heading"
        dataCy="TagHeading"
        title={<Translate contentKey="fintrackApp.tag.home.title">Tags</Translate>}
        subtitle={<Translate contentKey="fintrackApp.tag.home.productSubtitle">Group transactions with useful labels.</Translate>}
        actions={
          <>
            <Button color="secondary" outline size="sm" onClick={handleSyncList} disabled={loading}>
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.tag.home.refreshListLabel">Refresh List</Translate>
            </Button>
            <Button tag={Link} to="/tag/new" color="primary" size="sm" id="jh-create-entity" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.tag.home.createLabel">Create tag</Translate>
            </Button>
          </>
        }
      />
      {tagList && tagList.length > 0 ? (
        <>
          <div className="d-flex justify-content-end mb-2">
            <Button color="link" className="p-0 small text-decoration-none" onClick={sort('name')} data-cy="tagSortByName">
              <Translate contentKey="fintrackApp.tag.home.sortByName">Sort by name</Translate>{' '}
              <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
            </Button>
          </div>
          <div className="vstack gap-2" data-cy="tagProductList" data-testid="tagProductList">
            {tagList.map(tag => (
              <article key={tag.id} className="card border-0 shadow-sm" data-cy="entityTable">
                <div className="card-body p-3">
                  <div className="d-flex align-items-start gap-3">
                    <div className="flex-grow-1 min-w-0">
                      <div className="d-flex flex-wrap align-items-center gap-2 mb-1" data-cy="tagPrimaryRow">
                        <Link to={`/tag/${tag.id}`} className="d-inline-flex text-decoration-none" data-cy="entityDetailsLink">
                          <TagColorChip tag={tag} />
                        </Link>
                        <TagStatusBadge active={tag.active} />
                      </div>
                      {tag.description ? (
                        <p className="text-muted small mb-0" data-cy="tagDescription">
                          {tag.description}
                        </p>
                      ) : null}
                    </div>
                    <div className="d-flex align-items-center gap-1">
                      <Button
                        tag={Link}
                        to={`/tag/${tag.id}/edit`}
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
                      <ProductActionsMenu label={translate('fintrackApp.tag.moreActions')}>
                        <DropdownItem tag={Link} to={`/tag/${tag.id}`} data-cy="entityDetailsButton">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </DropdownItem>
                        <DropdownItem divider />
                        <DropdownItem tag={Link} to={`/tag/${tag.id}/delete`} className="text-danger" data-cy="entityDeleteButton">
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </DropdownItem>
                      </ProductActionsMenu>
                    </div>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </>
      ) : (
        !loading && (
          <ProductSection title={<Translate contentKey="fintrackApp.tag.home.title">Tags</Translate>} dataCy="tagEmptyState">
            <p className="text-muted mb-3">
              <Translate contentKey="fintrackApp.tag.home.notFound">No tags found</Translate>
            </p>
            <Button tag={Link} to="/tag/new" color="primary" size="sm">
              <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.tag.home.createLabel">Create tag</Translate>
            </Button>
          </ProductSection>
        )
      )}
    </ProductPage>
  );
};

export default Tag;
