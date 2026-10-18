import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Form, FormFeedback, FormGroup, Input, Label, Row, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  InternalTransferRead,
  createInternalTransfer,
  getInternalTransfer,
  internalTransferErrorKey,
  updateInternalTransfer,
} from './internal-transfer-product.service';
import { formatTransferAccount } from './internal-transfer-presentation';

type TransferForm = {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  transactionDate: string;
  sourcePostingDate: string;
  destinationPostingDate: string;
  samePostingDate: boolean;
  sourceDescription: string;
  destinationDescription: string;
  sourceExternalReference: string;
  destinationExternalReference: string;
  sourceNotes: string;
  destinationNotes: string;
  transferNotes: string;
};

const today = () => new Date().toISOString().slice(0, 10);

const emptyForm = (): TransferForm => ({
  sourceAccountId: '',
  destinationAccountId: '',
  amount: '',
  transactionDate: today(),
  sourcePostingDate: today(),
  destinationPostingDate: today(),
  samePostingDate: true,
  sourceDescription: '',
  destinationDescription: '',
  sourceExternalReference: '',
  destinationExternalReference: '',
  sourceNotes: '',
  destinationNotes: '',
  transferNotes: '',
});

const toForm = (transfer: InternalTransferRead): TransferForm => {
  const samePostingDate = transfer.source.postingDate === transfer.destination.postingDate;
  return {
    sourceAccountId: String(transfer.source.accountId),
    destinationAccountId: String(transfer.destination.accountId),
    amount: transfer.amount === undefined || transfer.amount === null ? '' : String(transfer.amount),
    transactionDate: transfer.transactionDate ?? transfer.source.transactionDate ?? '',
    sourcePostingDate: transfer.source.postingDate ?? '',
    destinationPostingDate: transfer.destination.postingDate ?? '',
    samePostingDate,
    sourceDescription: transfer.source.description ?? '',
    destinationDescription: transfer.destination.description ?? '',
    sourceExternalReference: '',
    destinationExternalReference: '',
    sourceNotes: '',
    destinationNotes: '',
    transferNotes: transfer.notes ?? '',
  };
};

const errorCopy = (key: string) => translate(`fintrackApp.internalTransfer.product.errors.${key}`);

export const InternalTransferUpdate = () => {
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = !id;
  const [form, setForm] = useState<TransferForm>(emptyForm);
  const [transfer, setTransfer] = useState<InternalTransferRead | null>(null);
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const sourceAccount = useMemo(
    () => accounts.find(account => String(account.id) === form.sourceAccountId),
    [accounts, form.sourceAccountId],
  );
  const destinationAccount = useMemo(
    () => accounts.find(account => String(account.id) === form.destinationAccountId),
    [accounts, form.destinationAccountId],
  );
  const sameAccount = Boolean(form.sourceAccountId && form.sourceAccountId === form.destinationAccountId);
  const currencyMismatch = Boolean(sourceAccount && destinationAccount && sourceAccount.currency !== destinationAccount.currency);
  const amountInvalid = !form.amount || Number(form.amount) <= 0 || Number.isNaN(Number(form.amount));

  const setField = <K extends keyof TransferForm>(field: K, value: TransferForm[K]) => {
    setForm(current => {
      if (field === 'transactionDate' && current.samePostingDate) {
        return {
          ...current,
          transactionDate: value as string,
          sourcePostingDate: value as string,
          destinationPostingDate: value as string,
        };
      }
      if (field === 'samePostingDate' && value === true) {
        return {
          ...current,
          samePostingDate: true,
          sourcePostingDate: current.transactionDate,
          destinationPostingDate: current.transactionDate,
        };
      }
      return { ...current, [field]: value };
    });
  };

  const loadTransfer = async (preserveForm = false) => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getInternalTransfer(id);
      setTransfer(result);
      if (!preserveForm) setForm(toForm(result));
    } catch (requestError) {
      setError(internalTransferErrorKey(requestError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    getSelectableFinancialAccounts()
      .then(setAccounts)
      .catch(() => setAccounts([]));
    if (!isNew) void loadTransfer();
  }, [id]);

  const validate = () => {
    if (isNew && (!form.sourceAccountId || !form.destinationAccountId || sameAccount || currencyMismatch)) return false;
    if (amountInvalid || !form.transactionDate || !form.sourceDescription.trim() || !form.destinationDescription.trim()) return false;
    if (!form.samePostingDate && (!form.sourcePostingDate || !form.destinationPostingDate)) return false;
    return true;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setError(null);
    if (!validate()) return;
    setSaving(true);
    const sourcePostingDate = form.samePostingDate ? form.transactionDate : form.sourcePostingDate;
    const destinationPostingDate = form.samePostingDate ? form.transactionDate : form.destinationPostingDate;
    try {
      if (isNew) {
        const created = await createInternalTransfer({
          sourceAccountId: Number(form.sourceAccountId),
          destinationAccountId: Number(form.destinationAccountId),
          amount: Number(form.amount),
          transactionDate: form.transactionDate,
          sourcePostingDate,
          destinationPostingDate,
          sourceDescription: form.sourceDescription.trim(),
          destinationDescription: form.destinationDescription.trim(),
          sourceExternalReference: form.sourceExternalReference.trim() || undefined,
          destinationExternalReference: form.destinationExternalReference.trim() || undefined,
          sourceNotes: form.sourceNotes.trim() || undefined,
          destinationNotes: form.destinationNotes.trim() || undefined,
          transferNotes: form.transferNotes.trim() || undefined,
        });
        navigate(`/internal-transfers/${created.transferId}`, { replace: true });
      } else if (transfer && id) {
        const updated = await updateInternalTransfer(id, {
          expectedVersion: transfer.version,
          amount: Number(form.amount),
          transactionDate: form.transactionDate,
          sourcePostingDate,
          destinationPostingDate,
          sourceDescription: form.sourceDescription.trim(),
          destinationDescription: form.destinationDescription.trim(),
          notes: form.transferNotes.trim() || undefined,
        });
        navigate(`/internal-transfers/${updated.transferId}`, { replace: true });
      }
    } catch (requestError) {
      setError(internalTransferErrorKey(requestError));
    } finally {
      setSaving(false);
    }
  };

  const invalidClass = (invalid: boolean) => submitted && invalid;

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner />
        <span className="ms-2">
          <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </span>
      </div>
    );
  }

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="internal-transfer-form-heading"
        dataCy="internalTransferCreateUpdateHeading"
        title={
          <Translate contentKey={isNew ? 'fintrackApp.internalTransfer.product.new' : 'fintrackApp.internalTransfer.product.edit'}>
            {isNew ? 'New transfer' : 'Edit transfer'}
          </Translate>
        }
        subtitle={
          <Translate contentKey="fintrackApp.internalTransfer.product.formSubtitle">
            The two movements are saved together as one internal transfer.
          </Translate>
        }
        actions={
          <Button tag={Link} to={isNew ? '/internal-transfers' : `/internal-transfers/${id}`} color="secondary" outline>
            <Translate contentKey="entity.action.cancel">Cancel</Translate>
          </Button>
        }
      />

      {error ? (
        <Alert color="danger" role="alert" data-cy="internalTransferFormError">
          {errorCopy(error)}
          {error === 'versionConflict' && !isNew ? (
            <Button color="link" className="p-0 ms-2 align-baseline" onClick={() => loadTransfer(true)}>
              <Translate contentKey="fintrackApp.internalTransfer.product.reload">Reload current version</Translate>
            </Button>
          ) : null}
        </Alert>
      ) : null}

      <Form onSubmit={submit} noValidate>
        <div className="vstack gap-3">
          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.accounts">Accounts</Translate>}>
            {isNew ? (
              <Row className="g-3">
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-source-account">
                      <Translate contentKey="fintrackApp.internalTransfer.product.source">From</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-source-account"
                      type="select"
                      value={form.sourceAccountId}
                      invalid={invalidClass(!form.sourceAccountId)}
                      onChange={event => setField('sourceAccountId', event.target.value)}
                      data-cy="internalTransferSourceAccount"
                    >
                      <option value="">{translate('fintrackApp.internalTransfer.product.selectAccount')}</option>
                      {accounts.map(account => (
                        <option key={account.id} value={account.id}>
                          {formatFinancialAccountLabel(account)}
                        </option>
                      ))}
                    </Input>
                    <FormFeedback>
                      <Translate contentKey="fintrackApp.internalTransfer.product.errors.accountRequired">Select an account.</Translate>
                    </FormFeedback>
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-destination-account">
                      <Translate contentKey="fintrackApp.internalTransfer.product.destination">To</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-destination-account"
                      type="select"
                      value={form.destinationAccountId}
                      invalid={invalidClass(!form.destinationAccountId || sameAccount || currencyMismatch)}
                      onChange={event => setField('destinationAccountId', event.target.value)}
                      data-cy="internalTransferDestinationAccount"
                    >
                      <option value="">{translate('fintrackApp.internalTransfer.product.selectAccount')}</option>
                      {accounts
                        .filter(account => String(account.id) !== form.sourceAccountId)
                        .map(account => (
                          <option key={account.id} value={account.id}>
                            {formatFinancialAccountLabel(account)}
                          </option>
                        ))}
                    </Input>
                    {sameAccount ? (
                      <FormFeedback>
                        <Translate contentKey="fintrackApp.internalTransfer.product.errors.accountMismatch">
                          Choose two different accounts.
                        </Translate>
                      </FormFeedback>
                    ) : null}
                    {currencyMismatch ? (
                      <div className="invalid-feedback d-block">
                        <Translate contentKey="fintrackApp.internalTransfer.product.errors.currencyMismatch">
                          Accounts must use the same currency.
                        </Translate>
                      </div>
                    ) : null}
                  </FormGroup>
                </Col>
              </Row>
            ) : transfer ? (
              <>
                <Row className="g-3">
                  <Col md="6">
                    <div className="border rounded-3 p-3">
                      <div className="small text-muted mb-1">
                        <Translate contentKey="fintrackApp.internalTransfer.product.source">From</Translate>
                      </div>
                      <div className="fw-semibold">{formatTransferAccount(transfer.source)}</div>
                    </div>
                  </Col>
                  <Col md="6">
                    <div className="border rounded-3 p-3">
                      <div className="small text-muted mb-1">
                        <Translate contentKey="fintrackApp.internalTransfer.product.destination">To</Translate>
                      </div>
                      <div className="fw-semibold">{formatTransferAccount(transfer.destination)}</div>
                    </div>
                  </Col>
                </Row>
                <p className="small text-muted mb-0 mt-3">
                  <Translate contentKey="fintrackApp.internalTransfer.product.accountsLocked">
                    To change accounts, unlink this transfer and create a new one.
                  </Translate>
                </p>
              </>
            ) : null}
          </ProductSection>

          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.transferDetails">Transfer details</Translate>}>
            <Row className="g-3">
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="internal-transfer-amount">
                    <Translate contentKey="fintrackApp.internalTransfer.product.amount">Amount</Translate>
                  </Label>
                  <div className="input-group">
                    <Input
                      id="internal-transfer-amount"
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={form.amount}
                      invalid={invalidClass(amountInvalid)}
                      onChange={event => setField('amount', event.target.value)}
                      data-cy="internalTransferAmountInput"
                    />
                    <span className="input-group-text">{sourceAccount?.currency ?? transfer?.currency ?? '—'}</span>
                  </div>
                  {submitted && amountInvalid ? (
                    <div className="invalid-feedback d-block">
                      <Translate contentKey="fintrackApp.internalTransfer.product.errors.invalidAmount">
                        Enter an amount greater than zero.
                      </Translate>
                    </div>
                  ) : null}
                </FormGroup>
              </Col>
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="internal-transfer-date">
                    <Translate contentKey="fintrackApp.internalTransfer.product.transactionDate">Transfer date</Translate>
                  </Label>
                  <Input
                    id="internal-transfer-date"
                    type="date"
                    value={form.transactionDate}
                    invalid={invalidClass(!form.transactionDate)}
                    onChange={event => setField('transactionDate', event.target.value)}
                    data-cy="internalTransferDate"
                  />
                </FormGroup>
              </Col>
            </Row>
          </ProductSection>

          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.postingDates">Posting dates</Translate>}>
            <FormGroup check className="mb-3">
              <Input
                id="internal-transfer-same-posting-date"
                type="checkbox"
                checked={form.samePostingDate}
                onChange={event => setField('samePostingDate', event.target.checked)}
              />
              <Label check for="internal-transfer-same-posting-date">
                <Translate contentKey="fintrackApp.internalTransfer.product.samePostingDate">Use the transfer date for both</Translate>
              </Label>
            </FormGroup>
            {!form.samePostingDate ? (
              <Row className="g-3">
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-source-posting-date">
                      <Translate contentKey="fintrackApp.internalTransfer.product.sourcePostingDate">Source posting date</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-source-posting-date"
                      type="date"
                      value={form.sourcePostingDate}
                      invalid={invalidClass(!form.sourcePostingDate)}
                      onChange={event => setField('sourcePostingDate', event.target.value)}
                    />
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-destination-posting-date">
                      <Translate contentKey="fintrackApp.internalTransfer.product.destinationPostingDate">
                        Destination posting date
                      </Translate>
                    </Label>
                    <Input
                      id="internal-transfer-destination-posting-date"
                      type="date"
                      value={form.destinationPostingDate}
                      invalid={invalidClass(!form.destinationPostingDate)}
                      onChange={event => setField('destinationPostingDate', event.target.value)}
                    />
                  </FormGroup>
                </Col>
              </Row>
            ) : null}
          </ProductSection>

          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.descriptions">Descriptions</Translate>}>
            <Row className="g-3">
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="internal-transfer-source-description">
                    <Translate contentKey="fintrackApp.internalTransfer.product.sourceDescription">Source description</Translate>
                  </Label>
                  <Input
                    id="internal-transfer-source-description"
                    maxLength={500}
                    value={form.sourceDescription}
                    invalid={invalidClass(!form.sourceDescription.trim())}
                    onChange={event => setField('sourceDescription', event.target.value)}
                  />
                </FormGroup>
              </Col>
              <Col md="6">
                <FormGroup className="mb-0">
                  <Label for="internal-transfer-destination-description">
                    <Translate contentKey="fintrackApp.internalTransfer.product.destinationDescription">Destination description</Translate>
                  </Label>
                  <Input
                    id="internal-transfer-destination-description"
                    maxLength={500}
                    value={form.destinationDescription}
                    invalid={invalidClass(!form.destinationDescription.trim())}
                    onChange={event => setField('destinationDescription', event.target.value)}
                  />
                </FormGroup>
              </Col>
            </Row>
          </ProductSection>

          {isNew ? (
            <details className="border rounded-3 p-3">
              <summary className="fw-semibold">
                <Translate contentKey="fintrackApp.internalTransfer.product.optionalDetails">Optional details</Translate>
              </summary>
              <Row className="g-3 mt-1">
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-source-reference">
                      <Translate contentKey="fintrackApp.internalTransfer.product.sourceReference">Source external reference</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-source-reference"
                      maxLength={150}
                      value={form.sourceExternalReference}
                      onChange={event => setField('sourceExternalReference', event.target.value)}
                    />
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-destination-reference">
                      <Translate contentKey="fintrackApp.internalTransfer.product.destinationReference">
                        Destination external reference
                      </Translate>
                    </Label>
                    <Input
                      id="internal-transfer-destination-reference"
                      maxLength={150}
                      value={form.destinationExternalReference}
                      onChange={event => setField('destinationExternalReference', event.target.value)}
                    />
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-source-notes">
                      <Translate contentKey="fintrackApp.internalTransfer.product.sourceNotes">Source notes</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-source-notes"
                      type="textarea"
                      maxLength={1000}
                      value={form.sourceNotes}
                      onChange={event => setField('sourceNotes', event.target.value)}
                    />
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup className="mb-0">
                    <Label for="internal-transfer-destination-notes">
                      <Translate contentKey="fintrackApp.internalTransfer.product.destinationNotes">Destination notes</Translate>
                    </Label>
                    <Input
                      id="internal-transfer-destination-notes"
                      type="textarea"
                      maxLength={1000}
                      value={form.destinationNotes}
                      onChange={event => setField('destinationNotes', event.target.value)}
                    />
                  </FormGroup>
                </Col>
              </Row>
            </details>
          ) : null}

          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.transferNote">Transfer note</Translate>}>
            <FormGroup className="mb-0">
              <Label className="visually-hidden" for="internal-transfer-note">
                <Translate contentKey="fintrackApp.internalTransfer.product.transferNote">Transfer note</Translate>
              </Label>
              <Input
                id="internal-transfer-note"
                type="textarea"
                maxLength={500}
                value={form.transferNotes}
                onChange={event => setField('transferNotes', event.target.value)}
              />
            </FormGroup>
          </ProductSection>

          <div className="d-flex justify-content-end gap-2 pb-3">
            <Button tag={Link} to={isNew ? '/internal-transfers' : `/internal-transfers/${id}`} color="secondary" outline disabled={saving}>
              <Translate contentKey="entity.action.cancel">Cancel</Translate>
            </Button>
            <Button color="primary" type="submit" disabled={saving} data-cy="saveInternalTransfer">
              {saving ? <Spinner size="sm" className="me-2" /> : <FontAwesomeIcon icon="save" className="me-2" />}
              <Translate contentKey={isNew ? 'fintrackApp.internalTransfer.product.create' : 'entity.action.save'}>
                {isNew ? 'Create transfer' : 'Save'}
              </Translate>
            </Button>
          </div>
        </div>
      </Form>
    </ProductPage>
  );
};

export default InternalTransferUpdate;
