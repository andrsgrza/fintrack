import React, { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Col, DropdownItem, Form, FormGroup, Input, Label, Row, Spinner } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { FinancialAmount } from 'app/shared/ui/financial-amount';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { InternalTransferRead, getInternalTransfers } from './internal-transfer-product.service';
import { TransferLegSummary } from './internal-transfer-presentation';
import InternalTransferUnlinkModal from './internal-transfer-unlink-modal';

type Filters = {
  accountId: string;
  fromDate: string;
  toDate: string;
  sort: string;
};

const initialFilters: Filters = {
  accountId: '',
  fromDate: '',
  toDate: '',
  sort: 'outgoingTransaction.transactionDate,DESC',
};

const readFilters = (search: string): Filters => {
  const params = new URLSearchParams(search);
  return {
    accountId: params.get('accountId') ?? '',
    fromDate: params.get('fromDate') ?? '',
    toDate: params.get('toDate') ?? '',
    sort: params.get('sort') ?? initialFilters.sort,
  };
};

const writeFilters = (filters: Filters) => {
  const params = new URLSearchParams();
  if (filters.accountId) params.set('accountId', filters.accountId);
  if (filters.fromDate) params.set('fromDate', filters.fromDate);
  if (filters.toDate) params.set('toDate', filters.toDate);
  params.set('sort', filters.sort);
  return `?${params.toString()}`;
};

const TransferListItem = ({
  transfer,
  onUnlink,
}: {
  transfer: InternalTransferRead;
  onUnlink: (transfer: InternalTransferRead) => void;
}) => (
  <article className="border rounded-3 p-3 p-md-4" data-cy="internalTransferListItem">
    <div className="d-flex flex-column flex-lg-row gap-3">
      <div className="flex-shrink-0 text-lg-center" style={{ minWidth: '7rem' }}>
        <div className="text-uppercase small text-muted fw-semibold">
          <Translate contentKey="fintrackApp.internalTransfer.product.transactionDate">Transfer date</Translate>
        </div>
        <div className="fw-semibold">
          {transfer.transactionDate ? <TextFormat value={transfer.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : '—'}
        </div>
      </div>
      <div className="flex-grow-1">
        <Row className="g-2 align-items-stretch">
          <Col md="5">
            <TransferLegSummary leg={transfer.source} role="source" />
          </Col>
          <Col
            md="2"
            className="d-flex align-items-center justify-content-center text-muted fs-4"
            aria-label={translate('fintrackApp.internalTransfer.product.to')}
          >
            →
          </Col>
          <Col md="5">
            <TransferLegSummary leg={transfer.destination} role="destination" />
          </Col>
        </Row>
        {transfer.notes ? <p className="text-muted small mt-3 mb-0 text-break">{transfer.notes}</p> : null}
      </div>
      <div className="d-flex flex-lg-column align-items-lg-end justify-content-between gap-2 flex-shrink-0">
        <FinancialAmount amount={transfer.amount} currency={transfer.currency} compact dataCy="internalTransferAmount" />
        <div className="d-flex gap-2">
          <Button
            tag={Link}
            to={`/internal-transfers/${transfer.transferId}/edit`}
            color="primary"
            size="sm"
            data-cy="internalTransferEdit"
          >
            <FontAwesomeIcon icon="pencil-alt" className="me-1" />
            <Translate contentKey="fintrackApp.internalTransfer.product.edit">Edit internal transfer</Translate>
          </Button>
          <ProductActionsMenu label={translate('fintrackApp.internalTransfer.product.moreActions')} dataCy="internalTransferActions">
            <DropdownItem tag={Link} to={`/internal-transfers/${transfer.transferId}`}>
              <FontAwesomeIcon icon="eye" className="me-2" />
              <Translate contentKey="fintrackApp.internalTransfer.product.view">View internal transfer</Translate>
            </DropdownItem>
            <DropdownItem divider />
            <DropdownItem className="text-danger" onClick={() => onUnlink(transfer)} data-cy="internalTransferUnlink">
              <FontAwesomeIcon icon="asterisk" className="me-2" />
              <Translate contentKey="fintrackApp.internalTransfer.product.unlink.action">Unlink</Translate>
            </DropdownItem>
          </ProductActionsMenu>
        </div>
      </div>
    </div>
  </article>
);

export const InternalTransfer = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [filters, setFilters] = useState<Filters>(() => readFilters(location.search));
  const [transfers, setTransfers] = useState<InternalTransferRead[]>([]);
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [unlinkTarget, setUnlinkTarget] = useState<InternalTransferRead | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const page = await getInternalTransfers({
        accountId: filters.accountId ? Number(filters.accountId) : undefined,
        fromDate: filters.fromDate || undefined,
        toDate: filters.toDate || undefined,
        page: 0,
        size: 100,
        sort: filters.sort,
      });
      setTransfers(page.content);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    getSelectableFinancialAccounts()
      .then(setAccounts)
      .catch(() => setAccounts([]));
  }, []);

  useEffect(() => {
    setFilters(readFilters(location.search));
  }, [location.search]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = (next: Filters) => {
    setFilters(next);
    navigate(`/internal-transfers${writeFilters(next)}`, { replace: true });
  };

  const activeFilters = Boolean(filters.accountId || filters.fromDate || filters.toDate);

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="internal-transfer-heading"
        dataCy="InternalTransferHeading"
        title={<Translate contentKey="fintrackApp.internalTransfer.product.title">Internal Transfers</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.internalTransfer.product.listSubtitle">
            Move money between your own accounts without creating income or expense.
          </Translate>
        }
        actions={
          <Button tag={Link} to="/internal-transfers/new" color="primary" data-cy="internalTransferCreate">
            <FontAwesomeIcon icon="plus" className="me-1" />
            <Translate contentKey="fintrackApp.internalTransfer.product.new">New transfer</Translate>
          </Button>
        }
      />

      <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.filters">Filters</Translate>} className="mb-3">
        <Form
          onSubmit={event => {
            event.preventDefault();
            load();
          }}
        >
          <Row className="g-3 align-items-end">
            <Col md="4">
              <FormGroup className="mb-0">
                <Label for="internal-transfer-account-filter">
                  <Translate contentKey="fintrackApp.internalTransfer.product.account">Account</Translate>
                </Label>
                <Input
                  id="internal-transfer-account-filter"
                  type="select"
                  value={filters.accountId}
                  onChange={event => apply({ ...filters, accountId: event.target.value })}
                >
                  <option value="">{translate('fintrackApp.internalTransfer.product.allAccounts')}</option>
                  {accounts.map(account => (
                    <option key={account.id} value={account.id}>
                      {formatFinancialAccountLabel(account)}
                    </option>
                  ))}
                </Input>
              </FormGroup>
            </Col>
            <Col sm="6" md="2">
              <FormGroup className="mb-0">
                <Label for="internal-transfer-from-date">
                  <Translate contentKey="fintrackApp.internalTransfer.product.fromDate">From date</Translate>
                </Label>
                <Input
                  id="internal-transfer-from-date"
                  type="date"
                  value={filters.fromDate}
                  onChange={event => apply({ ...filters, fromDate: event.target.value })}
                />
              </FormGroup>
            </Col>
            <Col sm="6" md="2">
              <FormGroup className="mb-0">
                <Label for="internal-transfer-to-date">
                  <Translate contentKey="fintrackApp.internalTransfer.product.toDate">To date</Translate>
                </Label>
                <Input
                  id="internal-transfer-to-date"
                  type="date"
                  value={filters.toDate}
                  onChange={event => apply({ ...filters, toDate: event.target.value })}
                />
              </FormGroup>
            </Col>
            <Col md="3">
              <FormGroup className="mb-0">
                <Label for="internal-transfer-sort">
                  <Translate contentKey="fintrackApp.internalTransfer.product.sort">Sort</Translate>
                </Label>
                <Input
                  id="internal-transfer-sort"
                  type="select"
                  value={filters.sort}
                  onChange={event => apply({ ...filters, sort: event.target.value })}
                >
                  <option value="outgoingTransaction.transactionDate,DESC">
                    {translate('fintrackApp.internalTransfer.product.sortDateDesc')}
                  </option>
                  <option value="outgoingTransaction.transactionDate,ASC">
                    {translate('fintrackApp.internalTransfer.product.sortDateAsc')}
                  </option>
                  <option value="outgoingTransaction.amount,DESC">{translate('fintrackApp.internalTransfer.product.sortAmount')}</option>
                  <option value="outgoingTransaction.account.name,ASC">
                    {translate('fintrackApp.internalTransfer.product.sortSource')}
                  </option>
                  <option value="incomingTransaction.account.name,ASC">
                    {translate('fintrackApp.internalTransfer.product.sortDestination')}
                  </option>
                </Input>
              </FormGroup>
            </Col>
            <Col md="1" className="d-flex gap-2">
              {activeFilters ? (
                <Button color="secondary" outline size="sm" onClick={() => apply({ ...initialFilters })}>
                  <Translate contentKey="fintrackApp.internalTransfer.product.clear">Clear</Translate>
                </Button>
              ) : null}
            </Col>
          </Row>
        </Form>
      </ProductSection>

      {error ? (
        <Alert color="danger" role="alert">
          <Translate contentKey="fintrackApp.internalTransfer.product.errors.load">
            We could not load internal transfers. Try again.
          </Translate>
        </Alert>
      ) : null}
      {loading ? (
        <div className="text-center py-5" data-cy="internalTransferLoading">
          <Spinner />
          <span className="ms-2">
            <Translate contentKey="global.messages.info.loading">Loading...</Translate>
          </span>
        </div>
      ) : null}
      {!loading && !error && !transfers.length ? (
        <ProductSection
          title={<Translate contentKey="fintrackApp.internalTransfer.product.empty.title">No internal transfers</Translate>}
          dataCy="internalTransferEmpty"
        >
          <p className="text-muted mb-3">
            <Translate contentKey="fintrackApp.internalTransfer.product.empty.body">
              Internal transfers move money between your own accounts.
            </Translate>
          </p>
          {activeFilters ? (
            <Button color="secondary" outline onClick={() => apply({ ...initialFilters })}>
              <Translate contentKey="fintrackApp.internalTransfer.product.clear">Clear</Translate>
            </Button>
          ) : (
            <Button tag={Link} to="/internal-transfers/new" color="primary">
              <Translate contentKey="fintrackApp.internalTransfer.product.new">New transfer</Translate>
            </Button>
          )}
        </ProductSection>
      ) : null}
      {!loading && !error && transfers.length ? (
        <div className="vstack gap-3">
          {transfers.map(transfer => (
            <TransferListItem key={transfer.transferId} transfer={transfer} onUnlink={setUnlinkTarget} />
          ))}
        </div>
      ) : null}
      <InternalTransferUnlinkModal
        transfer={unlinkTarget}
        onClose={() => setUnlinkTarget(null)}
        onUnlinked={() => {
          setUnlinkTarget(null);
          load();
        }}
      />
    </ProductPage>
  );
};

export default InternalTransfer;
