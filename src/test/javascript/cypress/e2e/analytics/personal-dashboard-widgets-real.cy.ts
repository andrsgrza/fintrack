/** Real browser -> REST -> Spring -> disposable PostgreSQL coverage for the V2-4 widget workflow. */
describe('Personal dashboard widgets V2-4 real acceptance', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const context = {
    schemaVersion: 1,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_MONTH', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };
  let dashboardRun = 0;
  let dashboardId: number;
  let widgetId: number;

  beforeEach(() => {
    cy.login(username, password);
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/personal-dashboards',
      body: {
        name: `Widget E2E ${suffix}-${++dashboardRun}`,
        description: 'Isolated widget workflow',
        defaultContext: context,
        clientRequestId: `widget-dashboard-${suffix}-${dashboardRun}`,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      dashboardId = body.id;
    });
    cy.intercept('POST', '/api/personal-dashboards/*/widgets').as('saveWidget');
    cy.intercept('PUT', '/api/personal-dashboards/*/widgets/*').as('updateWidget');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('executeWidget');
    cy.intercept('GET', '/api/personal-dashboards/*').as('loadDashboard');
  });

  it('persists a widget, reloads the same identity, and executes it with the dashboard context', () => {
    cy.viewport(1440, 960);
    cy.visit(`/analytics/explorer?dashboardId=${dashboardId}`);
    cy.contains('button', /Gastos por categoría|Expenses by category/).click();
    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Real saved expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@saveWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition).not.to.have.property('context');
      expect(request.body).not.to.have.property('context');
    });
    cy.location('pathname').should('eq', `/analytics/dashboards/personal/${dashboardId}`);
    cy.wait('@loadDashboard');
    cy.wait('@executeWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.context.time.relativePeriod).to.equal('THIS_MONTH');
    });
    cy.get('[data-cy^="personalDashboardEditWidget-"]').then(button => {
      widgetId = Number(button.attr('data-cy')?.replace('personalDashboardEditWidget-', ''));
      expect(widgetId).to.be.greaterThan(0);
      cy.wrap(button).click();
    });
    cy.wait('@loadDashboard');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Real updated expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@updateWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.url).to.include(`/widgets/${widgetId}`);
    });
    cy.reload();
    cy.wait('@loadDashboard');
    cy.then(() => {
      cy.get(`[data-cy="personalDashboardWidget-${widgetId}"]`).contains('Real updated expenses').should('be.visible');
    });
    cy.authenticatedRequest<{ widgets: Array<{ id: number; position: number; width: string; visible: boolean }> }>({
      method: 'GET',
      url: `/api/personal-dashboards/${dashboardId}`,
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.widgets).to.have.length(1);
      expect(body.widgets[0]).to.include({ id: widgetId, position: 0, visible: true });
    });
    cy.screenshot('personal-dashboard-widget-real-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-widget-real-mobile', { capture: 'viewport' });
  });
});
