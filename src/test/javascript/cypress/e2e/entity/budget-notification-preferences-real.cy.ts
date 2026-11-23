/**
 * BUDGET-6C-A: browser -> REST -> Spring -> disposable PostgreSQL acceptance.
 * Intercepts below only observe requests; no Budget or preference response is stubbed.
 */
describe('Budget notification preferences real acceptance', { retries: 0 }, () => {
  type BudgetReference = { id: number };
  type Preference = {
    configured: boolean;
    version: number | null;
    percentageAlertsEnabled: boolean;
    thresholdPercentages: string[];
    periodCloseEnabled: boolean;
    periodCloseEnabledSince: string | null;
  };
  type NotificationPage = { totalElements: number };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  it('persists explicit creation choices, supports immediate close episodes, and never generates a notification while editing', () => {
    const budgetName = `Notification preferences ${suffix}`;

    cy.login(username, password);
    cy.intercept('POST', '/api/budgets').as('createBudget');
    cy.intercept('GET', '/api/budgets/*/notification-preferences').as('loadPreferences');
    cy.intercept('PUT', '/api/budgets/*/notification-preferences').as('savePreferences');
    cy.visit('/budgets/new');

    cy.get('[data-cy="budgetName"]').type(budgetName);
    cy.get('[data-cy="budgetTargetAmount"]').type('1250');
    cy.get('[data-cy="budgetCurrency"]').select('MXN');
    cy.get('[data-cy="budgetIntervalCount"]').type('1');
    cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    cy.get('[data-cy="budgetCreateNotificationThresholdEnabled"]').should('not.be.checked');
    cy.get('[data-cy="budgetCreateNotificationPeriodCloseEnabled"]').should('not.be.checked');
    cy.get('[data-cy="budgetCreateNotificationThresholdSelector"]').should('not.exist');
    cy.contains('h3', /Spending alerts|Alertas de gasto/).should('be.visible');

    cy.get('[data-cy="budgetCreateNotificationThresholdEnabled"]').click();
    cy.get('[data-cy="budgetCreateNotificationThresholdSelector"]').should('be.visible');
    cy.get('[data-cy="budgetCreateNotificationSuggestion50"]').click();
    cy.get('[data-cy="budgetCreateNotificationSuggestion50"]').should('have.attr', 'aria-pressed', 'true');
    cy.get('[data-cy="budgetCreateNotificationSuggestion80"]').click();
    cy.get('[data-cy="budgetCreateNotificationOpenCustomThreshold"]').click();
    cy.get('[data-cy="budgetCreateNotificationCustomThreshold"]').type('100.1250');
    cy.get('[data-cy="budgetCreateNotificationAddThreshold"]').click();
    cy.get('[data-cy="budgetCreateNotificationPeriodCloseEnabled"]').click();
    cy.get('[data-cy="submitBudget"]').click();

    cy.wait('@createBudget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.notificationPreferences).to.deep.equal({
        percentageAlertsEnabled: true,
        thresholdPercentages: ['50', '80', '100.1250'],
        periodCloseEnabled: true,
      });
      const budgetId = (response?.body as BudgetReference).id;
      let notificationsBeforeEditing = 0;

      cy.location('pathname').should('eq', `/budgets/${budgetId}`);
      cy.wait('@loadPreferences').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="budgetDetailNotificationThresholdEnabled"]').should('be.checked');
      cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').should('be.checked');
      cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${budgetId}/notification-preferences` }).then(
        ({ body, status }) => {
          expect(status).to.equal(200);
          expect(body).to.include({ configured: true, percentageAlertsEnabled: true, periodCloseEnabled: true });
          expect(body.thresholdPercentages).to.deep.equal(['50.0000', '80.0000', '100.1250']);
          expect(body.periodCloseEnabledSince).to.be.a('string');
        },
      );
      cy.authenticatedRequest<NotificationPage>({ method: 'GET', url: '/api/notifications', qs: { page: 0, size: 1 } }).then(
        ({ body, status }) => {
          expect(status).to.equal(200);
          notificationsBeforeEditing = body.totalElements;
        },
      );

      // OFF cancels the active threshold episode but retains the exact stored values. Saving preferences itself has
      // no notification-producing endpoint.
      cy.get('[data-cy="budgetDetailNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetDetailNotificationThresholdSelector"]').should('not.exist');
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.percentageAlertsEnabled).to.equal(false);
        expect(request.body.thresholdPercentages).to.deep.equal(['50.0000', '80.0000', '100.1250']);
        expect(response?.statusCode).to.equal(200);
      });
      cy.get('[data-cy="budgetDetailNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetDetailNotificationThresholdSelector"]').should('be.visible');
      cy.contains('[data-cy="budgetDetailNotificationCustomChip"]', '100.125%').should('be.visible');

      // OFF cancels the active period-close episode. Saving preferences itself has no notification-producing endpoint.
      cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').click();
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.periodCloseEnabled).to.equal(false);
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.periodCloseEnabledSince).to.equal(null);
      });
      cy.get('[data-cy="budgetNotificationNotice"]').should('be.visible');

      // Re-enabling starts a fresh persisted episode; it does not invoke a period-close producer.
      cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').click();
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.periodCloseEnabled).to.equal(true);
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.periodCloseEnabledSince).to.be.a('string');
      });
      cy.reload();
      cy.wait('@loadPreferences').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').should('be.checked');
      cy.get('[data-cy="budgetDetailNotificationEditor"] section')
        .first()
        .should('be.visible')
        .then($section => {
          cy.window().then(window => window.scrollTo(0, window.scrollY + $section[0].getBoundingClientRect().top - 12));
        });
      cy.window().its('scrollY').should('be.greaterThan', 100);
      cy.screenshot('budget-notification-preferences-desktop', { capture: 'viewport' });
      cy.authenticatedRequest<NotificationPage>({ method: 'GET', url: '/api/notifications', qs: { page: 0, size: 1 } }).then(
        ({ body, status }) => {
          expect(status).to.equal(200);
          expect(body.totalElements).to.equal(notificationsBeforeEditing);
        },
      );

      // The same creation form switches the language and explanatory copy with the Budget type.
      cy.visit('/budgets/new');
      cy.get('#budget-type').select('INCOME');
      cy.contains('h3', /Income alerts|Alertas de ingresos/).should('be.visible');
      cy.viewport(390, 844);
      cy.get('[data-cy="budgetCreateNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetCreateNotificationSuggestion50"]').click();
      cy.get('[data-cy="budgetCreateNotificationEditor"]').should('be.visible').screenshot('budget-notification-preferences-mobile');
    });
  });

  it('renders a completed Budget preference summary as read-only', () => {
    const budgetName = `Completed notification preferences ${suffix}`;
    cy.login(username, password);
    cy.authenticatedRequest<BudgetReference>({
      method: 'POST',
      url: '/api/budgets',
      body: {
        name: budgetName,
        description: 'Finished Budget notification UI fixture',
        type: 'EXPENSE',
        currency: 'MXN',
        clientRequestId: `finished-preferences-${suffix}`,
        timeZone: 'America/Mexico_City',
        initialRevision: {
          targetAmount: '100.00',
          intervalCount: 1,
          intervalUnit: 'DAY',
          alignment: 'ANCHORED',
          anchorDate: '2026-11-18',
          customDayOfMonth: null,
          recurrenceMode: 'ONCE',
          termination: 'NONE',
          terminationPeriodCount: null,
          recurrenceEndDate: null,
          accountScopeMode: 'ALL',
          accountIds: [],
          categoryScopeMode: 'ALL',
          categoryIds: [],
          tagScopeMode: 'NONE',
          tagIds: [],
          tagMatchMode: 'ANY',
        },
        notificationPreferences: {
          percentageAlertsEnabled: true,
          thresholdPercentages: ['50', '87.1250'],
          periodCloseEnabled: true,
        },
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      cy.visit(`/budgets/${body.id}?referenceDate=2026-11-19`);
    });

    cy.get('[data-cy="budgetDetailNotificationReadOnly"]').should('be.visible').and('contain.text', '87.125%');
    cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('not.exist');
    cy.get('[data-cy="budgetDetailNotificationReadOnly"] dl')
      .should('be.visible')
      .then($summary => {
        cy.window().then(window => window.scrollTo(0, window.scrollY + $summary[0].getBoundingClientRect().top - 12));
      });
    cy.window().its('scrollY').should('be.greaterThan', 100);
    cy.screenshot('budget-notification-preferences-finished', { capture: 'viewport' });
  });
});
