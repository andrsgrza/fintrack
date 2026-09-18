import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { usePopper } from 'react-popper';
import { Badge, Button } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';

import { ProductValidatedField } from './product-validated-form';

type ProductStatusHelpProps = {
  id: string;
  label: string;
  help: React.ReactNode;
  dataCyPrefix: string;
};

export const ProductStatusHelp = ({ id, label, help, dataCyPrefix }: ProductStatusHelpProps) => {
  const [visible, setVisible] = useState(false);
  const [targetElement, setTargetElement] = useState<HTMLButtonElement | null>(null);
  const [tooltipElement, setTooltipElement] = useState<HTMLDivElement | null>(null);
  const tooltipId = `${id}-tooltip`;
  const { styles, attributes } = usePopper(targetElement, tooltipElement, {
    placement: 'bottom-start',
    strategy: 'fixed',
    modifiers: [
      { name: 'offset', options: { offset: [0, 8] } },
      { name: 'flip', options: { fallbackPlacements: ['right-start', 'left-start', 'top-start'] } },
      { name: 'preventOverflow', options: { boundary: 'clippingParents', rootBoundary: 'viewport', padding: 8 } },
    ],
  });

  return (
    <span className="d-inline-flex" onMouseLeave={() => setVisible(false)}>
      <Button
        id={id}
        type="button"
        color="link"
        className="p-0 text-muted d-inline-flex align-items-center"
        aria-label={label}
        aria-describedby={tooltipId}
        title={label}
        innerRef={setTargetElement}
        data-cy={`${dataCyPrefix}StatusHelpButton`}
        data-testid={`${dataCyPrefix}StatusHelpButton`}
        onMouseEnter={() => setVisible(true)}
        onFocus={() => setVisible(true)}
        onBlur={() => setVisible(false)}
      >
        <FontAwesomeIcon icon={faCircleInfo} aria-hidden="true" />
      </Button>
      {visible && typeof document !== 'undefined'
        ? createPortal(
            <div ref={setTooltipElement} className="tooltip show" role="tooltip" style={styles.popper} {...attributes.popper}>
              <div
                id={tooltipId}
                className="tooltip-inner product-status-help-tooltip"
                data-cy={`${dataCyPrefix}StatusHelpTooltip`}
                data-testid={`${dataCyPrefix}StatusHelpTooltip`}
              >
                {help}
              </div>
              <span className="tooltip-arrow" data-popper-arrow style={styles.arrow} {...attributes.arrow} />
            </div>,
            document.body,
          )
        : null}
    </span>
  );
};

type CompactProductStatusControlProps = ProductStatusHelpProps & {
  name: string;
  active: boolean;
  onActiveChange: (active: boolean) => void;
  activeLabel: string;
  inactiveLabel: string;
};

export const CompactProductStatusControl = ({
  id,
  label,
  help,
  dataCyPrefix,
  name,
  active,
  onActiveChange,
  activeLabel,
  inactiveLabel,
}: CompactProductStatusControlProps) => (
  <div
    className="d-flex flex-wrap align-items-center gap-2"
    data-cy={`${dataCyPrefix}StatusControl`}
    data-testid={`${dataCyPrefix}StatusControl`}
  >
    <span className="small text-muted fw-semibold">{label}</span>
    <Badge color={active ? 'success' : 'secondary'} pill data-cy={`${dataCyPrefix}StatusValue`}>
      {active ? activeLabel : inactiveLabel}
    </Badge>
    <ProductValidatedField
      id={id}
      name={name}
      data-cy="active"
      check
      type="checkbox"
      className="form-switch mb-0"
      label={label}
      labelHidden
      aria-label={active ? activeLabel : inactiveLabel}
      onChange={event => onActiveChange(event.target.checked)}
    />
    <ProductStatusHelp id={`${id}-help`} label={label} help={help} dataCyPrefix={dataCyPrefix} />
  </div>
);
