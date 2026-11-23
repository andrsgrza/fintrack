/**
 * BUDGET-6C-A lifecycle and ownership acceptance. Each phase runs against the
 * same disposable database but a separately started e2e-clock application.
 * Intercepts observe requests only; preferences always persist through Spring.
 */
describe('Budget notification preferences lifecycle and ownership acceptance', { retries: 0 }, () => {
  type CreatedBudget = { id: number; version: number; name: string };
  type LifecycleResult = { budgetId: number; version: number; lifecycleEventId: number; effectivePeriodStart: string };
  type Preference = {
    configured: boolean;
    version: number | null;
    percentageAlertsEnabled: boolean;
    thresholdPercentages: string[];
    periodCloseEnabled: boolean;
    periodCloseEnabledSince: string | null;
  };
  type Overview = { budgets: Array<{ budgetId: number; name: string }> };
  type BudgetError = { code: string };

  const owner = Cypress.env('E2E_USERNAME') ?? 'user';
  const ownerPassword = Cypress.env('E2E_PASSWORD') ?? 'user';
  const otherUser = Cypress.env('E2E_OTHER_USERNAME') ?? 'admin';
  const otherPassword = Cypress.env('E2E_OTHER_PASSWORD') ?? 'admin';
  const phase = Cypress.env('BUDGET_NOTIFICATION_ACCEPTANCE_PHASE');
  const fixture = Cypress.env('BUDGET_NOTIFICATION_ACCEPTANCE_FIXTURE') ?? 'budget-6ca-notification-lifecycle';
  const boundary = '2026-03-16';
  const zone = 'America/Mexico_City';
  const pausedBudgetName = `Notification paused ${fixture}`;
  const finishedBudgetName = `Notification finished ${fixture}`;
  const ownedBudgetName = `Notification owner ${fixture}`;

  const preferences = {
    percentageAlertsEnabled: true,
    thresholdPercentages: ['50'],
    periodCloseEnabled: true,
  };

  const budgetPayload = (name: string) => ({
    name,
    description: 'Isolated BUDGET-6C-A lifecycle acceptance fixture',
    type: 'EXPENSE',
    currency: 'MXN',
    clientRequestId: `${name.replaceAll(' ', '-').toLowerCase()}-create`,
    timeZone: zone,
    initialRevision: {
      targetAmount: 100,
      intervalCount: 1,
      intervalUnit: 'DAY',
      alignment: 'ANCHORED',
      anchorDate: '2026-03-15',
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
    notificationPreferences: preferences,
  });

  const createBudget = (name: string) =>
    cy.authenticatedRequest<CreatedBudget>({ method: 'POST', url: '/api/budgets', body: budgetPayload(name) }).then(({ body, status }) => {
      expect(status).to.equal(201);
      return body;
    });

  const schedule = (budget: CreatedBudget, command: 'pause' | 'finish') =>
    cy
      .authenticatedRequest<LifecycleResult>({
        method: 'POST',
        url: `/api/budgets/${budget.id}/${command}`,
        body: {
          expectedVersion: budget.version,
          clientRequestId: `${budget.name.replaceAll(' ', '-').toLowerCase()}-${command}`,
          timeZone: zone,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.effectivePeriodStart).to.equal(boundary);
        return body;
      });

  const findBudget = (name: string) =>
    cy
      .authenticatedRequest<Overview>({
        method: 'GET',
        url: '/api/budgets/overview',
        qs: { referenceDate: boundary, timeZone: zone },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        const budget = body.budgets.find(candidate => candidate.name === name);
        if (!budget) throw new Error(`Expected owner-scoped Budget ${name}.`);
        return budget;
      });

  it('schedules pause and finish for the next effective period while the budgets remain editable today', function () {
    if (phase !== 'schedule') this.skip();

    cy.login(owner, ownerPassword);
    createBudget(pausedBudgetName).then(pausedBudget => {
      schedule(pausedBudget, 'pause');
    });
    createBudget(finishedBudgetName).then(finishedBudget => {
      return schedule(finishedBudget, 'finish').then(() => {
        cy.visit(`/budgets/${finishedBudget.id}?referenceDate=2026-03-15`);
        // A future finish is not terminal yet: the editor and real PUT remain available.
        cy.get('[data-cy="budgetDetailIdentity"]')
          .invoke('text')
          .should('match', /Active|Activo/);
        cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('be.enabled');
        cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${finishedBudget.id}/notification-preferences` })
          .then(({ body, status }) => {
            expect(status).to.equal(200);
            return cy.authenticatedRequest<Preference>({
              method: 'PUT',
              url: `/api/budgets/${finishedBudget.id}/notification-preferences`,
              body: { ...preferences, thresholdPercentages: ['50', '70'], expectedVersion: body.version },
            });
          })
          .then(({ body, status }) => {
            expect(status).to.equal(200);
            expect(body.thresholdPercentages).to.deep.equal(['50.0000', '70.0000']);
          });
      });
    });
  });

  it('edits a genuinely paused Budget and preserves a genuinely finished Budget as read-only', function () {
    if (phase !== 'boundary') this.skip();

    cy.login(owner, ownerPassword);
    findBudget(pausedBudgetName).then(paused => {
      cy.intercept('PUT', `/api/budgets/${paused.budgetId}/notification-preferences`).as('savePausedPreferences');
      cy.visit(`/budgets/${paused.budgetId}?referenceDate=${boundary}`);
    });
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Paused|Pausado/);
    cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('be.enabled');
    cy.get('[data-cy="budgetDetailNotificationOpenCustomThreshold"]').click();
    cy.get('[data-cy="budgetDetailNotificationCustomThreshold"]').type('80');
    cy.get('[data-cy="budgetDetailNotificationAddThreshold"]').click();
    cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').should('be.checked').click();
    cy.get('[data-cy="saveBudgetNotificationPreferences"]').click();
    cy.wait('@savePausedPreferences').then(({ response }) => expect(response?.statusCode).to.equal(200));
    cy.reload();
    cy.get('[data-cy="budgetDetailNotificationSuggestion80"]').should('have.attr', 'aria-pressed', 'true');
    cy.get('[data-cy="budgetDetailNotificationPeriodCloseEnabled"]').should('not.be.checked');

    findBudget(finishedBudgetName).then(finished => {
      cy.visit(`/budgets/${finished.budgetId}?referenceDate=${boundary}`);
      cy.get('[data-cy="budgetDetailIdentity"]')
        .invoke('text')
        .should('match', /Completed|Completado/);
      cy.get('[data-cy="budgetDetailNotificationReadOnly"]').should('be.visible').and('contain.text', '70%');
      cy.get('[data-cy="saveBudgetNotificationPreferences"]').should('not.exist');
      cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${finished.budgetId}/notification-preferences` })
        .then(({ body, status }) => {
          expect(status).to.equal(200);
          const before = body;
          return cy
            .authenticatedRequest<BudgetError>({
              method: 'PUT',
              url: `/api/budgets/${finished.budgetId}/notification-preferences`,
              failOnStatusCode: false,
              body: { ...preferences, thresholdPercentages: ['99'], expectedVersion: before.version },
            })
            .then(response => ({ before, response }));
        })
        .then(({ before, response }) => {
          expect(response.status).to.equal(400);
          expect(response.body.code).to.equal('INVALID_PAYLOAD');
          cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${finished.budgetId}/notification-preferences` }).then(
            ({ body, status }) => {
              expect(status).to.equal(200);
              expect(body).to.deep.equal(before);
            },
          );
        });
    });
  });

  it('rejects another authenticated user without mutating an owner preference', function () {
    if (phase !== 'ownership') this.skip();

    cy.login(owner, ownerPassword);
    createBudget(ownedBudgetName).then(created => {
      cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${created.id}/notification-preferences` }).then(
        ({ body, status }) => {
          expect(status).to.equal(200);
          const ownerPreference = body;
          cy.login(otherUser, otherPassword);
          cy.authenticatedRequest<BudgetError>({
            method: 'GET',
            url: `/api/budgets/${created.id}/notification-preferences`,
            failOnStatusCode: false,
          }).then(response => {
            expect(response.status).to.equal(400);
            expect(response.body.code).to.equal('INVALID_PAYLOAD');
          });
          cy.authenticatedRequest<BudgetError>({
            method: 'PUT',
            url: `/api/budgets/${created.id}/notification-preferences`,
            failOnStatusCode: false,
            body: { ...preferences, thresholdPercentages: ['99'], expectedVersion: ownerPreference.version },
          }).then(response => {
            expect(response.status).to.equal(400);
            expect(response.body.code).to.equal('INVALID_PAYLOAD');
          });
          cy.visit(`/budgets/${created.id}?referenceDate=${boundary}`);
          cy.contains(/Budget not found|No se encontró el presupuesto/).should('be.visible');
          cy.login(owner, ownerPassword);
          cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${created.id}/notification-preferences` }).then(
            ({ body, status }) => {
              expect(status).to.equal(200);
              expect(body).to.deep.equal(ownerPreference);
            },
          );
        },
      );
    });
  });
});
