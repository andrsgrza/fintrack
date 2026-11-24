/**
 * Real browser -> REST -> isolated Spring -> disposable PostgreSQL lifecycle proof.
 * Execute the three deterministic phases in order against the same disposable database.
 */
describe('Budget lifecycle real acceptance', { retries: 0 }, () => {
  type Reference = { id: number; name: string };
  type BudgetReference = { budgetId: number; name: string; version?: number };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const phase = Cypress.env('BUDGET_LIFECYCLE_PHASE');
  const fixture = Cypress.env('BUDGET_LIFECYCLE_FIXTURE') ?? 'budget-lifecycle-fixed';
  const pauseBudgetName = `Lifecycle pause ${fixture}`;
  const finishBudgetName = `Lifecycle finish ${fixture}`;
  let account: Reference;
  let category: Reference;

  const overview = (referenceDate: string) =>
    cy.authenticatedRequest<{ budgets: BudgetReference[] }>({
      method: 'GET',
      url: '/api/budgets/overview',
      qs: { referenceDate, timeZone: 'America/Mexico_City' },
    });

  const findBudget = (referenceDate: string, name: string) =>
    overview(referenceDate).then(({ body, status }) => {
      expect(status).to.equal(200);
      const budget = body.budgets.find(candidate => candidate.name === name);
      if (!budget) throw new Error(`Expected budget ${name} in the owner-scoped overview.`);
      return budget;
    });

  beforeEach(() => cy.login(username, password));

  it('phase one schedules pause and finalization from the active period', function () {
    if (phase !== 'schedule') this.skip();

    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Lifecycle account ${fixture}`,
        institutionName: 'Lifecycle bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-03-01',
        active: true,
      },
    })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        account = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: `Lifecycle category ${fixture}`, categoryType: 'EXPENSE', color: '#2f7ea5', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        category = body;
        return cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: '2026-03-16',
            postingDate: '2026-03-16',
            description: `Lifecycle paused-period expense ${fixture}`,
            amount: 25,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: account.id },
            category: { id: category.id },
          },
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(201);
        return cy.authenticatedRequest<{ id: number; version: number }>({
          method: 'POST',
          url: '/api/budgets',
          body: budgetPayload(pauseBudgetName),
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return cy
          .authenticatedRequest<{ version: number }>({
            method: 'POST',
            url: `/api/budgets/${body.id}/revisions`,
            body: {
              expectedVersion: body.version,
              clientRequestId: `lifecycle-revision-${fixture}`,
              timeZone: 'America/Mexico_City',
              applyToCurrentPeriod: false,
              effectivePeriodStart: '2026-03-16',
              revision: { ...budgetPayload(pauseBudgetName).initialRevision, targetAmount: 125 },
            },
          })
          .then(({ status: revisionStatus }) => {
            expect(revisionStatus).to.equal(200);
            cy.intercept('POST', `/api/budgets/${body.id}/pause`).as('pauseBudget');
            cy.visit(`/budgets/${body.id}?referenceDate=2026-03-15`);
          });
      });

    cy.get('[data-cy="budgetLifecycleActions"]').click();
    cy.contains('.dropdown-item', /Pause at next period|Pausar al siguiente período/).click();
    cy.get('[data-cy="confirmBudgetLifecycle"]').click();
    cy.wait('@pauseBudget').then(({ response }) => expect(response?.statusCode).to.equal(200));
    cy.get('[data-cy="budgetPendingLifecycle"]')
      .invoke('text')
      .should('match', /Mar 16, 2026|16 mar 2026/);
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Active|Activo/);

    cy.then(() =>
      cy.authenticatedRequest<{ id: number }>({ method: 'POST', url: '/api/budgets', body: budgetPayload(finishBudgetName) }),
    ).then(({ body, status }) => {
      expect(status).to.equal(201);
      cy.intercept('POST', `/api/budgets/${body.id}/finish`).as('finishBudget');
      cy.visit(`/budgets/${body.id}?referenceDate=2026-03-15`);
    });
    cy.get('[data-cy="budgetLifecycleActions"]').click();
    cy.contains('.dropdown-item', /Finish budget|Finalizar presupuesto/).click();
    cy.get('[data-cy="confirmBudgetLifecycle"]').click();
    cy.wait('@finishBudget').then(({ response }) => expect(response?.statusCode).to.equal(200));
    cy.get('[data-cy="budgetPendingLifecycle"]')
      .invoke('text')
      .should('match', /Mar 16, 2026|16 mar 2026/);
  });

  it('phase two proves the real paused and completed states at their boundaries', function () {
    if (phase !== 'boundary') this.skip();

    findBudget('2026-03-16', pauseBudgetName).then(budget => {
      cy.intercept('POST', `/api/budgets/${budget.budgetId}/resume`).as('resumeBudget');
      cy.visit(`/budgets/${budget.budgetId}?referenceDate=2026-03-16`);
    });
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Paused|Pausado/);
    cy.get('[data-cy="budgetDetailTarget"]').should('contain.text', '125.00');
    cy.get('[data-cy="budgetDetailActual"]').should('contain.text', '—');
    cy.get('[data-cy="budgetEvaluationState"]').should('be.visible');
    cy.get('[data-cy="budgetLifecycleActions"]').click();
    cy.contains('.dropdown-item', /Resume at next period|Reanudar en siguiente período/).click();
    cy.get('[data-cy="confirmBudgetLifecycle"]').click();
    cy.wait('@resumeBudget').then(({ response }) => expect(response?.statusCode).to.equal(200));
    cy.get('[data-cy="budgetPendingLifecycle"]')
      .invoke('text')
      .should('match', /Mar 17, 2026|17 mar 2026/);
    cy.screenshot('budget-5b-c-paused-desktop', { capture: 'viewport' });

    findBudget('2026-03-16', finishBudgetName).then(budget => cy.visit(`/budgets/${budget.budgetId}?referenceDate=2026-03-15`));
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Completed|Completado/);
    cy.get('[data-cy="budgetPeriodHistory"]').should('be.visible');
    cy.get('[data-cy="budgetLifecycleActions"]').click();
    cy.contains('.dropdown-item', /Pause|Pausar|Resume|Reanudar|Finish|Finalizar/).should('not.exist');
    cy.get('body').type('{esc}');
  });

  it('phase three proves the resume boundary keeps history paused and restores the next period', function () {
    if (phase !== 'resumed') this.skip();

    findBudget('2026-03-17', pauseBudgetName).then(budget => cy.visit(`/budgets/${budget.budgetId}?referenceDate=2026-03-16`));
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Active|Activo/);
    cy.get('[data-cy="budgetDetailActual"]').should('contain.text', '—');
    cy.get('[data-cy="budgetEvaluationState"]').should('be.visible');
    cy.get('[data-cy="budgetDetailReferenceDate"]').clear();
    cy.get('[data-cy="budgetDetailReferenceDate"]').type('2026-03-17');
    cy.get('[data-cy="budgetDetailActual"]').should('not.contain.text', '—');
    cy.get('[data-cy="budgetDetailTarget"]').should('contain.text', '125.00');
    // The March 16 movement belongs to the paused period. Resumption opens a new
    // March 17 period and must not carry that spending forward.
    cy.get('[data-cy="budgetDetailActual"]').should('contain.text', '0.00');
    cy.get('[data-cy="budgetDetailIdentity"]')
      .invoke('text')
      .should('match', /Active|Activo/);
    cy.screenshot('budget-5b-c-resumed-desktop', { capture: 'viewport' });
  });

  it('phase four deletes only the budget aggregate through the real confirmation UI', function () {
    if (phase !== 'delete') this.skip();

    const deleteBudgetName = `Lifecycle delete ${fixture}`;
    let accountId: number;
    let transactionId: number;
    let budgetId: number;

    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Lifecycle delete account ${fixture}`,
        institutionName: 'Lifecycle bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-03-01',
        active: true,
      },
    })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        accountId = body.id;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: `Lifecycle delete category ${fixture}`, categoryType: 'EXPENSE', color: '#7d4fa3', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        category = body;
        return cy.authenticatedRequest<{ id: number }>({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            transactionDate: '2026-03-17',
            postingDate: '2026-03-17',
            description: `Lifecycle deletion expense ${fixture}`,
            amount: 75,
            flow: 'OUT',
            origin: 'MANUAL',
            account: { id: accountId },
            category: { id: category.id },
          },
        });
      })
      .then(({ headers, status }) => {
        expect(status).to.equal(201);
        transactionId = Number(String(headers.location).split('/').pop());
        expect(transactionId).to.be.greaterThan(0);
        account = { id: accountId, name: '' };
        return cy.authenticatedRequest<{ id: number }>({ method: 'POST', url: '/api/budgets', body: budgetPayload(deleteBudgetName) });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        budgetId = body.id;
        return cy.authenticatedRequest<{ version: number }>({ method: 'GET', url: `/api/budgets/${budgetId}/history` });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        return cy.authenticatedRequest({
          method: 'POST',
          url: `/api/budgets/${budgetId}/revisions`,
          body: {
            expectedVersion: body.version,
            clientRequestId: `lifecycle-delete-revision-${fixture}`,
            timeZone: 'America/Mexico_City',
            applyToCurrentPeriod: false,
            effectivePeriodStart: '2026-03-18',
            revision: { ...budgetPayload(deleteBudgetName).initialRevision, targetAmount: 125 },
          },
        });
      })
      .then(({ status }) => {
        expect(status).to.equal(200);
        cy.intercept('DELETE', `/api/budgets/${budgetId}`).as('deleteBudget');
        cy.visit(`/budgets/${budgetId}?referenceDate=2026-03-17`);
      });

    cy.get('[data-cy="budgetLifecycleActions"]').click();
    cy.contains('.dropdown-item', /Delete budget|Eliminar presupuesto/).click();
    cy.get('[data-cy="confirmBudgetLifecycle"]').click();
    cy.wait('@deleteBudget').then(({ response }) => expect(response?.statusCode).to.equal(204));
    cy.location('pathname').should('eq', '/budgets');
    cy.get('[data-cy="budgetOverviewDeletedNotice"]').should('be.visible');
    cy.contains('[data-cy^="budgetCard-"]', deleteBudgetName).should('not.exist');
    cy.then(() => {
      cy.authenticatedRequest({ method: 'GET', url: `/api/financial-accounts/${accountId}` })
        .its('status')
        .should('eq', 200);
      expect(transactionId).to.be.greaterThan(0);
      cy.authenticatedRequest({ method: 'GET', url: `/api/financial-transactions/${transactionId}` })
        .its('status')
        .should('eq', 200);
      expect(budgetId).to.be.greaterThan(0);
      cy.authenticatedRequest({ method: 'GET', url: `/api/budgets/${budgetId}/history`, failOnStatusCode: false })
        .its('status')
        .should('eq', 400);
      cy.visit(`/budgets/${budgetId}?referenceDate=2026-03-17`);
      cy.contains(/Budget not found|No se encontró el presupuesto/).should('be.visible');
    });
  });

  function budgetPayload(name: string) {
    return {
      name,
      description: 'Deterministic lifecycle browser fixture',
      type: 'EXPENSE',
      currency: 'MXN',
      clientRequestId: `${name.replaceAll(' ', '-').toLowerCase()}-${fixture}`,
      timeZone: 'America/Mexico_City',
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
        accountScopeMode: 'SELECTED',
        accountIds: [account.id],
        categoryScopeMode: 'SELECTED',
        categoryIds: [category.id],
        tagScopeMode: 'NONE',
        tagIds: [],
        tagMatchMode: 'ANY',
      },
    };
  }
});
