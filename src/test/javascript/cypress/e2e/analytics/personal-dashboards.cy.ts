/**
 * Browser presentation coverage for V2-3. Requests are deliberately intercepted so this is a deterministic UX
 * contract test only; PersonalDashboardResourceIT provides the real PostgreSQL ownership and atomic-copy evidence.
 */
describe('Personal dashboards V2-3', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const context = {
    schemaVersion: 3,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_MONTH', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };
  const catalog = {
    schemaVersion: 3,
    semanticModel: 'FINTRACK_FINANCIAL',
    catalogVersion: '1.3',
    supportedRelativePeriods: ['THIS_MONTH', 'THIS_YEAR'],
    defaultLimit: 100,
    maxLimit: 500,
    authenticatedOwnerRequired: true,
    datasets: [],
  };
  const definition = {
    schemaVersion: 3,
    semanticModel: 'FINTRACK_FINANCIAL',
    dataset: 'TRANSACTION_LEDGER',
    timeBinding: 'TRANSACTION_DATE',
    projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
    predicates: [],
    groupBy: [],
    orderBy: [],
    limit: 100,
  };
  const widget = {
    id: 73,
    title: 'Economic expenses',
    description: 'Excludes linked internal transfers.',
    definition,
    visualizationType: 'KPI',
    visualizationOptions: {},
    position: 0,
    width: 'COMPACT',
    visible: true,
  };
  const response = {
    schemaVersion: 3,
    semanticModel: 'FINTRACK_FINANCIAL',
    effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
    executedAt: '2026-01-31T00:00:00Z',
    columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
    rows: [
      {
        dimensions: [],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 1250, unit: 'MONEY', availability: 'COMPLETE' }],
      },
    ],
  };
  const personalDashboard = (id: number, name: string, widgets = [widget]) => ({
    id,
    name,
    description: 'Personal dashboard fixture',
    version: 0,
    widgetCount: widgets.length,
    schemaVersion: 3,
    defaultContext: context,
    widgets,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('GET', '**/api/analytics/semantic/catalog', catalog).as('catalog');
    cy.intercept('GET', '**/api/financial-accounts/selectable', [
      { id: 7, name: 'Daily account', accountType: 'DEBIT', currency: 'MXN' },
    ]).as('accounts');
    cy.intercept('GET', '**/api/personal-dashboards', []).as('listPersonalDashboards');
    cy.intercept('POST', '**/api/personal-dashboards', request => {
      expect(request.body.defaultContext).to.deep.equal(context);
      request.reply({ statusCode: 201, body: personalDashboard(44, request.body.name, []) });
    }).as('createPersonalDashboard');
    cy.intercept('POST', '**/api/personal-dashboards/from-template', request => {
      expect(request.body.templateId).to.equal('financial-overview');
      expect(request.body.widgets.length).to.be.greaterThan(1);
      expect(request.body.widgets.every((savedWidget: { definition: object }) => !('context' in savedWidget.definition))).to.equal(true);
      request.reply({ statusCode: 201, body: personalDashboard(45, request.body.name) });
    }).as('copyPersonalDashboardTemplate');
    cy.intercept('GET', '**/api/personal-dashboards/44', personalDashboard(44, 'Blank dashboard', [])).as('getBlankDashboard');
    cy.intercept('GET', '**/api/personal-dashboards/45', personalDashboard(45, 'Independent overview')).as('getCopiedDashboard');
    cy.intercept('POST', '**/api/analytics/semantic/queries/execute', request => {
      expect(request.body.definition).to.deep.equal(definition);
      request.reply(response);
    }).as('executePersonalWidget');
  });

  it('keeps the gallery metadata-only, creates an empty dashboard, and renders its read-only viewer', () => {
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards');
    cy.wait(['@catalog', '@listPersonalDashboards']);
    cy.get('[data-cy="recommendedDashboardGallery"] article').should('have.length', 3);
    cy.get('[data-cy="personalDashboardEmptyState"]').should('be.visible');
    cy.get('@executePersonalWidget.all').should('have.length', 0);
    cy.screenshot('personal-dashboard-gallery-desktop', { capture: 'fullPage' });

    cy.get('[data-cy="createPersonalDashboard"]').should('not.have.attr', 'tabindex', '-1').click();
    cy.get('[data-cy="personalDashboardName"]').type('Blank dashboard');
    cy.get('[data-cy="personalDashboardSubmit"]').click();
    cy.wait('@createPersonalDashboard');
    cy.location('pathname').should('eq', '/analytics/dashboards/personal/44');
    cy.wait(['@getBlankDashboard', '@accounts']);
    cy.get('[data-cy="personalDashboardViewerEmptyState"]').should('be.visible');
    cy.contains('temporary for this view').should('be.visible');

    cy.viewport('iphone-6');
    cy.screenshot('personal-dashboard-empty-mobile', { capture: 'fullPage' });
  });

  it('copies an official template independently and executes its saved canonical definition with only temporary context changes', () => {
    cy.visit('/analytics/dashboards');
    cy.wait(['@catalog', '@listPersonalDashboards']);
    cy.get('[data-cy="recommendedDashboardGallery"] article').first().contains('button', 'Use as a starting point').click();
    cy.get('[data-cy="personalDashboardName"]').clear();
    cy.get('[data-cy="personalDashboardName"]').type('Independent overview');
    cy.get('[data-cy="personalDashboardSubmit"]').click();
    cy.wait('@copyPersonalDashboardTemplate');
    cy.location('pathname').should('eq', '/analytics/dashboards/personal/45');
    cy.wait(['@getCopiedDashboard', '@accounts', '@executePersonalWidget']);
    cy.get('[data-cy="personalDashboardWidget-73"]').contains('Economic expenses').should('be.visible');
    cy.contains('$1,250.00 MXN').should('be.visible');

    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.wait('@executePersonalWidget').its('request.body.context.time.relativePeriod').should('eq', 'THIS_YEAR');
    cy.contains('temporary for this view').should('be.visible');
  });
});
