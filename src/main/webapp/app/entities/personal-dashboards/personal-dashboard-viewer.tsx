import { DndContext, DragEndEvent, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors } from '@dnd-kit/core';
import { SortableContext, rectSortingStrategy, sortableKeyboardCoordinates, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  DropdownItem,
  DropdownMenu,
  DropdownToggle,
  Input,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  UncontrolledDropdown,
} from 'reactstrap';

import { useAppSelector } from 'app/config/store';
import { AnalyticsContextControls } from 'app/entities/analytics-explorer/analytics-context-controls';
import { getExplorerAccounts, getSemanticAnalyticsCatalog } from 'app/entities/analytics-explorer/analytics-explorer-api';
import {
  SemanticAnalyticsCatalog,
  SemanticQueryDefinition,
  VisualizationContext,
} from 'app/entities/analytics-explorer/analytics-explorer.model';
import { AnalyticsResultsPanel } from 'app/entities/analytics-explorer/analytics-results-panel';
import {
  categoryGroupingIndex,
  groupCategoryChildren,
  isTemporalGrouping,
  replaceTemporalGrouping,
  temporalOptionsFor,
} from 'app/entities/analytics-explorer/analytics-grouping';
import { copy, datasetById, label } from 'app/entities/analytics-explorer/analytics-explorer-utils';
import { dashboardDiagnostics, useDashboardWidgetResults } from 'app/entities/recommended-dashboards/recommended-dashboards';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  deletePersonalDashboard,
  deletePersonalDashboardWidget,
  duplicatePersonalDashboard,
  duplicatePersonalDashboardWidget,
  getPersonalDashboard,
  getPersonalDashboardDefault,
  PersonalDashboard,
  PersonalDashboardDefault,
  PersonalDashboardWidget,
  savePersonalDashboardLayout,
  setPersonalDashboardDefault,
  updatePersonalDashboardMetadata,
  updatePersonalDashboardWidget,
} from './personal-dashboard-api';
import {
  DashboardLayoutItem,
  DashboardLayoutPreset,
  applyLayoutPreset,
  changeLayoutVisibility,
  changeLayoutWidth,
  isChartVisualization,
  layoutFromWidgets,
  moveLayoutItem,
  sameLayout,
} from './personal-dashboard-layout';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type DrillState = { definition: SemanticQueryDefinition; label: string };
type ViewerWidget = PersonalDashboardWidget & { definition: SemanticQueryDefinition; trail: DrillState[]; contextPolicy?: 'CURRENT_ONLY' };

const widthClass = (width: PersonalDashboardWidget['width']) =>
  width === 'FULL' ? 'col-12' : width === 'COMPACT' ? 'col-12 col-md-6 col-xl-4' : 'col-12 col-md-6 col-xl-6';
const asOfDatasets = new Set(['FINANCIAL_POSITION_SNAPSHOT', 'ACCOUNT_POSITION_SNAPSHOT']);
const formatAsOf = (locale: string, isoDate: string) =>
  new Intl.DateTimeFormat(locale.startsWith('es') ? 'es-MX' : 'en-US', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(`${isoDate}T12:00:00Z`),
  );
const policyFor = (widget: PersonalDashboardWidget): 'CURRENT_ONLY' | undefined =>
  widget.visualizationOptions?.contextPolicy === 'CURRENT_ONLY' ? 'CURRENT_ONLY' : undefined;
const seriesLimitFor = (widget: PersonalDashboardWidget): number | 'ALL' => {
  const value = widget.visualizationOptions?.seriesLimit;
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : 'ALL';
};
const temporalOptionsForDefinition = (catalog: SemanticAnalyticsCatalog, definition: SemanticQueryDefinition) => {
  const dataset = datasetById(catalog, definition.dataset);
  return dataset ? temporalOptionsFor(dataset) : [];
};
const newRequestId = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `dashboard-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

type ManagementMode = 'metadata' | 'duplicate' | 'delete' | null;
type WidgetManagementMode = 'metadata' | 'delete' | null;

const PersonalDashboardManagementDialog = ({
  dashboard,
  locale,
  mode,
  onClose,
  onUpdated,
  onDuplicated,
  onDeleted,
  onReload,
}: {
  dashboard: PersonalDashboard | null;
  locale: string;
  mode: ManagementMode;
  onClose: () => void;
  onUpdated: (dashboard: PersonalDashboard) => void;
  onDuplicated: (dashboard: PersonalDashboard) => void;
  onDeleted: () => void;
  onReload: () => void;
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!dashboard) return;
    setName(mode === 'duplicate' ? `${dashboard.name} (${copy(locale, 'copia', 'copy')})` : dashboard.name);
    setDescription(dashboard.description ?? '');
    setError(null);
  }, [dashboard, locale, mode]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!dashboard || !mode || saving) return;
    setSaving(true);
    setError(null);
    try {
      if (mode === 'metadata') {
        onUpdated(
          await updatePersonalDashboardMetadata(dashboard.id, {
            expectedVersion: dashboard.version,
            name,
            description: description || null,
          }),
        );
      } else if (mode === 'duplicate') {
        onDuplicated(
          await duplicatePersonalDashboard(dashboard.id, {
            expectedVersion: dashboard.version,
            name,
            description: description || null,
            clientRequestId: newRequestId(),
          }),
        );
      } else {
        await deletePersonalDashboard(dashboard.id, dashboard.version);
        onDeleted();
      }
      onClose();
    } catch (requestError) {
      setError(dashboardDiagnostics(requestError));
    } finally {
      setSaving(false);
    }
  };

  const title =
    mode === 'metadata'
      ? copy(locale, 'Cambiar nombre y descripción', 'Change name and description')
      : mode === 'duplicate'
        ? copy(locale, 'Duplicar dashboard', 'Duplicate dashboard')
        : copy(locale, 'Eliminar dashboard', 'Delete dashboard');
  const submitLabel =
    mode === 'metadata'
      ? copy(locale, 'Guardar cambios', 'Save changes')
      : mode === 'duplicate'
        ? copy(locale, 'Duplicar dashboard', 'Duplicate dashboard')
        : copy(locale, 'Eliminar dashboard', 'Delete dashboard');

  return (
    <Modal isOpen={mode !== null} toggle={saving ? undefined : onClose} centered aria-labelledby="personal-dashboard-management-title">
      <form onSubmit={submit}>
        <ModalHeader toggle={saving ? undefined : onClose} id="personal-dashboard-management-title">
          {title}
        </ModalHeader>
        <ModalBody>
          {error ? (
            <Alert color="danger" className="d-flex flex-wrap align-items-center justify-content-between gap-2">
              <span>{error}</span>
              <Button color="danger" outline size="sm" type="button" onClick={onReload} disabled={saving}>
                {copy(locale, 'Recargar dashboard', 'Reload dashboard')}
              </Button>
            </Alert>
          ) : null}
          {mode === 'delete' ? (
            <p className="mb-0">
              {copy(
                locale,
                `Eliminarás “${dashboard?.name ?? ''}” y sus visualizaciones guardadas. No se eliminarán cuentas, transacciones ni otros dashboards.`,
                `You will delete “${dashboard?.name ?? ''}” and its saved visualizations. Accounts, transactions, and other dashboards will not be deleted.`,
              )}
            </p>
          ) : (
            <>
              {mode === 'duplicate' ? (
                <p className="small text-muted">
                  {copy(
                    locale,
                    'Se copiarán las consultas, contexto y distribución guardados. Los resultados y selecciones temporales no se copian.',
                    'Saved queries, context, and layout will be copied. Results and temporary selections are not copied.',
                  )}
                </p>
              ) : null}
              <Label for="personal-dashboard-manage-name" className="fw-semibold">
                {copy(locale, 'Nombre', 'Name')}
              </Label>
              <Input
                id="personal-dashboard-manage-name"
                value={name}
                required
                maxLength={100}
                autoFocus
                disabled={saving}
                onChange={event => setName(event.target.value)}
                data-cy="personalDashboardManageName"
              />
              <Label for="personal-dashboard-manage-description" className="fw-semibold mt-3">
                {copy(locale, 'Descripción opcional', 'Optional description')}
              </Label>
              <Input
                id="personal-dashboard-manage-description"
                type="textarea"
                rows={3}
                maxLength={500}
                value={description}
                disabled={saving}
                onChange={event => setDescription(event.target.value)}
                data-cy="personalDashboardManageDescription"
              />
            </>
          )}
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline disabled={saving} onClick={onClose}>
            {copy(locale, 'Cancelar', 'Cancel')}
          </Button>
          <Button color={mode === 'delete' ? 'danger' : 'primary'} type="submit" disabled={saving} data-cy="personalDashboardManageSubmit">
            {saving ? copy(locale, 'Guardando…', 'Saving…') : submitLabel}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

const PersonalDashboardWidgetManagementDialog = ({
  dashboard,
  widget,
  locale,
  mode,
  onClose,
  onUpdated,
  onReload,
}: {
  dashboard: PersonalDashboard | null;
  widget: PersonalDashboardWidget | null;
  locale: string;
  mode: WidgetManagementMode;
  onClose: () => void;
  onUpdated: (dashboard: PersonalDashboard) => void;
  onReload: () => void;
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!widget) return;
    setTitle(widget.title);
    setDescription(widget.description ?? '');
    setError(null);
  }, [mode, widget]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!dashboard || !widget || !mode || saving) return;
    setSaving(true);
    setError(null);
    try {
      const updated =
        mode === 'metadata'
          ? await updatePersonalDashboardWidget(dashboard.id, widget.id, {
              expectedVersion: dashboard.version,
              title,
              description: description || null,
              definition: widget.definition,
              visualizationType: widget.visualizationType,
              visualizationOptions: widget.visualizationOptions,
              width: widget.width,
              visible: widget.visible,
            })
          : await deletePersonalDashboardWidget(dashboard.id, widget.id, dashboard.version);
      onUpdated(updated);
      onClose();
    } catch (requestError) {
      setError(dashboardDiagnostics(requestError));
    } finally {
      setSaving(false);
    }
  };

  const deleting = mode === 'delete';
  return (
    <Modal
      isOpen={mode !== null}
      toggle={saving ? undefined : onClose}
      centered
      aria-labelledby="personal-dashboard-widget-management-title"
    >
      <form onSubmit={submit}>
        <ModalHeader toggle={saving ? undefined : onClose} id="personal-dashboard-widget-management-title">
          {deleting
            ? copy(locale, 'Eliminar widget', 'Delete widget')
            : copy(locale, 'Cambiar nombre y descripción', 'Change name and description')}
        </ModalHeader>
        <ModalBody>
          {error ? (
            <Alert color="danger" className="d-flex flex-wrap align-items-center justify-content-between gap-2">
              <span>{error}</span>
              <Button color="danger" outline size="sm" type="button" onClick={onReload} disabled={saving}>
                {copy(locale, 'Recargar dashboard', 'Reload dashboard')}
              </Button>
            </Alert>
          ) : null}
          {deleting ? (
            <p className="mb-0">
              {copy(
                locale,
                `Eliminarás permanentemente “${widget?.title ?? ''}”. No se eliminarán cuentas, transacciones ni los demás widgets.`,
                `You will permanently delete “${widget?.title ?? ''}”. Accounts, transactions, and other widgets will not be deleted.`,
              )}
            </p>
          ) : (
            <>
              <p className="small text-muted">
                {copy(
                  locale,
                  'La consulta, su visualización, tamaño, posición y visibilidad se conservarán.',
                  'Its query, visualization, size, position, and visibility will be preserved.',
                )}
              </p>
              <Label for="personal-dashboard-widget-manage-title" className="fw-semibold">
                {copy(locale, 'Título de la visualización', 'Visualization title')}
              </Label>
              <Input
                id="personal-dashboard-widget-manage-title"
                value={title}
                required
                maxLength={120}
                autoFocus
                disabled={saving}
                onChange={event => setTitle(event.target.value)}
                data-cy="personalDashboardWidgetManageTitle"
              />
              <Label for="personal-dashboard-widget-manage-description" className="fw-semibold mt-3">
                {copy(locale, 'Descripción opcional', 'Optional description')}
              </Label>
              <Input
                id="personal-dashboard-widget-manage-description"
                type="textarea"
                rows={3}
                maxLength={500}
                value={description}
                disabled={saving}
                onChange={event => setDescription(event.target.value)}
                data-cy="personalDashboardWidgetManageDescription"
              />
            </>
          )}
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline disabled={saving} onClick={onClose}>
            {copy(locale, 'Cancelar', 'Cancel')}
          </Button>
          <Button
            color={deleting ? 'danger' : 'primary'}
            type="submit"
            disabled={saving || (!deleting && !title.trim())}
            data-cy="personalDashboardWidgetManageSubmit"
          >
            {saving
              ? copy(locale, 'Guardando…', 'Saving…')
              : deleting
                ? copy(locale, 'Eliminar widget', 'Delete widget')
                : copy(locale, 'Guardar cambios', 'Save changes')}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

type CustomizationControls = {
  index: number;
  count: number;
  onMove: (widgetId: number, targetIndex: number) => void;
  onWidth: (widgetId: number, width: PersonalDashboardWidget['width']) => void;
  onHide: (widgetId: number) => void;
};

const PersonalWidgetCard = ({
  widget,
  state,
  locale,
  dashboardId,
  temporalOptions,
  onTemporalIntervalChange,
  onDrillDown,
  onDrillBack,
  onManage,
  operationPending,
  customization,
}: {
  widget: ViewerWidget;
  state: ReturnType<typeof useDashboardWidgetResults>[string] | undefined;
  locale: string;
  dashboardId: number;
  temporalOptions: string[];
  onTemporalIntervalChange: (widgetId: number, interval: string) => void;
  onDrillDown: (widget: ViewerWidget, categoryId: string, categoryLabel: string) => void;
  onDrillBack: (widget: ViewerWidget) => void;
  onManage?: (widget: PersonalDashboardWidget, action: 'metadata' | 'duplicate' | 'delete') => void;
  operationPending?: boolean;
  customization?: CustomizationControls;
}) => {
  const sortable = useSortable({ id: widget.id, disabled: !customization });
  const currentOnly = state?.error === 'CURRENT_ONLY_CONTEXT';
  const asOf = asOfDatasets.has(widget.definition.dataset) && state?.result?.effectiveContext.through;
  const temporalGrouping = widget.definition.groupBy.find(isTemporalGrouping);
  const supportsIntervalOverride = Boolean(temporalGrouping && temporalOptions.length > 1);
  const chartNeedsSpace = isChartVisualization(widget.visualizationType);
  const style = customization
    ? { transform: CSS.Transform.toString(sortable.transform), transition: sortable.transition, opacity: sortable.isDragging ? 0.55 : 1 }
    : undefined;
  return (
    <article
      ref={customization ? sortable.setNodeRef : undefined}
      style={style}
      className={`${widthClass(widget.width)} ${customization && sortable.isOver ? 'personal-dashboard-drop-target' : ''}`}
      data-cy={`personalDashboardWidget-${widget.id}`}
    >
      <div className="card border shadow-sm h-100">
        <div className="card-body p-3 p-md-4">
          {customization ? (
            <div className="d-flex flex-wrap align-items-center gap-2 border-bottom pb-2 mb-3">
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                aria-label={copy(locale, `Arrastrar ${widget.title}`, `Drag ${widget.title}`)}
                title={copy(
                  locale,
                  'Arrastra para reordenar; también puedes usar las flechas del teclado.',
                  'Drag to reorder; keyboard arrows also work.',
                )}
                style={{ touchAction: 'none' }}
                {...sortable.attributes}
                {...sortable.listeners}
                data-cy={`personalDashboardDragHandle-${widget.id}`}
              >
                ↕ {copy(locale, 'Mover', 'Move')}
              </button>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={() => customization.onMove(widget.id, customization.index - 1)}
                disabled={customization.index === 0}
                data-cy={`personalDashboardMoveUp-${widget.id}`}
              >
                ↑ {copy(locale, 'Mover arriba', 'Move up')}
              </button>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={() => customization.onMove(widget.id, customization.index + 1)}
                disabled={customization.index === customization.count - 1}
                data-cy={`personalDashboardMoveDown-${widget.id}`}
              >
                ↓ {copy(locale, 'Mover abajo', 'Move down')}
              </button>
              <label className="small fw-semibold ms-sm-auto" htmlFor={`personal-dashboard-width-${widget.id}`}>
                {copy(locale, 'Tamaño', 'Size')}
              </label>
              <select
                id={`personal-dashboard-width-${widget.id}`}
                className="form-select form-select-sm w-auto"
                value={widget.width}
                onChange={event => customization.onWidth(widget.id, event.target.value as PersonalDashboardWidget['width'])}
                data-cy={`personalDashboardWidth-${widget.id}`}
              >
                <option value="COMPACT" disabled={chartNeedsSpace}>
                  {copy(locale, 'Compacto', 'Compact')}
                </option>
                <option value="MEDIUM">{copy(locale, 'Mediano', 'Medium')}</option>
                <option value="FULL">{copy(locale, 'Ancho completo', 'Full width')}</option>
              </select>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={() => customization.onHide(widget.id)}
                data-cy={`personalDashboardHide-${widget.id}`}
              >
                {copy(locale, 'Ocultar', 'Hide')}
              </button>
              {chartNeedsSpace ? (
                <span className="small text-muted">{copy(locale, 'Gráfica: mínimo mediano.', 'Chart: medium minimum.')}</span>
              ) : null}
            </div>
          ) : null}
          <div className="personal-dashboard-widget-header mb-2" data-cy={`personalDashboardWidgetHeader-${widget.id}`}>
            <div className="personal-dashboard-widget-header__title" data-cy={`personalDashboardWidgetTitle-${widget.id}`}>
              <h2 className="h5 mb-0">{widget.title}</h2>
            </div>
            {!customization && (supportsIntervalOverride || onManage) ? (
              <div className="personal-dashboard-widget-header__actions" data-cy={`personalDashboardWidgetActions-${widget.id}`}>
                {supportsIntervalOverride ? (
                  <div className="personal-dashboard-widget-header__interval">
                    <label className="small fw-semibold mb-0" htmlFor={`personal-dashboard-widget-interval-${widget.id}`}>
                      {copy(locale, 'Intervalo', 'Interval')}
                    </label>
                    <select
                      id={`personal-dashboard-widget-interval-${widget.id}`}
                      className="form-select form-select-sm w-auto"
                      value={temporalGrouping}
                      onChange={event => onTemporalIntervalChange(widget.id, event.target.value)}
                      data-cy={`personalDashboardWidgetInterval-${widget.id}`}
                    >
                      {temporalOptions.map(interval => (
                        <option key={interval} value={interval}>
                          {label(locale, interval)}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                {onManage ? (
                  <div className="personal-dashboard-widget-header__menu">
                    <UncontrolledDropdown>
                      <DropdownToggle
                        color="secondary"
                        outline
                        size="sm"
                        aria-label={copy(locale, `Opciones para ${widget.title}`, `Options for ${widget.title}`)}
                        title={copy(locale, 'Opciones del widget', 'Widget options')}
                        disabled={operationPending}
                        data-cy={`personalDashboardWidgetMenu-${widget.id}`}
                      >
                        ⋯
                      </DropdownToggle>
                      <DropdownMenu end>
                        <DropdownItem onClick={() => onManage(widget, 'metadata')} data-cy={`personalDashboardWidgetRename-${widget.id}`}>
                          {copy(locale, 'Cambiar nombre y descripción', 'Change name and description')}
                        </DropdownItem>
                        <DropdownItem
                          tag={Link}
                          to={`/analytics/explorer?dashboardId=${dashboardId}&widgetId=${widget.id}`}
                          data-cy={`personalDashboardWidgetMenuEdit-${widget.id}`}
                        >
                          {copy(locale, 'Editar visualización', 'Edit visualization')}
                        </DropdownItem>
                        <DropdownItem
                          onClick={() => onManage(widget, 'duplicate')}
                          data-cy={`personalDashboardWidgetDuplicate-${widget.id}`}
                        >
                          {copy(locale, 'Duplicar widget', 'Duplicate widget')}
                        </DropdownItem>
                        <DropdownItem divider />
                        <DropdownItem
                          className="text-danger"
                          onClick={() => onManage(widget, 'delete')}
                          data-cy={`personalDashboardWidgetDelete-${widget.id}`}
                        >
                          {copy(locale, 'Eliminar widget', 'Delete widget')}
                        </DropdownItem>
                      </DropdownMenu>
                    </UncontrolledDropdown>
                  </div>
                ) : null}
              </div>
            ) : null}
            {widget.description ? (
              <p
                className="personal-dashboard-widget-header__description small text-muted"
                data-cy={`personalDashboardWidgetDescription-${widget.id}`}
              >
                {widget.description}
              </p>
            ) : null}
          </div>
          {asOf ? (
            <p className="small fw-semibold mb-3">{copy(locale, `Al ${formatAsOf(locale, asOf)}`, `As of ${formatAsOf(locale, asOf)}`)}</p>
          ) : null}
          {currentOnly ? (
            <div className="small border-start border-info border-3 ps-2" role="status">
              {copy(
                locale,
                'Esta medida usa información actual de crédito. Selecciona únicamente la fecha de hoy para verla; FINTRACK no inventa historial de límites.',
                'This measure uses current credit facts. Select today only to view it; FINTRACK does not invent limit history.',
              )}
            </div>
          ) : state?.error ? (
            <Alert color="danger" className="mb-0" role="alert">
              {copy(locale, 'Este widget no pudo cargarse:', 'This widget could not load:')} {state.error}
            </Alert>
          ) : (
            <AnalyticsResultsPanel
              definition={widget.definition}
              result={state?.result ?? null}
              visualization={widget.visualizationType}
              locale={locale}
              loading={state?.loading ?? true}
              stale={false}
              seriesLimit={seriesLimitFor(widget)}
              drillTrail={widget.trail.map(item => item.label)}
              onDrillDown={(categoryId, categoryLabel) => onDrillDown(widget, categoryId, categoryLabel)}
              onDrillBack={() => onDrillBack(widget)}
              compact
            />
          )}
        </div>
      </div>
    </article>
  );
};

/** A stored dashboard executes its saved canonical widgets against a temporary, viewer-level context only. */
const PersonalDashboardViewer = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const { dashboardId } = useParams<{ dashboardId: string }>();
  const navigate = useNavigate();
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [dashboard, setDashboard] = useState<PersonalDashboard | null>(null);
  const [context, setContext] = useState<VisualizationContext | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [drillStates, setDrillStates] = useState<Record<number, { definition: SemanticQueryDefinition; trail: DrillState[] }>>({});
  // This is deliberately not stored in the dashboard. It only changes the
  // canonical query copy sent by this rendered widget until the next reload.
  const [temporaryIntervals, setTemporaryIntervals] = useState<Record<number, string>>({});
  const [customizing, setCustomizing] = useState(false);
  const [layoutDraft, setLayoutDraft] = useState<DashboardLayoutItem[]>([]);
  const [layoutError, setLayoutError] = useState<string | null>(null);
  const [savingLayout, setSavingLayout] = useState(false);
  const [defaultDashboard, setDefaultDashboard] = useState<PersonalDashboardDefault>({
    dashboardId: null,
    officialTemplateId: 'financial-overview',
    version: null,
  });
  const [managementMode, setManagementMode] = useState<ManagementMode>(null);
  const [managementError, setManagementError] = useState<string | null>(null);
  const [settingDefault, setSettingDefault] = useState(false);
  const [widgetManagement, setWidgetManagement] = useState<{ mode: WidgetManagementMode; widgetId: number } | null>(null);
  const [widgetOperationId, setWidgetOperationId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    setDashboard(null);
    setContext(null);
    setDrillStates({});
    setTemporaryIntervals({});
    setCustomizing(false);
    setLayoutDraft([]);
    setLayoutError(null);
    setManagementMode(null);
    setManagementError(null);
    setWidgetManagement(null);
    setWidgetOperationId(null);
    void Promise.all([
      getSemanticAnalyticsCatalog(),
      getExplorerAccounts(),
      getPersonalDashboard(dashboardId ?? ''),
      getPersonalDashboardDefault(),
    ])
      .then(([nextCatalog, nextAccounts, nextDashboard, nextDefault]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setAccounts(
          nextAccounts
            .filter(account => account.id != null && account.name)
            .map(account => ({ id: account.id, name: account.name, accountType: account.accountType, currency: account.currency })),
        );
        setDashboard(nextDashboard);
        setDefaultDashboard(nextDefault);
        setLayoutDraft(layoutFromWidgets(nextDashboard.widgets));
        // This is a local copy: changing controls below never invokes a dashboard mutation endpoint.
        setContext(nextDashboard.defaultContext);
      })
      .catch(requestError => active && setError(dashboardDiagnostics(requestError)));
    return () => {
      active = false;
    };
  }, [dashboardId]);

  const persistedLayout = useMemo(() => layoutFromWidgets(dashboard?.widgets ?? []), [dashboard]);
  const activeLayout = customizing ? layoutDraft : persistedLayout;
  const layoutDirty = customizing && !sameLayout(layoutDraft, persistedLayout);
  const dashboardWidgets = useMemo(() => {
    const widgetById = new Map((dashboard?.widgets ?? []).map(widget => [widget.id, widget]));
    return activeLayout.flatMap((item, position) => {
      const widget = widgetById.get(item.widgetId);
      return widget ? [{ ...widget, width: item.width, visible: item.visible, position }] : [];
    });
  }, [activeLayout, dashboard]);

  const widgets = useMemo<ViewerWidget[]>(
    () =>
      dashboardWidgets
        .filter(widget => widget.visible)
        .map(widget => {
          const canonicalDefinition = drillStates[widget.id]?.definition ?? widget.definition;
          const interval = temporaryIntervals[widget.id];
          return {
            ...widget,
            definition: interval ? replaceTemporalGrouping(canonicalDefinition, interval) : canonicalDefinition,
            trail: drillStates[widget.id]?.trail ?? [],
            contextPolicy: policyFor(widget),
          };
        }),
    [dashboardWidgets, drillStates, temporaryIntervals],
  );
  const states = useDashboardWidgetResults(widgets, context);

  useEffect(() => {
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!layoutDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [layoutDirty]);

  const drillDown = (widget: ViewerWidget, categoryId: string, categoryLabel: string) => {
    if (widget.definition.dataset !== 'TRANSACTION_LEDGER') return;
    const groupingIndex = categoryGroupingIndex(widget.definition);
    if (groupingIndex < 0) return;
    const definition = groupCategoryChildren(widget.definition, groupingIndex as 0 | 1, categoryId);
    setDrillStates(previous => ({
      ...previous,
      [widget.id]: { definition, trail: [...widget.trail, { definition: widget.definition, label: categoryLabel }] },
    }));
  };
  const drillBack = (widget: ViewerWidget) => {
    const previous = widget.trail[widget.trail.length - 1];
    if (!previous) return;
    setDrillStates(current => ({ ...current, [widget.id]: { definition: previous.definition, trail: widget.trail.slice(0, -1) } }));
  };

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const visibleLayout = activeLayout.filter(item => item.visible);
  const hiddenWidgets = dashboardWidgets.filter(widget => !widget.visible);
  const startCustomization = () => {
    setLayoutDraft(persistedLayout);
    setLayoutError(null);
    setCustomizing(true);
  };
  const cancelCustomization = () => {
    setLayoutDraft(persistedLayout);
    setLayoutError(null);
    setCustomizing(false);
  };
  const updateDraftOrder = (widgetId: number, targetIndex: number) => {
    const target = visibleLayout[targetIndex];
    if (!target) return;
    setLayoutDraft(current =>
      moveLayoutItem(
        current,
        widgetId,
        current.findIndex(item => item.widgetId === target.widgetId),
      ),
    );
  };
  const dragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const activeId = Number(active.id);
    const overId = Number(over.id);
    if (!Number.isSafeInteger(activeId) || !Number.isSafeInteger(overId)) return;
    setLayoutDraft(current =>
      moveLayoutItem(
        current,
        activeId,
        current.findIndex(item => item.widgetId === overId),
      ),
    );
  };
  const applyPreset = (preset: DashboardLayoutPreset) => {
    setLayoutDraft(current => applyLayoutPreset(preset, current, dashboardWidgets));
  };
  const saveLayout = async () => {
    if (!dashboard || savingLayout || !layoutDirty) return;
    setSavingLayout(true);
    setLayoutError(null);
    try {
      const saved = await savePersonalDashboardLayout(dashboard.id, { expectedVersion: dashboard.version, widgets: layoutDraft });
      setDashboard(saved);
      setLayoutDraft(layoutFromWidgets(saved.widgets));
      setCustomizing(false);
    } catch (_requestError) {
      setLayoutError(
        copy(
          locale,
          'No se pudieron guardar los cambios de distribución. Conservamos tu borrador para que puedas revisarlo o reintentar.',
          'The layout changes could not be saved. Your draft is still here so you can review it or try again.',
        ),
      );
    } finally {
      setSavingLayout(false);
    }
  };
  const setAsDefault = async () => {
    if (!dashboard || settingDefault || dashboard.id === defaultDashboard.dashboardId) return;
    setSettingDefault(true);
    setManagementError(null);
    try {
      setDefaultDashboard(await setPersonalDashboardDefault(defaultDashboard.version, dashboard.id));
    } catch (requestError) {
      setManagementError(dashboardDiagnostics(requestError));
    } finally {
      setSettingDefault(false);
    }
  };
  const managementUpdated = (updated: PersonalDashboard) => {
    setDashboard(updated);
    setLayoutDraft(layoutFromWidgets(updated.widgets));
    setManagementError(null);
  };
  const managedWidget = dashboard?.widgets.find(widget => widget.id === widgetManagement?.widgetId) ?? null;
  const manageWidget = (widget: PersonalDashboardWidget, action: 'metadata' | 'duplicate' | 'delete') => {
    if (!dashboard || widgetOperationId !== null) return;
    if (action !== 'duplicate') {
      setWidgetManagement({ mode: action, widgetId: widget.id });
      return;
    }
    void (async () => {
      setWidgetOperationId(widget.id);
      setManagementError(null);
      try {
        managementUpdated(
          await duplicatePersonalDashboardWidget(dashboard.id, widget.id, {
            expectedVersion: dashboard.version,
            clientRequestId: newRequestId(),
          }),
        );
      } catch (requestError) {
        setManagementError(dashboardDiagnostics(requestError));
      } finally {
        setWidgetOperationId(null);
      }
    })();
  };
  const managementDuplicated = (duplicate: PersonalDashboard) => {
    setManagementError(null);
    navigate(`/analytics/dashboards/personal/${duplicate.id}`);
  };
  const managementDeleted = () => {
    setManagementError(null);
    navigate('/analytics/dashboards', { replace: true });
  };
  const navigateBack = () => {
    if (
      layoutDirty &&
      !window.confirm(
        copy(
          locale,
          'Tienes cambios sin guardar. ¿Quieres salir y descartarlos?',
          'You have unsaved changes. Do you want to leave and discard them?',
        ),
      )
    ) {
      return;
    }
    navigate('/analytics/dashboards');
  };

  return (
    <ProductPage wide className="personal-dashboard-viewer">
      <ProductPageHeader
        headingId="personal-dashboard-heading"
        title={dashboard?.name ?? copy(locale, 'Dashboard personal', 'Personal dashboard')}
        subtitle={
          dashboard?.description ??
          copy(locale, 'Una vista personal basada en consultas financieras guardadas.', 'A personal view based on saved financial queries.')
        }
        actions={
          <div className="d-flex flex-wrap align-items-center justify-content-end gap-2">
            {customizing ? (
              <>
                {layoutDirty ? (
                  <span className="small fw-semibold text-warning-emphasis" role="status" data-cy="personalDashboardUnsavedChanges">
                    {copy(locale, 'Cambios sin guardar', 'Unsaved changes')}
                  </span>
                ) : null}
                <Button
                  color="secondary"
                  outline
                  size="sm"
                  onClick={cancelCustomization}
                  disabled={savingLayout}
                  data-cy="personalDashboardCancelLayout"
                >
                  {copy(locale, 'Cancelar', 'Cancel')}
                </Button>
                <Button
                  color="primary"
                  size="sm"
                  onClick={() => void saveLayout()}
                  disabled={!layoutDirty || savingLayout}
                  data-cy="personalDashboardSaveLayout"
                >
                  {savingLayout ? copy(locale, 'Guardando…', 'Saving…') : copy(locale, 'Guardar cambios', 'Save changes')}
                </Button>
              </>
            ) : (
              <>
                <UncontrolledDropdown>
                  <DropdownToggle color="secondary" outline size="sm" caret data-cy="personalDashboardManage">
                    {copy(locale, 'Gestionar', 'Manage')}
                  </DropdownToggle>
                  <DropdownMenu end>
                    <DropdownItem onClick={() => setManagementMode('metadata')} data-cy="personalDashboardEditMetadata">
                      {copy(locale, 'Cambiar nombre y descripción', 'Change name and description')}
                    </DropdownItem>
                    <DropdownItem onClick={() => setManagementMode('duplicate')} data-cy="personalDashboardDuplicate">
                      {copy(locale, 'Duplicar dashboard', 'Duplicate dashboard')}
                    </DropdownItem>
                    <DropdownItem divider />
                    <DropdownItem
                      disabled={dashboard?.id === defaultDashboard.dashboardId || settingDefault}
                      onClick={() => void setAsDefault()}
                      data-cy="personalDashboardSetDefault"
                    >
                      {dashboard?.id === defaultDashboard.dashboardId
                        ? copy(locale, 'Dashboard predeterminado', 'Default dashboard')
                        : settingDefault
                          ? copy(locale, 'Guardando…', 'Saving…')
                          : copy(locale, 'Establecer como predeterminado', 'Set as default')}
                    </DropdownItem>
                    <DropdownItem divider />
                    <DropdownItem className="text-danger" onClick={() => setManagementMode('delete')} data-cy="personalDashboardDelete">
                      {copy(locale, 'Eliminar dashboard', 'Delete dashboard')}
                    </DropdownItem>
                  </DropdownMenu>
                </UncontrolledDropdown>
                <Button color="primary" size="sm" onClick={startCustomization} data-cy="personalDashboardCustomize">
                  {copy(locale, 'Personalizar dashboard', 'Customize dashboard')}
                </Button>
              </>
            )}
            <Button color="secondary" outline size="sm" onClick={navigateBack}>
              {copy(locale, 'Volver a dashboards', 'Back to dashboards')}
            </Button>
          </div>
        }
      />
      {error ? <Alert color="danger">{error}</Alert> : null}
      {layoutError ? (
        <Alert color="danger" data-cy="personalDashboardLayoutError">
          {layoutError}
        </Alert>
      ) : null}
      {managementError ? (
        <Alert
          color="danger"
          className="d-flex flex-wrap align-items-center justify-content-between gap-2"
          data-cy="personalDashboardManagementError"
        >
          <span>{managementError}</span>
          <Button color="danger" outline size="sm" onClick={() => navigate(0)}>
            {copy(locale, 'Recargar dashboard', 'Reload dashboard')}
          </Button>
        </Alert>
      ) : null}
      {!catalog || !dashboard || !context ? (
        <div className="border rounded-2 p-4 text-center">
          {error
            ? copy(locale, 'No fue posible abrir este dashboard.', 'This dashboard could not be opened.')
            : copy(locale, 'Cargando dashboard…', 'Loading dashboard…')}
        </div>
      ) : (
        <div className="d-flex flex-column gap-4">
          <ProductSection title={<span className="visually-hidden">{copy(locale, 'Contexto del dashboard', 'Dashboard context')}</span>}>
            <AnalyticsContextControls
              catalog={catalog}
              context={context}
              accounts={accounts}
              locale={locale}
              disabled={false}
              onChange={setContext}
            />
          </ProductSection>
          <p className="small text-muted mb-0" data-cy="personalDashboardContextNotice">
            {copy(
              locale,
              'Los cambios de período, cuentas y monedas son temporales para esta vista; el dashboard guardado no se modifica.',
              'Period, account, and currency changes are temporary for this view; the saved dashboard is not changed.',
            )}
          </p>
          {customizing ? (
            <section className="border rounded-2 p-3 p-md-4" aria-labelledby="personal-dashboard-customization-heading">
              <div className="mb-3">
                <div>
                  <h2 id="personal-dashboard-customization-heading" className="h5 mb-1">
                    {copy(locale, 'Personalizar distribución', 'Customize layout')}
                  </h2>
                  <p className="small text-muted mb-0">
                    {copy(
                      locale,
                      'Los cambios se ven aquí primero y no afectan las consultas ni los resultados hasta que guardes.',
                      'Changes preview here first and do not affect queries or results until you save.',
                    )}
                  </p>
                </div>
                <div
                  className="personal-dashboard-layout-presets d-flex flex-wrap align-items-center justify-content-md-end gap-2 mt-3"
                  aria-label={copy(locale, 'Preajustes de distribución', 'Layout presets')}
                >
                  <span className="small fw-semibold">{copy(locale, 'Preajustes', 'Presets')}</span>
                  {(
                    [
                      ['BALANCED', 'Equilibrado', 'Balanced'],
                      ['COMPACT', 'Compacto', 'Compact'],
                      ['CHART_FOCUSED', 'Centrado en gráficas', 'Chart-focused'],
                    ] as Array<[DashboardLayoutPreset, string, string]>
                  ).map(([preset, spanish, english]) => (
                    <Button
                      key={preset}
                      color="secondary"
                      outline
                      size="sm"
                      onClick={() => applyPreset(preset)}
                      data-cy={`personalDashboardPreset-${preset}`}
                    >
                      {copy(locale, spanish, english)}
                    </Button>
                  ))}
                </div>
              </div>
              {!widgets.length ? (
                <section className="border rounded-2 p-4 text-center" data-cy="personalDashboardAllHiddenState">
                  <h3 className="h6">{copy(locale, 'Todos los widgets están ocultos', 'All widgets are hidden')}</h3>
                  <p className="small text-muted mb-3">
                    {copy(
                      locale,
                      'Ocultarlos no los elimina ni modifica sus consultas.',
                      'Hiding them does not delete them or modify their queries.',
                    )}
                  </p>
                  <Button
                    color="secondary"
                    outline
                    size="sm"
                    onClick={() => setLayoutDraft(current => current.map(item => ({ ...item, visible: true })))}
                    data-cy="personalDashboardRestoreAllWidgets"
                  >
                    {copy(locale, 'Mostrar todos los widgets', 'Show all widgets')}
                  </Button>
                </section>
              ) : (
                <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={dragEnd}>
                  <SortableContext items={widgets.map(widget => widget.id)} strategy={rectSortingStrategy}>
                    <div className="row g-3" data-cy="personalDashboardWidgets">
                      {widgets.map((widget, index) => (
                        <PersonalWidgetCard
                          key={widget.id}
                          widget={widget}
                          state={states[widget.id]}
                          locale={locale}
                          dashboardId={dashboard.id}
                          temporalOptions={temporalOptionsForDefinition(catalog, widget.definition)}
                          onTemporalIntervalChange={(widgetId, interval) =>
                            setTemporaryIntervals(previous => ({ ...previous, [widgetId]: interval }))
                          }
                          onDrillDown={drillDown}
                          onDrillBack={drillBack}
                          customization={{
                            index,
                            count: widgets.length,
                            onMove: updateDraftOrder,
                            onWidth: (widgetId, width) => setLayoutDraft(current => changeLayoutWidth(current, widgetId, width)),
                            onHide: widgetId => setLayoutDraft(current => changeLayoutVisibility(current, widgetId, false)),
                          }}
                        />
                      ))}
                    </div>
                  </SortableContext>
                </DndContext>
              )}
              {hiddenWidgets.length ? (
                <section
                  className="border-top mt-4 pt-3"
                  aria-labelledby="personal-dashboard-hidden-widgets-heading"
                  data-cy="personalDashboardHiddenWidgets"
                >
                  <h3 id="personal-dashboard-hidden-widgets-heading" className="h6 mb-2">
                    {copy(locale, 'Widgets ocultos', 'Hidden widgets')} ({hiddenWidgets.length})
                  </h3>
                  <ul className="list-unstyled mb-0 d-flex flex-column gap-2">
                    {hiddenWidgets.map(widget => (
                      <li key={widget.id} className="d-flex flex-wrap align-items-center justify-content-between gap-2">
                        <span>{widget.title}</span>
                        <Button
                          color="secondary"
                          outline
                          size="sm"
                          onClick={() => setLayoutDraft(current => changeLayoutVisibility(current, widget.id, true))}
                          data-cy={`personalDashboardShow-${widget.id}`}
                        >
                          {copy(locale, 'Mostrar widget', 'Show widget')}
                        </Button>
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}
            </section>
          ) : !widgets.length ? (
            <section className="border rounded-2 p-4 text-center" data-cy="personalDashboardViewerEmptyState">
              <h2 className="h5">
                {dashboardWidgets.length
                  ? copy(locale, 'Todos los widgets están ocultos', 'All widgets are hidden')
                  : copy(locale, 'Este dashboard aún no tiene visualizaciones', 'This dashboard has no visualizations yet')}
              </h2>
              <p className="small text-muted mb-0">
                {dashboardWidgets.length
                  ? copy(
                      locale,
                      'Personaliza el dashboard para volver a mostrar un widget.',
                      'Customize the dashboard to show a widget again.',
                    )
                  : copy(
                      locale,
                      'Crea una consulta en el Explorador de métricas y guárdala aquí cuando esté lista.',
                      'Create a query in Metric Explorer and save it here when it is ready.',
                    )}
              </p>
              {dashboardWidgets.length ? (
                <Button color="primary" size="sm" className="mt-3" onClick={startCustomization} data-cy="personalDashboardManageWidgets">
                  {copy(locale, 'Administrar widgets', 'Manage widgets')}
                </Button>
              ) : (
                <Button
                  tag={Link}
                  to={`/analytics/explorer?dashboardId=${dashboard.id}`}
                  color="primary"
                  size="sm"
                  className="mt-3"
                  data-cy="personalDashboardAddVisualization"
                >
                  {copy(locale, 'Añadir visualización', 'Add visualization')}
                </Button>
              )}
            </section>
          ) : (
            <DndContext>
              <SortableContext items={widgets.map(widget => widget.id)} strategy={rectSortingStrategy}>
                <div className="row g-3" data-cy="personalDashboardWidgets">
                  {widgets.map(widget => (
                    <PersonalWidgetCard
                      key={widget.id}
                      widget={widget}
                      state={states[widget.id]}
                      locale={locale}
                      dashboardId={dashboard.id}
                      temporalOptions={temporalOptionsForDefinition(catalog, widget.definition)}
                      onTemporalIntervalChange={(widgetId, interval) =>
                        setTemporaryIntervals(previous => ({ ...previous, [widgetId]: interval }))
                      }
                      onDrillDown={drillDown}
                      onDrillBack={drillBack}
                      onManage={manageWidget}
                      operationPending={widgetOperationId === widget.id}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>
          )}
        </div>
      )}
      <PersonalDashboardManagementDialog
        dashboard={dashboard}
        locale={locale}
        mode={managementMode}
        onClose={() => setManagementMode(null)}
        onUpdated={managementUpdated}
        onDuplicated={managementDuplicated}
        onDeleted={managementDeleted}
        onReload={() => navigate(0)}
      />
      <PersonalDashboardWidgetManagementDialog
        dashboard={dashboard}
        widget={managedWidget}
        locale={locale}
        mode={widgetManagement?.mode ?? null}
        onClose={() => setWidgetManagement(null)}
        onUpdated={managementUpdated}
        onReload={() => navigate(0)}
      />
    </ProductPage>
  );
};

export default PersonalDashboardViewer;
