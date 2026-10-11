import {
  IDescriptionNormalizationRuleConfigured,
  IDescriptionNormalizationRuleConfiguredCondition,
} from 'app/shared/model/description-normalization-rule-configured.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

type TranslateFn = (key: string, options?: Record<string, unknown>) => string;

export const formatDescriptionNormalizationRuleStatus = (active: boolean | undefined, translate: TranslateFn) =>
  active
    ? translate('fintrackApp.descriptionNormalizationRule.status.active')
    : translate('fintrackApp.descriptionNormalizationRule.status.inactive');

export const formatDescriptionNormalizationLogic = (
  logic: IDescriptionNormalizationRuleConfigured['conditionOperator'],
  translate: TranslateFn,
) =>
  logic === RuleConditionLogic.ANY
    ? translate('fintrackApp.descriptionNormalizationRule.summary.any')
    : translate('fintrackApp.descriptionNormalizationRule.summary.all');

export const formatDescriptionNormalizationCondition = (
  condition: IDescriptionNormalizationRuleConfiguredCondition,
  translate: TranslateFn,
) => {
  const operator = condition.operator
    ? translate(`fintrackApp.descriptionNormalizationRule.operators.${condition.operator}`)
    : translate('fintrackApp.descriptionNormalizationRule.summary.unknownOperator');
  const value = condition.value ? `“${condition.value}”` : translate('fintrackApp.descriptionNormalizationRule.summary.missingValue');
  const sensitivity = condition.caseSensitive
    ? translate('fintrackApp.descriptionNormalizationRule.summary.caseSensitive')
    : translate('fintrackApp.descriptionNormalizationRule.summary.caseInsensitive');

  return `${operator} ${value} · ${sensitivity}`;
};

export const formatDescriptionNormalizationConditionSummary = (
  rule: Pick<IDescriptionNormalizationRuleConfigured, 'conditionOperator' | 'conditions'>,
  translate: TranslateFn,
  limit?: number,
) => {
  const conditions = rule.conditions ?? [];
  if (conditions.length === 0) {
    return translate('fintrackApp.descriptionNormalizationRule.summary.noConditions');
  }

  const displayedConditions = limit === undefined ? conditions : conditions.slice(0, limit);
  const joiner =
    rule.conditionOperator === RuleConditionLogic.ANY
      ? ` ${translate('fintrackApp.descriptionNormalizationRule.summary.or')} `
      : ` ${translate('fintrackApp.descriptionNormalizationRule.summary.and')} `;
  const summary = displayedConditions.map(condition => formatDescriptionNormalizationCondition(condition, translate)).join(joiner);
  const remaining = conditions.length - displayedConditions.length;

  return remaining > 0
    ? `${summary} ${translate('fintrackApp.descriptionNormalizationRule.summary.moreConditions', { count: remaining })}`
    : summary;
};
