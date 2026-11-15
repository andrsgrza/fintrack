import axios from 'axios';
import React, { FormEvent, useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Label, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import {
  ExplorerVisualization,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';
import { copy } from 'app/entities/analytics-explorer/analytics-explorer-utils';
import { visualizationEligibility } from 'app/entities/analytics-explorer/analytics-visualization-compatibility';

import {
  addPersonalDashboardWidget,
  createPersonalDashboard,
  getPersonalDashboard,
  listPersonalDashboards,
  PersonalDashboard,
  PersonalDashboardSummary,
  PersonalDashboardWidget,
  updatePersonalDashboardWidget,
} from './personal-dashboard-api';

type CommandError = { code?: string; message?: string };

const incompatibleVisualizationMessage = 'The visualization is incompatible with the semantic result shape.';

export type WidgetEditingTarget = {
  dashboardId: number;
  dashboardName: string;
  widgetId: number;
  expectedVersion: number;
  title: string;
  description: string | null;
  width: PersonalDashboardWidget['width'];
  visible: boolean;
  visualizationOptions: Record<string, unknown>;
};

type Props = {
  definition: SemanticQueryDefinition;
  context: VisualizationContext;
  visualization: ExplorerVisualization;
  seriesLimit: number | 'ALL';
  locale: string;
  editing: WidgetEditingTarget | null;
  preferredDashboardId: number | null;
  onClose: () => void;
  onSaved: (dashboard: PersonalDashboard) => void;
  onReloadEditing: () => void;
};

const requestId = (prefix: string) =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

const defaultTitle = (definition: SemanticQueryDefinition, locale: string) =>
  definition.projections.length === 1 && definition.projections[0]?.alias
    ? definition.projections[0].alias.replace(/_/g, ' ')
    : copy(locale, 'Nueva visualización', 'New visualization');

const widthFor = (visualization: ExplorerVisualization): PersonalDashboardWidget['width'] =>
  visualization === 'KPI' ? 'COMPACT' : visualization === 'LINE' ? 'FULL' : 'MEDIUM';

const safeSeriesLimit = (value: unknown): number | 'ALL' =>
  typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : 'ALL';

const commandError = (error: unknown): CommandError => {
  if (axios.isAxiosError<CommandError>(error)) return error.response?.data ?? { message: error.message };
  return { message: error instanceof Error ? error.message : 'The request could not be completed.' };
};

const errorMessage = (locale: string, error: CommandError) => {
  if (error.code === 'VERSION_CONFLICT') {
    return copy(
      locale,
      'Este dashboard cambió en otra sesión. Recarga la visualización guardada antes de volver a intentar.',
      'This dashboard changed in another session. Reload the saved visualization before trying again.',
    );
  }
  if (error.code === 'NOT_FOUND') {
    return copy(
      locale,
      'El dashboard o la visualización ya no existe. Elige otro destino o vuelve al dashboard.',
      'The dashboard or visualization no longer exists. Choose another destination or return to the dashboard.',
    );
  }
  if (error.code === 'INVALID_PAYLOAD' && error.message === incompatibleVisualizationMessage) {
    return copy(
      locale,
      'Esta visualización no es compatible con los cálculos y desgloses de la consulta.',
      'This visualization is not compatible with the query calculations and breakdowns.',
    );
  }
  return error.message ?? copy(locale, 'No fue posible guardar la visualización.', 'The visualization could not be saved.');
};

/**
 * A compact destination/title step around the existing Explorer. It deliberately receives no result rows and only
 * sends the canonical AST plus rendering choices to the established dashboard commands.
 */
const PersonalDashboardWidgetSaveDialog = ({
  definition,
  context,
  visualization,
  seriesLimit,
  locale,
  editing,
  preferredDashboardId,
  onClose,
  onSaved,
  onReloadEditing,
}: Props) => {
  const [dashboards, setDashboards] = useState<PersonalDashboardSummary[]>([]);
  const [selectedDashboardId, setSelectedDashboardId] = useState<number | null>(preferredDashboardId);
  const [creatingDestination, setCreatingDestination] = useState(false);
  const [createdDashboard, setCreatedDashboard] = useState<PersonalDashboard | null>(null);
  const [title, setTitle] = useState(editing?.title ?? defaultTitle(definition, locale));
  const [description, setDescription] = useState(editing?.description ?? '');
  const [dashboardName, setDashboardName] = useState('');
  const [dashboardDescription, setDashboardDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<CommandError | null>(null);
  const addRequestId = useRef(requestId('add-widget'));
  const createRequestId = useRef(requestId('create-dashboard'));

  useEffect(() => {
    if (editing) return;
    let active = true;
    void listPersonalDashboards()
      .then(items => {
        if (!active) return;
        setDashboards(items);
        setSelectedDashboardId(current => current ?? items[0]?.id ?? null);
        setCreatingDestination(items.length === 0);
      })
      .catch(loadError => active && setError(commandError(loadError)));
    return () => {
      active = false;
    };
  }, [editing]);

  const visualizationOptions = {
    ...(editing?.visualizationOptions ?? {}),
    seriesLimit,
  };

  const selectedDashboard = createdDashboard ?? dashboards.find(dashboard => dashboard.id === selectedDashboardId) ?? null;
  const visualizationCapability = visualizationEligibility(definition, null, locale).find(option => option.option === visualization);

  const refreshDestinations = async () => {
    setError(null);
    try {
      if (editing) {
        onReloadEditing();
        return;
      }
      const items = await listPersonalDashboards();
      setDashboards(items);
      if (selectedDashboardId) {
        const fresh = await getPersonalDashboard(selectedDashboardId);
        setDashboards(current => current.map(item => (item.id === fresh.id ? { ...item, version: fresh.version } : item)));
      }
    } catch (loadError) {
      setError(commandError(loadError));
    }
  };

  const persist = async (event: FormEvent) => {
    event.preventDefault();
    if (saving || !visualizationCapability?.enabled) return;
    setSaving(true);
    setError(null);
    try {
      if (editing) {
        const dashboard = await updatePersonalDashboardWidget(editing.dashboardId, editing.widgetId, {
          expectedVersion: editing.expectedVersion,
          title,
          description: description || null,
          definition,
          visualizationType: visualization,
          visualizationOptions,
          width: editing.width,
          visible: editing.visible,
        });
        onSaved(dashboard);
        return;
      }

      let destination = createdDashboard;
      if (!destination && creatingDestination) {
        destination = await createPersonalDashboard({
          name: dashboardName,
          description: dashboardDescription || null,
          // This is an explicit dashboard default chosen while creating it; it is never placed in the widget AST.
          defaultContext: context,
          clientRequestId: createRequestId.current,
        });
        setCreatedDashboard(destination);
        setSelectedDashboardId(destination.id);
      }
      if (!destination && selectedDashboardId) destination = await getPersonalDashboard(selectedDashboardId);
      if (!destination) {
        setError({ message: copy(locale, 'Selecciona o crea un dashboard de destino.', 'Select or create a destination dashboard.') });
        setSaving(false);
        return;
      }
      const dashboard = await addPersonalDashboardWidget(destination.id, {
        expectedVersion: destination.version,
        title,
        description: description || null,
        definition,
        visualizationType: visualization,
        visualizationOptions,
        width: widthFor(visualization),
        visible: true,
        clientRequestId: addRequestId.current,
      });
      onSaved(dashboard);
    } catch (requestError) {
      setError(commandError(requestError));
      setSaving(false);
    }
  };

  const conflict = error?.code === 'VERSION_CONFLICT';
  return (
    <Modal isOpen toggle={saving ? undefined : onClose} centered aria-labelledby="save-visualization-title">
      <form onSubmit={persist}>
        <ModalHeader toggle={saving ? undefined : onClose} id="save-visualization-title">
          {editing
            ? copy(locale, 'Guardar cambios de visualización', 'Save visualization changes')
            : copy(locale, 'Guardar visualización', 'Save visualization')}
        </ModalHeader>
        <ModalBody>
          <p className="small text-muted">
            {editing
              ? copy(
                  locale,
                  'Actualizarás esta misma visualización. Su posición, tamaño y visibilidad se conservarán.',
                  'You are updating this same visualization. Its position, size, and visibility will be preserved.',
                )
              : copy(
                  locale,
                  'Se guarda la consulta y su forma de presentarla, nunca los importes ni los resultados de la vista previa.',
                  'The query and its presentation are saved, never preview amounts or result rows.',
                )}
          </p>
          {!visualizationCapability?.enabled ? (
            <Alert color="warning" data-cy="widgetSaveVisualizationIncompatibility">
              {copy(
                locale,
                `Esta visualización no se puede guardar con esta consulta: ${visualizationCapability?.reason}`,
                `This visualization cannot be saved with this query: ${visualizationCapability?.reason}`,
              )}
            </Alert>
          ) : null}
          {error ? <Alert color="danger">{errorMessage(locale, error)}</Alert> : null}
          {conflict ? (
            <Button
              type="button"
              color="secondary"
              outline
              size="sm"
              className="mb-3"
              disabled={saving}
              onClick={() => void refreshDestinations()}
            >
              {copy(locale, 'Recargar visualización guardada', 'Reload saved visualization')}
            </Button>
          ) : null}
          {editing ? (
            <div className="border rounded-2 bg-light p-2 mb-3 small" data-cy="widgetSaveEditingDestination">
              {copy(locale, 'Dashboard:', 'Dashboard:')} <strong>{editing.dashboardName}</strong>
            </div>
          ) : creatingDestination && !createdDashboard ? (
            <section className="border rounded-2 p-3 mb-3" data-cy="widgetSaveCreateDestination">
              <div className="d-flex justify-content-between align-items-center gap-2 mb-2">
                <strong>{copy(locale, 'Nuevo dashboard', 'New dashboard')}</strong>
                {dashboards.length ? (
                  <Button type="button" size="sm" color="link" disabled={saving} onClick={() => setCreatingDestination(false)}>
                    {copy(locale, 'Elegir existente', 'Choose existing')}
                  </Button>
                ) : null}
              </div>
              <Label for="widget-save-dashboard-name" className="small fw-semibold">
                {copy(locale, 'Nombre del dashboard', 'Dashboard name')}
              </Label>
              <Input
                id="widget-save-dashboard-name"
                value={dashboardName}
                required
                maxLength={100}
                disabled={saving}
                onChange={event => setDashboardName(event.target.value)}
                data-cy="widgetSaveDashboardName"
              />
              <Label for="widget-save-dashboard-description" className="small fw-semibold mt-2">
                {copy(locale, 'Descripción opcional', 'Optional description')}
              </Label>
              <Input
                id="widget-save-dashboard-description"
                type="textarea"
                rows={2}
                maxLength={500}
                value={dashboardDescription}
                disabled={saving}
                onChange={event => setDashboardDescription(event.target.value)}
              />
              <p className="small text-muted mb-0 mt-2">
                {copy(
                  locale,
                  'El contexto actual será el contexto inicial del dashboard; no se guardará dentro de esta visualización.',
                  'The current context becomes the dashboard’s initial context; it is not saved inside this visualization.',
                )}
              </p>
            </section>
          ) : (
            <section className="mb-3" data-cy="widgetSaveExistingDestination">
              <div className="d-flex justify-content-between align-items-center gap-2">
                <Label for="widget-save-dashboard" className="fw-semibold mb-1">
                  {copy(locale, 'Guardar en', 'Save to')}
                </Label>
                <Button
                  type="button"
                  size="sm"
                  color="link"
                  disabled={saving || !!createdDashboard}
                  onClick={() => setCreatingDestination(true)}
                >
                  {copy(locale, 'Crear dashboard nuevo', 'Create new dashboard')}
                </Button>
              </div>
              <Input
                id="widget-save-dashboard"
                type="select"
                value={selectedDashboardId ?? ''}
                required
                disabled={saving || !!createdDashboard}
                onChange={event => setSelectedDashboardId(Number(event.target.value))}
                data-cy="widgetSaveDashboard"
              >
                <option value="" disabled>
                  {copy(locale, 'Selecciona un dashboard', 'Select a dashboard')}
                </option>
                {dashboards.map(dashboard => (
                  <option key={dashboard.id} value={dashboard.id}>
                    {dashboard.name}
                  </option>
                ))}
              </Input>
              {createdDashboard ? (
                <p className="small text-success mt-2 mb-0">
                  {copy(
                    locale,
                    'Dashboard creado. Puedes reintentar guardar esta visualización sin crear otro.',
                    'Dashboard created. You can retry saving this visualization without creating another.',
                  )}
                </p>
              ) : null}
            </section>
          )}
          <Label for="widget-save-title" className="fw-semibold">
            {copy(locale, 'Título de la visualización', 'Visualization title')}
          </Label>
          <Input
            id="widget-save-title"
            value={title}
            required
            maxLength={160}
            autoFocus={!editing}
            disabled={saving}
            onChange={event => setTitle(event.target.value)}
            data-cy="widgetSaveTitle"
          />
          <Label for="widget-save-description" className="fw-semibold mt-3">
            {copy(locale, 'Descripción opcional', 'Optional description')}
          </Label>
          <Input
            id="widget-save-description"
            type="textarea"
            rows={2}
            maxLength={500}
            value={description}
            disabled={saving}
            onChange={event => setDescription(event.target.value)}
            data-cy="widgetSaveDescription"
          />
          <p className="small text-muted mb-0 mt-2">
            {copy(
              locale,
              `Presentación: ${visualization}. ${seriesLimit === 'ALL' ? 'Todas las series disponibles.' : `Hasta ${seriesLimit} series visibles.`}`,
              `Presentation: ${visualization}. ${seriesLimit === 'ALL' ? 'All available series.' : `Up to ${seriesLimit} visible series.`}`,
            )}
          </p>
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline disabled={saving} onClick={onClose}>
            {copy(locale, 'Cancelar', 'Cancel')}
          </Button>
          <Button
            color="primary"
            type="submit"
            disabled={
              saving || !visualizationCapability?.enabled || !title.trim() || (!editing && !creatingDestination && !selectedDashboard)
            }
            data-cy="widgetSaveSubmit"
          >
            {saving
              ? copy(locale, 'Guardando…', 'Saving…')
              : editing
                ? copy(locale, 'Guardar cambios', 'Save changes')
                : copy(locale, 'Guardar visualización', 'Save visualization')}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export const savedSeriesLimit = safeSeriesLimit;
export default PersonalDashboardWidgetSaveDialog;
