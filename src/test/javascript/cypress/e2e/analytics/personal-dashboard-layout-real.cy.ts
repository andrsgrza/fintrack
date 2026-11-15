/** Real browser -> REST -> Spring -> disposable PostgreSQL coverage for DASHBOARDS-V2-5 layout customization. */
describe('Personal dashboard layout V2-5 real acceptance', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const context = {
    schemaVersion: 1,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_YEAR', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };
  const countByInstitution = {
    schemaVersion: 1,
    semanticModel: 'FINTRACK_FINANCIAL',
    dataset: 'TRANSACTION_LEDGER',
    timeBinding: 'TRANSACTION_DATE',
    projections: [{ alias: 'movimientos', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] }],
    predicates: [],
    groupBy: ['ACCOUNT_INSTITUTION'],
    orderBy: [{ field: 'ACCOUNT_INSTITUTION', projectionAlias: null, direction: 'ASC' }],
    limit: 100,
  };
  const expenseTotal = {
    schemaVersion: 1,
    semanticModel: 'FINTRACK_FINANCIAL',
    dataset: 'TRANSACTION_LEDGER',
    timeBinding: 'TRANSACTION_DATE',
    projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
    predicates: [
      { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
      { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
    ],
    groupBy: [],
    orderBy: [],
    limit: 100,
  };
  const monthlyCount = {
    ...countByInstitution,
    projections: [{ alias: 'movimientos', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] }],
    groupBy: ['TRANSACTION_MONTH'],
    orderBy: [{ field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' }],
  };

  let dashboardId: number;
  let dashboardVersion: number;
  let barWidgetId: number;
  let kpiWidgetId: number;
  let lineWidgetId: number;
  let runId: string;

  const addWidget = (title: string, definition: object, visualizationType: string, width: string) => {
    const requestId = title.toLowerCase().replace(/[^a-z0-9]+/g, '-');
    return cy
      .authenticatedRequest<{ version: number; widgets: Array<{ id: number; title: string }> }>({
        method: 'POST',
        url: `/api/personal-dashboards/${dashboardId}/widgets`,
        body: {
          expectedVersion: dashboardVersion,
          title,
          definition,
          visualizationType,
          visualizationOptions: {},
          width,
          visible: true,
          clientRequestId: `layout-widget-${requestId}-${runId}`,
        },
      } as Cypress.RequestOptions)
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        dashboardVersion = body.version;
        const widget = body.widgets.find(candidate => candidate.title === title);
        expect(widget?.id).to.be.greaterThan(0);
        return widget?.id;
      });
  };

  beforeEach(() => {
    dashboardId = 0;
    dashboardVersion = 0;
    barWidgetId = 0;
    kpiWidgetId = 0;
    lineWidgetId = 0;
    runId = `${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    cy.login(username, password);
    cy.authenticatedRequest<{ id: number; version: number }>({
      method: 'POST',
      url: '/api/personal-dashboards',
      body: {
        name: `Layout E2E ${runId.slice(-6)}`,
        description: 'Isolated layout customization workflow',
        defaultContext: context,
        clientRequestId: `layout-dashboard-${runId}`,
      },
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(201);
      dashboardId = body.id;
      dashboardVersion = body.version;
    });
    cy.then(() => addWidget('Institution movement count', countByInstitution, 'BAR', 'MEDIUM')).then(id => {
      barWidgetId = Number(id);
    });
    cy.then(() => addWidget('Economic expense total', expenseTotal, 'KPI', 'COMPACT')).then(id => {
      kpiWidgetId = Number(id);
    });
    cy.then(() =>
      cy
        .authenticatedRequest<{ version: number }>({
          method: 'PUT',
          url: `/api/personal-dashboards/${dashboardId}/layout`,
          body: {
            expectedVersion: dashboardVersion,
            widgets: [
              { widgetId: barWidgetId, width: 'MEDIUM', visible: true },
              { widgetId: kpiWidgetId, width: 'COMPACT', visible: true },
            ],
          },
        } as Cypress.RequestOptions)
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          dashboardVersion = body.version;
        }),
    );
    cy.then(() => addWidget('Monthly movement trend', monthlyCount, 'LINE', 'FULL')).then(id => {
      lineWidgetId = Number(id);
    });
    cy.intercept('GET', '/api/personal-dashboards/*').as('loadDashboard');
    cy.intercept('PUT', '/api/personal-dashboards/*/layout').as('saveLayout');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('executeWidget');
  });

  it('previews, saves, reloads, and cancels a complete personal layout without changing queries', () => {
    cy.viewport(1440, 960);
    cy.visit(`/analytics/dashboards/personal/${dashboardId}`);
    cy.wait('@loadDashboard');
    cy.get('[data-cy^="personalDashboardWidget-"]').should('have.length', 3);
    cy.get('[data-cy="personalDashboardCustomize"]').click();

    cy.get(`[data-cy="personalDashboardMoveUp-${barWidgetId}"]`).should('be.disabled');
    cy.get(`[data-cy="personalDashboardMoveDown-${lineWidgetId}"]`).should('be.disabled');
    cy.get(`[data-cy="personalDashboardDragHandle-${barWidgetId}"]`).focus();
    cy.realPress('Space');
    cy.realPress('ArrowRight');
    cy.realPress('Space');
    cy.get('[data-cy="personalDashboardWidgets"] > article')
      .first()
      .should('have.attr', 'data-cy', `personalDashboardWidget-${kpiWidgetId}`);
    cy.get(`[data-cy="personalDashboardWidth-${barWidgetId}"]`).select('FULL');
    cy.get(`[data-cy="personalDashboardHide-${lineWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardHiddenWidgets"]').should('contain.text', 'Monthly movement trend');
    cy.get('[data-cy="personalDashboardPreset-CHART_FOCUSED"]').click();
    cy.contains('[data-cy="personalDashboardUnsavedChanges"]', /Cambios sin guardar|Unsaved changes/).should('be.visible');
    cy.get('[aria-label="Preajustes de distribución"]').then($presets => {
      expect($presets[0].getBoundingClientRect().right).to.be.lte(1440);
    });
    cy.document().then(document => {
      expect(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)).to.be.lte(1440);
    });
    cy.window().then(window => {
      const overflowingElements = Array.from(window.document.querySelectorAll('*'))
        .filter(element => element.getBoundingClientRect().right > window.innerWidth + 1)
        .map(element => `${element.tagName}.${Array.from(element.classList).join('.')}`)
        .slice(0, 12);
      if (overflowingElements.length) {
        throw new Error(`Elements beyond viewport: ${overflowingElements.join(', ')}`);
      }
    });
    cy.screenshot('personal-dashboard-layout-customization-desktop', { capture: 'viewport' });

    cy.get('[data-cy="personalDashboardSaveLayout"]').click();
    cy.wait('@saveLayout').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.have.keys(['expectedVersion', 'widgets']);
      expect(request.body).not.to.have.property('definition');
      expect(request.body).not.to.have.property('context');
      expect(request.body.widgets).to.deep.equal([
        { widgetId: kpiWidgetId, width: 'COMPACT', visible: true },
        { widgetId: barWidgetId, width: 'FULL', visible: true },
        { widgetId: lineWidgetId, width: 'FULL', visible: false },
      ]);
    });
    cy.get('[data-cy="personalDashboardCustomize"]').should('be.visible');

    cy.authenticatedRequest<{
      widgets: Array<{
        id: number;
        position: number;
        width: string;
        visible: boolean;
        definition: object;
      }>;
    }>({
      method: 'GET',
      url: `/api/personal-dashboards/${dashboardId}`,
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.widgets.find(widget => widget.id === kpiWidgetId)).to.include({
        position: 0,
        width: 'COMPACT',
        visible: true,
      });
      expect(body.widgets.find(widget => widget.id === barWidgetId)).to.include({
        position: 1,
        width: 'FULL',
        visible: true,
      });
      expect(body.widgets.find(widget => widget.id === lineWidgetId)).to.include({
        position: 2,
        width: 'FULL',
        visible: false,
      });
      body.widgets.forEach(widget => {
        expect(widget).not.to.have.property('initialPosition');
        expect(widget).not.to.have.property('initialWidth');
        expect(widget).not.to.have.property('initialVisible');
      });
      expect(body.widgets.find(widget => widget.id === barWidgetId)?.definition).to.deep.equal(countByInstitution);
      expect(body.widgets.find(widget => widget.id === kpiWidgetId)?.definition).to.deep.equal(expenseTotal);
      expect(body.widgets.find(widget => widget.id === lineWidgetId)?.definition).to.deep.equal(monthlyCount);
    });

    cy.reload();
    cy.wait('@loadDashboard');
    cy.get(`[data-cy="personalDashboardWidget-${lineWidgetId}"]`).should('not.exist');
    cy.get(`[data-cy="personalDashboardWidget-${barWidgetId}"]`).should('have.class', 'col-12');
    cy.get('[data-cy="personalDashboardRestoreLayout"]').should('not.exist');
    cy.get('[data-cy="personalDashboardCustomize"]').click();
    cy.get(`[data-cy="personalDashboardShow-${lineWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardPreset-BALANCED"]').click();
    cy.get('[data-cy="personalDashboardPreset-COMPACT"]').click();
    cy.get('[data-cy="personalDashboardPreset-CHART_FOCUSED"]').click();
    cy.get('@saveLayout.all').should('have.length', 1);
    cy.get('[data-cy="personalDashboardCancelLayout"]').click();
    cy.get(`[data-cy="personalDashboardWidget-${lineWidgetId}"]`).should('not.exist');
    cy.get(`[data-cy="personalDashboardWidget-${barWidgetId}"]`).should('have.class', 'col-12');

    cy.get('[data-cy="personalDashboardCustomize"]').click();
    cy.get(`[data-cy="personalDashboardHide-${kpiWidgetId}"]`).click();
    cy.get(`[data-cy="personalDashboardHide-${barWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardAllHiddenState"]').should('be.visible');
    cy.get('[data-cy="personalDashboardRestoreAllWidgets"]').click();
    cy.get(`[data-cy="personalDashboardWidget-${lineWidgetId}"]`).should('be.visible');
    cy.get('[data-cy="personalDashboardCancelLayout"]').click();

    cy.get('[data-cy="personalDashboardCustomize"]').click();
    cy.get(`[data-cy="personalDashboardShow-${lineWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardPreset-BALANCED"]').click();
    cy.get('[data-cy="personalDashboardPreset-COMPACT"]').click();
    cy.get('[data-cy="personalDashboardPreset-CHART_FOCUSED"]').click();
    cy.get('[data-cy="personalDashboardSaveLayout"]').click();
    cy.wait('@saveLayout').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.widgets).to.deep.equal([
        { widgetId: kpiWidgetId, width: 'COMPACT', visible: true },
        { widgetId: barWidgetId, width: 'FULL', visible: true },
        { widgetId: lineWidgetId, width: 'FULL', visible: true },
      ]);
    });
    cy.reload();
    cy.wait('@loadDashboard');
    cy.get('[data-cy^="personalDashboardWidget-"]').should('have.length', 3);

    cy.viewport('iphone-6');
    cy.document().then(document => {
      expect(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)).to.be.lte(375);
    });
    cy.get('[data-cy="personalDashboardWidgets"]').scrollIntoView();
    cy.screenshot('personal-dashboard-layout-saved-mobile', { capture: 'viewport' });
  });
});
