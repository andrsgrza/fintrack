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

describe('InternalTransfer e2e test', () => {
  const internalTransferPageUrl = '/internal-transfer';
  const internalTransferPageUrlPattern = new RegExp('/internal-transfer(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const internalTransferSample = {"createdAt":"2026-07-07T02:16:29.475Z"};

  let internalTransfer;
  // let financialTransaction;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-transactions',
      body: {"transactionDate":"2026-07-07","postingDate":"2026-07-06","description":"broadcast geez beneath","amount":32148.45,"flow":"IN","origin":"FILE_IMPORT","externalReference":"lox","notes":"unless","createdAt":"2026-07-06T19:02:18.055Z","updatedAt":"2026-07-06T20:32:12.632Z"},
    }).then(({ body }) => {
      financialTransaction = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/internal-transfers+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/internal-transfers').as('postEntityRequest');
    cy.intercept('DELETE', '/api/internal-transfers/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/financial-transactions', {
      statusCode: 200,
      body: [financialTransaction],
    });

  });
   */

  afterEach(() => {
    if (internalTransfer) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/internal-transfers/${internalTransfer.id}`,
      }).then(() => {
        internalTransfer = undefined;
      });
    }
  });

  /* Disabled due to incompatibility
  afterEach(() => {
    if (financialTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${financialTransaction.id}`,
      }).then(() => {
        financialTransaction = undefined;
      });
    }
  });
   */

  it('InternalTransfers menu should load InternalTransfers page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('internal-transfer');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('InternalTransfer').should('exist');
    cy.url().should('match', internalTransferPageUrlPattern);
  });

  describe('InternalTransfer page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(internalTransferPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create InternalTransfer page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/internal-transfer/new$'));
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/internal-transfers',
          body: {
            ...internalTransferSample,
            outgoingTransaction: financialTransaction,
            incomingTransaction: financialTransaction,
          },
        }).then(({ body }) => {
          internalTransfer = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/internal-transfers+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [internalTransfer],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(internalTransferPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(internalTransferPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details InternalTransfer page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('internalTransfer');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      it('edit button click should load edit InternalTransfer page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      it('edit button click should load edit InternalTransfer page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of InternalTransfer', () => {
        cy.intercept('GET', '/api/internal-transfers/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('internalTransfer').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);

        internalTransfer = undefined;
      });
    });
  });

  describe('new InternalTransfer page', () => {
    beforeEach(() => {
      cy.visit(`${internalTransferPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('InternalTransfer');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of InternalTransfer', () => {
      cy.get(`[data-cy="notes"]`).type('hydrocarbon how likewise');
      cy.get(`[data-cy="notes"]`).should('have.value', 'hydrocarbon how likewise');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-06T18:09');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-06T18:09');

      cy.get(`[data-cy="outgoingTransaction"]`).select(1);
      cy.get(`[data-cy="incomingTransaction"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        internalTransfer = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', internalTransferPageUrlPattern);
    });
  });
});
