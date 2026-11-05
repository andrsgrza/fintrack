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
  let accountId: number;
  let categoryId: number;

  beforeEach(() => {
    cy.login(username, password);
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Widget grouping account ${suffix}-${dashboardRun}`,
        institutionName: 'Widget grouping institution',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      accountId = body.id;
    });
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/categories',
      body: {
        name: `Widget grouping category ${suffix}-${dashboardRun}`,
        description: 'Isolated root-category grouping fixture',
        categoryType: 'EXPENSE',
        color: '#225588',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      categoryId = body.id;
    });
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: '2026-11-01',
          description: 'Isolated category-month line fixture',
          amount: 125.5,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: accountId },
          category: { id: categoryId },
        },
      }),
    )
      .its('status')
      .should('eq', 201);
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

  it('executes, saves, reloads, and edits a category-first monthly line without changing its canonical grouping order', () => {
    let widgetId: number;
    cy.viewport(1440, 960);
    cy.visit(`/analytics/explorer?dashboardId=${dashboardId}`);
    cy.contains('button', /Gastos por categoría|Expenses by category/).click();
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('CATEGORY_ROOT');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-1"]').select('TRANSACTION_MONTH');
    cy.contains('button', /^(Línea|Line)$/)
      .should('not.be.disabled')
      .click();
    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@executeWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_ROOT', 'TRANSACTION_MONTH']);
      // This example deliberately ranks the selected calculation; it is a valid canonical order even though it is
      // not a grouping-field order. The grouping sequence itself remains the axis/series presentation contract.
      expect(request.body.definition.orderBy).to.have.length(1);
      expect(request.body.definition.orderBy[0]).to.include({ field: null, projectionAlias: 'gastos' });
    });
    cy.get('[data-cy="analyticsSemanticChart"] svg').should('be.visible');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Real saved expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@saveWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition).not.to.have.property('context');
      expect(request.body).not.to.have.property('context');
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_ROOT', 'TRANSACTION_MONTH']);
      expect(request.body.visualizationType).to.equal('LINE');
    });
    cy.location('pathname').should('eq', `/analytics/dashboards/personal/${dashboardId}`);
    cy.wait('@loadDashboard');
    cy.wait('@executeWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.context.time.relativePeriod).to.equal('THIS_MONTH');
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_ROOT', 'TRANSACTION_MONTH']);
    });
    cy.get('[data-cy^="personalDashboardEditWidget-"]').then(button => {
      widgetId = Number(button.attr('data-cy')?.replace('personalDashboardEditWidget-', ''));
      expect(widgetId).to.be.greaterThan(0);
      cy.wrap(button).click();
    });
    cy.wait('@loadDashboard');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'CATEGORY_ROOT');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').should('have.value', 'TIME');
    cy.get('[data-cy="analyticsGroupingInterval-1"]').should('have.value', 'TRANSACTION_MONTH');
    cy.contains('button', /^(Línea|Line)$/).should('not.be.disabled');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Real updated expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@updateWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.url).to.include(`/widgets/${widgetId}`);
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_ROOT', 'TRANSACTION_MONTH']);
      expect(request.body.visualizationType).to.equal('LINE');
    });
    cy.reload();
    cy.wait('@loadDashboard');
    cy.authenticatedRequest<{ widgets: Array<{ id: number; position: number; width: string; visible: boolean }> }>({
      method: 'GET',
      url: `/api/personal-dashboards/${dashboardId}`,
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.widgets).to.have.length(1);
      expect(body.widgets[0]).to.include({ id: widgetId, position: 0, visible: true });
    });
    cy.then(() => {
      cy.contains('h2', 'Real updated expenses')
        .should('be.visible')
        .closest('article')
        .should('have.attr', 'data-cy', `personalDashboardWidget-${widgetId}`)
        .within(() => {
          cy.get('svg').should('exist');
        });
      cy.get(`[data-cy="personalDashboardWidget-${widgetId}"]`).scrollIntoView();
    });
    cy.screenshot('personal-dashboard-category-month-line-real-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-category-month-line-real-mobile', { capture: 'viewport' });
  });

  it('executes, saves, reloads, and opens a month-first category line without changing its canonical grouping order', () => {
    let widgetId: number;
    cy.viewport(1440, 960);
    cy.visit(`/analytics/explorer?dashboardId=${dashboardId}`);
    cy.contains('button', /Gastos por categoría|Expenses by category/).click();
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').select('TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').select('CATEGORY_ROOT');
    cy.contains('button', /^(Línea|Line)$/)
      .should('not.be.disabled')
      .click();
    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@executeWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition.groupBy).to.deep.equal(['TRANSACTION_MONTH', 'CATEGORY_ROOT']);
    });
    cy.get('[data-cy="analyticsSemanticChart"] svg').should('be.visible');
    cy.get('[data-cy="analyticsSaveVisualization"]').click();
    cy.get('[data-cy="widgetSaveTitle"]').clear();
    cy.get('[data-cy="widgetSaveTitle"]').type('Real month-first expenses');
    cy.get('[data-cy="widgetSaveSubmit"]').click();
    cy.wait('@saveWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition.groupBy).to.deep.equal(['TRANSACTION_MONTH', 'CATEGORY_ROOT']);
      expect(request.body.visualizationType).to.equal('LINE');
    });
    cy.location('pathname').should('eq', `/analytics/dashboards/personal/${dashboardId}`);
    cy.wait('@loadDashboard');
    cy.wait('@executeWidget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.context.time.relativePeriod).to.equal('THIS_MONTH');
      expect(request.body.definition.groupBy).to.deep.equal(['TRANSACTION_MONTH', 'CATEGORY_ROOT']);
    });
    cy.get('[data-cy^="personalDashboardEditWidget-"]').then(button => {
      widgetId = Number(button.attr('data-cy')?.replace('personalDashboardEditWidget-', ''));
      expect(widgetId).to.be.greaterThan(0);
      cy.wrap(button).click();
    });
    cy.wait('@loadDashboard');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').should('have.value', 'TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').should('have.value', 'CATEGORY_ROOT');
    cy.contains('button', /^(Línea|Line)$/).should('not.be.disabled');
    cy.authenticatedRequest<{ widgets: Array<{ id: number; position: number; visible: boolean }> }>({
      method: 'GET',
      url: `/api/personal-dashboards/${dashboardId}`,
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.widgets).to.have.length(1);
      expect(body.widgets[0]).to.include({ id: widgetId, position: 0, visible: true });
    });
  });
});
