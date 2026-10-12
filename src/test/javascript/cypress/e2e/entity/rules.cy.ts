describe('Rules landing navigation e2e test', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  beforeEach(() => {
    cy.login(username, password);
  });

  it('opens both existing rule managers from the unified Rules landing and returns through product navigation', () => {
    cy.intercept('GET', '/api/description-normalization-rules+(?*|)', { statusCode: 200, body: [] }).as('descriptionRulesRequest');
    cy.intercept('GET', '/api/transaction-rules+(?*|)', { statusCode: 200, body: [] }).as('classificationRulesRequest');

    cy.visit('/');
    cy.clickOnEntityMenuItem('rules');
    cy.url().should('match', /\/rules$/);
    cy.get('[data-cy="rulesHeading"]').should('be.visible');
    cy.get('[data-cy="rulesDescriptionManagerLink"]').click();
    cy.wait('@descriptionRulesRequest');
    cy.url().should('match', /\/description-normalization-rule$/);

    cy.clickOnEntityMenuItem('rules');
    cy.get('[data-cy="rulesClassificationManagerLink"]').click();
    cy.wait('@classificationRulesRequest');
    cy.url().should('match', /\/transaction-rule$/);

    cy.visit('/description-normalization-rule');
    cy.wait('@descriptionRulesRequest');
    cy.url().should('match', /\/description-normalization-rule$/);
    cy.visit('/transaction-rule');
    cy.wait('@classificationRulesRequest');
    cy.url().should('match', /\/transaction-rule$/);
  });
});
