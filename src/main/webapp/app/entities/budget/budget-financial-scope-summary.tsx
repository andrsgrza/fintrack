import React, { useEffect, useMemo, useState } from 'react';

import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';

import { BudgetScopePreview, BudgetType, previewBudgetScope } from './budget-api';
import { copy, userFacingError } from './budget-utils';

export type BudgetFinancialScope = {
  accountScopeMode: 'ALL' | 'SELECTED';
  accountIds: number[];
  categoryScopeMode: 'ALL' | 'SELECTED';
  categoryIds: number[];
  tagScopeMode: 'NONE' | 'SELECTED';
  tagIds: number[];
  tagMatchMode: 'ANY' | 'ALL';
};

export type BudgetCategoryCoverageState = 'NOT_REQUIRED' | 'LOADING' | 'READY' | 'INVALID' | 'UNAVAILABLE';

const namesFor = <T extends { id?: number; name?: string }>(ids: number[], references: T[]) =>
  ids.map(id => references.find(reference => reference.id === id)?.name).filter((name): name is string => !!name);

const categoryPath = (category: BudgetScopePreview['selectedCategories'][number]) => category.path.join(' › ') || category.name;

/**
 * Shared, read-only scope presentation. Category expansion always comes from the server's semantic reference catalog;
 * React only renders the resolved result, so it cannot disagree with financial descendant matching.
 */
export const BudgetFinancialScopeSummary = ({
  locale,
  type,
  scope,
  accounts,
  tags,
  heading = true,
  dataCy = 'budgetFinancialScopeSummary',
  onCategoryCoverageStateChange,
}: {
  locale: string;
  type: BudgetType;
  scope: BudgetFinancialScope;
  accounts: IFinancialAccountSelectable[];
  tags: ITagSelectable[];
  heading?: boolean;
  dataCy?: string;
  onCategoryCoverageStateChange?: (state: BudgetCategoryCoverageState) => void;
}) => {
  const categoryIdsKey = useMemo(
    () => (scope.categoryScopeMode === 'SELECTED' ? [...scope.categoryIds].sort((left, right) => left - right).join(',') : ''),
    [scope.categoryIds, scope.categoryScopeMode],
  );
  const [categoryCoverage, setCategoryCoverage] = useState<BudgetScopePreview | null>(null);
  const [coverageError, setCoverageError] = useState<string | null>(null);
  const [coverageLoading, setCoverageLoading] = useState(false);

  useEffect(() => {
    if (scope.categoryScopeMode !== 'SELECTED' || !categoryIdsKey) {
      setCategoryCoverage(null);
      setCoverageError(null);
      setCoverageLoading(false);
      onCategoryCoverageStateChange?.(scope.categoryScopeMode === 'SELECTED' ? 'INVALID' : 'NOT_REQUIRED');
      return;
    }
    const controller = new AbortController();
    const ids = categoryIdsKey.split(',').map(Number);
    setCoverageLoading(true);
    setCoverageError(null);
    onCategoryCoverageStateChange?.('LOADING');
    void previewBudgetScope(type, ids, controller.signal)
      .then(next => {
        if (!controller.signal.aborted) {
          setCategoryCoverage(next);
          onCategoryCoverageStateChange?.(next.missingCategoryIds.length || next.incompatibleCategoryIds.length ? 'INVALID' : 'READY');
        }
      })
      .catch(problem => {
        if (!controller.signal.aborted) {
          setCategoryCoverage(null);
          setCoverageError(
            userFacingError(
              problem,
              copy(
                locale,
                'No se pudo comprobar la cobertura actual de categorías.',
                'The current category coverage could not be checked.',
              ),
            ),
          );
          onCategoryCoverageStateChange?.('UNAVAILABLE');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setCoverageLoading(false);
      });
    return () => controller.abort();
  }, [categoryIdsKey, locale, onCategoryCoverageStateChange, scope.categoryScopeMode, type]);

  const selectedAccounts = scope.accountIds
    .map(id => accounts.find(account => account.id === id))
    .filter((account): account is IFinancialAccountSelectable => !!account)
    .map(formatFinancialAccountLabel);
  const selectedTags = namesFor(scope.tagIds, tags);
  const unresolvedAccounts = Math.max(0, scope.accountIds.length - selectedAccounts.length);
  const unresolvedTags = Math.max(0, scope.tagIds.length - selectedTags.length);
  const selectedCategories = categoryCoverage?.selectedCategories ?? [];
  const effectiveCategories = categoryCoverage?.effectiveCategories ?? [];

  return (
    <section data-cy={dataCy} aria-busy={coverageLoading}>
      {heading ? <h4 className="h6 mb-3">{copy(locale, 'Alcance financiero', 'Financial scope')}</h4> : null}
      <dl className="row g-2 mb-0 small">
        <dt className="col-12 col-md-3 text-muted">{copy(locale, 'Cuentas', 'Accounts')}</dt>
        <dd className="col-12 col-md-9">
          {scope.accountScopeMode === 'ALL' ? (
            <span>{copy(locale, 'Todas las cuentas compatibles de esta moneda.', 'All compatible accounts in this currency.')}</span>
          ) : (
            <>
              <span>
                {selectedAccounts.length
                  ? selectedAccounts.join(', ')
                  : copy(locale, 'Sin cuentas seleccionadas.', 'No accounts selected.')}
              </span>
              {unresolvedAccounts ? (
                <span className="text-danger d-block mt-1">
                  {copy(
                    locale,
                    `${unresolvedAccounts} cuenta${unresolvedAccounts === 1 ? '' : 's'} no está disponible.`,
                    `${unresolvedAccounts} account${unresolvedAccounts === 1 ? '' : 's'} is unavailable.`,
                  )}
                </span>
              ) : null}
            </>
          )}
        </dd>

        <dt className="col-12 col-md-3 text-muted">{copy(locale, 'Categorías', 'Categories')}</dt>
        <dd className="col-12 col-md-9">
          {scope.categoryScopeMode === 'ALL' ? (
            <>
              <span>{copy(locale, 'Todas las categorías compatibles.', 'All compatible categories.')}</span>
              <span className="text-muted d-block mt-1">
                {copy(
                  locale,
                  'Las categorías incluidas se determinan según la jerarquía vigente.',
                  'Included categories are determined by the current hierarchy.',
                )}
              </span>
            </>
          ) : !scope.categoryIds.length ? (
            <span className="text-danger">{copy(locale, 'Selecciona al menos una categoría.', 'Select at least one category.')}</span>
          ) : coverageLoading ? (
            <span className="text-muted">{copy(locale, 'Comprobando categorías incluidas…', 'Checking included categories…')}</span>
          ) : coverageError ? (
            <span className="text-danger" role="alert">
              {coverageError}
            </span>
          ) : categoryCoverage?.missingCategoryIds.length ? (
            <span className="text-danger" role="alert">
              {copy(
                locale,
                'Una categoría seleccionada ya no está disponible. Revisa el alcance antes de guardar.',
                'A selected category is no longer available. Review the scope before saving.',
              )}
            </span>
          ) : categoryCoverage?.incompatibleCategoryIds.length ? (
            <span className="text-danger" role="alert">
              {copy(
                locale,
                'Una categoría seleccionada no es compatible con este presupuesto. Revisa el alcance antes de guardar.',
                'A selected category is not compatible with this budget. Review the scope before saving.',
              )}
            </span>
          ) : (
            <>
              <div>
                <span className="text-muted d-block mb-1">{copy(locale, 'Seleccionadas', 'Selected')}</span>
                {selectedCategories.map(category => (
                  <span className="d-block" key={category.id}>
                    {categoryPath(category)}
                    {category.active === false ? ` · ${copy(locale, 'inactiva', 'inactive')}` : ''}
                  </span>
                ))}
              </div>
              <div className="mt-2">
                <strong>
                  {copy(
                    locale,
                    `${selectedCategories.length} categoría${selectedCategories.length === 1 ? '' : 's'} seleccionada${selectedCategories.length === 1 ? '' : 's'} · ${effectiveCategories.length} categoría${effectiveCategories.length === 1 ? '' : 's'} incluida${effectiveCategories.length === 1 ? '' : 's'}`,
                    `${selectedCategories.length} selected categor${selectedCategories.length === 1 ? 'y' : 'ies'} · ${effectiveCategories.length} included categor${effectiveCategories.length === 1 ? 'y' : 'ies'}`,
                  )}
                </strong>
                <span className="text-muted d-block mt-1">
                  {copy(
                    locale,
                    'Las categorías incluidas se determinan según la jerarquía vigente.',
                    'Included categories are determined by the current hierarchy.',
                  )}
                </span>
                <details className="mt-2" data-cy="budgetEffectiveCategories">
                  <summary>
                    {copy(
                      locale,
                      `Ver categorías incluidas (${effectiveCategories.length})`,
                      `View included categories (${effectiveCategories.length})`,
                    )}
                  </summary>
                  <ul className="mb-0 mt-2 ps-3">
                    {effectiveCategories.map(category => (
                      <li key={category.id}>
                        {categoryPath(category)}
                        {category.active === false ? ` · ${copy(locale, 'inactiva', 'inactive')}` : ''}
                      </li>
                    ))}
                  </ul>
                </details>
              </div>
            </>
          )}
        </dd>

        <dt className="col-12 col-md-3 text-muted">{copy(locale, 'Etiquetas', 'Tags')}</dt>
        <dd className="col-12 col-md-9 mb-0">
          {scope.tagScopeMode === 'NONE' ? (
            <span>{copy(locale, 'Sin filtro de etiquetas.', 'No tag filter.')}</span>
          ) : (
            <>
              <span>
                {scope.tagMatchMode === 'ALL'
                  ? copy(locale, 'Todas las etiquetas elegidas: ', 'All selected tags: ')
                  : copy(locale, 'Cualquiera de las etiquetas elegidas: ', 'Any selected tag: ')}
                {selectedTags.length ? selectedTags.join(', ') : copy(locale, 'sin etiquetas seleccionadas', 'no selected tags')}
              </span>
              {unresolvedTags ? (
                <span className="text-danger d-block mt-1">
                  {copy(
                    locale,
                    `${unresolvedTags} etiqueta${unresolvedTags === 1 ? '' : 's'} no está disponible.`,
                    `${unresolvedTags} tag${unresolvedTags === 1 ? '' : 's'} is unavailable.`,
                  )}
                </span>
              ) : null}
            </>
          )}
        </dd>
      </dl>
    </section>
  );
};
