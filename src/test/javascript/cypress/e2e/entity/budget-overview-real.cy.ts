/**
 * Real browser -> REST -> Spring -> disposable PostgreSQL coverage for BUDGET-5A.
 * No budget, calendar-preview, overview, selector, or evaluation response is stubbed.
 */
describe('Budget overview and creation real acceptance', () => {
  type Reference = { id: number; name: string };
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  let account: Reference;
  let rootCategory: Reference;
  let childCategory: Reference;
  let tag: Reference;
  let isolatedAccount: Reference;
  let isolatedExpenseRoot: Reference;
  let isolatedExpenseChild: Reference;
  let isolatedExpenseTag: Reference;
  let isolatedIncomeAccount: Reference;
  let isolatedIncomeCategory: Reference;
  let isolatedTransferDestination: Reference;

  const createIsolatedFixtures = () => {
    const fixture = `${suffix}-${Cypress._.uniqueId('budget-case')}`;
    return cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name: `Budget isolated expense account ${fixture}`,
          institutionName: 'Budget browser bank',
          accountType: 'DEBIT',
          currency: 'MXN',
          initialBalance: 0,
          initialBalanceDate: '2026-01-01',
          active: true,
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedAccount = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: `Budget isolated root ${fixture}`, categoryType: 'EXPENSE', color: '#2f7ea5', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedExpenseRoot = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: {
            name: `Budget isolated child ${fixture}`,
            categoryType: 'EXPENSE',
            color: '#75a743',
            parentCategory: { id: isolatedExpenseRoot.id },
            active: true,
          },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedExpenseChild = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/tags',
          body: { name: `Tag ${fixture}`, color: '#7d4fa3', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedExpenseTag = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/financial-accounts',
          body: {
            name: `Budget isolated income account ${fixture}`,
            institutionName: 'Budget browser bank',
            accountType: 'DEBIT',
            currency: 'MXN',
            initialBalance: 0,
            initialBalanceDate: '2026-01-01',
            active: true,
          },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedIncomeAccount = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/financial-accounts',
          body: {
            name: `Budget isolated transfer account ${fixture}`,
            institutionName: 'Budget browser bank',
            accountType: 'DEBIT',
            currency: 'MXN',
            initialBalance: 0,
            initialBalanceDate: '2026-01-01',
            active: true,
          },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedTransferDestination = body;
        return cy.authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: { name: `Budget isolated income ${fixture}`, categoryType: 'INCOME', color: '#3f8c54', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        isolatedIncomeCategory = body;
      });
  };

  before(() => {
    cy.login(username, password);
    cy.authenticatedRequest<Reference>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: `Budget browser account ${suffix}`,
        institutionName: 'Budget browser bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body, status }) => {
      expect(status).to.equal(201);
      account = body;
    });
    cy.then(() =>
      cy
        .authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: {
            name: `Budget root ${suffix}`,
            description: 'Real budget scope fixture',
            categoryType: 'EXPENSE',
            color: '#2f7ea5',
            active: true,
          },
        })
        .then(({ body, status }) => {
          expect(status).to.equal(201);
          rootCategory = body;
        }),
    );
    cy.then(() =>
      cy
        .authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/categories',
          body: {
            name: `Budget child ${suffix}`,
            description: 'Descendant inclusion fixture',
            categoryType: 'EXPENSE',
            color: '#75a743',
            parentCategory: { id: rootCategory.id },
            active: true,
          },
        })
        .then(({ body, status }) => {
          expect(status).to.equal(201);
          childCategory = body;
        }),
    );
    cy.then(() =>
      cy
        .authenticatedRequest<Reference>({
          method: 'POST',
          url: '/api/tags',
          body: { name: `Budget tag ${suffix}`, description: 'Real budget tag fixture', color: '#7d4fa3', active: true },
        })
        .then(({ body, status }) => {
          expect(status).to.equal(201);
          tag = body;
        }),
    );
  });

  beforeEach(() => {
    cy.login(username, password);
    createIsolatedFixtures();
    cy.intercept('POST', '/api/budgets/calendar-preview', request => {
      const body = request.body as {
        alignment?: string;
        anchorDate?: string;
        customDayOfMonth?: number | null;
        intervalCount?: number;
        intervalUnit?: string;
      };
      if (body.intervalCount === 4 && body.intervalUnit === 'DAY' && body.anchorDate === '2026-03-11') {
        request.alias = 'fourDayPreview';
      } else if (body.alignment === 'NATURAL' && body.intervalUnit === 'MONTH' && body.anchorDate === '2026-03-01') {
        request.alias = 'naturalMonthPreview';
      } else if (body.alignment === 'CUSTOM' && body.intervalUnit === 'WEEK' && body.anchorDate === '2026-03-10') {
        request.alias = 'customTuesdayPreview';
      } else if (
        body.alignment === 'CUSTOM' &&
        body.intervalUnit === 'MONTH' &&
        body.anchorDate === '2026-02-28' &&
        body.customDayOfMonth === 31
      ) {
        request.alias = 'customThirtyOnePreview';
      } else {
        request.alias = 'calendarPreview';
      }
    });
    cy.intercept('POST', '/api/budgets').as('createBudget');
    cy.intercept('GET', '/api/budgets/overview*').as('budgetOverview');
  });

  it('uses the Java calendar preview, reusable scopes, and the real owner-scoped overview', () => {
    const budgetName = `Browser groceries ${suffix}`;
    cy.viewport(1440, 960);
    cy.visit('/budgets');
    cy.wait('@budgetOverview').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="createBudget"]').click();
    cy.location('pathname').should('eq', '/budgets/new');

    cy.get('[data-cy="budgetName"]').type(budgetName);
    cy.get('[data-cy="budgetTargetAmount"]').type('1250');
    cy.get('[data-cy="budgetCurrency"]').select('MXN');
    cy.get('[data-cy="budgetIntervalCount"]').type('1');
    cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    cy.wait('@calendarPreview').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ intervalCount: 1, intervalUnit: 'MONTH', alignment: 'ANCHORED', recurrenceMode: 'RECURRING' });
      expect(response?.body.periods).to.have.length.at.least(1);
    });
    cy.get('[data-cy="budgetCalendarPreview"] ol').should('be.visible');

    cy.get('input[name="accountScope"]').eq(1).check();
    cy.contains(`Budget browser account ${suffix}`).click();
    cy.get('input[name="categoryScope"]').eq(1).check();
    cy.get('fieldset')
      .eq(1)
      .find('label')
      .filter((_, label) => label.textContent?.trim() === `Budget root ${suffix}`)
      .click();
    cy.get('input[name="tagScope"]').eq(1).check();
    cy.get('[data-cy="budgetTagSelectorAdd"]').click();
    cy.get('[data-cy="budgetTagSelectorOption"]').contains(`Budget tag ${suffix}`).click();
    cy.get('input[name="tagMatchMode"]').eq(1).check();

    cy.get('[data-cy="submitBudget"]').click();
    cy.wait('@createBudget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body).to.include({ name: budgetName, type: 'EXPENSE', currency: 'MXN' });
      expect(request.body.initialRevision).to.deep.include({
        accountScopeMode: 'SELECTED',
        accountIds: [account.id],
        categoryScopeMode: 'SELECTED',
        categoryIds: [rootCategory.id],
        tagScopeMode: 'SELECTED',
        tagIds: [tag.id],
        tagMatchMode: 'ALL',
      });
      expect(request.body.initialRevision.categoryIds).not.to.include(childCategory.id);
    });
    cy.location('pathname').should('match', /^\/budgets\/\d+$/);
    cy.wait('@budgetOverview').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy^="budgetCard-"]', budgetName).within(() => {
      cy.contains(/Expense limit|Límite de gasto/).should('be.visible');
      cy.get('[data-cy="budgetActual"]').invoke('text').should('match', /0\.00/);
      cy.contains(/Within limit|Dentro del límite/).should('be.visible');
    });
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(document.documentElement.clientWidth));
    cy.screenshot('budget-5a-create-and-overview-desktop', { capture: 'viewport' });

    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('budget-5a-create-and-overview-mobile', { capture: 'viewport' });
  });

  it('creates each approved calendar mode through real React, REST, Spring, and PostgreSQL', () => {
    const base = (name: string) => {
      cy.visit('/budgets/new');
      cy.get('[data-cy="budgetName"]').type(name);
      cy.get('[data-cy="budgetTargetAmount"]').type('400');
      cy.get('[data-cy="budgetCurrency"]').select('MXN');
      cy.get('[data-cy="budgetIntervalCount"]').type('1');
      cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    };
    const submit = () => {
      cy.get('[data-cy="submitBudget"]').should('not.be.disabled').click();
      cy.wait('@createBudget').its('response.statusCode').should('eq', 201);
    };

    base(`Four day specific ${suffix}`);
    cy.get('[data-cy="budgetIntervalCount"]').clear();
    cy.get('[data-cy="budgetIntervalCount"]').type('4');
    cy.get('[data-cy="budgetIntervalUnit"]').select('DAY');
    cy.get('[data-cy="budgetStartDate"]').clear();
    cy.get('[data-cy="budgetStartDate"]').type('2026-03-11');
    cy.wait('@fourDayPreview').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ intervalCount: 4, intervalUnit: 'DAY', alignment: 'ANCHORED', anchorDate: '2026-03-11' });
    });
    submit();

    base(`Natural month ${suffix}`);
    cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    cy.get('[data-cy="budgetCalendarMode"]').select('NATURAL');
    cy.get('#budget-natural-month').clear();
    cy.get('#budget-natural-month').type('2026-03');
    cy.wait('@naturalMonthPreview').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ intervalUnit: 'MONTH', alignment: 'NATURAL', anchorDate: '2026-03-01' });
    });
    submit();

    base(`Custom Tuesday ${suffix}`);
    cy.get('[data-cy="budgetIntervalUnit"]').select('WEEK');
    cy.get('[data-cy="budgetCalendarMode"]').select('CUSTOM');
    cy.get('#budget-custom-week').clear();
    cy.get('#budget-custom-week').type('2026-W11');
    cy.get('#budget-custom-weekday').select('2');
    cy.wait('@customTuesdayPreview').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ intervalUnit: 'WEEK', alignment: 'CUSTOM', anchorDate: '2026-03-10', customDayOfMonth: null });
    });
    submit();

    base(`Custom thirty one ${suffix}`);
    cy.get('[data-cy="budgetIntervalUnit"]').select('MONTH');
    cy.get('[data-cy="budgetCalendarMode"]').select('CUSTOM');
    cy.get('#budget-custom-cycle-month').clear();
    cy.get('#budget-custom-cycle-month').type('2026-02');
    cy.get('#budget-custom-day').select('31');
    cy.wait('@customThirtyOnePreview').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ intervalUnit: 'MONTH', alignment: 'CUSTOM', anchorDate: '2026-02-28', customDayOfMonth: 31 });
      expect(response?.body.periods.map((period: { startInclusive: string }) => period.startInclusive)).to.include('2026-03-31');
    });
    cy.screenshot('budget-5a-1-calendar-modes-desktop', { capture: 'viewport' });
    cy.viewport('iphone-6');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(375));
    cy.screenshot('budget-5a-1-calendar-modes-mobile', { capture: 'viewport' });
    cy.viewport(1440, 960);
    cy.get('[data-cy="submitBudget"]').click();
    cy.wait('@createBudget').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      const id = response?.body.id;
      cy.authenticatedRequest({ method: 'GET', url: `/api/budgets/${id}/history` }).then(({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.revisions[0]).to.include({ alignment: 'CUSTOM', customDayOfMonth: 31, anchorDate: '2026-02-28' });
      });
    });
    cy.location('pathname').should('match', /^\/budgets\/\d+$/);
    cy.get('[data-cy="budgetReferenceDate"]').clear();
    cy.get('[data-cy="budgetReferenceDate"]').type('2026-03-15');
    cy.get('[data-cy="budgetReferenceToday"]').click();

    base(`One-time calendar ${suffix}`);
    cy.get('[data-cy="budgetCalendarPreview"] ol').should('be.visible');
    cy.get('#budget-recurrence').select('ONCE');
    cy.get('[data-cy="budgetCalendarPreview"] ol').should('be.visible');
    cy.get('[data-cy="submitBudget"]').should('not.be.disabled').click();
    cy.wait('@createBudget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.initialRevision).to.deep.include({ recurrenceMode: 'ONCE', termination: 'NONE' });
    });

    base(`Finite calendar ${suffix}`);
    cy.get('[data-cy="budgetCalendarPreview"] ol').should('be.visible');
    cy.get('#budget-termination').select('AFTER_N_PERIODS');
    cy.get('#budget-termination-count').type('2');
    cy.get('[data-cy="budgetCalendarPreview"] ol').should('be.visible');
    cy.get('[data-cy="submitBudget"]').should('not.be.disabled').click();
    cy.wait('@createBudget').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.initialRevision).to.deep.include({
        recurrenceMode: 'RECURRING',
        termination: 'AFTER_N_PERIODS',
        terminationPeriodCount: 2,
      });
    });
  });

  it('updates a selected-scope expense only when its persisted transaction matches and reaches the exact 120 percent state', () => {
    const fixture = `${suffix}-${Cypress._.uniqueId('expense-scope')}`;
    const budgetName = `Browser scope mutations ${fixture}`;
    const referenceDate = '2026-03-15';

    // This is a real persisted ledger movement, not an intercepted Budget or Analytics response. It starts without
    // the selected tag, then changes to matching, non-matching and again matching through the normal REST boundary.
    return cy
      .authenticatedRequest<{ id: number }>({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: referenceDate,
          postingDate: referenceDate,
          description: `Budget actual expense ${suffix}`,
          amount: 300,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: isolatedAccount.id },
          category: { id: isolatedExpenseChild.id },
        },
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        const transactionId = body.id;
        return cy
          .authenticatedRequest({
            method: 'POST',
            url: '/api/budgets',
            body: {
              name: budgetName,
              description: 'Real 120 percent evaluation fixture',
              type: 'EXPENSE',
              currency: 'MXN',
              clientRequestId: `budget-actual-${fixture}`,
              timeZone: 'America/Mexico_City',
              initialRevision: {
                targetAmount: 3000,
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
                accountIds: [isolatedAccount.id],
                categoryScopeMode: 'SELECTED',
                categoryIds: [isolatedExpenseRoot.id],
                tagScopeMode: 'SELECTED',
                tagIds: [isolatedExpenseTag.id],
                tagMatchMode: 'ALL',
              },
            },
          })
          .then(({ status }) => {
            expect(status).to.equal(201);
            return transactionId;
          });
      })
      .then(transactionId => {
        cy.visit(`/budgets?referenceDate=${referenceDate}`);
        cy.wait('@budgetOverview').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
          const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
          expect(item?.evaluation).to.include({ actualAmount: 0, progressPercentage: 0, goalStatus: 'FINISHED_WITHIN_LIMIT' });
        });
        cy.contains('[data-cy^="budgetCard-"]', budgetName).within(() => {
          cy.get('[data-cy="budgetActual"]').should('contain.text', '0.00');
        });

        cy.authenticatedRequest({
          method: 'PATCH',
          url: `/api/financial-transactions/${transactionId}`,
          body: { tags: [{ id: isolatedExpenseTag.id }] },
        }).then(({ status }) => expect(status).to.equal(200));
        cy.reload();
        cy.wait('@budgetOverview').then(({ response }) => {
          const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
          expect(item?.evaluation).to.include({ actualAmount: 300, progressPercentage: 10, goalStatus: 'FINISHED_WITHIN_LIMIT' });
        });

        cy.authenticatedRequest({
          method: 'PATCH',
          url: `/api/financial-transactions/${transactionId}`,
          body: { tags: [] },
        }).then(({ status }) => expect(status).to.equal(200));
        cy.reload();
        cy.wait('@budgetOverview').then(({ response }) => {
          const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
          expect(item?.evaluation.actualAmount).to.equal(0);
        });

        cy.authenticatedRequest({
          method: 'PATCH',
          url: `/api/financial-transactions/${transactionId}`,
          body: { amount: 3600, tags: [{ id: isolatedExpenseTag.id }] },
        }).then(({ status }) => expect(status).to.equal(200));
        cy.reload();
        cy.wait('@budgetOverview').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
          const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
          expect(item?.evaluation).to.include({ actualAmount: 3600, progressPercentage: 120, goalStatus: 'EXCEEDED' });
        });
        cy.contains('[data-cy^="budgetCard-"]', budgetName).within(() => {
          cy.get('[data-cy="budgetActual"]').should('contain.text', '3,600.00');
          cy.contains(/Limit exceeded|Límite excedido/).should('be.visible');
          cy.contains('120.00%').should('be.visible');
        });
      });
  });

  it('evaluates an income objective from real income only and excludes both expenses and linked transfer legs after reload', () => {
    const fixture = `${suffix}-${Cypress._.uniqueId('income-scope')}`;
    const budgetName = `Browser income objective ${fixture}`;
    const referenceDate = '2026-03-15';
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-transactions',
      body: {
        transactionDate: referenceDate,
        postingDate: referenceDate,
        description: `Budget actual income ${suffix}`,
        amount: 800,
        flow: 'IN',
        origin: 'MANUAL',
        account: { id: isolatedIncomeAccount.id },
        category: { id: isolatedIncomeCategory.id },
      },
    })
      .its('status')
      .should('eq', 201);
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-transactions',
      body: {
        transactionDate: referenceDate,
        postingDate: referenceDate,
        description: `Budget ignored expense ${suffix}`,
        amount: 500,
        flow: 'OUT',
        origin: 'MANUAL',
        account: { id: isolatedIncomeAccount.id },
        category: { id: isolatedExpenseChild.id },
      },
    })
      .its('status')
      .should('eq', 201);
    cy.then(() =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/internal-transfers/commands/create',
          body: {
            sourceAccountId: isolatedIncomeAccount.id,
            destinationAccountId: isolatedTransferDestination.id,
            amount: 999,
            transactionDate: referenceDate,
            sourcePostingDate: referenceDate,
            destinationPostingDate: referenceDate,
            sourceDescription: `Budget transfer out ${suffix}`,
            destinationDescription: `Budget transfer in ${suffix}`,
          },
        })
        .its('status')
        .should('eq', 201),
    );
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/budgets',
      body: {
        name: budgetName,
        type: 'INCOME',
        currency: 'MXN',
        clientRequestId: `budget-income-${suffix}`,
        timeZone: 'America/Mexico_City',
        initialRevision: {
          targetAmount: 1000,
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
          accountIds: [isolatedIncomeAccount.id, isolatedTransferDestination.id],
          categoryScopeMode: 'ALL',
          categoryIds: [],
          tagScopeMode: 'NONE',
          tagIds: [],
          tagMatchMode: 'ANY',
        },
      },
    })
      .its('status')
      .should('eq', 201);

    cy.visit(`/budgets?referenceDate=${referenceDate}`);
    cy.wait('@budgetOverview').then(({ response }) => {
      const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
      expect(item?.evaluation).to.include({ actualAmount: 800, progressPercentage: 80, goalStatus: 'GOAL_NOT_MET' });
    });
    cy.contains('[data-cy^="budgetCard-"]', budgetName).within(() => {
      cy.get('[data-cy="budgetActual"]').should('contain.text', '800.00');
      cy.contains(/Goal not met|Meta no alcanzada/).should('be.visible');
    });
  });

  it('navigates recurring periods and applies objective filters through the real overview endpoint', () => {
    const fixture = `${suffix}-${Cypress._.uniqueId('period-navigation')}`;
    const budgetName = `Browser period navigation ${fixture}`;
    const march = '2026-03-15';
    const april = '2026-04-15';
    [
      { date: march, amount: 100, description: `Budget March ${suffix}` },
      { date: april, amount: 200, description: `Budget April ${suffix}` },
    ].forEach(fixture => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate: fixture.date,
          postingDate: fixture.date,
          description: fixture.description,
          amount: fixture.amount,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: isolatedAccount.id },
          category: { id: isolatedExpenseChild.id },
        },
      })
        .its('status')
        .should('eq', 201);
    });
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/budgets',
      body: {
        name: budgetName,
        type: 'EXPENSE',
        currency: 'MXN',
        clientRequestId: `budget-period-navigation-${suffix}`,
        timeZone: 'America/Mexico_City',
        initialRevision: {
          targetAmount: 1000,
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
          accountIds: [isolatedAccount.id],
          categoryScopeMode: 'SELECTED',
          categoryIds: [isolatedExpenseRoot.id],
          tagScopeMode: 'NONE',
          tagIds: [],
          tagMatchMode: 'ANY',
        },
      },
    })
      .its('status')
      .should('eq', 201);

    cy.visit(`/budgets?referenceDate=${march}`);
    cy.wait('@budgetOverview').then(({ response }) => {
      const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
      expect(item?.evaluation).to.include({ startInclusive: '2026-03-01', endExclusive: '2026-04-01', actualAmount: 100 });
    });
    cy.intercept('GET', '/api/budgets/overview*', request => {
      if (request.query.referenceDate === april) request.alias = 'aprilBudgetOverview';
    });
    cy.get('[data-cy="budgetReferenceDate"]').clear();
    cy.get('[data-cy="budgetReferenceDate"]').type(april);
    cy.wait('@aprilBudgetOverview').then(({ request, response }) => {
      expect(request.query.referenceDate).to.equal(april);
      const item = response?.body.budgets.find((candidate: { name: string }) => candidate.name === budgetName);
      expect(item?.evaluation).to.include({ startInclusive: '2026-04-01', endExclusive: '2026-05-01', actualAmount: 200 });
    });
    cy.intercept('GET', '/api/budgets/overview*', request => {
      if (request.query.type === 'EXPENSE') request.alias = 'expenseBudgetOverview';
    });
    cy.get('#budget-type-filter').select('EXPENSE');
    cy.wait('@expenseBudgetOverview').then(({ request }) => expect(request.query.type).to.equal('EXPENSE'));
    cy.contains('[data-cy^="budgetCard-"]', budgetName).should('be.visible');
    cy.intercept('GET', '/api/budgets/overview*', request => {
      if (request.query.referenceDate !== april && request.query.type === 'EXPENSE') request.alias = 'todayBudgetOverview';
    });
    cy.get('[data-cy="budgetReferenceToday"]').click();
    cy.wait('@todayBudgetOverview').its('response.statusCode').should('eq', 200);
  });
});
