/**
 * Real browser -> REST -> Spring -> disposable PostgreSQL proof that both Metric Explorer authoring
 * modes operate on precisely one SemanticQueryDefinition. No semantic result endpoint is intercepted.
 */
describe('Metric Explorer Visual and Written Query real acceptance', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const now = new Date();
  const transactionDate = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join(
    '-',
  );

  let accountId: number;
  let categoryId: number;

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Explorer parity account ${suffix}`,
        institutionName: 'Explorer parity institution',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: transactionDate,
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
        name: `Explorer parity category ${suffix}`,
        description: 'Disposable visual and written query parity fixture',
        categoryType: 'EXPENSE',
        color: '#225588',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      categoryId = body.id;
    });
    cy.then(() =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate,
            description: 'Disposable visual and written query parity expense',
            amount: 125.5,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: accountId },
            category: { id: categoryId },
          },
        })
        .its('status')
        .should('eq', 201),
    );
  });

  it('round-trips one visual AST through Consulta escrita and executes that unchanged AST', () => {
    let visualDefinition: Record<string, unknown>;
    cy.intercept('POST', '/api/analytics/semantic/queries/format').as('formatDefinition');
    cy.intercept('POST', '/api/analytics/semantic/queries/parse').as('parseDefinition');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('executeDefinition');

    cy.login(username, password);
    cy.viewport(1440, 960);
    cy.visit('/analytics/explorer');
    cy.contains('button', /Gastos por categoría|Expenses by category/).click();
    // Choosing an example validates its initial AST through the same format/parse boundary. Wait for that work before
    // changing the grouping, otherwise the initial normalization can legitimately win a race with this test input.
    cy.wait('@formatDefinition');
    cy.wait('@parseDefinition');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('CATEGORY_ROOT');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'CATEGORY_ROOT');
    cy.get('[data-cy="analyticsContextTimeMode"]').select('THIS_YEAR');
    cy.get('[data-cy="analyticsModeSql"]').click();

    cy.wait('@formatDefinition').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.not.have.property('context');
      expect(request.body).to.include({ dataset: 'TRANSACTION_LEDGER' });
      visualDefinition = request.body;
    });
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'SELECT SUM(TRANSACTION_AMOUNT) AS gastos');

    // A harmless whitespace edit marks the text draft as requiring validation. Switching back therefore exercises
    // the real parser, rather than merely hiding an already-valid editor.
    cy.get('[data-cy="analyticsSqlText"]').type(' ');
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.wait('@parseDefinition').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.text).to.match(/^SELECT SUM\(TRANSACTION_AMOUNT\) AS gastos/);
      expect(response?.body.definition).to.deep.equal(visualDefinition);
    });
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'CATEGORY_ROOT');

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@executeDefinition').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.definition).to.deep.equal(visualDefinition);
      expect(request.body.context).to.not.deep.equal(visualDefinition);
    });
    cy.get('[data-cy="analyticsResultTable"]').should('contain.text', '125.5');
    cy.get('[data-cy="analyticsResultTable"]').scrollIntoView();
    cy.screenshot('metric-explorer-visual-written-parity-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('metric-explorer-visual-written-parity-mobile', { capture: 'viewport' });
  });
});
