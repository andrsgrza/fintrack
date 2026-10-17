import React from 'react';
import { Link } from 'react-router-dom';
import { Badge } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { CategoryPath } from 'app/entities/category/category-presentation';
import { FinancialAmount } from 'app/shared/ui/financial-amount';

type Flow = string | null | undefined;

type AccountPresentation = {
  id?: number;
  name?: string | null;
  accountType?: string | null;
  currency?: string | null;
  lastFourDigits?: string | null;
  active?: boolean | null;
};

type DraftCategoryPresentation = {
  id?: number;
  name?: string | null;
  parentName?: string | null;
  color?: string | null;
  active?: boolean | null;
};

type CompactTag = {
  id?: number;
  name?: string | null;
  color?: string | null;
  active?: boolean | null;
};

const colorIdentityStyle = (color?: string | null, accentWidth = '0.3rem'): React.CSSProperties | undefined =>
  color
    ? {
        backgroundColor: `${color}14`,
        borderColor: `${color}80`,
        borderInlineStart: `${accentWidth} solid ${color}`,
      }
    : undefined;

export const formatTransactionAccountLabel = (account?: AccountPresentation | null) => {
  if (!account?.name) {
    return '';
  }

  const parts = [
    account.name,
    account.accountType ? translate(`fintrackApp.AccountType.${account.accountType}`) : undefined,
    account.currency ?? undefined,
    account.lastFourDigits ? `••••${account.lastFourDigits}` : undefined,
    account.active === false ? translate('fintrackApp.financialAccount.status.inactive') : undefined,
  ];
  return parts.filter(Boolean).join(' · ');
};

export const TransactionFlowBadge = ({ flow }: { flow?: Flow }) => {
  if (flow !== 'IN' && flow !== 'OUT') {
    return null;
  }

  return (
    <Badge color={flow === 'IN' ? 'success' : 'secondary'} pill className="fw-normal" data-cy="transactionFlowBadge">
      <Translate contentKey={`fintrackApp.TransactionFlow.${flow}`} />
    </Badge>
  );
};

export const TransactionAmount = ({
  amount,
  currency,
  flow,
  compact = false,
}: {
  amount?: number | null;
  currency?: string | null;
  flow?: Flow;
  compact?: boolean;
}) => {
  return (
    <div className={compact ? '' : 'text-md-end'} data-cy="transactionAmount">
      <FinancialAmount amount={amount} currency={currency} flow={flow} compact={compact} dataCy="transactionAmountValue" />
      <TransactionFlowBadge flow={flow} />
    </div>
  );
};

export const TransactionAccountLabel = ({
  account,
  className = '',
}: {
  account?: AccountPresentation | IFinancialAccount | null;
  className?: string;
}) => {
  const label = formatTransactionAccountLabel(account);
  if (!label) {
    return <span className={`text-muted ${className}`.trim()}>—</span>;
  }

  const content = <span>{label}</span>;
  return account?.id ? (
    <Link to={`/financial-account/${account.id}`} className={`text-decoration-none ${className}`.trim()} data-cy="transactionAccountLink">
      {content}
    </Link>
  ) : (
    <span className={className}>{content}</span>
  );
};

const DraftCategory = ({ category }: { category: DraftCategoryPresentation }) => {
  const label = [category.parentName, category.name].filter(Boolean).join(' › ');
  const content = (
    <span
      className="d-inline-flex align-items-center rounded-1 border px-2 py-1 small fw-semibold"
      style={colorIdentityStyle(category.color, '0.35rem')}
      data-cy="transactionCategory"
      data-color-treatment="category"
    >
      {label}
      {category.active === false ? <span className="ms-1 text-muted">({translate('fintrackApp.category.status.inactive')})</span> : null}
    </span>
  );

  return category.id ? (
    <Link to={`/category/${category.id}`} className="text-decoration-none" data-cy="transactionCategoryLink">
      {content}
    </Link>
  ) : (
    content
  );
};

export const TransactionCategory = ({
  category,
  draftCategory,
}: {
  category?: ICategory | null;
  draftCategory?: DraftCategoryPresentation | null;
}) => {
  if (category) {
    return (
      <span
        className="d-inline-flex align-items-center rounded-1 border px-2 py-1 small fw-semibold"
        style={colorIdentityStyle(category.color, '0.35rem')}
        data-cy="transactionCategory"
        data-color-treatment="category"
      >
        <CategoryPath category={category} categories={[]} linkCurrent className="text-body" dataCy="transactionCategoryPath" />
        {category.active === false ? <span className="ms-1 text-muted">({translate('fintrackApp.category.status.inactive')})</span> : null}
      </span>
    );
  }

  if (draftCategory?.name) {
    return <DraftCategory category={draftCategory} />;
  }

  return (
    <span className="text-muted small" data-cy="transactionNoCategory">
      <Translate contentKey="fintrackApp.financialTransaction.product.noCategory">No category</Translate>
    </span>
  );
};

const TransactionTagChip = ({ tag, showInactiveLabel = false }: { tag: CompactTag; showInactiveLabel?: boolean }) => (
  <span
    className={`d-inline-flex align-items-center rounded-pill border px-2 py-1 small fw-semibold${tag.active === false ? ' opacity-75' : ''}`}
    data-cy="transactionTagChip"
    data-color-treatment="tag"
    title={tag.active === false ? translate('fintrackApp.tag.status.inactive') : undefined}
    style={colorIdentityStyle(tag.color)}
  >
    {tag.name}
    {tag.active === false && showInactiveLabel ? (
      <span className="ms-1 text-muted">({translate('fintrackApp.tag.status.inactive')})</span>
    ) : null}
  </span>
);

export const TransactionTagChips = ({
  tags = [],
  tagNames = [],
  maxVisible = 3,
  showEmpty = false,
  showInactiveLabel = false,
}: {
  tags?: CompactTag[] | null;
  tagNames?: string[] | null;
  maxVisible?: number;
  showEmpty?: boolean;
  showInactiveLabel?: boolean;
}) => {
  const resolvedTags = tags ?? [];
  const resolvedTagNames = tagNames ?? [];
  const visibleTags = resolvedTags.length
    ? resolvedTags.slice(0, maxVisible)
    : resolvedTagNames.slice(0, maxVisible).map(name => ({ name }));
  const total = resolvedTags.length ? resolvedTags.length : resolvedTagNames.length;

  if (!visibleTags.length) {
    return showEmpty ? (
      <span className="text-muted small" data-cy="transactionNoTags">
        <Translate contentKey="fintrackApp.financialTransaction.product.noTags">No tags</Translate>
      </span>
    ) : null;
  }

  return (
    <span className="d-inline-flex flex-wrap gap-1 align-items-center" data-cy="transactionTagChips">
      {visibleTags.map((tag, index) => (
        <TransactionTagChip key={tag.id ?? `${tag.name}-${index}`} tag={tag} showInactiveLabel={showInactiveLabel} />
      ))}
      {total > maxVisible ? (
        <Badge color="light" className="border text-dark fw-normal" pill data-cy="transactionAdditionalTags">
          +{total - maxVisible}
        </Badge>
      ) : null}
    </span>
  );
};

export const TransactionClassification = ({
  category,
  draftCategory,
  tags,
  tagNames,
  showEmptyTags = false,
}: {
  category?: ICategory | null;
  draftCategory?: DraftCategoryPresentation | null;
  tags?: CompactTag[] | null;
  tagNames?: string[] | null;
  showEmptyTags?: boolean;
}) => (
  <div className="d-flex flex-wrap gap-2 align-items-center" data-cy="transactionClassification">
    <TransactionCategory category={category} draftCategory={draftCategory} />
    <TransactionTagChips tags={tags} tagNames={tagNames} showEmpty={showEmptyTags} />
  </div>
);
