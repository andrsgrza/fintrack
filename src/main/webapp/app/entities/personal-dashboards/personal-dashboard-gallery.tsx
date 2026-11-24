import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Input, Label, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import { getSemanticAnalyticsCatalog } from 'app/entities/analytics-explorer/analytics-explorer-api';
import { SemanticAnalyticsCatalog, VisualizationContext } from 'app/entities/analytics-explorer/analytics-explorer.model';
import { copy } from 'app/entities/analytics-explorer/analytics-explorer-utils';
import { dashboardDiagnostics } from 'app/entities/recommended-dashboards/recommended-dashboards';
import {
  FinancialVisualizationDefinition,
  RecommendedDashboardTemplate,
  recommendedDashboardTemplates,
} from 'app/entities/recommended-dashboards/recommended-dashboard-templates';
import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  copyOfficialDashboardTemplate,
  createPersonalDashboard,
  getPersonalDashboardDefault,
  listPersonalDashboards,
  PersonalDashboardCreate,
  PersonalDashboardDefault,
  PersonalDashboardSummary,
  PersonalDashboardTemplateCopy,
  OfficialDashboardTemplateId,
  setPersonalDashboardDefault,
} from './personal-dashboard-api';

type CreateMode = { kind: 'blank' } | { kind: 'template'; template: RecommendedDashboardTemplate } | null;

const localized = (locale: string, value: { es: string; en: string }) => copy(locale, value.es, value.en);
const newRequestId = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `dashboard-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
const defaultContext = (catalog: SemanticAnalyticsCatalog): VisualizationContext => ({
  schemaVersion: catalog.schemaVersion,
  time: { mode: 'RELATIVE', relativePeriod: 'THIS_MONTH', from: null, through: null },
  accountIds: [],
  currencyCodes: [],
});
const widthFor = (widget: FinancialVisualizationDefinition): 'COMPACT' | 'MEDIUM' | 'FULL' =>
  widget.span === 'KPI' ? 'COMPACT' : widget.span === 'FULL' ? 'FULL' : 'MEDIUM';

/** Maps the one live recommended-template registry to V2-2's persisted widget contract. */
const copyPayload = (
  template: RecommendedDashboardTemplate,
  name: string,
  description: string,
  context: VisualizationContext,
  locale: string,
): PersonalDashboardTemplateCopy => ({
  templateId: template.id,
  name,
  description: description || null,
  defaultContext: context,
  clientRequestId: newRequestId(),
  widgets: template.widgets.map(widget => ({
    title: localized(locale, widget.title),
    description: localized(locale, widget.description),
    definition: widget.definition,
    visualizationType: widget.visualization,
    // Current-only credit facts are a presentation applicability note, never a widget context binding.
    visualizationOptions: widget.contextPolicy ? { contextPolicy: widget.contextPolicy } : {},
    width: widthFor(widget),
    visible: true,
  })),
});

const emptyText = (locale: string) =>
  copy(
    locale,
    'Aún no tienes dashboards personales. Crea uno vacío o parte de una vista recomendada.',
    'You do not have personal dashboards yet. Create a blank one or start from a recommended view.',
  );

const DashboardCreationDialog = ({
  mode,
  catalog,
  locale,
  onClose,
}: {
  mode: CreateMode;
  catalog: SemanticAnalyticsCatalog | null;
  locale: string;
  onClose: () => void;
}) => {
  const navigate = useNavigate();
  const template = mode?.kind === 'template' ? mode.template : null;
  const [name, setName] = useState(template ? `${localized(locale, template.title)} (${copy(locale, 'copia', 'copy')})` : '');
  const [description, setDescription] = useState(template ? localized(locale, template.description) : '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setName(template ? `${localized(locale, template.title)} (${copy(locale, 'copia', 'copy')})` : '');
    setDescription(template ? localized(locale, template.description) : '');
    setError(null);
  }, [locale, template]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!catalog || saving) return;
    setSaving(true);
    setError(null);
    try {
      const context = defaultContext(catalog);
      const dashboard = template
        ? await copyOfficialDashboardTemplate(copyPayload(template, name, description, context, locale))
        : await createPersonalDashboard({
            name,
            description: description || null,
            defaultContext: context,
            clientRequestId: newRequestId(),
          } satisfies PersonalDashboardCreate);
      navigate(`/analytics/dashboards/personal/${dashboard.id}`);
    } catch (requestError) {
      setError(dashboardDiagnostics(requestError));
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={mode !== null} toggle={saving ? undefined : onClose} centered aria-labelledby="personal-dashboard-create-title">
      <form onSubmit={submit}>
        <ModalHeader toggle={saving ? undefined : onClose} id="personal-dashboard-create-title">
          {template
            ? copy(locale, 'Crear desde dashboard recomendado', 'Create from recommended dashboard')
            : copy(locale, 'Crear dashboard', 'Create dashboard')}
        </ModalHeader>
        <ModalBody>
          {template ? (
            <p className="small text-muted">
              {copy(
                locale,
                'Se copiarán sus visualizaciones y consultas validadas. Los cambios futuros del recomendado no modificarán tu copia.',
                'Its validated visualizations and queries will be copied. Future recommended changes will not modify your copy.',
              )}
            </p>
          ) : (
            <p className="small text-muted">
              {copy(
                locale,
                'El dashboard iniciará vacío. Agrega visualizaciones desde el Explorador de métricas.',
                'The dashboard starts empty. Add visualizations from Metric Explorer.',
              )}
            </p>
          )}
          {error ? <Alert color="danger">{error}</Alert> : null}
          <Label for="personal-dashboard-name" className="fw-semibold">
            {copy(locale, 'Nombre', 'Name')}
          </Label>
          <Input
            id="personal-dashboard-name"
            value={name}
            required
            maxLength={100}
            autoFocus
            disabled={saving}
            onChange={event => setName(event.target.value)}
            data-cy="personalDashboardName"
          />
          <Label for="personal-dashboard-description" className="fw-semibold mt-3">
            {copy(locale, 'Descripción opcional', 'Optional description')}
          </Label>
          <Input
            id="personal-dashboard-description"
            type="textarea"
            rows={3}
            maxLength={500}
            value={description}
            disabled={saving}
            onChange={event => setDescription(event.target.value)}
            data-cy="personalDashboardDescription"
          />
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline disabled={saving} onClick={onClose}>
            {copy(locale, 'Cancelar', 'Cancel')}
          </Button>
          <Button color="primary" type="submit" disabled={!catalog || saving} data-cy="personalDashboardSubmit">
            {saving ? copy(locale, 'Creando…', 'Creating…') : copy(locale, 'Crear dashboard', 'Create dashboard')}
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

/** Metadata-only landing page: it never executes any financial widget queries. */
const PersonalDashboardGallery = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [dashboards, setDashboards] = useState<PersonalDashboardSummary[]>([]);
  const [defaultDashboard, setDefaultDashboard] = useState<PersonalDashboardDefault>({
    dashboardId: null,
    officialTemplateId: 'financial-overview',
    version: null,
  });
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<CreateMode>(null);
  const [settingDefaultId, setSettingDefaultId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void Promise.all([getSemanticAnalyticsCatalog(), listPersonalDashboards(), getPersonalDashboardDefault()])
      .then(([nextCatalog, nextDashboards, nextDefault]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setDashboards(nextDashboards);
        setDefaultDashboard(nextDefault);
      })
      .catch(requestError => active && setError(dashboardDiagnostics(requestError)));
    return () => {
      active = false;
    };
  }, []);

  const selectPersonalDefault = async (dashboardId: number) => {
    if (settingDefaultId || dashboardId === defaultDashboard.dashboardId) return;
    setSettingDefaultId(`personal-${dashboardId}`);
    setError(null);
    try {
      setDefaultDashboard(await setPersonalDashboardDefault(defaultDashboard.version, dashboardId));
    } catch (requestError) {
      setError(dashboardDiagnostics(requestError));
    } finally {
      setSettingDefaultId(null);
    }
  };

  const selectOfficialDefault = async (templateId: OfficialDashboardTemplateId) => {
    if (settingDefaultId || (defaultDashboard.dashboardId === null && defaultDashboard.officialTemplateId === templateId)) return;
    setSettingDefaultId(`official-${templateId}`);
    setError(null);
    try {
      setDefaultDashboard(await setPersonalDashboardDefault(defaultDashboard.version, null, templateId));
    } catch (requestError) {
      setError(dashboardDiagnostics(requestError));
    } finally {
      setSettingDefaultId(null);
    }
  };

  const templates = useMemo(() => (catalog ? recommendedDashboardTemplates(catalog) : []), [catalog]);
  return (
    <ProductPage wide className="personal-dashboard-gallery">
      <ProductPageHeader
        headingId="dashboard-gallery-heading"
        title={copy(locale, 'Dashboards', 'Dashboards')}
        subtitle={copy(
          locale,
          'Empieza con una vista recomendada o crea un espacio personal para tus próximas visualizaciones.',
          'Start with a recommended view or create a personal space for your upcoming visualizations.',
        )}
        actions={
          <Button color="primary" onClick={() => setMode({ kind: 'blank' })} disabled={!catalog} data-cy="createPersonalDashboard">
            {copy(locale, 'Crear dashboard', 'Create dashboard')}
          </Button>
        }
      />
      {error ? <Alert color="danger">{error}</Alert> : null}
      {!catalog ? (
        <div className="border rounded-2 p-4 text-center">{copy(locale, 'Cargando dashboards…', 'Loading dashboards…')}</div>
      ) : (
        <div className="d-flex flex-column gap-4">
          <ProductSection title={copy(locale, 'Recomendados', 'Recommended')}>
            <div className="row g-3" data-cy="recommendedDashboardGallery">
              {templates.map(template => (
                <article className="col-12 col-md-6 col-xl-4" key={template.id}>
                  <div className="card h-100 shadow-sm border">
                    <div className="card-body d-flex flex-column p-4">
                      <div className="d-flex align-items-start justify-content-between gap-2">
                        <h2 className="h5">{localized(locale, template.title)}</h2>
                        {defaultDashboard.dashboardId === null && defaultDashboard.officialTemplateId === template.id ? (
                          <span className="badge text-bg-primary" data-cy={`recommendedDashboardDefault-${template.id}`}>
                            {copy(locale, 'Predeterminado', 'Default')}
                          </span>
                        ) : null}
                      </div>
                      <p className="text-muted small flex-grow-1">{localized(locale, template.description)}</p>
                      <p className="small text-muted mb-3">
                        {copy(locale, `${template.widgets.length} visualizaciones`, `${template.widgets.length} visualizations`)}
                      </p>
                      <div className="d-flex flex-wrap gap-2">
                        <Button tag={Link} to={`/analytics/dashboards/${template.id}`} color="primary" outline>
                          {copy(locale, 'Abrir', 'Open')}
                        </Button>
                        <Button color="secondary" outline onClick={() => setMode({ kind: 'template', template })}>
                          {copy(locale, 'Usar como base', 'Use as a starting point')}
                        </Button>
                        {defaultDashboard.dashboardId === null && defaultDashboard.officialTemplateId === template.id ? null : (
                          <Button
                            color="secondary"
                            outline
                            onClick={() => void selectOfficialDefault(template.id)}
                            disabled={settingDefaultId !== null}
                            data-cy={`recommendedDashboardSetDefault-${template.id}`}
                          >
                            {settingDefaultId === `official-${template.id}`
                              ? copy(locale, 'Guardando…', 'Saving…')
                              : copy(locale, 'Establecer como predeterminado', 'Set as default')}
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </ProductSection>
          <ProductSection title={copy(locale, 'Mis dashboards', 'My dashboards')}>
            <div className="d-flex justify-content-between align-items-center gap-3 mb-3">
              <p className="small text-muted mb-0">
                {copy(
                  locale,
                  'Tus dashboards guardan contexto y definiciones, nunca resultados financieros.',
                  'Your dashboards save context and definitions, never financial results.',
                )}
              </p>
              <Button color="primary" size="sm" onClick={() => setMode({ kind: 'blank' })}>
                {copy(locale, 'Crear dashboard', 'Create dashboard')}
              </Button>
            </div>
            {dashboards.length ? (
              <div className="row g-3" data-cy="personalDashboardGallery">
                {dashboards.map(dashboard => (
                  <article className="col-12 col-md-6 col-xl-4" key={dashboard.id}>
                    <div className="card card-body h-100 border shadow-sm">
                      <div className="d-flex align-items-start justify-content-between gap-2">
                        <h2 className="h5 mb-1">{dashboard.name}</h2>
                        {dashboard.id === defaultDashboard.dashboardId ? (
                          <span className="badge text-bg-primary" data-cy={`personalDashboardDefault-${dashboard.id}`}>
                            {copy(locale, 'Predeterminado', 'Default')}
                          </span>
                        ) : null}
                      </div>
                      {dashboard.description ? <p className="small text-muted mb-3">{dashboard.description}</p> : null}
                      <span className="small text-primary mt-auto mb-3">
                        {dashboard.widgetCount
                          ? copy(locale, `${dashboard.widgetCount} visualizaciones`, `${dashboard.widgetCount} visualizations`)
                          : copy(locale, 'Sin visualizaciones', 'No visualizations')}
                      </span>
                      <div className="d-flex flex-wrap gap-2">
                        <Button tag={Link} to={`/analytics/dashboards/personal/${dashboard.id}`} color="primary" outline size="sm">
                          {copy(locale, 'Abrir', 'Open')}
                        </Button>
                        <Button
                          color="secondary"
                          outline
                          size="sm"
                          disabled={dashboard.id === defaultDashboard.dashboardId || settingDefaultId !== null}
                          onClick={() => void selectPersonalDefault(dashboard.id)}
                          data-cy={`personalDashboardSetDefault-${dashboard.id}`}
                        >
                          {settingDefaultId === `personal-${dashboard.id}`
                            ? copy(locale, 'Guardando…', 'Saving…')
                            : copy(locale, 'Establecer como predeterminado', 'Set as default')}
                        </Button>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="border rounded-2 p-4 text-center" data-cy="personalDashboardEmptyState">
                <p className="mb-3">{emptyText(locale)}</p>
                <Button color="primary" onClick={() => setMode({ kind: 'blank' })}>
                  {copy(locale, 'Crear dashboard', 'Create dashboard')}
                </Button>
              </div>
            )}
          </ProductSection>
        </div>
      )}
      <DashboardCreationDialog mode={mode} catalog={catalog} locale={locale} onClose={() => setMode(null)} />
    </ProductPage>
  );
};

export default PersonalDashboardGallery;
