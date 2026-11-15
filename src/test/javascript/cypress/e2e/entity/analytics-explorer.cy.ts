describe('Analytics Explorer', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  type Reference = { id: number; name: string };

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

  const createAccount = (name: string, currency = 'MXN', initialBalance = 0, initialBalanceDate = '2026-01-01') =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name,
          institutionName: `${name} institution`,
          accountType: 'DEBIT',
          currency,
          initialBalance,
          initialBalanceDate,
          active: true,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createCreditAccount = (name: string, initialBalance: number, creditLimit?: number) => {
    if (creditLimit == null) {
      return cy
        .authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/financial-accounts',
          body: {
            name,
            institutionName: `${name} institution`,
            accountType: 'CREDIT_CARD',
            currency: 'MXN',
            initialBalance,
            initialBalanceDate: '2026-02-01',
            active: true,
          },
        })
        .then(({ body, status }) => {
          expect(status).to.equal(201);
          return body;
        });
    }
    return cy
      .authenticatedRequest<{ financialAccount: Reference }>({
        method: 'POST',
        url: '/api/financial-accounts/configured',
        body: {
          financialAccount: {
            name,
            institutionName: `${name} institution`,
            accountType: 'CREDIT_CARD',
            currency: 'MXN',
            initialBalance,
            initialBalanceDate: '2026-02-01',
            active: true,
          },
          creditAccountDetails: { creditLimit, statementDay: 15, paymentDueDay: 5, annualInterestRate: 42 },
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body.financialAccount;
      });
  };

  const createCategory = (name: string, parentId?: number) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/categories',
        body: {
          name,
          description: 'Semantic analytics Cypress fixture',
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

  const createTag = (name: string) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/tags',
        body: { name, description: 'Semantic analytics Cypress fixture', color: '#995577', active: true },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createTransaction = ({
    accountId,
    amount,
    date,
    categoryId,
    tagIds = [],
    flow = 'OUT',
  }: {
    accountId: number;
    amount: number;
    date: string;
    categoryId?: number;
    tagIds?: number[];
    flow?: 'IN' | 'OUT';
  }) =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: date,
          description: `Analytics E2E ${date} ${amount}`,
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

  const setRange = (from: string, through: string) => {
    cy.get('[data-cy="analyticsContextTimeMode"]').select('ABSOLUTE_RANGE');
    cy.get('[data-cy="analyticsContextFrom"]').clear();
    cy.get('[data-cy="analyticsContextFrom"]').type(from);
    cy.get('[data-cy="analyticsContextThrough"]').clear();
    cy.get('[data-cy="analyticsContextThrough"]').type(through);
  };

  const selectAccounts = (...names: string[]) => {
    cy.get('[data-cy="analyticsAccountPicker"] summary').click();
    cy.wrap(names).each((name, index) => {
      cy.get('[data-cy="analyticsAccountPicker"]').contains('label', name).find('input').check();
      cy.get('[data-cy="analyticsAccountPicker"] input[type="checkbox"]:checked').should('have.length', index + 1);
    });
  };

  const prepareWrittenQuery = (dataset: string, text: string) => {
    cy.get('[data-cy="analyticsCreateScratch"]').click();
    cy.get('[data-cy="analyticsInformationSource"]').select(dataset);
    cy.get('[data-cy="analyticsModeSql"]').click();
    cy.wait('@format').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsSqlText"]').clear();
    cy.get('[data-cy="analyticsSqlText"]').type(text, { parseSpecialCharSequences: false });
    cy.get('[data-cy="analyticsApplySql"]').click();
    cy.wait('@parse').its('response.statusCode').should('eq', 200);
  };

  const replaceWrittenQuery = (text: string) => {
    cy.get('[data-cy="analyticsSqlText"]').clear();
    cy.get('[data-cy="analyticsSqlText"]').type(text, { parseSpecialCharSequences: false });
    cy.get('[data-cy="analyticsApplySql"]').click();
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
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', "TRANSFER_LINK_STATUS EQUALS 'UNLINKED'");

    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsConditionsToggle"]').click();
    cy.contains(/Internal transfer relationship|Relación con transferencia interna/).should('be.visible');
  });

  it('keeps category plus month ordered in the canonical query while enabling a temporal line presentation', () => {
    useExample('expenses-by-category');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('CATEGORY_ROOT');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-1"]').select('TRANSACTION_MONTH');
    cy.contains('button', /^(Line|Línea)$/).should('not.be.disabled');

    cy.get('[data-cy="analyticsModeSql"]').click();
    cy.wait('@format').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsSqlText"]').should('contain.value', 'GROUP BY CATEGORY_ROOT, TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'CATEGORY_ROOT');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').should('have.value', 'TIME');
    cy.get('[data-cy="analyticsGroupingInterval-1"]').should('have.value', 'TRANSACTION_MONTH');
  });

  it('runs a query containing economic income and expense projections together', () => {
    useExample('income-and-expenses-monthly');
    cy.get('[data-cy="analyticsProjection-ingresos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsResults"]').should('be.visible');
  });

  it('keeps all governed sources with canonical primary, interval, and optional secondary grouping controls', () => {
    cy.get('[data-cy="analyticsCreateScratch"]').click();
    cy.get('[data-cy="analyticsInformationSource"]').find('option').should('have.length.at.least', 6);
    cy.get('[data-cy="analyticsDatasetCatalog"]').should('not.exist');
    cy.get('[data-cy="analyticsInformationSource"]').select('TRANSACTION_LEDGER');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').select('TRANSACTION_MONTH');
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
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').select('TRANSACTION_WEEK');
    cy.contains('button', /^(Line|Línea)$/).click();
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@weeklyExpenses').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="analyticsCurrencyFacet-MXN-MONEY"]').within(() => {
      cy.get('[data-cy="analyticsSemanticChart"] svg').should('be.visible');
      cy.get('[data-cy="analyticsSemanticChart"] circle.recharts-line-dot').should('have.length', 2);
      cy.get('[data-cy="analyticsTemporalChartNote"]')
        .invoke('text')
        .should('match', /Zeroes with the corresponding note are valid periods|Los ceros.*movimientos registrados/);
      cy.screenshot('analytics-weekly-economic-expenses-context-gaps');
    });
    cy.get('[data-cy="analyticsResultTable"]')
      .invoke('text')
      .should('match', /2[,.]001[,.]26/)
      .and('match', /MXN/);
  });

  it('executes the financial shortcuts against PostgreSQL and excludes linked transfer legs from expenses', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const categoryName = `Analytics groceries ${suffix}`;
    const sourceAccountName = `Analytics source ${suffix}`;
    let sourceAccountId: number;
    let destinationAccountId: number;
    let categoryId: number;

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: sourceAccountName,
        institutionName: 'Analytics E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 1000,
        initialBalanceDate: '2026-02-01',
        active: true,
      },
    }).then(({ body }) => {
      sourceAccountId = body.id;
    });
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Analytics destination ${suffix}`,
        institutionName: 'Analytics E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 1000,
        initialBalanceDate: '2026-02-01',
        active: true,
      },
    }).then(({ body }) => {
      destinationAccountId = body.id;
    });
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/categories',
      body: { name: categoryName, description: 'Analytics E2E fixture', categoryType: 'EXPENSE', color: '#225588', active: true },
    }).then(({ body }) => {
      categoryId = body.id;
    });
    cy.then(() => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: '2026-02-10',
          description: 'Analytics economic expense',
          amount: 125.5,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: sourceAccountId },
          category: { id: categoryId },
          tags: [],
        },
      })
        .its('status')
        .should('eq', 201);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/internal-transfers/commands/create',
        body: {
          sourceAccountId,
          destinationAccountId,
          amount: 999,
          transactionDate: '2026-02-11',
          sourceDescription: 'Analytics linked transfer out',
          destinationDescription: 'Analytics linked transfer in',
        },
      })
        .its('status')
        .should('eq', 201);
    });

    cy.visit('/analytics/explorer');
    cy.wait('@catalog').its('response.statusCode').should('eq', 200);
    useExample('expenses-by-category');
    cy.get('[data-cy="analyticsContextTimeMode"]').select('ABSOLUTE_RANGE');
    cy.get('[data-cy="analyticsContextFrom"]').clear();
    cy.get('[data-cy="analyticsContextFrom"]').type('2026-02-01');
    cy.get('[data-cy="analyticsContextThrough"]').clear();
    cy.get('[data-cy="analyticsContextThrough"]').type('2026-02-28');
    cy.get('[data-cy="analyticsAccountPicker"] summary').click();
    cy.get('[data-cy="analyticsAccountPicker"]').contains('label', sourceAccountName).find('input').check();
    cy.contains('button', /(?:Add calculation|Añadir cálculo)/).click();
    cy.get('[data-cy="analyticsCalculationPicker"]')
      .contains('button', /^(Number of expenses|Número de gastos)$/)
      .click();

    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ request, response }) => {
      expect(request.body.definition.predicates).to.deep.include.members([
        { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
        { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
      ]);
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.rows).to.have.length(1);
      expect(response?.body.rows[0].values).to.deep.include.members([
        { alias: 'gastos', value: 125.5, unit: 'MONEY', availability: 'COMPLETE', derived: false },
        { alias: 'numero_gastos', value: 1, unit: 'COUNT', availability: 'COMPLETE', derived: false },
      ]);
      expect(JSON.stringify(response?.body.rows)).not.to.contain('999');
    });
    cy.get('[data-cy="analyticsResultTable"]').should('contain.text', categoryName).and('contain.text', '125.50');
    cy.screenshot('analytics-real-postgresql-desktop', { capture: 'fullPage' });
    cy.viewport('iphone-6');
    cy.screenshot('analytics-real-postgresql-mobile', { capture: 'fullPage' });
  });

  it('drills a real category hierarchy without duplicate amounts and returns through the breadcrumb', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const rootName = `Analytics root ${suffix}`;
    const childName = `Analytics child ${suffix}`;
    const grandchildName = `Analytics grandchild ${suffix}`;
    const accountName = `Analytics hierarchy account ${suffix}`;
    let account: Reference;
    let root: Reference;
    let child: Reference;
    let grandchild: Reference;

    createAccount(accountName).then(value => {
      account = value;
    });
    cy.then(() => createCategory(rootName)).then(value => {
      root = value;
    });
    cy.then(() => createCategory(childName, root.id)).then(value => {
      child = value;
    });
    cy.then(() => createCategory(grandchildName, child.id)).then(value => {
      grandchild = value;
    });
    cy.then(() => createTransaction({ accountId: account.id, amount: 10, date: '2026-02-02', categoryId: root.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 20, date: '2026-02-03', categoryId: child.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 30, date: '2026-02-04', categoryId: grandchild.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 40, date: '2026-02-05' }));

    cy.visit('/analytics/explorer');
    cy.wait('@catalog').its('response.statusCode').should('eq', 200);
    useExample('expenses-by-category');
    setRange('2026-02-01', '2026-02-28');
    selectAccounts(accountName);
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('CATEGORY_ROOT');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const amounts = response?.body.rows.map(row => Number(row.values[0].value)).sort((left, right) => left - right);
      expect(amounts).to.deep.equal([40, 60]);
    });
    cy.get('[data-cy="analyticsResultTable"]').should('contain.text', rootName).and('contain.text', '60.00');

    cy.contains('[data-cy="analyticsResultTable"] tr', rootName)
      .contains('button', /View subcategories|Ver subcategorías/)
      .click();
    cy.get('[data-cy="analyticsPreviewStale"]').should('be.visible');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ request, response }) => {
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_BRANCH_CHILD']);
      const descendant = request.body.definition.predicates.find(
        predicate => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF',
      );
      expect(descendant?.values).to.deep.equal([String(root.id)]);
      expect(response?.statusCode).to.equal(200);
      const rows = (response?.body.rows ?? []) as Array<{ values: Array<{ value: number }> }>;
      const drilledAmounts = rows.map(row => Number(row.values[0].value));
      expect(drilledAmounts.reduce((total, amount) => total + amount, 0)).to.equal(60);
      expect(drilledAmounts).to.include(50);
    });
    cy.get('[data-cy="analyticsResults"]')
      .invoke('text')
      .should('match', /Exploring:|Explorando:/)
      .and('contain', childName);
    cy.contains('[data-cy="analyticsResults"] button', /Back|Volver/).click();
    cy.get('[data-cy="analyticsPreviewStale"]').should('be.visible');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ request }) => {
      expect(request.body.definition.groupBy).to.deep.equal(['CATEGORY_ROOT']);
      expect(
        request.body.definition.predicates.some(predicate => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF'),
      ).to.equal(false);
    });
    cy.screenshot('analytics-category-hierarchy-real', { capture: 'fullPage' });
  });

  it('filters overlapping tags through the real semantic pipeline without ledger fanout', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const accountName = `Analytics tag account ${suffix}`;
    const tagAName = `Analytics tag A ${suffix}`;
    const tagBName = `Analytics tag B ${suffix}`;
    let account: Reference;
    let tagA: Reference;
    let tagB: Reference;

    createAccount(accountName).then(value => {
      account = value;
    });
    cy.then(() => createTag(tagAName)).then(value => {
      tagA = value;
    });
    cy.then(() => createTag(tagBName)).then(value => {
      tagB = value;
    });
    cy.then(() => createTransaction({ accountId: account.id, amount: 100, date: '2026-02-10', tagIds: [tagA.id, tagB.id] }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 50, date: '2026-02-11', tagIds: [tagA.id] }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 30, date: '2026-02-12', tagIds: [tagB.id] }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 20, date: '2026-02-13' }));

    cy.then(() => {
      const base = 'TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED';
      const begin = (query: string) => {
        cy.visit('/analytics/explorer');
        cy.wait('@catalog').its('response.statusCode').should('eq', 200);
        prepareWrittenQuery('TRANSACTION_LEDGER', query);
        setRange('2026-02-01', '2026-02-28');
        selectAccounts(accountName);
      };

      begin(
        `SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE WHERE ${base} GROUP BY TRANSACTION_MONTH, TAG_ID ORDER BY TRANSACTION_MONTH ASC LIMIT 100`,
      );
      cy.get('[data-cy="analyticsExecute"]').click();
      cy.wait('@execute').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        const rows = (response?.body.rows ?? []) as Array<{ values: Array<{ value: number }> }>;
        expect(rows.map(row => Number(row.values[0].value)).sort((left, right) => left - right)).to.deep.equal([130, 150]);
        const warnings = (response?.body.warnings ?? []) as Array<{ code: string }>;
        expect(warnings.map(warning => warning.code)).to.include('NON_ADDITIVE_OVERLAPPING_TAG_GROUPS');
      });
      cy.get('[data-cy="analyticsWarning-NON_ADDITIVE_OVERLAPPING_TAG_GROUPS"]').should('be.visible');
      cy.get('[data-cy="analyticsModeVisual"]').click();
      cy.contains('button', /Donut|Dona/).should('be.disabled');
      cy.contains('button', /Bars|Barras/).click();
      cy.get('#analytics-bar-style option[value="STACKED_BAR"]').should('be.disabled');
      cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'TIME');
      cy.get('[data-cy="analyticsGroupingInterval-0"]').should('have.value', 'TRANSACTION_MONTH');
      cy.get('[data-cy="analyticsComparisonBreakdown"]').should('have.value', 'TAG_ID');

      cy.get('[data-cy="analyticsModeSql"]').click();
      cy.wait('@format').its('response.statusCode').should('eq', 200);
      replaceWrittenQuery(
        `SELECT COUNT(TRANSACTION_ID) AS conteo FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE WHERE TAG_ID HAS_ANY ('${tagA.id}') LIMIT 100`,
      );
      cy.get('[data-cy="analyticsExecute"]').click();
      cy.wait('@execute').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.rows[0].values[0].value).to.equal(2);
      });

      replaceWrittenQuery(
        `SELECT COUNT(TRANSACTION_ID) AS conteo FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE WHERE TAG_ID HAS_ALL ('${tagA.id}', '${tagB.id}') LIMIT 100`,
      );
      cy.get('[data-cy="analyticsExecute"]').click();
      cy.wait('@execute').then(({ response }) => {
        expect(response?.body.rows[0].values[0].value).to.equal(1);
      });

      replaceWrittenQuery(
        'SELECT COUNT(TRANSACTION_ID) AS conteo FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE WHERE TAG_ID HAS_NONE LIMIT 100',
      );
      cy.get('[data-cy="analyticsExecute"]').click();
      cy.wait('@execute').then(({ response }) => {
        expect(response?.body.rows[0].values[0].value).to.equal(1);
      });
      cy.screenshot('analytics-tags-overlap-real', { capture: 'fullPage' });
    });
  });

  it('presents authoritative credit debt and availability states from real credit-card fixtures', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const overLimitName = `Analytics over-limit card ${suffix}`;
    const noDetailsName = `Analytics no-details card ${suffix}`;
    const debitOnlyName = `Analytics debit-only account ${suffix}`;
    const currentCredit =
      'SELECT SUM(CREDIT_DEBT) AS deuda, SUM(CREDIT_LIMIT) AS limite, SUM(CREDIT_AVAILABLE) AS disponible, ' +
      'SUM(CREDIT_UTILIZATION_PERCENT) AS utilizacion FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE ' +
      'GROUP BY ACCOUNT_ID ORDER BY ACCOUNT_ID ASC LIMIT 100';
    const historicalDebt =
      'SELECT SUM(CREDIT_DEBT) AS deuda FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE ' +
      'GROUP BY ACCOUNT_ID, OBSERVATION_MONTH ORDER BY OBSERVATION_MONTH ASC LIMIT 100';

    createCreditAccount(overLimitName, 1200, 1000);
    createCreditAccount(noDetailsName, 75);
    createAccount(debitOnlyName);

    const run = (query: string, accountName: string, from?: string, through?: string) => {
      cy.visit('/analytics/explorer');
      cy.wait('@catalog').its('response.statusCode').should('eq', 200);
      prepareWrittenQuery('CREDIT_ACCOUNT_SNAPSHOT', query);
      if (from && through) setRange(from, through);
      selectAccounts(accountName);
      cy.get('[data-cy="analyticsExecute"]').click();
    };

    run(currentCredit, overLimitName);
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const values = response?.body.rows[0].values as Array<{
        alias: string;
        value: number;
        unit: string;
        availability: string;
        derived: boolean;
      }>;
      expect(values).to.deep.include.members([
        { alias: 'deuda', value: 1200, unit: 'MONEY', availability: 'COMPLETE', derived: false },
        { alias: 'limite', value: 1000, unit: 'MONEY', availability: 'COMPLETE', derived: false },
        { alias: 'disponible', value: -200, unit: 'MONEY', availability: 'COMPLETE', derived: false },
        { alias: 'utilizacion', value: 120, unit: 'PERCENT', availability: 'COMPLETE', derived: false },
      ]);
      const warnings = (response?.body.warnings ?? []) as Array<{ code: string }>;
      expect(warnings.map(warning => warning.code)).to.include('CREDIT_DEBT_POINT_IN_TIME');
    });
    cy.get('[data-cy="analyticsResultTable"]')
      .should('contain.text', '1,200.00')
      .invoke('text')
      .should('match', /120(?:[,.]00)?\s*%/);

    run(currentCredit, noDetailsName);
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const values = response?.body.rows[0].values as Array<{ alias: string; value: number; availability: string }>;
      expect(values[0]).to.deep.include({ alias: 'deuda', value: 75, availability: 'COMPLETE' });
      expect(values.slice(1).map(value => value.availability)).to.deep.equal(['UNAVAILABLE', 'UNAVAILABLE', 'UNAVAILABLE']);
      expect(response?.body.availability).to.equal('PARTIAL');
    });
    cy.get('[data-cy="analyticsResultAvailability"]')
      .invoke('text')
      .should('match', /Partial|Parcial/);
    cy.get('[data-cy="analyticsWarning-CREDIT_DETAILS_UNAVAILABLE"]').should('be.visible');
    cy.get('[data-cy="analyticsResultTable"]')
      .invoke('text')
      .should('match', /Unavailable|No disponible/);

    run(historicalDebt, overLimitName, '2026-01-01', '2026-01-31');
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const value = response?.body.rows[0].values[0];
      expect(value).to.deep.include({ alias: 'deuda', value: null, availability: 'UNAVAILABLE', derived: false });
      expect(response?.body.availability).to.equal('UNAVAILABLE');
    });
    cy.get('[data-cy="analyticsResultAvailability"]')
      .invoke('text')
      .should('match', /Unavailable|No disponible/);

    run(currentCredit, debitOnlyName);
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.rows).to.deep.equal([]);
      expect(response?.body.availability).to.equal('NOT_APPLICABLE');
      const warnings = (response?.body.warnings ?? []) as Array<{ code: string }>;
      expect(warnings.map(warning => warning.code)).to.deep.equal(['NO_APPLICABLE_CREDIT_ACCOUNTS']);
    });
    cy.get('[data-cy="analyticsNoData"]')
      .invoke('text')
      .should('match', /does not apply|no aplica/i);
    cy.get('[data-cy="analyticsResultAvailability"]')
      .invoke('text')
      .should('match', /Not applicable|No aplica/);
    cy.get('[data-cy="analyticsWarning-NO_APPLICABLE_CREDIT_ACCOUNTS"]').should('be.visible');
    cy.screenshot('analytics-credit-availability-real', { capture: 'fullPage' });
  });

  it('distinguishes real empty-period zeroes from NO_DATA and exposes loading state', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const accountName = `Analytics empty-period account ${suffix}`;
    const temporal =
      'SELECT SUM(TRANSACTION_AMOUNT) AS total, COUNT(TRANSACTION_ID) AS conteo, AVG(TRANSACTION_AMOUNT) AS promedio ' +
      'FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE WHERE TRANSACTION_FLOW = OUT ' +
      'GROUP BY TRANSACTION_WEEK ORDER BY TRANSACTION_WEEK ASC LIMIT 100';
    const average =
      'SELECT AVG(TRANSACTION_AMOUNT) AS promedio FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE ' +
      'WHERE TRANSACTION_FLOW = OUT LIMIT 100';

    createAccount(accountName);
    cy.visit('/analytics/explorer');
    cy.wait('@catalog').its('response.statusCode').should('eq', 200);
    prepareWrittenQuery('TRANSACTION_LEDGER', temporal);
    setRange('2026-02-02', '2026-02-08');
    selectAccounts(accountName);
    cy.intercept('POST', '/api/analytics/semantic/queries/execute', request => {
      request.continue(response => response.setDelay(550));
    }).as('delayedRealExecute');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.contains(/Calculating…|Calculando…/).should('be.visible');
    cy.wait('@delayedRealExecute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const values = response?.body.rows[0].values as Array<{
        alias: string;
        value: number | null;
        unit: string;
        availability: string;
        derived: boolean;
      }>;
      expect(values).to.deep.include.members([
        { alias: 'total', value: 0, unit: 'MONEY', availability: 'ZERO', derived: true },
        { alias: 'conteo', value: 0, unit: 'COUNT', availability: 'ZERO', derived: true },
        { alias: 'promedio', value: null, unit: 'MONEY', availability: 'NO_DATA', derived: true },
      ]);
      expect(response?.body.availability).to.equal('PARTIAL');
    });
    cy.get('[data-cy="analyticsResultTable"]')
      .invoke('text')
      .should('match', /Sin movimientos registrados|No registered movements/)
      .and('match', /no hay promedio|no average/i);

    cy.get('[data-cy="analyticsModeSql"]').click();
    replaceWrittenQuery(average);
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@delayedRealExecute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.rows).to.deep.equal([]);
      expect(response?.body.availability).to.equal('NO_DATA');
    });
    cy.get('[data-cy="analyticsNoData"]').should('contain.text', 'no equivale a cero');
  });

  it('renders every eligible real chart type and keeps the table values authoritative', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const accountName = `Analytics chart account ${suffix}`;
    const foodName = `Analytics food ${suffix}`;
    const travelName = `Analytics travel ${suffix}`;
    const categoryByTemporal =
      'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE ' +
      'WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED GROUP BY CATEGORY_ROOT, TRANSACTION_MONTH ' +
      'ORDER BY CATEGORY_ROOT ASC, TRANSACTION_MONTH ASC LIMIT 100';
    const kpi =
      'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE ' +
      'WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED LIMIT 100';
    const donut =
      'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE ' +
      'WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED GROUP BY CATEGORY_ID ORDER BY gastos DESC LIMIT 100';
    let account: Reference;
    let food: Reference;
    let travel: Reference;

    createAccount(accountName).then(value => {
      account = value;
    });
    cy.then(() => createCategory(foodName)).then(value => {
      food = value;
    });
    cy.then(() => createCategory(travelName)).then(value => {
      travel = value;
    });
    cy.then(() => createTransaction({ accountId: account.id, amount: 100, date: '2026-01-10', categoryId: food.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 200, date: '2026-01-12', categoryId: travel.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 150, date: '2026-02-10', categoryId: food.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 250, date: '2026-02-12', categoryId: travel.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 120, date: '2026-03-10', categoryId: food.id }));
    cy.then(() => createTransaction({ accountId: account.id, amount: 230, date: '2026-03-12', categoryId: travel.id }));

    cy.visit('/analytics/explorer');
    cy.wait('@catalog').its('response.statusCode').should('eq', 200);
    prepareWrittenQuery('TRANSACTION_LEDGER', categoryByTemporal);
    setRange('2026-01-01', '2026-03-31');
    selectAccounts(accountName);
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = (response?.body.rows ?? []) as Array<{ values: Array<{ value: number }> }>;
      expect(rows.map(row => Number(row.values[0].value)).sort((left, right) => left - right)).to.deep.equal([
        100, 120, 150, 200, 230, 250,
      ]);
    });
    cy.get('[data-cy="analyticsResultTable"]').should('contain.text', foodName).and('contain.text', travelName);
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.contains('button', /^(Line|Línea)$/).click();
    cy.get('[data-cy="analyticsSemanticChart"] svg path.recharts-line-curve').should('have.length', 3);
    cy.get('[data-cy="analyticsSemanticChart"] .recharts-xAxis').should('contain.text', foodName).and('contain.text', travelName);
    cy.get('[data-cy="analyticsInteractiveLegend"]').should('contain.text', 'January 2026').and('contain.text', 'February 2026');
    cy.get('[data-cy="analyticsTemporalSeriesLineNote"]').should('contain.text', 'Each line represents a period');
    cy.get('[data-cy="analyticsSemanticChart"]').scrollIntoView();
    cy.get('[data-cy="analyticsSemanticChart"]').screenshot('analytics-category-axis-temporal-series-real');
    cy.contains('button', /^(Bars|Barras)$/).click();
    cy.get('#analytics-bar-style').select('GROUPED_BAR');
    cy.get('[data-cy="analyticsSemanticChart"] svg .recharts-bar-rectangle').should('have.length.greaterThan', 1);
    cy.get('#analytics-bar-style').select('STACKED_BAR');
    cy.get('[data-cy="analyticsSemanticChart"] svg .recharts-bar-rectangle').should('have.length.greaterThan', 1);
    cy.contains('button', /^(Table|Tabla)$/).click();
    cy.get('[data-cy="analyticsSemanticChart"]').should('not.exist');

    cy.get('[data-cy="analyticsModeSql"]').click();
    replaceWrittenQuery(kpi);
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.rows[0].values[0].value).to.equal(1050);
    });
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.contains('button', /^KPI$/).click();
    cy.get('[data-cy="analyticsCurrencyFacet-MXN-MONEY"]').should('contain.text', '1,050.00');

    cy.get('[data-cy="analyticsModeSql"]').click();
    replaceWrittenQuery(donut);
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@execute').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.contains('button', /^(Donut|Dona)$/).click();
    cy.get('[role="img"][aria-label*="dona" i] svg, [role="img"][aria-label*="donut" i] svg')
      .should('be.visible')
      .find('path.recharts-sector')
      .should('have.length', 2)
      .should($slices => {
        [...$slices].forEach(slice => {
          const bounds = slice.getBoundingClientRect();
          expect(Math.max(bounds.width, bounds.height)).to.be.greaterThan(150);
        });
      });
    cy.get('[role="img"][aria-label*="dona" i], [role="img"][aria-label*="donut" i]').scrollIntoView();
    cy.get('[role="img"][aria-label*="dona" i], [role="img"][aria-label*="donut" i]').screenshot('analytics-donut-real');
    cy.screenshot('analytics-donut-real-viewport');
    cy.screenshot('analytics-chart-modes-real-desktop', { capture: 'fullPage' });
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('analytics-chart-modes-real-mobile', { capture: 'fullPage' });
  });

  it('keeps a multi-calculation definition identical across visual and written modes and retains an invalid written draft', () => {
    let canonical = '';

    useExample('expenses-by-category');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').select('TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').select('TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').select('ACCOUNT_INSTITUTION');
    cy.contains('button', /(?:Add calculation|Añadir cálculo)/).click();
    cy.get('[data-cy="analyticsCalculationPicker"]')
      .contains('button', /^(Number of expenses|Número de gastos)$/)
      .click();
    cy.contains('button', /(?:Add calculation|Añadir cálculo)/).click();
    cy.get('[data-cy="analyticsCalculationPicker"]')
      .contains('button', /^(Average expense|Gasto promedio)$/)
      .click();
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-numero_gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-gasto_promedio"]').should('be.visible');

    cy.get('[data-cy="analyticsModeSql"]').click();
    cy.wait('@format').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="analyticsSqlText"]')
      .invoke('val')
      .then(value => {
        canonical = String(value);
        expect(canonical).to.contain('SUM(TRANSACTION_AMOUNT) AS gastos');
        expect(canonical).to.contain('COUNT(TRANSACTION_ID) AS numero_gastos');
        expect(canonical).to.contain('AVG(TRANSACTION_AMOUNT) AS gasto_promedio');
        expect(canonical).to.contain('GROUP BY TRANSACTION_MONTH, ACCOUNT_INSTITUTION');
      });

    cy.get('[data-cy="analyticsSqlText"]').clear();
    cy.get('[data-cy="analyticsSqlText"]').type('SELECT UNKNOWN_FIELD FROM TRANSACTION_LEDGER', { parseSpecialCharSequences: false });
    cy.get('[data-cy="analyticsApplySql"]').click();
    cy.wait('@parse').its('response.statusCode').should('eq', 400);
    cy.get('[data-cy="analyticsDiagnostics"]').should('be.visible');
    cy.get('[data-cy="analyticsSqlText"]').should('have.value', 'SELECT UNKNOWN_FIELD FROM TRANSACTION_LEDGER');

    cy.then(() => replaceWrittenQuery(canonical));
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsProjection-gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-numero_gastos"]').should('be.visible');
    cy.get('[data-cy="analyticsProjection-gasto_promedio"]').should('be.visible');
    cy.get('[data-cy="analyticsPrimaryBreakdown"]').should('have.value', 'TIME');
    cy.get('[data-cy="analyticsGroupingInterval-0"]').should('have.value', 'TRANSACTION_MONTH');
    cy.get('[data-cy="analyticsComparisonBreakdown"]').should('have.value', 'ACCOUNT_INSTITUTION');
  });

  it('shows a visible diagnostic when query execution has a network failure', () => {
    useExample('expenses-by-category');
    cy.intercept('POST', '/api/analytics/semantic/queries/execute', { forceNetworkError: true }).as('networkFailure');
    cy.get('[data-cy="analyticsExecute"]').click();
    cy.wait('@networkFailure');
    cy.get('[data-cy="analyticsDiagnostics"]').should('be.visible');
    cy.get('[data-cy="analyticsResultTable"]').should('not.exist');
  });

  it('keeps internal transfers out of economic flow and returns each transfer once in its own currency', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const mxnSourceName = `Analytics MXN transfer source ${suffix}`;
    const mxnDestinationName = `Analytics MXN transfer destination ${suffix}`;
    const usdSourceName = `Analytics USD transfer source ${suffix}`;
    const usdDestinationName = `Analytics USD transfer destination ${suffix}`;
    const economicExpenses =
      'SELECT SUM(TRANSACTION_AMOUNT) AS gastos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE ' +
      'WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED ' +
      'GROUP BY TRANSACTION_MONTH ORDER BY TRANSACTION_MONTH ASC LIMIT 100';
    const transferEvents =
      'SELECT COUNT(TRANSFER_ID) AS transferencias, SUM(TRANSFER_AMOUNT) AS importe_transferido ' +
      'FROM INTERNAL_TRANSFER_EVENT TIME BINDING TRANSFER_DATE ' +
      'GROUP BY TRANSFER_MONTH, DESTINATION_ACCOUNT_ID ORDER BY TRANSFER_MONTH ASC LIMIT 100';
    let mxnSource: Reference;
    let mxnDestination: Reference;
    let usdSource: Reference;
    let usdDestination: Reference;

    createAccount(mxnSourceName).then(value => {
      mxnSource = value;
    });
    createAccount(mxnDestinationName).then(value => {
      mxnDestination = value;
    });
    createAccount(usdSourceName, 'USD').then(value => {
      usdSource = value;
    });
    createAccount(usdDestinationName, 'USD').then(value => {
      usdDestination = value;
    });
    cy.then(() => createTransaction({ accountId: mxnSource.id, amount: 100, date: '2026-02-05' }));
    cy.then(() => createTransaction({ accountId: usdSource.id, amount: 1, date: '2026-02-06' }));
    cy.then(() =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/internal-transfers/commands/create',
          body: {
            sourceAccountId: mxnSource.id,
            destinationAccountId: mxnDestination.id,
            amount: 250,
            transactionDate: '2026-02-10',
            sourceDescription: 'Analytics MXN transfer out',
            destinationDescription: 'Analytics MXN transfer in',
          },
        })
        .its('status')
        .should('eq', 201),
    );
    cy.then(() =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/internal-transfers/commands/create',
          body: {
            sourceAccountId: usdSource.id,
            destinationAccountId: usdDestination.id,
            amount: 40,
            transactionDate: '2026-02-11',
            sourceDescription: 'Analytics USD transfer out',
            destinationDescription: 'Analytics USD transfer in',
          },
        })
        .its('status')
        .should('eq', 201),
    );

    const run = (dataset: string, query: string) => {
      cy.visit('/analytics/explorer');
      cy.wait('@catalog').its('response.statusCode').should('eq', 200);
      prepareWrittenQuery(dataset, query);
      setRange('2026-02-01', '2026-02-28');
      selectAccounts(mxnSourceName, usdSourceName);
      cy.get('[data-cy="analyticsExecute"]').click();
    };

    run('TRANSACTION_LEDGER', economicExpenses);
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = response?.body.rows as Array<{ currencyCode: string; values: Array<{ value: number }> }>;
      expect(rows.find(row => row.currencyCode === 'MXN')?.values[0].value).to.equal(100);
      expect(rows.find(row => row.currencyCode === 'USD')?.values[0].value).to.equal(1);
      expect(rows.flatMap(row => row.values.map(value => value.value))).not.to.include(250);
      expect(rows.flatMap(row => row.values.map(value => value.value))).not.to.include(40);
    });
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.get('[data-cy="analyticsCurrencyFacet-MXN-MONEY"]').should('contain.text', 'Importes en MXN');
    cy.get('[data-cy="analyticsCurrencyFacet-USD-MONEY"]').should('contain.text', 'Importes en USD');
    cy.get('[data-cy="analyticsResultTable"]').should('contain.text', '100.00').and('contain.text', '1.00');

    run('INTERNAL_TRANSFER_EVENT', transferEvents);
    cy.wait('@execute').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = response?.body.rows as Array<{ currencyCode: string; values: Array<{ alias: string; value: number }> }>;
      expect(rows).to.have.length(2);
      expect(rows.map(row => row.values.find(value => value.alias === 'transferencias')?.value)).to.deep.equal([1, 1]);
      expect(
        rows.map(row => row.values.find(value => value.alias === 'importe_transferido')?.value).sort((left, right) => left - right),
      ).to.deep.equal([40, 250]);
      expect(rows.map(row => row.currencyCode).sort()).to.deep.equal(['MXN', 'USD']);
    });
    cy.get('[data-cy="analyticsModeVisual"]').click();
    cy.screenshot('analytics-internal-transfers-multicurrency-real', { capture: 'fullPage' });
  });
});
