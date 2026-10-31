import axios from 'axios';

import {
  AnalyticsReference,
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticQueryDefinition,
  VisualizationContext,
} from './analytics-explorer.model';
import { ICategory } from 'app/shared/model/category.model';
import { ITag } from 'app/shared/model/tag.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';

const api = 'api/analytics/semantic';

export const getSemanticAnalyticsCatalog = async (): Promise<SemanticAnalyticsCatalog> =>
  (await axios.get<SemanticAnalyticsCatalog>(`${api}/catalog`)).data;

export const formatSemanticQuery = async (definition: SemanticQueryDefinition): Promise<string> =>
  (await axios.post<{ text: string }>(`${api}/queries/format`, definition)).data.text;

export const parseSemanticQuery = async (text: string): Promise<{ definition: SemanticQueryDefinition; canonicalText: string }> =>
  (await axios.post<{ definition: SemanticQueryDefinition; canonicalText: string }>(`${api}/queries/parse`, { text })).data;

export const executeSemanticQuery = async (
  definition: SemanticQueryDefinition,
  context: VisualizationContext,
): Promise<SemanticAnalyticsResult> =>
  (await axios.post<SemanticAnalyticsResult>(`${api}/queries/execute`, { schemaVersion: definition.schemaVersion, definition, context }))
    .data;

export const getExplorerAccounts = async (): Promise<IFinancialAccountSelectable[]> =>
  (await axios.get<IFinancialAccountSelectable[]>('api/financial-accounts/selectable')).data;

const referencesFrom = (items: Array<ICategory | ITag>, kind: 'CATEGORY' | 'TAG'): AnalyticsReference[] => {
  const references = items
    .filter((item): item is (ICategory | ITag) & { id: number; name: string } => item.id != null && !!item.name)
    .map(item => ({
      id: item.id,
      name: item.name,
      description: item.description,
      color: item.color,
      active: item.active,
      parentId: kind === 'CATEGORY' ? ((item as ICategory).parentCategory?.id ?? null) : null,
      system: kind === 'CATEGORY' ? !(item as ICategory).user?.id : false,
    }));
  if (kind !== 'CATEGORY') return references;
  const byId = new Map(references.map(reference => [reference.id, reference]));
  const pathFor = (reference: AnalyticsReference, visited = new Set<number>()): string[] => {
    if (visited.has(reference.id)) return [reference.name];
    visited.add(reference.id);
    const parent = reference.parentId == null ? undefined : byId.get(reference.parentId);
    return parent ? [...pathFor(parent, visited), reference.name] : [reference.name];
  };
  return references.map(reference => ({ ...reference, path: pathFor(reference) }));
};

/** Existing owner-scoped resources supply human-readable classification controls; the executor re-validates IDs. */
export const getExplorerCategories = async (): Promise<AnalyticsReference[]> =>
  referencesFrom((await axios.get<ICategory[]>('api/categories?sort=name,asc')).data, 'CATEGORY');

export const getExplorerTags = async (): Promise<AnalyticsReference[]> =>
  referencesFrom((await axios.get<ITag[]>('api/tags?sort=name,asc')).data, 'TAG');
