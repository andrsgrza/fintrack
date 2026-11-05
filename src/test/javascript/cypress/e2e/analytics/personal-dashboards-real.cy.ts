/**
 * Real browser -> REST -> Spring -> disposable PostgreSQL acceptance for the personal-dashboard
 * boundary. This deliberately does not stub dashboard, catalog, account, or semantic-query APIs.
 */
describe('Personal dashboards V2-3 real acceptance', () => {
  type Reference = { id: number; name: string };
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const date = new Date().toISOString().slice(0, 10);
  let account: Reference;

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Personal dashboard E2E ${suffix}`,
        institutionName: 'Personal dashboard E2E institution',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 250,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      account = body;
    });
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: date,
          description: 'Personal dashboard real expense',
          amount: 77,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: account.id },
        },
      }),
    )
      .its('status')
      .should('eq', 201);
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('POST', '/api/personal-dashboards/from-template').as('copyOfficialTemplate');
    cy.intercept('GET', '/api/personal-dashboards/*').as('loadPersonalDashboard');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('executePersonalWidget');
  });

  it('creates a copied official dashboard and executes its stored definition through the real semantic API', () => {
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards');
    cy.get('[data-cy="recommendedDashboardGallery"] article').should('have.length', 3);
    cy.get('[data-cy="recommendedDashboardGallery"] article')
      .first()
      .contains('button', /Use as a starting point|Usar como base/)
      .click();
    cy.get('[data-cy="personalDashboardName"]').clear();
    cy.get('[data-cy="personalDashboardName"]').type(`Real copied overview ${suffix}`);
    cy.get('[data-cy="personalDashboardSubmit"]').click();
    cy.wait('@copyOfficialTemplate').its('response.statusCode').should('eq', 201);
    cy.location('pathname').should('match', /^\/analytics\/dashboards\/personal\/\d+$/);
    cy.wait('@loadPersonalDashboard').its('response.statusCode').should('eq', 200);
    cy.wait('@executePersonalWidget').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="personalDashboardWidgets"] article').should('have.length.at.least', 1);
    cy.contains(/temporary for this view|temporales para esta vista/).should('be.visible');
    cy.reload();
    cy.wait('@loadPersonalDashboard').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="personalDashboardWidgets"] article').should('have.length.at.least', 1);
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(document.documentElement.clientWidth));
    cy.screenshot('personal-dashboard-real-copied-overview-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('personal-dashboard-real-copied-overview-mobile', { capture: 'viewport' });
  });
});
