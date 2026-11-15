/** Real browser -> REST -> Spring -> disposable PostgreSQL acceptance for DASHBOARDS-V2-6 management. */
describe('Personal dashboard management V2-6 real acceptance', () => {
  type Dashboard = {
    id: number;
    name: string;
    description: string | null;
    version: number;
    defaultContext: object;
    widgets: Array<{ id: number; title: string; definition: object; position: number; width: string; visible: boolean }>;
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

  let source: Dashboard;
  let duplicate: Dashboard;

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<Dashboard>({
      method: 'POST',
      url: '/api/personal-dashboards',
      body: {
        name: `Management source ${runId}`,
        description: 'The independent source dashboard',
        defaultContext: context,
        clientRequestId: `management-source-${runId}`,
      },
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(201);
      source = body;
    });
    cy.then(() =>
      cy
        .authenticatedRequest<Dashboard>({
          method: 'POST',
          url: `/api/personal-dashboards/${source.id}/widgets`,
          body: {
            expectedVersion: source.version,
            title: 'Economic expense total',
            description: 'A copied canonical widget',
            definition: expenseTotal,
            visualizationType: 'KPI',
            visualizationOptions: {},
            width: 'COMPACT',
            visible: true,
            clientRequestId: `management-widget-${runId}`,
          },
        } as Cypress.RequestOptions)
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          source = body;
        }),
    );
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('GET', '/api/personal-dashboards/*').as('loadDashboard');
    cy.intercept('PATCH', '/api/personal-dashboards/*/metadata').as('updateMetadata');
    cy.intercept('POST', '/api/personal-dashboards/*/duplicate').as('duplicateDashboard');
    cy.intercept('PUT', '/api/personal-dashboards/default').as('setDefault');
    cy.intercept('DELETE', '/api/personal-dashboards/*').as('deleteDashboard');
    cy.intercept('GET', '/api/personal-dashboards/default').as('loadDefault');
  });

  it('renames, duplicates, defaults, and deletes a dashboard through the real owner-scoped API', () => {
    const renamed = `Management source renamed ${runId}`;
    const duplicateName = `Management duplicate ${runId}`;
    cy.viewport(1440, 960);
    cy.visit(`/analytics/dashboards/personal/${source.id}`);
    cy.wait('@loadDashboard').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardEditMetadata"]').click();
    cy.get('[data-cy="personalDashboardManageName"]').clear();
    cy.get('[data-cy="personalDashboardManageName"]').type(renamed);
    cy.get('[data-cy="personalDashboardManageDescription"]').clear();
    cy.get('[data-cy="personalDashboardManageDescription"]').type('Renamed through real browser acceptance');
    cy.get('[data-cy="personalDashboardManageSubmit"]').click();
    cy.wait('@updateMetadata').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ expectedVersion: source.version, name: renamed });
      source = response?.body as Dashboard;
    });
    cy.contains('h2', renamed).should('be.visible');
    cy.reload();
    cy.wait('@loadDashboard');
    cy.contains('h2', renamed).should('be.visible');
    cy.screenshot('personal-dashboard-management-renamed-desktop', { capture: 'viewport' });

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardDuplicate"]').click();
    cy.get('[data-cy="personalDashboardManageName"]').clear();
    cy.get('[data-cy="personalDashboardManageName"]').type(duplicateName);
    cy.get('[data-cy="personalDashboardManageSubmit"]').click();
    cy.wait('@duplicateDashboard').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body).to.include({ expectedVersion: source.version, name: duplicateName });
      duplicate = response?.body as Dashboard;
    });
    cy.then(() => duplicate.id).then(duplicateId => {
      cy.location('pathname').should('eq', `/analytics/dashboards/personal/${duplicateId}`);
    });
    cy.wait('@loadDashboard');

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardEditMetadata"]').click();
    cy.get('[data-cy="personalDashboardManageDescription"]').clear();
    cy.get('[data-cy="personalDashboardManageDescription"]').type('Changed only on the duplicate');
    cy.get('[data-cy="personalDashboardManageSubmit"]').click();
    cy.wait('@updateMetadata').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      duplicate = response?.body as Dashboard;
    });

    cy.authenticatedRequest<Dashboard>({ method: 'GET', url: `/api/personal-dashboards/${source.id}` } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        expect(body).to.include({ id: source.id, name: renamed, description: 'Renamed through real browser acceptance' });
        expect(body.widgets[0]).to.include({ id: source.widgets[0].id, title: 'Economic expense total' });
        expect(duplicate.id).to.not.equal(source.id);
        expect(duplicate.widgets[0].id).to.not.equal(body.widgets[0].id);
        expect(duplicate.defaultContext).to.deep.equal(body.defaultContext);
        expect(duplicate.widgets[0]).to.include({
          title: body.widgets[0].title,
          position: body.widgets[0].position,
          width: body.widgets[0].width,
          visible: body.widgets[0].visible,
        });
        expect(duplicate.widgets[0].definition).to.deep.equal(body.widgets[0].definition);
      },
    );

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardSetDefault"]').click();
    cy.wait('@setDefault').its('response.statusCode').should('eq', 200);
    cy.visit('/');
    cy.wait('@loadDefault').its('response.statusCode').should('eq', 200);
    cy.then(() => duplicate.id).then(duplicateId => {
      cy.location('pathname').should('eq', `/analytics/dashboards/personal/${duplicateId}`);
    });
    cy.wait('@loadDashboard');

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardDelete"]').click();
    cy.contains(/will delete|eliminarás/i).should('contain.text', duplicateName);
    cy.contains('button', /Cancel|Cancelar/).click();
    cy.then(() => duplicate.id).then(duplicateId =>
      cy
        .authenticatedRequest<Dashboard>({ method: 'GET', url: `/api/personal-dashboards/${duplicateId}` } as Cypress.RequestOptions)
        .its('status')
        .should('eq', 200),
    );

    cy.get('[data-cy="personalDashboardManage"]').click();
    cy.get('[data-cy="personalDashboardDelete"]').click();
    cy.get('[data-cy="personalDashboardManageSubmit"]').click();
    cy.wait('@deleteDashboard').its('response.statusCode').should('eq', 204);
    cy.location('pathname').should('eq', '/analytics/dashboards');
    cy.authenticatedRequest<{ dashboardId: number | null }>({
      method: 'GET',
      url: '/api/personal-dashboards/default',
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.dashboardId).to.equal(null);
    });

    cy.visit('/');
    cy.wait('@loadDefault').its('response.statusCode').should('eq', 200);
    cy.location('pathname').should('eq', '/analytics/dashboards/financial-overview');
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-management-fallback-mobile', { capture: 'viewport' });
  });
});
