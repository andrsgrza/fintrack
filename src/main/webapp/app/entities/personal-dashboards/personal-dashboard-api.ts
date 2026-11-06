import axios from 'axios';

import {
  ExplorerVisualization,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';

const api = 'api/personal-dashboards';

export type OfficialDashboardTemplateId = 'financial-overview' | 'movement-analysis' | 'accounts-and-position';

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
  templateId: OfficialDashboardTemplateId;
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

/** A persisted widget stores only its canonical analytical definition and presentation choices. */
export type PersonalDashboardWidgetAdd = {
  expectedVersion: number;
  title: string;
  description?: string | null;
  definition: SemanticQueryDefinition;
  visualizationType: ExplorerVisualization;
  visualizationOptions: Record<string, unknown>;
  width: PersonalDashboardWidget['width'];
  visible: boolean;
  clientRequestId: string;
};

export type PersonalDashboardWidgetUpdate = Omit<PersonalDashboardWidgetAdd, 'clientRequestId'>;

export type PersonalDashboardLayout = {
  expectedVersion: number;
  widgets: Array<{
    widgetId: number;
    width: PersonalDashboardWidget['width'];
    visible: boolean;
  }>;
};

export type PersonalDashboardMetadataUpdate = {
  expectedVersion: number;
  name: string;
  description?: string | null;
};

export type PersonalDashboardDuplicate = {
  expectedVersion: number;
  name: string;
  description?: string | null;
  clientRequestId: string;
};

/** Exactly one effective selection: an owned dashboard or a backend-governed immutable official template. */
export type PersonalDashboardDefault = {
  dashboardId: number | null;
  officialTemplateId: OfficialDashboardTemplateId | null;
  version: number | null;
};

export const listPersonalDashboards = async (): Promise<PersonalDashboardSummary[]> =>
  (await axios.get<PersonalDashboardSummary[]>(api)).data;

export const getPersonalDashboard = async (dashboardId: string | number): Promise<PersonalDashboard> =>
  (await axios.get<PersonalDashboard>(`${api}/${dashboardId}`)).data;

export const createPersonalDashboard = async (command: PersonalDashboardCreate): Promise<PersonalDashboard> =>
  (await axios.post<PersonalDashboard>(api, command)).data;

export const copyOfficialDashboardTemplate = async (command: PersonalDashboardTemplateCopy): Promise<PersonalDashboard> =>
  (await axios.post<PersonalDashboard>(`${api}/from-template`, command)).data;

export const addPersonalDashboardWidget = async (
  dashboardId: string | number,
  command: PersonalDashboardWidgetAdd,
): Promise<PersonalDashboard> => (await axios.post<PersonalDashboard>(`${api}/${dashboardId}/widgets`, command)).data;

export const updatePersonalDashboardWidget = async (
  dashboardId: string | number,
  widgetId: string | number,
  command: PersonalDashboardWidgetUpdate,
): Promise<PersonalDashboard> => (await axios.put<PersonalDashboard>(`${api}/${dashboardId}/widgets/${widgetId}`, command)).data;

export const savePersonalDashboardLayout = async (
  dashboardId: string | number,
  command: PersonalDashboardLayout,
): Promise<PersonalDashboard> => (await axios.put<PersonalDashboard>(`${api}/${dashboardId}/layout`, command)).data;

export const updatePersonalDashboardMetadata = async (
  dashboardId: string | number,
  command: PersonalDashboardMetadataUpdate,
): Promise<PersonalDashboard> => (await axios.patch<PersonalDashboard>(`${api}/${dashboardId}/metadata`, command)).data;

export const duplicatePersonalDashboard = async (
  dashboardId: string | number,
  command: PersonalDashboardDuplicate,
): Promise<PersonalDashboard> => (await axios.post<PersonalDashboard>(`${api}/${dashboardId}/duplicate`, command)).data;

export const deletePersonalDashboard = async (dashboardId: string | number, expectedVersion: number): Promise<void> => {
  await axios.delete(`${api}/${dashboardId}`, { params: { expectedVersion } });
};

export const getPersonalDashboardDefault = async (): Promise<PersonalDashboardDefault> =>
  (await axios.get<PersonalDashboardDefault>(`${api}/default`)).data;

export const setPersonalDashboardDefault = async (
  expectedVersion: number | null,
  dashboardId: number | null,
  officialTemplateId: OfficialDashboardTemplateId | null = null,
): Promise<PersonalDashboardDefault> =>
  (await axios.put<PersonalDashboardDefault>(`${api}/default`, { expectedVersion, dashboardId, officialTemplateId })).data;
