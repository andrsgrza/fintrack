import axios from 'axios';

import {
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticQueryDefinition,
  VisualizationContext,
} from './analytics-explorer.model';
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
