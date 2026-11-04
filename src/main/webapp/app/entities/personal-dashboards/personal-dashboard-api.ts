import axios from 'axios';

import {
  ExplorerVisualization,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';

const api = 'api/personal-dashboards';

export type PersonalDashboardSummary = {
  id: number;
  name: string;
  description: string | null;
  version: number;
  widgetCount: number;
  updatedAt: string;
};

export type PersonalDashboardWidget = {
  id: number;
  title: string;
  description: string | null;
  definition: SemanticQueryDefinition;
  visualizationType: ExplorerVisualization;
  visualizationOptions: Record<string, unknown>;
  position: number;
  width: 'COMPACT' | 'MEDIUM' | 'FULL';
  visible: boolean;
};

export type PersonalDashboard = PersonalDashboardSummary & {
  schemaVersion: number;
  defaultContext: VisualizationContext;
  widgets: PersonalDashboardWidget[];
  createdAt: string;
};

export type PersonalDashboardCreate = {
  name: string;
  description?: string | null;
  defaultContext: VisualizationContext;
  clientRequestId: string;
};

export type PersonalDashboardTemplateCopy = PersonalDashboardCreate & {
  templateId: 'financial-overview' | 'movement-analysis' | 'accounts-and-position';
  widgets: Array<{
    title: string;
    description?: string | null;
    definition: SemanticQueryDefinition;
    visualizationType: ExplorerVisualization;
    visualizationOptions: Record<string, unknown>;
    width: 'COMPACT' | 'MEDIUM' | 'FULL';
    visible: boolean;
  }>;
};

export const listPersonalDashboards = async (): Promise<PersonalDashboardSummary[]> =>
  (await axios.get<PersonalDashboardSummary[]>(api)).data;

export const getPersonalDashboard = async (dashboardId: string | number): Promise<PersonalDashboard> =>
  (await axios.get<PersonalDashboard>(`${api}/${dashboardId}`)).data;

export const createPersonalDashboard = async (command: PersonalDashboardCreate): Promise<PersonalDashboard> =>
  (await axios.post<PersonalDashboard>(api, command)).data;

export const copyOfficialDashboardTemplate = async (command: PersonalDashboardTemplateCopy): Promise<PersonalDashboard> =>
  (await axios.post<PersonalDashboard>(`${api}/from-template`, command)).data;
