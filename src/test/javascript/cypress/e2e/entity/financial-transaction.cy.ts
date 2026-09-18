import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('FinancialTransaction e2e test', () => {
  const financialTransactionPageUrl = '/financial-transaction';
  const financialTransactionPageUrlPattern = new RegExp('/financial-transaction(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let financialTransaction;
  let financialAccount;
  let category;
  let tag;
  let transactionRule;
  let manualCandidateDescription;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const buildFinancialAccountPayload = (name: string) => ({
    name,
    institutionName: 'E2E Bank',
    accountType: 'DEBIT',
    currency: 'MXN',
    initialBalance: 1000,
    initialBalanceDate: '2026-07-08',
    active: true,
  });

  const buildFinancialTransactionPayload = (accountId: number) => ({
    transactionDate: '2026-07-08',
    description: 'E2E transaction',
    amount: 100.5,
    flow: 'OUT',
    origin: 'MANUAL',
    account: { id: accountId },
  });

  const waitForManualCandidate = (
    predicate: (candidate: {
      description?: string;
      signedAmount?: number | string;
      notes?: string | null;
      classificationReviewStatus?: string | null;
      category?: { id?: number | string } | null;
      tags?: Array<{ id?: number | string }>;
    }) => boolean,
    attemptsRemaining = 25,
  ) =>
    cy.location('pathname').then(pathname => {
      const candidateId = pathname.match(/\/financial-transaction\/drafts\/(\d+)$/)?.[1];
      expect(candidateId, 'manual draft id in URL').to.exist;

      return cy
        .authenticatedRequest({
          method: 'GET',
          url: `/api/transaction-candidates/${candidateId}`,
        })
        .then(({ body }) => {
          if (!predicate(body)) {
            expect(attemptsRemaining, 'manual candidate save attempts remaining').to.be.greaterThan(0);
            cy.wait(250); // eslint-disable-line cypress/no-unnecessary-waiting
            return waitForManualCandidate(predicate, attemptsRemaining - 1);
          }

          return body;
        });
    });

  const waitForSavedManualCandidateDraft = (description: string) =>
    waitForManualCandidate(candidate => candidate.description === description && Number(candidate.signedAmount) === -100.5);

  const waitForSavedManualCandidateDescription = (description: string) =>
    waitForManualCandidate(candidate => candidate.description === description);

  const createCategory = () =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/categories',
        body: {
          name: uniqueName('manual-candidate-category'),
          description: 'Manual candidate E2E category',
          categoryType: 'EXPENSE',
          color: '#336699',
          active: true,
        },
      })
      .then(({ body }) => {
        category = body;
        return body;
      });

  const createTag = () =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/tags',
        body: {
          name: uniqueName('manual-candidate-tag'),
          description: 'Manual candidate E2E tag',
          color: '#663399',
          active: true,
        },
      })
      .then(({ body }) => {
        tag = body;
        return body;
      });

  const createRuleSuggestionSeed = () => {
    manualCandidateDescription = uniqueName('manual-candidate-description');

    return createCategory().then(createdCategory =>
      createTag().then(createdTag =>
        cy
          .authenticatedRequest({
            method: 'POST',
            url: '/api/transaction-rules/configured',
            body: {
              name: uniqueName('manual-candidate-rule'),
              description: 'Manual candidate E2E rule',
              conditionLogic: 'ALL',
              active: true,
              resultingCategory: { id: createdCategory.id },
              resultingTags: [{ id: createdTag.id }],
              conditions: [
                {
                  field: 'FLOW',
                  operator: 'EQUALS',
                  value: 'OUT',
                  secondValue: null,
                  caseSensitive: false,
                },
                {
                  field: 'DESCRIPTION',
                  operator: 'CONTAINS',
                  value: manualCandidateDescription,
                  secondValue: null,
                  caseSensitive: false,
                },
              ],
            },
          })
          .then(({ body }) => {
            transactionRule = body;
            return body;
          }),
      ),
    );
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: buildFinancialAccountPayload(`tx-account-${Date.now()}`),
    }).then(({ body }) => {
      financialAccount = body;
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-transactions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/transaction-candidates/manual').as('createManualCandidateRequest');
    cy.intercept('GET', '/api/transaction-candidates/manual-drafts').as('manualDraftsRequest');
    cy.intercept('GET', '/api/transaction-candidates/*').as('getManualCandidateRequest');
    cy.intercept('PATCH', '/api/transaction-candidates/*/manual-draft').as('patchManualCandidateRequest');
    cy.intercept('POST', '/api/transaction-candidates/*/post').as('postManualCandidateRequest');
    cy.intercept('POST', '/api/transaction-candidates/*/cancel').as('cancelManualCandidateRequest');
    cy.intercept('POST', '/api/transaction-candidates/*/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-candidates/*/apply-rules').as('candidateApplyRulesRequest');
    cy.intercept('POST', '/api/financial-transactions/rule-preview').as('rulePreviewRequest');
    cy.intercept('DELETE', '/api/financial-transactions/*').as('deleteEntityRequest');
    cy.intercept('GET', '/api/financial-accounts/selectable*').as('selectableAccountsRequest');
    cy.intercept('GET', '/api/categories/selectable*', request => {
      request.alias = request.query.includeId ? 'currentCategoryRequest' : 'categoriesRequest';
    });
    cy.intercept('GET', '/api/tags/selectable*', request => {
      request.alias = request.query.includeId ? 'currentTagRequest' : 'tagsRequest';
    });
  });

  afterEach(() => {
    if (financialTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${financialTransaction.id}`,
        failOnStatusCode: false,
      }).then(() => {
        financialTransaction = undefined;
      });
    }
  });

  afterEach(() => {
    if (transactionRule) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${transactionRule.id}`,
        failOnStatusCode: false,
      }).then(() => {
        transactionRule = undefined;
      });
    }
    if (tag) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/tags/${tag.id}`,
        failOnStatusCode: false,
      }).then(() => {
        tag = undefined;
      });
    }
    if (category) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/categories/${category.id}`,
        failOnStatusCode: false,
      }).then(() => {
        category = undefined;
      });
    }
  });

  afterEach(() => {
    if (financialAccount) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${financialAccount.id}`,
        failOnStatusCode: false,
      }).then(() => {
        financialAccount = undefined;
      });
    }
  });

  it('FinancialTransactions menu should load FinancialTransactions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('financial-transaction');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FinancialTransaction').should('exist');
    cy.url().should('match', financialTransactionPageUrlPattern);
  });

  describe('FinancialTransaction page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialTransactionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load manual candidate draft create page without creating a candidate on page load', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-transaction/new$'));
        cy.get('[data-cy="FinancialTransactionManualDraftHeading"]').should('exist');
        cy.get('@createManualCandidateRequest.all').should('have.length', 0);
        cy.get('@rulePreviewRequest.all').should('have.length', 0);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(financialAccount.id),
        }).then(({ body }) => {
          financialTransaction = body;
        });

        cy.visit(financialTransactionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('detail button click should load details FinancialTransaction page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('financialTransaction');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('edit button click should load posted FinancialTransaction edit page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialTransaction');
        cy.get('[data-cy="entityCreateSaveButton"]').should('exist');
        cy.get('[data-cy="entityCreateCancelButton"]').click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('edit button click should save posted FinancialTransaction edit page', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialTransaction');
        cy.get('[data-cy="entityCreateSaveButton"]').click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('last delete button click should delete instance of FinancialTransaction', () => {
        cy.intercept('GET', '/api/financial-transactions/*').as('dialogDeleteRequest');
        cy.visit(`${financialTransactionPageUrl}/${financialTransaction.id}/delete`);
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialTransaction').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);

        financialTransaction = undefined;
      });
    });
  });

  describe('manual candidate draft create page', () => {
    beforeEach(() => {
      createRuleSuggestionSeed().then(() => {
        cy.visit(`${financialTransactionPageUrl}/new`);
        cy.wait('@selectableAccountsRequest');
        cy.wait('@categoriesRequest');
        cy.wait('@tagsRequest');
      });
    });

    it('keeps a draft usable after its account becomes inactive, then posts it and excludes that account from new drafts', () => {
      cy.get('[data-cy="description"]').type(manualCandidateDescription);

      cy.wait('@createManualCandidateRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.source).to.equal('MANUAL');
        expect(response?.body.status).to.equal('DRAFT');
      });
      cy.url().should('match', new RegExp('/financial-transaction/drafts/\\d+$'));

      cy.get('[data-cy="account"]').select(String(financialAccount.id));
      cy.get('[data-cy="transactionDate"]').type('2026-07-08');
      cy.get('[data-cy="amount"]').clear().type('100.5');
      cy.get('[data-cy="flow"]').select('OUT');
      waitForSavedManualCandidateDraft(manualCandidateDescription);
      cy.get('[data-cy="manualDraftPostButton"]').should('be.disabled');

      cy.authenticatedRequest({
        method: 'PATCH',
        url: `/api/financial-accounts/${financialAccount.id}`,
        body: { active: false },
      })
        .its('status')
        .should('eq', 200);

      cy.reload();
      cy.wait('@getManualCandidateRequest');
      cy.get('[data-cy="description"]').should('have.value', manualCandidateDescription);
      cy.wait('@selectableAccountsRequest').then(({ request }) => {
        expect(request.url).to.contain('includeId=');
      });
      cy.get('[data-cy="account"] option:selected')
        .should('contain', financialAccount.name)
        .invoke('text')
        .should('match', /Inactiv[ae]/);
      cy.get('[data-cy="amount"]').should('have.value', '100.5');
      cy.get('[data-cy="flow"]').should('have.value', 'OUT');

      // Explicit manual selection avoids assuming a newly-created rule outranks pre-existing
      // local rules. It also establishes the historical associations we must preserve.
      cy.get('[data-cy="category"]').select(String(category.id));
      cy.get('[data-cy="tags"]').select([String(tag.id)]);
      waitForManualCandidate(
        candidate =>
          candidate.classificationReviewStatus === 'USER_SELECTED' &&
          candidate.category?.id === category.id &&
          candidate.tags?.some(candidateTag => candidateTag.id === tag.id) === true,
      );

      cy.authenticatedRequest({
        method: 'PATCH',
        url: `/api/categories/${category.id}`,
        body: { active: false },
      })
        .its('status')
        .should('eq', 200);
      cy.authenticatedRequest({
        method: 'PATCH',
        url: `/api/tags/${tag.id}`,
        body: { active: false },
      })
        .its('status')
        .should('eq', 200);

      cy.reload();
      cy.wait('@getManualCandidateRequest');
      cy.wait('@currentCategoryRequest').then(({ response }) => {
        expect(response?.body.map(selectableCategory => selectableCategory.id)).to.include(category.id);
      });
      cy.wait('@currentTagRequest').then(({ response }) => {
        expect(response?.body.map(selectableTag => selectableTag.id)).to.include(tag.id);
      });
      cy.contains('[data-cy="category"] option', category.name)
        .should('have.value', String(category.id))
        .invoke('text')
        .should('match', /Inactiv[ae]/);
      cy.get('[data-cy="category"]').should('have.value', String(category.id));
      cy.contains('[data-cy="tags"] option', tag.name)
        .should('have.value', String(tag.id))
        .invoke('text')
        .should('match', /Inactiv[ae]/);
      cy.get('[data-cy="tags"] option:selected').should('have.value', String(tag.id));

      cy.location('pathname').then(pathname => {
        const candidateId = pathname.match(/\/financial-transaction\/drafts\/(\d+)$/)?.[1];
        expect(candidateId, 'manual draft id in URL').to.exist;
        cy.authenticatedRequest({
          method: 'POST',
          url: `/api/transaction-candidates/${candidateId}/rule-preview`,
        }).then(({ body }) => {
          expect(body.suggestedCategory?.categoryId).not.to.equal(category.id);
          expect(body.suggestedTags?.map(suggestedTag => suggestedTag.tagId) ?? []).not.to.include(tag.id);
        });
      });

      const lastSecondNotes = uniqueName('latest-notes');
      cy.get('[data-cy="notes"]').type(lastSecondNotes);
      cy.get('[data-cy="manualDraftPostButton"]').should('not.be.disabled').click();
      let postedFinancialTransactionId;
      cy.wait('@postManualCandidateRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.status).to.equal('POSTED');
        expect(response?.body.financialTransaction.id).to.exist;
        financialTransaction = response?.body.financialTransaction;
        postedFinancialTransactionId = response?.body.financialTransaction.id;
      });
      cy.url().should('match', new RegExp('/financial-transaction/\\d+$'));
      cy.then(() => {
        expect(postedFinancialTransactionId).to.exist;
        cy.authenticatedRequest({
          method: 'GET',
          url: `/api/financial-transactions/${postedFinancialTransactionId}`,
        }).then(({ body }) => {
          financialTransaction = body;
          expect(body.description).to.equal(manualCandidateDescription);
          expect(body.notes).to.equal(lastSecondNotes);
          expect(body.category.id).to.equal(category.id);
          expect(body.tags.map(transactionTag => transactionTag.id)).to.include(tag.id);
        });
      });
      cy.visit(`${financialTransactionPageUrl}/drafts`);
      cy.wait('@manualDraftsRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.map(draft => draft.description)).not.to.include(manualCandidateDescription);
      });
      cy.contains('[data-cy="manualDraftRow"]', manualCandidateDescription).should('not.exist');
      cy.visit(`${financialTransactionPageUrl}/new`);
      cy.wait('@selectableAccountsRequest');
      cy.get('[data-cy="account"] option').should('not.contain', financialAccount.name);
      cy.wait('@categoriesRequest');
      cy.wait('@tagsRequest');
      cy.get('[data-cy="category"] option').should('not.contain', category.name);
      cy.get('[data-cy="tags"] option').should('not.contain', tag.name);

      cy.authenticatedRequest({ method: 'PATCH', url: `/api/categories/${category.id}`, body: { active: true } })
        .its('status')
        .should('eq', 200);
      cy.authenticatedRequest({ method: 'PATCH', url: `/api/tags/${tag.id}`, body: { active: true } })
        .its('status')
        .should('eq', 200);

      cy.reload();
      cy.wait('@categoriesRequest');
      cy.wait('@tagsRequest');
      cy.get('[data-cy="category"] option').should('contain', category.name);
      cy.get('[data-cy="tags"] option').should('contain', tag.name);
      cy.get('@rulePreviewRequest.all').should('have.length', 0);
    });

    it('cancels a saved candidate draft', () => {
      cy.get('[data-cy="description"]').type('Candidate to cancel');
      cy.wait('@createManualCandidateRequest').its('response.statusCode').should('eq', 201);

      cy.get('[data-cy="manualDraftCancelButton"]').click();
      cy.wait('@cancelManualCandidateRequest').its('response.statusCode').should('eq', 200);
      cy.url().should('match', financialTransactionPageUrlPattern);
    });

    it('lists, resumes, and cancels a recoverable manual draft from the draft recovery page', () => {
      const draftDescription = uniqueName('Recoverable manual draft');

      cy.get('[data-cy="description"]').type(draftDescription);
      cy.wait('@createManualCandidateRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.id).to.exist;
      });
      waitForSavedManualCandidateDescription(draftDescription);

      cy.visit(financialTransactionPageUrl);
      cy.wait('@entitiesRequest');
      cy.get('[data-cy="manualDraftsButton"]').click();
      cy.wait('@manualDraftsRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.map(draft => draft.description)).to.include(draftDescription);
      });
      cy.url().should('match', new RegExp('/financial-transaction/drafts$'));
      cy.contains('[data-cy="manualDraftRow"]', draftDescription).as('draftRow');
      cy.get('@draftRow').find('[data-cy="manualDraftResumeButton"]').click();
      cy.url().should('match', new RegExp('/financial-transaction/drafts/\\d+$'));
      cy.get('[data-cy="description"]').should('have.value', draftDescription);

      cy.visit(`${financialTransactionPageUrl}/drafts`);
      cy.wait('@manualDraftsRequest');
      cy.on('window:confirm', () => true);
      cy.contains('[data-cy="manualDraftRow"]', draftDescription).find('[data-cy="manualDraftCancelListButton"]').click();
      cy.wait('@cancelManualCandidateRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.status).to.equal('CANCELLED');
      });
      cy.contains('[data-cy="manualDraftRow"]', draftDescription).should('not.exist');
      cy.get('@rulePreviewRequest.all').should('have.length', 0);
    });
  });

  describe('FinancialTransaction ownership', () => {
    it('regular user should not see transactions on another users account', () => {
      const adminAccountName = `admin-tx-account-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(adminAccountName),
      }).then(({ body: adminAccount }) => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(adminAccount.id),
        }).then(({ body: adminTransaction }) => {
          cy.login(username, password);
          cy.authenticatedRequest({
            method: 'GET',
            url: '/api/financial-transactions',
          }).then(({ body: userTransactions }) => {
            expect(userTransactions.some(transaction => transaction.id === adminTransaction.id)).to.equal(false);
          });

          cy.login(adminUsername, adminPassword);
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-transactions/${adminTransaction.id}`,
          });
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-accounts/${adminAccount.id}`,
          });
        });
      });
    });

    it('admin should access transactions on another users account by direct id', () => {
      const userAccountName = `user-tx-account-${Date.now()}`;

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(userAccountName),
      }).then(({ body: userAccount }) => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(userAccount.id),
        }).then(({ body: userTransaction }) => {
          cy.login(adminUsername, adminPassword);
          cy.authenticatedRequest({
            method: 'GET',
            url: `/api/financial-transactions/${userTransaction.id}`,
          }).then(({ body: adminTransaction }) => {
            expect(adminTransaction.id).to.equal(userTransaction.id);
          });

          cy.login(username, password);
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-transactions/${userTransaction.id}`,
          });
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-accounts/${userAccount.id}`,
          });
        });
      });
    });
  });
});
