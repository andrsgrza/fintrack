/** Real browser -> REST -> Spring -> disposable PostgreSQL acceptance for DASHBOARDS-V2-6.1. */
describe('Personal widget lifecycle real acceptance', () => {
  type Widget = {
    id: number;
    title: string;
    description: string | null;
    definition: object;
    visualizationType: string;
    visualizationOptions: object;
    position: number;
    width: string;
    visible: boolean;
  };
  type Dashboard = {
    id: number;
    version: number;
    widgets: Widget[];
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const runId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const context = {
    schemaVersion: 1,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_YEAR', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };
  const economicExpenseTotal = {
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

  let dashboard: Dashboard;
  let expenseWidgetId = 0;
  let comparisonWidgetId = 0;
  let duplicateWidgetId = 0;

  const addWidget = (title: string, definition: object, visualizationType: string, width: string) =>
    cy
      .authenticatedRequest<Dashboard>({
        method: 'POST',
        url: `/api/personal-dashboards/${dashboard.id}/widgets`,
        body: {
          expectedVersion: dashboard.version,
          title,
          definition,
          visualizationType,
          visualizationOptions: {},
          width,
          visible: true,
          clientRequestId: `widget-lifecycle-add-${title.replace(/[^a-z0-9]+/gi, '-')}-${runId}`,
        },
      } as Cypress.RequestOptions)
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        dashboard = body;
        const widget = body.widgets.find(candidate => candidate.title === title);
        expect(widget?.id).to.be.greaterThan(0);
        return widget?.id;
      });

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<Dashboard>({
      method: 'POST',
      url: '/api/personal-dashboards',
      body: {
        name: `Widget lifecycle ${runId}`,
        description: 'Disposable browser-to-PostgreSQL lifecycle fixture',
        defaultContext: context,
        clientRequestId: `widget-lifecycle-dashboard-${runId}`,
      },
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(201);
      dashboard = body;
    });
    cy.then(() => addWidget('Economic expense total', economicExpenseTotal, 'KPI', 'COMPACT')).then(id => {
      expenseWidgetId = Number(id);
    });
    cy.then(() => addWidget('Institution movement count', countByInstitution, 'BAR', 'MEDIUM')).then(id => {
      comparisonWidgetId = Number(id);
    });
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('GET', '/api/personal-dashboards/*').as('loadDashboard');
    cy.intercept('PUT', '/api/personal-dashboards/*/widgets/*').as('renameWidget');
    cy.intercept('POST', '/api/personal-dashboards/*/widgets/*/duplicate').as('duplicateWidget');
    cy.intercept('DELETE', '/api/personal-dashboards/*/widgets/*').as('deleteWidget');
    cy.intercept('PUT', '/api/personal-dashboards/*/layout').as('saveLayout');
  });

  it('renames, duplicates, deletes and reloads independent personal widgets without disturbing layout controls', () => {
    const renamedTitle = `Economic expenses renamed ${runId}`;
    const copiedTitle = `Independent copy ${runId}`;
    const originalDefinition = JSON.parse(JSON.stringify(economicExpenseTotal));

    cy.viewport(1440, 960);
    cy.visit(`/analytics/dashboards/personal/${dashboard.id}`);
    cy.wait('@loadDashboard').its('response.statusCode').should('eq', 200);
    cy.document().then(document =>
      expect(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)).to.be.lte(document.documentElement.clientWidth + 1),
    );
    cy.get(`[data-cy="personalDashboardWidgetMenu-${expenseWidgetId}"]`).should('be.visible').click();
    cy.get(`[data-cy="personalDashboardWidgetRename-${expenseWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardWidgetManageTitle"]').clear();
    cy.get('[data-cy="personalDashboardWidgetManageTitle"]').type(renamedTitle);
    cy.get('[data-cy="personalDashboardWidgetManageDescription"]').clear();
    cy.get('[data-cy="personalDashboardWidgetManageDescription"]').type('Renamed through the real browser');
    cy.get('[data-cy="personalDashboardWidgetManageSubmit"]').click();
    cy.wait('@renameWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ title: renamedTitle, width: 'COMPACT', visible: true });
      expect(request.body.definition).to.deep.equal(originalDefinition);
      dashboard = response?.body as Dashboard;
      expect(dashboard.widgets.find(widget => widget.id === expenseWidgetId)).to.include({
        id: expenseWidgetId,
        title: renamedTitle,
        position: 0,
        width: 'COMPACT',
        visible: true,
      });
    });
    cy.reload();
    cy.wait('@loadDashboard');
    cy.get(`[data-cy="personalDashboardWidget-${expenseWidgetId}"]`).should('contain.text', renamedTitle);

    cy.get(`[data-cy="personalDashboardWidgetMenu-${expenseWidgetId}"]`).click();
    cy.get(`[data-cy="personalDashboardWidgetDuplicate-${expenseWidgetId}"]`).click();
    cy.wait('@duplicateWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.expectedVersion).to.equal(dashboard.version);
      dashboard = response?.body as Dashboard;
      const copied = dashboard.widgets.find(widget => widget.id !== expenseWidgetId && widget.title === `${renamedTitle} (copia)`);
      if (!copied) throw new Error('Expected a new independent widget copy.');
      expect(copied).to.include({ position: 2, width: 'COMPACT', visible: true });
      expect(copied.definition).to.deep.equal(originalDefinition);
      duplicateWidgetId = copied.id;
    });
    cy.then(() => duplicateWidgetId).then(widgetId =>
      cy.get(`[data-cy="personalDashboardWidget-${widgetId}"]`).should('contain.text', `${renamedTitle} (copia)`),
    );
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetMenu-${widgetId}"]`).click());
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetRename-${widgetId}"]`).click());
    cy.get('[data-cy="personalDashboardWidgetManageTitle"]').clear();
    cy.get('[data-cy="personalDashboardWidgetManageTitle"]').type(copiedTitle);
    cy.get('[data-cy="personalDashboardWidgetManageSubmit"]').click();
    cy.wait('@renameWidget').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      dashboard = response?.body as Dashboard;
    });
    cy.authenticatedRequest<Dashboard>({ method: 'GET', url: `/api/personal-dashboards/${dashboard.id}` } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        const original = body.widgets.find(widget => widget.id === expenseWidgetId);
        const copied = body.widgets.find(widget => widget.id === duplicateWidgetId);
        expect(original).to.include({ title: renamedTitle, position: 0, width: 'COMPACT', visible: true });
        expect(copied).to.include({ title: copiedTitle, position: 2, width: 'COMPACT', visible: true });
        expect(original?.definition).to.deep.equal(originalDefinition);
        expect(copied?.definition).to.deep.equal(originalDefinition);
        dashboard = body;
      },
    );

    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetMenu-${widgetId}"]`).click());
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetDelete-${widgetId}"]`).click());
    cy.contains(/permanently delete|eliminarás permanentemente/i).should('contain.text', copiedTitle);
    cy.contains('button', /Cancel|Cancelar/).click();
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidget-${widgetId}"]`).should('exist'));

    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetMenu-${widgetId}"]`).click());
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidgetDelete-${widgetId}"]`).click());
    cy.get('[data-cy="personalDashboardWidgetManageSubmit"]').click();
    cy.wait('@deleteWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.query.expectedVersion).to.equal(String(dashboard.version));
      dashboard = response?.body as Dashboard;
      expect(dashboard.widgets.map(widget => widget.id)).not.to.include(duplicateWidgetId);
      expect(dashboard.widgets.map(widget => widget.position)).to.deep.equal([0, 1]);
    });
    cy.then(() => duplicateWidgetId).then(widgetId => cy.get(`[data-cy="personalDashboardWidget-${widgetId}"]`).should('not.exist'));
    cy.reload();
    cy.wait('@loadDashboard');
    cy.authenticatedRequest<Dashboard>({ method: 'GET', url: `/api/personal-dashboards/${dashboard.id}` } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.widgets).to.have.length(2);
        expect(body.widgets.map(widget => widget.position)).to.deep.equal([0, 1]);
        expect(body.widgets.find(widget => widget.id === expenseWidgetId)?.definition).to.deep.equal(originalDefinition);
        dashboard = body;
      },
    );

    cy.get('[data-cy="personalDashboardCustomize"]').click();
    cy.get('[data-cy^="personalDashboardWidgetMenu-"]').should('not.exist');
    cy.get(`[data-cy="personalDashboardHide-${comparisonWidgetId}"]`).click();
    cy.get('[data-cy="personalDashboardSaveLayout"]').click();
    cy.wait('@saveLayout').its('response.statusCode').should('eq', 200);
    cy.reload();
    cy.wait('@loadDashboard');
    cy.get(`[data-cy="personalDashboardWidget-${comparisonWidgetId}"]`).should('not.exist');
    cy.get('[data-cy="personalDashboardCustomize"]').click();
    cy.get(`[data-cy="personalDashboardShow-${comparisonWidgetId}"]`).click();
    cy.get(`[data-cy="personalDashboardWidth-${comparisonWidgetId}"]`).select('FULL');
    cy.get('[data-cy="personalDashboardCancelLayout"]').click();
    cy.get(`[data-cy="personalDashboardWidget-${comparisonWidgetId}"]`).should('not.exist');
    cy.screenshot('personal-widget-lifecycle-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)).to.be.lte(375));
    cy.screenshot('personal-widget-lifecycle-mobile', { capture: 'viewport' });
  });

  it('does not expose personal widget mutation actions for an official dashboard', () => {
    cy.visit('/analytics/dashboards/financial-overview');
    cy.get('[data-cy^="personalDashboardWidgetMenu-"]').should('not.exist');
  });
});
