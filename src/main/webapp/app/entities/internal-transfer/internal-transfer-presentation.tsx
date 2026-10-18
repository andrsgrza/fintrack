import React from 'react';
import { Link } from 'react-router-dom';
import { Badge } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { FinancialAmount } from 'app/shared/ui/financial-amount';

import { InternalTransferLeg } from './internal-transfer-product.service';

const originKey = (origin?: string | null) => {
  switch (origin) {
    case 'MANUAL':
      return 'fintrackApp.financialTransaction.product.origins.manual';
    case 'FILE_IMPORT':
      return 'fintrackApp.financialTransaction.product.origins.fileImport';
    case 'API':
      return 'fintrackApp.financialTransaction.product.origins.api';
    default:
      return null;
  }
};

export const formatTransferAccount = (leg?: InternalTransferLeg | null) => {
  if (!leg?.accountName) return '—';
  return [
    leg.accountName,
    leg.accountType ? translate(`fintrackApp.AccountType.${leg.accountType}`) : null,
    leg.currency,
    leg.accountActive === false ? translate('fintrackApp.financialAccount.status.inactive') : null,
  ]
    .filter(Boolean)
    .join(' · ');
};

export const TransferAccount = ({ leg }: { leg: InternalTransferLeg }) => (
  <Link
    to={`/financial-account/${leg.accountId}`}
    className="text-decoration-none fw-semibold text-break"
    data-cy="internalTransferAccount"
  >
    {formatTransferAccount(leg)}
  </Link>
);

export const TransferLegSummary = ({ leg, role }: { leg: InternalTransferLeg; role: 'source' | 'destination' }) => (
  <div
    className={`border rounded-3 p-3 h-100 internal-transfer-leg internal-transfer-leg--${role}`}
    data-cy={`internalTransfer${role === 'source' ? 'Source' : 'Destination'}Leg`}
  >
    <div className="text-uppercase small fw-semibold text-muted mb-2">
      <Translate contentKey={`fintrackApp.internalTransfer.product.${role}`}>{role === 'source' ? 'From' : 'To'}</Translate>
    </div>
    <TransferAccount leg={leg} />
    <div className="mt-2 text-break">{leg.description || '—'}</div>
    {leg.postingDate ? (
      <div className="text-muted small mt-2">
        <Translate contentKey="fintrackApp.internalTransfer.product.postingDate">Posting date</Translate>:{' '}
        <TextFormat value={leg.postingDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
      </div>
    ) : null}
  </div>
);

export const TransferLegDetail = ({ leg, role }: { leg: InternalTransferLeg; role: 'source' | 'destination' }) => {
  const origin = originKey(leg.origin);
  return (
    <div className={`border rounded-3 p-3 h-100 internal-transfer-leg internal-transfer-leg--${role}`}>
      <div className="d-flex justify-content-between align-items-start gap-2 mb-3">
        <div>
          <div className="text-uppercase small fw-semibold text-muted mb-2">
            <Translate contentKey={`fintrackApp.internalTransfer.product.${role}`}>{role === 'source' ? 'From' : 'To'}</Translate>
          </div>
          <TransferAccount leg={leg} />
        </div>
        <FinancialAmount amount={leg.amount} currency={leg.currency} flow={leg.flow} compact dataCy={`internalTransfer${role}Amount`} />
      </div>
      <dl className="row mb-0 small">
        <dt className="col-5 text-muted fw-normal">
          <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction date</Translate>
        </dt>
        <dd className="col-7 fw-semibold">
          {leg.transactionDate ? <TextFormat value={leg.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : '—'}
        </dd>
        <dt className="col-5 text-muted fw-normal">
          <Translate contentKey="fintrackApp.internalTransfer.product.postingDate">Posting date</Translate>
        </dt>
        <dd className="col-7 fw-semibold">
          {leg.postingDate ? <TextFormat value={leg.postingDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : '—'}
        </dd>
        <dt className="col-5 text-muted fw-normal">
          <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
        </dt>
        <dd className="col-7 text-break">{leg.description || '—'}</dd>
        {leg.externalReference ? (
          <>
            <dt className="col-5 text-muted fw-normal">
              <Translate contentKey="fintrackApp.internalTransfer.product.externalReference">External reference</Translate>
            </dt>
            <dd className="col-7 text-break">{leg.externalReference}</dd>
          </>
        ) : null}
        {origin ? (
          <>
            <dt className="col-5 text-muted fw-normal">
              <Translate contentKey="fintrackApp.financialTransaction.origin">Origin</Translate>
            </dt>
            <dd className="col-7">
              <Translate contentKey={origin} />
            </dd>
          </>
        ) : null}
      </dl>
      {leg.notes ? (
        <div className="small text-break mt-3">
          <div className="text-muted mb-1">
            <Translate contentKey="fintrackApp.internalTransfer.product.legNotes">Movement notes</Translate>
          </div>
          {leg.notes}
        </div>
      ) : null}
      <div className="border-top pt-3 mt-3 d-flex flex-wrap align-items-center gap-2">
        {leg.categoryName ? (
          <Badge color="light" className="border text-dark fw-normal rounded-1">
            {leg.categoryName}
          </Badge>
        ) : null}
        {leg.tagNames?.map(tag => (
          <Badge key={tag} color="light" className="border text-dark fw-normal" pill>
            {tag}
          </Badge>
        ))}
        {!leg.categoryName && !leg.tagNames?.length ? (
          <span className="text-muted small">
            <Translate contentKey="fintrackApp.internalTransfer.product.noClassification">No category or tags</Translate>
          </span>
        ) : null}
      </div>
      <Link className="btn btn-sm btn-outline-secondary mt-3" to={`/financial-transaction/${leg.transactionId}`}>
        <Translate contentKey="fintrackApp.internalTransfer.product.viewTransaction">View transaction</Translate>
      </Link>
    </div>
  );
};
