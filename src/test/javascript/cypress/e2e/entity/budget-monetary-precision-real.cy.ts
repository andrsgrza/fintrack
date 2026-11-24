/**
 * BUDGET-FINAL-1: real browser -> Spring Boot -> disposable PostgreSQL evidence.
 * Intercepts only observe the UI request; no Budget or financial result is mocked.
 */
describe('Budget monetary precision and progress accessibility real acceptance', () => {
  type Reference = { id: number; name: string };
  type ProgressFixture = {
    expenseAccount: Reference;
    incomeAccount: Reference;
    expenseCategory: Reference;
    incomeCategory: Reference;
    expenseBudgetId: number;
  };

  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const largestTarget = '9999999999999999999.99';
  let expenseAccount: Reference;
  let expenseCategory: Reference;

  const revision = (targetAmount: string, categoryId: number, accountId: number) => ({
    targetAmount,
    intervalCount: 1,
    intervalUnit: 'MONTH',
    alignment: 'ANCHORED',
    anchorDate: '2026-03-01',
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
  });

  const budgetPayload = (name: string, type: 'EXPENSE' | 'INCOME', targetAmount: string, categoryId: number, accountId: number) => ({
    name,
    description: 'BUDGET-FINAL-1 isolated browser fixture',
    type,
    currency: 'MXN',
    clientRequestId: `budget-final-1-${suffix}-${Cypress._.uniqueId('request')}`,
    timeZone: 'America/Mexico_City',
    initialRevision: revision(targetAmount, categoryId, accountId),
  });

  const createAccount = (name: string) =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name,
          institutionName: 'BUDGET-FINAL-1 fixture bank',
          accountType: 'DEBIT',
          currency: 'MXN',
          initialBalance: 0,
          initialBalanceDate: '2026-01-01',
          active: true,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const createCategory = (name: string, categoryType: 'EXPENSE' | 'INCOME') =>
    cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/categories',
        body: { name, categoryType, color: categoryType === 'EXPENSE' ? '#b24b25' : '#287d52', active: true },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  before(() => {
    cy.login(username, password);
    createAccount(`Budget precision expense account ${suffix}`).then(value => {
      expenseAccount = value;
    });
    createCategory(`Budget precision expense ${suffix}`, 'EXPENSE').then(value => {
      expenseCategory = value;
    });
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.intercept('POST', '/api/budgets').as('createBudget');
  });

  it('preserves a large exact target through creation, persistence, reload, and current-period revision', () => {
    const budgetName = `Budget exact target ${suffix}`;
    cy.visit('/budgets/new');
    cy.get('[data-cy="budgetName"]').type(budgetName);
    cy.get('[data-cy="budgetTargetAmount"]').type(largestTarget);
    cy.get('[data-cy="budgetCurrency"]').select('MXN');
    cy.get('[data-cy="budgetIntervalCount"]').type('1');
    cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    cy.get('[data-cy="budgetStartDate"]').clear();
    cy.get('[data-cy="budgetStartDate"]').type('2026-03-01');
    cy.get('input[name="accountScope"]').eq(1).check();
    cy.contains(`Budget precision expense account ${suffix}`).click();
    cy.get('input[name="categoryScope"]').eq(1).check();
    cy.contains(`Budget precision expense ${suffix}`).click();
    cy.get('[data-cy="submitBudget"]').click();
    cy.wait('@createBudget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.initialRevision.targetAmount).to.equal(largestTarget);
      expect(response?.body.initialRevision.targetAmount).to.equal(largestTarget);
    });
    cy.location('pathname').should('match', /^\/budgets\/\d+$/);
    cy.get('[data-cy="budgetDetailHeading"]').should('contain.text', budgetName);
    cy.get('[data-cy="budgetDetailTarget"]').should('contain.text', '9,999,999,999,999,999,999.99');
    cy.get('[data-cy="budgetDetailTarget"]').should('not.contain.text', 'e+');
    cy.reload();
    cy.get('[data-cy="budgetDetailTarget"]').should('contain.text', '9,999,999,999,999,999,999.99');
    cy.url().then(url => {
      const budgetId = Number(new URL(url).pathname.split('/').pop());
      cy.authenticatedRequest({ method: 'GET', url: `/api/budgets/${budgetId}/history` }).then(({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.revisions[0].targetAmount).to.equal(largestTarget);
      });
    });
    cy.get('[data-cy="editBudget"]').click();
    cy.get('[data-cy="budgetEditAmount"]').clear();
    cy.get('[data-cy="budgetEditAmount"]').type('100.50');
    cy.contains('label', /Current period|Período actual/).click();
    cy.get('[data-cy="reviewBudgetRevision"]').click();
    cy.intercept('POST', '/api/budgets/*/revisions').as('applyExactRevision');
    cy.get('[data-cy="confirmBudgetRevision"]').click();
    cy.wait('@applyExactRevision').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.revision.targetAmount).to.equal('100.50');
    });
    cy.get('[data-cy="backToBudgetDetail"]').click();
    cy.get('[data-cy="budgetDetailTarget"]').should('contain.text', '100.50');
  });

  it('rejects over-scale and overflow targets in the browser and real REST boundary without partial budgets', () => {
    const assertServerRejects = (targetAmount: string) => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/budgets',
        failOnStatusCode: false,
        body: budgetPayload(`Budget invalid ${targetAmount} ${suffix}`, 'EXPENSE', targetAmount, expenseCategory.id, expenseAccount.id),
      }).then(({ body, status }) => {
        expect(status).to.equal(400);
        expect(body.code).to.equal('INVALID_PAYLOAD');
      });
    };
    cy.visit('/budgets/new');
    cy.get('[data-cy="budgetName"]').type(`Budget invalid text ${suffix}`);
    cy.get('[data-cy="budgetTargetAmount"]').type('0.001');
    cy.get('[data-cy="submitBudget"]').click();
    cy.get('[data-cy="budgetCreateError"]').should('be.visible');
    cy.get('@createBudget.all').should('have.length', 0);
    assertServerRejects('0.001');
    assertServerRejects('1.005');
    assertServerRejects('100.999');
    assertServerRejects('100.000');
    assertServerRejects('10000000000000000000.00');
    cy.authenticatedRequest({ method: 'GET', url: '/api/budgets/overview?referenceDate=2026-03-15' }).then(({ body, status }) => {
      expect(status).to.equal(200);
      expect(body.budgets.some((item: { name: string }) => item.name.includes(`Budget invalid ${suffix}`))).to.equal(false);
    });
  });

  it('keeps actual progress truthful while exposing a valid accessible range for expense and income', () => {
    const progressSuffix = `${suffix}-${Cypress._.uniqueId('progress')}`;
    const expenseName = `Budget expense 250 ${progressSuffix}`;
    const incomeName = `Budget income 250 ${progressSuffix}`;

    cy.then(() =>
      createAccount(`Budget progress expense account ${progressSuffix}`).then(progressExpenseAccount =>
        createAccount(`Budget progress income account ${progressSuffix}`).then(progressIncomeAccount =>
          createCategory(`Budget progress expense ${progressSuffix}`, 'EXPENSE').then(progressExpenseCategory =>
            createCategory(`Budget progress income ${progressSuffix}`, 'INCOME').then(progressIncomeCategory =>
              cy
                .authenticatedRequest<{ id: number }>({
                  method: 'POST',
                  url: '/api/budgets',
                  body: budgetPayload(expenseName, 'EXPENSE', '100.00', progressExpenseCategory.id, progressExpenseAccount.id),
                })
                .then(({ body, status }) => {
                  expect(status).to.equal(201);
                  return body.id;
                })
                .then(expenseBudgetId =>
                  cy
                    .authenticatedRequest({
                      method: 'POST',
                      url: '/api/budgets',
                      body: budgetPayload(incomeName, 'INCOME', '100.00', progressIncomeCategory.id, progressIncomeAccount.id),
                    })
                    .then(({ status }) => {
                      expect(status).to.equal(201);
                    })
                    .then(() =>
                      cy.authenticatedRequest({
                        method: 'POST',
                        url: '/api/financial-transactions',
                        body: {
                          transactionDate: '2026-03-10',
                          postingDate: '2026-03-10',
                          description: expenseName,
                          amount: 250,
                          flow: 'OUT',
                          origin: 'MANUAL',
                          account: { id: progressExpenseAccount.id },
                          category: { id: progressExpenseCategory.id },
                        },
                      }),
                    )
                    .then(({ status }) => {
                      expect(status).to.equal(201);
                    })
                    .then(() =>
                      cy.authenticatedRequest({
                        method: 'POST',
                        url: '/api/financial-transactions',
                        body: {
                          transactionDate: '2026-03-10',
                          postingDate: '2026-03-10',
                          description: incomeName,
                          amount: 250,
                          flow: 'IN',
                          origin: 'MANUAL',
                          account: { id: progressIncomeAccount.id },
                          category: { id: progressIncomeCategory.id },
                        },
                      }),
                    )
                    .then(({ status }) => {
                      expect(status).to.equal(201);
                      return {
                        expenseAccount: progressExpenseAccount,
                        incomeAccount: progressIncomeAccount,
                        expenseCategory: progressExpenseCategory,
                        incomeCategory: progressIncomeCategory,
                        expenseBudgetId,
                      } satisfies ProgressFixture;
                    }),
                ),
            ),
          ),
        ),
      ),
    ).then(({ expenseBudgetId }) => {
      cy.viewport(1440, 960);
      cy.visit('/budgets?referenceDate=2026-03-15');
      [expenseName, incomeName].forEach(name => {
        cy.contains('[data-cy^="budgetCard-"]', name).within(() => {
          cy.get('[data-cy="budgetActual"]').should('contain.text', '250.00');
          cy.contains('250.00%').should('be.visible');
          cy.get('[data-cy="budgetProgress"]')
            .should('have.attr', 'aria-valuemin', '0')
            .and('have.attr', 'aria-valuemax', '100')
            .and('have.attr', 'aria-valuenow', '100')
            .and('have.attr', 'aria-valuetext', '250.00%');
        });
      });
      cy.get(`[data-cy="budgetCard-${expenseBudgetId}"] [data-cy="budgetProgress"]`).scrollIntoView({ offset: { top: -120, left: 0 } });
      cy.screenshot('budget-final-1-progress-desktop', { capture: 'viewport' });
      cy.viewport('iphone-6');
      cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
      cy.get(`[data-cy="budgetCard-${expenseBudgetId}"] [data-cy="budgetProgress"]`).scrollIntoView({ offset: { top: -72, left: 0 } });
      cy.screenshot('budget-final-1-progress-mobile', { capture: 'viewport' });
    });
  });
});
