export type ContextualRuleSaveAction = 'SAVE' | 'REEVALUATE_ROW' | 'REEVALUATE_ALL';

export interface IContextualRuleSaveAction {
  action: ContextualRuleSaveAction;
  labelKey: string;
  label: string;
  dataCy: string;
}
