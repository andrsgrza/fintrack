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
  let secondaryFinancialTransaction;
  let secondaryCategory;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const buildFinancialAccountPayload = (name: string) => ({
    name,
    institutionName: 'E2E Bank',
    accountType: 'DEBIT',
    currency: 'MXN',
    initialBalance: 1000,
    initialBalanceDate: '2026-07-08',
    lastFourDigits: '4657',
    active: true,
  });

  const buildFinancialTransactionPayload = (accountId: number, categoryId?: number, tagId?: number) => ({
    transactionDate: '2026-07-08',
    description: 'E2E transaction',
    amount: 100.5,
    flow: 'OUT',
    origin: 'MANUAL',
    account: { id: accountId },
    ...(categoryId ? { category: { id: categoryId } } : {}),
    ...(tagId ? { tags: [{ id: tagId }] } : {}),
  });

  const waitForManualCandidate = (
    predicate: (candidate: {
      description?: string;
      signedAmount?: number | string;
      transactionDate?: string | null;
      postingDate?: string | null;
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
    cy.intercept('PATCH', '/api/financial-transactions/*').as('patchFinancialTransactionRequest');
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
    if (secondaryFinancialTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${secondaryFinancialTransaction.id}`,
        failOnStatusCode: false,
      }).then(() => {
        secondaryFinancialTransaction = undefined;
      });
    }
    if (secondaryCategory) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/categories/${secondaryCategory.id}`,
        failOnStatusCode: false,
      }).then(() => {
        secondaryCategory = undefined;
      });
    }
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
    cy.viewport(1280, 800);
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
        cy.get('[data-cy="FinancialTransactionManualDraftHeading"]')
          .invoke('text')
          .should('match', /Nueva transacción|New transaction/);
        cy.get('[data-cy="manualDraftAutosaveStatus"]').should('not.exist');
        cy.get('@createManualCandidateRequest.all').should('have.length', 0);
        cy.get('@rulePreviewRequest.all').should('have.length', 0);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        createCategory()
          .then(() => createTag())
          .then(() =>
            cy.authenticatedRequest({
              method: 'POST',
              url: '/api/financial-transactions',
              body: buildFinancialTransactionPayload(financialAccount.id, category.id, tag.id),
            }),
          )
          .then(({ body }) => {
            financialTransaction = body;
          });

        cy.visit(`${financialTransactionPageUrl}?page=1&sort=id,desc`);
        cy.wait('@entitiesRequest');
      });

      it('detail button click should load the product posted transaction detail', () => {
        cy.viewport(1280, 800);
        cy.get('[data-cy="financialTransactionProductList"]').should('exist');
        cy.contains('th', 'Created').should('not.exist');
        cy.contains('th', 'Updated').should('not.exist');
        cy.contains(entityTableSelector, financialTransaction.description)
          .should('contain', financialAccount.name)
          .and('contain', category.name)
          .and('contain', tag.name)
          .and('contain', '−100.50 MXN');
        cy.contains(entityTableSelector, financialTransaction.description)
          .find('[data-cy="transactionCategory"]')
          .should('have.attr', 'data-color-treatment', 'category')
          .and('have.class', 'rounded-1');
        cy.contains(entityTableSelector, financialTransaction.description)
          .find('[data-cy="transactionTagChip"]')
          .should('have.attr', 'data-color-treatment', 'tag')
          .and('have.class', 'rounded-pill');
        cy.contains('MANUAL').should('not.exist');
        cy.get(entityDetailsButtonSelector).first().click();
        cy.get('[data-cy="financialTransactionDetailsHeading"]').should('contain', financialTransaction.description);
        cy.get('[data-cy="transactionAmount"]').should('contain', '−100.50 MXN').and('contain', 'Gasto');
        cy.get('[data-cy="transactionFlowBadge"]').should('have.length', 1);
        cy.get('[data-cy="financialTransactionDetailAccountContext"]').should('not.contain', 'Gasto').and('not.contain', 'Ingreso');
        cy.get('[data-cy="transactionAccountLink"]')
          .should('contain', financialAccount.name)
          .and('contain', 'MXN')
          .and('contain', '••••4657');
        cy.get('[data-cy="financialTransactionDetailClassification"]').should('contain', category.name).and('contain', tag.name);
        cy.contains('MANUAL').should('not.exist');
        cy.contains('Created At').should('not.exist');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('filters posted transactions on the server by description, type, account, category, and tag', () => {
        const incomeDescription = uniqueName('filter-income-transaction');

        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/categories',
          body: {
            name: uniqueName('filter-income-category'),
            description: 'Income category for transaction filter E2E',
            categoryType: 'INCOME',
            color: '#336699',
            active: true,
          },
        })
          .then(({ body }) => {
            secondaryCategory = body;
            return cy.authenticatedRequest({
              method: 'POST',
              url: '/api/financial-transactions',
              body: {
                transactionDate: '2026-07-10',
                postingDate: '2026-07-10',
                description: incomeDescription,
                amount: 42,
                flow: 'IN',
                origin: 'MANUAL',
                account: { id: financialAccount.id },
                category: { id: secondaryCategory.id },
              },
            });
          })
          .then(({ body }) => {
            secondaryFinancialTransaction = body;
          });

        cy.visit(`${financialTransactionPageUrl}?page=1&sort=id,desc`);
        cy.wait('@entitiesRequest');
        cy.get('[data-cy="financialTransactionFiltersToggle"]').click();

        cy.get('[data-cy="financialTransactionSearch"]').type(incomeDescription);
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('description.contains')).to.equal(incomeDescription);
          expect(new URL(request.url).searchParams.get('page')).to.equal('0');
        });
        cy.contains(entityTableSelector, incomeDescription).should('exist');
        cy.contains(entityTableSelector, financialTransaction.description).should('not.exist');

        cy.get('[data-cy="financialTransactionClearFilters"]').click();
        cy.wait('@entitiesRequest');
        cy.get('[data-cy="financialTransactionFilterFlow"]').select('IN');
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('flow.equals')).to.equal('IN');
        });
        cy.contains(entityTableSelector, incomeDescription).should('exist');
        cy.contains(entityTableSelector, financialTransaction.description).should('not.exist');

        cy.get('[data-cy="financialTransactionClearFilters"]').click();
        cy.wait('@entitiesRequest');
        cy.get('[data-cy="financialTransactionFilterAccount"]').select(String(financialAccount.id));
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('accountId.equals')).to.equal(String(financialAccount.id));
        });
        cy.contains(entityTableSelector, incomeDescription).should('exist');
        cy.contains(entityTableSelector, financialTransaction.description).should('exist');

        cy.get('[data-cy="financialTransactionClearFilters"]').click();
        cy.wait('@entitiesRequest');
        cy.get('[data-cy="financialTransactionFilterCategory"]').select(String(category.id));
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('categoryId.equals')).to.equal(String(category.id));
        });
        cy.contains(entityTableSelector, financialTransaction.description).should('exist');
        cy.contains(entityTableSelector, incomeDescription).should('not.exist');

        cy.get('[data-cy="financialTransactionClearFilters"]').click();
        cy.wait('@entitiesRequest');
        cy.get('[data-cy="financialTransactionFilterTagsAdd"]').click();
        cy.get('[data-cy="financialTransactionFilterTagsSearch"]').type(tag.name);
        cy.contains('[data-cy="financialTransactionFilterTagsOption"]', tag.name).click();
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('tagsId.in')).to.equal(String(tag.id));
        });
        cy.contains(entityTableSelector, financialTransaction.description).should('exist');
        cy.contains(entityTableSelector, incomeDescription).should('not.exist');

        cy.contains('[data-cy="financialTransactionFilterTagsChip"]', tag.name)
          .find('[data-cy="financialTransactionFilterTagsRemove"]')
          .click();
        cy.get('[data-cy="financialTransactionSearchSubmit"]').click();
        cy.wait('@entitiesRequest').then(({ request }) => {
          expect(new URL(request.url).searchParams.get('tagsId.in')).to.be.null;
        });

        cy.url().should('not.contain', 'description.contains').and('not.contain', 'flow.equals').and('not.contain', 'categoryId.equals');
      });

      it('edit button click should load the posted transaction product form and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.get('[data-cy="FinancialTransactionCreateUpdateHeading"]')
          .invoke('text')
          .should('match', /Editar transacción|Edit transaction/);
        cy.get('[data-cy="financialTransactionEditTransactionSection"]').should('exist');
        cy.get('[data-cy="financialTransactionEditClassificationSection"]').should('exist');
        cy.get('[data-cy="financialTransactionEditOptionalDetails"]').should('exist');
        cy.get('[data-cy="account"]').should('not.exist');
        cy.get('[data-cy="transactionAccountLink"]').should('contain', financialAccount.name);
        cy.get('[data-cy="entityCreateSaveButton"]').should('exist');
        cy.get('[data-cy="entityCreateCancelButton"]').click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('edit button saves posted transaction fields and the detail reflects them', () => {
        const updatedDescription = `${financialTransaction.description} updated`;
        cy.get(entityEditButtonSelector).first().click();
        cy.get('[data-cy="description"]').clear().type(updatedDescription);
        cy.get('[data-cy="transactionDate"]').clear().type('2026-07-09');
        cy.get('[data-cy="amount"]').clear().type('125.75');
        cy.get('[data-cy="category"]').select(String(category.id));
        cy.contains('[data-cy="financialTransactionTagsChip"]', tag.name).find('[data-cy="financialTransactionTagsRemove"]').click();
        cy.get('[data-cy="financialTransactionTagsAdd"]').click();
        cy.get('[data-cy="financialTransactionTagsSearch"]').type(tag.name);
        cy.contains('[data-cy="financialTransactionTagsOption"]', tag.name).click();
        cy.get('[data-cy="financialTransactionEditOptionalDetails"] summary').click();
        cy.get('[data-cy="externalReference"]').type('updated-reference');
        cy.get('[data-cy="notes"]').type('updated notes');
        cy.get('[data-cy="entityCreateSaveButton"]').click();
        cy.wait('@patchFinancialTransactionRequest').then(({ request, response }) => {
          expect(response?.statusCode).to.equal(200);
          expect(request.body).to.include({
            description: updatedDescription,
            amount: 125.75,
            externalReference: 'updated-reference',
            notes: 'updated notes',
          });
          expect(request.body).not.to.have.property('account');
          expect(request.body).not.to.have.property('origin');
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
        cy.contains(entityTableSelector, updatedDescription).find(entityDetailsButtonSelector).click();
        cy.get('[data-cy="financialTransactionDetailsHeading"]').should('contain', updatedDescription);
        cy.get('[data-cy="transactionAmount"]').should('contain', '−125.75 MXN');
        cy.contains('[data-cy="financialTransactionDetailMetadata"]', 'updated-reference').should('exist');
        cy.get('[data-cy="financialTransactionDetailNotes"]').should('contain', 'updated notes');
      });

      it('detail overflow delete should delete a posted FinancialTransaction', () => {
        cy.intercept('GET', '/api/financial-transactions/*').as('dialogDeleteRequest');
        cy.get(entityDetailsButtonSelector).first().click();
        cy.get('[data-cy="financialTransactionDetailActionsMenuToggle"]').click();
        cy.get('[data-cy="entityDeleteButton"]').click();
        cy.wait('@dialogDeleteRequest');
        cy.get('[data-cy="financialTransactionDeleteDialogHeading"]')
          .invoke('text')
          .should('match', /Delete transaction|Eliminar transacción/);
        cy.get('#fintrackApp\\.financialTransaction\\.delete\\.question')
          .invoke('text')
          .should('match', /Are you sure you want to delete this transaction\?|¿Seguro que quieres eliminar esta transacción\?/);
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
      cy.get('[data-cy="FinancialTransactionManualDraftHeading"]')
        .invoke('text')
        .should('match', /Editar borrador|Edit draft/);
      cy.get('[data-cy="manualDraftAutosaveStatus"]')
        .invoke('text')
        .should('match', /Guardado automáticamente|Saved automatically/);

      cy.get('[data-cy="account"]').select(String(financialAccount.id));
      cy.get('[data-cy="transactionDate"]').type('2026-07-08');
      cy.get('[data-cy="samePostingDate"]').should('be.checked');
      cy.get('[data-cy="postingDate"]').should('be.disabled').and('have.value', '2026-07-08');
      cy.get('[data-cy="amount"]').clear().type('100.5');
      cy.get('[data-cy="flow-OUT"]').click().should('have.attr', 'aria-pressed', 'true');
      waitForSavedManualCandidateDraft(manualCandidateDescription);
      cy.get('[data-cy="manualDraftPostButton"]').should('be.disabled');

      let suggestedCategoryId: number | null = null;
      let suggestedCategoryName: string | null = null;
      let suggestedTagNames: string[] = [];
      cy.wait('@candidateRulePreviewRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(response?.body.hasSuggestions).to.equal(true);
        suggestedCategoryId = response?.body.suggestedCategory?.categoryId ?? null;
        suggestedCategoryName = response?.body.suggestedCategory?.categoryName ?? null;
        suggestedTagNames = (response?.body.suggestedTags ?? []).map(suggestion => suggestion.tagName);
      });
      cy.get('[data-testid="manual-draft-rule-suggestions"]').should($section => {
        if (suggestedCategoryName) {
          expect($section.text()).to.contain(suggestedCategoryName);
        }
        suggestedTagNames.forEach(suggestedTagName => expect($section.text()).to.contain(suggestedTagName));
      });
      cy.get('[data-cy="manualDraftApplyRulesButton"]').click();
      cy.wait('@candidateApplyRulesRequest').its('response.statusCode').should('eq', 200);
      cy.then(() => {
        if (suggestedCategoryId) {
          cy.get('[data-cy="category"]').should('have.value', String(suggestedCategoryId));
        }
        suggestedTagNames.forEach(suggestedTagName => cy.get('[data-cy="manualDraftTagsChip"]').should('contain', suggestedTagName));
      });

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
      cy.get('[data-cy="flow-OUT"]').should('have.attr', 'aria-pressed', 'true');

      // Explicit selections after applying suggestions are user-owned and must survive another preview.
      cy.get('[data-cy="category"]').select(String(category.id));
      cy.contains('[data-cy="manualDraftTagsChip"]', tag.name).find('[data-cy="manualDraftTagsRemove"]').click();
      cy.get('[data-cy="manualDraftTagsAdd"]').click();
      cy.get('[data-cy="manualDraftTagsSearch"]').type(tag.name);
      cy.contains('[data-cy="manualDraftTagsOption"]', tag.name).click();
      waitForManualCandidate(
        candidate =>
          candidate.classificationReviewStatus === 'USER_SELECTED' &&
          candidate.category?.id === category.id &&
          candidate.tags?.some(candidateTag => candidateTag.id === tag.id) === true,
      );

      const reEvaluatedDescription = `${manualCandidateDescription} revised`;
      cy.get('[data-cy="description"]').should('have.value', manualCandidateDescription).type(' revised');
      manualCandidateDescription = reEvaluatedDescription;
      waitForSavedManualCandidateDraft(manualCandidateDescription);
      cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="category"]').should('have.value', String(category.id));
      cy.get('[data-cy="manualDraftTagsChip"]').should('contain', tag.name);
      cy.get('[data-cy="manualDraftApplyRulesButton"]').should('not.be.disabled').click();
      cy.wait('@candidateApplyRulesRequest').its('response.statusCode').should('eq', 200);
      waitForManualCandidate(candidate =>
        ['SUGGESTED', 'USER_SELECTED', 'NOT_APPLICABLE'].includes(candidate.classificationReviewStatus ?? ''),
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
      cy.get('@currentCategoryRequest.all').should(interceptions => {
        const response = interceptions.at(-1)?.response;
        expect(response?.body.map(selectableCategory => selectableCategory.id)).to.include(category.id);
      });
      cy.get('@currentTagRequest.all').should(interceptions => {
        const response = interceptions.at(-1)?.response;
        expect(response?.body.map(selectableTag => selectableTag.id)).to.include(tag.id);
      });
      cy.contains('[data-cy="category"] option', category.name)
        .should('have.value', String(category.id))
        .invoke('text')
        .should('match', /Inactiv[ae]/);
      cy.get('[data-cy="category"]').should('have.value', String(category.id));
      cy.contains('[data-cy="manualDraftTagsChip"]', tag.name).should('exist');

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
      cy.get('[data-cy="manualDraftOptionalDetails"] summary').click();
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
          expect(body.transactionDate).to.equal('2026-07-08');
          expect(body.postingDate).to.equal('2026-07-08');
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
      cy.get('[data-cy="manualDraftTagsAdd"]').click();
      cy.get('body').find('[data-cy="manualDraftTagsOption"]').should('not.contain', tag.name);

      cy.authenticatedRequest({ method: 'PATCH', url: `/api/categories/${category.id}`, body: { active: true } })
        .its('status')
        .should('eq', 200);
      cy.authenticatedRequest({ method: 'PATCH', url: `/api/tags/${tag.id}`, body: { active: true } })
        .its('status')
        .should('eq', 200);

      cy.reload();
      cy.wait('@categoriesRequest');
      cy.wait('@tagsRequest');
      cy.get('[data-cy="flow-OUT"]').click().should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-cy="category"] option').should('contain', category.name);
      cy.get('[data-cy="manualDraftTagsAdd"]').click();
      cy.get('[data-cy="manualDraftTagsOption"]').should('contain', tag.name);
      cy.get('@rulePreviewRequest.all').should('have.length', 0);
    });

    it('cancels a saved candidate draft', () => {
      cy.get('[data-cy="description"]').type('Candidate to cancel');
      cy.wait('@createManualCandidateRequest').its('response.statusCode').should('eq', 201);

      cy.on('window:confirm', () => true);
      cy.get('[data-cy="manualDraftCancelButton"]').click();
      cy.wait('@cancelManualCandidateRequest').its('response.statusCode').should('eq', 200);
      cy.url().should('match', financialTransactionPageUrlPattern);
    });

    it('synchronizes posting date by default, supports independent dates, and hydrates that choice', () => {
      const draftDescription = uniqueName('same-posting-date-draft');

      cy.get('[data-cy="description"]').type(draftDescription);
      cy.wait('@createManualCandidateRequest').its('response.statusCode').should('eq', 201);
      cy.get('[data-cy="account"]').select(String(financialAccount.id));
      cy.get('[data-cy="transactionDate"]').type('2026-07-08');
      cy.get('[data-cy="samePostingDate"]').should('be.checked');
      cy.get('[data-cy="postingDate"]').should('be.disabled').and('have.value', '2026-07-08');

      cy.get('[data-cy="samePostingDate"]').uncheck();
      cy.get('[data-cy="postingDate"]').should('not.be.disabled').clear().type('2026-07-09');
      cy.get('[data-cy="transactionDate"]').clear().type('2026-07-10');
      cy.get('[data-cy="postingDate"]').should('have.value', '2026-07-09');
      waitForManualCandidate(candidate => candidate.transactionDate === '2026-07-10' && candidate.postingDate === '2026-07-09');

      cy.reload();
      cy.wait('@getManualCandidateRequest');
      cy.get('[data-cy="samePostingDate"]').should('not.be.checked');
      cy.get('[data-cy="transactionDate"]').should('have.value', '2026-07-10');
      cy.get('[data-cy="postingDate"]').should('have.value', '2026-07-09').and('not.be.disabled');

      cy.on('window:confirm', () => true);
      cy.get('[data-cy="manualDraftCancelButton"]').click();
      cy.wait('@cancelManualCandidateRequest').its('response.statusCode').should('eq', 200);
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
      cy.get('[data-cy="manualDraftBackToTransactions"]').click();
      cy.wait('@entitiesRequest');
      cy.url().should('match', financialTransactionPageUrlPattern);
      cy.get('[data-cy="manualDraftsButton"]').click();
      cy.wait('@manualDraftsRequest');
      cy.contains('[data-cy="manualDraftRow"]', draftDescription).as('draftRow');
      cy.get('@draftRow').find('[data-cy="manualDraftResumeButton"]').click();
      cy.url().should('match', new RegExp('/financial-transaction/drafts/\\d+$'));
      cy.get('[data-cy="description"]').should('have.value', draftDescription);

      cy.visit(`${financialTransactionPageUrl}/drafts`);
      cy.wait('@manualDraftsRequest');
      cy.on('window:confirm', () => true);
      cy.contains('[data-cy="manualDraftRow"]', draftDescription).find('[data-cy="manualDraftActionsMenuToggle"]').click();
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
