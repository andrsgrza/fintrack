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

describe('TransactionRuleCondition e2e test', () => {
  const transactionRuleConditionPageUrl = '/transaction-rule-condition';
  const transactionRuleConditionPageUrlPattern = new RegExp('/transaction-rule-condition(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let transactionRuleCondition;
  let transactionRule;

  const buildTransactionRulePayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      priority: 1,
      conditionLogic: 'ALL',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/transaction-rules',
      body: buildTransactionRulePayload(`rule-for-condition-${Date.now()}`),
    }).then(({ body }) => {
      transactionRule = body;
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-rule-conditions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/transaction-rule-conditions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/transaction-rule-conditions/*').as('deleteEntityRequest');
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-rules', {
      statusCode: 200,
      body: [transactionRule],
    });
  });

  afterEach(() => {
    if (transactionRuleCondition) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rule-conditions/${transactionRuleCondition.id}`,
      }).then(() => {
        transactionRuleCondition = undefined;
      });
    }
  });

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

  it('TransactionRuleConditions menu should load TransactionRuleConditions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('transaction-rule-condition');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('TransactionRuleCondition').should('exist');
    cy.url().should('match', transactionRuleConditionPageUrlPattern);
  });

  describe('TransactionRuleCondition page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(transactionRuleConditionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create TransactionRuleCondition page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/transaction-rule-condition/new$'));
        cy.getEntityCreateUpdateHeading('TransactionRuleCondition');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRuleConditionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/transaction-rule-conditions',
          body: {
            field: 'DESCRIPTION',
            operator: 'EQUALS',
            value: 'cypress-value',
            caseSensitive: false,
            position: 1,
            transactionRule: { id: transactionRule.id },
          },
        }).then(({ body }) => {
          transactionRuleCondition = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/transaction-rule-conditions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [transactionRuleCondition],
            },
          ).as('entitiesRequestInternal');
        });

        cy.visit(transactionRuleConditionPageUrl);
        cy.wait('@entitiesRequestInternal');
      });

      it('detail button click should load details TransactionRuleCondition page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('transactionRuleCondition');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRuleConditionPageUrlPattern);
      });

      it('edit button click should load edit TransactionRuleCondition page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRuleCondition');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRuleConditionPageUrlPattern);
      });

      it('edit button click should load edit TransactionRuleCondition page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRuleCondition');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRuleConditionPageUrlPattern);
      });

      it('last delete button click should delete instance of TransactionRuleCondition', () => {
        cy.intercept('GET', '/api/transaction-rule-conditions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('transactionRuleCondition').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRuleConditionPageUrlPattern);

        transactionRuleCondition = undefined;
      });
    });
  });

  describe('new TransactionRuleCondition page', () => {
    beforeEach(() => {
      cy.visit(`${transactionRuleConditionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('TransactionRuleCondition');
    });

    it('should create an instance of TransactionRuleCondition', () => {
      cy.get(`[data-cy="field"]`).select('DESCRIPTION');
      cy.get(`[data-cy="operator"]`).select('EQUALS');
      cy.get(`[data-cy="value"]`).type('cypress-create-value');
      cy.get(`[data-cy="caseSensitive"]`).should('not.be.checked');
      cy.get(`[data-cy="position"]`).type('1');
      cy.get(`[data-cy="transactionRule"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.transactionRule.id).to.equal(transactionRule.id);
        transactionRuleCondition = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', transactionRuleConditionPageUrlPattern);
    });
  });
});
