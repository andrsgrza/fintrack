/**
 * Browser UX contract for V2-4. API responses are intentionally intercepted here; the matching real PostgreSQL
 * journey lives in personal-dashboard-widgets-real.cy.ts.
 */
describe('Personal dashboard widgets V2-4', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const context = {
    schemaVersion: 1,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_MONTH', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };
  const expenseDefinition = {
    schemaVersion: 1,
    semanticModel: 'FINTRACK_FINANCIAL',
    dataset: 'TRANSACTION_LEDGER',
    timeBinding: 'TRANSACTION_DATE',
    projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
    predicates: [
      { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
      { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
    ],
    groupBy: ['CATEGORY_ID'],
    orderBy: [{ field: 'CATEGORY_ID', projectionAlias: null, direction: 'ASC' }],
    limit: 100,
  };
  const catalog = {
    schemaVersion: 1,
    semanticModel: 'FINTRACK_FINANCIAL',
    catalogVersion: '1.3',
    supportedRelativePeriods: ['THIS_MONTH', 'THIS_YEAR'],
    defaultLimit: 100,
    maxLimit: 500,
    authenticatedOwnerRequired: true,
    datasets: [
      {
        dataset: 'TRANSACTION_LEDGER',
        grain: 'Financial transaction',
        timeBinding: 'TRANSACTION_DATE',
        relationships: [],
        fields: [],
        aggregateCapabilities: [
          { function: 'COUNT', field: 'TRANSACTION_ID', outputUnit: 'COUNT', additivity: 'ADDITIVE' },
          { function: 'SUM', field: 'TRANSACTION_AMOUNT', outputUnit: 'MONEY', additivity: 'ADDITIVE' },
          { function: 'AVG', field: 'TRANSACTION_AMOUNT', outputUnit: 'MONEY', additivity: 'NON_ADDITIVE' },
        ],
        predicateCapabilities: [
          { field: 'TRANSACTION_FLOW', operators: ['EQUALS'] },
          { field: 'TRANSFER_LINK_STATUS', operators: ['EQUALS'] },
          { field: 'CATEGORY_ID', operators: ['EQUALS', 'IN', 'DESCENDANT_OF', 'IS_UNCATEGORIZED'] },
        ],
        groupings: ['TRANSACTION_MONTH', 'CATEGORY_ID', 'ACCOUNT_INSTITUTION'],
        additivity: 'ADDITIVE',
        currencyPolicy: 'PARTITION_BY_ACCOUNT_CURRENCY',
        temporalSemantics: 'Inclusive transaction date.',
      },
    ],
  };
  const result = {
    schemaVersion: 1,
    semanticModel: 'FINTRACK_FINANCIAL',
    effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
    executedAt: '2026-01-31T00:00:00Z',
    columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
    rows: [{ dimensions: [], currencyCode: 'MXN', values: [{ alias: 'gastos', value: 77, unit: 'MONEY', availability: 'COMPLETE' }] }],
  };

  let dashboard = {
    id: 44,
    name: 'Widget destination',
    description: 'Fixture dashboard',
    version: 3,
    widgetCount: 0,
    schemaVersion: 3,
    defaultContext: context,
    widgets: [] as Array<Record<string, unknown>>,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    dashboard = { ...dashboard, version: 3, widgetCount: 0, widgets: [] };
    cy.login(username, password);
    cy.intercept('GET', '**/api/analytics/semantic/catalog', catalog).as('catalog');
    cy.intercept('GET', '**/api/financial-accounts/selectable', []).as('accounts');
    cy.intercept('GET', '**/api/categories?sort=name,asc', []).as('categories');
    cy.intercept('GET', '**/api/tags?sort=name,asc', []).as('tags');
    cy.intercept('POST', '**/api/analytics/semantic/queries/format', {
      text: 'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER',
    }).as('formatQuery');
    cy.intercept('POST', '**/api/analytics/semantic/queries/parse', {
      definition: expenseDefinition,
      canonicalText: 'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER',
    }).as('parseQuery');
    cy.intercept('POST', '**/api/analytics/semantic/queries/execute', request => {
      expect(request.body.context.time.relativePeriod).to.equal('THIS_MONTH');
      request.reply(result);
    }).as('executeWidget');
    cy.intercept('GET', '**/api/personal-dashboards', [
      {
        id: dashboard.id,
        name: dashboard.name,
        description: dashboard.description,
        version: dashboard.version,
        widgetCount: dashboard.widgetCount,
      },
    ]).as('listDashboards');
    cy.intercept('GET', '**/api/personal-dashboards/44', request => request.reply(dashboard)).as('loadDashboard');
    cy.intercept('POST', '**/api/personal-dashboards/44/widgets', request => {
      expect(request.body.expectedVersion).to.equal(3);
      expect(request.body.definition).not.to.have.property('context');
      expect(request.body).not.to.have.property('context');
      dashboard = {
        ...dashboard,
        version: 4,
        widgetCount: 1,
        widgets: [
          {
            id: 73,
            title: request.body.title,
            description: request.body.description ?? null,
            definition: request.body.definition,
            visualizationType: request.body.visualizationType,
            visualizationOptions: request.body.visualizationOptions,
            position: 0,
            width: request.body.width,
            visible: true,
          },
        ],
      };
      request.reply({ statusCode: 200, body: dashboard });
    }).as('addWidget');
    cy.intercept('PUT', '**/api/personal-dashboards/44/widgets/73', request => {
      expect(request.body.expectedVersion).to.equal(4);
      expect(request.body.width).to.equal('MEDIUM');
      expect(request.body.visible).to.equal(true);
      dashboard = {
        ...dashboard,
        version: 5,
        widgets: [
          {
            ...dashboard.widgets[0],
            title: request.body.title,
            description: request.body.description ?? null,
            definition: request.body.definition,
            visualizationType: request.body.visualizationType,
            visualizationOptions: request.body.visualizationOptions,
          },
        ],
      };
      request.reply({ statusCode: 200, body: dashboard });
    }).as('updateWidget');
  });

  it('saves and edits the same widget while the dashboard, not the preview, supplies context', () => {
    cy.viewport(1440, 960);
    cy.visit('/analytics/explorer?dashboardId=44');
    cy.wait(['@catalog', '@accounts', '@categories', '@tags']);
    cy.get('[data-cy="analyticsExample-expenses-by-category"]').click();
    cy.wait(['@formatQuery', '@parseQuery']);
    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveDashboard"]').should('have.value', '44');
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Saved expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@addWidget').its('response.statusCode').should('eq', 200);
    cy.location('pathname').should('eq', '/analytics/dashboards/personal/44');
    cy.wait(['@loadDashboard', '@executeWidget']);
    cy.get('[data-cy="personalDashboardWidget-73"]').contains('Saved expenses').should('be.visible');

    cy.get('[data-cy="personalDashboardEditWidget-73"]').click();
    cy.location('search').should('include', 'dashboardId=44').and('include', 'widgetId=73');
    cy.wait('@loadDashboard');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Updated expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@updateWidget').its('response.statusCode').should('eq', 200);
    cy.location('pathname').should('eq', '/analytics/dashboards/personal/44');
    cy.reload();
    cy.wait('@loadDashboard');
    cy.get('[data-cy="personalDashboardWidget-73"]').contains('Updated expenses').should('be.visible');
    cy.screenshot('personal-dashboard-widget-save-edit-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-widget-save-edit-mobile', { capture: 'viewport' });
  });
});
