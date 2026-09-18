import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, DropdownItem, Spinner } from 'reactstrap';
import { JhiItemCount, JhiPagination, TextFormat, Translate, getPaginationState, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/util/pagination.constants';
import { overridePaginationStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { getEntities } from './financial-transaction.reducer';
import { TransactionAccountLabel, TransactionAmount, TransactionClassification } from './transaction-presentation';

export const FinancialTransaction = () => {
  const dispatch = useAppDispatch();
  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [paginationState, setPaginationState] = useState(
    overridePaginationStateWithQueryParams(getPaginationState(pageLocation, ITEMS_PER_PAGE, 'id'), pageLocation.search),
  );

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
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [paginationState.activePage, paginationState.order, paginationState.sort]);

  useEffect(() => {
    const params = new URLSearchParams(pageLocation.search);
    const page = params.get('page');
    const sort = params.get(SORT);
    if (page && sort) {
      const sortSplit = sort.split(',');
      setPaginationState({
        ...paginationState,
        activePage: +page,
        sort: sortSplit[0],
        order: sortSplit[1],
      });
    }
  }, [pageLocation.search]);

  const sortByTransactionDate = () => {
    setPaginationState({
      ...paginationState,
      order: paginationState.sort === 'transactionDate' && paginationState.order === ASC ? DESC : ASC,
      sort: 'transactionDate',
    });
  };

  const handlePagination = currentPage =>
    setPaginationState({
      ...paginationState,
      activePage: currentPage,
    });

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
            <Button color="secondary" outline size="sm" onClick={sortEntities} disabled={loading} data-cy="refreshTransactionList">
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
