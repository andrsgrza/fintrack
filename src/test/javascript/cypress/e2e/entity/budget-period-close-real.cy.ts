/**
 * BUDGET-6C-C browser -> REST -> isolated Spring -> disposable PostgreSQL proof.
 *
 * Run `setup` with the fixed clock in the open period, then restart only the isolated
 * e2e application with a clock after its boundary and run `capture` against the same
 * disposable database. No notification REST response is intercepted or stubbed.
 */
describe('Budget period-close notification producer real acceptance', { retries: 0 }, () => {
  type Reference = { id: number; name: string };
  type Budget = { id: number; name: string; version?: number };
  type BudgetOverview = { budgets: Array<{ budgetId: number; name: string }> };
  type NotificationPreference = {
    configured: boolean;
    version: number | null;
    percentageAlertsEnabled: boolean;
    thresholdPercentages: number[];
    periodCloseEnabled: boolean;
    periodCloseEnabledSince: string | null;
  };
  type Notification = {
    id: number;
    notificationType: string;
    snapshot: {
      budgetName: string;
      periodStart: string;
      periodEndExclusive: string;
      actualAmount: number;
      targetAmount: number;
      logicalEventKey: string;
    };
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const phase = Cypress.env('BUDGET_PERIOD_CLOSE_PHASE');
  const fixture = Cypress.env('BUDGET_PERIOD_CLOSE_FIXTURE') ?? 'budget-6cc-period-close';
  const budgetName = `Period close ${fixture}`;
  const accountName = `Period close account ${fixture}`;
  const categoryName = `Period close category ${fixture}`;
  const eventKeyFor = (budgetId: number) => `PERIOD_CLOSED:${budgetId}:2026-03-31`;
  const lifecycleFixture = Cypress.env('BUDGET_PERIOD_CLOSE_LIFECYCLE_FIXTURE') ?? 'budget-6cc-lifecycle';
  const recoveryBudgetName = `Period close recovery ${lifecycleFixture}`;
  const onceBudgetName = `Period close once ${lifecycleFixture}`;
  const finishedBudgetName = `Period close finish ${lifecycleFixture}`;
  const pausedBudgetName = `Period close pause ${lifecycleFixture}`;
  const cancelledBudgetName = `Period close cancelled ${lifecycleFixture}`;
  const lifecycleAccountName = `Period close lifecycle account ${lifecycleFixture}`;
  const lifecycleCategoryName = `Period close lifecycle category ${lifecycleFixture}`;

  const notificationPage = () =>
    cy.authenticatedRequest<{ notifications: Notification[] }>({
      method: 'GET',
      url: '/api/notifications',
      qs: { page: 0, size: 100 },
    });

  const budgetPayload = (accountId: number, categoryId: number) => ({
    name: budgetName,
    description: 'Isolated BUDGET-6C-C real period-close fixture',
    type: 'EXPENSE',
    currency: 'MXN',
    clientRequestId: `${fixture}-create`,
    timeZone: 'America/Mexico_City',
    initialRevision: {
      targetAmount: 100,
      intervalCount: 1,
      intervalUnit: 'DAY',
      alignment: 'ANCHORED',
      anchorDate: '2026-03-31',
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

  const lifecycleBudgetPayload = (
    name: string,
    accountId: number,
    categoryId: number,
    recurrenceMode: 'RECURRING' | 'ONCE' = 'RECURRING',
  ) => ({
    name,
    description: 'Isolated BUDGET-6C-C lifecycle acceptance fixture',
    type: 'EXPENSE',
    currency: 'MXN',
    clientRequestId: `${name.replaceAll(' ', '-').toLowerCase()}-create`,
    timeZone: 'America/Mexico_City',
    initialRevision: {
      targetAmount: 100,
      intervalCount: 1,
      intervalUnit: 'DAY',
      alignment: 'ANCHORED',
      anchorDate: '2026-03-31',
      customDayOfMonth: null,
      recurrenceMode,
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

  const findLifecycleBudget = (name: string) =>
    cy
      .authenticatedRequest<BudgetOverview>({
        method: 'GET',
        url: '/api/budgets/overview',
        qs: { referenceDate: '2026-04-03', timeZone: 'America/Mexico_City' },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        const budget = body.budgets.find(candidate => candidate.name === name);
        if (!budget) throw new Error(`Expected lifecycle Budget ${name}.`);
        return budget;
      });

  const periodClosePreference = (budgetId: number) =>
    cy
      .authenticatedRequest<NotificationPreference>({ method: 'GET', url: `/api/budgets/${budgetId}/notification-preferences` })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        return body;
      });

  const updatePeriodClosePreference = (budgetId: number, preference: NotificationPreference, periodCloseEnabled: boolean) =>
    cy.authenticatedRequest<NotificationPreference>({
      method: 'PUT',
      url: `/api/budgets/${budgetId}/notification-preferences`,
      body: {
        percentageAlertsEnabled: preference.percentageAlertsEnabled,
        thresholdPercentages: preference.thresholdPercentages,
        periodCloseEnabled,
        expectedVersion: preference.version,
      },
    });

  const scheduleLifecycle = (budget: Budget, command: 'pause' | 'finish') =>
    cy.authenticatedRequest({
      method: 'POST',
      url: `/api/budgets/${budget.id}/${command}`,
      body: {
        expectedVersion: budget.version,
        clientRequestId: `${budget.name.replaceAll(' ', '-').toLowerCase()}-${command}`,
        timeZone: 'America/Mexico_City',
      },
    });

  beforeEach(() => cy.login(username, password));

  it('creates only real open-period financial data before the closing boundary', function () {
    if (phase !== 'setup') this.skip();

    let account: Reference;
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: accountName,
        institutionName: 'Period close browser bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-03-31',
        active: true,
      },
    })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        account = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: categoryName, categoryType: 'EXPENSE', color: '#2f7ea5', active: true },
        });
      })
      .then(({ body: category, status }) => {
        expect(status).to.equal(201);
        return cy
          .authenticatedRequest<Budget>({ method: 'POST', url: '/api/budgets', body: budgetPayload(account.id, category.id) })
          .then(({ status: budgetStatus }) => {
            expect(budgetStatus).to.equal(201);
            return cy
              .authenticatedRequest({
                method: 'POST',
                url: '/api/financial-transactions',
                body: {
                  transactionDate: '2026-03-31',
                  postingDate: '2026-03-31',
                  description: `Period close actual expense ${fixture}`,
                  amount: 60,
                  flow: 'OUT',
                  origin: 'MANUAL',
                  account: { id: account.id },
                  category: { id: category.id },
                },
              })
              .then(({ status: transactionStatus }) => {
                expect(transactionStatus).to.equal(201);
                notificationPage().then(({ body }) => {
                  expect(body.notifications.filter(notification => notification.snapshot.budgetName === budgetName)).to.have.length(0);
                });
              });
          });
      });
  });

  it('materializes one typed immutable close through the scheduler after the boundary', function () {
    if (phase !== 'capture') this.skip();

    cy.authenticatedRequest<{ budgets: Array<{ budgetId: number; name: string }> }>({
      method: 'GET',
      url: '/api/budgets/overview',
      qs: { referenceDate: '2026-04-02', timeZone: 'America/Mexico_City' },
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      const budget = body.budgets.find(candidate => candidate.name === budgetName);
      if (!budget) throw new Error('The period-close Budget did not persist across the isolated restart.');
      const eventKey = eventKeyFor(budget.budgetId);

      cy.window().then(window => {
        const token = window.sessionStorage.getItem(Cypress.env('jwtStorageName'));
        expect(token, 'authenticated Cypress token').to.not.equal(null);
        return cy.task('waitForNotifications', {
          baseUrl: Cypress.config('baseUrl'),
          bearerToken: JSON.parse(token ?? 'null'),
          expectedCount: 1,
          timeoutMs: 90_000,
        });
      });
      notificationPage().then(({ body, status: notificationStatus }) => {
        expect(notificationStatus).to.equal(200);
        const matching = body.notifications.filter(notification => notification.snapshot.logicalEventKey === eventKey);
        expect(matching, 'one logical close after scheduler retries').to.have.length(1);
        expect(matching[0].notificationType).to.equal('PERIOD_CLOSED');
        expect(matching[0].snapshot).to.include({
          budgetName,
          periodStart: '2026-03-31',
          periodEndExclusive: '2026-04-01',
          actualAmount: 60,
          targetAmount: 100,
          logicalEventKey: eventKey,
        });
        return periodClosePreference(budget.budgetId).then(preference =>
          updatePeriodClosePreference(budget.budgetId, preference, false).then(({ body: disabled, status: updateStatus }) => {
            expect(updateStatus).to.equal(200);
            expect(disabled.periodCloseEnabled).to.equal(false);
            return notificationPage().then(({ body: afterDeactivation }) => {
              expect(
                afterDeactivation.notifications.filter(notification => notification.snapshot.logicalEventKey === eventKey),
              ).to.have.length(1);
            });
          }),
        );
      });
    });

    cy.visit('/notifications');
    cy.contains('article', budgetName).should('contain.text', 'Cierre de período registrado');
    cy.screenshot('budget-period-close-real', { capture: 'viewport' });
  });

  it('creates missed-period, terminal and paused lifecycle fixtures before their boundaries', function () {
    if (phase !== 'lifecycle-setup') this.skip();

    let account: Reference;
    let category: Reference;
    let finished: Budget;
    let paused: Budget;
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: lifecycleAccountName,
        institutionName: 'Period close lifecycle bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-03-31',
        active: true,
      },
    })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        account = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: lifecycleCategoryName, categoryType: 'EXPENSE', color: '#2f7ea5', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        category = body;
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: lifecycleBudgetPayload(recoveryBudgetName, account.id, category.id),
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: lifecycleBudgetPayload(onceBudgetName, account.id, category.id, 'ONCE'),
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: lifecycleBudgetPayload(finishedBudgetName, account.id, category.id),
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        finished = body;
        return cy.authenticatedRequest<Budget>({
          method: 'POST',
          url: '/api/budgets',
          body: lifecycleBudgetPayload(pausedBudgetName, account.id, category.id),
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        paused = body;
        return scheduleLifecycle(finished, 'finish');
      })
      .then(({ status }) => {
        expect(status).to.equal(200);
        return scheduleLifecycle(paused, 'pause');
      })
      .then(({ status }) => {
        expect(status).to.equal(200);
        return cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: '2026-03-31',
            postingDate: '2026-03-31',
            description: `Period close lifecycle expense ${lifecycleFixture}`,
            amount: 60,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: account.id },
            category: { id: category.id },
          },
        });
      })
      .then(({ status }) => expect(status).to.equal(201));
  });

  it('recovers missed closings without duplication and preserves terminal lifecycle rules', function () {
    if (phase !== 'lifecycle-capture') this.skip();

    let recoveryId: number;
    let onceId: number;
    let finishedId: number;
    let pausedId: number;
    cy.then(() => findLifecycleBudget(recoveryBudgetName))
      .then(budget => {
        recoveryId = budget.budgetId;
        return findLifecycleBudget(onceBudgetName);
      })
      .then(budget => {
        onceId = budget.budgetId;
        return findLifecycleBudget(finishedBudgetName);
      })
      .then(budget => {
        finishedId = budget.budgetId;
        return findLifecycleBudget(pausedBudgetName);
      })
      .then(budget => {
        pausedId = budget.budgetId;
        return cy.window();
      })
      .then(window => {
        const token = window.sessionStorage.getItem(Cypress.env('jwtStorageName'));
        expect(token, 'authenticated Cypress token').to.not.equal(null);
        return cy.task('waitForNotificationLogicalKeys', {
          baseUrl: Cypress.config('baseUrl'),
          bearerToken: JSON.parse(token ?? 'null'),
          expectedLogicalEventKeys: [
            `PERIOD_CLOSED:${recoveryId}:2026-03-31`,
            `PERIOD_CLOSED:${recoveryId}:2026-04-01`,
            `PERIOD_CLOSED:${onceId}:2026-03-31`,
            `PERIOD_CLOSED:${finishedId}:2026-03-31`,
          ],
          timeoutMs: 120_000,
        });
      })
      .then(() => notificationPage())
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        const recoveryEvents = body.notifications.filter(notification =>
          notification.snapshot.logicalEventKey.startsWith(`PERIOD_CLOSED:${recoveryId}:`),
        );
        expect(recoveryEvents.map(notification => notification.snapshot.periodStart)).to.include.members(['2026-03-31', '2026-04-01']);
        expect(new Set(recoveryEvents.map(notification => notification.snapshot.logicalEventKey)).size).to.equal(recoveryEvents.length);

        const onceEvents = body.notifications.filter(notification =>
          notification.snapshot.logicalEventKey.startsWith(`PERIOD_CLOSED:${onceId}:`),
        );
        expect(onceEvents).to.have.length(1);
        expect(onceEvents[0].snapshot.periodStart).to.equal('2026-03-31');

        expect(
          body.notifications.filter(notification => notification.snapshot.logicalEventKey === `PERIOD_CLOSED:${finishedId}:2026-03-31`),
        ).to.have.length(1);
        expect(
          body.notifications.filter(notification => notification.snapshot.logicalEventKey === `PERIOD_CLOSED:${pausedId}:2026-04-01`),
        ).to.have.length(0);

        // A recurring Budget stays editable after a historical close. Turning its preference off must not
        // retract either durable close that has already materialized.
        return periodClosePreference(recoveryId).then(preference => updatePeriodClosePreference(recoveryId, preference, false));
      })
      .then(({ body: disabled, status }) => {
        expect(status).to.equal(200);
        expect(disabled.periodCloseEnabled).to.equal(false);
        return notificationPage();
      })
      .then(({ body: afterDeactivation }) => {
        expect(
          afterDeactivation.notifications.filter(
            notification => notification.snapshot.logicalEventKey === `PERIOD_CLOSED:${recoveryId}:2026-03-31`,
          ),
        ).to.have.length(1);
        return cy.login(Cypress.env('E2E_OTHER_USERNAME') ?? 'admin', Cypress.env('E2E_OTHER_PASSWORD') ?? 'admin');
      })
      .then(() => notificationPage())
      .then(({ body: otherUserNotifications, status: otherUserStatus }) => {
        expect(otherUserStatus).to.equal(200);
        expect(otherUserNotifications.notifications.some(notification => notification.snapshot.budgetName === recoveryBudgetName)).to.equal(
          false,
        );
      });
  });

  it('does not resurrect a closing disabled before its capture episode', function () {
    if (phase !== 'cancellation-setup') this.skip();

    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `${lifecycleAccountName} cancelled`,
        institutionName: 'Period close cancellation bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-03-31',
        active: true,
      },
    })
      .then(({ body: account, status }) => {
        expect(status).to.equal(201);
        return cy
          .authenticatedRequest<Reference>({
            method: 'POST',
            url: '/api/categories',
            body: { name: `${lifecycleCategoryName} cancelled`, categoryType: 'EXPENSE', color: '#2f7ea5', active: true },
          })
          .then(({ body: category, status: categoryStatus }) => {
            expect(categoryStatus).to.equal(201);
            return cy.authenticatedRequest<Budget>({
              method: 'POST',
              url: '/api/budgets',
              body: lifecycleBudgetPayload(cancelledBudgetName, account.id, category.id),
            });
          });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return periodClosePreference(body.id).then(preference => updatePeriodClosePreference(body.id, preference, false));
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.periodCloseEnabled).to.equal(false);
      });
  });

  it('keeps a cancelled historical closing terminal after re-enabling notifications', function () {
    if (phase !== 'cancellation-capture') this.skip();

    findLifecycleBudget(cancelledBudgetName)
      .then(budget =>
        periodClosePreference(budget.budgetId).then(preference =>
          updatePeriodClosePreference(budget.budgetId, preference, true).then(({ body, status }) => ({ budget, body, status })),
        ),
      )
      .then(({ budget, body, status }) => {
        expect(status).to.equal(200);
        expect(body.periodCloseEnabled).to.equal(true);
        notificationPage().then(({ body: notifications, status: notificationStatus }) => {
          expect(notificationStatus).to.equal(200);
          expect(
            notifications.notifications.filter(
              notification => notification.snapshot.logicalEventKey === `PERIOD_CLOSED:${budget.budgetId}:2026-03-31`,
            ),
          ).to.have.length(0);
        });
      });
  });
});
