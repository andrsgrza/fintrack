/**
 * BUDGET-6C-B real browser acceptance. Each named phase is deliberately run
 * against an isolated Spring Boot process and a disposable PostgreSQL database.
 * Cypress observes real HTTP traffic only: it never stubs profile, budget or
 * financial responses.
 */
describe('Budget financial time zone complete real acceptance', { retries: 0 }, () => {
  type Profile = {
    timeZone: string | null;
    configurationRequired: boolean;
    effectiveChanges: Array<{ budgetId: number; budgetName: string; effectivePeriodStart: string }>;
  };
  type Reference = { id: number; name: string };
  type CreatedBudget = { id: number; version: number; name: string };
  type Evaluation = {
    budgetId: number;
    periodIndex: number;
    startInclusive: string;
    endExclusive: string;
    actualAmount: string | null;
    operationalStatus: string;
    evaluationStatus: string;
  };
  type Overview = { budgets: Array<{ budgetId: number; name: string; evaluation: Evaluation | null }> };
  type Preference = {
    configured: boolean;
    version: number | null;
    percentageAlertsEnabled: boolean;
    thresholdPercentages: number[];
    periodCloseEnabled: boolean;
    periodCloseEnabledSince: string | null;
  };
  type NotificationPage = {
    totalElements: number;
    notifications: Array<{ id: number; readAt: string | null; snapshot: unknown }>;
  };

  const owner = Cypress.env('E2E_USERNAME') ?? 'user';
  const ownerPassword = Cypress.env('E2E_PASSWORD') ?? 'user';
  const otherUser = Cypress.env('E2E_OTHER_USERNAME') ?? 'admin';
  const otherPassword = Cypress.env('E2E_OTHER_PASSWORD') ?? 'admin';
  const phase = Cypress.env('BUDGET_TIME_ZONE_ACCEPTANCE_PHASE');
  const fixture = Cypress.env('BUDGET_TIME_ZONE_ACCEPTANCE_FIXTURE') ?? 'budget-6cb-time-zone';
  const mexicoCity = 'America/Mexico_City';
  const losAngeles = 'America/Los_Angeles';
  const newYork = 'America/New_York';
  const utc = 'UTC';

  const phaseIt = (expectedPhase: string, title: string, body: () => void) => {
    if (phase === expectedPhase) it(title, body);
  };

  const setProfile = (timeZone: string) =>
    cy
      .authenticatedRequest<Profile>({ method: 'PUT', url: '/api/account/budget-time-zone', body: { timeZone } })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        expect(body.timeZone).to.equal(timeZone);
        return body;
      });

  const profile = () =>
    cy.authenticatedRequest<Profile>({ method: 'GET', url: '/api/account/budget-time-zone' }).then(({ body, status }) => {
      expect(status).to.equal(200);
      return body;
    });

  const createAccountAndCategory = (suffix: string) => {
    let account: Reference;
    return cy
      .authenticatedRequest<Reference>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name: `Timezone account ${suffix}`,
          institutionName: 'Timezone acceptance bank',
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
          body: { name: `Timezone category ${suffix}`, categoryType: 'EXPENSE', color: '#3178a8', active: true },
        });
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return { account, category: body };
      });
  };

  const createExpense = (accountId: number, categoryId: number, transactionDate: string, amount: number, suffix: string) =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/financial-transactions',
        body: {
          transactionDate,
          postingDate: transactionDate,
          description: `Timezone acceptance expense ${suffix}`,
          amount,
          flow: 'OUT',
          origin: 'MANUAL',
          account: { id: accountId },
          category: { id: categoryId },
        },
      })
      .its('status')
      .should('eq', 201);

  const budgetPayload = (
    name: string,
    anchorDate: string,
    intervalUnit: 'DAY' | 'MONTH',
    intervalCount: number,
    recurrenceMode: 'RECURRING' | 'ONCE' = 'RECURRING',
    scope?: { accountId: number; categoryId: number },
  ) => ({
    name,
    description: 'BUDGET-6C-B disposable real-browser acceptance fixture',
    type: 'EXPENSE',
    currency: 'MXN',
    clientRequestId: `${name.replaceAll(' ', '-').toLowerCase()}-create`,
    timeZone: mexicoCity,
    initialRevision: {
      targetAmount: 100,
      intervalCount,
      intervalUnit,
      alignment: 'ANCHORED',
      anchorDate,
      customDayOfMonth: null,
      recurrenceMode,
      termination: 'NONE',
      terminationPeriodCount: null,
      recurrenceEndDate: null,
      accountScopeMode: scope ? 'SELECTED' : 'ALL',
      accountIds: scope ? [scope.accountId] : [],
      categoryScopeMode: scope ? 'SELECTED' : 'ALL',
      categoryIds: scope ? [scope.categoryId] : [],
      tagScopeMode: 'NONE',
      tagIds: [],
      tagMatchMode: 'ANY',
    },
  });

  const createBudget = (
    name: string,
    anchorDate: string,
    intervalUnit: 'DAY' | 'MONTH',
    intervalCount: number,
    recurrenceMode: 'RECURRING' | 'ONCE' = 'RECURRING',
    scope?: { accountId: number; categoryId: number },
  ) =>
    cy
      .authenticatedRequest<CreatedBudget>({
        method: 'POST',
        url: '/api/budgets',
        body: budgetPayload(name, anchorDate, intervalUnit, intervalCount, recurrenceMode, scope),
      })
      .then(({ body, status }) => {
        expect(status).to.equal(201);
        return body;
      });

  const currentEvaluation = (budgetId: number, legacyTimeZone?: string) =>
    cy
      .authenticatedRequest<Evaluation>({
        method: 'GET',
        url: `/api/budgets/${budgetId}/evaluation/current`,
        qs: legacyTimeZone ? { timeZone: legacyTimeZone } : undefined,
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        return body;
      });

  const periodEvaluation = (budgetId: number, periodIndex: number, legacyTimeZone?: string) =>
    cy
      .authenticatedRequest<Evaluation>({
        method: 'GET',
        url: `/api/budgets/${budgetId}/evaluation/period/${periodIndex}`,
        qs: legacyTimeZone ? { timeZone: legacyTimeZone } : undefined,
      })
      .then(({ body, status }) => {
        expect(status).to.equal(200);
        return body;
      });

  const findBudget = (name: string) =>
    cy.authenticatedRequest<Overview>({ method: 'GET', url: '/api/budgets/overview' }).then(({ body, status }) => {
      expect(status).to.equal(200);
      const budget = body.budgets.find(candidate => candidate.name === name);
      if (!budget) throw new Error(`Missing disposable budget ${name}.`);
      return budget;
    });

  const saveThroughSettings = (timeZone: string) => {
    cy.intercept('PUT', '/api/account/budget-time-zone').as('saveBudgetTimeZone');
    cy.visit('/account/settings');
    cy.get('[data-cy="budgetTimeZoneInput"]').clear();
    cy.get('[data-cy="budgetTimeZoneInput"]').type(timeZone);
    cy.get('[data-cy="saveBudgetTimeZone"]').click();
    return cy.wait('@saveBudgetTimeZone').then(({ request, response }) => {
      expect(request.body).to.deep.equal({ timeZone });
      expect(response?.statusCode).to.equal(200);
      return response?.body as Profile;
    });
  };

  const browserTimeZone = (timeZone: string) =>
    cy
      .then(() =>
        Cypress.automation('remote:debugger:protocol', {
          command: 'Emulation.setTimezoneOverride',
          params: { timezoneId: timeZone },
        }),
      )
      .then(() => cy.window().then(window => expect(window.Intl.DateTimeFormat().resolvedOptions().timeZone).to.equal(timeZone)));

  phaseIt('B', 'keeps the persisted financial zone and period when the actual browser reports another IANA zone', () => {
    const budgetName = `Timezone browser independence ${fixture}`;
    cy.login(owner, ownerPassword);
    setProfile(mexicoCity);
    createAccountAndCategory(`${fixture}-browser`).then(({ account, category }) => {
      createExpense(account.id, category.id, '2026-03-15', 24, `${fixture}-browser`);
      createBudget(budgetName, '2026-03-15', 'DAY', 1, 'RECURRING', { accountId: account.id, categoryId: category.id }).then(budget => {
        currentEvaluation(budget.id, utc).then(authoritative => {
          expect(authoritative.startInclusive).to.equal('2026-03-15');
          expect(authoritative.actualAmount).to.equal('24.00');
          browserTimeZone(newYork);
          cy.intercept('PUT', '/api/account/budget-time-zone').as('unexpectedZoneWrite');
          cy.visit('/account/settings');
          cy.get('[data-cy="budgetTimeZoneInput"]').should('have.value', mexicoCity);
          cy.get('@unexpectedZoneWrite.all').should('have.length', 0);
          cy.visit('/budgets');
          cy.contains(budgetName).should('be.visible');
          cy.visit(`/budgets/${budget.id}`);
          cy.get('[data-cy="budgetDetailHeading"]').should('contain.text', budgetName);
          currentEvaluation(budget.id, newYork).then(conflictingClientZone => {
            expect(conflictingClientZone).to.deep.equal(authoritative);
          });
          cy.reload();
          profile().then(current => expect(current.timeZone).to.equal(mexicoCity));
          currentEvaluation(budget.id, newYork).then(afterReload => expect(afterReload).to.deep.equal(authoritative));
          cy.get('@unexpectedZoneWrite.all').should('have.length', 0);
        });
      });
    });
  });

  phaseIt('C_SETUP', 'preserves the current period while replacing a pending A to B transition with C through the settings UI', () => {
    const budgetName = `Timezone historical ${fixture}`;
    cy.login(owner, ownerPassword);
    setProfile(mexicoCity);
    createAccountAndCategory(`${fixture}-historical`).then(({ account, category }) => {
      createExpense(account.id, category.id, '2026-03-15', 50, `${fixture}-historical-a`);
      createExpense(account.id, category.id, '2026-03-16', 70, `${fixture}-historical-c`);
      createBudget(budgetName, '2026-03-15', 'DAY', 1, 'RECURRING', { accountId: account.id, categoryId: category.id }).then(budget => {
        periodEvaluation(budget.id, 0).then(before => {
          expect(before).to.include({ periodIndex: 0, startInclusive: '2026-03-15', endExclusive: '2026-03-16', actualAmount: '50.00' });
        });
        saveThroughSettings(losAngeles).then(firstChange => {
          expect(firstChange.timeZone).to.equal(losAngeles);
          expect(firstChange.effectiveChanges).to.deep.include({ budgetId: budget.id, budgetName, effectivePeriodStart: '2026-03-16' });
        });
        currentEvaluation(budget.id, utc).then(current => {
          expect(current).to.include({ periodIndex: 0, startInclusive: '2026-03-15', actualAmount: '50.00' });
        });
        saveThroughSettings(utc).then(secondChange => {
          expect(secondChange.timeZone).to.equal(utc);
          expect(secondChange.effectiveChanges).to.deep.include({ budgetId: budget.id, budgetName, effectivePeriodStart: '2026-03-16' });
        });
        profile().then(current => expect(current.timeZone).to.equal(utc));
        currentEvaluation(budget.id, newYork).then(current => {
          expect(current).to.include({ periodIndex: 0, startInclusive: '2026-03-15', actualAmount: '50.00' });
        });
      });
    });
  });

  phaseIt(
    'C_ADVANCE',
    'uses only C after the next boundary while retaining historical A financial results after an isolated clock advance',
    () => {
      const budgetName = `Timezone historical ${fixture}`;
      cy.login(owner, ownerPassword);
      findBudget(budgetName).then(budget => {
        const budgetId = budget.budgetId;
        currentEvaluation(budgetId, losAngeles).then(current => {
          // At 07:00Z, Mexico has crossed 16 March. If the obsolete Los Angeles B assignment remained,
          // its local date would still be 15 March and this would incorrectly resolve period zero.
          expect(current).to.include({ periodIndex: 1, startInclusive: '2026-03-16', endExclusive: '2026-03-17', actualAmount: '70.00' });
        });
        periodEvaluation(budgetId, 0, newYork).then(historical => {
          expect(historical).to.include({
            periodIndex: 0,
            startInclusive: '2026-03-15',
            endExclusive: '2026-03-16',
            actualAmount: '50.00',
          });
        });
        cy.visit('/budgets');
        cy.contains(budgetName).should('be.visible');
        cy.visit(`/budgets/${budgetId}?referenceDate=2026-03-15`);
        cy.get('[data-cy="budgetDetailHeading"]').should('contain.text', budgetName);
        cy.reload();
        currentEvaluation(budgetId, newYork).then(reloaded => {
          expect(reloaded).to.include({ periodIndex: 1, startInclusive: '2026-03-16', actualAmount: '70.00' });
        });
      });
    },
  );

  phaseIt(
    'D_SCHEDULE',
    'applies one profile change at each active or paused Budget boundary without treating future finish or once budgets as future calendars',
    () => {
      const monthlyName = `Timezone monthly ${fixture}`;
      const quarterlyName = `Timezone quarterly ${fixture}`;
      const pausedName = `Timezone paused ${fixture}`;
      const finishName = `Timezone finish ${fixture}`;
      const onceName = `Timezone once ${fixture}`;
      cy.login(owner, ownerPassword);
      setProfile(mexicoCity);
      createBudget(monthlyName, '2026-03-10', 'MONTH', 1).then(monthly => {
        createBudget(quarterlyName, '2026-02-01', 'MONTH', 3).then(quarterly => {
          createBudget(pausedName, '2026-03-15', 'DAY', 1).then(paused => {
            cy.authenticatedRequest({
              method: 'POST',
              url: `/api/budgets/${paused.id}/pause`,
              body: { expectedVersion: paused.version, clientRequestId: `${fixture}-pause`, timeZone: mexicoCity },
            });
            createBudget(finishName, '2026-03-15', 'DAY', 1).then(finished => {
              cy.authenticatedRequest({
                method: 'POST',
                url: `/api/budgets/${finished.id}/finish`,
                body: { expectedVersion: finished.version, clientRequestId: `${fixture}-finish`, timeZone: mexicoCity },
              });
              createBudget(onceName, '2026-03-15', 'DAY', 1, 'ONCE').then(once => {
                setProfile(utc).then(change => {
                  expect(change.effectiveChanges).to.deep.include.members([
                    { budgetId: monthly.id, budgetName: monthlyName, effectivePeriodStart: '2026-04-10' },
                    { budgetId: quarterly.id, budgetName: quarterlyName, effectivePeriodStart: '2026-05-01' },
                    { budgetId: paused.id, budgetName: pausedName, effectivePeriodStart: '2026-03-16' },
                  ]);
                  expect(change.effectiveChanges.map(entry => entry.budgetId)).not.to.include(finished.id);
                  expect(change.effectiveChanges.map(entry => entry.budgetId)).not.to.include(once.id);
                });
              });
            });
          });
        });
      });
    },
  );

  phaseIt('D_BOUNDARY', 'keeps paused periods paused and lets terminal budgets accept profile updates without inventing periods', () => {
    const pausedName = `Timezone paused ${fixture}`;
    const finishName = `Timezone finish ${fixture}`;
    const onceName = `Timezone once ${fixture}`;
    cy.login(owner, ownerPassword);
    findBudget(pausedName).then(paused => {
      currentEvaluation(paused.budgetId).then(result => {
        expect(result).to.include({ periodIndex: 1, operationalStatus: 'PAUSED', evaluationStatus: 'NOT_EVALUATED' });
      });
    });
    findBudget(finishName).then(finished => {
      currentEvaluation(finished.budgetId).then(result => {
        expect(result.operationalStatus).to.equal('COMPLETED');
      });
    });
    findBudget(onceName).then(once => {
      currentEvaluation(once.budgetId).then(result => {
        expect(result.operationalStatus).to.equal('COMPLETED');
      });
    });
    setProfile(newYork).then(change => {
      expect(change.timeZone).to.equal(newYork);
      expect(change.effectiveChanges.map(entry => entry.budgetName)).not.to.include.members([finishName, onceName]);
    });
  });

  phaseIt('D_DST_PRE', 'keeps the New York daily period stable across the real daylight-saving clock jump before its next boundary', () => {
    const budgetName = `Timezone dst ${fixture}`;
    cy.login(owner, ownerPassword);
    setProfile(newYork);
    createBudget(budgetName, '2026-03-08', 'DAY', 1).then(budget => {
      currentEvaluation(budget.id).then(result => {
        expect(result).to.include({ periodIndex: 0, startInclusive: '2026-03-08', endExclusive: '2026-03-09' });
      });
      setProfile(utc).then(change => {
        expect(change.effectiveChanges).to.deep.include({ budgetId: budget.id, budgetName, effectivePeriodStart: '2026-03-09' });
      });
    });
  });

  phaseIt('D_DST_POST_JUMP', 'does not turn the daylight-saving clock jump itself into an extra Budget period', () => {
    const budgetName = `Timezone dst ${fixture}`;
    cy.login(owner, ownerPassword);
    findBudget(budgetName).then(budget => {
      currentEvaluation(budget.budgetId).then(result => {
        expect(result).to.include({ periodIndex: 0, startInclusive: '2026-03-08', endExclusive: '2026-03-09' });
      });
    });
  });

  phaseIt('D_DST_NEXT_BOUNDARY', 'switches to UTC only at the actual post-DST next calendar boundary', () => {
    const budgetName = `Timezone dst ${fixture}`;
    cy.login(owner, ownerPassword);
    findBudget(budgetName).then(budget => {
      currentEvaluation(budget.budgetId, newYork).then(result => {
        expect(result).to.include({ periodIndex: 1, startInclusive: '2026-03-09', endExclusive: '2026-03-10' });
      });
    });
  });

  phaseIt('E', 'enforces ownership and rejects invalid or unauthenticated writes without changing Budget or notification state', () => {
    const budgetName = `Timezone security ${fixture}`;
    cy.login(owner, ownerPassword);
    setProfile(mexicoCity);
    createBudget(budgetName, '2026-03-15', 'DAY', 1).then(budget => {
      currentEvaluation(budget.id).then(beforeEvaluation => {
        cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${budget.id}/notification-preferences` }).then(
          ({ body: beforePreferences, status }) => {
            expect(status).to.equal(200);
            cy.authenticatedRequest<NotificationPage>({ method: 'GET', url: '/api/notifications', qs: { page: 0, size: 100 } }).then(
              ({ body: beforeNotifications, status: notificationStatus }) => {
                expect(notificationStatus).to.equal(200);
                setProfile(utc).then(() => {
                  profile().then(current => expect(current.timeZone).to.equal(utc));
                  currentEvaluation(budget.id).then(afterOwnChange => expect(afterOwnChange).to.deep.equal(beforeEvaluation));
                  cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${budget.id}/notification-preferences` }).then(
                    ({ body: afterOwnChangePreferences, status: preferenceStatus }) => {
                      expect(preferenceStatus).to.equal(200);
                      expect(afterOwnChangePreferences).to.deep.equal(beforePreferences);
                    },
                  );
                  cy.authenticatedRequest<NotificationPage>({ method: 'GET', url: '/api/notifications', qs: { page: 0, size: 100 } }).then(
                    ({ body: afterOwnChangeNotifications, status: afterNotificationStatus }) => {
                      expect(afterNotificationStatus).to.equal(200);
                      expect(afterOwnChangeNotifications).to.deep.equal(beforeNotifications);
                    },
                  );
                  cy.login(otherUser, otherPassword);
                  cy.visit('/account/settings');
                  cy.get('[data-cy="budgetTimeZoneSettings"]').should('be.visible');
                  setProfile(newYork);
                  cy.authenticatedRequest({
                    method: 'GET',
                    url: `/api/budgets/${budget.id}/evaluation/current`,
                    failOnStatusCode: false,
                  }).then(response => expect(response.status).to.equal(404));
                  cy.login(owner, ownerPassword);
                  profile().then(current => expect(current.timeZone).to.equal(utc));
                  currentEvaluation(budget.id).then(afterOtherUser => expect(afterOtherUser).to.deep.equal(beforeEvaluation));
                  cy.authenticatedRequest<Preference>({ method: 'GET', url: `/api/budgets/${budget.id}/notification-preferences` }).then(
                    ({ body: afterOtherUserPreferences, status: preferenceStatus }) => {
                      expect(preferenceStatus).to.equal(200);
                      expect(afterOtherUserPreferences).to.deep.equal(beforePreferences);
                    },
                  );
                  [null, '', 'America/Not_A_Zone', 'not/a/timezone'].forEach(timeZone => {
                    cy.authenticatedRequest({
                      method: 'PUT',
                      url: '/api/account/budget-time-zone',
                      failOnStatusCode: false,
                      body: { timeZone },
                    }).then(response => expect(response.status).to.equal(400));
                  });
                  profile().then(current => expect(current.timeZone).to.equal(utc));
                  currentEvaluation(budget.id).then(afterInvalid => expect(afterInvalid).to.deep.equal(beforeEvaluation));
                  cy.request({
                    method: 'PUT',
                    url: '/api/account/budget-time-zone',
                    failOnStatusCode: false,
                    body: { timeZone: utc },
                  }).then(response => expect(response.status).to.equal(401));
                });
              },
            );
          },
        );
      });
    });
  });
});
