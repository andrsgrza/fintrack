import React, { FormEvent, useMemo, useRef, useState } from 'react';
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
import { ProductConditionLogicControl } from 'app/shared/ui/product-condition-logic-control';
import { ProductSection } from 'app/shared/ui/product-page';
import { ProductStatusToggle } from 'app/shared/ui/product-status-control';

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
  const [active, setActive] = useState(initialValue?.active ?? true);
  const [conditionOperator, setConditionOperator] = useState<RuleConditionLogic>(
    (initialValue?.conditionOperator ?? RuleConditionLogic.ALL) as RuleConditionLogic,
  );
  const [resultingDescription, setResultingDescription] = useState(initialValue?.resultingDescription ?? '');
  const [conditions, setConditions] = useState<IDescriptionNormalizationRuleConfiguredCondition[]>(initialValue?.conditions ?? []);
  const [savingAction, setSavingAction] = useState<ContextualRuleSaveAction | null>(null);
  const savingRef = useRef(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const configuredRule = useMemo<IDescriptionNormalizationRuleConfigured>(
    () => ({
      name,
      description,
      active,
      conditionOperator: conditionOperator as keyof typeof RuleConditionLogic,
      resultingDescription,
      conditions: normalizeConditions(conditions),
    }),
    [active, conditionOperator, conditions, description, name, resultingDescription],
  );

  const validationErrors = useMemo<ValidationErrors>(() => {
    const errors: ValidationErrors = {};
    if (!name.trim()) errors.name = translate('entity.validation.required');
    if (!resultingDescription.trim()) errors.resultingDescription = translate('entity.validation.required');
    if (conditions.length === 0) errors.conditions = translate('fintrackApp.descriptionNormalizationRule.conditionsRequired');
    conditions.forEach((condition, index) => {
      if (!condition.operator) errors[`condition-operator-${index}`] = translate('entity.validation.required');
      if (!condition.value?.trim()) errors[`condition-value-${index}`] = translate('entity.validation.required');
    });
    return errors;
  }, [conditions, name, resultingDescription]);

  const updateCondition = (index: number, update: Partial<IDescriptionNormalizationRuleConfiguredCondition>) => {
    setConditions(current =>
      current.map((condition, conditionIndex) => (conditionIndex === index ? { ...condition, ...update } : condition)),
    );
  };

  const save = async (event: FormEvent, action: ContextualRuleSaveAction) => {
    event.preventDefault();
    if (savingRef.current) return;

    setSubmitAttempted(true);
    if (Object.keys(validationErrors).length > 0) return;

    setServerError(null);
    savingRef.current = true;
    setSavingAction(action);
    try {
      const response = initialValue?.id
        ? await updateConfiguredDescriptionNormalizationRule(initialValue.id, configuredRule)
        : await createConfiguredDescriptionNormalizationRule(configuredRule);
      await onSaved(response.data, action);
    } catch (requestError) {
      setServerError(serverErrorMessage(requestError) ?? translate('fintrackApp.descriptionNormalizationRule.configuredSaveFailed'));
    } finally {
      savingRef.current = false;
      setSavingAction(null);
    }
  };

  const completeConditions = conditions.filter(condition => condition.operator && condition.value?.trim());
  const previewReady = Boolean(
    name.trim() && resultingDescription.trim() && completeConditions.length === conditions.length && conditions.length > 0,
  );
  const hasValidationErrors = Object.keys(validationErrors).length > 0;

  return (
    <Form noValidate onSubmit={event => save(event, 'SAVE')} data-cy="descriptionNormalizationRuleContextualForm">
      {serverError ? (
        <Alert color="danger" fade={false} role="alert" data-cy="descriptionNormalizationRuleFormError">
          {serverError}
        </Alert>
      ) : null}
      {submitAttempted && hasValidationErrors ? (
        <Alert
          color="danger"
          fade={false}
          className="py-2 mb-3"
          role="alert"
          data-cy="descriptionNormalizationRuleValidationSummary"
          data-testid="descriptionNormalizationRuleValidationSummary"
        >
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.validationSummary">
            Review the highlighted rule configuration sections before saving.
          </Translate>
        </Alert>
      ) : null}

      <ProductSection
        title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.identity">Identity</Translate>}
        dataCy="descriptionNormalizationRuleIdentitySection"
      >
        <FormGroup>
          <Label for="contextual-description-rule-name">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.name">Name</Translate>
          </Label>
          <Input
            id="contextual-description-rule-name"
            data-cy="descriptionNormalizationRuleName"
            value={name}
            onChange={event => setName(event.target.value)}
            invalid={submitAttempted && Boolean(validationErrors.name)}
            required
            maxLength={100}
          />
          {submitAttempted && validationErrors.name ? <FormFeedback>{validationErrors.name}</FormFeedback> : null}
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
      </ProductSection>

      <ProductSection
        className="mt-3"
        title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.matchingConditions">Matching conditions</Translate>}
        dataCy="descriptionNormalizationRuleMatchingSection"
      >
        <ProductConditionLogicControl
          idPrefix="contextual-description-rule"
          dataCyPrefix="descriptionNormalizationRule"
          value={conditionOperator}
          onChange={setConditionOperator}
          label={translate('fintrackApp.descriptionNormalizationRule.conditionOperator')}
          allLabel={translate('fintrackApp.RuleConditionLogic.ALL')}
          anyLabel={translate('fintrackApp.RuleConditionLogic.ANY')}
          help={translate(
            `fintrackApp.descriptionNormalizationRule.editor.${conditionOperator === RuleConditionLogic.ALL ? 'allHelp' : 'anyHelp'}`,
          )}
        />
        <div className="d-flex justify-content-between align-items-center gap-2 mb-2">
          <h4 className="h6 mb-0">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.conditions">Conditions</Translate>
          </h4>
          <Button
            type="button"
            color="secondary"
            outline
            size="sm"
            data-cy="addDescriptionNormalizationRuleCondition"
            onClick={() => setConditions(current => [...current, createCondition()])}
          >
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.addCondition">Add condition</Translate>
          </Button>
        </div>
        {conditions.length === 0 ? (
          <p
            className={submitAttempted && validationErrors.conditions ? 'small text-danger mb-0' : 'text-muted mb-0'}
            data-cy="descriptionNormalizationRuleConditionsGuidance"
            data-testid="descriptionNormalizationRuleConditionsGuidance"
          >
            <Translate
              contentKey={
                submitAttempted && validationErrors.conditions
                  ? 'fintrackApp.descriptionNormalizationRule.conditionsRequired'
                  : 'fintrackApp.descriptionNormalizationRule.editor.noConditions'
              }
            >
              Add at least one condition to define when this rule matches the original imported description.
            </Translate>
          </p>
        ) : null}
        {conditions.map((condition, index) => (
          <div className="border rounded-2 p-3 mt-2" key={index} data-testid={`contextualDescriptionRuleCondition-${index}`}>
            <FormGroup>
              <Label for={`contextual-description-rule-condition-operator-${index}`}>
                <Translate contentKey="fintrackApp.descriptionNormalizationRuleCondition.operator">Operator</Translate>
              </Label>
              <Input
                id={`contextual-description-rule-condition-operator-${index}`}
                data-cy={`descriptionNormalizationRuleConditionOperator-${index}`}
                type="select"
                value={condition.operator ?? ''}
                invalid={submitAttempted && Boolean(validationErrors[`condition-operator-${index}`])}
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
              {submitAttempted && validationErrors[`condition-operator-${index}`] ? (
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
                invalid={submitAttempted && Boolean(validationErrors[`condition-value-${index}`])}
                onChange={event => updateCondition(index, { value: event.target.value })}
                required
                maxLength={1000}
              />
              {submitAttempted && validationErrors[`condition-value-${index}`] ? (
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
              outline
              size="sm"
              data-cy={`deleteDescriptionNormalizationRuleCondition-${index}`}
              data-testid={`deleteDescriptionNormalizationRuleCondition-${index}`}
              onClick={() => setConditions(current => current.filter((_, item) => item !== index))}
            >
              <Translate contentKey="entity.action.delete">Delete</Translate>
            </Button>
          </div>
        ))}
      </ProductSection>

      <ProductSection
        className="mt-3"
        title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.result">Results</Translate>}
        dataCy="descriptionNormalizationRuleResultsSection"
      >
        <FormGroup className="mb-0">
          <Label for="contextual-description-rule-result">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.resultingDescription">Resulting description</Translate>
          </Label>
          <Input
            id="contextual-description-rule-result"
            data-cy="descriptionNormalizationRuleResult"
            data-testid="contextualDescriptionRuleResult"
            value={resultingDescription}
            invalid={submitAttempted && Boolean(validationErrors.resultingDescription)}
            onChange={event => setResultingDescription(event.target.value)}
            required
            maxLength={500}
          />
          {submitAttempted && validationErrors.resultingDescription ? (
            <FormFeedback>{validationErrors.resultingDescription}</FormFeedback>
          ) : null}
          <FormText>
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.resultHelp">
              A matching original description is replaced with this value.
            </Translate>
          </FormText>
        </FormGroup>
      </ProductSection>

      <ProductSection
        className="mt-3"
        title={
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.configurationSummary">Configuration summary</Translate>
        }
        dataCy="descriptionNormalizationRuleConfigurationPreview"
      >
        <FormText className="d-block mb-3">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.configurationSummaryHelp">
            This describes the rule configuration; it does not evaluate an imported row.
          </Translate>
        </FormText>
        {previewReady ? (
          <>
            <p className="mb-1">
              <strong>
                <Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.previewIf">IF original description</Translate>
              </strong>
              {completeConditions.map((condition, index) => (
                <React.Fragment key={`${condition.operator}-${condition.value}-${index}`}>
                  {index > 0
                    ? ` ${translate(`fintrackApp.descriptionNormalizationRule.summary.${conditionOperator === RuleConditionLogic.ALL ? 'and' : 'or'}`)} `
                    : ' '}
                  {translate(`fintrackApp.descriptionNormalizationRule.summaryOperators.${condition.operator}`)} “{condition.value?.trim()}”
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
              Add a name, result, and valid condition to review this configuration summary.
            </Translate>
          </p>
        )}
      </ProductSection>

      <ProductSection
        className="mt-3"
        title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.editor.status">Status</Translate>}
        dataCy="descriptionNormalizationRuleStatusSection"
      >
        <ProductStatusToggle
          id="contextual-description-rule-active"
          name="descriptionNormalizationRuleActive"
          active={active}
          onActiveChange={setActive}
          label={translate('fintrackApp.descriptionNormalizationRule.active')}
          help={translate('fintrackApp.descriptionNormalizationRule.editor.statusHelp')}
          activeLabel={translate('fintrackApp.descriptionNormalizationRule.status.active')}
          inactiveLabel={translate('fintrackApp.descriptionNormalizationRule.status.inactive')}
          dataCyPrefix="descriptionNormalizationRule"
          disabled={savingAction !== null}
        />
      </ProductSection>

      <FormText className="d-block my-3">
        <Translate contentKey="fintrackApp.descriptionNormalizationRule.priorityServerManagedHelp">
          Rule order is managed from the rules list. New rules are added last.
        </Translate>
      </FormText>
      <div className="d-flex flex-wrap gap-2">
        <Button type="button" color="secondary" outline onClick={onCancel} disabled={savingAction !== null}>
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
