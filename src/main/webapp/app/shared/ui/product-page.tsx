import React from 'react';
import { DropdownMenu, DropdownToggle, UncontrolledDropdown } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEllipsisVertical } from '@fortawesome/free-solid-svg-icons';

type ProductPageProps = {
  children: React.ReactNode;
  wide?: boolean;
  className?: string;
};

export const ProductPage = ({ children, wide = false, className = '' }: ProductPageProps) => (
  <div className={`mx-auto pb-4 ${className}`} style={{ maxWidth: wide ? '1180px' : '840px' }}>
    {children}
  </div>
);

type ProductPageHeaderProps = {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  metadata?: React.ReactNode;
  actions?: React.ReactNode;
  headingId?: string;
  dataCy?: string;
  accentColor?: string | null;
  accentDataCy?: string;
};

export const ProductPageHeader = ({
  title,
  subtitle,
  metadata,
  actions,
  headingId,
  dataCy,
  accentColor,
  accentDataCy,
}: ProductPageHeaderProps) => (
  <header
    className="d-flex flex-column flex-md-row align-items-md-start justify-content-between gap-3 border-bottom pb-3 mb-4"
    data-cy={accentDataCy}
    data-testid={accentDataCy}
    style={accentColor ? { borderInlineStart: `0.35rem solid ${accentColor}`, paddingInlineStart: '1rem' } : undefined}
  >
    <div>
      <h2 id={headingId} className="mb-1" data-cy={dataCy}>
        {title}
      </h2>
      {metadata ? <div className="d-flex flex-wrap align-items-center gap-2 mb-1">{metadata}</div> : null}
      {subtitle ? <p className="text-muted mb-0">{subtitle}</p> : null}
    </div>
    {actions ? <div className="d-flex flex-wrap align-items-center gap-2 flex-shrink-0">{actions}</div> : null}
  </header>
);

type ProductSectionProps = {
  title: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  dataCy?: string;
};

export const ProductSection = ({ title, children, className = '', dataCy }: ProductSectionProps) => (
  <section className={`card border-0 shadow-sm h-100 ${className}`} data-cy={dataCy} data-testid={dataCy}>
    <div className="card-body p-3 p-md-4">
      <h3 className="h6 text-uppercase text-muted fw-semibold small mb-3">{title}</h3>
      {children}
    </div>
  </section>
);

type ProductActionsMenuProps = {
  label: string;
  children: React.ReactNode;
  dataCy?: string;
};

export const ProductActionsMenu = ({ label, children, dataCy = 'entityActionsMenu' }: ProductActionsMenuProps) => (
  <UncontrolledDropdown className="d-inline-block">
    <DropdownToggle color="secondary" outline size="sm" aria-label={label} title={label} data-cy={`${dataCy}Toggle`}>
      <FontAwesomeIcon icon={faEllipsisVertical} />
      <span className="visually-hidden">{label}</span>
    </DropdownToggle>
    <DropdownMenu end data-cy={dataCy}>
      {children}
    </DropdownMenu>
  </UncontrolledDropdown>
);
