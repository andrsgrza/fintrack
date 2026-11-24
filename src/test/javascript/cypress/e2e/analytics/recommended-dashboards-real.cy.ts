/**
 * Real browser -> REST -> Spring -> disposable PostgreSQL acceptance.  Unlike the companion
 * dashboard-shell spec, this file never intercepts semantic results: all values below originate
 * from the deterministic records created through the product API.
 */
describe('Recommended dashboards V2 real financial acceptance', () => {
  type Reference = { id: number; name: string };
  type DashboardFixture = {
    debit: Reference;
    credit: Reference;
    transferDestination: Reference;
    categories: Reference[];
    tags: Reference[];
    hierarchy: { account: Reference; root: Reference; child: Reference; grandchild: Reference };
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  // Dashboard context resolves dates in the local product timezone; UTC serialization can otherwise seed tomorrow
  // while the browser selects today near midnight.
  const now = new Date();
  const date = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
  const openingDate = '2026-01-01';
  const fixture: Partial<DashboardFixture> = {};
  const categoryColors = [
    '#1f77b4',
    '#ff7f0e',
    '#2ca02c',
    '#d62728',
    '#9467bd',
    '#8c564b',
    '#e377c2',
    '#7f7f7f',
    '#bcbd22',
    '#17becf',
    '#0b4f6c',
    '#8b4513',
    '#4f6d7a',
    '#7a5c61',
    '#596869',
    '#5d8233',
    '#c84b31',
    '#4d3c77',
    '#005f73',
    '#9b2226',
    '#386641',
    '#9d4edd',
  ];

  const createAccount = (name: string, accountType: 'DEBIT' | 'CREDIT_CARD', initialBalance: number, initialBalanceDate = openingDate) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name,
          institutionName: `Dashboard E2E institution ${accountType}`,
          accountType,
          currency: 'MXN',
          initialBalance,
          initialBalanceDate,
          active: true,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createConfiguredCreditAccount = (name: string) =>
    cy
      .authenticatedRequest<{ financialAccount: Reference }>({
        method: 'POST',
        url: '/api/financial-accounts/configured',
        body: {
          financialAccount: {
            name,
            institutionName: 'Dashboard E2E credit institution',
            accountType: 'CREDIT_CARD',
            currency: 'MXN',
            initialBalance: 250,
            initialBalanceDate: openingDate,
            active: true,
          },
          creditAccountDetails: { creditLimit: 1000, statementDay: 15, paymentDueDay: 5, annualInterestRate: 42 },
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body.financialAccount;
      });

  const createCategory = (index: number) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/categories',
        body: {
          name: `Dashboard category ${String(index + 1).padStart(2, '0')} ${suffix}`,
          description: 'Disposable dashboard legend fixture',
          categoryType: 'EXPENSE',
          color: categoryColors[index],
          active: true,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createHierarchyCategory = (name: string, parentId?: number) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/categories',
        body: {
          name,
          description: 'Disposable category drill-down fixture',
          categoryType: 'EXPENSE',
          color: '#225588',
          active: true,
          ...(parentId == null ? {} : { parentCategory: { id: parentId } }),
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createTag = (name: string, color: string) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/tags',
        body: { name, description: 'Disposable dashboard tag fixture', color, active: true },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createTransaction = ({
    accountId,
    amount,
    flow,
    categoryId,
    tagIds = [],
    transactionDate = date,
  }: {
    accountId: number;
    amount: number;
    flow: 'IN' | 'OUT';
    categoryId?: number;
    tagIds?: number[];
    transactionDate?: string;
  }) =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate,
          description: `Dashboard E2E ${flow} ${amount}`,
          amount,
          flow,
          origin: 'MANUAL',
          account: { id: accountId },
          ...(categoryId == null ? {} : { category: { id: categoryId } }),
          ...(tagIds.length ? { tags: tagIds.map(id => ({ id })) } : {}),
        },
      })
      .its('status')
      .should('eq', 201);

  const chooseAccounts = (...names: string[]) => {
    cy.get('[data-cy="analyticsAccountPicker"] summary').click();
    names.forEach(name => {
      cy.get('[data-cy="analyticsAccountPicker"]').contains('label', name).find('input').check();
    });
  };

  const setRange = (from: string, through: string) => {
    cy.get('[data-cy="analyticsContextTimeMode"]').select('ABSOLUTE_RANGE');
    cy.get('[data-cy="analyticsContextFrom"]').clear();
    cy.get('[data-cy="analyticsContextFrom"]').type(from);
    cy.get('[data-cy="analyticsContextThrough"]').clear();
    cy.get('[data-cy="analyticsContextThrough"]').type(through);
  };

  const openDetails = (widget: string) =>
    cy.get(widget).find('[data-cy="analyticsResultDetailsToggle"]').should('have.attr', 'aria-expanded', 'false').click();

  const closeDetails = (widget: string) =>
    cy.get(widget).find('[data-cy="analyticsResultDetailsToggle"]').should('have.attr', 'aria-expanded', 'true').click();

  /** A fixed global navbar is repeated by Chromium while Cypress stitches a full-page image. Hide only that chrome
   * during the capture, then restore it immediately, so the dashboard itself remains readable evidence. */
  const captureDashboardPage = (name: string) => {
    const setOptionalChromeVisibility = (selector: string, visibility: 'hidden' | 'visible') =>
      cy.document().then(document => Cypress.$(document).find(selector).css('visibility', visibility));

    setOptionalChromeVisibility('[data-cy="navbar"]', 'hidden');
    // The development ribbon is intentionally absent from the isolated production application.
    setOptionalChromeVisibility('.ribbon', 'hidden');
    cy.screenshot(name, { capture: 'fullPage' });
    setOptionalChromeVisibility('[data-cy="navbar"]', 'visible');
    setOptionalChromeVisibility('.ribbon', 'visible');
  };

  before(() => {
    cy.login(username, password);
    createAccount(`Dashboard cash ${suffix}`, 'DEBIT', 100).then(account => {
      fixture.debit = account;
    });
    createConfiguredCreditAccount(`Dashboard card ${suffix}`).then(account => {
      fixture.credit = account;
    });
    createAccount(`Dashboard transfer destination ${suffix}`, 'DEBIT', 0).then(account => {
      fixture.transferDestination = account;
    });
    fixture.categories = [];
    categoryColors.forEach((_, index) => {
      createCategory(index).then(category => fixture.categories?.push(category));
    });
    fixture.tags = [];
    createTag(`Dashboard tag violet ${suffix}`, '#8e44ad').then(tag => fixture.tags?.push(tag));
    createTag(`Dashboard tag teal ${suffix}`, '#00897b').then(tag => fixture.tags?.push(tag));
    createAccount(`Dashboard category hierarchy ${suffix}`, 'DEBIT', 0).then(account => {
      createHierarchyCategory(`Dashboard hierarchy root ${suffix}`).then(root => {
        createHierarchyCategory(`Dashboard hierarchy child ${suffix}`, root.id).then(child => {
          createHierarchyCategory(`Dashboard hierarchy grandchild ${suffix}`, child.id).then(grandchild => {
            fixture.hierarchy = { account, root, child, grandchild };
          });
        });
      });
    });
    cy.then(() => {
      const debit = fixture.debit;
      const categories = fixture.categories;
      const tags = fixture.tags;
      const hierarchy = fixture.hierarchy;
      if (!debit || !categories || !tags || !fixture.transferDestination || !hierarchy)
        throw new Error('Dashboard fixture was not created.');
      categories.forEach((category, index) => {
        createTransaction({
          accountId: debit.id,
          amount: index + 10,
          flow: 'OUT',
          categoryId: category.id,
          tagIds: index < 12 ? [tags[0].id] : index === 12 ? [tags[0].id, tags[1].id] : [tags[1].id],
        });
      });
      createTransaction({ accountId: debit.id, amount: 50, flow: 'OUT', transactionDate: '2026-02-10' });
      createTransaction({ accountId: debit.id, amount: 30, flow: 'IN', transactionDate: '2026-03-10' });
      createTransaction({ accountId: debit.id, amount: 5, flow: 'OUT' });
      createTransaction({ accountId: debit.id, amount: 1000, flow: 'IN' });
      createTransaction({ accountId: hierarchy.account.id, amount: 11, flow: 'OUT', categoryId: hierarchy.root.id });
      createTransaction({ accountId: hierarchy.account.id, amount: 17, flow: 'OUT', categoryId: hierarchy.child.id });
      createTransaction({ accountId: hierarchy.account.id, amount: 23, flow: 'OUT', categoryId: hierarchy.grandchild.id });
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/internal-transfers/commands/create',
        body: {
          sourceAccountId: debit.id,
          destinationAccountId: fixture.transferDestination.id,
          amount: 999,
          transactionDate: date,
          sourceDescription: 'Dashboard linked transfer source',
          destinationDescription: 'Dashboard linked transfer destination',
        },
      })
        .its('status')
        .should('eq', 201);
    });
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('POST', '/api/analytics/semantic/queries/execute').as('dashboardExecute');
    cy.intercept('GET', '/api/analytics/semantic/catalog').as('dashboardCatalog');
  });

  it('renders the overview from real financial results and excludes the linked transfer from economic flow', () => {
    const debit = fixture.debit;
    if (!debit) throw new Error('Dashboard fixture debit account is missing.');
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards/financial-overview');
    cy.wait('@dashboardCatalog').its('response.statusCode').should('eq', 200);
    chooseAccounts(debit.name);
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(1440));
    openDetails('[data-cy="recommendedWidget-economic-income"]');
    cy.get('[data-cy="recommendedWidget-economic-income"] [data-cy="analyticsResultTable"]').should('contain.text', '1,000.00');
    openDetails('[data-cy="recommendedWidget-economic-expenses"]');
    cy.get('[data-cy="recommendedWidget-economic-expenses"] [data-cy="analyticsResultTable"]').should('contain.text', '456.00');
    openDetails('[data-cy="recommendedWidget-net-economic-flow"]');
    cy.get('[data-cy="recommendedWidget-net-economic-flow"] [data-cy="analyticsResultTable"]').should('contain.text', '544.00');
    closeDetails('[data-cy="recommendedWidget-economic-income"]');
    closeDetails('[data-cy="recommendedWidget-economic-expenses"]');
    closeDetails('[data-cy="recommendedWidget-net-economic-flow"]');
    cy.get('@dashboardExecute.all').should('have.length.at.least', 14);
    cy.screenshot('dashboards-v21-overview-real-desktop-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-overview-real-desktop');
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('dashboards-v21-overview-real-mobile-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-overview-real-mobile');
  });

  it('renders all 22 real category series in the shared legend without changing its financial table or query', () => {
    const debit = fixture.debit;
    const categories = fixture.categories;
    const tags = fixture.tags;
    if (!debit || !categories || !tags) throw new Error('Dashboard fixture is missing.');
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards/movement-analysis');
    cy.wait('@dashboardCatalog').its('response.statusCode').should('eq', 200);
    chooseAccounts(debit.name);
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(1440));
    const categoryWidget = '[data-cy="recommendedWidget-category-expense-trend"]';
    // The 22 configured categories plus one genuine uncategorized expense are separate financial groups.
    openDetails(categoryWidget);
    cy.get(`${categoryWidget} [data-cy="analyticsResultTable"] tbody tr`).should('have.length', 23);
    cy.get(`${categoryWidget} [data-cy="analyticsDetailedResultTable"]`).within(() => {
      cy.contains('th', /Mes|Month/).should('be.visible');
      cy.contains('th', /Categoría principal|Root category/).should('be.visible');
    });
    cy.get('@dashboardExecute.all').then(requests => {
      const executionsBeforePivot = requests.length;
      cy.get(`${categoryWidget} [data-cy="analyticsTableModePivot"]`).click();
      cy.get(`${categoryWidget} [data-cy="analyticsPivotResultTable"]`).within(() => {
        cy.contains('th', /Mes|Month/).should('be.visible');
        cy.contains('th', categories[0].name).should('be.visible');
      });
      cy.get('@dashboardExecute.all').should('have.length', executionsBeforePivot);
    });
    cy.get(`${categoryWidget} [data-cy="analyticsTableModeDetailed"]`).click();
    cy.get(`${categoryWidget} [data-cy="analyticsDetailedResultTable"] tbody tr`).should('have.length', 23);
    cy.get(`${categoryWidget} [data-cy="analyticsInteractiveLegend"]`).within(() => {
      cy.contains('Series (23)').should('be.visible');
      cy.get('.overflow-auto').should('have.css', 'max-height', '176px');
      cy.get('[aria-pressed="true"]').should('have.length', 23);
      cy.contains('button', categories[0].name).find('span').should('have.css', 'color', 'rgb(31, 119, 180)');
      cy.get('[data-cy="analyticsLegendSearch"]').type('Category 22');
      cy.get('[aria-pressed="true"]').should('have.length', 1);
      cy.get('[data-cy="analyticsLegendSearch"]').clear();
      cy.get('[aria-pressed="true"]').first().click();
      cy.get('[aria-pressed="false"]').should('have.length', 1);
      cy.get('[data-cy="analyticsLegendHideAll"]').click();
      cy.get('[aria-pressed="false"]').should('have.length', 23);
      cy.get('[data-cy="analyticsLegendShowAll"]').click();
      cy.get('[aria-pressed="true"]').should('have.length', 23);
      cy.get('[aria-label^="Mostrar sólo"]').first().click();
      cy.get('[aria-pressed="true"]').should('have.length', 1);
    });
    cy.get(`${categoryWidget} [data-cy="analyticsResultTable"] tbody tr`).should('have.length', 23);
    closeDetails(categoryWidget);
    cy.get(`${categoryWidget} [data-cy="analyticsLegendShowAll"]`).click();
    cy.get(`${categoryWidget} .recharts-line-dot`).first().trigger('mouseover', { force: true });
    cy.get(`${categoryWidget} .recharts-tooltip-wrapper`).should('be.visible').and('contain.text', 'MXN');
    cy.get('[data-cy="recommendedWidget-tag-comparison"]')
      .contains('button', tags[0].name)
      .find('span')
      .should('have.css', 'color', 'rgb(142, 68, 173)');
    cy.get('[data-cy="recommendedWidget-tag-comparison"]')
      .invoke('text')
      .should('match', /Tag groups overlap|Tags can overlap|Las etiquetas pueden/i);
    cy.screenshot('dashboards-v21-movements-real-desktop-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-movements-real-desktop');
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('dashboards-v21-movements-real-mobile-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-movements-real-mobile');
  });

  it('drills a real category hierarchy without invalid ordering, duplication, or losing the original query on back', () => {
    const hierarchy = fixture.hierarchy;
    if (!hierarchy) throw new Error('Dashboard hierarchy fixture is missing.');
    let branchRequest = 0;
    cy.intercept('POST', '/api/analytics/semantic/queries/execute', request => {
      const definition = request.body.definition;
      const rootQuery =
        definition.groupBy.length === 1 &&
        definition.groupBy[0] === 'CATEGORY_ROOT' &&
        definition.orderBy.length === 1 &&
        definition.orderBy[0].field === 'CATEGORY_ROOT';
      if (rootQuery) request.alias = 'categoryHierarchyRoot';
      if (definition.groupBy.length === 1 && definition.groupBy[0] === 'CATEGORY_BRANCH_CHILD') {
        request.alias = `categoryHierarchyBranch${branchRequest++}`;
      }
    });

    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards/movement-analysis');
    cy.wait('@dashboardCatalog').its('response.statusCode').should('eq', 200);
    cy.wait('@categoryHierarchyRoot');
    chooseAccounts(hierarchy.account.name);

    let originalContext: Record<string, unknown> | undefined;
    cy.wait('@categoryHierarchyRoot').then(({ request }) => {
      originalContext = request.body.context;
      expect(originalContext?.accountIds).to.deep.equal([hierarchy.account.id]);
    });

    const widget = '[data-cy="recommendedWidget-category-hierarchy"]';
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).should('be.visible');
    cy.get(`${widget} [data-cy="analyticsResultDetailsToggle"]`).should('not.exist');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.contains('tr', hierarchy.root.name)
        .should('contain.text', '51.00')
        .contains('button', /Ver subcategorías|View subcategories/)
        .click();
    });
    cy.wait('@categoryHierarchyBranch0').then(({ request }) => {
      expect(request.body.context).to.deep.equal(originalContext);
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_BRANCH_CHILD']);
      expect(request.body.definition.orderBy).to.deep.equal([{ field: 'CATEGORY_BRANCH_CHILD', projectionAlias: null, direction: 'ASC' }]);
      expect(
        request.body.definition.predicates.filter(
          (predicate: { field: string; operator: string }) => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF',
        ),
      ).to.deep.equal([{ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: [String(hierarchy.root.id)] }]);
    });
    cy.get(widget)
      .contains(new RegExp(`(?:Explorando|Exploring): ${hierarchy.root.name}`))
      .should('be.visible');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.get('tbody tr').should('have.length', 2);
      cy.contains('tr', '11.00').should('contain.text', '11.00');
      cy.contains('tr', hierarchy.child.name)
        .should('contain.text', '40.00')
        .contains('button', /Ver subcategorías|View subcategories/)
        .click();
    });

    cy.wait('@categoryHierarchyBranch1').then(({ request }) => {
      expect(request.body.context).to.deep.equal(originalContext);
      expect(
        request.body.definition.predicates.filter(
          (predicate: { field: string; operator: string }) => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF',
        ),
      ).to.deep.equal([{ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: [String(hierarchy.child.id)] }]);
    });
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.get('tbody tr').should('have.length', 2);
      cy.contains('tr', '17.00').should('contain.text', '17.00');
      cy.contains('tr', hierarchy.grandchild.name)
        .should('contain.text', '23.00')
        .contains('button', /Ver subcategorías|View subcategories/)
        .click();
    });

    cy.wait('@categoryHierarchyBranch2').then(({ request }) => {
      expect(request.body.context).to.deep.equal(originalContext);
      expect(
        request.body.definition.predicates.filter(
          (predicate: { field: string; operator: string }) => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF',
        ),
      ).to.deep.equal([{ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: [String(hierarchy.grandchild.id)] }]);
    });
    cy.get(widget)
      .contains(new RegExp(`(?:Explorando|Exploring): ${hierarchy.root.name} › ${hierarchy.child.name} › ${hierarchy.grandchild.name}`))
      .should('be.visible');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.contains('tr', '23.00').should('contain.text', '23.00');
      cy.get('tbody tr').should('have.length', 1);
    });
    cy.get(widget).screenshot('dashboards-v21-category-drill-real');

    cy.get(widget)
      .contains('button', /Volver|Back/)
      .click();
    cy.get(widget)
      .contains(new RegExp(`(?:Explorando|Exploring): ${hierarchy.root.name} › ${hierarchy.child.name}`))
      .should('be.visible');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.contains('tr', '17.00').should('contain.text', '17.00');
      cy.contains('tr', hierarchy.grandchild.name).should('contain.text', '23.00');
    });
    cy.get(widget)
      .contains('button', /Volver|Back/)
      .click();
    cy.get(widget)
      .contains(new RegExp(`(?:Explorando|Exploring): ${hierarchy.root.name}`))
      .should('be.visible');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).within(() => {
      cy.contains('tr', '11.00').should('contain.text', '11.00');
      cy.contains('tr', hierarchy.child.name).should('contain.text', '40.00');
    });
    cy.get(widget)
      .contains('button', /Volver|Back/)
      .click();
    cy.get(widget)
      .contains(/Explorando:|Exploring:/)
      .should('not.exist');
    cy.get(`${widget} [data-cy="analyticsResultTable"]`).contains('tr', hierarchy.root.name).should('contain.text', '51.00');
  });

  it('renders real positions and credit facts without inventing historical credit limits', () => {
    const debit = fixture.debit;
    const credit = fixture.credit;
    if (!debit || !credit) throw new Error('Dashboard fixture accounts are missing.');
    cy.viewport(1440, 960);
    cy.visit('/analytics/dashboards/accounts-and-position');
    cy.wait('@dashboardCatalog').its('response.statusCode').should('eq', 200);
    chooseAccounts(debit.name, credit.name);
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(1440));
    cy.get('[data-cy="recommendedWidget-position-by-account"] .recharts-reference-line-line').should('exist');
    cy.get('[data-cy="recommendedWidget-current-credit-limit"] [data-cy="analyticsSemanticChart"]').should('be.visible');
    cy.get('[data-cy="recommendedWidget-current-credit-limit"] [data-cy="analyticsResultTable"]').should('not.exist');
    cy.get('[data-cy="recommendedWidget-current-credit-limit"] [data-cy="analyticsResultDetailsToggle"]').should(
      'contain.text',
      'Ver tabla de resultados',
    );
    openDetails('[data-cy="recommendedWidget-position-by-account"]');
    cy.get('[data-cy="recommendedWidget-position-by-account"] [data-cy="analyticsResultTable"] tbody tr').should('have.length', 1);
    openDetails('[data-cy="recommendedWidget-credit-debt-history"]');
    cy.get('[data-cy="recommendedWidget-credit-debt-history"] [data-cy="analyticsResultTable"]').should('contain.text', '250.00');
    openDetails('[data-cy="recommendedWidget-current-credit-limit"]');
    cy.get('[data-cy="recommendedWidget-current-credit-limit"] [data-cy="analyticsResultTable"]').should('contain.text', '1,000.00');
    openDetails('[data-cy="recommendedWidget-current-available-credit"]');
    cy.get('[data-cy="recommendedWidget-current-available-credit"] [data-cy="analyticsResultTable"]').should('contain.text', '750.00');
    openDetails('[data-cy="recommendedWidget-current-credit-utilization"]');
    cy.get('[data-cy="recommendedWidget-current-credit-utilization"] [data-cy="analyticsResultTable"]').should('contain.text', '25');
    closeDetails('[data-cy="recommendedWidget-position-by-account"]');
    closeDetails('[data-cy="recommendedWidget-credit-debt-history"]');
    closeDetails('[data-cy="recommendedWidget-current-credit-limit"]');
    closeDetails('[data-cy="recommendedWidget-current-available-credit"]');
    closeDetails('[data-cy="recommendedWidget-current-credit-utilization"]');
    cy.get('@dashboardExecute.all').should('have.length.at.least', 10);
    cy.screenshot('dashboards-v21-accounts-real-desktop-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-accounts-real-desktop');
    setRange(openingDate, '2026-03-31');
    openDetails('[data-cy="recommendedWidget-position-by-account"]');
    cy.get('[data-cy="recommendedWidget-position-by-account"] [data-cy="analyticsResultTable"] tbody tr')
      .should('have.length', 1)
      .and('contain.text', '80.00');
    closeDetails('[data-cy="recommendedWidget-position-by-account"]');
    openDetails('[data-cy="recommendedWidget-position-history"]');
    cy.get('[data-cy="recommendedWidget-position-history"] [data-cy="analyticsResultTable"] tbody tr')
      .should('have.length', 3)
      .and('contain.text', '100.00')
      .and('contain.text', '50.00')
      .and('contain.text', '80.00');
    closeDetails('[data-cy="recommendedWidget-position-history"]');
    captureDashboardPage('dashboards-v21-accounts-real-historical-desktop');
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('dashboards-v21-accounts-real-mobile-viewport', { capture: 'viewport' });
    captureDashboardPage('dashboards-v21-accounts-real-mobile');
  });
});
