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
    cy.get('[data-cy="analyticsProjection-movimientos"]').should('be.visible');
    cy.get('[data-cy="analyticsDatasetCatalog"]').should('not.exist');
    cy.get('@execute.all').should('have.length', 0);

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsResults"]').should('be.visible');

    cy.get('[data-cy="analyticsContextTimeMode"]').select('ABSOLUTE_RANGE');
    cy.get('[data-cy="analyticsPreviewStale"]').should('be.visible');
  });

  it('preserves the complete canonical definition from visual to written query and back', () => {
    useExample('expenses-by-category');
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');

    cy.get('[data-cy="analyticsModeSql"]').click();
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'SUM(TRANSACTION_AMOUNT) AS gastos');
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'TRANSFER_LINK_STATUS = UNLINKED');

    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsConditionsToggle"]').click();
    cy.contains('Internal transfer relationship').should('be.visible');
  });

  it('runs a query containing economic income and expense projections together', () => {
    useExample('income-and-expenses-monthly');
    cy.get('[data-cy="analyticsProjection-ingresos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsResults"]').should('be.visible');
  });

  it('keeps all governed sources in the compact information selector with primary and comparison breakdown controls', () => {
    cy.get('[data-cy="analyticsCreateScratch"]').click();
    cy.get('[data-cy="analyticsInformationSource"]').find('option').should('have.length.at.least', 6);
    cy.get('[data-cy="analyticsDatasetCatalog"]').should('not.exist');
    cy.get('[data-cy="analyticsInformationSource"]').select('TRANSACTION_LEDGER');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').select('ACCOUNT_INSTITUTION');
  });

  it('renders gaps for missing weekly observations and preserves the complete effective context', () => {
    cy.intercept('POST', '/api/analytics/semantic/queries/execute', {
      schemaVersion: 1,
      semanticModel: 'FINTRACK_FINANCIAL',
      effectiveContext: { from: '2025-12-01', through: '2026-02-28', accountIds: [], currencyCodes: ['MXN'] },
      executedAt: '2026-02-28T12:00:00Z',
      columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
      rows: [
        {
          dimensions: [{ field: 'TRANSACTION_WEEK', value: '2025-W49', metadata: null }],
          currencyCode: 'MXN',
          values: [{ alias: 'gastos', value: 2001.26, unit: 'MONEY', availability: 'COMPLETE' }],
        },
        {
          dimensions: [{ field: 'TRANSACTION_WEEK', value: '2025-W51', metadata: null }],
          currencyCode: 'MXN',
          values: [{ alias: 'gastos', value: 0, unit: 'MONEY', availability: 'ZERO' }],
        },
      ],
    }).as('weeklyExpenses');

    useExample('expenses-by-category');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TRANSACTION_WEEK');
    cy.wait('@format').its('response.statusCode').should('eq', 200);
    cy.wait('@parse').its('response.statusCode').should('eq', 200);
    cy.contains('button', /^Line$/).click();
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@weeklyExpenses').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="analyticsCurrencyFacet-MXN-MONEY"]').within(() => {
      cy.contains('$2,001.26 MXN').should('be.visible');
      cy.get('[data-cy="analyticsSemanticChart"] svg').should('be.visible');
      cy.get('[data-cy="analyticsSemanticChart"] circle.recharts-line-dot').should('have.length', 2);
      cy.get('[data-cy="analyticsTemporalChartNote"]').should(
        'contain.text',
        'Zeroes with the corresponding note are valid periods without registered movements',
      );
      cy.screenshot('analytics-weekly-economic-expenses-context-gaps');
    });
  });
});
