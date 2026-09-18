import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, DropdownItem } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './tag.reducer';
import { TagStatusBadge } from './tag-presentation';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductStatusHelp } from 'app/shared/ui/product-status-control';

export const TagDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const tagEntity = useAppSelector(state => state.tag.entity);
  return (
    <ProductPage>
      <ProductPageHeader
        headingId="tag-details-heading"
        dataCy="tagDetailsHeading"
        accentColor={tagEntity.color}
        accentDataCy="tagDetailColorAccent"
        title={tagEntity.name ?? <Translate contentKey="fintrackApp.tag.detail.title">Tag</Translate>}
        metadata={
          <>
            <TagStatusBadge active={tagEntity.active} />
            <ProductStatusHelp
              id="tag-detail-status-help"
              label={translate('fintrackApp.tag.status.label')}
              help={
                <Translate contentKey="fintrackApp.tag.status.help">
                  Inactive tags are kept for history and cannot be newly assigned until reactivated.
                </Translate>
              }
              dataCyPrefix="tagDetail"
            />
          </>
        }
        actions={
          <>
            <Button tag={Link} to="/tag" replace color="secondary" outline size="sm" data-cy="entityDetailsBackButton">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            <Button tag={Link} to={`/tag/${tagEntity.id}/edit`} replace color="primary" size="sm" data-cy="entityDetailsEditButton">
              <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
            </Button>
            <ProductActionsMenu label={translate('fintrackApp.tag.moreActions')} dataCy="tagDetailActionsMenu">
              <DropdownItem tag={Link} to={`/tag/${tagEntity.id}/delete`} className="text-danger" data-cy="entityDeleteButton">
                <Translate contentKey="entity.action.delete">Delete</Translate>
              </DropdownItem>
            </ProductActionsMenu>
          </>
        }
      />
      <ProductSection title={<Translate contentKey="fintrackApp.tag.description">Description</Translate>} dataCy="tagDetailDescription">
        <p className="mb-0">
          {tagEntity.description || <Translate contentKey="fintrackApp.tag.noDescription">No description provided.</Translate>}
        </p>
      </ProductSection>
    </ProductPage>
  );
};

export default TagDetail;
