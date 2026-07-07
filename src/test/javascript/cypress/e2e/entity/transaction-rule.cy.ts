import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateCancelButtonSelector,
  entityCreateSaveButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('TransactionRule e2e test', () => {
  const transactionRulePageUrl = '/transaction-rule';
  const transactionRulePageUrlPattern = new RegExp('/transaction-rule(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const transactionRuleSample = {"name":"since till ditch","priority":30269,"conditionLogic":"ALL","active":false,"createdAt":"2026-07-06T19:01:26.924Z","updatedAt":"2026-07-07T04:04:48.532Z"};

  let transactionRule;
  // let user;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/users',
      body: {"login":"41p8@Ke\\`ZzkcD-\\.LrF3YL\\orwq","firstName":"Norma","lastName":"Rosario Solorio","email":"Isabel.VargasDelapaz40@yahoo.com","imageUrl":"yawningly over","langKey":"that zowie"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-rules+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/transaction-rules').as('postEntityRequest');
    cy.intercept('DELETE', '/api/transaction-rules/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

    cy.intercept('GET', '/api/categories', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-subscriptions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/tags', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/transaction-rule-conditions', {
      statusCode: 200,
      body: [],
    });

  });
   */

  afterEach(() => {
    if (transactionRule) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${transactionRule.id}`,
      }).then(() => {
        transactionRule = undefined;
      });
    }
  });

  /* Disabled due to incompatibility
  afterEach(() => {
    if (user) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/users/${user.id}`,
      }).then(() => {
        user = undefined;
      });
    }
  });
   */

  it('TransactionRules menu should load TransactionRules page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('transaction-rule');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('TransactionRule').should('exist');
    cy.url().should('match', transactionRulePageUrlPattern);
  });

  describe('TransactionRule page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(transactionRulePageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create TransactionRule page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/transaction-rule/new$'));
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/transaction-rules',
          body: {
            ...transactionRuleSample,
            user: user,
          },
        }).then(({ body }) => {
          transactionRule = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/transaction-rules+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [transactionRule],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(transactionRulePageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(transactionRulePageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details TransactionRule page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('transactionRule');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      it('edit button click should load edit TransactionRule page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      it('edit button click should load edit TransactionRule page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of TransactionRule', () => {
        cy.intercept('GET', '/api/transaction-rules/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('transactionRule').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);

        transactionRule = undefined;
      });
    });
  });

  describe('new TransactionRule page', () => {
    beforeEach(() => {
      cy.visit(`${transactionRulePageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('TransactionRule');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of TransactionRule', () => {
      cy.get(`[data-cy="name"]`).type('mindless');
      cy.get(`[data-cy="name"]`).should('have.value', 'mindless');

      cy.get(`[data-cy="description"]`).type('roughly');
      cy.get(`[data-cy="description"]`).should('have.value', 'roughly');

      cy.get(`[data-cy="priority"]`).type('7062');
      cy.get(`[data-cy="priority"]`).should('have.value', '7062');

      cy.get(`[data-cy="conditionLogic"]`).select('ANY');

      cy.get(`[data-cy="resultingDescription"]`).type('dilate surge');
      cy.get(`[data-cy="resultingDescription"]`).should('have.value', 'dilate surge');

      cy.get(`[data-cy="active"]`).should('not.be.checked');
      cy.get(`[data-cy="active"]`).click();
      cy.get(`[data-cy="active"]`).should('be.checked');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T13:03');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T13:03');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T15:47');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T15:47');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        transactionRule = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', transactionRulePageUrlPattern);
    });
  });
});
