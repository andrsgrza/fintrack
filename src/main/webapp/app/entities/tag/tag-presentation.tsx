import React from 'react';
import { Badge } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

import { ITag } from 'app/shared/model/tag.model';

export const TagStatusBadge = ({ active }: { active?: boolean }) => (
  <Badge color={active ? 'success' : 'secondary'} pill data-cy="tagStatusBadge" data-testid="tagStatusBadge">
    <Translate contentKey={active ? 'fintrackApp.tag.status.active' : 'fintrackApp.tag.status.inactive'}>
      {active ? 'Active' : 'Inactive'}
    </Translate>
  </Badge>
);

export const TagColorChip = ({ tag }: { tag: ITag }) => (
  <span
    className="d-inline-flex align-items-center rounded-2 border px-2 py-1 small fw-semibold"
    data-cy="tagColorChip"
    data-testid="tagColorChip"
    aria-label={tag.color ? `${tag.name}: ${translate('fintrackApp.tag.colorSwatch', { color: tag.color })}` : tag.name}
    style={
      tag.color
        ? {
            borderColor: `${tag.color}80`,
            borderInlineStart: `0.3rem solid ${tag.color}`,
            backgroundColor: `${tag.color}1A`,
          }
        : undefined
    }
  >
    {tag.name}
  </span>
);
