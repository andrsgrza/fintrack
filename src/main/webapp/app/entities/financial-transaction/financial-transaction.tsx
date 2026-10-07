import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Col, Collapse, DropdownItem, Form, FormGroup, Input, Label, Row, Spinner } from 'reactstrap';
import { JhiItemCount, JhiPagination, TextFormat, Translate, getPaginationState, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/util/pagination.constants';
import { overridePaginationStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductTagSelector } from 'app/shared/ui/product-tag-selector';

import { getEntities } from './financial-transaction.reducer';
import { TransactionAccountLabel, TransactionAmount, TransactionCategory, TransactionClassification } from './transaction-presentation';

type TransactionListFilters = {
  description: string;
  transactionDateFrom: string;
  transactionDateTo: string;
  accountId: string;
  flow: string;
  categoryId: string;
  tagIds: string[];
};

const emptyFilters: TransactionListFilters = {
  description: '',
  transactionDateFrom: '',
  transactionDateTo: '',
  accountId: '',
  flow: '',
  categoryId: '',
  tagIds: [],
};

const filtersFromSearch = (search: string): TransactionListFilters => {
  const params = new URLSearchParams(search);
  return {
    description: params.get('description.contains') ?? '',
    transactionDateFrom: params.get('transactionDate.greaterThanOrEqual') ?? '',
    transactionDateTo: params.get('transactionDate.lessThanOrEqual') ?? '',
    accountId: params.get('accountId.equals') ?? '',
    flow: params.get('flow.equals') ?? '',
    categoryId: params.get('categoryId.equals') ?? '',
    tagIds: (params.get('tagsId.in') ?? '')
      .split(',')
      .map(value => value.trim())
      .filter(Boolean),
  };
};

const hasFilters = (filters: TransactionListFilters) =>
  Boolean(
    filters.description ||
      filters.transactionDateFrom ||
      filters.transactionDateTo ||
      filters.accountId ||
      filters.flow ||
      filters.categoryId ||
      filters.tagIds.length,
  );

const filtersToQuery = (filters: TransactionListFilters) => {
  const params = new URLSearchParams();
  if (filters.description.trim()) {
    params.set('description.contains', filters.description.trim());
  }
  if (filters.transactionDateFrom) {
    params.set('transactionDate.greaterThanOrEqual', filters.transactionDateFrom);
  }
  if (filters.transactionDateTo) {
    params.set('transactionDate.lessThanOrEqual', filters.transactionDateTo);
  }
  if (filters.accountId) {
    params.set('accountId.equals', filters.accountId);
  }
  if (filters.flow) {
    params.set('flow.equals', filters.flow);
  }
  if (filters.categoryId) {
    params.set('categoryId.equals', filters.categoryId);
  }
  if (filters.tagIds.length) {
    params.set('tagsId.in', filters.tagIds.join(','));
  }
  return params;
};

const listSearch = (filters: TransactionListFilters, page: number, sort: string, order: string) => {
  const params = filtersToQuery(filters);
  params.set('page', String(page));
  params.set(SORT, `${sort},${order}`);
  return `?${params.toString()}`;
};

const categoryOptionLabel = (category: ICategorySelectable) =>
  [
    category.parentCategoryName,
    category.name,
    category.active === false ? `(${translate('fintrackApp.category.status.inactive')})` : undefined,
  ]
    .filter(Boolean)
    .join(' › ');

export const FinancialTransaction = () => {
  const dispatch = useAppDispatch();
  const pageLocation = useLocation();
  const navigate = useNavigate();

  const paginationState = useMemo(
    () => overridePaginationStateWithQueryParams(getPaginationState(pageLocation, ITEMS_PER_PAGE, 'id'), pageLocation.search),
    [pageLocation],
  );
  const appliedFilters = useMemo(() => filtersFromSearch(pageLocation.search), [pageLocation.search]);
  const appliedFilterQuery = useMemo(() => filtersToQuery(appliedFilters).toString(), [appliedFilters]);
  const [filterDraft, setFilterDraft] = useState<TransactionListFilters>(appliedFilters);
  const [filtersOpen, setFiltersOpen] = useState(hasFilters(appliedFilters));
  const [selectableAccounts, setSelectableAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [selectableCategories, setSelectableCategories] = useState<ICategorySelectable[]>([]);
  const [selectableTags, setSelectableTags] = useState<ITagSelectable[]>([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [tagsLoadError, setTagsLoadError] = useState(false);

  const financialTransactionList = useAppSelector(state => state.financialTransaction.entities);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const errorMessage = useAppSelector(state => state.financialTransaction.errorMessage);
  const totalItems = useAppSelector(state => state.financialTransaction.totalItems);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        page: paginationState.activePage - 1,
        size: paginationState.itemsPerPage,
        sort: `${paginationState.sort},${paginationState.order}`,
        query: appliedFilterQuery,
      }),
    );
  };

  const navigateToList = (
    filters = appliedFilters,
    page = paginationState.activePage,
    sort = paginationState.sort,
    order = paginationState.order,
  ) => {
    const nextSearch = listSearch(filters, page, sort, order);
    if (pageLocation.search !== nextSearch) {
      navigate(`${pageLocation.pathname}${nextSearch}`);
    } else {
      getAllEntities();
    }
  };

  useEffect(() => {
    getAllEntities();
  }, [appliedFilterQuery, dispatch, paginationState.activePage, paginationState.itemsPerPage, paginationState.order, paginationState.sort]);

  useEffect(() => {
    setFilterDraft(appliedFilters);
    if (hasFilters(appliedFilters)) {
      setFiltersOpen(true);
    }
  }, [pageLocation.search]);

  useEffect(() => {
    let mounted = true;
    const accountId = filterDraft.accountId ? Number(filterDraft.accountId) : undefined;
    const categoryIds = filterDraft.categoryId ? [Number(filterDraft.categoryId)] : [];
    const tagIds = filterDraft.tagIds.map(Number).filter(Number.isFinite);

    setTagsLoading(true);
    setTagsLoadError(false);
    Promise.all([getSelectableFinancialAccounts(accountId), getSelectableCategories(categoryIds), getSelectableTags(tagIds)])
      .then(([accounts, categories, tags]) => {
        if (mounted) {
          setSelectableAccounts(accounts);
          setSelectableCategories(categories);
          setSelectableTags(tags);
        }
      })
      .catch(() => {
        if (mounted) {
          setSelectableAccounts([]);
          setSelectableCategories([]);
          setTagsLoadError(true);
        }
      })
      .finally(() => {
        if (mounted) {
          setTagsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [filterDraft.accountId, filterDraft.categoryId, filterDraft.tagIds]);

  const sortByTransactionDate = () => {
    navigateToList(
      appliedFilters,
      paginationState.activePage,
      'transactionDate',
      paginationState.sort === 'transactionDate' && paginationState.order === ASC ? DESC : ASC,
    );
  };

  const handlePagination = currentPage => navigateToList(appliedFilters, currentPage);

  const submitFilters = event => {
    event.preventDefault();
    navigateToList(filterDraft, 1);
  };

  const clearFilters = () => {
    setFilterDraft(emptyFilters);
    navigateToList(emptyFilters, 1);
  };

  const selectedCategory = selectableCategories.find(category => category.id?.toString() === filterDraft.categoryId);
  const selectedTags = filterDraft.tagIds
    .map(tagId => selectableTags.find(tag => tag.id?.toString() === tagId))
    .filter((tag): tag is ITagSelectable => !!tag);

  const transactionDateSortIcon =
    paginationState.sort !== 'transactionDate' ? faSort : paginationState.order === ASC ? faSortUp : faSortDown;

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="financial-transaction-heading"
        dataCy="FinancialTransactionHeading"
        title={<Translate contentKey="fintrackApp.financialTransaction.home.title">Transactions</Translate>}
        actions={
          <>
            <Button tag={Link} to="/financial-transaction/drafts" color="secondary" outline size="sm" data-cy="manualDraftsButton">
              <FontAwesomeIcon icon="list" />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.home.viewDraftsLabel">Drafts</Translate>
            </Button>
            <Button color="secondary" outline size="sm" onClick={getAllEntities} disabled={loading} data-cy="refreshTransactionList">
              <FontAwesomeIcon icon="sync" spin={loading} />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.home.refreshListLabel">Refresh</Translate>
            </Button>
            <Button tag={Link} to="/financial-transaction/new" color="primary" size="sm" id="jh-create-entity" data-cy="entityCreateButton">
              <FontAwesomeIcon icon="plus" />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.home.createLabel">New transaction</Translate>
            </Button>
          </>
        }
      />

      <Form className="border rounded-3 bg-white p-3 mb-4" onSubmit={submitFilters} data-cy="financialTransactionFilters">
        <div className="d-flex flex-column flex-md-row align-items-md-center gap-2">
          <FormGroup noMargin className="flex-grow-1">
            <Label className="visually-hidden" for="financial-transaction-search">
              <Translate contentKey="fintrackApp.financialTransaction.product.searchDescription">Search description</Translate>
            </Label>
            <Input
              id="financial-transaction-search"
              data-cy="financialTransactionSearch"
              type="search"
              placeholder={translate('fintrackApp.financialTransaction.product.searchDescription')}
              value={filterDraft.description}
              onChange={event => setFilterDraft(current => ({ ...current, description: event.target.value }))}
            />
          </FormGroup>
          <div className="d-flex flex-wrap gap-2">
            <Button color="primary" size="sm" type="submit" data-cy="financialTransactionSearchSubmit">
              <Translate contentKey="fintrackApp.financialTransaction.product.search">Search</Translate>
            </Button>
            <Button
              color="secondary"
              outline
              size="sm"
              type="button"
              onClick={() => setFiltersOpen(open => !open)}
              aria-expanded={filtersOpen}
              data-cy="financialTransactionFiltersToggle"
            >
              <Translate contentKey="fintrackApp.financialTransaction.product.filters">Filters</Translate>
            </Button>
            {hasFilters(appliedFilters) || hasFilters(filterDraft) ? (
              <Button
                color="link"
                className="p-0 align-self-center text-decoration-none"
                type="button"
                onClick={clearFilters}
                data-cy="financialTransactionClearFilters"
              >
                <Translate contentKey="fintrackApp.financialTransaction.product.clearFilters">Clear filters</Translate>
              </Button>
            ) : null}
          </div>
        </div>

        <Collapse isOpen={filtersOpen}>
          <Row className="g-3 pt-3" data-cy="financialTransactionFilterFields">
            <Col md="6" lg="3">
              <Label for="financial-transaction-filter-date-from">
                <Translate contentKey="fintrackApp.financialTransaction.product.transactionDateFrom">Transaction date from</Translate>
              </Label>
              <Input
                id="financial-transaction-filter-date-from"
                data-cy="financialTransactionFilterDateFrom"
                type="date"
                value={filterDraft.transactionDateFrom}
                onChange={event => setFilterDraft(current => ({ ...current, transactionDateFrom: event.target.value }))}
              />
            </Col>
            <Col md="6" lg="3">
              <Label for="financial-transaction-filter-date-to">
                <Translate contentKey="fintrackApp.financialTransaction.product.transactionDateTo">Transaction date to</Translate>
              </Label>
              <Input
                id="financial-transaction-filter-date-to"
                data-cy="financialTransactionFilterDateTo"
                type="date"
                value={filterDraft.transactionDateTo}
                onChange={event => setFilterDraft(current => ({ ...current, transactionDateTo: event.target.value }))}
              />
            </Col>
            <Col md="6" lg="3">
              <Label for="financial-transaction-filter-account">
                <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>
              </Label>
              <Input
                id="financial-transaction-filter-account"
                data-cy="financialTransactionFilterAccount"
                type="select"
                value={filterDraft.accountId}
                onChange={event => setFilterDraft(current => ({ ...current, accountId: event.target.value }))}
              >
                <option value="" />
                {selectableAccounts.map(account => (
                  <option key={account.id} value={account.id}>
                    {formatFinancialAccountLabel(account)}
                  </option>
                ))}
              </Input>
            </Col>
            <Col md="6" lg="3">
              <Label for="financial-transaction-filter-flow">
                <Translate contentKey="fintrackApp.financialTransaction.flow">Type</Translate>
              </Label>
              <Input
                id="financial-transaction-filter-flow"
                data-cy="financialTransactionFilterFlow"
                type="select"
                value={filterDraft.flow}
                onChange={event => setFilterDraft(current => ({ ...current, flow: event.target.value }))}
              >
                <option value="" />
                <option value="IN">{translate('fintrackApp.TransactionFlow.IN')}</option>
                <option value="OUT">{translate('fintrackApp.TransactionFlow.OUT')}</option>
              </Input>
            </Col>
            <Col md="6">
              <Label for="financial-transaction-filter-category">
                <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate>
              </Label>
              <Input
                id="financial-transaction-filter-category"
                data-cy="financialTransactionFilterCategory"
                type="select"
                value={filterDraft.categoryId}
                onChange={event => setFilterDraft(current => ({ ...current, categoryId: event.target.value }))}
              >
                <option value="" />
                {selectableCategories.map(category => (
                  <option key={category.id} value={category.id}>
                    {categoryOptionLabel(category)}
                  </option>
                ))}
              </Input>
              {selectedCategory ? (
                <div
                  className="pt-2"
                  data-cy="financialTransactionFilterCategoryIdentity"
                  data-testid="financialTransactionFilterCategoryIdentity"
                >
                  <TransactionCategory
                    draftCategory={{
                      id: selectedCategory.id,
                      name: selectedCategory.name,
                      parentName: selectedCategory.parentCategoryName,
                      color: selectedCategory.color,
                      active: selectedCategory.active,
                    }}
                  />
                </div>
              ) : null}
            </Col>
            <Col md="6">
              <div className="small fw-semibold mb-2">
                <Translate contentKey="fintrackApp.financialTransaction.tags">Tags</Translate>
              </div>
              <ProductTagSelector
                id="financial-transaction-filter-tags"
                dataCy="financialTransactionFilterTags"
                selectedTags={selectedTags}
                availableTags={selectableTags}
                loading={tagsLoading}
                error={tagsLoadError}
                onChange={nextTags =>
                  setFilterDraft(current => ({
                    ...current,
                    tagIds: nextTags.map(tag => tag.id?.toString()).filter((tagId): tagId is string => !!tagId),
                  }))
                }
              />
            </Col>
          </Row>
        </Collapse>
      </Form>

      {loading && !financialTransactionList.length ? (
        <div className="d-flex align-items-center gap-2 text-muted py-4" data-cy="financialTransactionListLoading">
          <Spinner size="sm" />
          <Translate contentKey="fintrackApp.financialTransaction.product.loading">Loading transactions…</Translate>
        </div>
      ) : null}

      {errorMessage ? (
        <Alert color="danger" fade={false} data-cy="financialTransactionListError">
          <Translate contentKey="fintrackApp.financialTransaction.product.loadFailed">Transactions could not be loaded.</Translate>
        </Alert>
      ) : null}

      {!loading && !errorMessage && financialTransactionList.length === 0 ? (
        <ProductSection title={<Translate contentKey="fintrackApp.financialTransaction.product.emptyTitle">No transactions yet</Translate>}>
          <p className="text-muted mb-3">
            <Translate contentKey="fintrackApp.financialTransaction.product.emptyDescription">
              Create a transaction to start tracking this account activity.
            </Translate>
          </p>
          <Button tag={Link} to="/financial-transaction/new" color="primary" size="sm" data-cy="financialTransactionEmptyCreateButton">
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.financialTransaction.home.createLabel">New transaction</Translate>
          </Button>
        </ProductSection>
      ) : null}

      {financialTransactionList.length > 0 ? (
        <div className="vstack gap-2" data-cy="financialTransactionProductList">
          <div className="d-flex justify-content-end">
            <Button
              color="link"
              className="p-0 text-decoration-none small text-muted"
              type="button"
              onClick={sortByTransactionDate}
              data-cy="financialTransactionDateSort"
            >
              <Translate contentKey="fintrackApp.financialTransaction.product.sortByDate">Sort by transaction date</Translate>{' '}
              <FontAwesomeIcon icon={transactionDateSortIcon} />
            </Button>
          </div>
          {financialTransactionList.map(financialTransaction => (
            <article
              key={financialTransaction.id}
              className="border rounded-3 bg-white p-3 p-md-4"
              data-cy="entityTable"
              data-testid="financialTransactionRow"
            >
              <div className="d-flex flex-column flex-md-row align-items-md-start gap-3">
                <div className="flex-grow-1 min-w-0">
                  <Link
                    to={`/financial-transaction/${financialTransaction.id}`}
                    className="h5 d-inline-block mb-2 text-decoration-none text-body"
                    data-cy="entityDetailsButton"
                  >
                    {financialTransaction.description}
                  </Link>
                  <div className="d-flex flex-wrap gap-2 small text-muted mb-3">
                    {financialTransaction.transactionDate ? (
                      <span>
                        <TextFormat type="date" value={financialTransaction.transactionDate} format={APP_LOCAL_DATE_FORMAT} />
                      </span>
                    ) : null}
                    {financialTransaction.transactionDate && financialTransaction.account ? <span>·</span> : null}
                    <TransactionAccountLabel account={financialTransaction.account} className="text-muted" />
                  </div>
                  <TransactionClassification category={financialTransaction.category} tags={financialTransaction.tags} />
                </div>

                <div className="d-flex align-items-start justify-content-between gap-3 flex-shrink-0">
                  <TransactionAmount
                    amount={financialTransaction.amount}
                    currency={financialTransaction.account?.currency}
                    flow={financialTransaction.flow}
                  />
                  <div className="d-flex align-items-center gap-1">
                    <Button
                      tag={Link}
                      to={`/financial-transaction/${financialTransaction.id}/edit?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`}
                      color="secondary"
                      outline
                      size="sm"
                      aria-label={translate('entity.action.edit')}
                      title={translate('entity.action.edit')}
                      data-cy="entityEditButton"
                    >
                      <FontAwesomeIcon icon="pencil-alt" />
                    </Button>
                    <ProductActionsMenu
                      label={translate('fintrackApp.financialTransaction.product.moreActions')}
                      dataCy="financialTransactionActionsMenu"
                    >
                      <DropdownItem
                        tag={Link}
                        to={`/financial-transaction/${financialTransaction.id}/delete?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`}
                        className="text-danger"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" className="me-2" />
                        <Translate contentKey="entity.action.delete">Delete</Translate>
                      </DropdownItem>
                    </ProductActionsMenu>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : null}

      {totalItems ? (
        <div className={financialTransactionList.length > 0 ? 'pt-4' : 'd-none'} data-cy="financialTransactionPagination">
          <div className="justify-content-center d-flex small text-muted">
            <JhiItemCount page={paginationState.activePage} total={totalItems} itemsPerPage={paginationState.itemsPerPage} i18nEnabled />
          </div>
          <div className="justify-content-center d-flex">
            <JhiPagination
              activePage={paginationState.activePage}
              onSelect={handlePagination}
              maxButtons={5}
              itemsPerPage={paginationState.itemsPerPage}
              totalItems={totalItems}
            />
          </div>
        </div>
      ) : null}
    </ProductPage>
  );
};

export default FinancialTransaction;
