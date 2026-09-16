import React, { FormEvent, useState } from 'react';
import { Alert, Button, Form, FormGroup, Input, Label } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { createConfiguredDescriptionNormalizationRule } from 'app/entities/description-normalization-rule/description-normalization-rule-configured.service';
import {
  IDescriptionNormalizationRuleConfigured,
  IDescriptionNormalizationRuleConfiguredCondition,
} from 'app/shared/model/description-normalization-rule-configured.model';
import { ContextualRuleSaveAction, IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { DescriptionNormalizationRuleOperator } from 'app/shared/model/enumerations/description-normalization-rule-operator.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

interface DescriptionNormalizationRuleConfiguredFormProps {
  initialValue?: IDescriptionNormalizationRuleConfigured;
  saveActions: IContextualRuleSaveAction[];
  onSaved: (rule: IDescriptionNormalizationRuleConfigured, action: ContextualRuleSaveAction) => void | Promise<void>;
  onCancel: () => void;
}

const createCondition = (): IDescriptionNormalizationRuleConfiguredCondition => ({
  operator: DescriptionNormalizationRuleOperator.CONTAINS,
  value: '',
  caseSensitive: false,
});

const normalizeConditions = (conditions: IDescriptionNormalizationRuleConfiguredCondition[]) =>
  conditions.map((condition, position) => ({ ...condition, position }));

const serverErrorMessage = error => error?.response?.data?.detail ?? error?.response?.data?.message;

/** Reusable atomic-create editor used by the rule page and Transaction Ingestion context. */
export const DescriptionNormalizationRuleConfiguredForm = ({
  initialValue,
  saveActions,
  onSaved,
  onCancel,
}: DescriptionNormalizationRuleConfiguredFormProps) => {
  const [name, setName] = useState(initialValue?.name ?? '');
  const [description, setDescription] = useState(initialValue?.description ?? '');
  const [active, setActive] = useState(initialValue?.active ?? false);
  const [conditionOperator, setConditionOperator] = useState(initialValue?.conditionOperator ?? RuleConditionLogic.ALL);
  const [resultingDescription, setResultingDescription] = useState(initialValue?.resultingDescription ?? '');
  const [conditions, setConditions] = useState<IDescriptionNormalizationRuleConfiguredCondition[]>(initialValue?.conditions ?? []);
  const [savingAction, setSavingAction] = useState<ContextualRuleSaveAction | null>(null);
  const [error, setError] = useState<string | null>(null);

  const updateCondition = (index: number, update: Partial<IDescriptionNormalizationRuleConfiguredCondition>) => {
    setConditions(current =>
      current.map((condition, conditionIndex) => (conditionIndex === index ? { ...condition, ...update } : condition)),
    );
  };

  const save = async (event: FormEvent, action: ContextualRuleSaveAction) => {
    event.preventDefault();
    setError(null);
    setSavingAction(action);
    try {
      const response = await createConfiguredDescriptionNormalizationRule({
        name,
        description,
        active,
        conditionOperator,
        resultingDescription,
        conditions: normalizeConditions(conditions),
      });
      await onSaved(response.data, action);
    } catch (requestError) {
      setError(serverErrorMessage(requestError) ?? translate('fintrackApp.transactionIngestion.workflow.ruleCreation.saveFailed'));
    } finally {
      setSavingAction(null);
    }
  };

  return (
    <Form onSubmit={event => save(event, 'SAVE')} data-cy="descriptionNormalizationRuleContextualForm">
      {error ? <Alert color="danger">{error}</Alert> : null}
      <FormGroup>
        <Label for="contextual-description-rule-name">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.name">Name</Translate>
        </Label>
        <Input
          id="contextual-description-rule-name"
          value={name}
          onChange={event => setName(event.target.value)}
          required
          maxLength={100}
        />
      </FormGroup>
      <FormGroup>
        <Label for="contextual-description-rule-description">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.description">Description</Translate>
        </Label>
        <Input
          id="contextual-description-rule-description"
          type="textarea"
          value={description}
          onChange={event => setDescription(event.target.value)}
          maxLength={500}
        />
      </FormGroup>
      <FormGroup>
        <Label for="contextual-description-rule-result">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.resultingDescription">Resulting description</Translate>
        </Label>
        <Input
          id="contextual-description-rule-result"
          data-testid="contextualDescriptionRuleResult"
          value={resultingDescription}
          onChange={event => setResultingDescription(event.target.value)}
          required
          maxLength={500}
        />
      </FormGroup>
      <FormGroup>
        <Label for="contextual-description-rule-condition-operator">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditionOperator">Condition operator</Translate>
        </Label>
        <Input
          id="contextual-description-rule-condition-operator"
          type="select"
          value={conditionOperator}
          onChange={event => setConditionOperator(event.target.value as keyof typeof RuleConditionLogic)}
        >
          {Object.keys(RuleConditionLogic).map(value => (
            <option value={value} key={value}>
              {translate(`fintrackApp.RuleConditionLogic.${value}`)}
            </option>
          ))}
        </Input>
      </FormGroup>
      <section className="mb-3" data-testid="contextualDescriptionRuleConditions">
        <div className="d-flex justify-content-between align-items-center mb-2">
          <strong>
            <Translate contentKey="fintrackApp.transactionRule.conditions">Conditions</Translate>
          </strong>
          <Button type="button" color="secondary" size="sm" onClick={() => setConditions(current => [...current, createCondition()])}>
            <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.transactionRule.addCondition">Add condition</Translate>
          </Button>
        </div>
        {conditions.map((condition, index) => (
          <div className="border rounded p-2 mb-2" key={index} data-testid={`contextualDescriptionRuleCondition-${index}`}>
            <FormGroup>
              <Label for={`contextual-description-rule-condition-operator-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.operator">Operator</Translate>
              </Label>
              <Input
                id={`contextual-description-rule-condition-operator-${index}`}
                type="select"
                value={condition.operator ?? ''}
                onChange={event =>
                  updateCondition(index, { operator: event.target.value as keyof typeof DescriptionNormalizationRuleOperator })
                }
              >
                {Object.keys(DescriptionNormalizationRuleOperator).map(value => (
                  <option value={value} key={value}>
                    {translate(`fintrackApp.DescriptionNormalizationRuleOperator.${value}`)}
                  </option>
                ))}
              </Input>
            </FormGroup>
            <FormGroup>
              <Label for={`contextual-description-rule-condition-value-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.value">Value</Translate>
              </Label>
              <Input
                id={`contextual-description-rule-condition-value-${index}`}
                data-testid={`contextualDescriptionRuleConditionValue-${index}`}
                value={condition.value ?? ''}
                onChange={event => updateCondition(index, { value: event.target.value })}
                required
                maxLength={1000}
              />
            </FormGroup>
            <FormGroup check className="mb-2">
              <Input
                id={`contextual-description-rule-condition-case-${index}`}
                type="checkbox"
                checked={Boolean(condition.caseSensitive)}
                onChange={event => updateCondition(index, { caseSensitive: event.target.checked })}
              />
              <Label check for={`contextual-description-rule-condition-case-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.caseSensitive">Case sensitive</Translate>
              </Label>
            </FormGroup>
            <Button
              type="button"
              color="danger"
              size="sm"
              onClick={() => setConditions(current => current.filter((_, item) => item !== index))}
            >
              <Translate contentKey="entity.action.delete">Delete</Translate>
            </Button>
          </div>
        ))}
      </section>
      <FormGroup check className="mb-3">
        <Input
          id="contextual-description-rule-active"
          type="checkbox"
          checked={active}
          onChange={event => setActive(event.target.checked)}
        />
        <Label check for="contextual-description-rule-active">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.active">Active</Translate>
        </Label>
      </FormGroup>
      <div className="d-flex flex-wrap gap-2">
        <Button type="button" color="secondary" onClick={onCancel} disabled={savingAction !== null}>
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        {saveActions.map(saveAction => (
          <Button
            type={saveAction.action === 'SAVE' ? 'submit' : 'button'}
            color="primary"
            key={saveAction.action}
            data-cy={saveAction.dataCy}
            data-testid={saveAction.dataCy}
            disabled={savingAction !== null}
            onClick={saveAction.action === 'SAVE' ? undefined : event => save(event, saveAction.action)}
          >
            {savingAction === saveAction.action ? <FontAwesomeIcon icon="sync" spin /> : <FontAwesomeIcon icon="save" />}{' '}
            <Translate contentKey={saveAction.labelKey}>{saveAction.label}</Translate>
          </Button>
        ))}
      </div>
    </Form>
  );
};

export default DescriptionNormalizationRuleConfiguredForm;
