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
      cy.get('[data-cy="budgetNotificationPreferencesSummary"]').should('contain.text', '50%').and('contain.text', '100.125%');
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('not.exist');
      cy.get('[data-cy="budgetNotificationPreferencesSection"]').scrollIntoView();
      cy.get('[data-cy="budgetNotificationPreferencesSection"]').screenshot('budget-detail-read-only-desktop');
      cy.get('[data-cy="configureBudgetNotifications"]').click();
      cy.location('pathname').should('eq', `/budgets/${budgetId}/edit`);
      cy.get('[data-cy="budgetEditSectionNav"]').should('be.visible').screenshot('budget-edit-section-navigation-desktop');
      cy.get('[data-cy="budgetEditSectionGeneralLink"]').click();
      cy.get('[data-cy="budgetEditName"]').clear();
      cy.get('[data-cy="budgetEditName"]').type(`${budgetName} local draft`);
      cy.get('[data-cy="budgetEditMetadataDirty"]').should('be.visible');
      cy.get('[data-cy="budgetEditSectionNotificationsLink"]').click();
      cy.get('[data-cy="budgetEditNotifications"]').scrollIntoView();
      cy.get('[data-cy="budgetEditNotificationThresholdEnabled"]').should('be.checked');
      cy.get('[data-cy="budgetEditNotificationPeriodCloseEnabled"]').should('be.checked');
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
      cy.get('[data-cy="budgetEditNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetEditNotificationThresholdSelector"]').should('not.exist');
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.percentageAlertsEnabled).to.equal(false);
        expect(request.body.thresholdPercentages).to.deep.equal(['50.0000', '80.0000', '100.1250']);
        expect(response?.statusCode).to.equal(200);
      });
      cy.get('[data-cy="budgetEditMetadataDirty"]').should('be.visible');
      cy.get('[data-cy="budgetEditName"]').should('have.value', `${budgetName} local draft`);
      cy.get('[data-cy="cancelBudgetMetadata"]').click();
      cy.get('[data-cy="budgetEditMetadataDirty"]').should('not.exist');
      cy.get('[data-cy="budgetEditNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetEditNotificationThresholdSelector"]').should('be.visible');
      cy.contains('[data-cy="budgetEditNotificationCustomChip"]', '100.125%').should('be.visible');

      // OFF cancels the active period-close episode. Saving preferences itself has no notification-producing endpoint.
      cy.get('[data-cy="budgetEditNotificationPeriodCloseEnabled"]').click();
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.periodCloseEnabled).to.equal(false);
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.periodCloseEnabledSince).to.equal(null);
      });
      cy.get('[data-cy="budgetEditNotificationNotice"]').should('be.visible');

      // Re-enabling starts a fresh persisted episode; it does not invoke a period-close producer.
      cy.get('[data-cy="budgetEditNotificationPeriodCloseEnabled"]').click();
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
      cy.wait('@savePreferences').then(({ request, response }) => {
        expect(request.body.periodCloseEnabled).to.equal(true);
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.periodCloseEnabledSince).to.be.a('string');
      });
      cy.reload();
      cy.wait('@loadPreferences').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="budgetEditNotificationPeriodCloseEnabled"]').should('be.checked');
      cy.get('[data-cy="budgetEditNotificationEditor"] section')
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
      cy.visit(`/budgets/${budgetId}/edit`);
      cy.get('[data-cy="budgetEditSectionNav"]').should('be.visible').screenshot('budget-edit-section-navigation-mobile');
      cy.visit('/budgets/new');
      cy.get('[data-cy="budgetCreateNotificationThresholdEnabled"]').click();
      cy.get('[data-cy="budgetCreateNotificationSuggestion50"]').click();
      cy.get('[data-cy="budgetCreateNotificationEditor"]').should('be.visible').screenshot('budget-notification-preferences-mobile');
    });
  });

  it('preserves the editor draft after a real concurrent preference update', () => {
    const budgetName = `Notification conflict ${suffix}`;
    cy.login(username, password);
    cy.authenticatedRequest<BudgetReference>({
      method: 'POST',
      url: '/api/budgets',
      body: {
        name: budgetName,
        description: 'Concurrent notification-preference fixture',
        type: 'EXPENSE',
        currency: 'MXN',
        clientRequestId: `notification-conflict-${suffix}`,
        timeZone: 'America/Mexico_City',
        initialRevision: {
          targetAmount: '100.00',
          intervalCount: 1,
          intervalUnit: 'MONTH',
          alignment: 'ANCHORED',
          anchorDate: '2026-11-19',
          customDayOfMonth: null,
          recurrenceMode: 'RECURRING',
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
          thresholdPercentages: ['50'],
          periodCloseEnabled: true,
        },
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      cy.intercept('PUT', `/api/budgets/${body.id}/notification-preferences`).as('editorConflictSave');
      cy.visit(`/budgets/${body.id}/edit#notifications`);
      cy.get('[data-cy="budgetEditNotificationSuggestion80"]').should('have.attr', 'aria-pressed', 'false').click();
      cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${body.id}/notification-preferences` })
        .then(({ body: current, status: readStatus }) => {
          expect(readStatus).to.equal(200);
          return cy
            .authenticatedRequest<Preference>({
              method: 'PUT',
              url: `/api/budgets/${body.id}/notification-preferences`,
              body: {
                percentageAlertsEnabled: true,
                thresholdPercentages: ['50', '70'],
                periodCloseEnabled: true,
                expectedVersion: current.version,
              },
            })
            .then(({ body: updated, status: updateStatus }) => {
              expect(updateStatus).to.equal(200);
              expect(updated.version).to.not.equal(current.version);
            });
        })
        .then(() => {
          cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
          cy.wait('@editorConflictSave').its('response.statusCode').should('eq', 409);
          cy.get('[data-cy="budgetEditNotificationError"]')
            .invoke('text')
            .should('match', /cambiaron en otra sesión|changed in another session/);
          cy.get('[data-cy="budgetEditNotificationSuggestion80"]').should('have.attr', 'aria-pressed', 'true');
          cy.get('[data-cy="budgetEditNotificationDirty"]').should('be.visible');
        });
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

    cy.get('[data-cy="budgetNotificationPreferencesSummary"]').should('be.visible').and('contain.text', '87.125%');
    cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('not.exist');
    cy.get('[data-cy="budgetNotificationPreferencesSummary"]')
      .should('be.visible')
      .then($summary => {
        cy.window().then(window => window.scrollTo(0, window.scrollY + $summary[0].getBoundingClientRect().top - 12));
      });
    cy.window().its('scrollY').should('be.greaterThan', 100);
    cy.screenshot('budget-notification-preferences-finished', { capture: 'viewport' });
  });
});
