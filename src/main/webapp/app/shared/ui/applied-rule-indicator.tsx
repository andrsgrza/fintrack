import React, { useId, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { Dropdown, DropdownMenu, DropdownToggle, UncontrolledTooltip } from 'reactstrap';
import { Link } from 'react-router-dom';

export type AppliedRuleReference = {
  id?: number | null;
  name?: string | null;
};

/**
 * A compact provenance marker for a value that a rule applied. It deliberately
 * keeps the full rule name on demand so review rows remain scannable.
 */
export const AppliedRuleIndicator = ({
  attribution,
  href,
  dataCy = 'appliedRuleIndicator',
}: {
  attribution?: AppliedRuleReference | null;
  href: string;
  dataCy?: string;
}) => {
  const generatedId = useId().replace(/:/g, '');
  const [menuOpen, setMenuOpen] = useState(false);
  if (!attribution?.name) return null;

  const tooltip = `Aplicada por: ${attribution.name}`;
  const triggerId = `${dataCy}-${generatedId}`;
  const ruleTarget = attribution.id ? `${href}/${attribution.id}/view` : undefined;

  return (
    <span className="applied-rule-indicator" data-cy={dataCy} data-testid={dataCy}>
      <Dropdown direction="down" className="d-inline-block" isOpen={menuOpen} toggle={() => setMenuOpen(open => !open)}>
        <DropdownToggle
          id={triggerId}
          type="button"
          color="secondary"
          outline
          className="applied-rule-indicator-toggle"
          aria-label={tooltip}
          title={tooltip}
          data-cy={`${dataCy}Toggle`}
        >
          <FontAwesomeIcon icon={faWandMagicSparkles} aria-hidden="true" />
          <span className="visually-hidden">{tooltip}</span>
        </DropdownToggle>
        <DropdownMenu end className="applied-rule-indicator-menu" data-cy={`${dataCy}Menu`}>
          <span className="d-block small text-muted px-3 py-2">
            Aplicada por:{' '}
            {ruleTarget ? (
              <Link className="transaction-batch-rule-link" to={ruleTarget}>
                {attribution.name}
              </Link>
            ) : (
              attribution.name
            )}
          </span>
        </DropdownMenu>
      </Dropdown>
      {!menuOpen && (
        <UncontrolledTooltip target={triggerId} placement="top" className="product-status-help-tooltip">
          {tooltip}
        </UncontrolledTooltip>
      )}
    </span>
  );
};
