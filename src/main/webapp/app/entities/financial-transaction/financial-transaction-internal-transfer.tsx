import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Col, FormGroup, Input, Label, Row, Spinner } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Translate, translate } from 'react-jhipster';

import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';
import { ProductSection } from 'app/shared/ui/product-page';
import {
  createCounterpartForTransaction,
  findEligibleCounterparts,
  getInternalTransferForTransaction,
  InternalTransferCounterpart,
  InternalTransferRead,
  internalTransferErrorKey,
  linkExistingTransactions,
} from 'app/entities/internal-transfer/internal-transfer-product.service';
import { InternalTransferUnlinkModal } from 'app/entities/internal-transfer/internal-transfer-unlink-modal';

type CounterpartMode = 'CREATE_COUNTERPART' | 'LINK_EXISTING';

type Props = {
  transaction: IFinancialTransaction;
  onRelationChange?: (transfer: InternalTransferRead | null) => void;
  onRelationLoaded?: () => void;
  showMarkAction?: boolean;
};

const counterpartFor = (transfer: InternalTransferRead, transactionId?: number) =>
  transfer.source.transactionId === transactionId ? transfer.destination : transfer.source;

/**
 * Product boundary for a posted transaction's optional transfer relationship.
 * It uses the neutral transfer command endpoints and never creates a second
 * copy of the current FinancialTransaction.
 */
export const FinancialTransactionInternalTransfer = ({ transaction, onRelationChange, onRelationLoaded, showMarkAction = true }: Props) => {
  const [transfer, setTransfer] = useState<InternalTransferRead | null>(null);
  const [relationLoaded, setRelationLoaded] = useState(false);
  const [configure, setConfigure] = useState(false);
  const [mode, setMode] = useState<CounterpartMode>('CREATE_COUNTERPART');
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [targetAccountId, setTargetAccountId] = useState('');
  const [postingDate, setPostingDate] = useState('');
  const [description, setDescription] = useState('');
  const [externalReference, setExternalReference] = useState('');
  const [notes, setNotes] = useState('');
  const [search, setSearch] = useState('');
  const [accountFilterId, setAccountFilterId] = useState('');
  const [page, setPage] = useState(0);
  const [results, setResults] = useState<InternalTransferCounterpart[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selected, setSelected] = useState<InternalTransferCounterpart | null>(null);
  const [selectedStale, setSelectedStale] = useState(false);
  const [searching, setSearching] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [unlinkOpen, setUnlinkOpen] = useState(false);
  const latestSearchRequest = useRef(0);
  const selectedSourceSignature = useRef<string | null>(null);

  const loadRelationship = async () => {
    if (!transaction.id) return;
    setRelationLoaded(false);
    try {
      const nextTransfer = await getInternalTransferForTransaction(transaction.id);
      setTransfer(nextTransfer);
      onRelationChange?.(nextTransfer);
    } catch (requestError) {
      const status = requestError?.response?.status;
      if (status === 404) {
        setTransfer(null);
        onRelationChange?.(null);
      } else {
        setError(translate('fintrackApp.internalTransfer.product.errors.load'));
      }
    } finally {
      setRelationLoaded(true);
      onRelationLoaded?.();
    }
  };

  useEffect(() => {
    void loadRelationship();
  }, [transaction.id]);

  useEffect(() => {
    if (!configure) return;
    let mounted = true;
    getSelectableFinancialAccounts(transaction.account?.id)
      .then(nextAccounts => {
        if (mounted) setAccounts(nextAccounts);
      })
      .catch(() => {
        if (mounted) setAccounts([]);
      });
    return () => {
      mounted = false;
    };
  }, [configure, transaction.account?.id]);

  const compatibleAccounts = useMemo(
    () => accounts.filter(account => account.id !== transaction.account?.id && account.currency === transaction.account?.currency),
    [accounts, transaction.account?.currency, transaction.account?.id],
  );

  const eligibilitySignature = useMemo(
    () =>
      JSON.stringify({
        accountId: transaction.account?.id ?? null,
        currency: transaction.account?.currency ?? null,
        transactionDate: transaction.transactionDate ?? null,
        amount: transaction.amount ?? null,
        flow: transaction.flow ?? null,
      }),
    [transaction.account?.currency, transaction.account?.id, transaction.amount, transaction.flow, transaction.transactionDate],
  );
  const canSearchExisting = Boolean(
    transaction.id &&
      transaction.account?.id &&
      transaction.account?.currency &&
      transaction.transactionDate &&
      transaction.amount != null &&
      transaction.flow,
  );

  const openConfigure = () => {
    setError('');
    setConfigure(true);
    setMode('CREATE_COUNTERPART');
    setTargetAccountId('');
    setPostingDate(transaction.postingDate ? String(transaction.postingDate) : '');
    setDescription(transaction.description ?? '');
    setExternalReference(transaction.externalReference ?? '');
    setNotes(transaction.notes ?? '');
    setSearch('');
    setAccountFilterId('');
    setPage(0);
    setSelected(null);
    setSelectedStale(false);
    setResults([]);
  };

  useEffect(() => {
    if (!configure || mode !== 'LINK_EXISTING') {
      latestSearchRequest.current += 1;
      return;
    }
    if (!canSearchExisting || !transaction.id) {
      setResults([]);
      setTotalElements(0);
      setTotalPages(0);
      setSearching(false);
      return;
    }

    const requestId = latestSearchRequest.current + 1;
    latestSearchRequest.current = requestId;
    const load = async () => {
      setSearching(true);
      setError('');
      try {
        const response = await findEligibleCounterparts(transaction.id, {
          search,
          accountId: accountFilterId ? Number(accountFilterId) : undefined,
          page,
        });
        if (requestId !== latestSearchRequest.current) return;
        setResults(response.content ?? []);
        setTotalElements(response.totalElements ?? response.content?.length ?? 0);
        setTotalPages(response.totalPages ?? 0);
      } catch (requestError) {
        if (requestId !== latestSearchRequest.current) return;
        setResults([]);
        setTotalElements(0);
        setTotalPages(0);
        setError(translate(`fintrackApp.internalTransfer.product.errors.${internalTransferErrorKey(requestError)}`));
      } finally {
        if (requestId === latestSearchRequest.current) setSearching(false);
      }
    };
    const delay = search.trim() ? window.setTimeout(() => void load(), 250) : undefined;
    if (!delay) void load();
    return () => {
      latestSearchRequest.current += 1;
      if (delay) window.clearTimeout(delay);
    };
  }, [accountFilterId, canSearchExisting, configure, eligibilitySignature, mode, page, search, transaction.id]);

  useEffect(() => {
    if (selected && selectedSourceSignature.current && selectedSourceSignature.current !== eligibilitySignature) {
      setSelectedStale(true);
    }
  }, [eligibilitySignature, selected]);

  const selectMode = (nextMode: CounterpartMode) => {
    setError('');
    setMode(nextMode);
    setPage(0);
    if (nextMode === 'CREATE_COUNTERPART') {
      setTargetAccountId('');
      setPostingDate(transaction.postingDate ? String(transaction.postingDate) : '');
      setDescription(transaction.description ?? '');
      setExternalReference(transaction.externalReference ?? '');
      setNotes(transaction.notes ?? '');
    }
  };

  const createOrLink = async () => {
    if (!transaction.id) return;
    setSubmitting(true);
    setError('');
    try {
      const nextTransfer =
        mode === 'CREATE_COUNTERPART'
          ? await createCounterpartForTransaction(transaction.id, {
              targetAccountId: Number(targetAccountId),
              postingDate: postingDate || undefined,
              description,
              externalReference: externalReference || undefined,
              notes: notes || undefined,
            })
          : await linkExistingTransactions(
              transaction.flow === 'OUT' ? transaction.id : (selected?.transactionId ?? 0),
              transaction.flow === 'OUT' ? (selected?.transactionId ?? 0) : transaction.id,
            );
      setTransfer(nextTransfer);
      onRelationChange?.(nextTransfer);
      setConfigure(false);
    } catch (requestError) {
      setError(translate(`fintrackApp.internalTransfer.product.errors.${internalTransferErrorKey(requestError)}`));
    } finally {
      setSubmitting(false);
    }
  };

  if (!transaction.id || !relationLoaded) return null;

  if (transfer) {
    const counterpart = counterpartFor(transfer, transaction.id);
    return (
      <>
        <ProductSection
          title={<Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.linked">Internal transfer</Translate>}
          dataCy="financialTransactionInternalTransfer"
        >
          <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3">
            <div>
              <p className="mb-1 fw-semibold" data-cy="financialTransactionInternalTransferCounterpart">
                <FontAwesomeIcon icon="exchange-alt" className="me-2" aria-hidden="true" />
                {counterpart.accountName} · {counterpart.description}
              </p>
              <p className="small text-muted mb-0">
                <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.linkedDescription">
                  This transaction is part of an internal transfer.
                </Translate>
              </p>
            </div>
            <div className="d-flex flex-wrap gap-2">
              <Button tag={Link} to={`/internal-transfers/${transfer.transferId}`} size="sm" color="secondary" outline>
                <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.viewTransfer">
                  View internal transfer
                </Translate>
              </Button>
              <Button tag={Link} to={`/financial-transaction/${counterpart.transactionId}`} size="sm" color="secondary" outline>
                <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.viewCounterpart">
                  View counterpart transaction
                </Translate>
              </Button>
              <Button tag={Link} to={`/internal-transfers/${transfer.transferId}/edit`} size="sm" color="primary">
                <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.editTransfer">
                  Edit internal transfer
                </Translate>
              </Button>
              <Button
                type="button"
                color="danger"
                outline
                size="sm"
                onClick={() => setUnlinkOpen(true)}
                data-cy="financialTransactionUnlinkTransfer"
              >
                <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.unlink">
                  Unlink internal transfer
                </Translate>
              </Button>
            </div>
          </div>
        </ProductSection>
        <InternalTransferUnlinkModal
          transfer={unlinkOpen ? transfer : null}
          onClose={() => setUnlinkOpen(false)}
          onUnlinked={() => {
            setUnlinkOpen(false);
            setTransfer(null);
            onRelationChange?.(null);
          }}
        />
      </>
    );
  }

  if (!showMarkAction) return null;

  return (
    <ProductSection
      title={<Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.title">Internal transfer</Translate>}
      dataCy="financialTransactionMarkInternalTransfer"
    >
      {error ? (
        <Alert color="danger" className="mb-3" role="alert">
          {error}
        </Alert>
      ) : null}
      {!configure ? (
        <Button type="button" color="secondary" outline onClick={openConfigure} data-cy="financialTransactionMarkInternalTransferButton">
          <FontAwesomeIcon icon="exchange-alt" className="me-2" />
          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.mark">Mark as internal transfer</Translate>
        </Button>
      ) : (
        <div className="border rounded-3 p-3">
          <div className="d-flex flex-wrap gap-2 mb-3">
            <Button
              type="button"
              color={mode === 'CREATE_COUNTERPART' ? 'primary' : 'secondary'}
              outline={mode !== 'CREATE_COUNTERPART'}
              onClick={() => selectMode('CREATE_COUNTERPART')}
            >
              <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.createCounterpart">
                Create counterpart
              </Translate>
            </Button>
            <Button
              type="button"
              color={mode === 'LINK_EXISTING' ? 'primary' : 'secondary'}
              outline={mode !== 'LINK_EXISTING'}
              onClick={() => selectMode('LINK_EXISTING')}
            >
              <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.linkExisting">
                Link existing counterpart
              </Translate>
            </Button>
          </div>
          {mode === 'CREATE_COUNTERPART' ? (
            <Row className="g-3">
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="posted-transfer-account">
                    <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartAccount">
                      Counterpart account
                    </Translate>
                  </Label>
                  <Input
                    id="posted-transfer-account"
                    type="select"
                    value={targetAccountId}
                    onChange={event => setTargetAccountId(event.target.value)}
                  >
                    <option value="" />
                    {compatibleAccounts.map(account => (
                      <option key={account.id} value={account.id}>
                        {formatFinancialAccountLabel(account)}
                      </option>
                    ))}
                  </Input>
                </FormGroup>
              </Col>
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="posted-transfer-posting-date">
                    <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartPostingDate">
                      Counterpart posting date
                    </Translate>
                  </Label>
                  <Input
                    id="posted-transfer-posting-date"
                    type="date"
                    value={postingDate}
                    onChange={event => setPostingDate(event.target.value)}
                  />
                </FormGroup>
              </Col>
              <Col xs="12">
                <FormGroup className="mb-0">
                  <Label for="posted-transfer-description">
                    <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartDescription">
                      Counterpart description
                    </Translate>
                  </Label>
                  <Input id="posted-transfer-description" value={description} onChange={event => setDescription(event.target.value)} />
                </FormGroup>
              </Col>
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="posted-transfer-reference">
                    <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartReference">
                      Counterpart reference
                    </Translate>
                  </Label>
                  <Input
                    id="posted-transfer-reference"
                    value={externalReference}
                    onChange={event => setExternalReference(event.target.value)}
                  />
                </FormGroup>
              </Col>
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="posted-transfer-notes">
                    <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartNotes">
                      Counterpart notes
                    </Translate>
                  </Label>
                  <Input id="posted-transfer-notes" value={notes} onChange={event => setNotes(event.target.value)} />
                </FormGroup>
              </Col>
            </Row>
          ) : (
            <div data-cy="postedTransferLinkExisting">
              {!canSearchExisting ? (
                <p className="small text-muted mb-0" data-cy="postedTransferCounterpartGuidance">
                  <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.searchNeedsSource">
                    Complete the account, date, type, and amount to find an eligible counterpart.
                  </Translate>
                </p>
              ) : (
                <>
                  <Row className="g-2">
                    <Col md="6">
                      <FormGroup className="mb-0">
                        <Label for="posted-transfer-account-filter">
                          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.filterAccount">
                            Counterpart account
                          </Translate>
                        </Label>
                        <Input
                          id="posted-transfer-account-filter"
                          type="select"
                          value={accountFilterId}
                          onChange={event => {
                            setAccountFilterId(event.target.value);
                            setPage(0);
                          }}
                        >
                          <option value="">
                            {translate('fintrackApp.financialTransaction.manualDraft.internalTransfer.allEligibleAccounts')}
                          </option>
                          {compatibleAccounts.map(account => (
                            <option key={account.id} value={account.id}>
                              {formatFinancialAccountLabel(account)}
                            </option>
                          ))}
                        </Input>
                      </FormGroup>
                    </Col>
                    <Col md="6">
                      <FormGroup className="mb-0">
                        <Label for="posted-transfer-search">
                          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.search">
                            Search counterpart
                          </Translate>
                        </Label>
                        <Input
                          id="posted-transfer-search"
                          value={search}
                          onChange={event => {
                            setSearch(event.target.value);
                            setPage(0);
                          }}
                        />
                      </FormGroup>
                    </Col>
                  </Row>
                  {selected ? (
                    <div
                      className={`counterpart-selection-summary mt-3 ${selectedStale ? 'is-stale' : ''}`}
                      data-cy="postedTransferSelectedCounterpart"
                    >
                      <span className="fw-semibold">
                        <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.selected">
                          Selected counterpart
                        </Translate>
                        :
                      </span>{' '}
                      {selected.description} · {selected.accountName}
                      {selectedStale ? (
                        <span className="text-warning ms-2">
                          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.counterpartUnavailable">
                            The selected counterpart is no longer available.
                          </Translate>
                        </span>
                      ) : null}
                    </div>
                  ) : null}
                  <div className="small text-muted mt-3" aria-live="polite" data-cy="postedTransferCounterpartCount">
                    {searching ? (
                      <>
                        <Spinner size="sm" className="me-2" />
                        <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.loading">
                          Loading eligible counterparts…
                        </Translate>
                      </>
                    ) : (
                      translate('fintrackApp.financialTransaction.manualDraft.internalTransfer.resultCount', {
                        count: totalElements,
                      })
                    )}
                  </div>
                  {!searching && !results.length && !error ? (
                    <p className="small text-muted mt-2 mb-0">
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraft.internalTransfer.noResults">
                        No eligible counterparts match these filters.
                      </Translate>
                    </p>
                  ) : null}
                  <div
                    className="vstack gap-2 mt-2"
                    role="listbox"
                    aria-label={translate('fintrackApp.financialTransaction.manualDraft.internalTransfer.searchResults')}
                  >
                    {results.map(result => {
                      const isSelected = selected?.transactionId === result.transactionId;
                      return (
                        <Button
                          key={result.transactionId}
                          type="button"
                          color={isSelected ? 'primary' : 'light'}
                          outline={!isSelected}
                          className="counterpart-result text-start"
                          aria-selected={isSelected}
                          role="option"
                          onClick={() => {
                            setSelected(result);
                            selectedSourceSignature.current = eligibilitySignature;
                            setSelectedStale(false);
                          }}
                          data-cy="postedTransferCounterpartResult"
                        >
                          <span className="d-flex justify-content-between gap-2">
                            <span className="fw-semibold">{result.description}</span>
                            {isSelected ? <FontAwesomeIcon icon="check" aria-label="Selected" /> : null}
                          </span>
                          <span className="d-block small">
                            {result.accountName} · {result.transactionDate}
                            {result.postingDate ? ` · ${result.postingDate}` : ''} · {result.amount} {result.currency}
                          </span>
                        </Button>
                      );
                    })}
                  </div>
                  {totalPages > 1 ? (
                    <div className="d-flex justify-content-end align-items-center gap-2 mt-3">
                      <Button
                        type="button"
                        size="sm"
                        color="secondary"
                        outline
                        disabled={searching || page === 0}
                        onClick={() => setPage(page - 1)}
                      >
                        <Translate contentKey="entity.action.previous">Previous</Translate>
                      </Button>
                      <span className="small text-muted">
                        {page + 1} / {totalPages}
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        color="secondary"
                        outline
                        disabled={searching || page + 1 >= totalPages}
                        onClick={() => setPage(page + 1)}
                      >
                        <Translate contentKey="entity.action.next">Next</Translate>
                      </Button>
                    </div>
                  ) : null}
                </>
              )}
            </div>
          )}
          {error ? (
            <Alert color="danger" className="mt-3 mb-0">
              {error}
            </Alert>
          ) : null}
          <div className="d-flex flex-wrap justify-content-end gap-2 mt-3">
            <Button type="button" color="secondary" outline onClick={() => setConfigure(false)} disabled={submitting}>
              <Translate contentKey="entity.action.cancel">Cancel</Translate>
            </Button>
            <Button
              type="button"
              color="primary"
              onClick={() => void createOrLink()}
              disabled={
                submitting ||
                (mode === 'CREATE_COUNTERPART' ? !targetAccountId || !postingDate || !description.trim() : !selected || selectedStale)
              }
            >
              {submitting ? <Spinner size="sm" /> : null}{' '}
              <Translate
                contentKey={
                  mode === 'CREATE_COUNTERPART'
                    ? 'fintrackApp.financialTransaction.manualDraft.internalTransfer.createCounterpart'
                    : 'fintrackApp.financialTransaction.manualDraft.internalTransfer.linkCounterpart'
                }
              >
                {mode === 'CREATE_COUNTERPART' ? 'Create counterpart' : 'Link counterpart'}
              </Translate>
            </Button>
          </div>
        </div>
      )}
    </ProductSection>
  );
};

export default FinancialTransactionInternalTransfer;
