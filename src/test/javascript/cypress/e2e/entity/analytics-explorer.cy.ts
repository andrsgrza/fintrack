describe('Analytics Explorer', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('GET', '/api/analytics/semantic/catalog').as('catalog');
    cy.intercept('POST', '/api/analytics/semantic/queries/format').as('format');
    cy.intercept('POST', '/api/analytics/semantic/queries/parse').as('parse');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('execute');
    cy.visit('/analytics/explorer');
    cy.wait('@catalog').its('response.statusCode').should('eq', 200);
  });

  const useExample = (id: string) => {
    cy.get(`[data-cy="analyticsExample-${id}"]`).click();
    cy.wait('@format').its('response.statusCode').should('eq', 200);
    cy.wait('@parse').its('response.statusCode').should('eq', 200);
  };

  it('constructs and runs transaction count by institution only after an explicit action', () => {
    useExample('transactions-by-institution');
    cy.get('[data-cy="analyticsProjection-transaction_count"]').should('be.visible');
    cy.get('@execute.all').should('have.length', 0);

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsResults"]').should('be.visible');

    cy.get('[data-cy="analyticsContextTimeMode"]').select('ABSOLUTE_RANGE');
    cy.get('[data-cy="analyticsPreviewStale"]').should('be.visible');
  });

  it('preserves the complete canonical definition from visual to SQL-like and back', () => {
    useExample('economic-expense-by-category');
    cy.get('[data-cy="analyticsProjection-average_expense"]').should('be.visible');

    cy.get('[data-cy="analyticsModeSql"]').click();
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'AVG(TRANSACTION_AMOUNT) AS average_expense');
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'TRANSFER_LINK_STATUS = UNLINKED');

    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsProjection-average_expense"]').should('be.visible');
    cy.get('[data-cy="analyticsCondition-TRANSFER_LINK_STATUS"]').should('be.visible');
  });

  it('runs a query containing count and average projections together', () => {
    useExample('count-and-average-expense');
    cy.get('[data-cy="analyticsProjection-expense_count"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-average_expense"]').should('be.visible');

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsResults"]').should('be.visible');
  });
});
