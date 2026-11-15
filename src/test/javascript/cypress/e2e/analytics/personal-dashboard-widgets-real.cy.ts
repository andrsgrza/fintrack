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
  let dashboardVersion: number;
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
    cy.authenticatedRequest<{ id: number; version: number }>({
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
      dashboardVersion = body.version;
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
    const savedWidget = '[data-cy^="personalDashboardWidget-"]';
    cy.get(`${savedWidget} [data-cy="analyticsResultDetailsToggle"]`).click();
    cy.get(`${savedWidget} [data-cy="analyticsDetailedResultTable"]`).within(() => {
      cy.contains('th', /Categoría principal|Root category/).should('be.visible');
      cy.contains('th', /Mes|Month/).should('be.visible');
    });
    cy.get('@executeWidget.all').then(requests => {
      const executionsBeforePivot = requests.length;
      cy.get(`${savedWidget} [data-cy="analyticsTableModePivot"]`).click();
      cy.get(`${savedWidget} [data-cy="analyticsPivotResultTable"]`).should('be.visible');
      cy.get('@executeWidget.all').should('have.length', executionsBeforePivot);
    });
    cy.get(`${savedWidget} [data-cy="analyticsPivotResultTable"]`).scrollIntoView();
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(1440));
    cy.screenshot('personal-dashboard-category-month-pivot-desktop', { capture: 'viewport' });
    cy.viewport('iphone-6');
    cy.get(`${savedWidget} [data-cy="analyticsPivotResultTable"]`).scrollIntoView();
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-category-month-pivot-mobile', { capture: 'viewport' });
    cy.viewport(1440, 960);
    cy.get(`${savedWidget} [data-cy="analyticsTableModeDetailed"]`).click();
    cy.get('button[data-cy^="personalDashboardWidgetMenu-"]').then(button => {
      widgetId = Number(button.attr('data-cy')?.replace('personalDashboardWidgetMenu-', ''));
      expect(widgetId).to.be.greaterThan(0);
      cy.wrap(button).click();
      cy.get(`[data-cy="personalDashboardWidgetMenuEdit-${widgetId}"]`).click();
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
    cy.get('button[data-cy^="personalDashboardWidgetMenu-"]').then(button => {
      widgetId = Number(button.attr('data-cy')?.replace('personalDashboardWidgetMenu-', ''));
      expect(widgetId).to.be.greaterThan(0);
      cy.wrap(button).click();
      cy.get(`[data-cy="personalDashboardWidgetMenuEdit-${widgetId}"]`).click();
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

  it('presents a real category-account result in both authored orientations without re-executing for the table mode', () => {
    const secondAccountName = `Second grouping account ${suffix}-${dashboardRun}`;
    const categoryAccountDefinition = () => ({
      schemaVersion: 1,
      semanticModel: 'FINTRACK_FINANCIAL',
      dataset: 'TRANSACTION_LEDGER',
      timeBinding: 'TRANSACTION_DATE',
      projections: [
        { alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] },
        { alias: 'conteo', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] },
      ],
      predicates: [
        { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
        { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
        { field: 'CATEGORY_ID', operator: 'EQUALS', values: [String(categoryId)] },
      ],
      groupBy: ['CATEGORY_ROOT', 'ACCOUNT_ID'],
      orderBy: [
        { field: 'CATEGORY_ROOT', projectionAlias: null, direction: 'ASC' },
        { field: 'ACCOUNT_ID', projectionAlias: null, direction: 'ASC' },
      ],
      limit: 100,
    });
    let secondAccountId: number;

    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: secondAccountName,
        institutionName: 'Second grouping institution',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      secondAccountId = body.id;
    });
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: '2026-11-02',
          description: 'Second isolated category-account fixture',
          amount: 50.25,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: secondAccountId },
          category: { id: categoryId },
        },
      }),
    )
      .its('status')
      .should('eq', 201);
    cy.then(() =>
      cy
        .authenticatedRequest<{ version: number; name: string; description: string | null }>({
          method: 'GET',
          url: `/api/personal-dashboards/${dashboardId}`,
        })
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          return cy.authenticatedRequest<{ version: number }>({
            method: 'PUT',
            url: `/api/personal-dashboards/${dashboardId}`,
            body: {
              expectedVersion: body.version,
              name: body.name,
              description: body.description,
              defaultContext: { ...context, accountIds: [accountId, secondAccountId] },
            },
          });
        })
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          dashboardVersion = body.version;
        }),
    );
    const addTableWidget = (title: string, definition: object) =>
      cy
        .authenticatedRequest<{ version: number; widgets: Array<{ id: number; title: string }> }>({
          method: 'POST',
          url: `/api/personal-dashboards/${dashboardId}/widgets`,
          body: {
            expectedVersion: dashboardVersion,
            title,
            definition,
            visualizationType: 'TABLE',
            visualizationOptions: {},
            width: 'FULL',
            visible: true,
            clientRequestId: `table-grouping-widget-${title.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}-${suffix}-${dashboardRun}`,
          },
        } as Cypress.RequestOptions)
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          dashboardVersion = body.version;
          const widget = body.widgets.find(candidate => candidate.title === title);
          expect(widget?.id).to.be.greaterThan(0);
          return Number(widget?.id);
        });

    cy.then(() => addTableWidget('Category then account', categoryAccountDefinition()));
    cy.then(() =>
      addTableWidget('Account then category', {
        ...categoryAccountDefinition(),
        groupBy: ['ACCOUNT_ID', 'CATEGORY_ROOT'],
        orderBy: [
          { field: 'ACCOUNT_ID', projectionAlias: null, direction: 'ASC' },
          { field: 'CATEGORY_ROOT', projectionAlias: null, direction: 'ASC' },
        ],
      }),
    );

    cy.viewport(1440, 960);
    cy.visit(`/analytics/dashboards/personal/${dashboardId}`);
    cy.wait('@loadDashboard');
    cy.contains('h2', 'Category then account').closest('article').as('categoryFirstWidget');
    cy.contains('h2', 'Account then category').closest('article').as('accountFirstWidget');
    cy.get('@categoryFirstWidget')
      .find('[data-cy="analyticsDetailedResultTable"]')
      .within(() => {
        cy.get('thead th').eq(0).should('contain.text', 'Categoría principal');
        cy.get('thead th').eq(1).should('contain.text', 'Cuenta');
        cy.contains('td', secondAccountName).should('be.visible');
        cy.contains('td', '125.50').should('be.visible');
        cy.contains('td', '50.25').should('be.visible');
      });
    cy.get('@executeWidget.all').then(requests => {
      const executionsBeforePivot = requests.length;
      cy.get('@categoryFirstWidget').find('[data-cy="analyticsTableModePivot"]').click();
      cy.get('@categoryFirstWidget')
        .find('[data-cy="analyticsPivotResultTable"]')
        .within(() => {
          cy.get('thead th').first().should('contain.text', 'Categoría principal');
          cy.contains('th', secondAccountName).should('be.visible');
          cy.contains('td', '125.50').should('be.visible');
          cy.contains('td', '50.25').should('be.visible');
        });
      cy.get('@executeWidget.all').should('have.length', executionsBeforePivot);
    });
    cy.get('@accountFirstWidget').find('[data-cy="analyticsTableModePivot"]').click();
    cy.get('@accountFirstWidget')
      .find('[data-cy="analyticsPivotResultTable"]')
      .within(() => {
        cy.get('thead th').first().should('contain.text', 'Cuenta');
        cy.contains('th', /Categoría principal|Widget grouping category/).should('be.visible');
        cy.contains('td', '125.50').should('be.visible');
        cy.contains('td', '50.25').should('be.visible');
      });
  });
});
