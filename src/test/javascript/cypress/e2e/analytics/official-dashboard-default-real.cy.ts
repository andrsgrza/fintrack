/** Real browser -> REST -> Spring -> disposable PostgreSQL acceptance for official and personal default selection. */
describe('Official dashboard defaults real acceptance', () => {
  type Dashboard = { id: number; name: string; version: number };
  type DefaultDashboard = {
    dashboardId: number | null;
    officialTemplateId: 'financial-overview' | 'movement-analysis' | 'accounts-and-position' | null;
    version: number | null;
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const runId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const context = {
    schemaVersion: 1,
    time: { mode: 'RELATIVE', relativePeriod: 'THIS_MONTH', from: null, through: null },
    accountIds: [],
    currencyCodes: [],
  };

  it('persists official → personal → official selection, never copies officials, and falls back after a selected personal dashboard is deleted', () => {
    let personal: Dashboard;

    cy.login(username, password);
    cy.authenticatedRequest<DefaultDashboard>({ method: 'GET', url: '/api/personal-dashboards/default' } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        // A fresh isolated user starts on the official Financial Overview; normalize deterministically if a prior
        // Cypress spec used this login in the same disposable application.
        const expectedVersion = body.version;
        cy.authenticatedRequest<DefaultDashboard>({
          method: 'PUT',
          url: '/api/personal-dashboards/default',
          body: { expectedVersion, dashboardId: null, officialTemplateId: 'financial-overview' },
        } as Cypress.RequestOptions).then(({ body: reset, status: resetStatus }) => {
          expect(resetStatus).to.equal(200);
          expect(reset).to.include({ dashboardId: null, officialTemplateId: 'financial-overview' });
        });
      },
    );

    cy.intercept('PUT', '/api/personal-dashboards/default').as('setDefault');
    cy.intercept('GET', '/api/personal-dashboards/default').as('loadDefault');
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards');
    cy.wait('@loadDefault').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="recommendedDashboardSetDefault-movement-analysis"]').click();
    cy.wait('@setDefault').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ dashboardId: null, officialTemplateId: 'movement-analysis' });
      expect(response?.body).to.include({ dashboardId: null, officialTemplateId: 'movement-analysis' });
    });
    cy.get('[data-cy="recommendedDashboardDefault-movement-analysis"]')
      .invoke('text')
      .should('match', /Predeterminado|Default/);
    cy.screenshot('official-dashboard-default-movement-desktop', { capture: 'viewport' });

    cy.visit('/');
    cy.wait('@loadDefault').its('response.statusCode').should('eq', 200);
    cy.location('pathname').should('eq', '/analytics/dashboards/movement-analysis');
    cy.get('[data-cy="personalDashboardManage"]').should('not.exist');

    cy.authenticatedRequest<Dashboard>({
      method: 'POST',
      url: '/api/personal-dashboards',
      body: {
        name: `Official default personal ${runId}`,
        description: 'Created only to verify an owned default selection',
        defaultContext: context,
        clientRequestId: `official-default-personal-${runId}`,
      },
    } as Cypress.RequestOptions).then(({ body, status }) => {
      expect(status).to.equal(201);
      personal = body;
    });
    cy.then(() => personal).then(dashboard =>
      cy
        .authenticatedRequest<Dashboard>({
          method: 'POST',
          url: `/api/personal-dashboards/${dashboard.id}/duplicate`,
          body: {
            expectedVersion: dashboard.version,
            name: `Official default duplicate ${runId}`,
            description: null,
            clientRequestId: `official-default-duplicate-${runId}`,
          },
        } as Cypress.RequestOptions)
        .then(({ status }) => expect(status).to.equal(201)),
    );
    cy.authenticatedRequest<DefaultDashboard>({ method: 'GET', url: '/api/personal-dashboards/default' } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        expect(body).to.include({ dashboardId: null, officialTemplateId: 'movement-analysis' });
      },
    );

    cy.visit('/analytics/dashboards');
    cy.wait('@loadDefault');
    cy.then(() => personal.id).then(personalId => cy.get(`[data-cy="personalDashboardSetDefault-${personalId}"]`).click());
    cy.wait('@setDefault').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ dashboardId: personal.id, officialTemplateId: null });
      expect(response?.body).to.include({ dashboardId: personal.id, officialTemplateId: null });
    });
    cy.then(() => personal.id).then(personalId =>
      cy
        .get(`[data-cy="personalDashboardDefault-${personalId}"]`)
        .invoke('text')
        .should('match', /Predeterminado|Default/),
    );
    cy.then(() => personal.id).then(personalId =>
      cy.get(`[data-cy="personalDashboardDefault-${personalId}"]`).closest('.card').screenshot('official-dashboard-default-personal-card'),
    );
    cy.screenshot('official-dashboard-default-personal-desktop', { capture: 'viewport' });

    cy.visit('/');
    cy.wait('@loadDefault');
    cy.then(() => personal.id).then(personalId => cy.location('pathname').should('eq', `/analytics/dashboards/personal/${personalId}`));

    cy.visit('/analytics/dashboards/accounts-and-position');
    cy.get('[data-cy="recommendedDashboardSetDefault"]').click();
    cy.wait('@setDefault').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ dashboardId: null, officialTemplateId: 'accounts-and-position' });
    });
    cy.get('[data-cy="recommendedDashboardDefault"]')
      .invoke('text')
      .should('match', /Predeterminado|Default/);
    cy.visit('/');
    cy.wait('@loadDefault');
    cy.location('pathname').should('eq', '/analytics/dashboards/accounts-and-position');

    cy.visit('/analytics/dashboards');
    cy.wait('@loadDefault');
    cy.then(() => personal.id).then(personalId => cy.get(`[data-cy="personalDashboardSetDefault-${personalId}"]`).click());
    cy.wait('@setDefault').its('response.statusCode').should('eq', 200);
    cy.then(() => personal).then(dashboard =>
      cy
        .authenticatedRequest<void>({
          method: 'DELETE',
          url: `/api/personal-dashboards/${dashboard.id}`,
          qs: { expectedVersion: dashboard.version },
        } as Cypress.RequestOptions)
        .its('status')
        .should('eq', 204),
    );
    cy.authenticatedRequest<DefaultDashboard>({ method: 'GET', url: '/api/personal-dashboards/default' } as Cypress.RequestOptions).then(
      ({ body, status }) => {
        expect(status).to.equal(200);
        expect(body).to.include({ dashboardId: null, officialTemplateId: 'financial-overview' });
      },
    );
    cy.visit('/');
    cy.wait('@loadDefault');
    cy.location('pathname').should('eq', '/analytics/dashboards/financial-overview');
  });
});
