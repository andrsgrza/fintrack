import React, { FormEvent, useMemo, useState } from 'react';
import { Alert, Button, Form, FormFeedback, FormGroup, FormText, Input, Label } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import {
  createConfiguredDescriptionNormalizationRule,
  updateConfiguredDescriptionNormalizationRule,
} from 'app/entities/description-normalization-rule/description-normalization-rule-configured.service';
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

type ValidationErrors = Record<string, string>;

const createCondition = (): IDescriptionNormalizationRuleConfiguredCondition => ({
  operator: DescriptionNormalizationRuleOperator.CONTAINS,
  value: '',
  caseSensitive: false,
});

const normalizeConditions = (conditions: IDescriptionNormalizationRuleConfiguredCondition[]) =>
  conditions.map((condition, position) => ({ ...condition, position }));

const serverErrorMessage = error => error?.response?.data?.detail ?? error?.response?.data?.message;

/**
 * Product configured editor shared by standalone create/edit and Transaction Ingestion contextual creation.
 * It deliberately keeps this product-facing aggregate command separate from the generic condition CRUD endpoints.
 */
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
  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({});

  const configuredRule = useMemo<IDescriptionNormalizationRuleConfigured>(
    () => ({
      name,
      description,
      active,
      conditionOperator,
      resultingDescription,
      conditions: normalizeConditions(conditions),
    }),
    [active, conditionOperator, conditions, description, name, resultingDescription],
  );

  const updateCondition = (index: number, update: Partial<IDescriptionNormalizationRuleConfiguredCondition>) => {
    setConditions(current =>
      current.map((condition, conditionIndex) => (conditionIndex === index ? { ...condition, ...update } : condition)),
    );
  };

  const validateDraft = (): ValidationErrors => {
    const errors: ValidationErrors = {};
    if (!name.trim()) errors.name = translate('entity.validation.required');
    if (!resultingDescription.trim()) errors.resultingDescription = translate('entity.validation.required');
    if (active && conditions.length === 0) {
      errors.conditions = translate('fintrackApp.descriptionNormalizationRule.activeRequiresCondition');
    }
    conditions.forEach((condition, index) => {
      if (!condition.operator) errors[`condition-operator-${index}`] = translate('entity.validation.required');
      if (!condition.value?.trim()) errors[`condition-value-${index}`] = translate('entity.validation.required');
    });
    return errors;
  };

  const save = async (event: FormEvent, action: ContextualRuleSaveAction) => {
    event.preventDefault();
    const draftErrors = validateDraft();
    setValidationErrors(draftErrors);
    if (Object.keys(draftErrors).length > 0) {
      setError(translate('fintrackApp.descriptionNormalizationRule.configuredValidationFailed'));
      return;
    }

    setError(null);
    setSavingAction(action);
    try {
      const response = initialValue?.id
        ? await updateConfiguredDescriptionNormalizationRule(initialValue.id, configuredRule)
        : await createConfiguredDescriptionNormalizationRule(configuredRule);
      await onSaved(response.data, action);
    } catch (requestError) {
      setError(serverErrorMessage(requestError) ?? translate('fintrackApp.descriptionNormalizationRule.configuredSaveFailed'));
    } finally {
      setSavingAction(null);
    }
  };

  const completeConditions = conditions.filter(condition => condition.operator && condition.value?.trim());
  const previewReady = Boolean(
    name.trim() && resultingDescription.trim() && completeConditions.length === conditions.length && conditions.length > 0,
  );

  return (
    <Form noValidate onSubmit={event => save(event, 'SAVE')} data-cy="descriptionNormalizationRuleContextualForm">
      {error ? (
        <Alert color="danger" fade={false} role="alert" data-cy="descriptionNormalizationRuleFormError">
          {error}
        </Alert>
      ) : null}

      <section className="mb-4" aria-labelledby="description-normalization-rule-identity-heading">
        <h3 className="h5" id="description-normalization-rule-identity-heading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.identity">Identity</Translate>
        </h3>
        <FormGroup>
          <Label for="contextual-description-rule-name">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.name">Name</Translate>
          </Label>
          <Input
            id="contextual-description-rule-name"
            data-cy="descriptionNormalizationRuleName"
            value={name}
            onChange={event => setName(event.target.value)}
            invalid={Boolean(validationErrors.name)}
            required
            maxLength={100}
          />
          {validationErrors.name ? <FormFeedback>{validationErrors.name}</FormFeedback> : null}
        </FormGroup>
        <FormGroup className="mb-0">
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
      </section>

      <section className="mb-4" aria-labelledby="description-normalization-rule-matching-heading">
        <h3 className="h5" id="description-normalization-rule-matching-heading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.matching">Matching</Translate>
        </h3>
        <FormGroup>
          <Label for="contextual-description-rule-condition-operator">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditionOperator">Condition operator</Translate>
          </Label>
          <Input
            id="contextual-description-rule-condition-operator"
            data-cy="descriptionNormalizationRuleConditionLogic"
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
          <FormText>
            <Translate
              contentKey={`fintrackApp.descriptionNormalizationRule.editor.${conditionOperator === RuleConditionLogic.ALL ? 'allHelp' : 'anyHelp'}`}
            >
              Every condition must match the original description.
            </Translate>
          </FormText>
        </FormGroup>
        <div className="d-flex justify-content-between align-items-center mb-2">
          <h4 className="h6 mb-0">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditions">Conditions</Translate>
          </h4>
          <Button
            type="button"
            color="secondary"
            size="sm"
            data-cy="addDescriptionNormalizationRuleCondition"
            onClick={() => setConditions(current => [...current, createCondition()])}
          >
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.addCondition">Add condition</Translate>
          </Button>
        </div>
        {validationErrors.conditions ? (
          <Alert color="warning" fade={false}>
            {validationErrors.conditions}
          </Alert>
        ) : null}
        {conditions.length === 0 ? (
          <p className="text-muted mb-0">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.noConditions">No conditions have been added.</Translate>
          </p>
        ) : null}
        {conditions.map((condition, index) => (
          <div className="border rounded p-3 mb-2" key={index} data-testid={`contextualDescriptionRuleCondition-${index}`}>
            <FormGroup>
              <Label for={`contextual-description-rule-condition-operator-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.operator">Operator</Translate>
              </Label>
              <Input
                id={`contextual-description-rule-condition-operator-${index}`}
                data-cy={`descriptionNormalizationRuleConditionOperator-${index}`}
                type="select"
                value={condition.operator ?? ''}
                invalid={Boolean(validationErrors[`condition-operator-${index}`])}
                onChange={event =>
                  updateCondition(index, { operator: event.target.value as keyof typeof DescriptionNormalizationRuleOperator })
                }
              >
                {Object.keys(DescriptionNormalizationRuleOperator).map(value => (
                  <option value={value} key={value}>
                    {translate(`fintrackApp.descriptionNormalizationRule.operators.${value}`)}
                  </option>
                ))}
              </Input>
              {validationErrors[`condition-operator-${index}`] ? (
                <FormFeedback>{validationErrors[`condition-operator-${index}`]}</FormFeedback>
              ) : null}
            </FormGroup>
            <FormGroup>
              <Label for={`contextual-description-rule-condition-value-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.value">Value</Translate>
              </Label>
              <Input
                id={`contextual-description-rule-condition-value-${index}`}
                data-cy={`descriptionNormalizationRuleConditionValue-${index}`}
                data-testid={`contextualDescriptionRuleConditionValue-${index}`}
                value={condition.value ?? ''}
                invalid={Boolean(validationErrors[`condition-value-${index}`])}
                onChange={event => updateCondition(index, { value: event.target.value })}
                required
                maxLength={1000}
              />
              {validationErrors[`condition-value-${index}`] ? (
                <FormFeedback>{validationErrors[`condition-value-${index}`]}</FormFeedback>
              ) : null}
              {condition.operator === DescriptionNormalizationRuleOperator.REGEX ? (
                <FormText>
                  <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.regexHelp">
                    Advanced: use a valid Java regular expression.
                  </Translate>
                </FormText>
              ) : null}
            </FormGroup>
            <FormGroup check className="mb-3">
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
              data-cy={`deleteDescriptionNormalizationRuleCondition-${index}`}
              data-testid={`deleteDescriptionNormalizationRuleCondition-${index}`}
              onClick={() => setConditions(current => current.filter((_, item) => item !== index))}
            >
              <Translate contentKey="entity.action.delete">Delete</Translate>
            </Button>
          </div>
        ))}
      </section>

      <section className="mb-4" aria-labelledby="description-normalization-rule-result-heading">
        <h3 className="h5" id="description-normalization-rule-result-heading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.result">Result</Translate>
        </h3>
        <FormGroup className="mb-0">
          <Label for="contextual-description-rule-result">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.resultingDescription">Resulting description</Translate>
          </Label>
          <Input
            id="contextual-description-rule-result"
            data-cy="descriptionNormalizationRuleResult"
            data-testid="contextualDescriptionRuleResult"
            value={resultingDescription}
            invalid={Boolean(validationErrors.resultingDescription)}
            onChange={event => setResultingDescription(event.target.value)}
            required
            maxLength={500}
          />
          {validationErrors.resultingDescription ? <FormFeedback>{validationErrors.resultingDescription}</FormFeedback> : null}
          <FormText>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.resultHelp">
              A matching original description is replaced with this value.
            </Translate>
          </FormText>
        </FormGroup>
      </section>

      <section className="mb-4" aria-labelledby="description-normalization-rule-status-heading">
        <h3 className="h5" id="description-normalization-rule-status-heading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.status">Status</Translate>
        </h3>
        <FormGroup check className="mb-0">
          <Input
            id="contextual-description-rule-active"
            data-cy="descriptionNormalizationRuleActive"
            type="checkbox"
            checked={active}
            onChange={event => setActive(event.target.checked)}
          />
          <Label check for="contextual-description-rule-active">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.active">Active</Translate>
          </Label>
          <FormText>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.statusHelp">
              Active rules participate in future import normalization; saving does not reprocess history.
            </Translate>
          </FormText>
        </FormGroup>
      </section>

      <section
        className="border rounded bg-light p-3 mb-4"
        aria-labelledby="description-normalization-rule-preview-heading"
        data-cy="descriptionNormalizationRuleConfigurationPreview"
      >
        <h3 className="h5" id="description-normalization-rule-preview-heading">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.preview">Configuration preview</Translate>
        </h3>
        {previewReady ? (
          <>
            <p className="mb-1">
              <strong>
                <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.previewIf">IF original description</Translate>
              </strong>
              {completeConditions.map((condition, index) => (
                <React.Fragment key={`${condition.operator}-${condition.value}-${index}`}>
                  {index > 0 ? ` ${translate(`fintrackApp.RuleConditionLogic.${conditionOperator}`)} ` : ' '}
                  {translate(`fintrackApp.descriptionNormalizationRule.operators.${condition.operator}`)} “{condition.value?.trim()}”
                </React.Fragment>
              ))}
            </p>
            <p className="mb-0">
              <strong>
                <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.previewThen">THEN replace it with</Translate>
              </strong>{' '}
              “{resultingDescription.trim()}”
            </p>
          </>
        ) : (
          <p className="mb-0 text-muted">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.previewIncomplete">
              Complete the name, result, and every condition to see this configuration summary.
            </Translate>
          </p>
        )}
      </section>

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
