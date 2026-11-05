/**
 * Browser-contract coverage for the dashboard shell. Run this only against the disposable E2E service: request
 * payloads are intercepted intentionally, so this is not evidence for financial execution correctness (that lives in
 * SemanticAnalyticsExecutionServiceIT). The intercept keeps UI scenarios deterministic without changing test data.
 */
describe('Recommended dashboards V2', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const catalog = {
    schemaVersion: 3,
    semanticModel: 'FINTRACK_FINANCIAL',
    catalogVersion: '1.3',
    supportedRelativePeriods: ['THIS_MONTH'],
    defaultLimit: 100,
    maxLimit: 500,
    authenticatedOwnerRequired: true,
    datasets: [
      {
        dataset: 'TRANSACTION_LEDGER',
        groupings: ['TRANSACTION_DAY', 'TRANSACTION_WEEK', 'TRANSACTION_MONTH', 'TRANSACTION_YEAR'],
      },
    ],
  };

  const responseFor = (definition: { groupBy: string[]; projections: Array<{ alias: string; function: string; field: string }> }) => {
    const temporal = definition.groupBy.find(
      field => field.startsWith('TRANSACTION_') || field.startsWith('TRANSFER_') || field === 'OBSERVATION_MONTH',
    );
    const comparison = definition.groupBy.find(field => field === 'CATEGORY_ROOT' || field === 'TAG_ID' || field === 'ACCOUNT_ID');
    const dimensions = definition.groupBy.map(field => {
      if (field === temporal) return { field, value: field.endsWith('MONTH') ? '2026-01' : '2026-01-15', metadata: null };
      if (field === 'CATEGORY_ROOT') {
        return {
          field,
          value: '100',
          metadata: { stableId: '100', label: 'Food', color: '#4477aa', active: true, synthetic: false, path: ['Food'] },
        };
      }
      if (field === 'TAG_ID') {
        return {
          field,
          value: '200',
          metadata: { stableId: '200', label: 'Essentials', color: '#44aa77', active: true, synthetic: false, path: [] },
        };
      }
      if (field === 'ACCOUNT_ID') {
        return {
          field,
          value: '7',
          metadata: { stableId: '7', label: 'Daily account', color: null, active: true, synthetic: false, path: [] },
        };
      }
      return { field, value: comparison ? 'Comparison' : 'Total', metadata: null };
    });
    const values = definition.projections.map((projection, index) => ({
      alias: projection.alias,
      value: index + 1 === 1 ? 1250 : 250,
      unit: projection.function === 'COUNT' ? 'COUNT' : projection.field === 'CREDIT_UTILIZATION_PERCENT' ? 'PERCENT' : 'MONEY',
      availability: 'COMPLETE',
    }));
    return {
      schemaVersion: 3,
      semanticModel: 'FINTRACK_FINANCIAL',
      effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
      executedAt: '2026-01-31T00:00:00Z',
      columns: values.map(value => ({ id: value.alias, dimension: false, unit: value.unit, currencyFaceted: value.unit === 'MONEY' })),
      rows: [{ dimensions, currencyCode: values.some(value => value.unit === 'MONEY') ? 'MXN' : null, values }],
    };
  };

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('GET', '**/api/analytics/semantic/catalog', catalog).as('catalog');
    cy.intercept('GET', '**/api/financial-accounts/selectable', [
      { id: 7, name: 'Daily account', accountType: 'DEBIT', currency: 'MXN' },
    ]).as('accounts');
    cy.intercept('POST', '**/api/analytics/semantic/queries/execute', request => {
      if (request.body.definition.groupBy?.[0] === 'TRANSACTION_WEEK') request.alias = 'weeklyDashboardWidget';
      request.reply(responseFor(request.body.definition));
    }).as('executeDashboardWidget');
  });

  it('switches among exactly three recommended views and presents semantic widgets through the shared result layer', () => {
    cy.visit('/analytics/dashboards/financial-overview');
    cy.contains('Resumen financiero').should('be.visible');
    cy.contains('a', 'Análisis de movimientos').click();
    cy.contains('Análisis de movimientos').should('be.visible');
    cy.get('[data-cy="recommendedWidget-transfer-activity"]').should('be.visible');
    cy.contains('a', 'Cuentas y patrimonio registrado').click();
    cy.contains('Cuentas y patrimonio registrado').should('be.visible');
    cy.get('[data-cy="recommendedWidget-position-by-account"]').should('be.visible');
  });

  it('changes only chart presentation when a legend series is hidden', () => {
    cy.visit('/analytics/dashboards/movement-analysis');
    cy.get('[data-cy="recommendedWidget-category-expense-trend"] [data-cy="analyticsInteractiveLegend"]')
      .find('[aria-pressed="true"]')
      .first()
      .click();
    cy.get('[data-cy="recommendedWidget-category-expense-trend"] [data-cy="analyticsInteractiveLegend"] [aria-pressed="false"]').should(
      'have.length',
      1,
    );
    cy.get('[data-cy="recommendedWidget-category-expense-trend"] [data-cy="analyticsResultDetailsToggle"]')
      .should('have.attr', 'aria-expanded', 'false')
      .click();
    cy.get('[data-cy="recommendedWidget-category-expense-trend"] [data-cy="analyticsResultTable"] tbody tr').should('have.length', 1);
  });

  it('uses a widget-local interval only for the effective temporal request and restores the saved default after reload', () => {
    cy.visit('/analytics/dashboards/financial-overview');
    cy.contains('Trend by').should('not.exist');
    cy.get('[data-cy="recommendedWidgetInterval-economic-trend"]').should('have.value', 'TRANSACTION_MONTH').select('TRANSACTION_WEEK');
    cy.wait('@weeklyDashboardWidget').then(({ request }) => {
      expect(request.body.definition.groupBy).to.deep.equal(['TRANSACTION_WEEK']);
      expect(request.body.definition.orderBy).to.deep.equal([{ field: 'TRANSACTION_WEEK', projectionAlias: null, direction: 'ASC' }]);
    });
    cy.reload();
    cy.get('[data-cy="recommendedWidgetInterval-economic-trend"]').should('have.value', 'TRANSACTION_MONTH');
  });
});
