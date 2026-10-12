import React from 'react';
import { FormGroup, FormText, Input, Label } from 'reactstrap';

import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

type ProductConditionLogicControlProps = {
  idPrefix: string;
  dataCyPrefix: string;
  value: RuleConditionLogic;
  onChange: (value: RuleConditionLogic) => void;
  label: React.ReactNode;
  allLabel: string;
  anyLabel: string;
  help: React.ReactNode;
  anyDisabled?: boolean;
  restrictionHelp?: React.ReactNode;
  restrictionDataCy?: string;
};

/** Shared, accessible ALL/ANY interaction used by the distinct rule editors. */
export const ProductConditionLogicControl = ({
  idPrefix,
  dataCyPrefix,
  value,
  onChange,
  label,
  allLabel,
  anyLabel,
  help,
  anyDisabled = false,
  restrictionHelp,
  restrictionDataCy,
}: ProductConditionLogicControlProps) => (
  <fieldset className="mb-3" data-cy={`${dataCyPrefix}ConditionLogic`}>
    <legend className="h6 mb-1">{label}</legend>
    <div className="d-flex flex-wrap gap-3">
      <FormGroup check className="mb-0">
        <Input
          id={`${idPrefix}-all`}
          data-cy="conditionLogicAll"
          type="radio"
          name={`${idPrefix}-condition-logic`}
          value={RuleConditionLogic.ALL}
          checked={value === RuleConditionLogic.ALL}
          onChange={() => onChange(RuleConditionLogic.ALL)}
        />
        <Label check for={`${idPrefix}-all`}>
          {allLabel}
        </Label>
      </FormGroup>
      <FormGroup check className="mb-0">
        <Input
          id={`${idPrefix}-any`}
          data-cy="conditionLogicAny"
          type="radio"
          name={`${idPrefix}-condition-logic`}
          value={RuleConditionLogic.ANY}
          checked={value === RuleConditionLogic.ANY}
          disabled={anyDisabled}
          onChange={() => onChange(RuleConditionLogic.ANY)}
        />
        <Label check for={`${idPrefix}-any`}>
          {anyLabel}
        </Label>
      </FormGroup>
    </div>
    <FormText className="d-block mt-2">{help}</FormText>
    {restrictionHelp ? (
      <FormText className="d-block mt-2" data-cy={restrictionDataCy}>
        {restrictionHelp}
      </FormText>
    ) : null}
  </fieldset>
);
