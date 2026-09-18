import React from 'react';
import { translate } from 'react-jhipster';

import { ProductValidatedField } from './product-validated-form';

export const isHexColor = (color?: string | null) => Boolean(color && /^#[0-9A-Fa-f]{6}$/.test(color));

type HexColorControlProps = {
  name: string;
  pickerId: string;
  colorInputId: string;
  colorValue: string;
  onChange: (color: string) => void;
  pickerLabel: string;
  colorLabel: string;
  dataCyPrefix: string;
};

export const HexColorControl = ({
  name,
  pickerId,
  colorInputId,
  colorValue,
  onChange,
  pickerLabel,
  colorLabel,
  dataCyPrefix,
}: HexColorControlProps) => {
  const pickerColor = isHexColor(colorValue) ? colorValue : '#000000';

  return (
    <fieldset
      className="border rounded-3 px-3 pb-3 pt-2 m-0"
      data-cy={`${dataCyPrefix}ColorControl`}
      data-testid={`${dataCyPrefix}ColorControl`}
    >
      <legend className="float-none w-auto px-1 mb-0 fs-6">{colorLabel}</legend>
      <div
        className="color-control-row d-flex flex-column flex-sm-row align-items-sm-center gap-2"
        data-cy={`${dataCyPrefix}ColorControlRow`}
        data-testid={`${dataCyPrefix}ColorControlRow`}
      >
        <div className="color-picker-control d-flex flex-shrink-0 align-items-center justify-content-center">
          <label className="visually-hidden" htmlFor={pickerId}>
            {pickerLabel}
          </label>
          <input
            id={pickerId}
            className="form-control form-control-color color-picker-control-input"
            data-cy={`${dataCyPrefix}ColorPicker`}
            type="color"
            value={pickerColor}
            onChange={event => onChange(event.target.value)}
            aria-label={pickerLabel}
          />
        </div>
        <div className="color-hex-control flex-grow-1 min-w-0">
          <ProductValidatedField
            label={colorLabel}
            labelHidden
            id={colorInputId}
            name={name}
            data-cy="color"
            type="text"
            value={colorValue}
            onChange={event => onChange(event.target.value)}
            validate={{
              pattern: {
                value: /^#[0-9A-Fa-f]{6}$/,
                message: translate('entity.validation.pattern', { pattern: '^#[0-9A-Fa-f]{6}$' }),
              },
            }}
          />
        </div>
      </div>
    </fieldset>
  );
};
