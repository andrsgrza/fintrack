import React from 'react';
import { Link } from 'react-router-dom';
import { Badge } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

import { ICategory } from 'app/shared/model/category.model';

const categoryId = (category?: ICategory | null) => category?.id;

/**
 * Builds a hierarchy from the already loaded category catalog. The API provides an immediate parent and the catalog
 * is loaded once per page, so this does not require a request per ancestor.
 */
export const getCategoryPath = (category?: ICategory | null, categories: ReadonlyArray<ICategory> = []): ICategory[] => {
  if (!category) {
    return [];
  }

  const categoriesById = new Map(categories.filter(item => item.id !== undefined).map(item => [item.id, item]));
  const path: ICategory[] = [];
  const visited = new Set<number>();
  let current: ICategory | null | undefined = category;

  while (current) {
    const id = categoryId(current);
    if (id !== undefined && visited.has(id)) {
      break;
    }
    if (id !== undefined) {
      visited.add(id);
    }
    path.unshift(current);

    const parent = current.parentCategory;
    if (!parent) {
      break;
    }
    current = parent.id !== undefined ? (categoriesById.get(parent.id) ?? parent) : parent;
  }

  return path;
};

export const getCategoryPathLabel = (category?: ICategory | null, categories: ReadonlyArray<ICategory> = []) =>
  getCategoryPath(category, categories)
    .map(item => item.name)
    .filter((name): name is string => Boolean(name))
    .join(' > ');

export const getImmediateChildren = (categoryIdValue?: number, categories: ReadonlyArray<ICategory> = []) =>
  categories.filter(category => category.parentCategory?.id === categoryIdValue);

export const CategoryTypeLabel = ({ categoryType }: { categoryType?: ICategory['categoryType'] }) =>
  categoryType ? <Translate contentKey={`fintrackApp.CategoryType.${categoryType}`} /> : null;

export const CategoryStatusBadge = ({ active }: { active?: boolean }) => (
  <Badge color={active ? 'success' : 'secondary'} pill data-cy="categoryStatusBadge">
    <Translate contentKey={active ? 'fintrackApp.category.status.active' : 'fintrackApp.category.status.inactive'}>
      {active ? 'Active' : 'Inactive'}
    </Translate>
  </Badge>
);

export const CategoryAppearance = ({ category }: { category: ICategory }) => (
  <div className="d-flex flex-wrap align-items-center gap-2" data-cy="categoryAppearance">
    {category.color ? (
      <span
        className="d-inline-flex align-items-center gap-1"
        aria-label={translate('fintrackApp.category.colorSwatch', { color: category.color })}
      >
        <span
          aria-hidden="true"
          className="border rounded-circle d-inline-block"
          style={{ width: '1rem', height: '1rem', backgroundColor: category.color }}
        />
        <span>{category.color}</span>
      </span>
    ) : null}
    {category.icon ? (
      <Badge color="light" className="border text-dark fw-normal">
        {category.icon}
      </Badge>
    ) : null}
    {!category.color && !category.icon ? <span className="text-muted">—</span> : null}
  </div>
);

export const CategoryPath = ({
  category,
  categories,
  linkCurrent = false,
}: {
  category?: ICategory | null;
  categories: ReadonlyArray<ICategory>;
  linkCurrent?: boolean;
}) => {
  const path = getCategoryPath(category, categories);

  if (path.length === 0) {
    return <span className="text-muted">—</span>;
  }

  return (
    <span data-cy="categoryPath" data-testid="categoryPath">
      {path.map((item, index) => {
        const isCurrent = index === path.length - 1;
        return (
          <React.Fragment key={item.id ?? `${item.name}-${index}`}>
            {index > 0 ? <span className="text-muted"> &gt; </span> : null}
            {item.id !== undefined && (linkCurrent || !isCurrent) ? <Link to={`/category/${item.id}`}>{item.name}</Link> : item.name}
          </React.Fragment>
        );
      })}
    </span>
  );
};
