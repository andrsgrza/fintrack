import React from 'react';
import { Link } from 'react-router-dom';
import { Badge } from 'reactstrap';
import { Translate } from 'react-jhipster';

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
    .join(' › ');

export const getImmediateChildren = (categoryIdValue?: number, categories: ReadonlyArray<ICategory> = []) =>
  categories.filter(category => category.parentCategory?.id === categoryIdValue);

export type CategoryHierarchyRow = {
  category: ICategory;
  depth: number;
  hasChildren: boolean;
  ancestorIds: number[];
};

/**
 * Flattens the current catalog in parent-before-child order while preserving the list's existing sibling order.
 * Missing parents and malformed cycles remain visible instead of silently disappearing from the catalog.
 */
export const getCategoryHierarchyRows = (categories: ReadonlyArray<ICategory> = []): CategoryHierarchyRow[] => {
  const knownIds = new Set(categories.map(category => category.id).filter((id): id is number => id !== undefined));
  const childrenByParent = new Map<number, ICategory[]>();
  const roots: ICategory[] = [];

  categories.forEach(category => {
    const parentId = category.parentCategory?.id;
    if (parentId === undefined || !knownIds.has(parentId)) {
      roots.push(category);
      return;
    }
    childrenByParent.set(parentId, [...(childrenByParent.get(parentId) ?? []), category]);
  });

  const rows: CategoryHierarchyRow[] = [];
  const visited = new Set<number>();
  const appendCategory = (category: ICategory, depth: number, ancestorIds: number[]) => {
    if (category.id !== undefined && visited.has(category.id)) {
      return;
    }
    if (category.id !== undefined) {
      visited.add(category.id);
    }
    const children = category.id !== undefined ? (childrenByParent.get(category.id) ?? []) : [];
    rows.push({ category, depth, hasChildren: children.length > 0, ancestorIds });
    if (category.id !== undefined) {
      children.forEach(child => appendCategory(child, depth + 1, [...ancestorIds, category.id]));
    }
  };

  roots.forEach(category => appendCategory(category, 0, []));
  categories.forEach(category => appendCategory(category, 0, []));
  return rows;
};

export const CategoryTypeLabel = ({ categoryType }: { categoryType?: ICategory['categoryType'] }) =>
  categoryType ? (
    <Badge color="light" className="border text-dark fw-normal" data-cy="categoryTypeBadge" data-testid="categoryTypeBadge">
      <Translate contentKey={`fintrackApp.CategoryType.${categoryType}`} />
    </Badge>
  ) : null;

export const CategoryStatusBadge = ({ active }: { active?: boolean }) => (
  <Badge color={active ? 'success' : 'secondary'} pill data-cy="categoryStatusBadge" data-testid="categoryStatusBadge">
    <Translate contentKey={active ? 'fintrackApp.category.status.active' : 'fintrackApp.category.status.inactive'}>
      {active ? 'Active' : 'Inactive'}
    </Translate>
  </Badge>
);

export const getCategoryColorAccentStyle = (category: ICategory): React.CSSProperties =>
  category.color
    ? {
        borderInlineStart: `0.4rem solid ${category.color}`,
      }
    : {};

export const CategoryPath = ({
  category,
  categories,
  linkCurrent = false,
  dataCy = 'categoryPath',
  className = '',
}: {
  category?: ICategory | null;
  categories: ReadonlyArray<ICategory>;
  linkCurrent?: boolean;
  dataCy?: string;
  className?: string;
}) => {
  const path = getCategoryPath(category, categories);

  if (path.length === 0) {
    return <span className="text-muted">—</span>;
  }

  return (
    <span className={className} data-cy={dataCy} data-testid={dataCy}>
      {path.map((item, index) => {
        const isCurrent = index === path.length - 1;
        return (
          <React.Fragment key={item.id ?? `${item.name}-${index}`}>
            {index > 0 ? <span className="text-muted category-breadcrumb-separator"> {'›'} </span> : null}
            {item.id !== undefined && (linkCurrent || !isCurrent) ? (
              <Link
                to={`/category/${item.id}`}
                className="category-breadcrumb-link"
                data-cy="categoryBreadcrumbAncestor"
                onClick={event => event.stopPropagation()}
              >
                {item.name}
              </Link>
            ) : (
              item.name
            )}
          </React.Fragment>
        );
      })}
    </span>
  );
};
