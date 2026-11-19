/**
 * BUDGET-6C-D browser -> REST -> Spring -> disposable PostgreSQL acceptance.
 *
 * The setup phase creates financial records while the March 31 period is open. The capture and presentation phases
 * run after that boundary against the same disposable database and fixed E2E clock. No notification response is
 * stubbed or intercepted.
 */
describe('Budget period-close notification presentation real acceptance', { retries: 0 }, () => {
  type Reference = { id: number; name: string };
  type Budget = { id: number; name: string };
  type Notification = {
    id: number;
    budgetId: number;
    notificationType: string;
    readAt: string | null;
    snapshot: {
      budgetName: string;
      periodStart: string;
      periodEndExclusive: string;
      actualAmount: string;
      targetAmount: string;
      progressPercentage: string;
      logicalEventKey: string;
    };
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const phase = Cypress.env('BUDGET_PERIOD_CLOSE_UX_PHASE');
  const fixture = Cypress.env('BUDGET_PERIOD_CLOSE_UX_FIXTURE') ?? 'budget-6cd-period-close';
  const accountName = `Period close UX account ${fixture}`;
  const expenseCategoryName = `Period close UX expense ${fixture}`;
  const zeroExpenseCategoryName = `Period close UX zero expense ${fixture}`;
  const incomeCategoryName = `Period close UX income ${fixture}`;
  const expenseBudgetName = `Period close UX expense Budget ${fixture}`;
  const incomeBudgetName = `Period close UX income Budget ${fixture}`;
  const zeroBudgetName = `Period close UX zero Budget ${fixture}`;
  const periodStart = '2026-03-31';
  const referenceDate = '2026-04-02';
  const periodLabel = /31\s+mar\s+2026/i;

  const notificationPage = () =>
    cy.authenticatedRequest<{ notifications: Notification[] }>({
      method: 'GET',
      url: '/api/notifications',
      qs: { page: 0, size: 100 },
    });

  const periodCloseItem = (selector: string, budgetName: string) =>
    cy.get<HTMLElement>(selector).then(items => {
      const matchingItem = [...items].find(item => item.textContent?.includes(budgetName) && periodLabel.test(item.textContent));
      if (!matchingItem) throw new Error(`Expected the period-close notification for ${budgetName}.`);
      return cy.wrap(matchingItem);
    });

  const budgetPayload = (name: string, type: 'EXPENSE' | 'INCOME', accountId: number, categoryId: number, targetAmount: number) => ({
    name,
    description: 'Isolated BUDGET-6C-D presentation fixture',
    type,
    currency: 'MXN',
    clientRequestId: `${name.replaceAll(' ', '-').toLowerCase()}-create`,
    timeZone: 'America/Mexico_City',
    initialRevision: {
      targetAmount,
      intervalCount: 1,
      intervalUnit: 'DAY',
      alignment: 'ANCHORED',
      anchorDate: periodStart,
      customDayOfMonth: null,
      recurrenceMode: 'RECURRING',
      termination: 'NONE',
      terminationPeriodCount: null,
      recurrenceEndDate: null,
      accountScopeMode: 'SELECTED',
      accountIds: [accountId],
      categoryScopeMode: 'SELECTED',
      categoryIds: [categoryId],
      tagScopeMode: 'NONE',
      tagIds: [],
      tagMatchMode: 'ANY',
    },
    notificationPreferences: {
      percentageAlertsEnabled: false,
      thresholdPercentages: [],
      periodCloseEnabled: true,
    },
  });

  const findBudgets = () =>
    cy
      .authenticatedRequest<{ budgets: Array<{ budgetId: number; name: string }> }>({
        method: 'GET',
        url: '/api/budgets/overview',
        qs: { referenceDate, timeZone: 'America/Mexico_City' },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        const byName = new Map(body.budgets.map(budget => [budget.name, budget]));
        const expense = byName.get(expenseBudgetName);
        const income = byName.get(incomeBudgetName);
        const zero = byName.get(zeroBudgetName);
        expect(expense, 'expense Budget').to.not.equal(undefined);
        expect(income, 'income Budget').to.not.equal(undefined);
        expect(zero, 'zero-activity Budget').to.not.equal(undefined);
        return { expense: expense as { budgetId: number }, income: income as { budgetId: number }, zero: zero as { budgetId: number } };
      });

  const findExpenseFixtureReferences = () =>
    cy.authenticatedRequest<Reference[]>({ method: 'GET', url: '/api/financial-accounts' }).then(({ body: accounts, status }) => {
      expect(status).to.equal(200);
      const account = accounts.find(candidate => candidate.name === accountName);
      expect(account, 'fixture account').to.not.equal(undefined);
      return cy
        .authenticatedRequest<Reference[]>({ method: 'GET', url: '/api/categories' })
        .then(({ body: categories, status: categoryStatus }) => {
          expect(categoryStatus).to.equal(200);
          const category = categories.find(candidate => candidate.name === expenseCategoryName);
          expect(category, 'fixture expense category').to.not.equal(undefined);
          return { account, category };
        });
    });

  const waitForMaterializedClosings = (budgetIds: number[]) =>
    cy.window().then(window => {
      const storedToken = window.sessionStorage.getItem(Cypress.env('jwtStorageName'));
      expect(storedToken, 'authenticated Cypress token').to.not.equal(null);
      return cy.task('waitForNotificationLogicalKeys', {
        baseUrl: Cypress.config('baseUrl'),
        bearerToken: JSON.parse(storedToken ?? 'null'),
        expectedLogicalEventKeys: budgetIds.map(id => `PERIOD_CLOSED:${id}:${periodStart}`),
        timeoutMs: 90_000,
      });
    });

  beforeEach(() => cy.login(username, password));

  it('creates the authoritative expense, income, and zero-activity financial fixtures while the period is open', function () {
    if (phase !== 'setup') this.skip();

    let account: Reference;
    let expenseCategory: Reference;
    let zeroExpenseCategory: Reference;
    let incomeCategory: Reference;
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: accountName,
        institutionName: 'Period close UX bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: periodStart,
        active: true,
      },
    })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        account = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: expenseCategoryName, categoryType: 'EXPENSE', color: '#b96a2c', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        expenseCategory = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: zeroExpenseCategoryName, categoryType: 'EXPENSE', color: '#6f7a86', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        zeroExpenseCategory = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: incomeCategoryName, categoryType: 'INCOME', color: '#317b56', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        incomeCategory = body;
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: budgetPayload(expenseBudgetName, 'EXPENSE', account.id, expenseCategory.id, 100),
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: budgetPayload(incomeBudgetName, 'INCOME', account.id, incomeCategory.id, 200),
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: budgetPayload(zeroBudgetName, 'EXPENSE', account.id, zeroExpenseCategory.id, 50),
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: periodStart,
            postingDate: periodStart,
            description: `Period close UX expense ${fixture}`,
            amount: 60,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: account.id },
            category: { id: expenseCategory.id },
          },
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: periodStart,
            postingDate: periodStart,
            description: `Period close UX income ${fixture}`,
            amount: 150,
            flow: 'IN',
            origin: 'MANUAL',
            account: { id: account.id },
            category: { id: incomeCategory.id },
          },
        });
      })
      .its('status')
      .should('eq', 201);
  });

  it('captures typed immutable close snapshots through the actual scheduler and outbox', function () {
    if (phase !== 'capture') this.skip();

    findBudgets().then(budgets => {
      waitForMaterializedClosings([budgets.expense.budgetId, budgets.income.budgetId, budgets.zero.budgetId]);
      notificationPage().then(({ body, status }) => {
        expect(status).to.equal(200);
        const byName = new Map(body.notifications.map(notification => [notification.snapshot.budgetName, notification]));
        const expense = byName.get(expenseBudgetName);
        const income = byName.get(incomeBudgetName);
        const zero = byName.get(zeroBudgetName);
        expect(expense?.notificationType).to.equal('PERIOD_CLOSED');
        expect(expense?.snapshot).to.include({ actualAmount: '60.00', targetAmount: '100.00', progressPercentage: '60.00', periodStart });
        expect(income?.snapshot).to.include({ actualAmount: '150.00', targetAmount: '200.00', progressPercentage: '75.00', periodStart });
        expect(zero?.snapshot).to.include({ actualAmount: '0', targetAmount: '50.00', progressPercentage: '0.00', periodStart });
      });
    });
  });

  it('presents immutable period closes in the real bell and history, then opens the exact historical Budget period', function () {
    if (phase !== 'presentation') this.skip();

    findBudgets().then(budgets => {
      notificationPage().then(({ body }) => {
        const expense = body.notifications.find(
          notification => notification.budgetId === budgets.expense.budgetId && notification.snapshot.periodStart === periodStart,
        );
        expect(expense?.readAt, 'the fixture starts unread').to.equal(null);
      });

      cy.viewport(1200, 720);
      cy.visit('/');
      cy.get('[data-cy="notificationBell"]').click();
      cy.contains(/Período cerrado|Period closed/).should('be.visible');
      periodCloseItem('.notification-preview-item', expenseBudgetName).should('contain.text', '60.00').and('contain.text', '100.00');
      periodCloseItem('.notification-preview-item', incomeBudgetName).should('contain.text', '150.00').and('contain.text', '200.00');
      periodCloseItem('.notification-preview-item', zeroBudgetName).contains(/No se registraron gastos|No expenses were recorded/);
      periodCloseItem('.notification-preview-item', expenseBudgetName).contains(/Resultado registrado el|Result recorded on/);
      periodCloseItem('.notification-preview-item', expenseBudgetName).scrollIntoView();
      cy.screenshot('budget-6cd-bell-desktop');

      cy.get('[data-cy="notificationBell"]').click();
      cy.viewport(390, 844);
      cy.get('[data-cy="navbar"] button[aria-label="Menu"]').click();
      cy.get('[data-cy="notificationBell"]').should('be.visible').click();
      cy.contains(/Período cerrado|Period closed/).should('be.visible');
      periodCloseItem('.notification-preview-item', expenseBudgetName).scrollIntoView();
      cy.screenshot('budget-6cd-bell-mobile');

      periodCloseItem('.notification-preview-item', expenseBudgetName).click();
      cy.location('pathname').should('eq', `/budgets/${budgets.expense.budgetId}`);
      cy.location('search').should('include', `referenceDate=${periodStart}`);
      cy.get('[data-cy="budgetDetailEvaluation"]').should('contain.text', '60.00');
      cy.viewport(1200, 720);

      notificationPage().then(({ body }) => {
        expect(
          body.notifications.find(
            notification => notification.budgetId === budgets.expense.budgetId && notification.snapshot.periodStart === periodStart,
          )?.readAt,
        ).to.be.a('string');
      });

      findExpenseFixtureReferences().then(({ account, category }) => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: periodStart,
            postingDate: periodStart,
            description: `Period close UX correction ${fixture}`,
            amount: 40,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: account.id },
            category: { id: category.id },
          },
        })
          .its('status')
          .should('eq', 201);
      });

      cy.reload();
      cy.get('[data-cy="budgetDetailEvaluation"]').should('contain.text', '100.00');
      cy.visit('/notifications');
      periodCloseItem('article', expenseBudgetName).should('contain.text', '60.00');
      periodCloseItem('article', incomeBudgetName).should('contain.text', '150.00');
      periodCloseItem('article', zeroBudgetName).contains(/No se registraron gastos|No expenses were recorded/);
      periodCloseItem('article', expenseBudgetName).scrollIntoView();
      cy.screenshot('budget-6cd-history-desktop');
      cy.viewport(390, 844);
      periodCloseItem('article', expenseBudgetName).scrollIntoView();
      cy.screenshot('budget-6cd-history-mobile');
    });
  });
});
