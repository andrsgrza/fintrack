/**
 * BUDGET-6B: browser -> REST -> Spring -> disposable PostgreSQL acceptance.
 * No notification endpoint is intercepted or stubbed. The isolated test app runs its real BUDGET-6A outbox worker.
 */
describe('Budget notification center real acceptance', { retries: 0 }, () => {
  type Reference = { id: number; name: string; version?: number };
  type Notification = {
    id: number;
    budgetId: number;
    readAt: string | null;
    snapshot: { budgetName: string; periodStart: string; newlyCrossedPercentages: string[] };
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  // The isolated Spring environment owns its deterministic E2E clock. Do not
  // derive financial fixtures from the host browser date, which may differ.
  const referenceDate = '2026-03-15';
  const periodStart = '2026-03-01';
  let account: Reference;
  let category: Reference;
  let existingNotificationCount = 0;

  const budgetPayload = (name: string, requestId: string) => ({
    name,
    type: 'EXPENSE',
    currency: 'MXN',
    clientRequestId: requestId,
    timeZone: 'America/Mexico_City',
    initialRevision: {
      targetAmount: 100,
      intervalCount: 1,
      intervalUnit: 'MONTH',
      alignment: 'ANCHORED',
      anchorDate: periodStart,
      customDayOfMonth: null,
      recurrenceMode: 'RECURRING',
      termination: 'NONE',
      terminationPeriodCount: null,
      recurrenceEndDate: null,
      accountScopeMode: 'SELECTED',
      accountIds: [account.id],
      categoryScopeMode: 'SELECTED',
      categoryIds: [category.id],
      tagScopeMode: 'NONE',
      tagIds: [],
      tagMatchMode: 'ANY',
    },
  });

  const createNotifiedBudget = (name: string, requestId: string) =>
    cy
      .authenticatedRequest<Reference>({ method: 'POST', url: '/api/budgets', body: budgetPayload(name, requestId) })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest({
          method: 'PUT',
          url: `/api/budgets/${body.id}/notification-preferences`,
          body: {
            expectedVersion: null,
            percentageAlertsEnabled: true,
            thresholdPercentages: [50, 80, 100],
            periodCloseEnabled: false,
          },
        });
      })
      .then(({ status }) => expect(status).to.equal(200));

  const awaitNotifications = (expectedCount: number): Cypress.Chainable<Notification[]> =>
    cy.window().then(window => {
      const storedToken = window.sessionStorage.getItem(Cypress.env('jwtStorageName'));
      expect(storedToken, 'authenticated Cypress token').to.not.equal(null);
      return cy
        .task('waitForNotifications', {
          baseUrl: Cypress.config('baseUrl'),
          bearerToken: JSON.parse(storedToken ?? 'null'),
          expectedCount,
          timeoutMs: 45_000,
        })
        .then(() =>
          cy
            .authenticatedRequest<{ notifications: Notification[] }>({
              method: 'GET',
              url: '/api/notifications',
              qs: { page: 0, size: 20 },
            })
            .then(({ body, status }) => {
              expect(status).to.equal(200);
              return body.notifications;
            }),
        );
    });

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Notification account ${suffix}`,
        institutionName: 'Notification browser bank',
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
          body: {
            name: `Notification category ${suffix}`,
            categoryType: 'EXPENSE',
            color: '#2f7ea5',
            active: true,
          },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        category = body;
      });
  });

  it('delivers an immutable threshold snapshot, reads it in the browser, and persists the read state', () => {
    const firstBudgetName = `Notification first ${suffix}`;
    const secondBudgetName = `Notification second ${suffix}`;

    cy.login(username, password);
    cy.visit('/');
    cy.authenticatedRequest<{ totalElements: number }>({
      method: 'GET',
      url: '/api/notifications',
      qs: { page: 0, size: 1 },
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      existingNotificationCount = body.totalElements;
    });
    cy.then(() => createNotifiedBudget(firstBudgetName, `notification-first-${suffix}`));
    cy.then(() => createNotifiedBudget(secondBudgetName, `notification-second-${suffix}`));
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-transactions',
      body: {
        transactionDate: referenceDate,
        postingDate: referenceDate,
        description: `Notification threshold crossing ${suffix}`,
        amount: 100,
        flow: 'OUT',
        origin: 'MANUAL',
        account: { id: account.id },
        category: { id: category.id },
      },
    })
      .its('status')
      .should('eq', 201);

    cy.then(() => awaitNotifications(existingNotificationCount + 2)).then(notifications => {
      const first = notifications.find(notification => notification.snapshot.budgetName === firstBudgetName);
      expect(first, 'first materialized notification').to.not.equal(undefined);
      expect(first?.snapshot.newlyCrossedPercentages).to.deep.equal(['50.0000', '80.0000', '100.0000']);
      expect(notifications.map(notification => notification.snapshot.budgetName)).to.include.members([firstBudgetName, secondBudgetName]);
    });

    cy.intercept('GET', '/api/notifications*').as('notificationHistory');
    cy.intercept('PATCH', '/api/notifications/*/read').as('readNotification');
    cy.visit('/notifications');
    cy.wait('@notificationHistory').its('response.statusCode').should('eq', 200);
    cy.contains('article', firstBudgetName).should('contain.text', '50.00%, 80.00%, 100.00%');
    cy.contains('article', firstBudgetName)
      .find('button')
      .contains(/Open budget|Abrir presupuesto/)
      .click();
    cy.wait('@readNotification').its('response.statusCode').should('eq', 200);
    cy.location('pathname').should('match', /^\/budgets\/\d+$/);
    cy.location('search').should('include', `referenceDate=${periodStart}`);

    cy.authenticatedRequest<{ notifications: Notification[] }>({
      method: 'GET',
      url: '/api/notifications',
      qs: { page: 0, size: 20 },
    }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.notifications.find(notification => notification.snapshot.budgetName === firstBudgetName)?.readAt).to.be.a('string');
    });

    cy.visit('/notifications');
    cy.wait('@notificationHistory').its('response.statusCode').should('eq', 200);
    cy.contains('button', /No leídas|Unread/).click();
    cy.contains('article', firstBudgetName).should('not.exist');
    cy.contains('article', secondBudgetName).should('exist');
    cy.get('button:visible')
      .contains(/Marcar todas como leídas|Mark all as read/)
      .click();
    cy.contains(/No hay notificaciones para este filtro|There are no notifications for this filter/).should('be.visible');
    cy.reload();
    cy.wait('@notificationHistory').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="notificationBell"] .notification-badge').should('not.exist');
  });
});
