/**
 * BUDGET-6C-B browser -> REST -> Spring -> disposable PostgreSQL coverage.
 * Request interception observes the real API only; it never supplies a response.
 */
describe('Budget financial time zone real acceptance', { retries: 0 }, () => {
  type Profile = { timeZone: string | null; configurationRequired: boolean; effectiveChanges: unknown[] };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  it('suggests the browser zone, requires confirmation, and retains the persisted authoritative value after reload', () => {
    cy.login(username, password);
    cy.intercept('GET', '/api/account/budget-time-zone').as('readBudgetTimeZone');
    cy.intercept('PUT', '/api/account/budget-time-zone').as('saveBudgetTimeZone');

    cy.visit('/account/settings');
    cy.wait('@readBudgetTimeZone').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect((response?.body as Profile).configurationRequired).to.equal(true);
      expect((response?.body as Profile).timeZone).to.equal(null);
    });

    cy.get('[data-cy="budgetTimeZoneSettings"]').should('be.visible');
    cy.get('[data-cy="budgetTimeZoneSuggestion"]').should('be.visible');
    cy.get('[data-cy="budgetTimeZoneInput"]').invoke('val').should('be.a', 'string').and('not.be.empty');
    cy.get('@saveBudgetTimeZone.all').should('have.length', 0);

    cy.get('[data-cy="saveBudgetTimeZone"]').click();
    cy.wait('@saveBudgetTimeZone').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.timeZone).to.be.a('string');
      expect(request.body.timeZone).to.have.length.greaterThan(0);
      expect((response?.body as Profile).timeZone).to.equal(request.body.timeZone);
    });
    cy.contains(/Zona horaria guardada|Time zone saved/).should('be.visible');

    cy.reload();
    cy.wait('@readBudgetTimeZone').then(({ response }) => {
      expect((response?.body as Profile).configurationRequired).to.equal(false);
      cy.get('[data-cy="budgetTimeZoneInput"]').should('have.value', (response?.body as Profile).timeZone);
    });
    cy.get('[data-cy="budgetTimeZoneSuggestion"]').should('not.exist');
    cy.get('@saveBudgetTimeZone.all').should('have.length', 1);

    cy.authenticatedRequest<Profile>({ method: 'GET', url: '/api/account/budget-time-zone' }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.configurationRequired).to.equal(false);
      expect(body.timeZone).to.be.a('string');
      expect(body.timeZone).to.have.length.greaterThan(0);
    });
  });
});
