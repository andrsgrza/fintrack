import React, { useEffect, useState } from 'react';
import { Button, FormText } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionOrigin } from 'app/shared/model/enumerations/transaction-origin.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import {
  getAllowedOperators,
  getSecondValueInputKind,
  getValueInputKind,
  requiresSecondValue,
  supportsCaseSensitive as fieldSupportsCaseSensitive,
} from '../transaction-rule-condition-form-helpers';

interface TransactionRuleConditionFormSectionProps {
  initialCondition?: ITransactionRuleCondition;
  isNew: boolean;
  fixedTransactionRuleId?: string | number;
  defaultPosition?: number;
  showParentSelector?: boolean;
  parentDisabled?: boolean;
  requestedTransactionRuleId?: string | number;
  onSubmit: (entity: ITransactionRuleCondition) => void;
  onCancel?: () => void;
  submitting?: boolean;
  submitLabelKey?: string;
  submitLabel?: string;
}

export const TransactionRuleConditionFormSection = ({
  initialCondition,
  isNew,
  fixedTransactionRuleId,
  defaultPosition,
  showParentSelector = true,
  parentDisabled = false,
  requestedTransactionRuleId,
  onSubmit,
  onCancel,
  submitting = false,
  submitLabelKey = 'entity.action.save',
  submitLabel = 'Save',
}: TransactionRuleConditionFormSectionProps) => {
  const dispatch = useAppDispatch();
  const transactionRules = useAppSelector(state => state.transactionRule.entities);
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const transactionRuleFieldValues = Object.keys(TransactionRuleField);
  const [selectedField, setSelectedField] = useState(TransactionRuleField.DESCRIPTION);
  const [selectedOperator, setSelectedOperator] = useState(RuleOperator.EQUALS);
  const [value, setValue] = useState('');
  const [secondValue, setSecondValue] = useState('');
  const [caseSensitive, setCaseSensitive] = useState(false);
  const [selectedTransactionRuleId, setSelectedTransactionRuleId] = useState<string | number | undefined>(
    fixedTransactionRuleId ?? initialCondition?.transactionRule?.id ?? requestedTransactionRuleId ?? undefined,
  );

  const allowedOperators = getAllowedOperators(selectedField);
  const isBetweenOperator = requiresSecondValue(selectedOperator);
  const supportsCaseSensitive = fieldSupportsCaseSensitive(selectedField);
  const valueInputKind = getValueInputKind(selectedField, selectedOperator);
  const secondValueInputKind = getSecondValueInputKind(selectedField);

  useEffect(() => {
    setSelectedField((initialCondition?.field as TransactionRuleField) ?? TransactionRuleField.DESCRIPTION);
    setSelectedOperator((initialCondition?.operator as RuleOperator) ?? RuleOperator.EQUALS);
    setValue(initialCondition?.value ?? '');
    setSecondValue(initialCondition?.secondValue ?? '');
    setCaseSensitive(Boolean(initialCondition?.caseSensitive));
    setSelectedTransactionRuleId(
      fixedTransactionRuleId ?? initialCondition?.transactionRule?.id ?? requestedTransactionRuleId ?? undefined,
    );
  }, [
    initialCondition?.id,
    initialCondition?.field,
    initialCondition?.operator,
    initialCondition?.value,
    initialCondition?.secondValue,
    initialCondition?.caseSensitive,
    initialCondition?.transactionRule?.id,
    fixedTransactionRuleId,
    requestedTransactionRuleId,
  ]);

  useEffect(() => {
    if (selectedField === TransactionRuleField.ACCOUNT) {
      dispatch(getFinancialAccounts({}));
    }
  }, [selectedField]);

  const resetConditionValues = (nextField: TransactionRuleField, nextOperator: RuleOperator) => {
    setValue('');
    setSecondValue('');
    if (!fieldSupportsCaseSensitive(nextField)) {
      setCaseSensitive(false);
    }
    if (!requiresSecondValue(nextOperator)) {
      setSecondValue('');
    }
  };

  const handleFieldChange = event => {
    const nextField = event.target.value as TransactionRuleField;
    const nextAllowedOperators = getAllowedOperators(nextField);
    const nextOperator = nextAllowedOperators.includes(selectedOperator) ? selectedOperator : nextAllowedOperators[0];
    setSelectedField(nextField);
    setSelectedOperator(nextOperator);
    resetConditionValues(nextField, nextOperator);
  };

  const handleOperatorChange = event => {
    const nextOperator = event.target.value as RuleOperator;
    const currentInputKind = getValueInputKind(selectedField, selectedOperator);
    const nextInputKind = getValueInputKind(selectedField, nextOperator);
    setSelectedOperator(nextOperator);
    if (!requiresSecondValue(nextOperator)) {
      setSecondValue('');
    }
    if (currentInputKind !== nextInputKind) {
      setValue('');
    }
    if (!fieldSupportsCaseSensitive(selectedField)) {
      setCaseSensitive(false);
    }
  };

  const defaultValues = () => ({
    id: initialCondition?.id,
    field: initialCondition?.field ?? TransactionRuleField.DESCRIPTION,
    operator: initialCondition?.operator ?? RuleOperator.EQUALS,
    caseSensitive: initialCondition?.caseSensitive ?? false,
    position: initialCondition?.position ?? defaultPosition,
    transactionRule: fixedTransactionRuleId ?? initialCondition?.transactionRule?.id ?? requestedTransactionRuleId ?? undefined,
  });

  const saveEntity = values => {
    const position =
      values.position !== undefined && values.position !== null && values.position !== '' ? Number(values.position) : values.position;

    const entity: ITransactionRuleCondition = {
      id: initialCondition?.id ?? values.id,
      field: selectedField,
      operator: selectedOperator,
      value,
      secondValue: isBetweenOperator ? secondValue : null,
      caseSensitive: supportsCaseSensitive ? caseSensitive : false,
      position,
    };

    if (isNew) {
      const parentId = fixedTransactionRuleId ?? selectedTransactionRuleId ?? values.transactionRule;
      entity.transactionRule = transactionRules.find(it => it.id?.toString() === parentId?.toString()) ?? {
        id: Number(parentId),
      };
    }

    onSubmit(entity);
  };

  return (
    <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
      {!isNew ? (
        <ValidatedField
          name="id"
          required
          readOnly
          id="transaction-rule-condition-id"
          label={translate('global.field.id')}
          validate={{ required: true }}
        />
      ) : null}
      <ValidatedField
        label={translate('fintrackApp.transactionRuleCondition.field')}
        id="transaction-rule-condition-field"
        name="field"
        data-cy="field"
        type="select"
        value={selectedField}
        onChange={handleFieldChange}
      >
        {transactionRuleFieldValues.map(transactionRuleField => (
          <option value={transactionRuleField} key={transactionRuleField}>
            {translate(`fintrackApp.TransactionRuleField.${transactionRuleField}`)}
          </option>
        ))}
      </ValidatedField>
      <ValidatedField
        label={translate('fintrackApp.transactionRuleCondition.operator')}
        id="transaction-rule-condition-operator"
        name="operator"
        data-cy="operator"
        type="select"
        value={selectedOperator}
        onChange={handleOperatorChange}
      >
        {allowedOperators.map(ruleOperator => (
          <option value={ruleOperator} key={ruleOperator}>
            {translate(`fintrackApp.RuleOperator.${ruleOperator}`)}
          </option>
        ))}
      </ValidatedField>
      {valueInputKind === 'flow-select' ? (
        <ValidatedField
          label={translate('fintrackApp.transactionRuleCondition.value')}
          id="transaction-rule-condition-value"
          name="value"
          data-cy="value"
          type="select"
          value={value}
          onChange={event => setValue(event.target.value)}
        >
          <option value="" key="0" />
          {Object.keys(TransactionFlow).map(transactionFlow => (
            <option value={transactionFlow} key={transactionFlow}>
              {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
            </option>
          ))}
        </ValidatedField>
      ) : null}
      {valueInputKind === 'origin-select' ? (
        <ValidatedField
          label={translate('fintrackApp.transactionRuleCondition.value')}
          id="transaction-rule-condition-value"
          name="value"
          data-cy="value"
          type="select"
          value={value}
          onChange={event => setValue(event.target.value)}
        >
          <option value="" key="0" />
          {Object.keys(TransactionOrigin).map(transactionOrigin => (
            <option value={transactionOrigin} key={transactionOrigin}>
              {translate(`fintrackApp.TransactionOrigin.${transactionOrigin}`)}
            </option>
          ))}
        </ValidatedField>
      ) : null}
      {valueInputKind === 'account-select' ? (
        <ValidatedField
          label={translate('fintrackApp.transactionRuleCondition.value')}
          id="transaction-rule-condition-value"
          name="value"
          data-cy="value"
          type="select"
          value={value}
          onChange={event => setValue(event.target.value)}
        >
          <option value="" key="0" />
          {financialAccounts
            ? financialAccounts.map(account => (
                <option value={account.id} key={account.id}>
                  {account.name}
                </option>
              ))
            : null}
        </ValidatedField>
      ) : null}
      {valueInputKind === 'text' || valueInputKind === 'number' || valueInputKind === 'date' ? (
        <>
          <ValidatedField
            label={translate('fintrackApp.transactionRuleCondition.value')}
            id="transaction-rule-condition-value"
            name="value"
            data-cy="value"
            type={valueInputKind}
            value={value}
            step={valueInputKind === 'number' ? '0.01' : undefined}
            onChange={event => setValue(event.target.value)}
            validate={{
              maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
            }}
          />
          {selectedOperator === RuleOperator.IN || selectedOperator === RuleOperator.NOT_IN ? (
            <FormText>
              <Translate contentKey={`fintrackApp.transactionRuleCondition.help.${selectedField}`}>Use comma-separated values.</Translate>
            </FormText>
          ) : null}
        </>
      ) : null}
      {isBetweenOperator ? (
        <ValidatedField
          label={translate('fintrackApp.transactionRuleCondition.secondValue')}
          id="transaction-rule-condition-secondValue"
          name="secondValue"
          data-cy="secondValue"
          type={secondValueInputKind}
          value={secondValue}
          step={secondValueInputKind === 'number' ? '0.01' : undefined}
          onChange={event => setSecondValue(event.target.value)}
          validate={{
            maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
          }}
        />
      ) : null}
      {supportsCaseSensitive ? (
        <ValidatedField
          label={translate('fintrackApp.transactionRuleCondition.caseSensitive')}
          id="transaction-rule-condition-caseSensitive"
          name="caseSensitive"
          data-cy="caseSensitive"
          check
          type="checkbox"
          checked={caseSensitive}
          onChange={event => setCaseSensitive(event.target.checked)}
        />
      ) : null}
      <ValidatedField
        label={translate('fintrackApp.transactionRuleCondition.position')}
        id="transaction-rule-condition-position"
        name="position"
        data-cy="position"
        type="text"
        validate={{
          required: { value: true, message: translate('entity.validation.required') },
          min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
          validate: v => isNumber(v) || translate('entity.validation.number'),
        }}
      />
      {showParentSelector ? (
        <>
          <ValidatedField
            id="transaction-rule-condition-transactionRule"
            name="transactionRule"
            data-cy="transactionRule"
            label={translate('fintrackApp.transactionRuleCondition.transactionRule')}
            type="select"
            required
            disabled={parentDisabled}
            value={selectedTransactionRuleId}
            onChange={event => setSelectedTransactionRuleId(event.target.value)}
          >
            <option value="" key="0" />
            {transactionRules
              ? transactionRules.map(otherEntity => (
                  <option value={otherEntity.id} key={otherEntity.id}>
                    {otherEntity.name}
                  </option>
                ))
              : null}
          </ValidatedField>
          <FormText>
            <Translate contentKey="entity.validation.required">This field is required.</Translate>
          </FormText>
        </>
      ) : null}
      {onCancel ? (
        <>
          <Button type="button" color="secondary" onClick={onCancel} data-cy="cancelConditionEditButton">
            <Translate contentKey="fintrackApp.transactionRule.cancelConditionEdit">Cancel</Translate>
          </Button>
          &nbsp;
        </>
      ) : null}
      <Button
        color="primary"
        id={showParentSelector ? 'save-entity' : 'save-condition'}
        data-cy={showParentSelector ? 'entityCreateSaveButton' : 'conditionSaveButton'}
        type="submit"
        disabled={submitting}
      >
        <FontAwesomeIcon icon="save" />
        &nbsp;
        <Translate contentKey={submitLabelKey}>{submitLabel}</Translate>
      </Button>
    </ValidatedForm>
  );
};

export default TransactionRuleConditionFormSection;
